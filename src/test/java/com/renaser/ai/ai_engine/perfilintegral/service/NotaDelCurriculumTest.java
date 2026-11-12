package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("La nota del CV con «Qué buscar en el CV»")
class NotaDelCurriculumTest {

    @Test
    @DisplayName("AC-08: 80 en los criterios y 62,5 en lo buscado con p = 25 dan 75,625")
    void elEjemploDeLaSpec() {
        BigDecimal loBuscado = NotaDelCurriculum.deLoBuscado(
                List.of("TIENE", "MENCIONA", "MENCIONA", "TIENE"));
        assertThat(loBuscado).isEqualByComparingTo("75");
        BigDecimal sesentaYDosYMedio = NotaDelCurriculum.deLoBuscado(
                List.of("TIENE", "MENCIONA", "NO_APARECE", "TIENE"));
        assertThat(sesentaYDosYMedio).isEqualByComparingTo("62.5");

        assertThat(NotaDelCurriculum.combinar(new BigDecimal("80.00"), sesentaYDosYMedio,
                BigDecimal.valueOf(25))).isEqualByComparingTo("75.625");
    }

    @Test
    @DisplayName("AC-09: sin búsqueda terminada o con p = 0 es exactamente la de hoy")
    void sinLoBuscadoEsLaDeHoy() {
        BigDecimal deHoy = new BigDecimal("71.37");
        assertThat(NotaDelCurriculum.combinar(deHoy, null, BigDecimal.valueOf(25))).isSameAs(deHoy);
        assertThat(NotaDelCurriculum.combinar(deHoy, BigDecimal.valueOf(100), BigDecimal.ZERO))
                .isSameAs(deHoy);
        assertThat(NotaDelCurriculum.combinar(null, BigDecimal.valueOf(100), BigDecimal.TEN)).isNull();
    }

    @Test
    @DisplayName("Una nota redonda sale sin decimales de más")
    void sinCerosDeMas() {
        assertThat(NotaDelCurriculum.combinar(BigDecimal.valueOf(80), BigDecimal.valueOf(100),
                BigDecimal.valueOf(50)).toPlainString()).isEqualTo("90");
    }

    @Test
    @DisplayName("Lo buscado: Lo tiene 100, Lo menciona 50, No aparece 0; sin líneas, nada")
    void loBuscado() {
        assertThat(NotaDelCurriculum.deLoBuscado(List.of("NO_APARECE", "NO_APARECE"))).isEqualByComparingTo("0");
        assertThat(NotaDelCurriculum.deLoBuscado(List.of("TIENE"))).isEqualByComparingTo("100");
        assertThat(NotaDelCurriculum.deLoBuscado(List.of())).isNull();
    }

    @Test
    @DisplayName("Los criterios generales: solo los del currículum, divididos entre los que tienen nota")
    void criteriosGenerales() {
        List<NotaCriterio> notas = List.of(
                NotaCriterio.builder().criterioId(1L).puntaje(BigDecimal.valueOf(80)).build(),
                NotaCriterio.builder().criterioId(2L).puntaje(BigDecimal.valueOf(60)).build(),
                NotaCriterio.builder().criterioId(9L).puntaje(BigDecimal.valueOf(10)).build());
        Map<Long, BigDecimal> pesos = Map.of(1L, BigDecimal.valueOf(30), 2L, BigDecimal.valueOf(10),
                9L, BigDecimal.valueOf(60));
        assertThat(NotaDelCurriculum.deLosCriterios(notas, pesos, Set.of(1L, 2L, 3L)))
                .isEqualByComparingTo("75.00");
        assertThat(NotaDelCurriculum.deLosCriterios(List.of(), pesos, Set.of(1L))).isNull();
    }
}
