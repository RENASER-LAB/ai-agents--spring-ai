package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DesgloseEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.ResultadoAlineacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ResultadoAlineacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion.ResumenCerrado;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El desglose de la evaluación del banco")
class ServicioDesgloseEvaluacionImplTest {

    private static final long POSTULACION = 5L;
    private static final long EVALUACION = 40L;
    private static final long ORGANIZACION = 1L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private EvaluacionRepository evaluaciones;
    @Mock private RespuestaRepository respuestas;
    @Mock private PreguntaRepository preguntas;
    @Mock private NotaRespuestaRepository notasRespuesta;
    @Mock private ResultadoAlineacionRepository alineaciones;
    @Mock private ServicioCalificacion calificacion;
    @Mock private Permisos permisos;
    // Devuelve null por defecto = banco sin método CRITERIOS: el camino clásico de estos tests.
    @Mock private CalificacionCriterios calificacionCriterios;
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository notasEtapa;

    @InjectMocks
    private ServicioDesgloseEvaluacionImpl servicio;

    private ContextoUsuario quien;

    @BeforeEach
    void quienMira() {
        quien = new ContextoUsuario(10L, 20L, ORGANIZACION, "EQUIPO", List.of(1L),
                Map.of("ver_respuestas_evaluacion", "TODO"));
        lenient().when(permisos.alcanceDe(anyString()))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, 10L));
    }

    @Test
    void sinEvaluacionAsignadaTodoVieneVacioYNoEsUnError() {
        // La vacante pudo publicarse con la evaluación del banco apagada.
        conPostulacion(null);

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.notaEvaluacion()).isNull();
        assertThat(d.abiertas()).isEmpty();
        assertThat(d.alineacion()).isEmpty();
        assertThat(d.cerradas().preguntas()).isZero();
    }

    @Test
    void unaRespuestaConNotaSaleConSuExplicacionYSuEvidencia() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("80", 4);
        respuestaAbierta(1L, 7L, "Cuenta una vez que priorizaste", "V",
                "Elegí el cliente grande y avisé al chico");
        when(notasRespuesta.findByRespuestaIdIn(List.of(1L))).thenReturn(List.of(
                NotaRespuesta.builder().respuestaId(1L).puntaje(new BigDecimal("3"))
                        .explicacion("Prioriza con criterio y comunica")
                        .evidenciaCitada("«avisé al chico»")
                        .confianza(new BigDecimal("0.9")).build()));

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.abiertas()).hasSize(1);
        assertThat(d.abiertas().get(0).puntaje()).isEqualByComparingTo("3");
        assertThat(d.abiertas().get(0).evidenciaCitada()).contains("avisé");
        assertThat(d.abiertas().get(0).motivoAjuste()).isNull();
    }

    @Test
    void unaRespuestaSinCalificarSaleSinNotaPeroSale() {
        // Respondida y pendiente es distinto de no estar: si desapareciera, «3 de 5
        // calificadas» no tendría dónde leerse.
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        respuestaAbierta(1L, 7L, "Pregunta", "V", "Mi respuesta");
        when(notasRespuesta.findByRespuestaIdIn(List.of(1L))).thenReturn(List.of());

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.abiertas()).hasSize(1);
        assertThat(d.abiertas().get(0).respuesta()).isEqualTo("Mi respuesta");
        assertThat(d.abiertas().get(0).puntaje()).isNull();
        assertThat(d.notaEvaluacion()).isNull();
    }

    @Test
    void laNotaPonderaLoCerradoYLoAbiertoPorSusPreguntas() {
        // 4 cerradas a 80 y 1 abierta con 4/4 (=100): (80·4 + 100·1) / 5 = 84.
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("80", 4);
        respuestaAbierta(1L, 7L, "Pregunta", "V", "Respuesta redonda");
        when(notasRespuesta.findByRespuestaIdIn(List.of(1L))).thenReturn(List.of(
                NotaRespuesta.builder().respuestaId(1L).puntaje(new BigDecimal("4")).build()));

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.notaEvaluacion()).isEqualByComparingTo("84.00");
    }

    @Test
    void unaPreguntaNoPuntuableNoEntraEnElDesglose() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                Respuesta.builder().id(1L).evaluacionId(EVALUACION).preguntaId(7L)
                        .texto("Datos de contacto").build()));
        when(preguntas.findByIdIn(List.of(7L))).thenReturn(List.of(
                Pregunta.builder().id(7L).enunciado("Tu correo").tipo("DATO")
                        .esPuntuable(false).build()));
        lenient().when(notasRespuesta.findByRespuestaIdIn(anyList())).thenReturn(List.of());

        assertThat(servicio.ver(quien, POSTULACION).abiertas()).isEmpty();
    }

    @Test
    void losSemaforosDeAlineacionSalenTalCualSeGuardaron() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of());
        when(alineaciones.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                ResultadoAlineacion.builder().bloque("DINERO").semaforo("AMBAR")
                        .explicacion("Gasta sin registrar").build()));

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.alineacion()).hasSize(1);
        assertThat(d.alineacion().get(0).semaforo()).isEqualTo("AMBAR");
    }

    @Test
    void unaPostulacionDeOtraOrganizacionNoExiste() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.ver(quien, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fueraDeSusVacantesTampocoExiste() {
        // El mismo control que el resto del panel: para quien solo ve sus vacantes,
        // una postulación ajena no es un 403 que confirma que existe — es un 404.
        conPostulacion(EVALUACION);
        when(permisos.alcanceDe("ver_respuestas_evaluacion"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, 10L));
        when(vacantes.findById(3L)).thenReturn(Optional.of(
                Vacante.builder().id(3L).responsableUsuarioId(99L).build()));

        assertThatThrownBy(() -> servicio.ver(quien, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- Los dobles ----------

    @Test
    void enUnBancoCriteriosLaNotaEsElIndiceDeLaEtapa() {
        // Promediar los 0–4 a partes iguales enseñaría una nota distinta de la que decide:
        // sin peso de ítem y sin pilares. La que se enseña es la misma con la que se ranquea.
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of());
        when(calificacionCriterios.metodoDe(org.mockito.ArgumentMatchers.any()))
                .thenReturn("CRITERIOS");
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(POSTULACION, "PERFIL_INTEGRAL"))
                .thenReturn(Optional.of(com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa
                        .builder().puntaje(new BigDecimal("62.50")).build()));

        assertThat(servicio.ver(quien, POSTULACION).notaEvaluacion())
                .isEqualByComparingTo(new BigDecimal("62.50"));
    }

    @Test
    void enUnBancoCriteriosSinEtapaCalculadaLaNotaVieneVacia() {
        // El evaluador aún no terminó: sin nota es sin nota, no un promedio provisional.
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of());
        when(calificacionCriterios.metodoDe(org.mockito.ArgumentMatchers.any()))
                .thenReturn("CRITERIOS");
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(POSTULACION, "PERFIL_INTEGRAL"))
                .thenReturn(Optional.empty());

        assertThat(servicio.ver(quien, POSTULACION).notaEvaluacion()).isNull();
    }

    private void conPostulacion(Long evaluacionId) {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(3L)
                        .evaluacionId(evaluacionId).build()));
    }

    private void conEvaluacion(String estado) {
        when(evaluaciones.findById(EVALUACION)).thenReturn(Optional.of(
                Evaluacion.builder().id(EVALUACION).estado(estado).build()));
    }

    private void conCerradas(String nota, int cuantas) {
        when(calificacion.resumenDeLoCerrado(POSTULACION))
                .thenReturn(new ResumenCerrado(new BigDecimal(nota), cuantas));
    }

    private void respuestaAbierta(Long respuestaId, Long preguntaId, String enunciado,
                                  String tipo, String texto) {
        when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                Respuesta.builder().id(respuestaId).evaluacionId(EVALUACION)
                        .preguntaId(preguntaId).texto(texto).build()));
        when(preguntas.findByIdIn(List.of(preguntaId))).thenReturn(List.of(
                Pregunta.builder().id(preguntaId).enunciado(enunciado).tipo(tipo)
                        .esPuntuable(true).build()));
        lenient().when(alineaciones.findByEvaluacionId(EVALUACION)).thenReturn(List.of());
    }
}
