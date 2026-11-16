package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Dato;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Detalle;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EntregableAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DesgloseEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PatronDelCuestionario;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioDesgloseEvaluacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.EntregaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.NotaCriterioResponse;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.RespuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CriterioDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.EntregaVista;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.OpcionDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PreguntaDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PruebaDelCandidato;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPrueba;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPruebaPropia;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El informe de la prueba del puesto armado con los servicios simulados: cuándo se puede
 * descargar, las tres formas de la prueba (la del editor, la de una plantilla y el cuestionario
 * técnico), quién puso cada nota, lo que falta calificar, los entregables que no llegaron y lo
 * que queda para la entrevista.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("El armado del informe de la prueba del puesto")
class ArmadoDeLaPruebaTest {

    private static final Long POSTULACION = 10L;
    private static final Instant AHORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final Instant ENTREGADA = Instant.parse("2026-11-12T22:05:00Z");

    @Mock private IntentoPruebaRepository intentos;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private VersionPlantillaPruebaRepository versionesPlantilla;
    @Mock private PlantillaPruebaRepository plantillas;
    @Mock private ServicioCalificacionPrueba deLaPlantilla;
    @Mock private ServicioCalificacionPruebaPropia delEditor;
    @Mock private ServicioDesgloseEvaluacion desglose;
    @Mock private ColaCalificacionIa cola;

    @InjectMocks private ArmadoDeLaPrueba armado;

    // ================================================================= el estado

    @Nested
    @DisplayName("Cuándo se puede descargar")
    class Estado {

        @Test
        @DisplayName("el cuestionario técnico depende solo de la nota de la etapa")
        void cuestionario() {
            Base base = base(postulacion(null), vacanteConCuestionario());
            assertThat(armado.estado(base).motivo()).isEqualTo(MotivosDelInforme.SIN_CALIFICAR);

            notaDeLaEtapa(BigDecimal.valueOf(60));
            assertThat(armado.estado(base))
                    .isEqualTo(new EstadoDeUnInforme("PRUEBA", true, null, BigDecimal.valueOf(60)));
            verifyNoInteractions(intentos, cola);
        }

        @Test
        @DisplayName("sin intento, o empezado y sin entregar, no tiene calificación")
        void sinEntregar() {
            assertThat(armado.estado(base(postulacion(null), null)).motivo())
                    .isEqualTo(MotivosDelInforme.SIN_CALIFICAR);

            intento(IntentoPrueba.builder().iniciadoEn(ENTREGADA).build());
            assertThat(armado.estado(base(postulacion(null), null)).disponible()).isFalse();
        }

        @Test
        @DisplayName("entregada y con la IA calificando dice que está calificando")
        void calificando() {
            intento(IntentoPrueba.builder().entregadoEn(ENTREGADA).build());
            seguimiento("EN_CURSO");
            notaDeLaEtapa(BigDecimal.TEN);
            assertThat(armado.estado(base(postulacion(null), null)).motivo())
                    .isEqualTo(MotivosDelInforme.CALIFICANDO);
        }

        @Test
        @DisplayName("con la nota de la etapa, con algún criterio calificado o sin completar, está disponible")
        void disponible() {
            intento(IntentoPrueba.builder().entregadoEn(ENTREGADA).build());
            notaDeLaEtapa(BigDecimal.valueOf(55));
            assertThat(armado.estado(base(postulacion(null), null)))
                    .isEqualTo(new EstadoDeUnInforme("PRUEBA", true, null, BigDecimal.valueOf(55)));

            when(notasEtapa.findByPostulacionIdAndEtapaCodigo(any(), any())).thenReturn(Optional.empty());
            when(deLaPlantilla.notasParaElInforme(any())).thenReturn(List.of(
                    new NotaCriterioResponse(1L, "Diagnóstico", 25.0, 20.0, null, "AGENTE")));
            assertThat(armado.estado(base(postulacion(null), null)))
                    .isEqualTo(new EstadoDeUnInforme("PRUEBA", true, null, null));

            when(deLaPlantilla.notasParaElInforme(any())).thenReturn(List.of());
            intento(IntentoPrueba.builder().noCompletada(true).build());
            assertThat(armado.estado(base(postulacion(null), null)).disponible()).isTrue();
        }

