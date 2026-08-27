package com.renaser.ai.ai_engine.perfilintegral.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Las fórmulas con las que se puntúa el banco CAZATALENTOS.
 *
 * <p>Sueltas y sin dependencias por el mismo motivo que {@link FormulasBancoV3}: deciden la
 * nota de una persona, así que tienen que poder probarse contra los ejemplos del documento
 * de la clienta —los candidatos A, B, C y D de la parte 1.4— sin levantar una base de datos
 * ni un contexto de Spring.
 *
 * <p><b>El puntaje del ítem es el conteo de criterios presentes</b>, de 0 a 4, con dos
 * compuertas previas (parte 1.3 del documento):
 * <ol>
 *   <li>si la respuesta cumple la SEÑAL DE 0 de esa pregunta, el puntaje es 0 y se acaba
 *       el cálculo;</li>
 *   <li>sin C1 no hay nada que contar: «si solo explica cómo actúa "en general", C1 está
 *       ausente y el puntaje máximo es 0» (hoja «Cómo se califica»).</li>
 * </ol>
 *
 * <p>Quién pone cada cosa: el agente dice qué criterios vio; el conteo lo hace este código.
 * Así la aritmética no depende del modelo, y un criterio guardado vale después para las
 * banderas del cuestionario completo.
 *
 * <p>Ver docs/CAZATALENTOS-BANCO-RENASER.md y docs/insumos/CAZATALENTOS-sistema-de-filtro.md,
 * partes 1 y 8.
 */
public final class FormulasCazatalentos {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    /** El techo de un ítem de peso 1. Con peso 2 llega a 8 — no es el 0–3 del banco v3. */
    public static final int TECHO_DEL_ITEM = 4;

    private FormulasCazatalentos() {
    }

    /**
     * El puntaje de una respuesta: 0 si cumple la señal de 0 o no hay episodio; si no, el
     * número de criterios presentes.
     */
    public static int puntaje(boolean cumpleSenalCero, boolean c1Episodio, boolean c2Autoria,
                              boolean c3Dato, boolean c4Incomodidad) {
        if (cumpleSenalCero || !c1Episodio) {
            return 0;
        }
        return 1 + (c2Autoria ? 1 : 0) + (c3Dato ? 1 : 0) + (c4Incomodidad ? 1 : 0);
    }

    /**
     * Igual, con la REGLA DURA de R11: «sin ninguna cifra, el máximo de esta pregunta es 2».
     * El tope solo actúa cuando falta el dato duro (C3), y viene de la pregunta como dato,
     * no de un condicional por código de pregunta.
     *
     * @param topeSinDato el máximo si C3 está ausente, o null si la pregunta no declara tope
     */
    public static int puntaje(boolean cumpleSenalCero, boolean c1Episodio, boolean c2Autoria,
                              boolean c3Dato, boolean c4Incomodidad, Integer topeSinDato) {
        int base = puntaje(cumpleSenalCero, c1Episodio, c2Autoria, c3Dato, c4Incomodidad);
        if (topeSinDato != null && !c3Dato) {
            return Math.min(base, topeSinDato);
        }
        return base;
    }

    /** Lo que el ítem aporta al pilar: su puntaje por su peso. Peso 0 no suma (Z01..Z03). */
    public static int conPeso(int puntajeDelItem, int peso) {
        return puntajeDelItem * peso;
    }

    /**
     * Puntaje de pilar (%): puntos obtenidos ÷ (4 × Σ pesos del pilar) × 100.
     *
     * <p>El documento dice «4 × n° de preguntas del pilar», escrito cuando todas pesaban 1.
     * Con pesos, un ítem de peso 2 aporta doble arriba y doble abajo: si no, R11 con un 4
     * sumaría 8 sobre un máximo que solo cuenta 4, y el pilar pasaría de 100.
     *
     * @param puntosObtenidos Σ (puntaje × peso) de las preguntas del pilar
     * @param sumaDePesos     Σ pesos de las preguntas puntuables del pilar
     */
    public static BigDecimal puntajePilar(int puntosObtenidos, int sumaDePesos) {
        return puntajePilar(BigDecimal.valueOf(puntosObtenidos), sumaDePesos);
    }

    /**
     * La misma cuenta con decimales: una nota ajustada a mano puede traerlos (un 2.5), y
     * truncarla a entero perdería media unidad del pilar sin que nadie lo viera.
     */
    public static BigDecimal puntajePilar(BigDecimal puntosObtenidos, int sumaDePesos) {
        if (sumaDePesos <= 0) {
            return BigDecimal.ZERO;
        }
        return puntosObtenidos
                .divide(BigDecimal.valueOf((long) TECHO_DEL_ITEM * sumaDePesos), 10, RoundingMode.HALF_UP)
                .multiply(CIEN)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Índice RENASER: Σ (puntaje de pilar × peso del pilar) ÷ 100.
     *
     * <p>Los pesos vienen de {@code peso_dimension} y suman 100 sin Integridad, que es
     * eliminatoria y no pondera. Un pilar con peso y sin puntaje es un error de datos, no
     * un cero: callarlo daría un índice bajo sin que nadie sepa por qué.
     *
     * @param puntajePorPilar el % de cada pilar, por código de dimensión
     * @param pesoPorPilar    el peso de cada pilar para este nivel, por código de dimensión
     */
    public static BigDecimal indice(Map<String, BigDecimal> puntajePorPilar,
                                    Map<String, BigDecimal> pesoPorPilar) {
        BigDecimal suma = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> peso : pesoPorPilar.entrySet()) {
            BigDecimal delPilar = puntajePorPilar.get(peso.getKey());
            if (delPilar == null) {
                throw new IllegalArgumentException(
                        "El pilar " + peso.getKey() + " tiene peso pero ninguna pregunta puntuada");
            }
            suma = suma.add(delPilar.multiply(peso.getValue()));
        }
        return suma.divide(CIEN, 2, RoundingMode.HALF_UP);
    }
}
