package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.service.DireccionDelCandidato;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El aviso que sale al cambiar de estado, y sobre todo el de la prueba del puesto.
 *
 * <p>Ese es el unico momento del recorrido en que el candidato necesita algo mas que entrar:
 * tiene que leer un enunciado y entregar un trabajo. Los primeros veintidos correos se
 * mandaron publicando una plantilla por puesto y moviendo a la gente en medio; funciono
 * porque nadie mas se movio en esos minutos, y quien se hubiera movido habria recibido el
 * enunciado de otra vacante sin que nada avisara. Estas pruebas son las que impiden que eso
 * pueda volver a pasar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("El aviso al candidato")
class MaquinaEstadosAvisoTest {

    @Mock private EstadoPostulacionRepository estados;
    @Mock private PostulacionRepository postulaciones;
    @Mock private TransicionEstadoRepository transiciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;
    @Mock private VacanteRepository vacantes;
    @Mock private ServicioAuditoria auditoria;
    @Mock private ServicioCorreo correo;
    @Mock private DireccionDelCandidato direcciones;
    @Mock private ServicioEnlaceAcceso enlaces;
    @Mock private VersionPlantillaPruebaRepository versionesDePrueba;
    @Mock private ServicioParametros parametros;

    private MaquinaEstados maquina;

    private static final String PDF_ARQ = "https://ejemplo.test/arquitecto.pdf";
    private static final String PDF_CIVIL = "https://ejemplo.test/civil.pdf";

    @BeforeEach
    void montar() {
        maquina = new MaquinaEstados(estados, postulaciones, transiciones, usuarios, personas,
                vacantes, auditoria, correo, direcciones, enlaces, versionesDePrueba, parametros);

        when(usuarios.findById(anyLong())).thenReturn(Optional.of(usuarioCon(7L)));
        when(personas.findById(anyLong())).thenReturn(Optional.of(personaCon("Ana")));
        when(direcciones.de(any(), anyLong())).thenReturn("ana@ejemplo.test");
        when(enlaces.generarEnlace(anyLong()))
                .thenReturn(new ServicioEnlaceAcceso.EnlaceGenerado("https://portal.test/acceso?token=x", null));
        when(parametros.texto(anyLong(), eq("whatsapp_evidencia"), anyString())).thenReturn("982255360");
    }

    private Usuario usuarioCon(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setPersonaId(id);
        return u;
    }

    private Persona personaCon(String nombre) {
        Persona p = new Persona();
        p.setNombre(nombre);
        return p;
    }

    private EstadoPostulacion estado(String codigo, String esperaA) {
        return EstadoPostulacion.builder()
                .codigo(codigo).nombre(codigo).esperaA(esperaA).esFinal(false).build();
    }

