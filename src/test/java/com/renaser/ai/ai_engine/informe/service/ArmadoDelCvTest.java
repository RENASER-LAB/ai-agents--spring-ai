package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Experiencia;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Recuento;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.DeUnaPersona;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.Encontrado;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.LineaVigente;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.service.RecorridoDeLaVacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El informe del currículum armado con los repositorios simulados: cuándo se puede descargar
 * (también el de un CV que no se pudo leer), la nota con «Qué buscar en el CV», los criterios con
 * sus ajustes y huecos, los bloques y la experiencia con lo que falte dicho como «sin dato».
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("El armado del informe del currículum")
class ArmadoDelCvTest {

    private static final Long POSTULACION = 10L;
    private static final Instant AHORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final String TEXTO_LEGIBLE = "Analista de selección con cinco años en retail y planillas.";

    @Mock private CriterioRepository criterios;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private PesoCriterioRepository pesosCriterio;
    @Mock private LoBuscadoEnElCv loBuscado;
    @Mock private RecorridoDeLaVacante recorridos;

    @InjectMocks private ArmadoDelCv armado;

    @Nested
    @DisplayName("Cuándo se puede descargar")
    class Estado {

        @Test
        @DisplayName("con algún criterio calificado está disponible con la nota del CV")
        void conNota() {
            criteriosDelCv();
            when(notasCriterio.findByPostulacionId(POSTULACION)).thenReturn(List.of(nota(1L, 80, "AGENTE", null)));
            when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(3L, "ANALISTA")).thenReturn(
                    List.of(peso(1L, 60), peso(2L, 40)));

            EstadoDeUnInforme estado = armado.estado(base(vacante(), puesto(), cv(TEXTO_LEGIBLE), "TERMINADA"));

            assertThat(estado.disponible()).isTrue();
            assertThat(estado.nota()).isEqualByComparingTo("80");
        }

        @Test
        @DisplayName("sin notas: calificando, o disponible sin nota si el CV no tenía texto que leer")
        void sinNotas() {
            assertThat(armado.estado(base(null, null, cv(TEXTO_LEGIBLE), "EN_CURSO")).motivo())
                    .isEqualTo(MotivosDelInforme.CALIFICANDO);
            assertThat(armado.estado(base(null, null, cv("  corto  "), "TERMINADA")))
                    .isEqualTo(new EstadoDeUnInforme("CV", true, null, null));
            assertThat(armado.estado(base(null, null, cv(null), "FALLIDA")).disponible()).isTrue();
        }

