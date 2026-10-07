package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioDelBorradorDePrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregableDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregablePropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendadorPrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PosicionDePregunta;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * La aduana de lo que propone el RECOMENDADOR para una prueba técnica (V67, V68).
 *
 * <p>Las reglas de la fase 1 ({@code RecetaRecomendacion}) más las del punto 12 de la spec,
 * para que lo que se agregue se pueda publicar tal cual: <b>lo que valen los criterios nuevos</b>
 * (V69) suma exactamente lo que faltaba; un criterio del borrador sigue valiendo lo mismo, y
 * sus cerradas nuevas salen de su parte calificada sin pasarla; la parte calificada de uno
 * nuevo —lo que vale menos sus cerradas— no es negativa, dice quién la califica y mira algo;
 * uno con abiertas tiene parte calificada; todo
 * entregable propuesto tiene su alcance —de una pregunta, o general que cubre toda la prueba
 * o alguna pregunta— y lo califica alguien; y <b>un criterio de IA que solo mira enlaces, sin
 * abiertas, tiene que ser de persona</b>: la IA no abre enlaces.
 *
 * <p><b>La IA nunca propone «Mira»</b> (V68): lo que mira cada criterio se deduce, como en el
 * editor, del alcance de los entregables. El caso es opcional. Si no cuadra, se le devuelve
 * UNA vez con los errores delante.
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
        List<CriterioPropuesto> criterios = resultado.criterios();
        List<EntregablePropuesto> propuestos = lista(resultado.entregables());

        // Lo que ya hay en el borrador.
        Map<Long, CriterioDelBorradorDePrueba> delBorrador = new HashMap<>();
        Set<String> yaEscritas = new HashSet<>();
        for (CriterioDelBorradorDePrueba c : lista(insumo.criteriosDelBorrador())) {
            delBorrador.put(c.id(), c);
            lista(c.preguntas()).forEach(p -> yaEscritas.add(normal(p.enunciado())));
        }
        Set<String> nombresDeEntregables = new HashSet<>();
        List<String> formatosDeTodaLaPrueba = new ArrayList<>();
        for (EntregableDelBorrador e : lista(insumo.entregablesDelBorrador())) {
            nombresDeEntregables.add(normal(e.nombre()));
            if (e.todaLaPrueba()) {
                formatosDeTodaLaPrueba.add(e.formato());
            }
        }

        // Los entregables propuestos y su alcance; de ahí sale lo que mira cada criterio.
        Map<Integer, Set<Integer>> miraPorCriterio = new HashMap<>();
        Set<String> preguntasConArchivo = new HashSet<>();
        for (int k = 0; k < propuestos.size(); k++) {
            validarEntregable(criterios, propuestos.get(k), k, nombresDeEntregables,
                    preguntasConArchivo, miraPorCriterio, errores);
        }

        Set<Integer> calificados = new HashSet<>();
        Set<String> propuestas = new HashSet<>();
        Set<Long> existentesUsados = new HashSet<>();
        int suma = 0;
        boolean haySuma = true;
        for (int i = 0; i < criterios.size(); i++) {
            CriterioPropuesto c = criterios.get(i);
            String cual = "el criterio " + (i + 1);
            List<PreguntaPropuesta> suyas = lista(c.preguntas());
            Set<Integer> loQueMira = miraPorCriterio.getOrDefault(i, Set.of());
            int cerradas = validarPreguntas(suyas, cual, yaEscritas, propuestas, errores);
            boolean conParte;
            if (c.criterioExistenteId() != null) {
                // Sigue valiendo lo mismo (V69): sus cerradas nuevas no suman a lo que falta.
                CriterioDelBorradorDePrueba existente = delBorrador.get(c.criterioExistenteId());
                conParte = validarExistente(c, existente, cerradas, existentesUsados, cual, errores);
            } else {
                BigDecimal puntos = c.puntosDelCriterio();
                List<String> formatos = new ArrayList<>(formatosDeTodaLaPrueba);
                loQueMira.forEach(k -> formatos.add(propuestos.get(k).formato()));
                conParte = validarNuevo(c, puntos, cerradas, formatos, cual, errores);
                // Sin puntos, o mal escritos, su falta ya lo dice: no hay suma que dar (QA-11).
                OptionalInt vale = puntos == null ? OptionalInt.empty() : ReglasDeLaPrueba.paraLaSuma(puntos);
                suma += vale.orElse(0);
                haySuma &= vale.isPresent();
            }
            if (conParte) {
                calificados.addAll(loQueMira);
            }
            if (c.queEvalua() != null && c.queEvalua().length() > ReglasDePuntos.MAXIMO_TEXTO_IA) {
                errores.add(cual + ": «qué evalúa» pasa de " + ReglasDePuntos.MAXIMO_TEXTO_IA
                        + " caracteres");
            }
        }

        // Todo entregable lo califica alguien: un criterio con parte calificada que lo mira.
        for (int k = 0; k < propuestos.size(); k++) {
            if (!calificados.contains(k)) {
                errores.add("el entregable " + (k + 1) + " («" + propuestos.get(k).nombre()
                        + "») no lo califica nadie: ningún criterio con parte calificada lo mira");
            }
        }
        if (haySuma && suma != insumo.puntosQueFaltan()) {
            errores.add("los puntos de tus criterios nuevos suman " + suma
                    + " y tienen que sumar exactamente " + insumo.puntosQueFaltan()
                    + ", lo que le falta a la prueba para llegar a 100 (las preguntas que pones en "
                    + "un criterio del borrador no suman: ese criterio sigue valiendo lo mismo)");
        }
        return errores;
    }

    /**
     * La forma de un entregable propuesto y su alcance. Anota qué criterios lo miran: el de
     * su pregunta, todos si cubre toda la prueba, o los de las preguntas que cubre.
     */
    private static void validarEntregable(List<CriterioPropuesto> criterios, EntregablePropuesto e,
                                          int k, Set<String> nombres, Set<String> preguntasConArchivo,
                                          Map<Integer, Set<Integer>> miraPorCriterio,
                                          List<String> errores) {
        String cual = "el entregable " + (k + 1);
        ReglasDeLaPrueba.formaDelEntregable(e.nombre(), e.formato())
                .forEach(f -> errores.add(cual + ": " + f));
        if (e.nombre() != null && !e.nombre().isBlank() && !nombres.add(normal(e.nombre()))) {
            errores.add(cual + " repite el nombre de otro entregable");
        }
        if (e.queDebeTener() != null && e.queDebeTener().length() > ReglasDePuntos.MAXIMO_TEXTO_IA) {
            errores.add(cual + ": «qué debe tener una buena entrega» pasa de "
                    + ReglasDePuntos.MAXIMO_TEXTO_IA + " caracteres");
        }
        if (e.esDeUnaPregunta()) {
            if (Boolean.TRUE.equals(e.todaLaPrueba()) || !lista(e.cubre()).isEmpty()) {
                errores.add(cual + " es de una pregunta y general a la vez: elige una de las dos");
            }
            PosicionDePregunta p = e.pregunta();
            if (!existe(criterios, p)) {
                errores.add(cual + " es de una pregunta que no está en tu propuesta");
            } else if (!preguntasConArchivo.add(p.criterio() + "-" + p.pregunta())) {
                errores.add(cual + ": esa pregunta ya pide otro archivo, y una pregunta pide como "
                        + "mucho uno");
            } else {
                miraPorCriterio.computeIfAbsent(p.criterio(), x -> new LinkedHashSet<>()).add(k);
            }
            return;
        }
        if (e.cubreTodaLaPrueba()) {
            for (int i = 0; i < criterios.size(); i++) {
                miraPorCriterio.computeIfAbsent(i, x -> new LinkedHashSet<>()).add(k);
            }
            return;
        }
        List<PosicionDePregunta> cubre = lista(e.cubre());
        if (cubre.isEmpty()) {
            errores.add(cual + " es general y no cubre ninguna pregunta: pon «todaLaPrueba» o "
                    + "las preguntas que reúne en «cubre»");
        }
        for (PosicionDePregunta p : cubre) {
            if (existe(criterios, p)) {
                miraPorCriterio.computeIfAbsent(p.criterio(), x -> new LinkedHashSet<>()).add(k);
            } else {
                errores.add(cual + " cubre una pregunta que no está en tu propuesta");
            }
        }
    }

    private static boolean existe(List<CriterioPropuesto> criterios, PosicionDePregunta p) {
        return p != null && p.criterio() != null && p.pregunta() != null
                && p.criterio() >= 0 && p.criterio() < criterios.size()
                && p.pregunta() >= 0 && p.pregunta() < lista(criterios.get(p.criterio()).preguntas()).size();
    }

    /**
     * Preguntas para un criterio del borrador. Ese criterio sigue valiendo lo mismo (V69): sus
     * cerradas nuevas salen de su parte calificada, que no pueden pasar, y si tiene o recibe
     * abiertas, o mira archivos, tiene que quedarle algo para calificarlas. Devuelve si le
     * queda parte calificada.
     */
    private static boolean validarExistente(CriterioPropuesto c, CriterioDelBorradorDePrueba existente,
                                            int cerradas, Set<Long> usados, String cual,
                                            List<String> errores) {
        List<PreguntaPropuesta> suyas = lista(c.preguntas());
        if (existente == null) {
            errores.add(cual + " dice ir al criterio " + c.criterioExistenteId()
                    + " del borrador, y el borrador no tiene ese criterio");
        } else if (!usados.add(c.criterioExistenteId())) {
            errores.add("el criterio " + c.criterioExistenteId()
                    + " del borrador aparece dos veces: junta sus preguntas en uno");
        }
        if (suyas.isEmpty()) {
            errores.add(cual + " va a un criterio del borrador y no trae ninguna pregunta");
        }
        if (existente == null) {
            return false;
        }
        int queda = existente.parteCalificada() - cerradas;
        boolean tieneAbiertas = suyas.stream().anyMatch(p -> ReglasDePuntos.ABIERTA.equals(p.tipo()))
                || lista(existente.preguntas()).stream().anyMatch(p -> ReglasDePuntos.ABIERTA.equals(p.tipo()))
                || !lista(existente.entregables()).isEmpty();
        if (queda < 0) {
            errores.add(cual + " pone " + cerradas + " puntos de cerradas en un criterio del "
                    + "borrador que vale " + existente.puntos() + " y solo le quedan "
                    + existente.parteCalificada() + " fuera de sus cerradas: ese criterio sigue "
                    + "valiendo lo mismo");
        } else if (queda == 0 && tieneAbiertas) {
            errores.add(cual + " deja sin parte calificada a un criterio del borrador que tiene "
                    + "abiertas o archivos: nadie las calificaría");
        }
        return queda > 0;
    }

    /**
     * Un criterio nuevo: su nombre, lo que vale (V69) y que su parte calificada —lo que vale
     * menos sus cerradas— mire algo que pueda calificar. Devuelve si tiene parte calificada.
     */
    private static boolean validarNuevo(CriterioPropuesto c, BigDecimal puntos, int cerradas,
                                        List<String> formatos, String cual, List<String> errores) {
        List<PreguntaPropuesta> suyas = lista(c.preguntas());
        boolean tieneAbiertas = suyas.stream().anyMatch(p -> ReglasDePuntos.ABIERTA.equals(p.tipo()));
        if (c.nombre() == null || c.nombre().isBlank()) {
            errores.add(cual + " es nuevo y no tiene nombre");
        } else if (c.nombre().length() > 120) {
            errores.add(cual + " tiene un nombre de más de 120 caracteres");
        }
        List<String> forma = ReglasDeLaPrueba.formaDeLosPuntosDelCriterio(puntos, c.calificador(), cerradas);
        forma.forEach(f -> errores.add(cual + ": " + f));
        if (puntos == null || !ReglasDePuntos.esEntero(puntos)) {
            return false;
        }
        int parte = puntos.intValue() - cerradas;
        if (parte < 0) {
            errores.add(cual + ": sus cerradas suman " + cerradas + " y el criterio vale "
                    + puntos.intValue() + "; sus puntos son todo lo que vale, cerradas incluidas");
            return false;
        }
        boolean conParte = parte > 0;
        boolean miraAlgo = !formatos.isEmpty();
        if (conParte && !tieneAbiertas && !miraAlgo) {
            errores.add(cual + " tiene parte calificada y no mira nada: dale una abierta o pide "
                    + "un archivo en una de sus preguntas");
        }
        if (!conParte && tieneAbiertas) {
            errores.add(cual + " tiene abiertas y sus cerradas ya suman todo lo que vale: no le "
                    + "queda parte calificada para calificarlas");
        }
        if (!conParte && suyas.stream().noneMatch(p -> ReglasDePuntos.esCerrada(p.tipo()))) {
            errores.add(cual + " está vacío: no tiene cerradas ni parte calificada");
        }
        if (conParte && CalificacionDeLaPruebaPropia.IA.equals(c.calificador()) && !tieneAbiertas
                && miraAlgo && formatos.stream().allMatch("ENLACE"::equals)) {
            errores.add(cual + " solo mira entregables de enlace, sin abiertas, y la IA no "
                    + "abre enlaces: tiene que calificarlo una persona");
        }
        return conParte;
    }

    /** Las reglas de cada pregunta; devuelve los puntos de sus cerradas. */
    private static int validarPreguntas(List<PreguntaPropuesta> suyas, String cual, Set<String> yaEscritas,
                                        Set<String> propuestas, List<String> errores) {
        int suma = 0;
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
}