        @Test
        @DisplayName("entregada sin ninguna nota: falló si la calificación quedó detenida; si no, sin calificar")
        void sinNingunaNota() {
            intento(IntentoPrueba.builder().entregadoEn(ENTREGADA).build());
            seguimiento("DETENIDA");
            assertThat(armado.estado(base(postulacion(null), null)).motivo()).isEqualTo(MotivosDelInforme.FALLO);

            seguimiento("TERMINADA");
            assertThat(armado.estado(base(postulacion(null), null)).motivo())
                    .isEqualTo(MotivosDelInforme.SIN_CALIFICAR);

            when(cola.calificacionDePrueba(any())).thenReturn(Map.of());
            assertThat(armado.estado(base(postulacion(null), null)).motivo())
                    .isEqualTo(MotivosDelInforme.SIN_CALIFICAR);
        }
    }

    // ================================================================= la prueba del editor

    @Nested
    @DisplayName("La prueba escrita en el editor")
    class DelEditor {

        private Informe armarDelEditor() {
            intento(IntentoPrueba.builder().versionBancoId(70L).entregadoEn(ENTREGADA).build());
            when(versionesBanco.findById(70L)).thenReturn(Optional.of(
                    VersionBanco.builder().id(70L).etiqueta("Caso de nómina").enunciado("  Arma la planilla  ").build()));
            when(desglose.paraElInforme(any())).thenReturn(new DesgloseEvaluacion(POSTULACION, null, null, null,
                    null, List.of(), List.of(),
                    List.of(new PatronDelCuestionario("IDEAL", "Siempre lo ideal", null, 5, 5)), null));
            when(delEditor.paraElInforme(any())).thenReturn(new PruebaDelCandidato(POSTULACION, "NO_COMPLETADA",
                    false, null, ENTREGADA, false, null, List.of("La nota de Entrevista"), false, false,
                    "La IA vuelve a intentarlo",
                    List.of(criterio("Diagnóstico", 23, 25, "CALIFICADO", "IA", null, null,
                                    List.of(pregunta("ABIERTA", " Ubicó la rotación ", true, null))),
                            criterio("Presupuesto", 10, 20, "CALIFICADO", "IA", "Lucía", " la hoja no abrió ", List.of()),
                            criterio("Cierre", 4, 4, "SOLO_SISTEMA", "IA", null, null, List.of(
                                    pregunta("OPCION_UNICA", null, true, List.of(
                                            new OpcionDelCandidato(1L, "A tiempo", 4, true),
                                            new OpcionDelCandidato(2L, "Tarde", 0, false))),
                                    pregunta("OPCION_MULTIPLE", " lo escribió aparte ", true, List.of(
                                            new OpcionDelCandidato(3L, "Uno", 1, false))),
                                    pregunta("VERDADERO_FALSO", null, false, null),
                                    pregunta("ESCALA", "3", true, List.of()))),
                            criterio("Entrevista", null, 10, "PENDIENTE", "PERSONA", null, null, List.of()),
                            criterio("Plan", null, 10, "PENDIENTE", "IA", null, null, List.of()),
                            criterio("Riesgos", 1, 10, "CALIFICADO", "PERSONA", null, null, List.of()),
                            criterio("Equipo", 5, 10, "CALIFICADO", "IA", null, null, List.of())),
                    List.of(entregaVista("Planilla.xlsx", true), entregaVista("Informe.pdf", false))));
            Base base = new Base(postulacion(50L), null, null, "Renaser", "Ana Torres", "ana@correo.pe", null,
                    null, List.of(AfirmacionCv.builder().id(1L).clasificacion("FALTA_INFO").texto("Inglés")
                            .build()),
                    null, List.of(), List.of(), "TERMINADA");
            return armado.armar(base, AHORA);
        }

