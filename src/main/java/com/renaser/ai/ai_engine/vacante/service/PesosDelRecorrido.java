package com.renaser.ai.ai_engine.vacante.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuánto pesa cada etapa en una vacante concreta (V95): los pesos de su versión, repartidos entre
 * las etapas de su recorrido.
 *
 * <p><b>La versión no se toca.</b> Para cada etapa del recorrido que tiene peso en la versión, su
 * peso es el de la versión dividido entre la suma de los de esas etapas, por 100, con dos
 * decimales; la última que pesa absorbe el redondeo para que el total dé 100,00. Las etapas sin
 * fila en la versión —la Decisión, o la Simulación con la v3— no cuentan. Con la v4
 * (40/30/15/15) y Perfil → Prueba → Decisión: 57,14 y 42,86, el mismo reparto que ya traía la v3.
 *
 * <p><b>Con todas las etapas de la versión en el recorrido, los pesos son los de la versión tal
 * cual</b>: las vacantes de siempre dan exactamente lo mismo que antes de la V95.
 *
 * <p>Es la regla única: la usan la nota global de la Decisión y la tabla «Cómo pesa en esta
 * vacante» de la tuerca. Si dos sitios la repitieran, la tabla y el semáforo podrían contar cosas
 * distintas.
 */
public final class PesosDelRecorrido {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private PesosDelRecorrido() {}

    /**
     * Los pesos de las etapas del recorrido que tienen fila en la versión, en el orden del
     * recorrido. Vacío si ninguna etapa del recorrido pesa en la versión.
     *
     * @param recorrido  los códigos de etapa de la vacante, en orden
     * @param laVersion  el peso de cada etapa en la versión de pesos de la vacante
     */
    public static Map<String, BigDecimal> repartir(List<String> recorrido,
                                                   Map<String, BigDecimal> laVersion) {
        Map<String, BigDecimal> dentro = new LinkedHashMap<>();
        for (String etapa : recorrido) {
            BigDecimal peso = laVersion.get(etapa);
            if (peso != null) {
                dentro.put(etapa, peso);
            }
        }
        if (dentro.isEmpty() || dentro.size() == laVersion.size()) {
            return dentro;
        }
        BigDecimal suma = dentro.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.signum() <= 0) {
            return dentro;
        }
        String ultimaQuePesa = null;
        for (Map.Entry<String, BigDecimal> e : dentro.entrySet()) {
            if (e.getValue().signum() > 0) {
                ultimaQuePesa = e.getKey();
            }
        }
        Map<String, BigDecimal> repartidos = new LinkedHashMap<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : dentro.entrySet()) {
            if (e.getKey().equals(ultimaQuePesa)) {
                continue;
            }
            BigDecimal suyo = e.getValue().multiply(CIEN).divide(suma, 2, RoundingMode.HALF_UP);
            repartidos.put(e.getKey(), suyo);
            acumulado = acumulado.add(suyo);
        }
        Map<String, BigDecimal> enOrden = new LinkedHashMap<>();
        for (String etapa : dentro.keySet()) {
            enOrden.put(etapa, etapa.equals(ultimaQuePesa)
                    ? CIEN.subtract(acumulado).setScale(2, RoundingMode.HALF_UP)
                    : repartidos.get(etapa));
        }
        return enOrden;
    }
}
