package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Novedad;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.PeticionVinculo;
import com.renaser.ai.ai_engine.whatsapp.entity.BajaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.BetaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.TextosDeWhatsapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lo que el bot le pide a EX (V73): AC-07 y AC-13, y los casos límite de la spec. */
@ExtendWith(MockitoExtension.class)
@DisplayName("La integración con el bot: vincular, novedades y bajas")
class ServicioIntegracionWhatsappImplTest {

    private static final String NUMERO = "+51987654321";

    @Mock private VinculoWhatsappRepository vinculos;
    @Mock private AvisoWhatsappRepository avisos;
    @Mock private BajaWhatsappRepository bajas;
    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private EstadoPostulacionRepository estados;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private PersonaRepository personas;
    @Mock private BetaWhatsapp beta;
    @Mock private com.renaser.ai.ai_engine.whatsapp.service.ResultadosPorWhatsapp resultados;

    private ServicioIntegracionWhatsappImpl integracion;

    @BeforeEach
    void montar() {
        integracion = new ServicioIntegracionWhatsappImpl(vinculos, avisos, bajas, postulaciones,
                vacantes, estados, intentos, personas, beta, resultados);
        lenient().when(estados.findAllByOrderByOrden()).thenReturn(List.of(
                EstadoPostulacion.builder().codigo("PERFIL_CALIFICANDO").esFinal(false).build(),
                EstadoPostulacion.builder().codigo("NO_CONTINUA").esFinal(true).build()));
        lenient().when(beta.encendida()).thenReturn(true);
        lenient().when(beta.esDeLaPlataforma(1L)).thenReturn(true);
        lenient().when(personas.findById(33L)).thenReturn(Optional.of(
                Persona.builder().id(33L).nombre("Ana").build()));
        lenient().when(vacantes.findAllById(any())).thenReturn(List.of(Vacante.builder().id(5L)
                .titulo("Asistente Contable").build()));
    }

    private static VinculoWhatsapp anunciado() {
        return VinculoWhatsapp.builder().id(7L).postulacionId(70L).personaId(33L).referencia("pw_ref")
                .telefono(NUMERO).estado(VinculoWhatsapp.PENDIENTE)
                .anuncioCaducaEn(Instant.now().plusSeconds(3600)).build();
    }

    private static Postulacion postulacion(String estado) {
        return Postulacion.builder().id(70L).organizacionId(1L).vacanteId(5L).estadoCodigo(estado).build();
    }

    @Test
    @DisplayName("AC-07 · desde el número anunciado, diga lo que diga, queda vinculado y recibe su novedad")
    void vinculaConCualquierTexto() {
        VinculoWhatsapp anunciado = anunciado();
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of(anunciado));
        when(postulaciones.findById(70L)).thenReturn(Optional.of(postulacion("PERFIL_CALIFICANDO")));
        when(vinculos.activosDelNumero(NUMERO)).thenAnswer(i -> List.of(anunciado));
        when(postulaciones.findAllById(any())).thenReturn(List.of(postulacion("PERFIL_CALIFICANDO")));

        var resultado = integracion.vincular(new PeticionVinculo("51987654321", "wamid.1",
                "lo que sea, borré el mensaje", null));

