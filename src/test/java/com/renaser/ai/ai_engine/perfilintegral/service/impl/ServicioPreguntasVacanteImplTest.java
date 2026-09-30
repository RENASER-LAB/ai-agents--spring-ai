package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambiarPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CorregirInstrucciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El editor de preguntas propias: lo que se cambia con candidatos dentro")
class ServicioPreguntasVacanteImplTest {

    private static final long ORGANIZACION = 2L;
    private static final long VACANTE = 40L;
    private static final long PUBLICADA = 70L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private Permisos permisos;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private PostulacionRepository postulaciones;
    @Mock private EvaluacionRepository evaluaciones;
    @Mock private RespuestaRepository respuestas;
    @Mock private NotaRespuestaRepository notasRespuesta;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private CriterioBancoRepository criteriosBanco;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private PropuestaPreguntasRepository propuestas;
    @Mock private CalificacionPorPuntos porPuntos;
    @Mock private PuenteCalificacionIa puente;
    @Mock private ColaCalificacionIa cola;
    @Mock private ServicioAuditoria auditoria;
    @InjectMocks private ServicioPreguntasVacanteImpl servicio;

    private final Vacante vacante = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
            .estado("PUBLICADA").origenPreguntas("VACANTE").aplicaEvaluacion(true).build();
    private final VersionBanco publicada = VersionBanco.builder().id(PUBLICADA).estado("PUBLICADA")
            .metodoCalificacion("PUNTOS").versionGuia(1).guiaCalificacion("Guía vieja").build();

    @BeforeEach
    void armar() {
        lenient().when(alcance.laVacanteVisible(eq(QUIEN), eq(VACANTE), any())).thenReturn(vacante);
        lenient().when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA"))
                .thenReturn(Optional.of(publicada));
    }

