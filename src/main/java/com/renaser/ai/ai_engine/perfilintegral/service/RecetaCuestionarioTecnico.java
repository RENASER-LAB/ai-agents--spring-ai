package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.BloquePedido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.PreguntaGenerada;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * La estructura fija del cuestionario técnico y su aduana. Pura a propósito, como
 * {@code FormulasCazatalentos}: la receta de la clienta es mecánica y se prueba con casos
 * a mano, sin IA ni base.
 *
 * <p>«La estructura no se negocia; el contenido sí.» DIR 12 · SUP 10 · OPE 8, por bloques:
 * es lo que permite comparar candidatos de puestos distintos sin comparar peras con
 * manzanas. El REDACTOR recibe esta estructura como dato y la aduana la exige de vuelta.
 */
public final class RecetaCuestionarioTecnico {

    public static final String EXPERIENCIA = "EXPERIENCIA";
    public static final String RIESGO_1 = "RIESGO_1";
    public static final String RIESGO_2 = "RIESGO_2";
    public static final String RIESGO_3 = "RIESGO_3";
    public static final String REQUERIMIENTO = "REQUERIMIENTO";
    public static final String DILEMA = "DILEMA";
    public static final String PRESENCIAL = "PRESENCIAL";

    // Lo que la clienta prohibió preguntar. Frases y raíces, sin acentos y en minúscula;
    // «política» y «salud» van como frase compuesta a propósito: «políticas de crédito» y
    // «salud financiera» son preguntas legítimas de administración.
    private static final List<String> PROHIBIDO = List.of(
            "estado civil", "hijos", "embaraz", "religi", "sindicato", "sindical", "etnic",
            "partido politic", "afiliacion politic", "ideologia",
            "estado de salud", "tu salud", "su salud", "enfermedad");

    private RecetaCuestionarioTecnico() {
    }

    /** Los bloques del nivel, en orden, sin tema (el tema lo pone la ficha). */
    public static List<BloquePedido> estructura(String nivel) {
        return switch (nivel) {
            case "DIRECCION" -> List.of(
                    new BloquePedido(EXPERIENCIA, 2, null),
                    new BloquePedido(RIESGO_1, 3, null),
                    new BloquePedido(RIESGO_2, 2, null),
                    new BloquePedido(RIESGO_3, 2, null),
                    new BloquePedido(REQUERIMIENTO, 1, null),
                    new BloquePedido(DILEMA, 1, null),
                    new BloquePedido(PRESENCIAL, 1, null));
            case "SUPERVISION" -> List.of(
                    new BloquePedido(EXPERIENCIA, 2, null),
                    new BloquePedido(RIESGO_1, 3, null),
                    new BloquePedido(RIESGO_2, 2, null),
                    new BloquePedido(RIESGO_3, 1, null),
                    new BloquePedido(REQUERIMIENTO, 1, null),
                    new BloquePedido(DILEMA, 1, null));
            case "EJECUCION" -> List.of(
                    new BloquePedido(EXPERIENCIA, 2, null),
                    new BloquePedido(RIESGO_1, 2, null),
                    new BloquePedido(RIESGO_2, 2, null),
                    new BloquePedido(RIESGO_3, 1, null),
                    new BloquePedido(DILEMA, 1, null));
            default -> throw new IllegalArgumentException(
                    "Nivel desconocido para el cuestionario técnico: " + nivel);
        };
    }

    public static int totalPreguntas(String nivel) {
        return estructura(nivel).stream().mapToInt(BloquePedido::cantidad).sum();
    }

