package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CasoPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioDelBorradorDePrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregablePropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendadorPrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PosicionDePregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendadorPrueba;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.prueba.entity.Entregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.NotaCriterioPrueba;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.CriterioDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.PreguntaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las reglas de la prueba técnica escrita en el editor (V67): lo que frena publicarla, la
 * cuenta de cada criterio (parte automática + parte calificada), lo que falta para entregar y
 * la aduana de las recomendaciones.
 */
@DisplayName("Las reglas de la prueba escrita en el editor")
class ReglasDeLaPruebaTest {

    private static final VersionBanco VERSION = VersionBanco.builder().id(1L)
            .enunciado("El caso").modalidad("CRONOMETRADA").duracionMinutos(90).build();

    private static EntregableRequerido entregable(long id, String nombre, String formato, boolean obligatorio) {
        return EntregableRequerido.builder().id(id).nombre(nombre).formato(formato)
                .esObligatorio(obligatorio).versionBancoId(1L).build();
    }

    private static EntregableRequerido general(long id, String nombre, String alcance) {
        return EntregableRequerido.builder().id(id).nombre(nombre).formato("ARCHIVO")
                .esObligatorio(true).versionBancoId(1L).alcance(alcance).build();
    }

    private static PreguntaDeLaPrueba unica(long id, long criterio, int puntos, RespuestaPrueba r) {
        Pregunta p = Pregunta.builder().id(id).tipo("OPCION_UNICA").enunciado("¿Cuál?").puntos(puntos)
                .criterioBancoId(criterio).orden((int) id).build();
        List<Opcion> opciones = List.of(
                Opcion.builder().id(id * 10 + 1).preguntaId(id).texto("Buena")
                        .puntaje(BigDecimal.valueOf(puntos)).orden(1).build(),
                Opcion.builder().id(id * 10 + 2).preguntaId(id).texto("Mala")
                        .puntaje(BigDecimal.ZERO).orden(2).build());
        return CalificacionDeLaPruebaPropia.calcularPregunta(p, opciones, r);
    }

    private static PreguntaDeLaPrueba abierta(long id, long criterio, RespuestaPrueba r) {
        Pregunta p = Pregunta.builder().id(id).tipo("ABIERTA").enunciado("Cuenta").puntos(0)
                .criterioBancoId(criterio).orden((int) id).build();
        return CalificacionDeLaPruebaPropia.calcularPregunta(p, List.of(), r);
    }