        @Test
        @DisplayName("sin nota de la etapa dice «pendiente» y qué criterios faltan, y los datos clave cuentan los entregables")
        void notaPendiente() {
            Informe informe = armarDelEditor();

            assertThat(informe.nota().texto()).isEqualTo("pendiente");
            assertThat(informe.nota().sobre()).isNull();
            assertThat(informe.nota().base()).isEqualTo("Pendiente: le falta la nota de Entrevista, Plan.");
            assertThat(informe.datosClave()).extracting(Dato::etiqueta, Dato::valor).containsExactly(
                    tuple("Prueba", "Caso de nómina"), tuple("Entregables", "1 de 2 entregados"),
                    tuple("No la completó", "12/11/2026 · 17:05"), tuple("Generado", "16/11/2026 · 10:00"));
            assertThat(informe.resumen().texto()).isEqualTo("No completó la prueba: respondió 4 de 5 preguntas. "
                    + "Entregó 1 de 2 entregables. Falta la nota de Entrevista, Plan.");
        }

        @Test
        @DisplayName("cada criterio dice quién lo puntuó, el ajuste a mano con su motivo y por qué falta la nota")
        void criterios() {
            List<Criterio> criterios = armarDelEditor().evaluado().criterios();

            assertThat(criterios).extracting(Criterio::nombre, Criterio::quien).containsExactly(
                    tuple("Diagnóstico", "IA"), tuple("Presupuesto", "Persona"), tuple("Cierre", "Sistema"),
                    tuple("Entrevista", "Persona"), tuple("Plan", "IA"), tuple("Riesgos", "Persona"),
                    tuple("Equipo", "IA"));
            assertThat(criterios.get(0).maximoTexto()).isEqualTo("/ 25");
            assertThat(criterios.get(0).porcentaje()).isEqualTo(92);
            assertThat(criterios.get(1).ajustada()).isTrue();
            assertThat(criterios.get(1).motivoAjuste()).isEqualTo("la hoja no abrió");
            assertThat(criterios.get(0).motivoAjuste()).isNull();
            assertThat(criterios.get(3).pendiente()).isEqualTo("Le toca calificarlo a una persona");
            assertThat(criterios.get(4).pendiente()).isEqualTo("La IA vuelve a intentarlo");
            assertThat(criterios.get(4).notaTexto()).isEqualTo("—");
        }

        @Test
        @DisplayName("los tres mejores, los peores del resto y, para la entrevista, lo de la prueba, la evaluación y el CV")
        void bloques() {
            List<Bloque> bloques = armarDelEditor().bloques();

            assertThat(bloques).extracting(Bloque::codigo).containsExactly("FUERTES", "FORTALECER", "PENDIENTE");
            assertThat(bloques.get(0).puntos()).extracting(Punto::texto)
                    .containsExactly("Cierre", "Diagnóstico", "Presupuesto");
            assertThat(bloques.get(1).puntos()).extracting(Punto::texto).containsExactly("Riesgos", "Equipo");
            assertThat(bloques.get(2).titulo()).isEqualTo("Por verificar en la entrevista");
            assertThat(bloques.get(2).puntos()).extracting(Punto::texto, Punto::grupo).containsExactly(
                    tuple("Entrevista: Le toca calificarlo a una persona", "De la prueba"),
                    tuple("Plan: La IA vuelve a intentarlo", "De la prueba"),
                    tuple("No entregó «Informe.pdf»", "De la prueba"),
                    tuple("No completó la prueba", "De la prueba"),
                    tuple("Siempre lo ideal", "De la evaluación"),
                    tuple("«Inglés»", "Del CV"));
        }

