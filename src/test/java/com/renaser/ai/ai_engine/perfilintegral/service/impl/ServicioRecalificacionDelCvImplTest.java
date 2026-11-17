package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.Afectado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.ListaDeAfectados;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.NoEncolada;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.TandaDeRecalificacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.VacanteAfectada;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.HallazgoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El comando que recalifica a los afectados por la fecha o por los años tapados (spec
 * la-ia-sabe-que-dia-es-hoy, AC-17 a AC-19).
 *
 * <p>La vacante abierta tiene dos afectados en curso (uno por la fecha, uno por los años), uno
 * contratado y uno descartado que también lo serían, uno sin nada que arreglar (con una nota
 * ajustada a mano que habla de fechas), uno ya calificado con la fecha de hoy y uno sin retrato.
 * La vacante archivada tiene otro afectado, que no sale.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("La recalificación de los afectados por la fecha")
class ServicioRecalificacionDelCvImplTest {

    private static final long PLATAFORMA = 1L;
    private static final long ABIERTA = 5L;
    private static final long ARCHIVADA = 6L;
    private static final long CRITERIO_CV = 100L;
    private static final String FECHA_MALA =
            "La experiencia de febrero 2026 a la actualidad tiene una fecha futura: no puede ser.";
    private static final String CON_ANIOS = "Analista. Tengo más de 5 años de experiencia en Recursos Humanos.";

    @Mock private OrganizacionRepository organizaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private PostulacionRepository postulaciones;
    @Mock private EstadoPostulacionRepository estados;
    @Mock private PerfilTalentoRepository perfiles;
    @Mock private HallazgoPerfilRepository hallazgos;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private CriterioRepository criterios;
    @Mock private CvRepository cvs;
    @Mock private LoBuscadoEnElCv loBuscado;
    @Mock private ColaCalificacionIa cola;
    @Mock private NombresDeUsuarios nombres;
    @Mock private ServicioAuditoria auditoria;

    private ServicioRecalificacionDelCvImpl servicio;

    private final ContextoUsuario plataforma = quien(PLATAFORMA);

