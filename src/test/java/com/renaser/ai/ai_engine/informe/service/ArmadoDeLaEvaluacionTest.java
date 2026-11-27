package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Dato;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Detalle;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Senal;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.CriterioDelDesglose;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DesgloseEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DesglosePorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PatronDelCuestionario;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PreguntaDelDesglose;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.ResumenCerradas;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.Alerta;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Dimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.HallazgoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.OrdenPregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.DimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OrdenPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioDesgloseEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.service.impl.CalificacionCriterios;
import com.renaser.ai.ai_engine.pesos.entity.PesoComponentePerfil;
import com.renaser.ai.ai_engine.pesos.repository.PesoComponentePerfilRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * El informe de la evaluación armado con los repositorios simulados: cuándo se puede descargar,
 * las tres formas de enseñar «Qué se evaluó» (pilares de un banco CAZATALENTOS, criterios de
 * las preguntas propias y lo cerrado de los demás bancos), el porqué de cada abierta con sus
 * huecos y ajustes a mano, y el anexo con lo que contestó en cada pregunta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("El armado del informe de la evaluación")
class ArmadoDeLaEvaluacionTest {

    private static final Long POSTULACION = 10L;
    private static final Long EVALUACION = 50L;
    private static final Instant AHORA = Instant.parse("2026-11-16T15:00:00Z");

    @Mock private EvaluacionRepository evaluaciones;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private OrdenPreguntaRepository ordenes;
    @Mock private PreguntaRepository preguntas;
    @Mock private RespuestaRepository respuestas;
    @Mock private OpcionRepository opciones;
    @Mock private NotaRespuestaRepository notasRespuesta;
    @Mock private PreguntaDimensionRepository preguntaDimensiones;
    @Mock private DimensionRepository dimensiones;
    @Mock private CriterioBancoRepository criteriosBanco;
    @Mock private PesoComponentePerfilRepository pesosComponente;
    @Mock private ServicioDesgloseEvaluacion desglose;
    @Mock private CalificacionCriterios calificacionCriterios;

    @InjectMocks private ArmadoDeLaEvaluacion armado;

    // ================================================================= el estado

    @Nested
    @DisplayName("Cuándo se puede descargar")
    class Estado {

        @Test
        @DisplayName("sin evaluación, o sin entregar, no tiene calificación")
        void sinEvaluacionOSinEntregar() {
            Base sinEvaluacion = base(postulacion(null), null, null, "SIN_EMPEZAR");
            assertThat(armado.estado(sinEvaluacion))
                    .isEqualTo(new EstadoDeUnInforme("EVALUACION", false, MotivosDelInforme.SIN_CALIFICAR, null));

            evaluacion(null);
            assertThat(armado.estado(base(postulacion(EVALUACION), null, null, "SIN_EMPEZAR")).motivo())
                    .isEqualTo(MotivosDelInforme.SIN_CALIFICAR);
        }

        @Test
        @DisplayName("entregada y con la IA trabajando dice que está calificando aunque haya nota vieja")
        void calificando() {
            evaluacion(AHORA);
            notaDeLaEtapa(BigDecimal.valueOf(70));
            assertThat(armado.estado(base(postulacion(EVALUACION), null, null, "EN_CURSO")))
                    .isEqualTo(new EstadoDeUnInforme("EVALUACION", false, MotivosDelInforme.CALIFICANDO, null));
        }

        @Test
        @DisplayName("con la nota de la etapa está disponible y la lleva")
        void conNota() {
            evaluacion(AHORA);
            notaDeLaEtapa(BigDecimal.valueOf(71.5));
            assertThat(armado.estado(base(postulacion(EVALUACION), null, null, "TERMINADA")))
                    .isEqualTo(new EstadoDeUnInforme("EVALUACION", true, null, BigDecimal.valueOf(71.5)));
        }