        @Test
        @DisplayName("el anexo trae el caso, cada respuesta con lo marcado y los entregables sin su contenido")
        void anexo() {
            Informe informe = armarDelEditor();

            assertThat(informe.anexo().caso()).isEqualTo("Arma la planilla");
            assertThat(informe.anexo().subtitulo()).isEqualTo("5 preguntas · 2 entregables");
            assertThat(informe.anexo().entregablesResumen()).isEqualTo("1 de 2 entregados");
            assertThat(informe.anexo().preguntas()).extracting(PreguntaAnexo::meta, PreguntaAnexo::respuesta)
                    .containsExactly(
                            tuple("Abierta · Diagnóstico", "Ubicó la rotación"),
                            tuple("Opción única · Cierre", "«A tiempo»"),
                            tuple("Opción múltiple · Cierre", "lo escribió aparte"),
                            tuple("Pregunta · Cierre", null),
                            tuple("Escala · Cierre", "3"));
            assertThat(informe.anexo().entregables()).extracting(EntregableAnexo::detalle, EntregableAnexo::entregado)
                    .containsExactly(tuple("Subido el 12/11 a las 17:05", true),
                            tuple("No se subió ningún archivo", false));
            assertThat(informe.detalle()).isEmpty();
        }

        @Test
        @DisplayName("si el servicio no devuelve la prueba sale solo con la fecha de entrega y la nota de la etapa")
        void sinLaPrueba() {
            intento(IntentoPrueba.builder().versionBancoId(70L).entregadoEn(ENTREGADA).build());
            notaDeLaEtapa(BigDecimal.valueOf(81.6));

            Informe informe = armado.armar(base(postulacion(null), null), AHORA);

            assertThat(informe.nota().texto()).isEqualTo("82");
            assertThat(informe.nota().sobre()).isEqualTo("de 100");
            assertThat(informe.nota().base()).isEqualTo("La suma de los criterios de su rúbrica.");
            assertThat(informe.datosClave()).extracting(Dato::etiqueta).containsExactly("Entregada", "Generado");
            assertThat(informe.resumen()).isNull();
            assertThat(informe.anexo().subtitulo()).isEqualTo("0 preguntas");
            assertThat(informe.anexo().entregablesResumen()).isNull();
            verifyNoInteractions(desglose);
        }

        @Test
        @DisplayName("sin etiqueta se llama «Prueba del puesto» y, sin motivo, falta que la IA lo califique")
        void sinEtiquetaNiMotivo() {
            intento(IntentoPrueba.builder().versionBancoId(70L).noCompletada(true).build());
            when(versionesBanco.findById(70L)).thenReturn(Optional.of(VersionBanco.builder().etiqueta(" ").build()));
            when(delEditor.paraElInforme(any())).thenReturn(new PruebaDelCandidato(POSTULACION, "EN_CURSO", false,
                    null, null, false, null, List.of(), false, false, " ",
                    List.of(criterio("Plan", null, 10, "PENDIENTE", "IA", null, null, List.of())), List.of()));

            Informe informe = armado.armar(base(postulacion(null), null), AHORA);

            assertThat(informe.datosClave().get(0)).isEqualTo(new Dato("Prueba", "Prueba del puesto"));
            assertThat(informe.datosClave().get(1).etiqueta()).isEqualTo("No la completó");
            assertThat(informe.evaluado().criterios().get(0).pendiente()).isEqualTo("Falta que la IA lo califique");
            assertThat(informe.anexo().caso()).isNull();
        }
    }

    // ================================================================= la de una plantilla

    @Nested
    @DisplayName("La prueba de una plantilla")
    class DeUnaPlantilla {

