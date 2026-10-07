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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
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

    private ReglasDeLaPrueba() {
    }

    // ============================== La forma: al guardar ==============================

    /**
     * Lo que impide guardar los puntos de un criterio (V69), dicho entero: quien arma la
     * prueba escribe lo que vale el criterio entero, y lo que no suman sus cerradas es su
     * parte calificada, que necesita quién la califique.
     *
     * <p>Que las cerradas pasen del total NO impide guardar: el total se mantiene aunque
     * cambien las cerradas, así que eso puede pasar en cualquier momento y se dice como falta
     * al publicar ({@link #cerradasPorEncima}).
     *
     * @param cerradas lo que suman hoy las cerradas del criterio
     */
    public static List<String> formaDeLosPuntosDelCriterio(BigDecimal puntos, String calificador, int cerradas) {
        List<String> faltas = new ArrayList<>();
        int total = -1;
        if (puntos == null) {
            faltas.add("Faltan los puntos del criterio: lo que vale entero, cerradas incluidas.");
        } else if (!ReglasDePuntos.esEntero(puntos)) {
            faltas.add("Los puntos del criterio tienen que ser un número entero, sin decimales.");
        } else if (puntos.signum() < 0 || puntos.compareTo(BigDecimal.valueOf(ReglasDePuntos.TOTAL)) > 0) {
            faltas.add("Los puntos del criterio van de 0 a 100.");
        } else {
            total = puntos.intValue();
        }
        // Mal escritos (total en -1) no dejan parte calificada que decir.
        boolean conParte = total > cerradas;
        boolean sinCalificador = calificador == null || calificador.isBlank();
        if (conParte && sinCalificador) {
            faltas.add("Falta decir quién califica los " + (total - cerradas)
                    + " puntos que no son de cerradas: la IA o una persona.");
        } else if (!sinCalificador && !CALIFICADORES.contains(calificador)) {
            faltas.add("Quien califica la parte calificada es «IA» o «PERSONA».");
        }
        return faltas;
    }

    /**
     * Lo escrito, como entero, para sumarlo al total de la prueba; vacío si no es un entero
     * (o es tan grande que no cabe en una suma). Entonces no hay suma que decir: la falta de
     * ese campo ya dice qué está mal, y una suma que lo dejara fuera contradiría la que se ve
     * escrita en el formulario (QA-11). La regla es la de las preguntas propias
     * ({@link ReglasDePuntos#paraLaSuma}): «Cambiar los puntos» del banco suma igual.
     */
    public static OptionalInt paraLaSuma(BigDecimal escrito) {
        return ReglasDePuntos.paraLaSuma(escrito);
    }

    /** La falta de un criterio cuyas cerradas pasan de lo que vale (V69). */
    public static String cerradasPorEncima(String nombre, int cerradas, int puntosDelCriterio) {
        return "Las cerradas de «" + nombre + "» suman " + cerradas + " y el criterio vale "
                + puntosDelCriterio + ".";
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

    /**
     * Lo que impide guardar el tiempo. Vacío del todo se admite en un borrador.
     *
     * <p>Desde la V68 son dos: «Cronometrada» (con sus minutos) o «Sin cronómetro», que se
     * guarda como {@link #PLAZO_ABIERTO} sin días: se trabaja hasta la fecha límite.
     */
    public static List<String> formaDelTiempo(String modalidad, Integer minutos) {
        List<String> faltas = new ArrayList<>();
        if (modalidad == null || modalidad.isBlank()) {
            if (minutos != null) {
                faltas.add("Di si la prueba es cronometrada (con minutos) o sin cronómetro.");
            }
            return faltas;
        }
        if (!CRONOMETRADA.equals(modalidad) && !PLAZO_ABIERTO.equals(modalidad)) {
            faltas.add("La prueba es cronometrada o sin cronómetro.");
            return faltas;
        }
        if (CRONOMETRADA.equals(modalidad) && minutos != null && minutos < MINUTOS_MINIMOS) {
            faltas.add("Una prueba cronometrada dura al menos " + MINUTOS_MINIMOS + " minutos.");
        }
        return faltas;
    }

    /**
     * Lo que impide guardar el alcance de un entregable (V68): es el archivo de una pregunta
     * o un general que cubre toda la prueba o alguna pregunta, nunca las dos cosas.
     */
    public static List<String> formaDelAlcance(Long preguntaId, boolean todaLaPrueba, List<Long> cubre) {
        List<String> faltas = new ArrayList<>();
        boolean conCubre = cubre != null && !cubre.isEmpty();
        if (preguntaId != null && (todaLaPrueba || conCubre)) {
            faltas.add("Un archivo es de una pregunta o general, no las dos cosas.");
        } else if (preguntaId == null && !todaLaPrueba && !conCubre) {
            faltas.add("Elige qué cubre el entregable general: toda la prueba o alguna pregunta.");
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
     * Todo lo que impide publicar la prueba de una vacante, dicho entero (punto 12 de la
     * spec): lo de la versión y, además, su fecha límite, que es de la vacante.
     *
     * @param cierraEn la fecha límite de la vacante ({@code vacante.prueba_cierra_en}), o nula
     * @param ahora    contra qué se mira que sea futura
     */
    public static List<String> faltasParaPublicar(Resultado r, Instant cierraEn, Instant ahora) {
        return faltas(r, faltaDeLaFecha(cierraEn, ahora));
    }

    /**
     * Lo que impide publicar una versión, sin mirar la fecha (que es de la vacante): lo usa
     * la vista previa de una copia, que no tiene vacante propia.
     */
    public static List<String> faltasParaPublicar(Resultado r) {
        return faltas(r, null);
    }

    /** La lista entera; la falta de la fecha, si la hay, va junto al tiempo. */
    private static List<String> faltas(Resultado r, String faltaDeLaFecha) {
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

        // El tiempo y la fecha límite. El caso nunca es obligatorio (decisión 2).
        faltas.addAll(tiempoParaPublicar(v));
        if (faltaDeLaFecha != null) {
            faltas.add(faltaDeLaFecha);
        }

        // Los criterios y su parte calificada.
        Set<Long> calificados = new HashSet<>();
        for (CriterioDeLaPrueba c : r.criterios()) {
            faltas.addAll(faltasDelCriterio(c));
            if (c.tieneParteCalificada()) {
                c.entregables().forEach(e -> calificados.add(e.getId()));
            }
        }

        // Todo entregable bien formado, cubriendo algo y con alguien que lo califique.
        for (EntregableRequerido e : r.entregables()) {
            faltas.addAll(faltasDelEntregable(r, e, calificados.contains(e.getId())));
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

    /**
     * Lo que le falta a un criterio: quién califica su parte calificada, que mire algo y, si
     * es de la IA, que no mire solo enlaces sin abiertas.
     */
    static List<String> faltasDelCriterio(CriterioDeLaPrueba c) {
        List<String> faltas = new ArrayList<>();
        String nombre = c.criterio().getNombre();
        String cual = "El criterio «" + nombre + "»";
        if (nombre == null || nombre.isBlank()) {
            faltas.add("Un criterio no tiene nombre.");
        }
        if (c.cerradasPorEncima()) {
            // Lo primero que hay que arreglar (V69): mientras sus cerradas pasen de lo que
            // vale, no queda parte calificada y lo demás de este criterio no se puede decir.
            faltas.add(cerradasPorEncima(nombre, c.sistemaMaximo(), c.puntosDelCriterio()));
            return faltas;
        }
        boolean tieneAbiertas = !c.abiertas().isEmpty();
        boolean tieneCerradas = c.preguntas().stream().anyMatch(p -> !p.esAbierta());
        if (c.tieneParteCalificada()) {
            if (c.calificador() == null) {
                faltas.add(cual + ": falta decir quién califica su parte calificada, la IA o "
                        + "una persona.");
            }
            if (!tieneAbiertas && c.entregables().isEmpty()) {
                faltas.add(nadaQueCalificar(nombre, c.puntosDelCriterio(), c.sistemaMaximo()));
            } else if (c.esDeIa() && !tieneAbiertas && soloEnlaces(c.entregables())) {
                faltas.add(cual + " lo califica la IA y solo mira enlaces, que la IA no abre. "
                        + "Pásalo a una persona o agrégale una abierta.");
            }
        } else if (tieneAbiertas) {
            // Sin parte calificada, mirar un entregable no es una falta suya: la falta es del
            // entregable si nadie más lo califica («nadie lo califica»).
            faltas.add(cual + " tiene abiertas y su parte calificada no tiene puntos: súbele "
                    + "los puntos al criterio para que alguien las califique.");
        } else if (!tieneCerradas) {
            faltas.add(cual + " está vacío: no tiene cerradas ni parte calificada.");
        }
        return faltas;
    }

    /**
     * La falta de un criterio cuyos puntos para abiertas y archivos no tienen nada que mirar:
     * cuántos quedan sin quien los califique y las tres salidas. Sin cerradas tiene su propio
     * texto. Empieza por «El criterio «X»» para que el panel la lleve a ese criterio y no la
     * tome por la de un entregable.
     *
     * @param total    lo que vale el criterio
     * @param cerradas lo que suman sus cerradas (menos que el total: si no, no queda nada)
     */
    static String nadaQueCalificar(String nombre, int total, int cerradas) {
        String vale = "El criterio «" + nombre + "» vale " + total;
        if (cerradas == 0) {
            return vale + " y no tiene nada que calificar: agrégale una abierta, un archivo o "
                    + "cerradas que sumen " + total + ".";
        }
        return vale + " y sus cerradas suman " + cerradas + ": nadie puede calificar los otros "
                + (total - cerradas) + ". Baja el total a " + cerradas + ", sube sus cerradas o "
                + "agrégale una abierta o un archivo.";
    }

    /**
     * Lo que le falta a un entregable: su forma, que un general cubra algo y que lo mire al
     * menos un criterio con parte calificada.
     */
    static List<String> faltasDelEntregable(Resultado r, EntregableRequerido e, boolean loCalificaAlguien) {
        List<String> faltas = new ArrayList<>(formaDelEntregable(e.getNombre(), e.getFormato()));
        String cual = "«" + e.getNombre() + "»";
        boolean sinCubrir = EntregableRequerido.PREGUNTAS.equals(e.getAlcance())
                && r.cubreDe(e.getId()).isEmpty();
        if (sinCubrir) {
            faltas.add(cual + " no cubre ninguna pregunta: elige «Toda la prueba» o las "
                    + "preguntas que reúne.");
        } else if (!loCalificaAlguien) {
            faltas.add(cual + ": nadie lo califica. " + (EntregableRequerido.PREGUNTA.equals(e.getAlcance())
                    ? "El criterio de su pregunta no tiene parte calificada: súbele los puntos."
                    : "Ningún criterio que lo mira tiene parte calificada: súbele los puntos a "
                            + "uno de ellos o cambia lo que cubre."));
        }
        return faltas;
    }

    /** El tiempo de una versión, tal como lo exige publicar. «Sin cronómetro» no pide días. */
    public static List<String> tiempoParaPublicar(VersionBanco v) {
        List<String> faltas = new ArrayList<>();
        if (v.getModalidad() == null) {
            faltas.add("Falta el tiempo: elige «Cronometrada», con sus minutos, o «Sin cronómetro».");
        } else if (CRONOMETRADA.equals(v.getModalidad())
                && (v.getDuracionMinutos() == null || v.getDuracionMinutos() < MINUTOS_MINIMOS)) {
            faltas.add("Faltan los minutos: una prueba cronometrada dura al menos "
                    + MINUTOS_MINIMOS + " minutos.");
        }
        return faltas;
    }

    /**
     * La fecha límite para dar la prueba (de la vacante), tal como la exige publicar: puesta
     * y futura. Nula si está bien.
     */
    public static String faltaDeLaFecha(Instant cierraEn, Instant ahora) {
        if (cierraEn == null) {
            return FALTA_LA_FECHA;
        }
        if (!cierraEn.isAfter(ahora)) {
            return "La fecha límite para dar la prueba ya pasó: pon una futura.";
        }
        return null;
    }

    /** El texto de la falta de la fecha: el panel lo reconoce para llevar a la configuración. */
    public static final String FALTA_LA_FECHA = "Falta la fecha límite para dar la prueba.";

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
