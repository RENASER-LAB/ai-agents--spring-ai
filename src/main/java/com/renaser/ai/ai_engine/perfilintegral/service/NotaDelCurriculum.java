package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La nota del CV, en un solo sitio (V80).
 *
 * <p>La calculaban por su cuenta tres sitios —la nota del Perfil Integral, el ranking y lo que
 * recibe {@code POTENCIAL_RIESGO}— y desde «Qué buscar en el CV» los tres tienen que decir el
 * mismo número. La regla:
 *
 * <ul>
 *   <li><b>Criterios generales</b>: cada uno de los ocho por su peso del nivel, divididos entre
 *       los pesos de los que sí tienen nota. Es la nota del CV de siempre.
 *   <li><b>Lo buscado</b>: el promedio de las líneas, con Lo tiene = 100, Lo menciona = 50 y No
 *       aparece = 0.
 *   <li><b>La nota del CV</b> = criterios generales × (1 − p) + lo buscado × p, con p el peso
 *       de la versión de pesos entre 100. Sin líneas, con la búsqueda pendiente, fallida o con
 *       el CV sin texto, o con p = 0, es exactamente la de los criterios generales.
 * </ul>
 *
 * <p>El peso del CV dentro del Perfil Integral no cambia: lo buscado entra en la nota del CV,
 * no encima de ella.
 */
public final class NotaDelCurriculum {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal CINCUENTA = BigDecimal.valueOf(50);

    private NotaDelCurriculum() {
    }

    /**
     * Los criterios generales, sobre 100. Solo los del currículum: la misma versión de pesos
     * trae también los de la simulación y la validación, y sumarlos cambiaría la nota del CV
     * cada vez que alguien califica otra etapa.
     *
     * @return nulo si ningún criterio del currículum tiene nota con peso
     */
    public static BigDecimal deLosCriterios(Collection<NotaCriterio> notas,
                                            Map<Long, BigDecimal> pesos,
                                            Set<Long> delCurriculum) {
        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal pesoTotal = BigDecimal.ZERO;
        for (NotaCriterio nota : notas) {
            if (!delCurriculum.contains(nota.getCriterioId())) {
                continue;
            }
            BigDecimal peso = pesos.get(nota.getCriterioId());
            if (peso == null || nota.getPuntaje() == null) {
                continue;
            }
            suma = suma.add(nota.getPuntaje().multiply(peso));
            pesoTotal = pesoTotal.add(peso);
        }
        return pesoTotal.signum() == 0 ? null : suma.divide(pesoTotal, 2, RoundingMode.HALF_UP);
    }

    /**
     * Lo buscado, sobre 100: el promedio de las líneas. Un estado que no es ninguno de los tres
     * cuenta como «No aparece».
     *
     * @return nulo sin líneas
     */
    public static BigDecimal deLoBuscado(List<String> estados) {
        if (estados == null || estados.isEmpty()) {
            return null;
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (String estado : estados) {
            if ("TIENE".equals(estado)) {
                suma = suma.add(CIEN);
            } else if ("MENCIONA".equals(estado)) {
                suma = suma.add(CINCUENTA);
            }
        }
        return suma.divide(BigDecimal.valueOf(estados.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * La nota del CV.
     *
     * @param generales la de los criterios generales; sin ella no hay nota del CV
     * @param loBuscado la de lo buscado, o nulo si no hay líneas o la búsqueda no terminó
     * @param peso      el peso de «Qué buscar en el CV» de la versión de pesos, en %, de 0 a 50
     */
    public static BigDecimal combinar(BigDecimal generales, BigDecimal loBuscado, BigDecimal peso) {
        if (generales == null) {
            return null;
        }
        if (loBuscado == null || peso == null || peso.signum() <= 0) {
            return generales;
        }
        BigDecimal p = peso.min(CIEN);
        BigDecimal nota = generales.multiply(CIEN.subtract(p))
                .add(loBuscado.multiply(p))
                .divide(CIEN, 3, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return nota.scale() < 0 ? nota.setScale(0, RoundingMode.UNNECESSARY) : nota;
    }
}
