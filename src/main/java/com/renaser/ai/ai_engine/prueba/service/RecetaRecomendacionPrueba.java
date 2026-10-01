package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioDelBorradorDePrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregableDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregablePropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendadorPrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendadorPrueba;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.OpcionAValidar;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.PreguntaAValidar;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * La aduana de lo que propone el RECOMENDADOR para una prueba técnica (V67).
 *
 * <p>Las reglas de la fase 1 ({@code RecetaRecomendacion}) más las del punto 3 de la spec,
 * para que lo que se agregue se pueda publicar tal cual: los puntos de las cerradas y de las
 * partes calificadas suman exactamente lo que faltaba; un criterio con parte calificada
 * dice quién la califica y mira algo; uno con abiertas o entregables tiene parte calificada;
 * todo entregable propuesto está en algún criterio; y <b>un criterio que solo mira
 * entregables de enlace, sin abiertas, tiene que ser de persona</b>: la IA no abre enlaces.
 * Si no cuadra, se le devuelve UNA vez con los errores delante.
 */
public final class RecetaRecomendacionPrueba {

    private RecetaRecomendacionPrueba() {
    }

    public static List<String> validar(InsumoRecomendadorPrueba insumo, ResultadoRecomendadorPrueba resultado) {
        List<String> errores = new ArrayList<>();
        if (resultado == null || lista(resultado.criterios()).isEmpty()) {
            errores.add("no devolviste ningún criterio");
            return errores;
        }

        // Lo que ya hay en el borrador.
        Map<Long, CriterioDelBorradorDePrueba> delBorrador = new HashMap<>();
        Set<String> yaEscritas = new HashSet<>();
        for (CriterioDelBorradorDePrueba c : lista(insumo.criteriosDelBorrador())) {
            delBorrador.put(c.id(), c);
            lista(c.preguntas()).forEach(p -> yaEscritas.add(normal(p.enunciado())));
        }
        Map<Long, EntregableDelBorrador> entregablesDelBorrador = new HashMap<>();
        Set<String> nombresDeEntregables = new HashSet<>();
        for (EntregableDelBorrador e : lista(insumo.entregablesDelBorrador())) {
            entregablesDelBorrador.put(e.id(), e);
            nombresDeEntregables.add(normal(e.nombre()));
        }

        // Los entregables propuestos.
        List<EntregablePropuesto> propuestos = lista(resultado.entregables());
        int nEntregable = 1;
        for (EntregablePropuesto e : propuestos) {
            String cual = "el entregable " + nEntregable++;
            ReglasDeLaPrueba.formaDelEntregable(e.nombre(), e.formato())
                    .forEach(f -> errores.add(cual + ": " + f));
            if (e.nombre() != null && !e.nombre().isBlank() && !nombresDeEntregables.add(normal(e.nombre()))) {
                errores.add(cual + " repite el nombre de otro entregable");
            }
            if (e.queDebeTener() != null && e.queDebeTener().length() > ReglasDePuntos.MAXIMO_TEXTO_IA) {
                errores.add(cual + ": «qué debe tener una buena entrega» pasa de "
                        + ReglasDePuntos.MAXIMO_TEXTO_IA + " caracteres");
            }
        }

        // El caso: sin enunciado en el borrador, y con entregables, hace falta uno.
        boolean hayEntregables = !entregablesDelBorrador.isEmpty() || !propuestos.isEmpty();
        boolean sinEnunciado = insumo.enunciadoActual() == null || insumo.enunciadoActual().isBlank();
        boolean casoPropuesto = resultado.caso() != null && resultado.caso().enunciado() != null
                && !resultado.caso().enunciado().isBlank();
        if (hayEntregables && sinEnunciado && !casoPropuesto) {
            errores.add("la prueba pide entregables y no tiene caso: propón el enunciado en «caso»");
        }
        if (casoPropuesto && resultado.caso().enunciado().length() > 10_000) {
            errores.add("el enunciado del caso pasa de 10000 caracteres");
        }

        Set<Integer> entregablesMirados = new HashSet<>();
        Set<Long> existentesUsados = new HashSet<>();
        Set<String> propuestas = new HashSet<>();
        int suma = 0;
        int nCriterio = 1;
        for (CriterioPropuesto c : resultado.criterios()) {
            String cual = "el criterio " + nCriterio++;
            List<PreguntaPropuesta> suyas = lista(c.preguntas());
            boolean tieneAbiertas = suyas.stream().anyMatch(p -> ReglasDePuntos.ABIERTA.equals(p.tipo()));
            if (c.criterioExistenteId() != null) {
                if (!delBorrador.containsKey(c.criterioExistenteId())) {
                    errores.add(cual + " dice ir al criterio " + c.criterioExistenteId()
                            + " del borrador, y el borrador no tiene ese criterio");
                } else if (!existentesUsados.add(c.criterioExistenteId())) {
                    errores.add("el criterio " + c.criterioExistenteId()
                            + " del borrador aparece dos veces: junta sus preguntas en uno");
                }
                if (suyas.isEmpty()) {
                    errores.add(cual + " va a un criterio del borrador y no trae ninguna pregunta");
                }
                CriterioDelBorradorDePrueba existente = delBorrador.get(c.criterioExistenteId());
                if (existente != null && tieneAbiertas && existente.parteCalificada() <= 0) {
                    errores.add(cual + " pone abiertas en un criterio del borrador sin parte "
                            + "calificada: nadie las calificaría");
                }
            } else {
                if (c.nombre() == null || c.nombre().isBlank()) {
                    errores.add(cual + " es nuevo y no tiene nombre");
                } else if (c.nombre().length() > 120) {
                    errores.add(cual + " tiene un nombre de más de 120 caracteres");
                }
                BigDecimal parte = c.parteCalificada() == null ? BigDecimal.ZERO : c.parteCalificada();
                ReglasDeLaPrueba.formaDeLaParteCalificada(parte, c.calificador())
                        .forEach(f -> errores.add(cual + ": " + f));
                // Lo que mira: entregables propuestos (por posición) o del borrador (por id).
                List<String> formatos = new ArrayList<>();
                for (Integer i : lista(c.entregables())) {
                    if (i == null || i < 0 || i >= propuestos.size()) {
                        errores.add(cual + " mira el entregable " + i + ", que no está en tu propuesta");
                    } else {
                        entregablesMirados.add(i);
                        formatos.add(propuestos.get(i).formato());
                    }
                }
                for (Long id : lista(c.entregablesExistentes())) {
                    EntregableDelBorrador e = entregablesDelBorrador.get(id);
                    if (e == null) {
                        errores.add(cual + " mira el entregable " + id + " del borrador, y el "
                                + "borrador no lo tiene");
                    } else {
                        formatos.add(e.formato());
                    }
                }
                boolean conParte = parte.signum() > 0;
                boolean miraAlgo = !formatos.isEmpty();
                if (conParte && !tieneAbiertas && !miraAlgo) {
                    errores.add(cual + " tiene parte calificada y no mira nada: dale una abierta o "
                            + "un entregable");
                }
                if (!conParte && (tieneAbiertas || miraAlgo)) {
                    errores.add(cual + " tiene abiertas o entregables y su parte calificada no "
                            + "tiene puntos");
                }
                if (!conParte && suyas.stream().noneMatch(p -> ReglasDePuntos.esCerrada(p.tipo()))) {
                    errores.add(cual + " está vacío: no tiene cerradas ni parte calificada");
                }
                if (conParte && CalificacionDeLaPruebaPropia.IA.equals(c.calificador()) && !tieneAbiertas
                        && miraAlgo && formatos.stream().allMatch("ENLACE"::equals)) {
                    errores.add(cual + " solo mira entregables de enlace, sin abiertas, y la IA no "
                            + "abre enlaces: tiene que calificarlo una persona");
                }
                if (ReglasDePuntos.esEntero(parte)) {
                    suma += parte.intValue();
                }
            }
            if (c.queEvalua() != null && c.queEvalua().length() > ReglasDePuntos.MAXIMO_TEXTO_IA) {
                errores.add(cual + ": «qué evalúa» pasa de " + ReglasDePuntos.MAXIMO_TEXTO_IA
                        + " caracteres");
            }

            int nPregunta = 1;
            for (PreguntaPropuesta p : suyas) {
                String donde = "En " + cual + ", la pregunta " + nPregunta++;
                boolean abierta = ReglasDePuntos.ABIERTA.equals(p.tipo());
                PreguntaAValidar aValidar = new PreguntaAValidar(p.tipo(), p.enunciado(),
                        abierta && p.puntos() == null ? BigDecimal.ZERO : p.puntos(),
                        p.queDebeTener(), lista(p.opciones()).stream()
                                .map(o -> new OpcionAValidar(o.texto(), o.puntos())).toList());
                errores.addAll(ReglasDeLaPrueba.formaDeLaPregunta(donde, aValidar));
                if (!abierta) {
                    errores.addAll(ReglasDePuntos.puntuacionDeLaPregunta(donde, aValidar));
                    if (p.puntos() != null && ReglasDePuntos.esEntero(p.puntos())) {
                        suma += p.puntos().intValue();
                    }
                }
                String normal = normal(p.enunciado());
                if (!normal.isEmpty() && yaEscritas.contains(normal)) {
                    errores.add(donde + " repite una pregunta que ya está en el borrador");
                } else if (!normal.isEmpty() && !propuestas.add(normal)) {
                    errores.add(donde + " está repetida en tu propuesta");
                }
            }
        }
        for (int i = 0; i < propuestos.size(); i++) {
            if (!entregablesMirados.contains(i)) {
                errores.add("el entregable " + (i + 1) + " («" + propuestos.get(i).nombre()
                        + "») no está en ningún criterio");
            }
        }
        if (suma != insumo.puntosQueFaltan()) {
            errores.add("los puntos de tus cerradas y de tus partes calificadas suman " + suma
                    + " y tienen que sumar exactamente " + insumo.puntosQueFaltan()
                    + ", lo que le falta a la prueba para llegar a 100");
        }
        return errores;
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
}