        @Test
        @DisplayName("sin nota y con la IA fallida dice que la calificación falló")
        void fallida() {
            evaluacion(AHORA);
            assertThat(armado.estado(base(postulacion(EVALUACION), null, null, "FALLIDA")).motivo())
                    .isEqualTo(MotivosDelInforme.FALLO);
        }

        @Test
        @DisplayName("sin nota y con preguntas propias a medias sigue calificando; si no, no tiene calificación")
        void sinNotaSegunLasPreguntasPropias() {
            evaluacion(AHORA);
            when(desglose.paraElInforme(any())).thenReturn(desglose(null, null, porPuntos(false, List.of())));
            assertThat(armado.estado(base(postulacion(EVALUACION), null, null, "TERMINADA")).motivo())
                    .isEqualTo(MotivosDelInforme.CALIFICANDO);

            when(desglose.paraElInforme(any())).thenReturn(desglose(null, null, porPuntos(true, List.of())));
            assertThat(armado.estado(base(postulacion(EVALUACION), null, null, "TERMINADA")).motivo())
                    .isEqualTo(MotivosDelInforme.SIN_CALIFICAR);

            when(desglose.paraElInforme(any())).thenReturn(desglose(null, null, null));
            assertThat(armado.estado(base(postulacion(EVALUACION), null, null, "TERMINADA")).motivo())
                    .isEqualTo(MotivosDelInforme.SIN_CALIFICAR);
        }
    }

    // ================================================================= un banco CAZATALENTOS

    @Nested
    @DisplayName("Un banco CAZATALENTOS, con pilares")
    class ConPilares {

        private Informe armarCazatalentos() {
            evaluacion(Instant.parse("2026-11-10T14:30:00Z"));
            notaDeLaEtapa(BigDecimal.valueOf(74.4));
            when(calificacionCriterios.metodoDe(any())).thenReturn(CalificacionCriterios.METODO);
            when(desglose.paraElInforme(any())).thenReturn(desglose(new ResumenCerradas(BigDecimal.valueOf(80), 1),
                    null, null, List.of(new PatronDelCuestionario("IDEAL", "Siempre lo ideal",
                            "  Eligió la opción perfecta en todas  ", 5, 5))));
            when(pesosComponente.findByVersionPesosId(3L)).thenReturn(List.of(
                    peso("CV", 30), peso("EVALUACION", 70)));
            // P6 está en el orden pero ya no existe en el banco: se salta y no deja hueco.
            when(ordenes.findByEvaluacionIdOrderByPosicion(EVALUACION)).thenReturn(
                    List.of(orden(1L), orden(2L), orden(3L), orden(4L), orden(5L), orden(6L), orden(7L),
                            orden(8L), orden(9L)));
            when(preguntas.findByIdIn(any())).thenReturn(List.of(
                    pregunta(1L, "ABIERTA", true, (short) 2, null, null),
                    pregunta(2L, "ABIERTA", true, null, null, null),
                    pregunta(3L, "ABIERTA", true, null, null, null),
                    pregunta(4L, "OPCION_UNICA", true, null, null, null),
                    pregunta(5L, "OPCION_MULTIPLE", true, null, null, 77L),
                    pregunta(7L, "ABIERTA", false, null, null, null),
                    pregunta(8L, "VERDADERO_FALSO", true, null, null, null),
                    pregunta(9L, null, true, null, null, null)));
            when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                    Respuesta.builder().id(101L).preguntaId(1L).texto("  Cambié el proceso de ingreso  ").build(),
                    Respuesta.builder().id(103L).preguntaId(3L).texto("Algo hice").build(),
                    Respuesta.builder().id(104L).preguntaId(4L).opcionId(41L).build(),
                    Respuesta.builder().id(105L).preguntaId(5L).detalle("{\"marcadas\":[53,52,51]}").build(),
                    Respuesta.builder().id(107L).preguntaId(7L).texto("Libre").build(),
                    Respuesta.builder().id(108L).preguntaId(8L).texto(" Verdadero ").build(),
                    Respuesta.builder().id(109L).preguntaId(9L).opcionId(999L).texto("otra cosa").build()));
            when(opciones.findByPreguntaIdIn(any())).thenReturn(List.of(
                    Opcion.builder().id(41L).preguntaId(4L).texto(" Siempre ").orden(1).build(),
                    Opcion.builder().id(42L).preguntaId(4L).texto("Nunca").orden(2).build(),
                    Opcion.builder().id(51L).preguntaId(5L).texto("Uno").orden(1).build(),
                    Opcion.builder().id(52L).preguntaId(5L).texto(" ").letra("B").orden(2).build(),
                    Opcion.builder().id(53L).preguntaId(5L).build()));
            when(notasRespuesta.findByRespuestaIdIn(any())).thenReturn(List.of(
                    NotaRespuesta.builder().respuestaId(101L).puntaje(BigDecimal.valueOf(3))
                            .explicacion(" Episodio claro y propio ").evidenciaCitada(" cambié el proceso ")
                            .c1Episodio(true).c2Autoria(false).c3Dato(true).c4Incomodidad(null)
                            .ajustadaPorUsuarioId(9L).puntajeIa(BigDecimal.valueOf(2))
                            .motivoAjuste("  revisé el audio  ").build(),
                    NotaRespuesta.builder().respuestaId(104L).puntaje(BigDecimal.valueOf(4)).build()));
            when(dimensiones.findAllByOrderByOrden()).thenReturn(List.of(
                    Dimension.builder().codigo("PIL_ADAPT").nombre("Adaptabilidad").build(),
                    Dimension.builder().codigo("PIL_LIDER").nombre("Liderazgo").build()));
            when(preguntaDimensiones.findByPreguntaIdIn(any())).thenReturn(List.of(
                    dimension(1L, "PIL_ADAPT"), dimension(4L, "PIL_ADAPT"), dimension(2L, "PIL_LIDER"),
                    dimension(3L, "PIL_LIDER"), dimension(7L, "DIM_OTRA")));
            when(criteriosBanco.findByVersionBancoIdIn(any())).thenReturn(List.of(
                    CriterioBanco.builder().id(77L).nombre("Trabajo en equipo").build()));