    private static CriterioDeLaPrueba criterio(long id, String nombre, int calificada, String calificador,
                                               List<PreguntaDeLaPrueba> preguntas,
                                               List<EntregableRequerido> mira, NotaCriterioPrueba nota) {
        int sistemaMaximo = preguntas.stream().filter(p -> !p.esAbierta()).mapToInt(PreguntaDeLaPrueba::maximo).sum();
        BigDecimal sistema = preguntas.stream().filter(p -> !p.esAbierta()).map(PreguntaDeLaPrueba::obtenido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        CriterioBanco c = CriterioBanco.builder().id(id).nombre(nombre).puntosCalificados(calificada)
                .calificador(calificador).build();
        return new CriterioDeLaPrueba(c, preguntas, mira, sistemaMaximo, sistema, calificada,
                calificada > 0 ? calificador : null, nota);
    }

    @Nested
    @DisplayName("Publicar")
    class Publicar {

        private final Instant ahora = Instant.now();
        private final Instant manana = ahora.plus(1, ChronoUnit.DAYS);

        @Test
        @DisplayName("90 puntos, un entregable que nadie califica y un criterio de IA que solo mira un enlace: tres faltas; sin enunciado no es falta (AC-02)")
        void lasTresFaltas() {
            EntregableRequerido tablero = entregable(1, "Tablero.xlsx", "ARCHIVO", true);
            EntregableRequerido video = entregable(2, "Video", "ENLACE", false);
            VersionBanco sinEnunciado = VersionBanco.builder().id(1L).modalidad("CRONOMETRADA")
                    .duracionMinutos(60).build();
            Resultado r = new Resultado(sinEnunciado, List.of(
                    criterio(10, "Conocimiento", 60, "IA", List.of(unica(1, 10, 10, null), abierta(2, 10, null)),
                            List.of(), null),
                    criterio(11, "Comunicación", 20, "IA", List.of(), List.of(video), null)),
                    List.of(), List.of(tablero, video));

            List<String> faltas = ReglasDeLaPrueba.faltasParaPublicar(r, manana, ahora);

            assertThat(faltas).hasSize(3);
            assertThat(faltas).anyMatch(f -> f.contains("suman 90 de 100"));
            assertThat(faltas).anyMatch(f -> f.contains("«Tablero.xlsx»: nadie lo califica"));
            assertThat(faltas).anyMatch(f -> f.contains("solo mira enlaces"));
            assertThat(faltas).noneMatch(f -> f.contains("enunciado"));
        }

        @Test
        @DisplayName("Un entregable y ningún enunciado, con todo lo demás en regla, se publica (AC-02)")
        void sinEnunciadoSePublica() {
            EntregableRequerido informe = general(3, "Informe", EntregableRequerido.TODA_LA_PRUEBA);
            VersionBanco sinEnunciado = VersionBanco.builder().id(1L).modalidad("PLAZO_ABIERTO").build();
            Resultado r = new Resultado(sinEnunciado, List.of(
                    criterio(10, "Todo", 90, "IA", List.of(unica(1, 10, 10, null)), List.of(informe), null)),
                    List.of(), List.of(informe));
            assertThat(ReglasDeLaPrueba.faltasParaPublicar(r, manana, ahora)).isEmpty();
        }

        @Test
        @DisplayName("Sin fecha límite o con una pasada no se publica, y la falta va junto al tiempo (AC-08)")
        void laFechaLimite() {
            Resultado r = new Resultado(VersionBanco.builder().id(1L).build(), List.of(
                    criterio(10, "Todo", 0, null, List.of(unica(1, 10, 100, null)), List.of(), null)),
                    List.of(), List.of());

            List<String> sinFecha = ReglasDeLaPrueba.faltasParaPublicar(r, null, ahora);
            assertThat(sinFecha).containsExactly(
                    "Falta el tiempo: elige «Cronometrada», con sus minutos, o «Sin cronómetro».",
                    ReglasDeLaPrueba.FALTA_LA_FECHA);
            assertThat(ReglasDeLaPrueba.faltasParaPublicar(r, ahora.minusSeconds(60), ahora))
                    .anyMatch(f -> f.contains("ya pasó"));
            assertThat(ReglasDeLaPrueba.faltaDeLaFecha(manana, ahora)).isNull();
            // La vista previa de una copia no mira la fecha: es de la vacante.
            assertThat(ReglasDeLaPrueba.faltasParaPublicar(r)).noneMatch(f -> f.contains("fecha"));
        }

        @Test
        @DisplayName("Una parte calificada sin calificador ni nada que mirar, y abiertas sin parte calificada")
        void parteCalificadaSinSentido() {
            Resultado r = new Resultado(VERSION, List.of(
                    criterio(10, "Sin calificador", 50, null, List.of(), List.of(), null),
                    criterio(11, "Abiertas sin puntos", 0, null,
                            List.of(unica(1, 11, 50, null), abierta(2, 11, null)), List.of(), null)),
                    List.of(), List.of());
            List<String> faltas = ReglasDeLaPrueba.faltasParaPublicar(r);
            assertThat(faltas).anyMatch(f -> f.contains("«Sin calificador»: falta decir quién califica"));
            assertThat(faltas).anyMatch(f -> f.contains("«Sin calificador»: su parte calificada no mira nada"));
            assertThat(faltas).anyMatch(f -> f.contains("«Abiertas sin puntos» tiene abiertas y su parte calificada no tiene puntos"));
        }

        @Test
        @DisplayName("Un general que solo cubre preguntas de criterios sin parte calificada: «nadie lo califica» (AC-06)")
        void generalQueNadieCalifica() {
            EntregableRequerido informe = general(3, "Informe", EntregableRequerido.PREGUNTAS);
            EntregableRequerido archivo = EntregableRequerido.builder().id(4L).nombre("Hoja.xlsx")
                    .formato("ARCHIVO").alcance(EntregableRequerido.PREGUNTA).preguntaId(1L).build();
            Resultado r = new Resultado(VERSION, List.of(
                    criterio(10, "Solo cerradas", 0, null, List.of(unica(1, 10, 100, null)),
                            List.of(informe, archivo), null)),
                    List.of(), List.of(informe, archivo), java.util.Map.of(3L, List.of(1L)));

            List<String> faltas = ReglasDeLaPrueba.faltasParaPublicar(r);

            assertThat(faltas).anyMatch(f -> f.startsWith("«Informe»: nadie lo califica. Ningún criterio"));
            assertThat(faltas).anyMatch(f -> f.startsWith("«Hoja.xlsx»: nadie lo califica. El criterio de su pregunta"));
            // Mirarlo sin parte calificada no es una falta del criterio.
            assertThat(faltas).noneMatch(f -> f.contains("«Solo cerradas»"));
        }

        @Test
        @DisplayName("Un general que ya no cubre ninguna pregunta sale como falta")
        void generalSinCubrir() {
            EntregableRequerido informe = general(3, "Informe", EntregableRequerido.PREGUNTAS);
            Resultado r = new Resultado(VERSION, List.of(
                    criterio(10, "Todo", 100, "IA", List.of(abierta(2, 10, null)), List.of(), null)),
                    List.of(), List.of(informe));
            assertThat(ReglasDeLaPrueba.faltasParaPublicar(r))
                    .contains("«Informe» no cubre ninguna pregunta: elige «Toda la prueba» o las preguntas que reúne.");
        }

        @Test
        @DisplayName("El tiempo: cronometrada con 5 minutos o más, o sin cronómetro y sin días (AC-12)")
        void elTiempo() {
            VersionBanco corto = VersionBanco.builder().id(1L).modalidad("CRONOMETRADA").duracionMinutos(3).build();
            assertThat(ReglasDeLaPrueba.tiempoParaPublicar(corto)).hasSize(1);
            assertThat(ReglasDeLaPrueba.tiempoParaPublicar(VersionBanco.builder().id(1L).build())).hasSize(1);
            assertThat(ReglasDeLaPrueba.tiempoParaPublicar(VersionBanco.builder().id(1L)
                    .modalidad("PLAZO_ABIERTO").build())).isEmpty();
            assertThat(ReglasDeLaPrueba.formaDelTiempo("CRONOMETRADA", 4)).hasSize(1);
            assertThat(ReglasDeLaPrueba.formaDelTiempo("PLAZO_ABIERTO", null)).isEmpty();
            assertThat(ReglasDeLaPrueba.formaDelTiempo(null, 30)).hasSize(1);
            assertThat(ReglasDeLaPrueba.formaDelTiempo("OTRA", null)).hasSize(1);
            assertThat(ReglasDeLaPrueba.formaDelTiempo(null, null)).isEmpty();
        }

        @Test
        @DisplayName("El alcance: de una pregunta o general, nunca las dos; un general cubre algo")
        void elAlcance() {
            assertThat(ReglasDeLaPrueba.formaDelAlcance(1L, false, List.of())).isEmpty();
            assertThat(ReglasDeLaPrueba.formaDelAlcance(null, true, List.of())).isEmpty();
            assertThat(ReglasDeLaPrueba.formaDelAlcance(null, false, List.of(2L))).isEmpty();
            assertThat(ReglasDeLaPrueba.formaDelAlcance(null, false, null)).hasSize(1);
            assertThat(ReglasDeLaPrueba.formaDelAlcance(1L, false, List.of(2L))).hasSize(1);
            assertThat(ReglasDeLaPrueba.formaDelAlcance(1L, true, null)).hasSize(1);
        }

        @Test
        @DisplayName("Unas cerradas que pasan de lo que vale el criterio: una sola falta para él, la que hay que arreglar primero (V69)")
        void cerradasPorEncima() {
            Resultado r = new Resultado(VERSION, List.of(
                    conTotal(10, "Excel", 30, "IA", List.of(unica(1, 10, 40, null), abierta(2, 10, null))),
                    conTotal(11, "Comunicación", 60, "PERSONA", List.of(abierta(3, 11, null)))),
                    List.of(), List.of());

            List<String> faltas = ReglasDeLaPrueba.faltasParaPublicar(r, manana, ahora);

            // La prueba suma lo que vale cada criterio, lo escrito: 30 + 60.
            assertThat(faltas).containsExactly("Los puntos suman 90 de 100: faltan 10.",
                    "Las cerradas de «Excel» suman 40 y el criterio vale 30.");
            assertThat(r.total()).isEqualTo(90);
            assertThat(r.criterios().get(0).cerradasPorEncima()).isTrue();
            assertThat(r.criterios().get(1).cerradasPorEncima()).isFalse();
        }
    }

    /** Un criterio de la V69: vale {@code total} y su parte calificada se deduce de sus cerradas. */
    private static CriterioDeLaPrueba conTotal(long id, String nombre, int total, String calificador,
                                               List<PreguntaDeLaPrueba> preguntas) {
        int sistemaMaximo = preguntas.stream().filter(p -> !p.esAbierta()).mapToInt(PreguntaDeLaPrueba::maximo).sum();
        CriterioBanco c = CriterioBanco.builder().id(id).nombre(nombre).puntosDelCriterio(total)
                .puntosCalificados(0).calificador(calificador).build();
        int calificada = CalificacionDeLaPruebaPropia.parteCalificada(c, sistemaMaximo);
        return new CriterioDeLaPrueba(c, preguntas, List.of(), sistemaMaximo, BigDecimal.ZERO, calificada,
                calificada > 0 ? calificador : null, null);
    }

    @Nested
    @DisplayName("Los puntos del criterio (V69)")
    class LosPuntosDelCriterio {

        @Test
        @DisplayName("Se escriben enteros, de 0 a 100, y siempre")
        void bienEscritos() {
            assertThat(ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(null, "IA", 0))
                    .containsExactly("Faltan los puntos del criterio: lo que vale entero, cerradas incluidas.");
            assertThat(ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(new BigDecimal("2.5"), "IA", 0))
                    .containsExactly("Los puntos del criterio tienen que ser un número entero, sin decimales.");
            assertThat(ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(BigDecimal.valueOf(101), "IA", 0))
                    .containsExactly("Los puntos del criterio van de 0 a 100.");
            assertThat(ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(BigDecimal.valueOf(-1), "IA", 0))
                    .containsExactly("Los puntos del criterio van de 0 a 100.");
        }

        @Test
        @DisplayName("QA-11: a la suma va lo escrito si es entero, aunque esté fuera de 0 a 100; con decimales, nada")
        void paraLaSuma() {
            assertThat(ReglasDeLaPrueba.paraLaSuma(BigDecimal.valueOf(150))).hasValue(150);
            assertThat(ReglasDeLaPrueba.paraLaSuma(new BigDecimal("30.00"))).hasValue(30);
            assertThat(ReglasDeLaPrueba.paraLaSuma(BigDecimal.valueOf(-5))).hasValue(-5);
            assertThat(ReglasDeLaPrueba.paraLaSuma(new BigDecimal("25.5"))).isEmpty();
            assertThat(ReglasDeLaPrueba.paraLaSuma(null)).isEmpty();
            assertThat(ReglasDeLaPrueba.paraLaSuma(new BigDecimal("1E+10"))).isEmpty();
        }

        @Test
        @DisplayName("Lo que no suman las cerradas necesita quién lo califique; si lo suman todo, o lo pasan, no se pregunta")
        void quienCalifica() {
            assertThat(ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(BigDecimal.valueOf(30), " ", 10))
                    .containsExactly("Falta decir quién califica los 20 puntos que no son de cerradas: la IA o una persona.");
            assertThat(ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(BigDecimal.valueOf(30), "PERSONA", 10)).isEmpty();
            assertThat(ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(BigDecimal.TEN, null, 10)).isEmpty();
            assertThat(ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(BigDecimal.valueOf(5), null, 10)).isEmpty();
            assertThat(ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(BigDecimal.TEN, "ROBOT", 0))
                    .containsExactly("Quien califica la parte calificada es «IA» o «PERSONA».");
        }

        @Test
        @DisplayName("La parte calificada es lo que vale menos sus cerradas, nunca negativa; sin total, la guardada")
        void laParteCalificada() {
            CriterioBanco conTotal = CriterioBanco.builder().puntosDelCriterio(30).puntosCalificados(99).build();
            assertThat(CalificacionDeLaPruebaPropia.parteCalificada(conTotal, 10)).isEqualTo(20);
            assertThat(CalificacionDeLaPruebaPropia.parteCalificada(conTotal, 40)).isZero();
            CriterioBanco deAntes = CriterioBanco.builder().puntosCalificados(25).build();
            assertThat(CalificacionDeLaPruebaPropia.parteCalificada(deAntes, 10)).isEqualTo(25);
            assertThat(CalificacionDeLaPruebaPropia.parteCalificada(CriterioBanco.builder().build(), 10)).isZero();
        }
    }

    @Nested
    @DisplayName("La cuenta de un criterio")
    class LaCuenta {

        @Test
        @DisplayName("Un mixto con solo su parte automática está pendiente y no tiene nota (AC-15)")
        void mixtoSinParteCalificadaEstaPendiente() {
            RespuestaPrueba buena = RespuestaPrueba.builder().opcionId(11L).build();
            CriterioDeLaPrueba c = criterio(10, "Mixto", 20, "IA", List.of(unica(1, 10, 10, buena)), List.of(), null);
            assertThat(c.sistema()).isEqualByComparingTo("10");
            assertThat(c.pendiente()).isTrue();
            assertThat(c.notaDelCriterio()).isNull();
            Resultado r = new Resultado(VERSION, List.of(c), List.of(), List.of());
            assertThat(r.nota()).isNull();
            assertThat(r.pendientes()).containsExactly("Mixto");
        }

        @Test
        @DisplayName("Con su parte calificada puesta, vale cerradas + calificada (AC-13)")
        void conLaCalificadaValeLaSuma() {
            RespuestaPrueba buena = RespuestaPrueba.builder().opcionId(11L).build();
            NotaCriterioPrueba nota = NotaCriterioPrueba.builder().puntaje(new BigDecimal("20")).build();
            CriterioDeLaPrueba c = criterio(10, "Mixto", 20, "IA", List.of(unica(1, 10, 10, buena)), List.of(), nota);
            assertThat(c.maximo()).isEqualTo(30);
            assertThat(c.notaDelCriterio()).isEqualByComparingTo("30");
        }
    }

    @Nested
    @DisplayName("Lo que falta para entregar")
    class LoQueFalta {

        @Test
        @DisplayName("Un texto en blanco no cuenta; una múltiple necesita una marcada; y el obligatorio sin subir (AC-10)")
        void loQueFalta() {
            RespuestaPrueba enBlanco = RespuestaPrueba.builder().texto("   ").build();
            Pregunta multiple = Pregunta.builder().id(3L).tipo("OPCION_MULTIPLE").enunciado("¿Cuáles?").puntos(10)
                    .criterioBancoId(10L).orden(3).build();
            Opcion a = Opcion.builder().id(31L).preguntaId(3L).texto("A").puntaje(BigDecimal.TEN).orden(1).build();
            PreguntaDeLaPrueba sinMarcar = CalificacionDeLaPruebaPropia.calcularPregunta(multiple, List.of(a),
                    RespuestaPrueba.builder().detalle("{\"marcadas\":[]}").build());
            PreguntaDeLaPrueba marcada = CalificacionDeLaPruebaPropia.calcularPregunta(multiple, List.of(a),
                    RespuestaPrueba.builder().detalle("{\"marcadas\":[31]}").build());
            assertThat(sinMarcar.respondida()).isFalse();
            assertThat(marcada.respondida()).isTrue();

            EntregableRequerido tablero = entregable(5, "Tablero.xlsx", "ARCHIVO", true);
            List<PreguntaDeLaPrueba> preguntas = new ArrayList<>(List.of(
                    unica(1, 10, 10, RespuestaPrueba.builder().opcionId(11L).build()),
                    abierta(2, 10, enBlanco), sinMarcar));
            Resultado r = new Resultado(VERSION, List.of(criterio(10, "C", 20, "IA", preguntas,
                    List.of(tablero), null)), List.of(), List.of(tablero));

            var falta = CalificacionDeLaPruebaPropia.loQueFalta(r, List.of());
            assertThat(falta.preguntas()).containsExactly(2, 3);
            assertThat(falta.entregables()).containsExactly("Tablero.xlsx");
            assertThat(falta.dicho()).isEqualTo("le faltaron 2 preguntas y «Tablero.xlsx»");

            var conElTablero = CalificacionDeLaPruebaPropia.loQueFalta(r,
                    List.of(Entregable.builder().entregableRequeridoId(5L).build()));
            assertThat(conElTablero.entregables()).isEmpty();
            assertThat(new CalificacionDeLaPruebaPropia.LoQueFalta(List.of(4), List.of()).dicho())
                    .isEqualTo("le faltó 1 pregunta");
            assertThat(new CalificacionDeLaPruebaPropia.LoQueFalta(List.of(), List.of("Tablero.xlsx")).dicho())
                    .isEqualTo("le faltó «Tablero.xlsx»");
        }
    }

    @Nested
    @DisplayName("La aduana de las recomendaciones")
    class LaAduana {

        private final InsumoRecomendadorPrueba insumo = new InsumoRecomendadorPrueba(null, 100, null,
                List.of("ABIERTA"), null, "CRONOMETRADA", 60, null, List.of(), List.of());

        @Test
        @DisplayName("Un criterio de IA que solo mira un enlace tiene que ser de persona (AC-21)")
        void soloEnlaceEsDePersona() {
            var resultado = new ResultadoRecomendadorPrueba(new CasoPropuesto("El caso", null, null),
                    List.of(new EntregablePropuesto("Video", null, "ENLACE", true, null)),
                    List.of(new CriterioPropuesto(null, "Comunicación", null, List.of(),
                            null, "IA", List.of(0), List.of(), new BigDecimal("100"))));
            assertThat(RecetaRecomendacionPrueba.validar(insumo, resultado))
                    .anyMatch(e -> e.contains("la IA no abre enlaces"));
        }

        @Test
        @DisplayName("Lo que suma bien, con su caso y lo que mira cada criterio, pasa")
        void loBuenoPasa() {
            var resultado = new ResultadoRecomendadorPrueba(new CasoPropuesto("El caso", null, null),
                    List.of(new EntregablePropuesto("Tablero", "La hoja", "ARCHIVO", true, null)),
                    List.of(new CriterioPropuesto(null, "Conocimiento", null, List.of(
                            new PreguntaPropuesta("OPCION_UNICA", "¿Qué libro?", new BigDecimal("40"), null,
                                    List.of(new OpcionPropuesta("Diario", new BigDecimal("40")),
                                            new OpcionPropuesta("Caja", BigDecimal.ZERO))),
                            new PreguntaPropuesta("ABIERTA", "¿Cómo lo hallaste?", BigDecimal.ZERO,
                                    "La cuenta", List.of())),
                            null, "IA", List.of(0), List.of(), new BigDecimal("100"))));
            assertThat(RecetaRecomendacionPrueba.validar(insumo, resultado)).isEmpty();
        }

        @Test
        @DisplayName("Un general que no cubre nada, y puntos que no suman")
        void generalSinCubrirYSumaMal() {
            var resultado = new ResultadoRecomendadorPrueba(null,
                    List.of(new EntregablePropuesto("Tablero", null, "ARCHIVO", true, null, null, false, List.of())),
                    List.of(new CriterioPropuesto(null, "Conocimiento", null, List.of(
                            new PreguntaPropuesta("ABIERTA", "¿Cómo?", BigDecimal.ZERO, null, List.of())),
                            new BigDecimal("50"), "IA", List.of(), List.of(), null)));
            List<String> errores = RecetaRecomendacionPrueba.validar(insumo, resultado);
            assertThat(errores).anyMatch(e -> e.contains("no cubre ninguna pregunta"));
            assertThat(errores).anyMatch(e -> e.contains("no lo califica nadie"));
            assertThat(errores).anyMatch(e -> e.contains("suman 50"));
        }

        @Test
        @DisplayName("El archivo de una pregunta de un criterio sin parte calificada: nadie lo califica (AC-21)")
        void archivoQueNadieCalifica() {
            var cerrada = new PreguntaPropuesta("OPCION_UNICA", "¿Qué libro?", new BigDecimal("100"), null,
                    List.of(new OpcionPropuesta("Diario", new BigDecimal("100")),
                            new OpcionPropuesta("Caja", BigDecimal.ZERO)));
            var resultado = new ResultadoRecomendadorPrueba(null,
                    List.of(new EntregablePropuesto("Hoja.xlsx", null, "ARCHIVO", true, null,
                            new PosicionDePregunta(0, 0), null, null)),
                    List.of(new CriterioPropuesto(null, "Solo cerradas", null, List.of(cerrada),
                            null, null, null, null, new BigDecimal("100"))));
            assertThat(RecetaRecomendacionPrueba.validar(insumo, resultado))
                    .containsExactly("el entregable 1 («Hoja.xlsx») no lo califica nadie: ningún criterio "
                            + "con parte calificada lo mira");
        }

        @Test
        @DisplayName("Sin caso, con el archivo dentro de su pregunta y un general que cubre dos de criterios distintos, pasa")
        void conAlcancePasa() {
            var abierta = new PreguntaPropuesta("ABIERTA", "Arma un flujo de caja", BigDecimal.ZERO, "Que cuadre", List.of());
            var otra = new PreguntaPropuesta("ABIERTA", "Explícalo a gerencia", BigDecimal.ZERO, null, List.of());
            var resultado = new ResultadoRecomendadorPrueba(null,
                    List.of(new EntregablePropuesto("Flujo.xlsx", null, "ARCHIVO", true, null,
                                    new PosicionDePregunta(0, 0), null, null),
                            new EntregablePropuesto("Informe", null, "ARCHIVO", true, null, null, false,
                                    List.of(new PosicionDePregunta(0, 0), new PosicionDePregunta(1, 0)))),
                    List.of(new CriterioPropuesto(null, "Excel", null, List.of(abierta),
                                    null, "IA", null, null, new BigDecimal("60")),
                            new CriterioPropuesto(null, "Comunicación", null, List.of(otra),
                                    null, "PERSONA", null, null, new BigDecimal("40"))));
            assertThat(RecetaRecomendacionPrueba.validar(insumo, resultado)).isEmpty();
        }

        @Test
        @DisplayName("Dos archivos para una pregunta, o uno de una pregunta que no existe, no pasan")
        void dosArchivosEnUnaPregunta() {
            var abierta = new PreguntaPropuesta("ABIERTA", "Arma un flujo de caja", BigDecimal.ZERO, null, List.of());
            var resultado = new ResultadoRecomendadorPrueba(null,
                    List.of(new EntregablePropuesto("A", null, "ARCHIVO", true, null, new PosicionDePregunta(0, 0), null, null),
                            new EntregablePropuesto("B", null, "ARCHIVO", true, null, new PosicionDePregunta(0, 0), null, null),
                            new EntregablePropuesto("C", null, "ARCHIVO", true, null, new PosicionDePregunta(3, 0), true, null)),
                    List.of(new CriterioPropuesto(null, "Excel", null, List.of(abierta),
                            null, "IA", null, null, new BigDecimal("100"))));
            List<String> errores = RecetaRecomendacionPrueba.validar(insumo, resultado);
            assertThat(errores).anyMatch(e -> e.contains("ya pide otro archivo"));
            assertThat(errores).anyMatch(e -> e.contains("una pregunta que no está en tu propuesta"));
            assertThat(errores).anyMatch(e -> e.contains("de una pregunta y general a la vez"));
        }

        private final PreguntaPropuesta cerradaDe20 = new PreguntaPropuesta("OPCION_UNICA", "¿Qué asiento?",
                new BigDecimal("20"), null, List.of(new OpcionPropuesta("Debe", new BigDecimal("20")),
                        new OpcionPropuesta("Haber", BigDecimal.ZERO)));
        private final PreguntaPropuesta abiertaNueva = new PreguntaPropuesta("ABIERTA", "¿Cómo cierras el mes?",
                BigDecimal.ZERO, "Los pasos", List.of());

        @Test
        @DisplayName("Un criterio nuevo dice lo que vale; si sus cerradas pasan de ahí, o no lo dice, no pasa (V69)")
        void loQueValeUnCriterioNuevo() {
            var porEncima = new ResultadoRecomendadorPrueba(null, List.of(), List.of(
                    new CriterioPropuesto(null, "Asientos", null, List.of(cerradaDe20, abiertaNueva),
                            null, "IA", null, null, BigDecimal.TEN),
                    new CriterioPropuesto(null, "Sin puntos", null, List.of(abiertaNueva),
                            null, "IA", null, null, null)));
            List<String> errores = RecetaRecomendacionPrueba.validar(insumo, porEncima);
            assertThat(errores).contains("el criterio 1: sus cerradas suman 20 y el criterio vale 10; sus "
                    + "puntos son todo lo que vale, cerradas incluidas");
            assertThat(errores).anyMatch(e -> e.startsWith("el criterio 2: Faltan los puntos del criterio"));

            var todoDeCerradas = new ResultadoRecomendadorPrueba(null, List.of(), List.of(
                    new CriterioPropuesto(null, "Asientos", null, List.of(cerradaDe20, abiertaNueva),
                            null, null, null, null, new BigDecimal("20"))));
            assertThat(RecetaRecomendacionPrueba.validar(insumo, todoDeCerradas))
                    .anyMatch(e -> e.contains("tiene abiertas y sus cerradas ya suman todo lo que vale"));
        }

        @Test
        @DisplayName("QA-11: un criterio nuevo sin puntos, o con decimales, dice su falta y no trae una suma que lo deja fuera")
        void unTotalMalEscritoNoDaSuma() {
            for (BigDecimal malo : java.util.Arrays.asList(null, new BigDecimal("25.5"))) {
                var resultado = new ResultadoRecomendadorPrueba(null, List.of(), List.of(
                        new CriterioPropuesto(null, "Cierre", null, List.of(abiertaNueva), null, "IA",
                                null, null, malo),
                        new CriterioPropuesto(null, "Asientos", null, List.of(cerradaDe20), null, null,
                                null, null, new BigDecimal("20"))));
                List<String> errores = RecetaRecomendacionPrueba.validar(insumo, resultado);
                assertThat(errores).anyMatch(e -> e.startsWith("el criterio 1: "));
                assertThat(errores).noneMatch(e -> e.startsWith("los puntos de tus criterios nuevos suman"));
            }
            // Un entero fuera de 0 a 100 se suma tal cual.
            var demasiado = new ResultadoRecomendadorPrueba(null, List.of(), List.of(
                    new CriterioPropuesto(null, "Cierre", null, List.of(abiertaNueva), null, "IA",
                            null, null, BigDecimal.valueOf(150))));
            assertThat(RecetaRecomendacionPrueba.validar(insumo, demasiado))
                    .contains("el criterio 1: Los puntos del criterio van de 0 a 100.")
                    .anyMatch(e -> e.startsWith("los puntos de tus criterios nuevos suman 150 y tienen que sumar "
                            + "exactamente 100"));
        }

        /** Un borrador con «Contable», que vale 30: 10 de cerradas y 20 de la IA, con una abierta. */
        private InsumoRecomendadorPrueba conUnCriterioDe30(int faltan) {
            var contable = new CriterioDelBorradorDePrueba(5L, "Contable", null, 10, 20, "IA", List.of(),
                    List.of(new PreguntaDelBorrador("ABIERTA", "¿Cómo cuadras?", 0),
                            new PreguntaDelBorrador("OPCION_UNICA", "¿Qué libro?", 10)), 30);
            return new InsumoRecomendadorPrueba(null, faltan, null, List.of("ABIERTA"), null, "CRONOMETRADA",
                    60, null, List.of(), List.of(contable));
        }

        @Test
        @DisplayName("Preguntas para un criterio del borrador: sigue valiendo lo mismo y sus cerradas salen de su parte calificada (V69)")
        void unCriterioDelBorradorSigueValiendoLoMismo() {
            var bien = new ResultadoRecomendadorPrueba(null, List.of(), List.of(
                    new CriterioPropuesto(5L, null, null, List.of(new PreguntaPropuesta("OPCION_UNICA",
                            "¿Qué cuenta?", new BigDecimal("15"), null, List.of(
                                    new OpcionPropuesta("Caja", new BigDecimal("15")),
                                    new OpcionPropuesta("Bancos", BigDecimal.ZERO))))),
                    new CriterioPropuesto(null, "Cierre", null, List.of(abiertaNueva), null, "PERSONA",
                            null, null, BigDecimal.TEN)));
            assertThat(RecetaRecomendacionPrueba.validar(conUnCriterioDe30(10), bien)).isEmpty();

            // Las cerradas no suman a lo que falta: 20 que faltan no se llenan en «Contable».
            var sinCriteriosNuevos = new ResultadoRecomendadorPrueba(null, List.of(), List.of(
                    new CriterioPropuesto(5L, null, null, List.of(new PreguntaPropuesta("OPCION_UNICA",
                            "¿Qué cuenta?", new BigDecimal("15"), null, List.of(
                                    new OpcionPropuesta("Caja", new BigDecimal("15")),
                                    new OpcionPropuesta("Bancos", BigDecimal.ZERO)))))));
            assertThat(RecetaRecomendacionPrueba.validar(conUnCriterioDe30(20), sinCriteriosNuevos))
                    .anyMatch(e -> e.startsWith("los puntos de tus criterios nuevos suman 0 y tienen que sumar "
                            + "exactamente 20"));
        }

        @Test
        @DisplayName("Unas cerradas que pasan, o se comen, la parte calificada de un criterio del borrador con abiertas no pasan (V69)")
        void cerradasQueNoCabenEnElCriterioDelBorrador() {
            var pasan = new ResultadoRecomendadorPrueba(null, List.of(), List.of(
                    new CriterioPropuesto(5L, null, null, List.of(cerradaDe20, new PreguntaPropuesta(
                            "OPCION_UNICA", "¿Y esta?", BigDecimal.TEN, null, List.of(
                                    new OpcionPropuesta("Sí", BigDecimal.TEN), new OpcionPropuesta("No", BigDecimal.ZERO)))))));
            assertThat(RecetaRecomendacionPrueba.validar(conUnCriterioDe30(0), pasan))
                    .anyMatch(e -> e.contains("pone 30 puntos de cerradas en un criterio del borrador que vale 30 "
                            + "y solo le quedan 20"));

            var seLaComen = new ResultadoRecomendadorPrueba(null, List.of(), List.of(
                    new CriterioPropuesto(5L, null, null, List.of(cerradaDe20))));
            assertThat(RecetaRecomendacionPrueba.validar(conUnCriterioDe30(0), seLaComen))
                    .containsExactly("el criterio 1 deja sin parte calificada a un criterio del borrador que "
                            + "tiene abiertas o archivos: nadie las calificaría");
        }
    }
}