    /**
     * Tres candidatos que entregaron: los dos primeros con una abierta calificada por la IA,
     * el tercero con la suya ajustada a mano (esa no se recalifica).
     */
    private void tresCalificados() {
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(List.of(
                Postulacion.builder().id(1L).evaluacionId(11L).build(),
                Postulacion.builder().id(2L).evaluacionId(12L).build(),
                Postulacion.builder().id(3L).evaluacionId(13L).build()));
        when(evaluaciones.findAllById(anyList())).thenReturn(List.of(
                Evaluacion.builder().id(11L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build(),
                Evaluacion.builder().id(12L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build(),
                Evaluacion.builder().id(13L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build()));
        when(respuestas.findByEvaluacionIdIn(any())).thenReturn(List.of(
                Respuesta.builder().id(101L).evaluacionId(11L).build(),
                Respuesta.builder().id(102L).evaluacionId(12L).build(),
                Respuesta.builder().id(103L).evaluacionId(13L).build()));
        when(notasRespuesta.findByRespuestaIdIn(anyList())).thenReturn(List.of(
                NotaRespuesta.builder().respuestaId(101L).versionGuia(1).build(),
                NotaRespuesta.builder().respuestaId(102L).versionGuia(1).build(),
                NotaRespuesta.builder().respuestaId(103L).versionGuia(1).ajustadaPorUsuarioId(9L).build()));
    }

    @Test
    @DisplayName("Sin saldo de IA, cambiar la guía no guarda nada y lo dice (AC-29)")
    void sinSaldoNoSeGuardaNada() {
        tresCalificados();
        when(cola.recalificacionDe(anyList())).thenReturn(Map.of());
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION))
                .thenReturn("La IA no tiene saldo: la empresa llegó a su tope mensual de IA.");

        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE,
                new CorregirInstrucciones("Guía nueva", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene saldo").hasMessageContaining("No se guardó");
        assertThat(publicada.getGuiaCalificacion()).isEqualTo("Guía vieja");
        assertThat(publicada.getVersionGuia()).isEqualTo(1);
        verify(versionesBanco, never()).save(any());
        verify(cola, never()).recalificar(anyLong());
    }

    @Test
    @DisplayName("Cambiar la guía sube su número, deja rastro y recalifica a quien tiene nota de la IA (AC-28, AC-30)")
    void cambiarLaGuiaRecalifica() {
        tresCalificados();
        when(cola.recalificacionDe(anyList())).thenReturn(Map.of());
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn(null);
        when(cola.recalificar(anyLong())).thenReturn(true);

        var aplicado = servicio.corregirInstrucciones(QUIEN, VACANTE,
                new CorregirInstrucciones("Guía nueva", List.of(), List.of()));

        assertThat(aplicado.personas()).isEqualTo(2);
        assertThat(publicada.getVersionGuia()).isEqualTo(2);
        assertThat(publicada.getGuiaCalificacion()).isEqualTo("Guía nueva");
        verify(cola).recalificar(1L);
        verify(cola).recalificar(2L);
        verify(cola, never()).recalificar(3L);
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("corregir_instrucciones_ia"),
                eq("version_banco"), eq(PUBLICADA),
                org.mockito.ArgumentMatchers.argThat(antes -> antes != null
                        && antes.toString().contains("Guía vieja")),
                any(), any());
    }

    @Test
    @DisplayName("Con la IA apagada, cambiar la guía tampoco guarda nada (AC-29)")
    void conLaIaApagadaNoSeGuardaNada() {
        tresCalificados();
        when(cola.recalificacionDe(anyList())).thenReturn(Map.of());
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION))
                .thenReturn("La IA está apagada en este sistema: ahora no se puede usar.");

        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE,
                new CorregirInstrucciones("Guía nueva", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("apagada").hasMessageContaining("No se guardó");
        assertThat(publicada.getGuiaCalificacion()).isEqualTo("Guía vieja");
        assertThat(publicada.getVersionGuia()).isEqualTo(1);
        verify(cola, never()).recalificar(anyLong());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Tres calificados, uno con una abierta ajustada y otra de la IA: se encola a los tres (AC-28)")
    void losTresSeEncolanAunqueUnoTengaUnaAjustada() {
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(List.of(
                Postulacion.builder().id(1L).evaluacionId(11L).build(),
                Postulacion.builder().id(2L).evaluacionId(12L).build(),
                Postulacion.builder().id(3L).evaluacionId(13L).build()));
        when(evaluaciones.findAllById(anyList())).thenReturn(List.of(
                Evaluacion.builder().id(11L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build(),
                Evaluacion.builder().id(12L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build(),
                Evaluacion.builder().id(13L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build()));
        when(respuestas.findByEvaluacionIdIn(any())).thenReturn(List.of(
                Respuesta.builder().id(101L).evaluacionId(11L).build(),
                Respuesta.builder().id(102L).evaluacionId(12L).build(),
                Respuesta.builder().id(103L).evaluacionId(13L).build(),
                Respuesta.builder().id(104L).evaluacionId(13L).build()));
        when(notasRespuesta.findByRespuestaIdIn(anyList())).thenReturn(List.of(
                NotaRespuesta.builder().respuestaId(101L).versionGuia(1).build(),
                NotaRespuesta.builder().respuestaId(102L).versionGuia(1).build(),
                NotaRespuesta.builder().respuestaId(103L).versionGuia(1).ajustadaPorUsuarioId(9L).build(),
                NotaRespuesta.builder().respuestaId(104L).versionGuia(1).build()));
        when(cola.recalificacionDe(anyList())).thenReturn(Map.of());
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn(null);
        when(cola.recalificar(anyLong())).thenReturn(true);

        var aplicado = servicio.corregirInstrucciones(QUIEN, VACANTE,
                new CorregirInstrucciones("Guía nueva", List.of(), List.of()));

        assertThat(aplicado.personas()).isEqualTo(3);
        verify(cola).recalificar(1L);
        verify(cola).recalificar(2L);
        verify(cola).recalificar(3L);
        // Nadie se mueve ni se recalcula aquí: cada uno conserva su nota hasta que llega la nueva
        verify(puente, never()).recalcularSinMover(anyLong());
    }

    @Test
    @DisplayName("La tercera falló con 402: las dos primeras al día, ella pendiente con el motivo, "
            + "y «Reintentar» solo la encola a ella (AC-29)")
    void laQueFalloQuedaPendienteYSoloElla() {
        publicada.setVersionGuia(2);
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(List.of(
                Postulacion.builder().id(1L).evaluacionId(11L).build(),
                Postulacion.builder().id(2L).evaluacionId(12L).build(),
                Postulacion.builder().id(3L).evaluacionId(13L).build()));
        when(evaluaciones.findAllById(anyList())).thenReturn(List.of(
                Evaluacion.builder().id(11L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build(),
                Evaluacion.builder().id(12L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build(),
                Evaluacion.builder().id(13L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build()));
        when(respuestas.findByEvaluacionIdIn(any())).thenReturn(List.of(
                Respuesta.builder().id(101L).evaluacionId(11L).build(),
                Respuesta.builder().id(102L).evaluacionId(12L).build(),
                Respuesta.builder().id(103L).evaluacionId(13L).build()));
        // Las dos primeras ya tienen la nota de la guía 2; la tercera conserva la de la 1
        when(notasRespuesta.findByRespuestaIdIn(anyList())).thenReturn(List.of(
                NotaRespuesta.builder().respuestaId(101L).versionGuia(2).build(),
                NotaRespuesta.builder().respuestaId(102L).versionGuia(2).build(),
                NotaRespuesta.builder().respuestaId(103L).versionGuia(1).build()));
        String motivo = "El proveedor contestó 402: la cuenta no tiene saldo.";
        when(cola.recalificacionDe(anyList())).thenReturn(Map.of(
                3L, new ColaCalificacionIa.Seguimiento("DETENIDA", motivo)));
        when(porPuntos.calcular(PUBLICADA, null)).thenReturn(new CalificacionPorPuntos.Resultado(
                publicada, List.of(), List.of(), BigDecimal.ZERO, true));

        var recalificacion = servicio.ver(QUIEN, VACANTE).recalificacion();
        assertThat(recalificacion.conNota()).isEqualTo(3);
        assertThat(recalificacion.alDia()).isEqualTo(2);
        assertThat(recalificacion.recalificando()).isZero();
        assertThat(recalificacion.pendientes()).isEqualTo(1);
        assertThat(recalificacion.motivos()).containsExactly(motivo);

        when(cola.recalificar(3L)).thenReturn(true);
        var reintento = servicio.reintentarRecalificacion(QUIEN, VACANTE);
        assertThat(reintento.personas()).isEqualTo(1);
        assertThat(reintento.motivo()).isNull();
        verify(cola).recalificar(3L);
        verify(cola, never()).recalificar(1L);
        verify(cola, never()).recalificar(2L);
    }

    /** Una pendiente de recalificar: la tercera conserva la nota de la guía 1 y la vigente es la 2. */
    private void unaPendienteDeRecalificar() {
        publicada.setVersionGuia(2);
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(List.of(
                Postulacion.builder().id(1L).evaluacionId(11L).build(),
                Postulacion.builder().id(3L).evaluacionId(13L).build()));
        when(evaluaciones.findAllById(anyList())).thenReturn(List.of(
                Evaluacion.builder().id(11L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build(),
                Evaluacion.builder().id(13L).versionBancoNivelId(PUBLICADA).estado("TERMINADA").build()));
        when(respuestas.findByEvaluacionIdIn(any())).thenReturn(List.of(
                Respuesta.builder().id(101L).evaluacionId(11L).build(),
                Respuesta.builder().id(103L).evaluacionId(13L).build()));
        when(notasRespuesta.findByRespuestaIdIn(anyList())).thenReturn(List.of(
                NotaRespuesta.builder().respuestaId(101L).versionGuia(2).build(),
                NotaRespuesta.builder().respuestaId(103L).versionGuia(1).build()));
        when(cola.recalificacionDe(anyList())).thenReturn(Map.of(
                3L, new ColaCalificacionIa.Seguimiento("DETENIDA", "El proveedor contestó 402.")));
    }

    @Test
    @DisplayName("QA-PP-05: «Reintentar» con la IA apagada no encola a nadie y dice por qué, "
            + "sin dar a entender que no quedaba nadie (AC-29)")
    void reintentarConLaIaApagadaLoDice() {
        unaPendienteDeRecalificar();
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION))
                .thenReturn("La IA está apagada en este sistema: ahora no se puede usar.");

        var reintento = servicio.reintentarRecalificacion(QUIEN, VACANTE);

        assertThat(reintento.personas()).isZero();
        assertThat(reintento.motivo()).contains("apagada")
                .contains("1 persona sigue con la nota de la guía anterior");
        verify(cola, never()).recalificar(anyLong());
    }

    @Test
    @DisplayName("QA-PP-05: «Reintentar» en el tope de IA tampoco encola, y lo dice (AC-29)")
    void reintentarEnElTopeLoDice() {
        unaPendienteDeRecalificar();
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION))
                .thenReturn("La IA no tiene saldo: la empresa llegó a su tope mensual de IA.");

        var reintento = servicio.reintentarRecalificacion(QUIEN, VACANTE);

        assertThat(reintento.personas()).isZero();
        assertThat(reintento.motivo()).contains("tope").contains("No se volvió a pedir");
        verify(cola, never()).recalificar(anyLong());
    }

    @Test
    @DisplayName("QA-PP-05: sin nadie pendiente, «Reintentar» no pregunta por la IA ni da motivo")
    void reintentarSinPendientesNoDaMotivo() {
        tresCalificados();
        when(cola.recalificacionDe(anyList())).thenReturn(Map.of());

        var reintento = servicio.reintentarRecalificacion(QUIEN, VACANTE);

        assertThat(reintento.personas()).isZero();
        assertThat(reintento.motivo()).isNull();
        verify(cola, never()).porQueNoSePuedeUsarLaIa(any());
        verify(cola, never()).recalificar(anyLong());
    }

    @Test
    @DisplayName("QA-PP-05: si la cola no admite a alguien, se dice cuántos quedaron fuera")
    void reintentarQueNoEncolaATodosLoDice() {
        unaPendienteDeRecalificar();
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn(null);
        when(cola.recalificar(3L)).thenReturn(false);

        var reintento = servicio.reintentarRecalificacion(QUIEN, VACANTE);

        assertThat(reintento.personas()).isZero();
        assertThat(reintento.motivo()).isEqualTo(
                "1 persona ya tenía una recalificación en marcha: no se pidió otra.");
    }

    @Test
    @DisplayName("Con una recalificación en curso no se cambian ni la guía ni los puntos (AC-33)")
    void conRecalificacionEnCursoNoSeCambia() {
        tresCalificados();
        when(cola.recalificacionDe(anyList())).thenReturn(Map.of(
                1L, new ColaCalificacionIa.Seguimiento("EN_CURSO", null)));

        assertThatThrownBy(() -> servicio.cambiarPuntos(QUIEN, VACANTE, new CambiarPuntos(List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("en curso");
        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE,
                new CorregirInstrucciones("x", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("en curso");
    }

    @Test
    @DisplayName("Copiar de una vacante de otra empresa contesta 404 y no crea nada (AC-15)")
    void copiarDeOtraEmpresaEs404() {
        vacante.setEstado("BORRADOR");
        when(vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(99L, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.copiar(QUIEN, VACANTE, new CopiarDeOtraVacante(99L)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> servicio.vistaPrevia(QUIEN, VACANTE, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(versionesBanco, never()).saveAndFlush(any());
        verify(preguntas, never()).save(any());
    }

    @Test
    @DisplayName("Con el borrador en 100, pedir recomendaciones no encola nada (AC-14b)")
    void conCienNoQuedaSitio() {
        vacante.setEstado("BORRADOR");
        VersionBanco borrador = VersionBanco.builder().id(71L).estado("BORRADOR").build();
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
        Pregunta cien = Pregunta.builder().id(1L).tipo("ABIERTA").puntos(100).build();
        when(porPuntos.calcular(71L, null)).thenReturn(new CalificacionPorPuntos.Resultado(borrador,
                List.of(), List.of(CalificacionPorPuntos.calcularPregunta(cien, List.of(), null, null)),
                BigDecimal.ZERO, true));

        assertThatThrownBy(() -> servicio.pedirRecomendaciones(QUIEN, VACANTE, new PedirRecomendaciones(null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no queda sitio");
        verify(cola, never()).encolarRecomendador(any(), any());
        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("Sin cupo de IA, pedir recomendaciones dice que no se encoló, sin hablar de error")
    void sinCupoNoSeEncola() {
        vacante.setEstado("BORRADOR");
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION))
                .thenReturn("La IA no tiene saldo: la empresa llegó a su tope mensual de IA.");

        var pedida = servicio.pedirRecomendaciones(QUIEN, VACANTE, new PedirRecomendaciones("énfasis en Excel"));

        assertThat(pedida.encolada()).isFalse();
        assertThat(pedida.mensaje()).contains("no tiene saldo");
        verify(cola, never()).encolarRecomendador(any(), any());
    }

    @Test
    @DisplayName("Se ofrecen la activa y la archivada, no la eliminada; el nivel solo filtra (AC-15b, AC-15d)")
    void lasQueSeOfrecenParaCopiar() {
        when(versionesBanco.propiasPublicadasDe(ORGANIZACION)).thenReturn(List.of(
                VersionBanco.builder().id(81L).vacanteId(1L).build(),
                VersionBanco.builder().id(82L).vacanteId(2L).build(),
                VersionBanco.builder().id(83L).vacanteId(3L).build()));
        when(vacantes.findAllById(any())).thenReturn(List.of(
                Vacante.builder().id(1L).organizacionId(ORGANIZACION).titulo("Asistente contable")
                        .estado("PUBLICADA").puestoId(11L)
                        .publicadaEn(java.time.Instant.parse("2026-08-01T00:00:00Z")).build(),
                Vacante.builder().id(2L).organizacionId(ORGANIZACION).titulo("Jefe de contabilidad")
                        .estado("CERRADA").puestoId(12L)
                        .archivadaEn(java.time.Instant.parse("2026-06-01T00:00:00Z"))
                        .publicadaEn(java.time.Instant.parse("2026-03-01T00:00:00Z")).build(),
                Vacante.builder().id(3L).organizacionId(ORGANIZACION).titulo("Creada por error")
                        .estado("BORRADOR").puestoId(11L)
                        .eliminadaEn(java.time.Instant.parse("2026-09-01T00:00:00Z")).build()));
        when(puestos.findAllById(any())).thenReturn(List.of(
                com.renaser.ai.ai_engine.vacante.entity.Puesto.builder().id(11L)
                        .organizacionId(ORGANIZACION).nivelPuestoCodigo("EJECUCION").build(),
                com.renaser.ai.ai_engine.vacante.entity.Puesto.builder().id(12L)
                        .organizacionId(ORGANIZACION).nivelPuestoCodigo("DIRECCION").build()));
        when(criteriosBanco.findByVersionBancoIdIn(anyList())).thenReturn(List.of());
        when(preguntas.findByVersionBancoIdIn(anyList())).thenReturn(List.of());

        var todas = servicio.copiables(QUIEN, VACANTE, null, null);
        assertThat(todas).extracting(c -> c.vacanteId()).containsExactly(1L, 2L);
        assertThat(todas).extracting(c -> c.estado()).containsExactly("ACTIVA", "ARCHIVADA");

        assertThat(servicio.copiables(QUIEN, VACANTE, null, "EJECUCION"))
                .extracting(c -> c.vacanteId()).containsExactly(1L);
        assertThat(servicio.copiables(QUIEN, VACANTE, "jefe", null))
                .extracting(c -> c.titulo()).containsExactly("Jefe de contabilidad");
    }

    @Test
    @DisplayName("Reescalar en proporción: 12 de 20 pasa a 9 de 15; de un máximo 0 no hay proporción")
    void reescalar() {
        assertThat(ServicioPreguntasVacanteImpl.escalar(new BigDecimal("12"), 20, 15))
                .isEqualByComparingTo("9");
        assertThat(ServicioPreguntasVacanteImpl.escalar(new BigDecimal("16"), 20, 15))
                .isEqualByComparingTo("12");
        assertThat(ServicioPreguntasVacanteImpl.escalar(new BigDecimal("10"), 57, 62))
                .isEqualByComparingTo("10.88");
        assertThat(ServicioPreguntasVacanteImpl.escalar(new BigDecimal("3"), 0, 10))
                .isEqualByComparingTo("0");
    }
}