        @Test
        @DisplayName("cada criterio con su máximo y quién lo puntuó; el que no tiene nota dice que falta calificarlo")
        void criteriosYNombre() {
            intento(IntentoPrueba.builder().versionPlantillaPruebaId(80L).entregadoEn(ENTREGADA).build());
            notaDeLaEtapa(BigDecimal.valueOf(82));
            when(versionesPlantilla.findById(80L)).thenReturn(Optional.of(
                    VersionPlantillaPrueba.builder().id(80L).plantillaPruebaId(90L).enunciado(" El caso ").build()));
            when(plantillas.findById(90L)).thenReturn(Optional.of(
                    PlantillaPrueba.builder().id(90L).nombre("Prueba de analista").build()));
            when(deLaPlantilla.notasParaElInforme(any())).thenReturn(List.of(
                    new NotaCriterioResponse(1L, "Diagnóstico", 25.0, 20.0, " Bien ubicado ", "AGENTE"),
                    new NotaCriterioResponse(2L, "Presentación", null, null, null, null),
                    new NotaCriterioResponse(3L, "Fórmulas", 10.0, 10.0, null, "SISTEMA")));
            when(notasCriterio.findByPostulacionId(POSTULACION)).thenReturn(List.of(
                    NotaCriterio.builder().criterioId(1L).ajustadaPorUsuarioId(9L).motivoAjuste(" subió el anexo ")
                            .build(),
                    NotaCriterio.builder().criterioId(3L).build()));
            when(deLaPlantilla.respuestasParaElInforme(any())).thenReturn(List.of(
                    new RespuestaDePrueba(1L, "P1", 1, "ABIERTA", "¿Qué harías?", " Lo primero ", ENTREGADA),
                    new RespuestaDePrueba(2L, "P2", 2, "OPCION_MULTIPLE", "¿Cuáles?", null, null),
                    new RespuestaDePrueba(3L, "P3", 3, null, "¿Algo más?", "No", ENTREGADA)));
            when(deLaPlantilla.entregablesParaElInforme(any())).thenReturn(List.of(
                    new EntregaDeLaPrueba(1L, "Plan.pdf", null, "PDF", true, true, null, 5L, "plan.pdf", 1,
                            ENTREGADA, null),
                    new EntregaDeLaPrueba(2L, "Hoja.xlsx", null, "XLSX", false, false, null, null, null, null,
                            null, null)));

            Informe informe = armado.armar(base(postulacion(null), null), AHORA);

            assertThat(informe.nota().texto()).isEqualTo("82");
            assertThat(informe.datosClave().get(0)).isEqualTo(new Dato("Prueba", "Prueba de analista"));
            assertThat(informe.anexo().caso()).isEqualTo("El caso");
            List<Criterio> criterios = informe.evaluado().criterios();
            assertThat(criterios).extracting(Criterio::quien).containsExactly("Persona", null, "Sistema");
            assertThat(criterios.get(0).motivoAjuste()).isEqualTo("subió el anexo");
            assertThat(criterios.get(0).maximoTexto()).isEqualTo("/ 25");
            assertThat(criterios.get(0).explicacion()).isEqualTo("Bien ubicado");
            assertThat(criterios.get(1).maximoTexto()).isNull();
            assertThat(criterios.get(1).porcentaje()).isNull();
            assertThat(criterios.get(1).pendiente()).isEqualTo("Falta calificarlo");
            assertThat(criterios.get(2).ajustada()).isFalse();
            assertThat(informe.bloques().get(0).puntos()).extracting(Punto::texto)
                    .containsExactly("Fórmulas", "Diagnóstico");
            assertThat(informe.bloques().get(1).puntos()).isEmpty();
            assertThat(informe.anexo().preguntas()).extracting(PreguntaAnexo::meta, PreguntaAnexo::respuesta)
                    .containsExactly(tuple("Abierta", "Lo primero"), tuple("Opción múltiple", null),
                            tuple("Pregunta", "No"));
            assertThat(informe.resumen().texto()).isEqualTo(
                    "Respondió 2 de 3 preguntas. Entregó 1 de 2 entregables. Falta la nota de Presentación.");
        }