            Vacante vacante = Vacante.builder().id(5L).titulo("Analista de RR. HH.").versionPesosId(3L).build();
            Base base = new Base(postulacion(EVALUACION), vacante, null, "Renaser", "Ana Torres", "ana@correo.pe",
                    null, null,
                    List.of(AfirmacionCv.builder().id(1L).clasificacion("DECLARADA").texto(" Inglés avanzado ")
                            .preguntaValidacion("¿Con quién lo hablas?").build()),
                    PerfilTalento.builder().id(4L).resumen("  Perfil fuerte en selección  ").build(),
                    List.of(hallazgo("FORTALEZA", "Comunica con claridad", null),
                            hallazgo("RIESGO_DESARROLLABLE", "Excel básico", "un curso corto"),
                            hallazgo("RIESGO_CRITICO", "Llega tarde", null),
                            hallazgo("FALTA_EVIDENCIA", "No da cifras", null)),
                    List.of(Alerta.builder().id(1L).tipo("DEMASIADO_IDEAL").descripcion("Todo le sale bien").build()),
                    "TERMINADA");
            return armado.armar(base, AHORA);
        }

        @Test
        @DisplayName("el seguimiento lee las abiertas que contestó con texto, con su número del anexo; sin evaluación, ninguna")
        void abiertasParaElSeguimiento() {
            armarCazatalentos();

            assertThat(armado.abiertasConTexto(base(postulacion(EVALUACION), null, null, "TERMINADA")))
                    .extracting(PendientesEntreEtapas.Fuente::codigo, PendientesEntreEtapas.Fuente::enunciado,
                            PendientesEntreEtapas.Fuente::texto)
                    .containsExactly(tuple("P1", "Pregunta 1", "Cambié el proceso de ingreso"),
                            tuple("P3", "Pregunta 3", "Algo hice"),
                            tuple("P6", "Pregunta 7", "Libre"),
                            tuple("P7", "Pregunta 8", "Verdadero"));
            assertThat(armado.abiertasConTexto(base(postulacion(null), null, null, "TERMINADA"))).isEmpty();
        }

        @Test
        @DisplayName("la nota dice cuánto pesó el CV y la evaluación, y los datos clave cuentan las preguntas")
        void notaYDatosClave() {
            Informe informe = armarCazatalentos();

            assertThat(informe.etapa()).isEqualTo("EVALUACION");
            assertThat(informe.nota().texto()).isEqualTo("74");
            assertThat(informe.nota().base()).isEqualTo("La nota del Perfil Integral: CV 30 % · Evaluación 70 %.");
            assertThat(informe.datosClave()).extracting(Dato::etiqueta, Dato::valor).containsExactly(
                    tuple("Cómo se calcula", "CV 30 % · Evaluación 70 %"),
                    tuple("Preguntas", "4 cerradas · 4 abiertas"),
                    tuple("Entregada", "10/11/2026 · 09:30"),
                    tuple("Generado", "16/11/2026 · 10:00"));
            assertThat(informe.resumen().texto()).isEqualTo("Perfil fuerte en selección");
            assertThat(informe.detallePie()).isEqualTo("Lo cerrado: 80 de 100 en 1 pregunta.");
            assertThat(informe.nombreArchivo())
                    .isEqualTo("Informe evaluación - Ana Torres - Analista de RR. HH..pdf");
        }

        @Test
        @DisplayName("cada pilar sale con la cuenta del índice; uno con alguna respuesta sin nota queda pendiente, sin nota parcial")
        void pilares() {
            Informe informe = armarCazatalentos();

            assertThat(informe.evaluado().escala()).isEqualTo("sobre 100");
            List<Criterio> pilares = informe.evaluado().criterios();
            assertThat(pilares).extracting(Criterio::nombre).containsExactly("Adaptabilidad", "Liderazgo");
            // (3 × 2 + 4 × 1) ÷ (4 × 3) × 100
            assertThat(pilares.get(0).nota()).isEqualByComparingTo("83.33");
            assertThat(pilares.get(0).porcentaje()).isEqualTo(83);
            assertThat(pilares.get(0).preguntas()).isEqualTo("1 cerradas · 1 abiertas");
            assertThat(pilares.get(0).pendiente()).isNull();
            assertThat(pilares.get(1).nota()).isNull();
            assertThat(pilares.get(1).notaTexto()).isEqualTo("—");
            assertThat(pilares.get(1).pendiente()).isEqualTo("Le falta la nota de alguna respuesta");
        }

        @Test
        @DisplayName("el porqué de cada abierta: el ajuste a mano con lo que puso la IA, las cuatro señales, el cero de la que dejó en blanco y la que falta calificar")
        void elPorqueDeCadaAbierta() {
            List<Detalle> detalle = armarCazatalentos().detalle();

            // Solo las abiertas puntuables: la de opinión libre y las cerradas no salen.
            assertThat(detalle).extracting(Detalle::numero).containsExactly(1, 2, 3);
            Detalle ajustada = detalle.get(0);
            assertThat(ajustada.notaTexto()).isEqualTo("3 / 8");
            assertThat(ajustada.ajustada()).isTrue();
            assertThat(ajustada.motivoAjuste()).isEqualTo("La IA puso 2: revisé el audio");
            assertThat(ajustada.explicacion()).isEqualTo("Episodio claro y propio");
            assertThat(ajustada.evidencia()).isEqualTo("cambié el proceso");
            assertThat(ajustada.meta()).isEqualTo("Abierta · Adaptabilidad");
            assertThat(ajustada.senales()).extracting(Senal::nombre, Senal::cumple).containsExactly(
                    tuple("Episodio real", true),
                    tuple("Fue suyo", false),
                    tuple("Dato duro", true),
                    tuple("Incomodidad", false));

            Detalle enBlanco = detalle.get(1);
            assertThat(enBlanco.notaTexto()).isEqualTo("0");
            assertThat(enBlanco.explicacion()).isEqualTo("Sin respuesta: vale 0, que es la regla de siempre.");
            assertThat(enBlanco.senales()).isEmpty();
            assertThat(enBlanco.pendiente()).isNull();

            Detalle sinNota = detalle.get(2);
            assertThat(sinNota.notaTexto()).isEqualTo("—");
            assertThat(sinNota.explicacion()).isNull();
            assertThat(sinNota.pendiente()).isEqualTo("Falta calificarla");
        }

        @Test
        @DisplayName("el anexo trae cada pregunta con lo que marcó o escribió, en orden y sin hueco por la que ya no existe")
        void anexo() {
            Informe informe = armarCazatalentos();

            assertThat(informe.anexo().subtitulo()).isEqualTo("8 preguntas");
            List<PreguntaAnexo> anexo = informe.anexo().preguntas();
            assertThat(anexo).extracting(PreguntaAnexo::numero).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
            assertThat(anexo).extracting(PreguntaAnexo::respuesta).containsExactly(
                    "Cambié el proceso de ingreso", null, "Algo hice", "«Siempre»", "(sin texto) · «Uno» · B",
                    "Libre", "Verdadero", "otra cosa");
            assertThat(anexo).extracting(PreguntaAnexo::meta).containsExactly(
                    "Abierta · Adaptabilidad", "Abierta · Liderazgo", "Abierta · Liderazgo",
                    "Opción única · Adaptabilidad", "Opción múltiple · Trabajo en equipo", "Abierta",
                    "Verdadero falso", "Pregunta");
            // Lo que demostró solo en las abiertas; la explicación, en todas las que la tengan.
            assertThat(anexo.get(0).loQueDemostro()).isEqualTo("Episodio claro y propio");
            assertThat(anexo.get(3).loQueDemostro()).isNull();
        }

        @Test
        @DisplayName("los bloques salen del retrato, «Lo que pidió el CV» va siempre por contrastar y lo pendiente junta faltas, alertas y patrones")
        void bloques() {
            List<Bloque> bloques = armarCazatalentos().bloques();

            assertThat(bloques).extracting(Bloque::codigo)
                    .containsExactly("FUERTES", "FORTALECER", "DEBILIDADES", "PIDIO_EL_CV", "PENDIENTE");
            assertThat(bloques.get(0).puntos()).extracting(Punto::texto).containsExactly("Comunica con claridad");
            assertThat(bloques.get(1).puntos().get(0).detalle()).isEqualTo("un curso corto");
            assertThat(bloques.get(2).puntos()).extracting(Punto::texto).containsExactly("Llega tarde");
            assertThat(bloques.get(3).puntos()).extracting(Punto::texto, Punto::etiqueta)
                    .containsExactly(tuple("«Inglés avanzado»", "Por contrastar"));
            assertThat(bloques.get(4).titulo()).isEqualTo("Por verificar en la prueba del puesto");
            assertThat(bloques.get(4).puntos()).extracting(Punto::etiqueta)
                    .containsExactly("Falta evidencia", "Demasiado ideal", "Patrón");
            assertThat(bloques.get(4).puntos().get(2).detalle()).isEqualTo("Eligió la opción perfecta en todas");
        }
    }

    // ================================================================= preguntas propias

    @Nested
    @DisplayName("Preguntas propias de la vacante, por criterios")
    class PorPuntos {

        @Test
        @DisplayName("cada criterio con sus puntos y quién puso la nota; el que tiene un ajuste a mano dice Persona y su motivo")
        void criteriosPropios() {
            evaluacion(null);
            CriterioDelDesglose completo = new CriterioDelDesglose(1L, "Resolución", null, 10,
                    BigDecimal.valueOf(9), BigDecimal.valueOf(5), 5, BigDecimal.valueOf(4), 5, false,
                    List.of(preguntaDesglose("ABIERTA", false, null), preguntaDesglose("OPCION_UNICA", false, null)));
            CriterioDelDesglose ajustado = new CriterioDelDesglose(2L, "Orden", null, 20, BigDecimal.valueOf(3),
                    null, 0, null, 0, true,
                    List.of(preguntaDesglose("ABIERTA", true, "La respuesta venía en el adjunto"),
                            preguntaDesglose("ABIERTA", true, " "), preguntaDesglose("ESCALA", false, null)));
            CriterioDelDesglose soloSistema = new CriterioDelDesglose(3L, "Datos", null, 4, BigDecimal.ONE,
                    BigDecimal.ONE, 4, BigDecimal.ZERO, 0, false, List.of());
            when(desglose.paraElInforme(any())).thenReturn(desglose(null, null,
                    porPuntos(false, List.of(completo, ajustado, soloSistema))));
            // Sin orden congelado: las que contestó, en el orden en que las contestó.
            when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                    Respuesta.builder().id(202L).preguntaId(11L).texto(" 4 ").build(),
                    Respuesta.builder().id(201L).preguntaId(10L).texto("Llamé al proveedor").build()));
            when(preguntas.findByIdIn(any())).thenReturn(List.of(
                    pregunta(10L, "ABIERTA", true, null, 5, 88L),
                    pregunta(11L, "ESCALA", true, null, null, null)));
            when(notasRespuesta.findByRespuestaIdIn(any())).thenReturn(List.of(
                    NotaRespuesta.builder().respuestaId(201L).puntaje(BigDecimal.valueOf(4))
                            .ajustadaPorUsuarioId(9L).motivoAjuste(" Lo vio la jefa ").build()));
            when(criteriosBanco.findByVersionBancoIdIn(any())).thenReturn(List.of(
                    CriterioBanco.builder().id(88L).nombre("Resolución").build()));

            Informe informe = armado.armar(base(postulacion(EVALUACION), null, null, "TERMINADA"), AHORA);

            assertThat(informe.nota().texto()).isEqualTo("—");
            assertThat(informe.nota().base()).isEqualTo("La nota del Perfil Integral.");
            assertThat(informe.datosClave()).extracting(Dato::etiqueta).containsExactly("Preguntas", "Generado");
            assertThat(informe.datosClave().get(0).valor()).isEqualTo("1 cerrada · 1 abierta");
            assertThat(informe.resumen()).isNull();
            assertThat(informe.detallePie()).isNull();
            assertThat(informe.evaluado().escala()).isEqualTo("puntos de cada criterio");

            List<Criterio> criterios = informe.evaluado().criterios();
            assertThat(criterios).extracting(Criterio::quien).containsExactly("IA", "Persona", "Sistema");
            assertThat(criterios.get(0).notaTexto()).isEqualTo("9");
            assertThat(criterios.get(0).maximoTexto()).isEqualTo("/ 10");
            assertThat(criterios.get(0).porcentaje()).isEqualTo(90);
            assertThat(criterios.get(0).preguntas()).isEqualTo("1 cerradas · 1 abiertas");
            assertThat(criterios.get(0).motivoAjuste()).isNull();
            assertThat(criterios.get(1).nota()).isNull();
            assertThat(criterios.get(1).ajustada()).isTrue();
            assertThat(criterios.get(1).motivoAjuste()).isEqualTo("La respuesta venía en el adjunto");
            assertThat(criterios.get(1).pendiente()).isEqualTo("Le falta la nota de alguna abierta");

            // La abierta con techo propio y un ajuste sin la nota de la IA: solo el motivo.
            assertThat(informe.detalle()).singleElement().satisfies(d -> {
                assertThat(d.notaTexto()).isEqualTo("4 / 5");
                assertThat(d.motivoAjuste()).isEqualTo("Lo vio la jefa");
                assertThat(d.meta()).isEqualTo("Abierta · Resolución");
                assertThat(d.senales()).isEmpty();
            });
            assertThat(informe.anexo().preguntas()).extracting(PreguntaAnexo::respuesta)
                    .containsExactly("Llamé al proveedor", "4");
            assertThat(informe.anexo().preguntas()).extracting(PreguntaAnexo::meta)
                    .containsExactly("Abierta · Resolución", "Escala");
        }
    }

    // ================================================================= los demás bancos

    @Nested
    @DisplayName("Los demás bancos")
    class OtrosBancos {

        @Test
        @DisplayName("sin evaluación ni pesos que sumen: lo cerrado y la evaluación completa sobre 100, y un anexo vacío")
        void loCerradoYLaEvaluacionCompleta() {
            when(calificacionCriterios.metodoDe(any())).thenReturn("PUNTOS");
            when(desglose.paraElInforme(any())).thenReturn(desglose(new ResumenCerradas(BigDecimal.valueOf(70), 3),
                    BigDecimal.valueOf(65), null));
            when(pesosComponente.findByVersionPesosId(3L)).thenReturn(List.of(peso("OTRO", 100)));
            Vacante vacante = Vacante.builder().id(5L).titulo("Cajero").versionPesosId(3L).build();

            Informe informe = armado.armar(base(postulacion(null), vacante, null, "TERMINADA"), AHORA);

            assertThat(informe.nota().base()).isEqualTo("La nota del Perfil Integral.");
            assertThat(informe.datosClave().get(0).valor()).isEqualTo("0 cerradas · 0 abiertas");
            assertThat(informe.detallePie()).isEqualTo("Lo cerrado: 70 de 100 en 3 preguntas.");
            assertThat(informe.anexo().subtitulo()).isEqualTo("0 preguntas");
            assertThat(informe.anexo().preguntas()).isEmpty();
            assertThat(informe.detalle()).isEmpty();
            assertThat(informe.evaluado().criterios()).extracting(Criterio::nombre, Criterio::notaTexto,
                    Criterio::quien, Criterio::preguntas).containsExactly(
                    tuple("Preguntas cerradas", "70", "Sistema", "3 preguntas"),
                    tuple("Evaluación completa", "65", null, null));
        }

        @Test
        @DisplayName("una sola abierta con la nota de la IA cambiada a mano y sin motivo, y nada que enseñar en «Qué se evaluó»")
        void unaSolaAbiertaAjustada() {
            evaluacion(AHORA);
            when(desglose.paraElInforme(any())).thenReturn(desglose(new ResumenCerradas(null, 0), null, null));
            when(ordenes.findByEvaluacionIdOrderByPosicion(EVALUACION)).thenReturn(List.of(orden(20L)));
            when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                    Respuesta.builder().id(220L).preguntaId(20L).texto("Mi respuesta").build()));
            when(preguntas.findByIdIn(any())).thenReturn(List.of(pregunta(20L, "ABIERTA", true, null, null, null)));
            when(notasRespuesta.findByRespuestaIdIn(any())).thenReturn(List.of(
                    NotaRespuesta.builder().respuestaId(220L).puntaje(BigDecimal.valueOf(2))
                            .ajustadaPorUsuarioId(9L).puntajeIa(BigDecimal.valueOf(3)).build()));

            Informe informe = armado.armar(base(postulacion(EVALUACION), null, null, "TERMINADA"), AHORA);

            assertThat(informe.datosClave().get(0).valor()).isEqualTo("0 cerradas · 1 abierta");
            assertThat(informe.anexo().subtitulo()).isEqualTo("1 pregunta");
            assertThat(informe.detallePie()).isNull();
            assertThat(informe.evaluado().criterios()).isEmpty();
            assertThat(informe.detalle()).singleElement().satisfies(d -> {
                assertThat(d.notaTexto()).isEqualTo("2");
                assertThat(d.motivoAjuste()).isEqualTo("La IA puso 3");
            });
        }

        @Test
        @DisplayName("una evaluación entregada sin ninguna respuesta ni orden no tiene preguntas que enseñar")
        void sinRespuestas() {
            evaluacion(AHORA);
            when(desglose.paraElInforme(any())).thenReturn(desglose(null, null, null));

            Informe informe = armado.armar(base(postulacion(EVALUACION), null, null, "TERMINADA"), AHORA);

            assertThat(informe.anexo().preguntas()).isEmpty();
            assertThat(informe.datosClave()).extracting(Dato::etiqueta)
                    .containsExactly("Preguntas", "Entregada", "Generado");
        }
    }

    // ================================================================= ayudas

    private void evaluacion(Instant terminadaEn) {
        when(evaluaciones.findById(EVALUACION)).thenReturn(Optional.of(
                Evaluacion.builder().id(EVALUACION).terminadaEn(terminadaEn).build()));
    }

    private void notaDeLaEtapa(BigDecimal puntaje) {
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(eq(POSTULACION), eq("PERFIL_INTEGRAL")))
                .thenReturn(Optional.of(NotaEtapa.builder().puntaje(puntaje).build()));
    }

    private static Postulacion postulacion(Long evaluacionId) {
        return Postulacion.builder().id(POSTULACION).evaluacionId(evaluacionId).grupoPrioridad("ALTA").build();
    }

    private static Base base(Postulacion postulacion, Vacante vacante, PerfilTalento perfil, String comoVa) {
        return new Base(postulacion, vacante, null, "Renaser", "Ana Torres", "ana@correo.pe", null, null,
                List.of(), perfil, List.of(), List.of(), comoVa);
    }

    private static DesgloseEvaluacion desglose(ResumenCerradas cerradas, BigDecimal notaEvaluacion,
                                               DesglosePorPuntos porPuntos) {
        return desglose(cerradas, notaEvaluacion, porPuntos, List.of());
    }

    private static DesgloseEvaluacion desglose(ResumenCerradas cerradas, BigDecimal notaEvaluacion,
                                               DesglosePorPuntos porPuntos, List<PatronDelCuestionario> patrones) {
        return new DesgloseEvaluacion(POSTULACION, "TERMINADA", null, notaEvaluacion, cerradas, List.of(),
                List.of(), patrones, porPuntos);
    }

    private static DesglosePorPuntos porPuntos(boolean completo, List<CriterioDelDesglose> criterios) {
        return new DesglosePorPuntos(null, completo, false, null, null, criterios, List.of());
    }

    private static PreguntaDelDesglose preguntaDesglose(String tipo, boolean ajustada, String motivo) {
        return new PreguntaDelDesglose(1L, 1L, tipo, "¿?", 5, null, false, null, List.of(), null, null, null,
                ajustada, null, null, motivo, false);
    }

    private static OrdenPregunta orden(Long preguntaId) {
        return OrdenPregunta.builder().evaluacionId(EVALUACION).preguntaId(preguntaId)
                .posicion(preguntaId.intValue()).build();
    }

    private static Pregunta pregunta(Long id, String tipo, boolean puntuable, Short peso, Integer puntos,
                                     Long criterioBancoId) {
        return Pregunta.builder().id(id).versionBancoId(7L).tipo(tipo).esPuntuable(puntuable).peso(peso)
                .puntos(puntos).criterioBancoId(criterioBancoId).enunciado("Pregunta " + id).build();
    }

    private static PreguntaDimension dimension(Long preguntaId, String codigo) {
        return PreguntaDimension.builder().preguntaId(preguntaId).dimensionCodigo(codigo).build();
    }

    private static PesoComponentePerfil peso(String componente, int peso) {
        return PesoComponentePerfil.builder().versionPesosId(3L).componente(componente)
                .peso(BigDecimal.valueOf(peso)).build();
    }

    private static HallazgoPerfil hallazgo(String tipo, String descripcion, String sugerencia) {
        return HallazgoPerfil.builder().id((long) descripcion.length()).tipo(tipo).descripcion(descripcion)
                .sugerencia(sugerencia).build();
    }
}
