package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.OpcionAValidar;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.PreguntaAValidar;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.CriterioDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.PreguntaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Las reglas de la prueba técnica escrita en el editor (V67), en un solo sitio.
 *
 * <p>Las usan guardar (la forma: lo que nunca puede guardarse), publicar (la lista entera de
 * lo que falta, punto 3 de la spec), cambiar los puntos y validar lo que propone la IA. Con
 * una copia en cada uno, el editor dejaría guardar algo que la publicación rechaza por otra
 * razón. Las reglas de cada tipo de pregunta son las de la fase 1 ({@link ReglasDePuntos});
 * aquí solo se añade lo que una prueba tiene y unas preguntas no.
 */
public final class ReglasDeLaPrueba {

    public static final String CRONOMETRADA = "CRONOMETRADA";
    public static final String PLAZO_ABIERTO = "PLAZO_ABIERTO";
    public static final Set<String> FORMATOS = Set.of("ARCHIVO", "ENLACE", "CUALQUIERA");
    public static final Set<String> CALIFICADORES = Set.of(
            CalificacionDeLaPruebaPropia.IA, CalificacionDeLaPruebaPropia.PERSONA);

    /** Los mismos límites que las plantillas: cinco minutos como suelo, sin techo. */
    public static final int MINUTOS_MINIMOS = 5;
    public static final int DIAS_MINIMOS = 1;

    private ReglasDeLaPrueba() {
    }

    // ============================== La forma: al guardar ==============================

    /** Lo que impide guardar la parte calificada de un criterio, dicho entero. */
    public static List<String> formaDeLaParteCalificada(BigDecimal puntos, String calificador) {
        List<String> faltas = new ArrayList<>();
        if (puntos != null) {
            if (!ReglasDePuntos.esEntero(puntos)) {
                faltas.add("La parte calificada tiene que ser un número entero de puntos, sin decimales.");
            } else if (puntos.signum() < 0 || puntos.compareTo(BigDecimal.valueOf(ReglasDePuntos.TOTAL)) > 0) {
                faltas.add("La parte calificada va de 0 a 100 puntos.");
            }
        }
        boolean conPuntos = puntos != null && puntos.signum() > 0;
        if (conPuntos && (calificador == null || calificador.isBlank())) {
            faltas.add("Falta decir quién califica la parte calificada: la IA o una persona.");
        } else if (calificador != null && !calificador.isBlank()
                && !CALIFICADORES.contains(calificador)) {
            faltas.add("Quien califica la parte calificada es «IA» o «PERSONA».");
        }
        return faltas;
    }

    /** Lo que impide guardar un entregable. */
    public static List<String> formaDelEntregable(String nombre, String formato) {
        List<String> faltas = new ArrayList<>();
        if (nombre == null || nombre.isBlank()) {
            faltas.add("El entregable necesita un nombre.");
        }
        if (formato == null || !FORMATOS.contains(formato)) {
            faltas.add("El formato del entregable es archivo, enlace o cualquiera de los dos.");
        }
        return faltas;
    }

    /** Lo que impide guardar el tiempo. Vacío del todo se admite en un borrador. */
    public static List<String> formaDelTiempo(String modalidad, Integer minutos, Integer dias) {
        List<String> faltas = new ArrayList<>();
        if (modalidad == null || modalidad.isBlank()) {
            if (minutos != null || dias != null) {
                faltas.add("Di si la prueba es cronometrada (con minutos) o de plazo abierto (con días).");
            }
            return faltas;
        }
        if (!CRONOMETRADA.equals(modalidad) && !PLAZO_ABIERTO.equals(modalidad)) {
            faltas.add("La prueba es cronometrada o de plazo abierto.");
            return faltas;
        }
        if (CRONOMETRADA.equals(modalidad) && minutos != null && minutos < MINUTOS_MINIMOS) {
            faltas.add("Una prueba cronometrada dura al menos " + MINUTOS_MINIMOS + " minutos.");
        }
        if (PLAZO_ABIERTO.equals(modalidad) && dias != null && dias < DIAS_MINIMOS) {
            faltas.add("Una prueba de plazo abierto dura al menos " + DIAS_MINIMOS + " día.");
        }
        return faltas;
    }

    /**
     * La forma de una pregunta de la prueba: la de la fase 1, salvo que la abierta no lleva
     * puntos (decisión 3).
     */
    public static List<String> formaDeLaPregunta(String donde, PreguntaAValidar p) {
        if (ReglasDePuntos.ABIERTA.equals(p.tipo())) {
            if (p.puntos() != null && p.puntos().signum() != 0) {
                List<String> faltas = new ArrayList<>(ReglasDePuntos.formaDeLaPregunta(donde,
                        sinPuntos(p)));
                faltas.add(donde + ": en la prueba, una abierta no lleva puntos: la IA o una "
                        + "persona califican el criterio entero.");
                return faltas;
            }
            return ReglasDePuntos.formaDeLaPregunta(donde, sinPuntos(p));
        }
        return ReglasDePuntos.formaDeLaPregunta(donde, p);
    }

    private static PreguntaAValidar sinPuntos(PreguntaAValidar p) {
        return new PreguntaAValidar(p.tipo(), p.enunciado(), BigDecimal.ZERO, p.queDebeTener(),
                p.opciones());
    }

    // ============================== Publicar: la lista entera ==============================

