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
    @Mock private com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante alcance;
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
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository
            preguntaDimensiones;
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.DimensionRepository
            dimensiones;

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
        noAlcanza();

        assertThatThrownBy(() -> servicio.ver(quien, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fueraDeSusVacantesTampocoExiste() {
        // El mismo control que el resto del panel: para quien solo ve sus vacantes,
        // una postulación ajena no es un 403 que confirma que existe — es un 404. Quién
        // alcanza qué lo decide AlcanceSobreLaVacante, y allí tiene sus pruebas; aquí se
        // comprueba que su no llega hasta arriba sin envolverse en otra cosa.
        noAlcanza();

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

    /** La postulación tal como la devuelve el guardián del alcance. */
    private void conPostulacion(Long evaluacionId) {
        when(alcance.laPostulacionVisible(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(POSTULACION),
                org.mockito.ArgumentMatchers.eq("ver_respuestas_evaluacion")))
                .thenReturn(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(3L)
                        .evaluacionId(evaluacionId).build());
    }

    /** El guardián dice que no, sin distinguir de otra empresa de fuera de alcance. */
    private void noAlcanza() {
        when(alcance.laPostulacionVisible(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(POSTULACION),
                org.mockito.ArgumentMatchers.eq("ver_respuestas_evaluacion")))
                .thenThrow(new ResourceNotFoundException("Postulación", "id", POSTULACION));
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

    // ============ El pilar, las señales y los patrones ============

    /**
     * Cada respuesta dice qué pilar alimenta.
     *
     * <p>Sin esto las abiertas son una lista plana y no se puede saber cuáles sostienen
     * «Iniciativa». El vínculo existe desde la V41; lo que faltaba era enseñarlo.
     */
    @Test
    void cadaRespuestaDiceQuePilarAlimenta() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        respuestaAbierta(1L, 7L, "Cuenta una vez que propusiste algo", "V", "Propuse X");
        when(notasRespuesta.findByRespuestaIdIn(List.of(1L))).thenReturn(List.of());
        conPilar(7L, "PIL_INICIATIVA", "Iniciativa (pilar)");

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.abiertas().get(0).pilarCodigo()).isEqualTo("PIL_INICIATIVA");
        assertThat(d.abiertas().get(0).pilar()).isEqualTo("Iniciativa (pilar)");
    }

    /**
     * ⚠️ Una pregunta puede colgar además de alguna de las 22 dimensiones del catálogo
     * viejo, y esas NO son pilares. Es el mismo filtro que aplica CalificacionCriterios al
     * ponderar: agrupar por una dimensión que allí no pondera diría que una respuesta
     * sostiene algo que no mueve ninguna nota.
     */
    @Test
    void unaDimensionQueNoEsPilarNoAgrupaNada() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        respuestaAbierta(1L, 7L, "Pregunta", "V", "Respuesta");
        when(notasRespuesta.findByRespuestaIdIn(List.of(1L))).thenReturn(List.of());
        conPilar(7L, "ORIENTACION_LOGRO", "Orientación al logro");

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.abiertas().get(0).pilarCodigo()).isNull();
        assertThat(d.abiertas().get(0).pilar()).isNull();
    }

    /**
     * El 0-4 ES el conteo de las cuatro señales, así que se enseñan una a una: «3 de 4» sin
     * decir cuál faltó no se puede discutir con la persona.
     */
    @Test
    void lasCuatroSenalesViajanUnaAUna() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        respuestaAbierta(1L, 7L, "Pregunta", "V", "Respuesta");
        conNotaConSenales(1L, "3", true, true, true, false);
        sinPilares();

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.abiertas().get(0).senales().episodio()).isTrue();
        assertThat(d.abiertas().get(0).senales().autoria()).isTrue();
        assertThat(d.abiertas().get(0).senales().dato()).isTrue();
        assertThat(d.abiertas().get(0).senales().incomodidad()).isFalse();
    }

    /**
     * ⚠️ **El caso que convertiría una evaluación antigua en un cero.** Los bancos
     * anteriores a CAZATALENTOS no medían las señales y sus notas las tienen vacías.
     * Devolver cuatro falsos ahí diría que el candidato no cumplió ninguna.
     */
    @Test
    void unBancoQueNoMedIaLasSenalesNoDevuelveCuatroFalsos() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        respuestaAbierta(1L, 7L, "Pregunta", "V", "Respuesta");
        when(notasRespuesta.findByRespuestaIdIn(List.of(1L))).thenReturn(List.of(
                NotaRespuesta.builder().respuestaId(1L).puntaje(new BigDecimal("3"))
                        .explicacion("Sin señales: banco anterior").build()));
        sinPilares();

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.abiertas().get(0).senales()).isNull();
        assertThat(d.abiertas().get(0).puntaje()).isEqualByComparingTo("3");
        // Y sin señales tampoco hay patrones: no se afirma «nunca se incomodó» sobre un
        // banco que jamás midió la incomodidad.
        assertThat(d.patrones()).isEmpty();
    }

    @Test
    void nuncaSeIncomodoSaleCuandoNingunaRespuestaMarcaLaCuarta() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        dosRespuestas();
        when(notasRespuesta.findByRespuestaIdIn(List.of(1L, 2L))).thenReturn(List.of(
                notaConSenales(1L, "3", true, true, true, false),
                notaConSenales(2L, "3", true, true, true, false)));
        sinPilares();

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.patrones()).extracting(p -> p.codigo()).contains("SIN_INCOMODIDAD");
        assertThat(d.patrones()).noneMatch(p -> p.codigo().equals("SOLO_NOSOTROS"));
    }

    @Test
    void unaSolaRespuestaIncomoda_yElPatronNoSale() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        dosRespuestas();
        when(notasRespuesta.findByRespuestaIdIn(List.of(1L, 2L))).thenReturn(List.of(
                notaConSenales(1L, "4", true, true, true, true),
                notaConSenales(2L, "3", true, true, true, false)));
        sinPilares();

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.patrones()).noneMatch(p -> p.codigo().equals("SIN_INCOMODIDAD"));
    }

    /** La mitad es el corte que nombra la V41, y la frase dice de cuántas sale. */
    @Test
    void soloNosotrosSaleAPartirDeLaMitadSinAutoria() {
        conPostulacion(EVALUACION);
        conEvaluacion("ENTREGADA");
        conCerradas("0", 0);
        dosRespuestas();
        when(notasRespuesta.findByRespuestaIdIn(List.of(1L, 2L))).thenReturn(List.of(
                notaConSenales(1L, "3", true, false, true, true),
                notaConSenales(2L, "4", true, true, true, true)));
        sinPilares();

        DesgloseEvaluacion d = servicio.ver(quien, POSTULACION);

        assertThat(d.patrones()).filteredOn(p -> p.codigo().equals("SOLO_NOSOTROS"))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.deCuantas()).isEqualTo(1);
                    assertThat(p.total()).isEqualTo(2);
                    assertThat(p.descripcion()).contains("1 de 2");
                });
    }

    // ---------- Ayudas ----------

    private void conPilar(Long preguntaId, String codigo, String nombre) {
        when(preguntaDimensiones.findByPreguntaIdIn(List.of(preguntaId))).thenReturn(List.of(
                com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension.builder()
                        .preguntaId(preguntaId).dimensionCodigo(codigo).build()));
        lenient().when(dimensiones.findAllByOrderByOrden()).thenReturn(List.of(
                com.renaser.ai.ai_engine.perfilintegral.entity.Dimension.builder()
                        .codigo(codigo).nombre(nombre).build()));
    }

    private void sinPilares() {
        lenient().when(preguntaDimensiones.findByPreguntaIdIn(anyList())).thenReturn(List.of());
        lenient().when(dimensiones.findAllByOrderByOrden()).thenReturn(List.of());
    }

    private NotaRespuesta notaConSenales(Long respuestaId, String puntaje, boolean c1,
                                         boolean c2, boolean c3, boolean c4) {
        return NotaRespuesta.builder().respuestaId(respuestaId)
                .puntaje(new BigDecimal(puntaje)).explicacion("Lo que vio el agente")
                .c1Episodio(c1).c2Autoria(c2).c3Dato(c3).c4Incomodidad(c4).build();
    }

    private void conNotaConSenales(Long respuestaId, String puntaje, boolean c1, boolean c2,
                                   boolean c3, boolean c4) {
        when(notasRespuesta.findByRespuestaIdIn(List.of(respuestaId)))
                .thenReturn(List.of(notaConSenales(respuestaId, puntaje, c1, c2, c3, c4)));
    }

    private void dosRespuestas() {
        when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                Respuesta.builder().id(1L).evaluacionId(EVALUACION).preguntaId(7L)
                        .texto("Primera").build(),
                Respuesta.builder().id(2L).evaluacionId(EVALUACION).preguntaId(8L)
                        .texto("Segunda").build()));
        when(preguntas.findByIdIn(List.of(7L, 8L))).thenReturn(List.of(
                Pregunta.builder().id(7L).enunciado("Una").tipo("V").esPuntuable(true).build(),
                Pregunta.builder().id(8L).enunciado("Otra").tipo("V").esPuntuable(true).build()));
        lenient().when(alineaciones.findByEvaluacionId(EVALUACION)).thenReturn(List.of());
    }
}