        @Test
        @DisplayName("sin notas y con un CV legible: falló si la lectura falló; si no, todavía no tiene calificación")
        void sinNotasConCvLegible() {
            assertThat(armado.estado(base(null, null, cv(TEXTO_LEGIBLE), "FALLIDA")).motivo())
                    .isEqualTo(MotivosDelInforme.FALLO);
            assertThat(armado.estado(base(null, null, null, "FALLIDA")).motivo())
                    .isEqualTo(MotivosDelInforme.FALLO);
            assertThat(armado.estado(base(null, null, cv(TEXTO_LEGIBLE), "TERMINADA")).motivo())
                    .isEqualTo(MotivosDelInforme.SIN_CALIFICAR);
            assertThat(armado.estado(base(null, null, null, "SIN_EMPEZAR")).motivo())
                    .isEqualTo(MotivosDelInforme.SIN_CALIFICAR);
        }
    }

    @Nested
    @DisplayName("El informe")
    class Armar {

        @Test
        @DisplayName("la nota combina los criterios con lo buscado, y cada línea buscada dice si la tiene, la menciona o no aparece")
        void conLoBuscado() {
            criteriosDelCv();
            when(notasCriterio.findByPostulacionId(POSTULACION)).thenReturn(List.of(
                    nota(1L, 80, "AGENTE", " el puesto pide nómina "), nota(99L, 10, "AGENTE", null)));
            when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(3L, "ANALISTA")).thenReturn(
                    List.of(peso(1L, 60), peso(2L, 40)));
            List<LineaVigente> lineas = List.of(new LineaVigente(21L, 1, "Sabe SAP", 1),
                    new LineaVigente(22L, 2, "Inglés", 1), new LineaVigente(23L, 3, "Excel", 1));
            when(loBuscado.vigentes(5L)).thenReturn(lineas);
            when(loBuscado.deLaTanda(any(), any())).thenReturn(Map.of(POSTULACION, new DeUnaPersona("TERMINADA",
                    null, Map.of(21L, new Encontrado("TIENE", " uso SAP a diario ", null, null),
                    22L, new Encontrado("MENCIONA", " ", null, " ¿Cuánto inglés? ")))));
            when(loBuscado.pesoDe(3L)).thenReturn(BigDecimal.valueOf(20));
            Base base = new Base(postulacion(), vacante(), puesto(), "Renaser", "Ana Torres", "ana@correo.pe",
                    cv(TEXTO_LEGIBLE), dato(), List.of(
                    afirmacion("DEMOSTRADA", " Bajó la rotación 20 % "), afirmacion("DEMOSTRADA", " "),
                    afirmacion("CONTRADICHA", "Jefa en 2022"), afirmacion("DECLARADA", "Excel avanzado")),
                    null, List.of(), List.of(), "TERMINADA");

            Informe informe = armado.armar(base, AHORA);

            // Criterios 80 con pesos 60/40 (el segundo sin nota) y lo buscado (100 + 50 + 0) / 3 con peso 20.
            assertThat(informe.nota().valor()).isEqualByComparingTo("74");
            assertThat(informe.nota().base()).isEqualTo("Los criterios del currículum con los pesos de su nivel "
                    + "y «Qué buscar en el CV», que pesa el 20 %.");
            assertThat(informe.aviso()).isNull();

            List<Criterio> filas = informe.evaluado().criterios();
            assertThat(filas).extracting(Criterio::nombre).containsExactly("Resultados", "Habilidades");
            assertThat(filas.get(0).maximoTexto()).isNull();
            assertThat(filas.get(0).quien()).isEqualTo("Persona");
            assertThat(filas.get(0).motivoAjuste()).isEqualTo("el puesto pide nómina");
            assertThat(filas.get(1).maximoTexto()).isEqualTo("/ 20");
            assertThat(filas.get(1).quien()).isNull();
            assertThat(filas.get(1).pendiente()).isEqualTo("Sin nota todavía");

            assertThat(informe.recuentos()).extracting(Recuento::cuantos).containsExactly(1, 1, 1);
            List<Bloque> bloques = informe.bloques();
            assertThat(bloques).extracting(Bloque::codigo).containsExactly("DEMUESTRA", "NO_CUADRA", "PENDIENTE",
                    ArmadoDelCv.BLOQUE_LO_BUSCADO);
            assertThat(bloques.get(0).puntos()).extracting(Punto::texto).containsExactly("Bajó la rotación 20 %");
            assertThat(bloques.get(3).puntos()).extracting(Punto::texto, Punto::etiqueta, Punto::detalle)
                    .containsExactly(tuple("Sabe SAP", "Lo tiene", "«uso SAP a diario»"),
                            tuple("Inglés", "Lo menciona", "¿Cuánto inglés?"),
                            tuple("Excel", "No aparece", null));

            Experiencia experiencia = informe.experiencia();
            assertThat(experiencia.total()).isEqualTo("4 años y 2 meses");
            assertThat(experiencia.ultimoPuesto()).isEqualTo("Analista de Selección");
            assertThat(experiencia.ultimoDetalle()).isEqualTo("Retail Sur · 2 años y 5 meses");
            assertThat(experiencia.formacion()).isEqualTo("Licenciada en Psicología");
            assertThat(experiencia.habilidades()).containsExactly("Excel", "SAP");
            assertThat(experiencia.perfil()).isEqualTo("Selección para retail.");
        }

        @Test
        @DisplayName("con la búsqueda sin terminar cada línea está pendiente, y lo buscado sin peso no cambia la base de la nota")
        void busquedaSinTerminar() {
            criteriosDelCv();
            when(loBuscado.vigentes(5L)).thenReturn(List.of(new LineaVigente(21L, 1, "Sabe SAP", 1)));
            when(loBuscado.deLaTanda(any(), any())).thenReturn(Map.of(POSTULACION,
                    new DeUnaPersona("EN_CURSO", null, Map.of())));
            when(loBuscado.pesoDe(3L)).thenReturn(BigDecimal.ZERO);

            Informe informe = armado.armar(base(vacante(), null, cv(TEXTO_LEGIBLE), "TERMINADA"), AHORA);

            assertThat(informe.nota().valor()).isNull();
            assertThat(informe.nota().base()).isEqualTo("Los criterios del currículum con los pesos de su nivel.");
            assertThat(informe.bloques().get(3).puntos()).extracting(Punto::etiqueta, Punto::detalle)
                    .containsExactly(tuple("Pendiente", null));

            when(loBuscado.deLaTanda(any(), any())).thenReturn(Map.of());
            assertThat(armado.armar(base(vacante(), null, cv(TEXTO_LEGIBLE), "TERMINADA"), AHORA)
                    .bloques().get(3).puntos().get(0).etiqueta()).isEqualTo("Pendiente");
        }

        @Test
        @DisplayName("un CV que no se pudo leer avisa antes que nada y no inventa nada; sin datos, la experiencia dice «sin dato»")
        void cvIlegible() {
            criteriosDelCv();

            Informe informe = armado.armar(base(null, null, cv("escaneado"), "FALLIDA"), AHORA);

            assertThat(informe.aviso()).startsWith("El currículum no se pudo leer");
            assertThat(informe.bloques()).hasSize(3);
            assertThat(informe.nota().texto()).isEqualTo("—");
            assertThat(informe.experiencia()).isEqualTo(new Experiencia(ArmadoDelCv.SIN_DATO, null,
                    ArmadoDelCv.SIN_DATO, null, ArmadoDelCv.SIN_DATO, List.of(), null));
        }
    }

    @Nested
    @DisplayName("Lo que queda por verificar")
    class LoPendiente {

        @Test
        @DisplayName("con evaluación pasa a la evaluación, sin mirar el recorrido")
        void conEvaluacion() {
            criteriosDelCv();

            Informe informe = armado.armar(base(vacante(), null, cv(TEXTO_LEGIBLE), "TERMINADA"), AHORA);

            assertThat(pendiente(informe).titulo()).isEqualTo("Por verificar en la evaluación");
            verify(recorridos, never()).de(5L);
        }

        @ParameterizedTest(name = "{0} → «{1}»")
        @CsvSource(delimiter = '|', value = {
                "PERFIL_INTEGRAL PRUEBA_PUESTO DECISION | Por verificar en la prueba del puesto",
                "PERFIL_INTEGRAL SIMULACION PRUEBA_PUESTO | Por verificar en la simulación de trabajo",
                "PERFIL_INTEGRAL DECISION | Por verificar en la decisión",
                "PERFIL_INTEGRAL | Por verificar en la entrevista"})
        @DisplayName("en una vacante sin evaluación (V30) pasa a la etapa que sigue al Perfil Integral (V95)")
        void sinEvaluacion(String recorrido, String titulo) {
            criteriosDelCv();
            when(recorridos.de(5L)).thenReturn(Arrays.asList(recorrido.split(" ")));
            Vacante sinEvaluacion = Vacante.builder().id(5L).titulo("Analista").versionPesosId(3L)
                    .aplicaEvaluacion(false).build();
            Postulacion suya = Postulacion.builder().id(POSTULACION).vacanteId(5L).build();
            Base base = new Base(suya, sinEvaluacion, null, "Renaser", "Ana Torres", null, cv(TEXTO_LEGIBLE),
                    null, List.of(), null, List.of(), List.of(), "TERMINADA");

            assertThat(pendiente(armado.armar(base, AHORA)).titulo()).isEqualTo(titulo);
        }

        @Test
        @DisplayName("si rindió una evaluación, pasa a ella aunque la vacante ya no la aplique")
        void conEvaluacionRendida() {
            criteriosDelCv();
            Vacante sinEvaluacion = Vacante.builder().id(5L).aplicaEvaluacion(false).build();
            Postulacion suya = Postulacion.builder().id(POSTULACION).vacanteId(5L).evaluacionId(50L).build();
            Base base = new Base(suya, sinEvaluacion, null, "Renaser", "Ana Torres", null, cv(TEXTO_LEGIBLE),
                    null, List.of(), null, List.of(), List.of(), "TERMINADA");

            assertThat(pendiente(armado.armar(base, AHORA)).titulo()).isEqualTo("Por verificar en la evaluación");
        }

        private Bloque pendiente(Informe informe) {
            return informe.bloques().stream().filter(b -> "PENDIENTE".equals(b.codigo())).findFirst().orElseThrow();
        }
    }

    @Test
    @DisplayName("la experiencia con todo en blanco dice «sin dato» en cada hueco y no inventa habilidades")
    void experienciaEnBlanco() {
        Experiencia experiencia = ArmadoDelCv.experiencia(DatoCv.builder().ultimaEmpresa(" ").habilidades(" | ")
                .educacionMaxima(" ").perfilResumen(" ").build());

        assertThat(experiencia).isEqualTo(new Experiencia(ArmadoDelCv.SIN_DATO, null, ArmadoDelCv.SIN_DATO, null,
                ArmadoDelCv.SIN_DATO, List.of(), null));
        assertThat(ArmadoDelCv.experiencia(DatoCv.builder().ultimaMesesDuracion(3).habilidades("  ").build())
                .ultimoDetalle()).isEqualTo("3 meses");
    }

    // ================================================================= ayudas

    private void criteriosDelCv() {
        when(criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden("PERFIL_INTEGRAL")).thenReturn(
                List.of(com.renaser.ai.ai_engine.perfilintegral.entity.Criterio.builder().id(1L).nombre("Resultados")
                                .build(),
                        com.renaser.ai.ai_engine.perfilintegral.entity.Criterio.builder().id(2L).nombre("Habilidades")
                                .puntos(BigDecimal.valueOf(20)).build()));
    }

    private static NotaCriterio nota(Long criterioId, int puntaje, String origen, String motivoAjuste) {
        return NotaCriterio.builder().criterioId(criterioId).puntaje(BigDecimal.valueOf(puntaje)).origen(origen)
                .explicacion(" Explicación ").motivoAjuste(motivoAjuste)
                .ajustadaPorUsuarioId(motivoAjuste == null ? null : 9L).build();
    }

    private static PesoCriterio peso(Long criterioId, int peso) {
        return PesoCriterio.builder().criterioId(criterioId).peso(BigDecimal.valueOf(peso)).build();
    }

    private static Postulacion postulacion() {
        return Postulacion.builder().id(POSTULACION).build();
    }

    private static Vacante vacante() {
        return Vacante.builder().id(5L).titulo("Analista").versionPesosId(3L).build();
    }

    private static Puesto puesto() {
        return Puesto.builder().nivelPuestoCodigo("ANALISTA").build();
    }

    private static Cv cv(String texto) {
        return Cv.builder().id(7L).textoExtraido(texto).build();
    }

    private static DatoCv dato() {
        return DatoCv.builder().experienciaMesesTotal(50).ultimoPuesto(" Analista de Selección ")
                .ultimaEmpresa(" Retail Sur ").ultimaMesesDuracion(29).habilidades("Excel | SAP || ")
                .educacionMaxima(" Licenciada en Psicología ").perfilResumen(" Selección para retail. ").build();
    }

    private static AfirmacionCv afirmacion(String clase, String texto) {
        return AfirmacionCv.builder().clasificacion(clase).texto(texto).build();
    }

    private static Base base(Vacante vacante, Puesto puesto, Cv cv, String comoVa) {
        return new Base(postulacion(), vacante, puesto, "Renaser", "Ana Torres", null, cv, null, List.of(), null,
                List.of(), List.of(), comoVa);
    }
}