    @BeforeEach
    void escenario() {
        servicio = new ServicioRecalificacionDelCvImpl(organizaciones, vacantes, postulaciones, estados, perfiles,
                hallazgos, notasCriterio, criterios, cvs, new AnonimizadorCv(), loBuscado, cola, nombres, auditoria);
        when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(Organizacion.builder().id(PLATAFORMA).build()));
        when(estados.findAllByOrderByOrden()).thenReturn(List.of(
                estado("PERFIL_POR_CONFIRMAR", false), estado("PRUEBA_PUESTO_EN_CURSO", false),
                estado("CONTRATADO", true), estado("NO_CONTINUA", true), estado("CERRADA", true)));
        when(vacantes.findByEstadoAndEliminadaEnIsNullOrderByPublicadaEnDesc("PUBLICADA")).thenReturn(List.of(
                Vacante.builder().id(ABIERTA).titulo("Analista de Recursos Humanos").build(),
                Vacante.builder().id(ARCHIVADA).titulo("Cajero").archivadaEn(Instant.now()).build()));
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(ABIERTA)).thenReturn(List.of(
                postulacion(11L, "PERFIL_POR_CONFIRMAR"),     // por la fecha
                postulacion(12L, "PRUEBA_PUESTO_EN_CURSO"),   // por los años
                postulacion(13L, "CONTRATADO"),               // afectada, pero contratada
                postulacion(14L, "NO_CONTINUA"),              // afectada, pero descartada
                postulacion(15L, "PERFIL_POR_CONFIRMAR"),     // nada que arreglar
                postulacion(16L, "PERFIL_POR_CONFIRMAR"),     // ya calificada con la fecha de hoy
                postulacion(17L, "PERFIL_POR_CONFIRMAR")));   // sin retrato
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(ARCHIVADA)).thenReturn(List.of(
                postulacion(21L, "PERFIL_POR_CONFIRMAR")));

        when(perfiles.findByPostulacionIdIn(any())).thenReturn(List.of(
                perfil(11L, 911L), perfil(12L, 912L), perfil(13L, 913L), perfil(14L, 914L),
                perfil(15L, 915L), perfil(16L, 916L), perfil(21L, 921L)));
        when(hallazgos.findByPerfilTalentoIdIn(any())).thenReturn(List.of(
                hallazgo(11L, FECHA_MALA), hallazgo(13L, FECHA_MALA), hallazgo(14L, FECHA_MALA),
                hallazgo(15L, "Tiene alto potencial para crecer en el futuro."),
                hallazgo(16L, FECHA_MALA), hallazgo(21L, FECHA_MALA)));
        when(criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden("PERFIL_INTEGRAL"))
                .thenReturn(List.of(Criterio.builder().id(CRITERIO_CV).build()));
        when(notasCriterio.findByPostulacionIdIn(any())).thenReturn(List.of(
                nota(12L, CRITERIO_CV, "Buena experiencia.", 812L, null),
                // Una ajustada a mano no hace afectado a nadie aunque hable de fechas.
                nota(15L, CRITERIO_CV, FECHA_MALA, 815L, 9L),
                // Una nota de otra etapa (otra rúbrica) no cuenta como justificación del CV.
                nota(15L, 999L, FECHA_MALA, 816L, null),
                nota(16L, CRITERIO_CV, FECHA_MALA, 817L, null)));
        when(cvs.findByPostulacionIdIn(any())).thenReturn(List.of(
                Cv.builder().postulacionId(12L).textoExtraido(CON_ANIOS).build(),
                Cv.builder().postulacionId(15L).textoExtraido("Analista de RR. HH. sin cifras.").build(),
                Cv.builder().postulacionId(16L).textoExtraido(CON_ANIOS).build()));
        // La 16 ya se calificó con la fecha de hoy: su retrato y su nota del CV la llevaban.
        when(cola.conLaFechaDeHoy(any())).thenReturn(Set.of(916L, 817L));
        when(cola.recalificacionDelCvDe(any())).thenReturn(Map.of());
        when(nombres.porUsuario(any())).thenReturn(Map.of(1011L, "Rosa Huayta", 1012L, "Juan Pastor"));
        when(loBuscado.vigentes(ABIERTA)).thenReturn(List.of());
    }

    @Test
    @DisplayName("AC-17 · el modo de prueba lista los afectados en curso de vacantes abiertas, sus vacantes, el motivo y el coste, sin llamar a la IA")
    void laListaPrevia() {
        ListaDeAfectados lista = servicio.afectados(plataforma);

        assertThat(lista.afectados()).extracting(Afectado::postulacionId, Afectado::candidato, Afectado::porLaFecha,
                        Afectado::porLosAnios, Afectado::estado, Afectado::llamadas)
                .containsExactly(
                        tuple(11L, "Rosa Huayta", true, false, "PENDIENTE", 3),
                        tuple(12L, "Juan Pastor", false, true, "PENDIENTE", 3));
        assertThat(lista.pendientes()).isEqualTo(2);
        assertThat(lista.porLaFecha()).isEqualTo(1);
        assertThat(lista.porLosAnios()).isEqualTo(1);
        assertThat(lista.llamadasEstimadas()).isEqualTo(6);
        assertThat(lista.vacantes()).containsExactly(new VacanteAfectada(ABIERTA, "Analista de Recursos Humanos", 2));
        verify(cola, never()).recalificarElCv(anyLong(), anyBoolean());
        verify(postulaciones, never()).findByVacanteIdOrderByCreadoEnDesc(ARCHIVADA);
    }

    @Test
    @DisplayName("con «Qué buscar en el CV» la búsqueda suma una llamada por persona")
    void conLaBusqueda() {
        when(loBuscado.vigentes(ABIERTA)).thenReturn(List.of(new LoBuscadoEnElCv.LineaVigente(1L, 1, "Excel", 1)));

        assertThat(servicio.afectados(plataforma).llamadasEstimadas()).isEqualTo(8);
    }

    @Test
    @DisplayName("quien ya está en la cola sale aparte y no cuenta en el coste; quien falló sale para reintentarse")
    void enMarchaYFallidas() {
        when(cola.recalificacionDelCvDe(any())).thenReturn(Map.of(
                11L, new ColaCalificacionIa.Seguimiento("EN_CURSO", null),
                12L, new ColaCalificacionIa.Seguimiento("DETENIDA", "La cuenta del proveedor no tiene saldo (402).")));

        ListaDeAfectados lista = servicio.afectados(plataforma);

        assertThat(lista.pendientes()).isEqualTo(1);
        assertThat(lista.enMarcha()).isEqualTo(1);
        assertThat(lista.fallidas()).isEqualTo(1);
        assertThat(lista.llamadasEstimadas()).isEqualTo(3);
        assertThat(lista.afectados()).extracting(Afectado::estado, Afectado::motivoDelFallo).containsExactly(
                tuple("EN_CURSO", null), tuple("DETENIDA", "La cuenta del proveedor no tiene saldo (402)."));
    }

    @Test
    @DisplayName("AC-18 · al confirmar se reencola a cada afectado con el mecanismo de siempre, y queda en la auditoría quién lo pidió")
    void alConfirmar() {
        when(cola.recalificarElCv(anyLong(), anyBoolean())).thenReturn(true);

        TandaDeRecalificacion tanda = servicio.recalificar(plataforma);

        assertThat(tanda.encoladas()).isEqualTo(2);
        assertThat(tanda.fallidas()).isEmpty();
        assertThat(tanda.llamadasEstimadas()).isEqualTo(6);
        verify(cola).recalificarElCv(11L, false);
        verify(cola).recalificarElCv(12L, false);
        verify(cola, never()).recalificarElCv(eq(13L), anyBoolean());
        verify(cola, never()).recalificarElCv(eq(16L), anyBoolean());
        verify(auditoria).registrar(eq(3L), eq(plataforma), eq("recalificar_cv_por_la_fecha"), eq("postulacion"),
                eq(11L), any(), eq(Map.of("porLaFecha", true, "porLosAnios", false)), any());
        verify(auditoria).registrar(eq(3L), eq(plataforma), eq("recalificar_cv_por_la_fecha"), eq("postulacion"),
                eq(12L), any(), eq(Map.of("porLaFecha", false, "porLosAnios", true)), any());
    }

    @Test
    @DisplayName("AC-19 · si una falla al encolar, conserva lo suyo y sale en la lista de fallidas; las demás siguen")
    void unaFallaYLasDemasSiguen() {
        when(cola.recalificarElCv(11L, false)).thenThrow(new IllegalStateException("RabbitMQ no contesta"));
        when(cola.recalificarElCv(12L, false)).thenReturn(true);

        TandaDeRecalificacion tanda = servicio.recalificar(plataforma);

        assertThat(tanda.encoladas()).isEqualTo(1);
        assertThat(tanda.fallidas()).containsExactly(new NoEncolada(11L, "Rosa Huayta", "RabbitMQ no contesta"));
        verify(cola).recalificarElCv(12L, false);
    }

    @Test
    @DisplayName("confirmar dos veces no encola dos veces: quien ya está en la cola no se vuelve a pedir")
    void dosVeces() {
        when(cola.recalificacionDelCvDe(any())).thenReturn(Map.of(
                11L, new ColaCalificacionIa.Seguimiento("EN_CURSO", null)));
        when(cola.recalificarElCv(12L, false)).thenReturn(false);

        TandaDeRecalificacion tanda = servicio.recalificar(plataforma);

        assertThat(tanda.encoladas()).isZero();
        assertThat(tanda.yaEnMarcha()).isEqualTo(2);
        verify(cola, never()).recalificarElCv(eq(11L), anyBoolean());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("con la IA apagada lo dice como fallida, no como «ya en marcha»")
    void conLaIaApagada() {
        when(cola.recalificarElCv(anyLong(), anyBoolean())).thenReturn(false);
        when(cola.porQueNoSePuedeUsarLaIa(3L)).thenReturn("La IA está apagada en este sistema: ahora no se puede usar.");

        TandaDeRecalificacion tanda = servicio.recalificar(plataforma);

        assertThat(tanda.fallidas()).extracting(NoEncolada::motivo).containsOnly(
                "La IA está apagada en este sistema: ahora no se puede usar.");
    }

    @Test
    @DisplayName("solo la plataforma: cualquier otra empresa recibe un 403 y no se mira nada")
    void soloLaPlataforma() {
        ContextoUsuario deOtraEmpresa = quien(3L);
        assertThatThrownBy(() -> servicio.afectados(deOtraEmpresa)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.recalificar(deOtraEmpresa)).isInstanceOf(AccessDeniedException.class);
        verify(cola, never()).recalificarElCv(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("lo que cuenta como hablar de una fecha que no cuadra")
    void fechasQueNoCuadran() {
        assertThat(ServicioRecalificacionDelCvImpl.hablaDeUnaFechaQueNoCuadra(FECHA_MALA)).isTrue();
        assertThat(ServicioRecalificacionDelCvImpl.hablaDeUnaFechaQueNoCuadra(
                "Fechas imposibles en su historial (2026).")).isTrue();
        assertThat(ServicioRecalificacionDelCvImpl.hablaDeUnaFechaQueNoCuadra(
                "El puesto de julio 2026 aún no ocurre.")).isTrue();
        assertThat(ServicioRecalificacionDelCvImpl.hablaDeUnaFechaQueNoCuadra(
                "Declara un cargo con fecha posterior a hoy.")).isTrue();
        // «Futuro» sin ninguna fecha cerca no es una fecha imposible.
        assertThat(ServicioRecalificacionDelCvImpl.hablaDeUnaFechaQueNoCuadra(
                "Tiene alto potencial para crecer en el futuro.")).isFalse();
        assertThat(ServicioRecalificacionDelCvImpl.hablaDeUnaFechaQueNoCuadra(null)).isFalse();
    }

    // ================================================================= ayudas

    private static ContextoUsuario quien(long organizacionId) {
        return new ContextoUsuario(50L, 51L, organizacionId, "EQUIPO", List.of(),
                Map.of("administrar_plataforma", "TODO"));
    }

    private static EstadoPostulacion estado(String codigo, boolean esFinal) {
        return EstadoPostulacion.builder().codigo(codigo).esFinal(esFinal).build();
    }

    private static Postulacion postulacion(long id, String estado) {
        return Postulacion.builder().id(id).organizacionId(3L).usuarioId(1000L + id).vacanteId(id > 20 ? ARCHIVADA : ABIERTA)
                .estadoCodigo(estado).build();
    }

    private static PerfilTalento perfil(long postulacionId, long ejecucionIaId) {
        return PerfilTalento.builder().id(postulacionId + 400).postulacionId(postulacionId)
                .ejecucionIaId(ejecucionIaId).build();
    }

    private static HallazgoPerfil hallazgo(long postulacionId, String descripcion) {
        return HallazgoPerfil.builder().perfilTalentoId(postulacionId + 400).tipo("RIESGO_CRITICO")
                .descripcion(descripcion).build();
    }

    private static NotaCriterio nota(long postulacionId, long criterioId, String explicacion, long ejecucionIaId,
                                     Long ajustadaPor) {
        return NotaCriterio.builder().postulacionId(postulacionId).criterioId(criterioId).explicacion(explicacion)
                .ejecucionIaId(ejecucionIaId).ajustadaPorUsuarioId(ajustadaPor).build();
    }
}