    /**
     * Todo lo que impide publicar la prueba, dicho entero (punto 3 de la spec).
     */
    public static List<String> faltasParaPublicar(Resultado r) {
        List<String> faltas = new ArrayList<>();
        List<PreguntaDeLaPrueba> todas = r.todas();
        VersionBanco v = r.version();
        boolean hayEntregables = !r.entregables().isEmpty();

        if (todas.isEmpty() && !hayEntregables) {
            faltas.add("Todavía no hay nada que rendir: agrega preguntas o entregables.");
        } else {
            String total = ReglasDePuntos.faltaDelTotal(r.total());
            if (total != null) {
                faltas.add(total);
            }
        }
        if (r.criterios().isEmpty() && (!todas.isEmpty() || hayEntregables)) {
            faltas.add("Todavía no hay ningún criterio: lo que se califica va dentro de un criterio.");
        }

        // El caso y el tiempo.
        if (hayEntregables && (v.getEnunciado() == null || v.getEnunciado().isBlank())) {
            faltas.add("Falta el enunciado del caso: es obligatorio cuando la prueba pide entregables.");
        }
        faltas.addAll(tiempoParaPublicar(v));

        // Los criterios y su parte calificada.
        Set<Long> mirados = new HashSet<>();
        for (CriterioDeLaPrueba c : r.criterios()) {
            String nombre = c.criterio().getNombre();
            String cual = "El criterio «" + nombre + "»";
            if (nombre == null || nombre.isBlank()) {
                faltas.add("Un criterio no tiene nombre.");
            }
            c.entregables().forEach(e -> mirados.add(e.getId()));
            boolean tieneAbiertas = !c.abiertas().isEmpty();
            boolean miraEntregables = !c.entregables().isEmpty();
            boolean tieneCerradas = c.preguntas().stream().anyMatch(p -> !p.esAbierta());
            if (c.tieneParteCalificada()) {
                if (c.calificador() == null) {
                    faltas.add(cual + ": falta decir quién califica su parte calificada, la IA o "
                            + "una persona.");
                }
                if (!tieneAbiertas && !miraEntregables) {
                    faltas.add(cual + ": su parte calificada no mira nada. Agrégale una abierta o "
                            + "marca qué entregables mira.");
                } else if (c.esDeIa() && !tieneAbiertas && soloEnlaces(c.entregables())) {
                    faltas.add(cual + " lo califica la IA y solo mira enlaces, que la IA no abre. "
                            + "Pásalo a una persona o agrégale una abierta.");
                }
            } else if (tieneAbiertas || miraEntregables) {
                faltas.add(cual + " tiene abiertas o entregables, y su parte calificada no tiene "
                        + "puntos: dale puntos para que alguien la califique.");
            } else if (!tieneCerradas) {
                faltas.add(cual + " está vacío: no tiene cerradas ni parte calificada.");
            }
        }

        // Todo entregable, en algún criterio; y bien formado.
        for (EntregableRequerido e : r.entregables()) {
            faltas.addAll(formaDelEntregable(e.getNombre(), e.getFormato()));
            if (!mirados.contains(e.getId())) {
                faltas.add("«" + e.getNombre() + "» no está en ningún criterio.");
            }
        }

        // Las preguntas: todas en un criterio, y las reglas de cada tipo.
        int posicion = 1;
        for (PreguntaDeLaPrueba pc : todas) {
            Pregunta p = pc.pregunta();
            String donde = ReglasDePuntos.nombreDe(posicion++, p.getEnunciado());
            if (p.getCriterioBancoId() == null) {
                faltas.add(donde + ": no está en ningún criterio.");
            }
            PreguntaAValidar aValidar = comoAValidar(pc);
            faltas.addAll(formaDeLaPregunta(donde, aValidar));
            if (!pc.esAbierta()) {
                faltas.addAll(ReglasDePuntos.puntuacionDeLaPregunta(donde, aValidar));
            }
        }
        return faltas;
    }

    /** El tiempo de una versión, tal como lo exige publicar. */
    public static List<String> tiempoParaPublicar(VersionBanco v) {
        List<String> faltas = new ArrayList<>();
        if (v.getModalidad() == null) {
            faltas.add("Falta el tiempo: di si es cronometrada (con minutos) o de plazo abierto "
                    + "(con días).");
        } else if (CRONOMETRADA.equals(v.getModalidad())
                && (v.getDuracionMinutos() == null || v.getDuracionMinutos() < MINUTOS_MINIMOS)) {
            faltas.add("Una prueba cronometrada dura al menos " + MINUTOS_MINIMOS + " minutos: "
                    + "di cuántos.");
        } else if (PLAZO_ABIERTO.equals(v.getModalidad())
                && (v.getPlazoDias() == null || v.getPlazoDias() < DIAS_MINIMOS)) {
            faltas.add("Una prueba de plazo abierto necesita sus días: al menos " + DIAS_MINIMOS
                    + ".");
        }
        return faltas;
    }

    /** Si todos los entregables son de enlace: la IA no los abre. */
    public static boolean soloEnlaces(List<EntregableRequerido> entregables) {
        return !entregables.isEmpty()
                && entregables.stream().allMatch(e -> "ENLACE".equals(e.getFormato()));
    }

    static PreguntaAValidar comoAValidar(PreguntaDeLaPrueba pc) {
        Pregunta p = pc.pregunta();
        return new PreguntaAValidar(p.getTipo(), p.getEnunciado(),
                pc.esAbierta() ? BigDecimal.ZERO
                        : p.getPuntos() == null ? null : BigDecimal.valueOf(p.getPuntos()),
                p.getQueDebeTener(),
                pc.opciones().stream().map(o -> new OpcionAValidar(
                        ReglasDePuntos.ESCALA.equals(p.getTipo()) ? null : o.getTexto(),
                        o.getPuntaje())).toList());
    }
}
