package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.OpcionAValidar;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.PreguntaAValidar;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La aduana de lo que propone el RECOMENDADOR (V66), como la del REDACTOR.
 *
 * <p>Lo que devuelve el modelo tiene que cumplir, y si no se le devuelve UNA vez con los
 * errores delante:
 * <ul>
 *   <li>los puntos son enteros y suman exactamente lo que le faltaba al borrador;</li>
 *   <li>cada pregunta está en exactamente un criterio, nuevo o del borrador;</li>
 *   <li>no hay criterios nuevos vacíos;</li>
 *   <li>los tipos son válidos y las cerradas traen sus opciones, con las mismas reglas que
 *       exige publicar (así lo que se agrega se puede publicar tal cual);</li>
 *   <li>no repite ninguna pregunta del borrador.</li>
 * </ul>
 */
public final class RecetaRecomendacion {

    private RecetaRecomendacion() {
    }

    /** Todos los errores, no solo el primero: es lo que se le devuelve al modelo. */
    public static List<String> validar(InsumoRecomendador insumo, ResultadoRecomendador resultado) {
        List<String> errores = new ArrayList<>();
        if (resultado == null || resultado.criterios() == null || resultado.criterios().isEmpty()) {
            errores.add("no devolviste ningún criterio");
            return errores;
        }

        Set<Long> delBorrador = new HashSet<>();
        Set<String> yaEscritas = new HashSet<>();
        for (CriterioDelBorrador c : lista(insumo.criteriosDelBorrador())) {
            delBorrador.add(c.id());
            lista(c.preguntas()).forEach(p -> yaEscritas.add(normal(p.enunciado())));
        }
        lista(insumo.preguntasSinCriterio()).forEach(p -> yaEscritas.add(normal(p.enunciado())));

        Set<Long> existentesUsados = new HashSet<>();
        Set<String> propuestas = new HashSet<>();
        int suma = 0;
        int nCriterio = 1;
        for (CriterioPropuesto c : resultado.criterios()) {
            String cual = "el criterio " + nCriterio++;
            List<PreguntaPropuesta> suyas = lista(c.preguntas());
            if (c.criterioExistenteId() != null) {
                if (!delBorrador.contains(c.criterioExistenteId())) {
                    errores.add(cual + " dice ir al criterio " + c.criterioExistenteId()
                            + " del borrador, y el borrador no tiene ese criterio");
                } else if (!existentesUsados.add(c.criterioExistenteId())) {
                    errores.add("el criterio " + c.criterioExistenteId()
                            + " del borrador aparece dos veces: junta sus preguntas en uno");
                }
                if (suyas.isEmpty()) {
                    errores.add(cual + " va a un criterio del borrador y no trae ninguna pregunta");
                }
            } else {
                if (c.nombre() == null || c.nombre().isBlank()) {
                    errores.add(cual + " es nuevo y no tiene nombre");
                } else if (c.nombre().length() > 120) {
                    errores.add(cual + " tiene un nombre de más de 120 caracteres");
                }
                if (suyas.isEmpty()) {
                    errores.add(cual + " es nuevo y no tiene preguntas: un criterio vacío no sirve");
                }
            }
            if (c.queEvalua() != null && c.queEvalua().length() > ReglasDePuntos.MAXIMO_TEXTO_IA) {
                errores.add(cual + ": «qué evalúa» pasa de " + ReglasDePuntos.MAXIMO_TEXTO_IA
                        + " caracteres");
            }

            int nPregunta = 1;
            for (PreguntaPropuesta p : suyas) {
                String donde = "En " + cual + ", la pregunta " + nPregunta++;
                PreguntaAValidar aValidar = new PreguntaAValidar(p.tipo(), p.enunciado(), p.puntos(),
                        p.queDebeTener(), lista(p.opciones()).stream()
                                .map(o -> new OpcionAValidar(o.texto(), o.puntos())).toList());
                errores.addAll(ReglasDePuntos.formaDeLaPregunta(donde, aValidar));
                errores.addAll(ReglasDePuntos.puntuacionDeLaPregunta(donde, aValidar));
                if (p.puntos() != null && ReglasDePuntos.esEntero(p.puntos())) {
                    suma += p.puntos().intValue();
                }
                String normal = normal(p.enunciado());
                if (!normal.isEmpty() && yaEscritas.contains(normal)) {
                    errores.add(donde + " repite una pregunta que ya está en el borrador");
                } else if (!normal.isEmpty() && !propuestas.add(normal)) {
                    errores.add(donde + " está repetida en tu propuesta");
                }
            }
        }
        if (suma != insumo.puntosQueFaltan()) {
            errores.add("los puntos de tus preguntas suman " + suma + " y tienen que sumar "
                    + "exactamente " + insumo.puntosQueFaltan() + ", lo que le falta al borrador "
                    + "para llegar a 100");
        }
        return errores;
    }

    /** Los puntos que ya tiene el borrador. */
    public static int puntosDelBorrador(List<CriterioDelBorrador> criterios,
                                        List<PreguntaDelBorrador> sinCriterio) {
        int suma = lista(sinCriterio).stream().mapToInt(PreguntaDelBorrador::puntos).sum();
        for (CriterioDelBorrador c : lista(criterios)) {
            suma += c.puntos();
        }
        return suma;
    }

    private static String normal(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").toLowerCase(Locale.ROOT).strip();
    }

    private static <T> List<T> lista(List<T> valor) {
        return valor == null ? List.of() : valor;
    }

    /** Para quien lo necesite: los puntos de una pregunta propuesta como entero seguro. */
    public static int entero(BigDecimal puntos) {
        return puntos == null ? 0 : puntos.intValue();
    }
}