        assertThat(resultado.estadoHttp()).isEqualTo(200);
        assertThat(anunciado.getEstado()).isEqualTo(VinculoWhatsapp.ACTIVO);
        assertThat(anunciado.getPrimerWamid()).isEqualTo("wamid.1");
        assertThat(resultado.vinculo().nombre()).isEqualTo("Ana");
        assertThat(resultado.vinculo().postulaciones()).extracting(p -> p.postulacionRef())
                .containsExactly("pw_ref");
        // Las mismas palabras que el portal.
        assertThat(resultado.vinculo().novedades()).containsExactly(new Novedad(
                "Asistente Contable", "Estamos calificando tus respuestas",
                "Se juntan tu currículum y tu evaluación en un solo perfil. Suele tardar poco."));
    }

    @Test
    @DisplayName("Es idempotente: repetida, devuelve las mismas postulaciones activas y novedades")
    void vincularEsIdempotente() {
        VinculoWhatsapp anunciado = anunciado();
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of(anunciado));
        when(postulaciones.findById(70L)).thenReturn(Optional.of(postulacion("PERFIL_CALIFICANDO")));
        when(vinculos.activosDelNumero(NUMERO)).thenAnswer(i -> List.of(anunciado));
        when(postulaciones.findAllById(any())).thenReturn(List.of(postulacion("PERFIL_CALIFICANDO")));

        var primera = integracion.vincular(new PeticionVinculo(NUMERO, "wamid.1", "hola", null));
        var repetida = integracion.vincular(new PeticionVinculo(NUMERO, "wamid.1", "hola", null));

        assertThat(repetida.estadoHttp()).isEqualTo(200);
        assertThat(repetida.vinculo()).isEqualTo(primera.vinculo());
        assertThat(anunciado.getPrimerWamid()).isEqualTo("wamid.1");
    }

    @Test
    @DisplayName("El texto del mensaje puede venir vacío (un Flow o un adjunto): vincula igual")
    void textoVacio() {
        VinculoWhatsapp anunciado = anunciado();
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of(anunciado));
        when(postulaciones.findById(70L)).thenReturn(Optional.of(postulacion("PERFIL_CALIFICANDO")));
        when(vinculos.activosDelNumero(NUMERO)).thenAnswer(i -> List.of(anunciado));
        when(postulaciones.findAllById(any())).thenReturn(List.of(postulacion("PERFIL_CALIFICANDO")));

        assertThat(integracion.vincular(new PeticionVinculo(NUMERO, "wamid.9", "", null)).estadoHttp())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Los límites del contrato: nombre y vacante hasta 60, como mucho 8 novedades")
    void losLimites() {
        List<VinculoWhatsapp> nueve = new java.util.ArrayList<>();
        List<Postulacion> suyas = new java.util.ArrayList<>();
        for (long i = 0; i < 9; i++) {
            nueve.add(VinculoWhatsapp.builder().id(100 + i).postulacionId(700 + i).personaId(33L)
                    .referencia("pw_" + i).telefono(NUMERO).estado(VinculoWhatsapp.ACTIVO)
                    .vinculadoEn(Instant.now()).build());
            suyas.add(Postulacion.builder().id(700 + i).organizacionId(1L).vacanteId(5L)
                    .estadoCodigo("PERFIL_CALIFICANDO").build());
        }
        when(vinculos.activosDelNumero(NUMERO)).thenReturn(nueve);
        when(postulaciones.findAllById(any())).thenReturn(suyas);
        when(vacantes.findAllById(any())).thenReturn(List.of(Vacante.builder().id(5L)
                .titulo("Asistente Contable y Administrativo para la Sede Central de Arequipa, turno noche")
                .build()));
        when(personas.findById(33L)).thenReturn(Optional.of(Persona.builder().id(33L)
                .nombre("María de los Ángeles Guadalupe del Carmen Fernández de Córdoba y Ponce").build()));
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of());

        var vinculo = integracion.vincular(new PeticionVinculo(NUMERO, "wamid.10", "hola", null)).vinculo();
        var novedades = integracion.novedades(NUMERO).novedades();

        assertThat(vinculo.postulaciones()).hasSize(9);
        assertThat(vinculo.novedades()).hasSize(8);
        assertThat(novedades).hasSize(8);
        assertThat(vinculo.nombre()).hasSizeLessThanOrEqualTo(60).endsWith("…");
        assertThat(novedades).allSatisfy(n -> {
            assertThat(n.vacante()).hasSizeLessThanOrEqualTo(60).endsWith("…");
            assertThat(n.titulo()).hasSizeLessThanOrEqualTo(80);
            assertThat(n.texto()).hasSizeLessThanOrEqualTo(300);
        });
    }

    @Test
    @DisplayName("AC-07 · desde un número no anunciado: 404 SIN_ANUNCIO con su texto")
    void sinAnuncio() {
        var resultado = integracion.vincular(new PeticionVinculo(NUMERO, "wamid.2", "hola", null));

        assertThat(resultado.estadoHttp()).isEqualTo(404);
        assertThat(resultado.rechazo().codigo()).isEqualTo("SIN_ANUNCIO");
        assertThat(resultado.rechazo().texto()).isEqualTo(TextosDeWhatsapp.SIN_ANUNCIO);
    }

    @Test
    @DisplayName("Con el anuncio caducado (más de 24 h) ya no vincula")
    void anuncioCaducado() {
        VinculoWhatsapp viejo = anunciado();
        viejo.setAnuncioCaducaEn(Instant.now().minusSeconds(1));
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of(viejo));

        var resultado = integracion.vincular(new PeticionVinculo(NUMERO, "wamid.3", "hola", null));

        assertThat(resultado.estadoHttp()).isEqualTo(404);
        assertThat(viejo.getEstado()).isEqualTo(VinculoWhatsapp.PENDIENTE);
    }

    @Test
    @DisplayName("El proceso terminó entre el botón y el mensaje: 409 PROCESO_TERMINADO con el texto del punto 13")
    void procesoTerminado() {
        VinculoWhatsapp anunciado = anunciado();
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of(anunciado));
        when(postulaciones.findById(70L)).thenReturn(Optional.of(postulacion("NO_CONTINUA")));

        var resultado = integracion.vincular(new PeticionVinculo(NUMERO, "wamid.4", "hola", null));

        assertThat(resultado.estadoHttp()).isEqualTo(409);
        assertThat(resultado.rechazo().codigo()).isEqualTo("PROCESO_TERMINADO");
        assertThat(resultado.rechazo().texto()).isEqualTo("No tienes procesos activos con avisos por "
                + "WhatsApp. Puedes ver todo en el portal, en «Mis procesos».");
        assertThat(anunciado.getMotivoCierre()).isEqualTo("PROCESO_TERMINADO");
    }

    @Test
    @DisplayName("Con la beta apagada: 409 BETA_APAGADA y no vincula")
    void betaApagada() {
        when(beta.encendida()).thenReturn(false);

        var resultado = integracion.vincular(new PeticionVinculo(NUMERO, "wamid.5", "hola", null));

        assertThat(resultado.estadoHttp()).isEqualTo(409);
        assertThat(resultado.rechazo().codigo()).isEqualTo("BETA_APAGADA");
    }

    @Test
    @DisplayName("Un número que no es E.164 es un 400, no un número de nadie")
    void numeroQueNoVale() {
        assertThatThrownBy(() -> integracion.novedades("hola"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Sin vínculos activos, las novedades son una lista vacía")
    void sinNovedades() {
        var novedades = integracion.novedades(NUMERO);

        assertThat(novedades.novedades()).isEmpty();
    }

    @Test
    @DisplayName("AC-13 · la baja desvincula todo lo de ese número y lo suprime")
    void laBaja() {
        VinculoWhatsapp activo = anunciado();
        activo.setEstado(VinculoWhatsapp.ACTIVO);
        activo.setVinculadoEn(Instant.now());
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of(activo));

        int desvinculadas = integracion.baja("51987654321");

        assertThat(desvinculadas).isEqualTo(1);
        assertThat(activo.getEstado()).isEqualTo(VinculoWhatsapp.SUPRIMIDO);
        assertThat(activo.getMotivoCierre()).isEqualTo("BAJA");
        // El bot ya lo sabe: no se le revoca de vuelta.
        assertThat(activo.getRevocacionEnviadaEn()).isNotNull();
        ArgumentCaptor<BajaWhatsapp> guardada = ArgumentCaptor.forClass(BajaWhatsapp.class);
        verify(bajas).save(guardada.capture());
        assertThat(guardada.getValue().getTelefono()).isEqualTo(NUMERO);
        // V76 · también lo ya cerrado: sus resultados y sus eventos pendientes.
        verify(resultados).alDarseDeBaja(org.mockito.ArgumentMatchers.eq(NUMERO), any());
    }

    @Test
    @DisplayName("AC-13 · una baja sin nada que desvincular no es un error, y la segunda tampoco")
    void laBajaSinNada() {
        when(bajas.existsById(NUMERO)).thenReturn(false, true);

        assertThat(integracion.baja(NUMERO)).isZero();
        assertThat(integracion.baja(NUMERO)).isZero();
        verify(bajas, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    @DisplayName("Las tres rutas: un teléfono vacío o que no es E.164 es un 400")
    void telefonosQueNoValen() {
        assertThatThrownBy(() -> integracion.vincular(new PeticionVinculo("", "w", "hola", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> integracion.novedades(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> integracion.baja("12")).isInstanceOf(IllegalArgumentException.class);
    }
}