    /**
     * La aduana del borrador. Acumula TODOS los errores con su fila: al agente que
     * corrige (y a quien depure) le sirve la lista completa, no el primer tropiezo.
     * Lista vacía = el borrador puede mostrarse.
     */
    public static List<String> validar(String nivel, List<PreguntaGenerada> preguntas) {
        List<String> errores = new ArrayList<>();
        List<BloquePedido> esperado = estructura(nivel);

        // La secuencia exacta: el bloque de cada pregunta, en el orden del método.
        List<String> secuencia = new ArrayList<>();
        for (BloquePedido bloque : esperado) {
            for (int i = 0; i < bloque.cantidad(); i++) {
                secuencia.add(bloque.bloque());
            }
        }
        if (preguntas.size() != secuencia.size()) {
            errores.add("El nivel " + nivel + " lleva " + secuencia.size()
                    + " preguntas y llegaron " + preguntas.size());
        }

        Set<String> codigos = new HashSet<>();
        for (int i = 0; i < preguntas.size(); i++) {
            PreguntaGenerada p = preguntas.get(i);
            String fila = "pregunta " + (i + 1);

            if (i < secuencia.size() && !secuencia.get(i).equals(p.bloque())) {
                errores.add(fila + ": el bloque debía ser " + secuencia.get(i)
                        + " y llegó " + p.bloque());
            }
            if (esBlanco(p.enunciado())) {
                errores.add(fila + ": sin enunciado");
            }
            if (esBlanco(p.codigo())) {
                errores.add(fila + ": sin código");
            } else if (!codigos.add(p.codigo())) {
                errores.add(fila + ": el código " + p.codigo() + " está repetido");
            }

            boolean presencial = Boolean.TRUE.equals(p.presencial());
            if (presencial != PRESENCIAL.equals(p.bloque())) {
                errores.add(fila + ": presencial solo puede (y debe) marcarse en el bloque "
                        + "PRESENCIAL — la muestra de trabajo jamás se envía al candidato");
            }
            // La muestra no se califica en el formulario: no lleva guía. Todo lo demás sí,
            // porque sin C3/C4/señal el evaluador tendría que inventarse qué contar.
            if (!presencial && (esBlanco(p.c3Esperado()) || esBlanco(p.c4Esperado())
                    || esBlanco(p.senalDeCero()))) {
                errores.add(fila + ": guía de calificación incompleta (C3, C4 y señal de 0 "
                        + "son obligatorios)");
            }

            // El candidato ve el enunciado y puede ver el rótulo del bloque: los dos
            // se revisan. («sindicato/sindical» y no «sindica»: la sindicatura es un
            // término legítimo de administración.)
            String visible = sinAcentos(p.enunciado()) + " " + sinAcentos(p.bloqueEtiqueta());
            for (String tabu : PROHIBIDO) {
                if (visible.contains(tabu)) {
                    errores.add(fila + ": toca un tema prohibido («" + tabu + "») — no se "
                            + "pregunta estado civil, hijos, salud, embarazo, religión, "
                            + "política, sindicato ni origen étnico");
                }
            }
        }
        return errores;
    }

    /** Lo que la publicación exige de una pregunta, venga del agente o de la mano del dueño. */
    public record PreguntaPublicable(String codigo, String enunciado, String c3Esperado,
                                     String c4Esperado, String senalDeCero, boolean presencial) {}

    /**
     * La aduana de publicación. El dueño pudo editar el borrador a mano, así que antes de
     * publicar se vuelve a exigir lo que no se negocia: la cantidad del nivel, la muestra
     * presencial donde toca, la guía completa en todo lo puntuable y ningún tema
     * prohibido. La secuencia de bloques no se revisa aquí: quedó fijada al generar.
     */
    public static List<String> validarPublicacion(String nivel, List<PreguntaPublicable> preguntas) {
        List<String> errores = new ArrayList<>();
        int total = totalPreguntas(nivel);
        if (preguntas.size() != total) {
            errores.add("El nivel " + nivel + " lleva " + total + " preguntas y hay "
                    + preguntas.size());
        }
        long presenciales = preguntas.stream().filter(PreguntaPublicable::presencial).count();
        long presencialesEsperadas = estructura(nivel).stream()
                .filter(b -> PRESENCIAL.equals(b.bloque())).mapToInt(BloquePedido::cantidad).sum();
        if (presenciales != presencialesEsperadas) {
            errores.add("El nivel " + nivel + " lleva " + presencialesEsperadas
                    + " pregunta(s) presencial(es) y hay " + presenciales);
        }
        for (PreguntaPublicable p : preguntas) {
            String quien = p.codigo() == null ? "(sin código)" : p.codigo();
            if (esBlanco(p.enunciado())) {
                errores.add(quien + ": sin enunciado");
            }
            if (!p.presencial() && (esBlanco(p.c3Esperado()) || esBlanco(p.c4Esperado())
                    || esBlanco(p.senalDeCero()))) {
                errores.add(quien + ": guía de calificación incompleta (C3, C4 y señal de 0)");
            }
            String enunciado = sinAcentos(p.enunciado());
            for (String tabu : PROHIBIDO) {
                if (enunciado.contains(tabu)) {
                    errores.add(quien + ": toca un tema prohibido («" + tabu + "»)");
                }
            }
        }
        return errores;
    }

    private static boolean esBlanco(String s) {
        return s == null || s.isBlank();
    }

    private static String sinAcentos(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
