package com.renaser.ai.ai_engine.vacante.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Los pesos repartidos entre las etapas del recorrido (V95)")
class PesosDelRecorridoTest {

    private static Map<String, BigDecimal> version(Object... codigoYPeso) {
        Map<String, BigDecimal> pesos = new LinkedHashMap<>();
        for (int i = 0; i < codigoYPeso.length; i += 2) {
            pesos.put((String) codigoYPeso[i], new BigDecimal((String) codigoYPeso[i + 1]));
        }
        return pesos;
    }

    private static final Map<String, BigDecimal> V4 = version(
            "PERFIL_INTEGRAL", "40", "PRUEBA_PUESTO", "30", "SIMULACION", "15", "VALIDACION", "15");

    @Test
    @DisplayName("la v4 en Perfil → Prueba → Decisión da 57,14 y 42,86 (AC-11)")
    void elRepartoDeLaV4() {
        Map<String, BigDecimal> pesos = PesosDelRecorrido.repartir(
                List.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "DECISION"), V4);

        assertThat(pesos).containsOnlyKeys("PERFIL_INTEGRAL", "PRUEBA_PUESTO");
        assertThat(pesos.get("PERFIL_INTEGRAL")).isEqualByComparingTo("57.14");
        assertThat(pesos.get("PRUEBA_PUESTO")).isEqualByComparingTo("42.86");
    }

    @Test
    @DisplayName("con todas las etapas de la versión, los pesos son los de la versión tal cual (AC-12)")
    void conTodasLaVersionTalCual() {
        Map<String, BigDecimal> pesos = PesosDelRecorrido.repartir(
                List.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "SIMULACION", "VALIDACION", "DECISION"), V4);

        assertThat(pesos).isEqualTo(V4);
    }

    @Test
    @DisplayName("sin Prueba y con una versión de Perfil y Prueba, el Perfil pesa 100")
    void elPerfilSoloPesaCien() {
        Map<String, BigDecimal> pesos = PesosDelRecorrido.repartir(List.of("PERFIL_INTEGRAL", "DECISION"),
                version("PERFIL_INTEGRAL", "57.14", "PRUEBA_PUESTO", "42.86"));

        assertThat(pesos).containsOnlyKeys("PERFIL_INTEGRAL");
        assertThat(pesos.get("PERFIL_INTEGRAL")).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("el total da 100,00 y la última que pesa absorbe el redondeo, en el orden del recorrido")
    void elTotalDaCien() {
        Map<String, BigDecimal> pesos = PesosDelRecorrido.repartir(
                List.of("PERFIL_INTEGRAL", "SIMULACION", "VALIDACION", "DECISION"), V4);

        // 40/70 = 57,14 · 15/70 = 21,43 · y la Validación, lo que falta: 21,43
        assertThat(pesos.keySet()).containsExactly("PERFIL_INTEGRAL", "SIMULACION", "VALIDACION");
        assertThat(pesos.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");
        assertThat(pesos.get("SIMULACION")).isEqualByComparingTo("21.43");
        assertThat(pesos.get("VALIDACION")).isEqualByComparingTo("21.43");
    }

    @Test
    @DisplayName("una etapa del recorrido sin fila en la versión no cuenta")
    void sinFilaNoCuenta() {
        Map<String, BigDecimal> pesos = PesosDelRecorrido.repartir(
                List.of("PERFIL_INTEGRAL", "SIMULACION", "PRUEBA_PUESTO", "DECISION"),
                version("PERFIL_INTEGRAL", "57.14", "PRUEBA_PUESTO", "42.86"));

        assertThat(pesos).containsOnlyKeys("PERFIL_INTEGRAL", "PRUEBA_PUESTO");
        assertThat(pesos.get("PERFIL_INTEGRAL")).isEqualByComparingTo("57.14");
    }

    @Test
    @DisplayName("una etapa a cero no se queda con el redondeo")
    void unaEtapaACeroNoAbsorbe() {
        Map<String, BigDecimal> pesos = PesosDelRecorrido.repartir(
                List.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "SIMULACION", "DECISION"),
                version("PERFIL_INTEGRAL", "40", "PRUEBA_PUESTO", "30", "SIMULACION", "0", "VALIDACION", "30"));

        assertThat(pesos.get("SIMULACION")).isEqualByComparingTo("0.00");
        assertThat(pesos.get("PERFIL_INTEGRAL").add(pesos.get("PRUEBA_PUESTO")))
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("sin pesos de etapa en la versión no hay nada que repartir")
    void sinPesos() {
        assertThat(PesosDelRecorrido.repartir(List.of("PERFIL_INTEGRAL"), Map.of())).isEmpty();
    }
}