        @Test
        @DisplayName("sin versión, o con una plantilla sin nombre, se llama «Prueba del puesto» y no tiene caso")
        void sinNombre() {
            intento(IntentoPrueba.builder().entregadoEn(ENTREGADA).build());
            Informe sinVersion = armado.armar(base(postulacion(null), null), AHORA);
            assertThat(sinVersion.datosClave().get(0)).isEqualTo(new Dato("Prueba", "Prueba del puesto"));
            assertThat(sinVersion.anexo().caso()).isNull();
            assertThat(sinVersion.nota().base()).isEqualTo("Todavía no tiene nota de la etapa.");

            intento(IntentoPrueba.builder().versionPlantillaPruebaId(80L).entregadoEn(ENTREGADA).build());
            when(versionesPlantilla.findById(80L)).thenReturn(Optional.of(
                    VersionPlantillaPrueba.builder().id(80L).plantillaPruebaId(90L).build()));
            when(plantillas.findById(90L)).thenReturn(Optional.of(PlantillaPrueba.builder().nombre(" ").build()));
            assertThat(armado.armar(base(postulacion(null), null), AHORA).datosClave().get(0).valor())
                    .isEqualTo("Prueba del puesto");

            when(versionesPlantilla.findById(80L)).thenReturn(Optional.of(VersionPlantillaPrueba.builder().build()));
            assertThat(armado.armar(base(postulacion(null), null), AHORA).datosClave().get(0).valor())
                    .isEqualTo("Prueba del puesto");
        }
    }

    // ================================================================= el cuestionario técnico

    @Nested
    @DisplayName("El cuestionario técnico y los casos sin prueba")
    class CuestionarioYSinPrueba {

        @Test
        @DisplayName("el cuestionario trae sus respuestas como abiertas, sin criterios ni entregables")
        void cuestionarioTecnico() {
            when(deLaPlantilla.respuestasParaElInforme(any())).thenReturn(List.of(
                    new RespuestaDePrueba(1L, "C1", 1, "ABIERTA", "¿Qué es REST?", " Un estilo ", ENTREGADA),
                    new RespuestaDePrueba(2L, "C2", 2, "ABIERTA", "¿Y GraphQL?", " ", null)));

            Informe informe = armado.armar(base(postulacion(null), vacanteConCuestionario()), AHORA);

            assertThat(informe.datosClave()).extracting(Dato::etiqueta, Dato::valor).containsExactly(
                    tuple("Prueba", "Cuestionario técnico"), tuple("Entregada", "—"),
                    tuple("Generado", "16/11/2026 · 10:00"));
            assertThat(informe.anexo().preguntas()).extracting(PreguntaAnexo::meta, PreguntaAnexo::respuesta)
                    .containsExactly(tuple("Abierta", "Un estilo"), tuple("Abierta", null));
            assertThat(informe.resumen().texto()).isEqualTo("Respondió 1 de 2 preguntas.");
            assertThat(informe.anexo().subtitulo()).isEqualTo("2 preguntas");
            verifyNoInteractions(delEditor, versionesBanco, versionesPlantilla);
        }

        @Test
        @DisplayName("una sola pregunta se cuenta en singular")
        void unaPregunta() {
            when(deLaPlantilla.respuestasParaElInforme(any())).thenReturn(List.of(
                    new RespuestaDePrueba(1L, "C1", 1, "ABIERTA", "¿Qué es REST?", "Un estilo", ENTREGADA)));
            assertThat(armado.armar(base(postulacion(null), vacanteConCuestionario()), AHORA).anexo().subtitulo())
                    .isEqualTo("1 pregunta");
        }

        @Test
        @DisplayName("sin intento sale vacía: sin prueba, sin resumen y sin entregables")
        void sinIntento() {
            Informe informe = armado.armar(base(postulacion(null), Vacante.builder().id(5L).titulo("Cajero")
                    .instrumentoEtapaTecnica("PLANTILLA").build()), AHORA);

            assertThat(informe.datosClave()).extracting(Dato::etiqueta).containsExactly("Entregada", "Generado");
            assertThat(informe.resumen()).isNull();
            assertThat(informe.evaluado().criterios()).isEmpty();
            assertThat(informe.bloques().get(2).puntos()).isEmpty();
            assertThat(informe.nombreArchivo()).isEqualTo("Informe prueba - Ana Torres - Cajero.pdf");
        }
    }

