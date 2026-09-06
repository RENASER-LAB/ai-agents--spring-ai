package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.perfil.entity.LecturaCvPerfil;
import com.renaser.ai.ai_engine.perfil.repository.LecturaCvPerfilRepository;
import com.renaser.ai.ai_engine.perfil.service.ServicioPropuestaPerfil;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;
import com.renaser.ai.ai_engine.postulacion.service.impl.ExtractorTextoCv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El puente entre el agente `DATOS_CV` y un currículum que no cuelga de ninguna
 * postulación.
 *
 * ⚠️ **La guarda que más importa es `yaEstaCerrada`**, y se encontró probando a
 * mano: el trabajo sigue en la cola después de que la lectura se cierre —quitar
 * el currículum la cancela— y al terminar volcaba al perfil las propuestas de un
 * archivo que la persona acababa de borrar.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La lectura del currículum del perfil")
class PuenteLecturaCvPerfilImplTest {

    private static final long LECTURA = 4L;
    private static final long PERSONA = 7L;

    @Mock private LecturaCvPerfilRepository lecturas;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private ExtractorTextoCv extractor;
    @Mock private AnonimizadorCv anonimizador;
    @Mock private ServicioPropuestaPerfil propuesta;

    private PuenteLecturaCvPerfilImpl puente;

    private static final ResultadoDatos ALGO = new ResultadoDatos(
            "Lucía", null, null, "Analista", List.of("Excel"), 96, "Analista", "Clínica",
            12, "TITULADO", List.of(), List.of(), List.of(), List.of());

    private static LecturaCvPerfil enCurso() {
        return LecturaCvPerfil.builder().id(LECTURA).personaId(PERSONA).archivoId(50L)
                .estado(LecturaCvPerfil.EN_CURSO).intentos(0).creadoEn(Instant.now()).build();
    }

    @BeforeEach
    void montar() {
        puente = new PuenteLecturaCvPerfilImpl(
                lecturas, archivos, almacen, extractor, anonimizador, propuesta);
    }

    // ==================== El insumo ====================

    @Test
    @DisplayName("El modelo lee la versión recortada, igual que en el otro camino (RF-41)")
    void elModeloLeeLoRecortado() {
        when(lecturas.findById(LECTURA)).thenReturn(Optional.of(enCurso()));
        Archivo a = Archivo.builder().id(50L).ruta("1/x.pdf").tipo("application/pdf")
                .nombreOriginal("cv.pdf").build();
        when(archivos.findById(50L)).thenReturn(Optional.of(a));
        when(almacen.leer(a)).thenReturn(new byte[] {1});
        when(extractor.extraer(any(), eq("application/pdf"), eq("cv.pdf"))).thenReturn("con foto y edad");
        when(anonimizador.anonimizar("con foto y edad")).thenReturn("sin foto ni edad");

        InsumoDatos insumo = puente.insumo(LECTURA);

        assertThat(insumo.curriculum()).isEqualTo("sin foto ni edad");
        // Y donde los demás agentes ponen el puesto, aquí se dice que no hay.
        assertThat(insumo.puesto()).contains("Sin vacante");
    }

    @Test
    @DisplayName("Un archivo ya soltado no se intenta leer: se dice qué pasó")
    void elArchivoSoltadoNoSeLee() {
        when(lecturas.findById(LECTURA)).thenReturn(Optional.of(enCurso()));
        Archivo borrado = Archivo.builder().id(50L).ruta("1/x.pdf").build();
        borrado.setBorradoEn(Instant.now());
        when(archivos.findById(50L)).thenReturn(Optional.of(borrado));

        assertThatThrownBy(() -> puente.insumo(LECTURA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya no está guardado");
    }

    // ==================== Guardar lo leído ====================

    @Test
    @DisplayName("Con datos aprovechables, la lectura queda LISTA")
    void conDatosQuedaLista() {
        LecturaCvPerfil l = enCurso();
        when(lecturas.findById(LECTURA)).thenReturn(Optional.of(l));
        when(propuesta.proponerAlPerfil(PERSONA, ALGO)).thenReturn(true);

        puente.guardar(LECTURA, 1L, ALGO);

        assertThat(l.getEstado()).isEqualTo(LecturaCvPerfil.LISTA);
        assertThat(l.getMotivo()).isNull();
        assertThat(l.getTerminadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Si no entró NADA, se dice ilegible en vez de «listo»")
    void sinNadaAprovechableEsIlegible() {
        // Decirle LISTA le mandaría a buscar unos datos que no están.
        LecturaCvPerfil l = enCurso();
        when(lecturas.findById(LECTURA)).thenReturn(Optional.of(l));
        when(propuesta.proponerAlPerfil(PERSONA, ALGO)).thenReturn(false);

        puente.guardar(LECTURA, 1L, ALGO);

        assertThat(l.getEstado()).isEqualTo(LecturaCvPerfil.NO_LEGIBLE);
        assertThat(l.getMotivo()).contains("no dio ningún dato aprovechable");
    }

    @Test
    @DisplayName("Una lectura ya cerrada NO se vuelve a abrir ni vuelca nada al perfil")
    void laCerradaNoResucita() {
        // El trabajo sigue en la cola después de que la lectura se cierre: quitar
        // el currículum la cancela, y subir otro la deja atrás. Sin esta guarda
        // el perfil recibía las propuestas de un currículum ya borrado.
        LecturaCvPerfil cerrada = enCurso();
        cerrada.setEstado(LecturaCvPerfil.NO_LEGIBLE);
        cerrada.setTerminadoEn(Instant.now());
        when(lecturas.findById(LECTURA)).thenReturn(Optional.of(cerrada));

        puente.guardar(LECTURA, 1L, ALGO);

        verify(propuesta, never()).proponerAlPerfil(anyLong(), any());
        assertThat(cerrada.getEstado()).isEqualTo(LecturaCvPerfil.NO_LEGIBLE);
    }

    @Test
    @DisplayName("Y tampoco se le puede marcar ilegible por segunda vez")
    void laCerradaNoSeMarcaDosVeces() {
        LecturaCvPerfil lista = enCurso();
        lista.setEstado(LecturaCvPerfil.LISTA);
        when(lecturas.findById(LECTURA)).thenReturn(Optional.of(lista));

        puente.marcarNoLegible(LECTURA, "se agotaron los intentos");

        // Un fallo que llega tarde no puede tumbar una lectura que salió bien.
        assertThat(lista.getEstado()).isEqualTo(LecturaCvPerfil.LISTA);
        verify(lecturas, never()).save(any());
    }

    @Test
    @DisplayName("Agotados los reintentos, la viva sí se cierra con su motivo")
    void laVivaSeCierraAlAgotarse() {
        LecturaCvPerfil l = enCurso();
        when(lecturas.findById(LECTURA)).thenReturn(Optional.of(l));

        puente.marcarNoLegible(LECTURA, "No se pudo abrir el PDF");

        assertThat(l.getEstado()).isEqualTo(LecturaCvPerfil.NO_LEGIBLE);
        assertThat(l.getMotivo()).isEqualTo("No se pudo abrir el PDF");
        assertThat(l.getTerminadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Una lectura que no existe revienta con su id, no en silencio")
    void laQueNoExisteRevienta() {
        when(lecturas.findById(LECTURA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.insumo(LECTURA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(LECTURA));
    }
}
