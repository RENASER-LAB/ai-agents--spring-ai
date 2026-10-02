package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CasoPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregablePropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendadorPrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta;
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

        @Test
        @DisplayName("90 puntos, un entregable suelto, un criterio de IA que solo mira un enlace y sin enunciado: cuatro faltas (AC-04)")
        void lasCuatroFaltas() {
            EntregableRequerido tablero = entregable(1, "Tablero.xlsx", "ARCHIVO", true);
            EntregableRequerido video = entregable(2, "Video", "ENLACE", false);
            VersionBanco sinEnunciado = VersionBanco.builder().id(1L).modalidad("CRONOMETRADA")
                    .duracionMinutos(60).build();
            Resultado r = new Resultado(sinEnunciado, List.of(
                    criterio(10, "Conocimiento", 60, "IA", List.of(unica(1, 10, 10, null), abierta(2, 10, null)),
                            List.of(), null),
                    criterio(11, "Comunicación", 20, "IA", List.of(), List.of(video), null)),
                    List.of(), List.of(tablero, video));

            List<String> faltas = ReglasDeLaPrueba.faltasParaPublicar(r);

            assertThat(faltas).hasSize(4);
            assertThat(faltas).anyMatch(f -> f.contains("suman 90 de 100"));
            assertThat(faltas).anyMatch(f -> f.contains("«Tablero.xlsx» no está en ningún criterio"));
            assertThat(faltas).anyMatch(f -> f.contains("solo mira enlaces"));
            assertThat(faltas).anyMatch(f -> f.contains("enunciado"));
        }

        @Test
        @DisplayName("Sin entregables es un cuestionario y el enunciado no hace falta (AC-06)")
        void sinEntregablesNoHaceFaltaEnunciado() {
            VersionBanco sinEnunciado = VersionBanco.builder().id(1L).modalidad("CRONOMETRADA")
                    .duracionMinutos(30).build();
            Resultado r = new Resultado(sinEnunciado, List.of(
                    criterio(10, "Todo", 0, null, List.of(unica(1, 10, 100, null)), List.of(), null)),
                    List.of(), List.of());
            assertThat(r.esCuestionario()).isTrue();
            assertThat(ReglasDeLaPrueba.faltasParaPublicar(r)).isEmpty();
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
            assertThat(faltas).anyMatch(f -> f.contains("«Abiertas sin puntos» tiene abiertas o entregables"));
        }

        @Test
        @DisplayName("Un tiempo fuera de límites no se publica")
        void tiempoFueraDeLimites() {
            VersionBanco corto = VersionBanco.builder().id(1L).modalidad("CRONOMETRADA").duracionMinutos(3).build();
            assertThat(ReglasDeLaPrueba.tiempoParaPublicar(corto)).hasSize(1);
            assertThat(ReglasDeLaPrueba.tiempoParaPublicar(VersionBanco.builder().id(1L).build())).hasSize(1);
            assertThat(ReglasDeLaPrueba.formaDelTiempo("PLAZO_ABIERTO", null, 0)).hasSize(1);
            assertThat(ReglasDeLaPrueba.formaDelTiempo(null, null, null)).isEmpty();
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
                            new BigDecimal("100"), "IA", List.of(0), List.of())));
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
                            new BigDecimal("60"), "IA", List.of(0), List.of())));
            assertThat(RecetaRecomendacionPrueba.validar(insumo, resultado)).isEmpty();
        }

        @Test
        @DisplayName("Un entregable propuesto que no mira nadie, y puntos que no suman")
        void entregableSueltoYSumaMal() {
            var resultado = new ResultadoRecomendadorPrueba(new CasoPropuesto("El caso", null, null),
                    List.of(new EntregablePropuesto("Tablero", null, "ARCHIVO", true, null)),
                    List.of(new CriterioPropuesto(null, "Conocimiento", null, List.of(
                            new PreguntaPropuesta("ABIERTA", "¿Cómo?", BigDecimal.ZERO, null, List.of())),
                            new BigDecimal("50"), "IA", List.of(), List.of())));
            List<String> errores = RecetaRecomendacionPrueba.validar(insumo, resultado);
            assertThat(errores).anyMatch(e -> e.contains("no está en ningún criterio"));
            assertThat(errores).anyMatch(e -> e.contains("suman 50"));
        }
    }
}