    @Test
    @DisplayName("el porqué tras los textos solo repite las explicaciones que el redactor dejó ocultas")
    void detalleTrasLosTextos() {
        Criterio oculta = new Criterio("Diagnóstico", BigDecimal.TEN, BigDecimal.TEN, "10", "/ 10", 100, "IA",
                true, "corregida", "Una línea", "La explicación entera", null, null);
        Criterio igual = new Criterio("Plan", null, BigDecimal.TEN, "—", "/ 10", null, null, false, null,
                "Lo mismo", "Lo mismo", null, "Falta calificarlo");
        Criterio sinExplicacion = new Criterio("Cierre", null, BigDecimal.TEN, "—", "/ 10", null, null, false,
                null, null, " ", null, null);

        List<Detalle> detalle = ArmadoDeLaPrueba.detalleTrasLosTextos(List.of(oculta, igual, sinExplicacion));

        assertThat(detalle).singleElement().satisfies(d -> {
            assertThat(d.titulo()).isEqualTo("Diagnóstico");
            assertThat(d.meta()).isEqualTo("IA");
            assertThat(d.ajustada()).isTrue();
            assertThat(d.motivoAjuste()).isEqualTo("corregida");
            assertThat(d.explicacion()).isEqualTo("La explicación entera");
        });
    }

    // ================================================================= ayudas

    private void intento(IntentoPrueba intento) {
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento));
    }

    private void notaDeLaEtapa(BigDecimal puntaje) {
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(eq(POSTULACION), eq("PRUEBA_PUESTO")))
                .thenReturn(Optional.of(NotaEtapa.builder().puntaje(puntaje).build()));
    }

    private void seguimiento(String estado) {
        when(cola.calificacionDePrueba(any())).thenReturn(
                Map.of(POSTULACION, new ColaCalificacionIa.Seguimiento(estado, null)));
    }

    private static Vacante vacanteConCuestionario() {
        return Vacante.builder().id(5L).titulo("Desarrollador").instrumentoEtapaTecnica("CUESTIONARIO_TECNICO")
                .build();
    }

    private static Postulacion postulacion(Long evaluacionId) {
        return Postulacion.builder().id(POSTULACION).evaluacionId(evaluacionId).build();
    }

    private static Base base(Postulacion postulacion, Vacante vacante) {
        return new Base(postulacion, vacante, null, "Renaser", "Ana Torres", "ana@correo.pe", null, null,
                List.of(), null, List.of(), List.of(), "TERMINADA");
    }

    private static CriterioDelCandidato criterio(String nombre, Integer nota, int maximo, String estado,
                                                 String calificador, String ajustadaPor, String motivo,
                                                 List<PreguntaDelCandidato> preguntas) {
        return new CriterioDelCandidato(1L, nombre, null, maximo, 0, null, maximo, calificador, null, null,
                nota == null ? null : BigDecimal.valueOf(nota), estado, " Explicación de " + nombre + " ", null,
                null, ajustadaPor, null, motivo, preguntas, List.of());
    }

    private static PreguntaDelCandidato pregunta(String tipo, String respuesta, boolean respondida,
                                                 List<OpcionDelCandidato> opciones) {
        return new PreguntaDelCandidato(1L, tipo, "¿" + tipo + "?", null, 4, null, respuesta, respondida, opciones);
    }

    private static EntregaVista entregaVista(String nombre, boolean loEntrego) {
        return new EntregaVista(1L, nombre, null, null, true, null, loEntrego, null, null, null,
                loEntrego ? ENTREGADA : null, null, null);
    }
}
