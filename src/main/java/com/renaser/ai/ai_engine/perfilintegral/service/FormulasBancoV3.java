package com.renaser.ai.ai_engine.perfilintegral.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Las siete fórmulas con las que se puntúa el banco v3.
 *
 * <p>Están aquí, sueltas y sin dependencias, a propósito: son las que deciden la nota de una
 * persona, así que tienen que poder probarse una a una contra los ejemplos del documento del
 * cliente sin levantar una base de datos ni un contexto de Spring.
 *
 * <p><b>Todas devuelven un valor entre 0 y 3.</b> Eso es un ítem de peso 1. El peso se aplica
 * después multiplicando: peso 2 llega a 6, y peso 0 no suma nada. Son las dos únicas
 * multiplicaciones del sistema, dice el documento en su sección 0.1.
 *
 * <p>Ver docs/insumos/banco-renaser-v3-completo.pdf, sección 0.2, y
 * docs/AVANCE-BANCO-V3-2026-08-19.md.
 */
public final class FormulasBancoV3 {

    private static final BigDecimal TRES = BigDecimal.valueOf(3);
    private static final BigDecimal DOS = BigDecimal.valueOf(2);
    /** Lo que cuesta marcar como propio un elemento que no existe, en INV. */
    private static final BigDecimal CASTIGO_POR_FALSO = new BigDecimal("1.5");
    /** Desde cuántos falsos marcados el candidato queda señalado por inflar. */
    public static final int FALSOS_PARA_BANDERA = 2;

    private FormulasBancoV3() {
    }

    /**
     * EF-4 · Elección forzada. Se marca la afirmación MÁS parecida a uno y la MENOS, y cada
     * una esconde un valor de −2 a +2. La resta da un bruto de −4 a +4 que cae en un tramo.
     *
     * <p>No es una escala continua: el documento fija cuatro tramos y son los que valen.
     */
    public static BigDecimal ef4(int valorMas, int valorMenos) {
        int bruto = valorMas - valorMenos;
        if (bruto <= -2) return BigDecimal.ZERO;
        if (bruto <= 0) return BigDecimal.ONE;
        if (bruto <= 2) return DOS;
        return TRES;
    }

    /**
     * SJT-R · Situacional con calificación. El candidato puntúa cada opción del 1 al 5 y se
     * compara con la clave: acertar de lleno vale 2, fallar por uno vale 1, y de dos en
     * adelante no vale nada.
     *
     * @param respuestas lo que puso el candidato, por letra de opción
     * @param claves     lo que debía poner, por letra de opción
     */
    public static BigDecimal sjtR(Map<String, Integer> respuestas, Map<String, Integer> claves) {
        if (claves.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int suma = 0;
        for (Map.Entry<String, Integer> clave : claves.entrySet()) {
            Integer dada = respuestas.get(clave.getKey());
            if (dada == null) {
                continue;   // sin responder no resta, simplemente no suma
            }
            suma += Math.max(0, 2 - Math.abs(dada - clave.getValue()));
        }
        return BigDecimal.valueOf(suma)
                .divide(BigDecimal.valueOf(2L * claves.size()), 10, RoundingMode.HALF_UP)
                .multiply(TRES)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * SEC · Ordenamiento. Cinco pasos que hay que poner en su orden. No se mira si el orden
     * es idéntico —eso sería todo o nada— sino cuántas parejas quedaron en el orden correcto,
     * de las diez posibles. Así, equivocarse en un paso no arruina el ítem entero.
     *
     * @param orden    los pasos como los ordenó el candidato, por su número original
     * @param correcto los pasos en el orden bueno
     */
    public static BigDecimal sec(List<Integer> orden, List<Integer> correcto) {
        if (orden.size() < 2 || correcto.size() < 2) {
            return BigDecimal.ZERO;
        }
        int posibles = 0;
        int acertadas = 0;
        for (int i = 0; i < correcto.size(); i++) {
            for (int j = i + 1; j < correcto.size(); j++) {
                int a = orden.indexOf(correcto.get(i));
                int b = orden.indexOf(correcto.get(j));
                if (a < 0 || b < 0) {
                    continue;
                }
                posibles++;
                if (a < b) {
                    acertadas++;
                }
            }
        }
        if (posibles == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(acertadas)
                .divide(BigDecimal.valueOf(posibles), 10, RoundingMode.HALF_UP)
                .multiply(TRES)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * INV · Inventario con distractores. La lista mezcla herramientas reales con otras
     * inventadas que el candidato no puede distinguir. Marcar una inventada cuesta 1.5, más
     * de lo que suma acertar una real: reconocer lo que uno no conoce vale más que aparentar.
     *
     * @param realesMarcados  cuántas reales marcó
     * @param realesTotales   cuántas reales había
     * @param falsosMarcados  cuántas inventadas marcó
     */
    public static BigDecimal inv(int realesMarcados, int realesTotales, int falsosMarcados) {
        if (realesTotales <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal ganado = BigDecimal.valueOf(realesMarcados)
                .divide(BigDecimal.valueOf(realesTotales), 10, RoundingMode.HALF_UP)
                .multiply(TRES);
        BigDecimal castigo = CASTIGO_POR_FALSO.multiply(BigDecimal.valueOf(falsosMarcados));
        return ganado.subtract(castigo).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Marcar dos o más elementos inventados es una bandera de inflación para la entrevista. */
    public static boolean banderaDeInflacion(int falsosMarcados) {
        return falsosMarcados >= FALSOS_PARA_BANDERA;
    }

    /**
     * DE · Detección de error. Ocho afirmaciones, siempre cuatro ciertas y cuatro falsas.
     * Marcar una falsa descuenta una acertada: si no, bastaría con marcarlo todo.
     */
    public static BigDecimal de(int correctasMarcadas, int incorrectasMarcadas) {
        return BigDecimal.valueOf(correctasMarcadas - incorrectasMarcadas)
                .divide(BigDecimal.valueOf(4), 10, RoundingMode.HALF_UP)
                .multiply(TRES)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * CD · Caso descompuesto. Un caso real partido en campos cerrados. No se juzga el relato:
     * se cuenta cuántos campos quedaron válidos —completos, dentro de rango y coherentes
     * entre sí— sobre el total.
     */
    public static BigDecimal cd(int camposValidos, int camposTotales) {
        if (camposTotales <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(camposValidos)
                .divide(BigDecimal.valueOf(camposTotales), 10, RoundingMode.HALF_UP)
                .multiply(TRES)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * El peso convierte el 0-3 del ítem en lo que de verdad aporta: peso 1 hasta 3 puntos,
     * peso 2 hasta 6, peso 0 nada. El documento no admite otros pesos.
     */
    public static BigDecimal conPeso(BigDecimal puntajeDelItem, int peso) {
        return puntajeDelItem.multiply(BigDecimal.valueOf(peso)).setScale(2, RoundingMode.HALF_UP);
    }
}