    /** Deja lista una vacante con su prueba, y devuelve la postulacion que se va a mover. */
    private Postulacion vacanteCon(Long vacanteId, Long versionId, String urlConsigna, Integer dias) {
        when(vacantes.findById(vacanteId)).thenReturn(Optional.of(
                Vacante.builder().id(vacanteId).titulo("Puesto " + vacanteId)
                        .versionPlantillaPruebaId(versionId).build()));
        when(versionesDePrueba.findById(versionId)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(versionId)
                        .urlConsigna(urlConsigna).plazoDias(dias).build()));
        return Postulacion.builder().id(100L + vacanteId).organizacionId(1L).usuarioId(7L)
                .vacanteId(vacanteId).uuid(UUID.randomUUID())
                .estadoCodigo("PERFIL_POR_CONFIRMAR").build();
    }

    private Map<String, String> avisoAlMover(Postulacion p, String destino, String esperaA) {
        when(estados.findById(destino)).thenReturn(Optional.of(estado(destino, esperaA)));
        when(estados.findById("PERFIL_POR_CONFIRMAR"))
                .thenReturn(Optional.of(estado("PERFIL_POR_CONFIRMAR", "TALENTO")));

        maquina.transicionar(p, destino, null, null, true, false, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        // atLeastOnce y no una sola: hay pruebas que mueven a dos candidatos seguidos.
        // El captor guarda todas y getValue() devuelve la ultima, que es la que se mira.
        verify(correo, atLeastOnce())
                .enviar(anyLong(), anyLong(), anyString(), anyString(), variables.capture());
        return variables.getValue();
    }

    private String plantillaAlMover(Postulacion p, String destino, String esperaA) {
        when(estados.findById(destino)).thenReturn(Optional.of(estado(destino, esperaA)));
        when(estados.findById("PERFIL_POR_CONFIRMAR"))
                .thenReturn(Optional.of(estado("PERFIL_POR_CONFIRMAR", "TALENTO")));

        maquina.transicionar(p, destino, null, null, true, false, null);

        ArgumentCaptor<String> plantilla = ArgumentCaptor.forClass(String.class);
        verify(correo, atLeastOnce())
                .enviar(anyLong(), anyLong(), anyString(), plantilla.capture(), any());
        return plantilla.getValue();
    }

    @Test
    @DisplayName("al entrar en la prueba usa su plantilla, no la generica")
    void laPruebaTieneAvisoPropio() {
        Postulacion p = vacanteCon(15L, 17L, PDF_ARQ, 3);

        assertThat(plantillaAlMover(p, "PRUEBA_TURNO_CANDIDATO", "CANDIDATO"))
                .isEqualTo("PRUEBA_DISPONIBLE");
    }

    @Test
    @DisplayName("y lleva el enunciado, el plazo y el numero al que mandar la evidencia")
    void llevaLoQueHaceFalta() {
        Postulacion p = vacanteCon(15L, 17L, PDF_ARQ, 3);

        Map<String, String> v = avisoAlMover(p, "PRUEBA_TURNO_CANDIDATO", "CANDIDATO");

        assertThat(v).containsEntry("enlacePrueba", PDF_ARQ)
                     .containsEntry("plazo", "3 dias")
                     .containsEntry("whatsapp", "982255360");
    }

    @Test
    @DisplayName("cada vacante manda el enunciado SUYO")
    void cadaVacanteElSuyo() {
        // Es lo que el apaño de publicar una plantilla por puesto no podia garantizar: con
        // dos candidatos de vacantes distintas moviendose a la vez, uno recibia el PDF del
        // otro y no habia forma de notarlo salvo leyendo el correo enviado.
        assertThat(avisoAlMover(vacanteCon(15L, 17L, PDF_ARQ, 3),
                                "PRUEBA_TURNO_CANDIDATO", "CANDIDATO"))
                .containsEntry("enlacePrueba", PDF_ARQ);

        assertThat(avisoAlMover(vacanteCon(16L, 18L, PDF_CIVIL, 3),
                                "PRUEBA_TURNO_CANDIDATO", "CANDIDATO"))
                .containsEntry("enlacePrueba", PDF_CIVIL);
    }

    @Test
    @DisplayName("sin enunciado publicado el aviso sale igual, con el hueco vacio")
    void sinEnunciadoAvisaIgual() {
        // Un aviso incompleto es malo; no avisarle de que le toca la prueba es peor.
        Postulacion p = vacanteCon(15L, 17L, null, 3);

        assertThat(avisoAlMover(p, "PRUEBA_TURNO_CANDIDATO", "CANDIDATO"))
                .containsEntry("enlacePrueba", "");
    }

    @Test
    @DisplayName("una prueba con reloj dice minutos y no dias")
    void laCronometradaDiceMinutos() {
        when(vacantes.findById(15L)).thenReturn(Optional.of(
                Vacante.builder().id(15L).titulo("Puesto").versionPlantillaPruebaId(17L).build()));
        when(versionesDePrueba.findById(17L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(17L).urlConsigna(PDF_ARQ)
                        .duracionMinutos(90).build()));
        Postulacion p = Postulacion.builder().id(115L).organizacionId(1L).usuarioId(7L)
                .vacanteId(15L).uuid(UUID.randomUUID()).estadoCodigo("PERFIL_POR_CONFIRMAR").build();

        assertThat(avisoAlMover(p, "PRUEBA_TURNO_CANDIDATO", "CANDIDATO"))
                .containsEntry("plazo", "90 minutos");
    }

    @Test
    @DisplayName("las demas etapas siguen con el aviso de siempre")
    void lasDemasNoCambian() {
        Postulacion p = vacanteCon(15L, 17L, PDF_ARQ, 3);

        assertThat(plantillaAlMover(p, "PERFIL_TURNO_CANDIDATO", "CANDIDATO"))
                .isEqualTo("POSTULACION_AVANZA");
    }
}
