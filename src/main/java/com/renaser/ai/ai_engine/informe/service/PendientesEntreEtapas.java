package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.ContenidoDelCandidato;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.hay;
import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.limpio;

/**
 * El seguimiento de pendientes sin base de datos ni IA (spec seguimiento-de-pendientes-entre-etapas):
 * qué se le manda al agente, cómo se comprueba lo que devuelve y cómo se pone en el informe. Todo
 * aquí es una cuenta que se puede probar sola.
 *
 * <p><b>Qué se sigue.</b> Las dudas del CV que el informe ya enseña (las declaradas y las sin
 * información, con su pregunta de validación) y, en la prueba, también lo que la evaluación dejó
 * por verificar (falta de evidencia, alertas y patrones, D-1).
 *
 * <p><b>Lo comprueba el código, no la IA</b>, antes de guardar. Un «resuelto» o un «empeoró» se
 * guarda como «sigue pendiente», y cuenta como descartado con su motivo, cuando:
 * <ul>
 *   <li>no dice en qué respuesta se basa, o esa respuesta no existe;
 *   <li>se basa en un entregable que no se pudo leer;
 *   <li>la cita no está literal y seguida en esa respuesta (sin distinguir mayúsculas ni
 *       espacios repetidos);
 *   <li>la explicación no pasa las reglas de una línea del redactor
 *       ({@link RedaccionDelInforme#vale}).
 * </ul>
 * Una duda que no está en la lista se ignora, y una que el agente no devolvió queda «sigue
 * pendiente»: las dos cuentan como descartadas.
 *
 * <p><b>No califica.</b> Nada de aquí lee ni escribe una nota: cambia lo que dicen tres bloques del
 * informe y añade uno, «Lo que aclaró la prueba». El PDF y el visor los pintan como cualquier otro.
 */
public final class PendientesEntreEtapas {

    public static final String RESUELTA = "RESUELTA";
    public static final String SIGUE_PENDIENTE = "SIGUE_PENDIENTE";
    public static final String EMPEORO = "EMPEORO";
    /** De dónde viene la duda: del CV o de lo que dejó la evaluación. */
    public static final String DEL_CV = "CV";
    public static final String DE_LA_EVALUACION = "EVALUACION";
    /** El bloque nuevo del informe de la prueba. */
    public static final String BLOQUE_ACLARADO = "ACLARO_LA_PRUEBA";
    static final String TITULO_ACLARADO = "Lo que aclaró la prueba";
    static final String BLOQUE_PIDIO_EL_CV = "PIDIO_EL_CV";
    static final String BLOQUE_PENDIENTE = "PENDIENTE";
    static final String GRUPO_DEL_CV = "Del CV";
    static final String GRUPO_DE_LA_EVALUACION = "De la evaluación";
    /** El tope de la IA de la prueba: lo que no cabe en una llamada se parte en tandas. */
    static final int CARACTERES_POR_LLAMADA = 60_000;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ETAPA_PRUEBA = EtapaDelInforme.PRUEBA.name();
    /** Lo que se quita de los dos extremos de una cita: comillas, puntos suspensivos y espacios. */
    private static final String BORDES_DE_LA_CITA = "«»\"“”'‘’….";

    private PendientesEntreEtapas() {
    }

    // ============================================================ lo que se sigue

    /**
     * Una duda abierta y cómo quedó. Es lo que se guarda (una copia: las afirmaciones del CV se
     * borran al recalificarlo).
     *
     * @param codigo        D1, D2… en el orden en que se mandan
     * @param origen        CV · EVALUACION
     * @param clasificacion en las del CV, DECLARADA o FALTA_INFO; en las de la evaluación, su
     *                      etiqueta de siempre («Falta evidencia», «Demasiado ideal», «Patrón»…)
     * @param estado        RESUELTA · SIGUE_PENDIENTE · EMPEORO; nulo mientras no se ha seguido
     * @param pregunta      el número de la pregunta en que se basa, como en el anexo
     * @param entregable    o el nombre del entregable en que se basa (solo en la prueba)
     * @param citadaEn      la etapa de la cita: EVALUACION o PRUEBA
     */
    public record Duda(String codigo, String origen, String texto, String clasificacion,
                       String preguntaDeValidacion, String estado, Integer pregunta, String entregable,
                       String cita, String explicacion, String citadaEn) {

        /** Una duda que todavía nadie ha seguido. */
        public static Duda abierta(String codigo, String origen, String texto, String clasificacion,
                                   String preguntaDeValidacion) {
            return new Duda(codigo, origen, texto, clasificacion, preguntaDeValidacion, null, null, null,
                    null, null, null);
        }

        public Duda conCodigo(String nuevo) {
            return new Duda(nuevo, origen, texto, clasificacion, preguntaDeValidacion, estado, pregunta,
                    entregable, cita, explicacion, citadaEn);
        }

        Duda conEstado(String nuevo, Fuente fuente, String laCita, String laExplicacion, String etapa) {
            return new Duda(codigo, origen, texto, clasificacion, preguntaDeValidacion, nuevo,
                    fuente.pregunta(), fuente.entregable(), laCita, laExplicacion, etapa);
        }

        /** La misma, con «sigue pendiente» si nadie la había seguido. */
        Duda seguida() {
            return estado != null ? this : new Duda(codigo, origen, texto, clasificacion,
                    preguntaDeValidacion, SIGUE_PENDIENTE, pregunta, entregable, cita, explicacion, citadaEn);
        }

        public boolean resuelta() {
            return RESUELTA.equals(estado);
        }

        public boolean empeoro() {
            return EMPEORO.equals(estado);
        }
    }

    /**
     * Lo que el candidato escribió y se puede citar: una respuesta abierta («P3», la pregunta 3 del
     * anexo) o un entregable subido como archivo («E1»).
     *
     * @param referencia de un entregable, con qué archivo llegó: es lo que entra en la huella, para
     *                   no tener que leer el archivo cada vez que alguien abre el informe
     */
    public record Fuente(String codigo, Integer pregunta, String entregable, String enunciado, String texto,
                         String porQueNoSePuedeLeer, String referencia) {

        public static Fuente respuesta(int numero, String enunciado, String texto) {
            return new Fuente("P" + numero, numero, null, enunciado, texto, null, null);
        }

        public static Fuente entregable(int orden, String nombre, String referencia) {
            return new Fuente("E" + orden, null, nombre, null, null, null, referencia);
        }

        /** El entregable con lo que se pudo sacar de él, o el motivo de que no saliera nada. */
        public Fuente leida(String contenido, String porQueNo) {
            return new Fuente(codigo, pregunta, entregable, enunciado, limpio(contenido),
                    hay(contenido) ? null : porQueNo, referencia);
        }

        public boolean sePuedeLeer() {
            return hay(texto);
        }

        public boolean esRespuesta() {
            return pregunta != null;
        }
    }

    /** Lo que se sigue en una etapa: sus dudas y lo que se puede leer. */
    public record Seguir(String etapa, String vacante, List<Duda> dudas, List<Fuente> fuentes) {
    }

    // ============================================================ lo que recibe el agente

    /** @param enLaEvaluacion en la prueba, cómo quedó la duda tras la evaluación; nulo si no se siguió */
    public record DudaInsumo(String codigo, String origen, String texto, String clasificacion,
                             String preguntaDeValidacion, String enLaEvaluacion) {
    }

    public record RespuestaInsumo(String fuente, String pregunta, ContenidoDelCandidato respuesta) {
    }

    /** Un entregable: su texto envuelto, o solo el motivo de que no se pudiera leer. */
    public record EntregableInsumo(String fuente, String nombre, ContenidoDelCandidato contenido,
                                   String porQueNoSePuedeLeer) {
    }

    public record Insumo(String etapa, String vacante, List<DudaInsumo> dudas,
                         List<RespuestaInsumo> respuestas, List<EntregableInsumo> entregables) {
    }

    // ============================================================ lo que devuelve

    public record Revision(String codigo, String estado, String fuente, String cita, String explicacion) {
    }

    /** Lo que devuelve el modelo, tal cual. */
    public record Respuesta(List<Revision> dudas) {
    }

    /** Lo que se guarda: cada duda con su estado, y lo que se descartó con su motivo. */
    public record Resultado(List<Duda> dudas, int descartados, List<String> motivos) {
    }

    // ============================================================ huella, tandas y atajos

    /** Si hay algo que leer: sin respuestas con texto ni entregables, no se llama al modelo. */
    public static boolean hayQueLeer(Seguir seguir) {
        return seguir.fuentes().stream().anyMatch(f -> !f.esRespuesta() || f.sePuedeLeer());
    }

    /** Si de verdad hay texto: un entregable del que no salió nada no resuelve nada. */
    public static boolean hayTexto(Seguir seguir) {
        return seguir.fuentes().stream().anyMatch(Fuente::sePuedeLeer);
    }

    /** Sin nada que leer, sin llamar: cada duda conserva su estado («sigue pendiente» si no tenía). */
    public static Resultado sinLlamar(Seguir seguir) {
        return new Resultado(seguir.dudas().stream().map(Duda::seguida).toList(), 0, List.of());
    }

    /**
     * Un resumen estable de los datos: si cambia una duda, su estado, una respuesta o un
     * entregable, cambia la huella. Del entregable cuenta con qué archivo llegó, no su texto.
     */
    public static String huella(Seguir seguir) {
        StringBuilder s = new StringBuilder();
        s.append(seguir.etapa()).append('|');
        for (Duda d : seguir.dudas()) {
            s.append(d.codigo()).append('=').append(d.origen()).append(':').append(d.texto()).append(':')
                    .append(d.clasificacion()).append(':').append(d.preguntaDeValidacion()).append(':')
                    .append(d.estado()).append(':').append(d.pregunta()).append(':').append(d.entregable())
                    .append(':').append(d.cita()).append(':').append(d.citadaEn()).append('|');
        }
        for (Fuente f : seguir.fuentes()) {
            s.append(f.codigo()).append('=').append(f.enunciado()).append(':').append(f.entregable()).append(':')
                    .append(f.esRespuesta() ? f.texto() : f.referencia()).append('|');
        }
        return RedaccionDelInforme.sha256(s.toString());
    }

    /**
     * Lo que se manda, partido en tandas que caben en una llamada. Todas llevan todas las dudas;
     * lo que se reparte es lo que se lee. Casi siempre es una sola.
     */
    public static List<Insumo> tandas(Seguir seguir) {
        List<DudaInsumo> dudas = seguir.dudas().stream()
                .map(d -> new DudaInsumo(d.codigo(), d.origen(), d.texto(), d.clasificacion(),
                        d.preguntaDeValidacion(), d.estado()))
                .toList();
        int base = largo(dudas) + 500;
        List<List<Fuente>> grupos = new ArrayList<>();
        List<Fuente> actual = new ArrayList<>();
        int largo = base;
        for (Fuente f : seguir.fuentes()) {
            int suyo = largo(f);
            if (!actual.isEmpty() && largo + suyo > CARACTERES_POR_LLAMADA) {
                grupos.add(actual);
                actual = new ArrayList<>();
                largo = base;
            }
            actual.add(f);
            largo += suyo;
        }
        if (!actual.isEmpty()) {
            grupos.add(actual);
        }
        return grupos.stream().map(g -> insumo(seguir, dudas, g)).toList();
    }

    private static Insumo insumo(Seguir seguir, List<DudaInsumo> dudas, List<Fuente> fuentes) {
        List<RespuestaInsumo> respuestas = new ArrayList<>();
        List<EntregableInsumo> entregables = new ArrayList<>();
        for (Fuente f : fuentes) {
            if (f.esRespuesta()) {
                respuestas.add(new RespuestaInsumo(f.codigo(), f.enunciado(), new ContenidoDelCandidato(f.texto())));
            } else {
                entregables.add(new EntregableInsumo(f.codigo(), f.entregable(),
                        f.sePuedeLeer() ? new ContenidoDelCandidato(f.texto()) : null,
                        f.sePuedeLeer() ? null : f.porQueNoSePuedeLeer()));
            }
        }
        return new Insumo(seguir.etapa(), seguir.vacante(), dudas, respuestas, entregables);
    }

    private static int largo(Object algo) {
        try {
            return JSON.writeValueAsString(algo).length();
        } catch (RuntimeException e) {
            return CARACTERES_POR_LLAMADA;
        }
    }

    // ============================================================ la comprobación

    /** Lo que vale de una revisión del agente, o el motivo de que no valga. */
    private record Comprobada(String motivo, Fuente fuente, String cita, String explicacion) {
        static Comprobada no(String motivo) {
            return new Comprobada(motivo, null, null, null);
        }
    }

    /**
     * Cada duda con su estado tras comprobar lo que devolvió el agente, en una o varias tandas. Si
     * dos tandas dicen cosas distintas de la misma duda, una contradicción comprobada pesa más que
     * una resolución: es lo que hay que preguntar en la entrevista.
     */
    public static Resultado validar(Seguir seguir, List<Respuesta> respuestas) {
        Revisor revisor = new Revisor(seguir);
        for (Respuesta respuesta : respuestas) {
            if (respuesta != null && respuesta.dudas() != null) {
                respuesta.dudas().forEach(revisor::revisar);
            }
        }
        return revisor.resultado();
    }

    /** Lo que se va sabiendo de cada duda al repasar lo que devolvió el agente, tanda a tanda. */
    private static final class Revisor {

        private final Seguir seguir;
        private final String datosEnMinusculas;
        private final Set<String> cifras;
        private final Map<String, Fuente> fuentes = new HashMap<>();
        private final Map<String, Duda> porCodigo = new LinkedHashMap<>();
        private final Map<String, Duda> decididas = new HashMap<>();
        private final Set<String> devueltas = new HashSet<>();
        private final List<String> motivos = new ArrayList<>();

        Revisor(Seguir seguir) {
            this.seguir = seguir;
            String datos = textoDe(seguir);
            this.datosEnMinusculas = datos.toLowerCase(Locale.ROOT);
            this.cifras = RedaccionDelInforme.cifras(datos, true);
            seguir.fuentes().forEach(f -> fuentes.put(f.codigo(), f));
            seguir.dudas().forEach(d -> porCodigo.put(d.codigo(), d));
        }

        void revisar(Revision r) {
            String codigo = r == null || r.codigo() == null ? "" : r.codigo().strip().toUpperCase(Locale.ROOT);
            Duda duda = porCodigo.get(codigo);
            if (duda == null) {
                motivos.add("«" + codigo + "» no es ninguna de las dudas que recibió");
                return;
            }
            devueltas.add(codigo);
            String estado = estadoDe(r.estado());
            if (SIGUE_PENDIENTE.equals(estado)) {
                return;
            }
            Comprobada c = estado == null ? Comprobada.no("no dice cómo quedó («" + r.estado() + "»)")
                    : comprobar(r, fuentes, cifras, datosEnMinusculas);
            if (c.motivo() != null) {
                motivos.add(codigo + ": " + c.motivo());
                return;
            }
            Duda previa = decididas.get(codigo);
            if (previa == null || (EMPEORO.equals(estado) && !previa.empeoro())) {
                decididas.put(codigo, duda.conEstado(estado, c.fuente(), c.cita(), c.explicacion(), seguir.etapa()));
            }
        }

        Resultado resultado() {
            List<Duda> salida = new ArrayList<>();
            for (Duda d : seguir.dudas()) {
                if (!devueltas.contains(d.codigo())) {
                    motivos.add(d.codigo() + ": el agente no la devolvió");
                }
                salida.add(decididas.getOrDefault(d.codigo(), d).seguida());
            }
            return new Resultado(salida, motivos.size(), List.copyOf(motivos));
        }
    }

    private static Comprobada comprobar(Revision r, Map<String, Fuente> fuentes, Set<String> cifras,
                                        String datosEnMinusculas) {
        Fuente fuente = fuentes.get(fuenteDe(r.fuente()));
        if (fuente == null) {
            return Comprobada.no("no dice en qué respuesta se basa, o esa respuesta no existe («"
                    + r.fuente() + "»)");
        }
        if (!fuente.sePuedeLeer()) {
            return Comprobada.no("se basa en el entregable «" + fuente.entregable() + "», que no se pudo leer");
        }
        String cita = citaLimpia(r.cita());
        if (!hay(cita)) {
            return Comprobada.no("no trae cita");
        }
        if (!normalizado(fuente.texto()).contains(normalizado(cita))) {
            return Comprobada.no("la cita no está tal cual en " + (fuente.esRespuesta()
                    ? "la respuesta de la pregunta " + fuente.pregunta() : "el entregable «" + fuente.entregable() + "»"));
        }
        if (!hay(r.explicacion())) {
            return Comprobada.no("no trae explicación");
        }
        if (!RedaccionDelInforme.vale(r.explicacion(), cifras, datosEnMinusculas, List.of(), null)) {
            return Comprobada.no("la explicación no pasa la comprobación (trae «…», una cifra o un nombre "
                    + "que no está en los datos, o la marca del anonimizador)");
        }
        return new Comprobada(null, fuente, cita, r.explicacion().strip());
    }

    /** «resuelto», «Empeoró», «sigue pendiente»… al estado que se guarda; nulo si no es ninguno. */
    static String estadoDe(String estado) {
        if (estado == null) {
            return null;
        }
        String e = estado.strip().toUpperCase(Locale.ROOT).replace('Ó', 'O').replace(' ', '_');
        return switch (e) {
            case "RESUELTO", "RESUELTA" -> RESUELTA;
            case "EMPEORO" -> EMPEORO;
            case "SIGUE_PENDIENTE", "PENDIENTE" -> SIGUE_PENDIENTE;
            default -> null;
        };
    }

    /** «P3», «p3» o un 3 a secas son la pregunta 3; «E1», el primer entregable. */
    static String fuenteDe(String fuente) {
        if (fuente == null) {
            return "";
        }
        String f = fuente.strip().toUpperCase(Locale.ROOT);
        return !f.isEmpty() && f.chars().allMatch(Character::isDigit) ? "P" + f : f;
    }

    /**
     * La cita sin comillas, puntos suspensivos ni el punto final de los extremos, y con un solo
     * espacio entre palabras: así se enseña.
     */
    static String citaLimpia(String cita) {
        if (cita == null) {
            return null;
        }
        int desde = 0;
        int hasta = cita.length();
        while (desde < hasta && esBorde(cita.charAt(desde))) {
            desde++;
        }
        while (hasta > desde && esBorde(cita.charAt(hasta - 1))) {
            hasta--;
        }
        return limpio(unEspacio(cita.substring(desde, hasta), false));
    }

    private static boolean esBorde(char c) {
        return Character.isWhitespace(c) || c == '\u00A0' || BORDES_DE_LA_CITA.indexOf(c) >= 0;
    }

    /** En minúsculas y con un solo espacio entre palabras: así se compara la cita. */
    static String normalizado(String texto) {
        return unEspacio(texto, true);
    }

    private static String unEspacio(String texto, boolean enMinusculas) {
        StringBuilder s = new StringBuilder(texto.length());
        boolean espacio = false;
        for (char c : texto.toCharArray()) {
            if (Character.isWhitespace(c) || c == '\u00A0' || c == '\u202F') {
                espacio = !s.isEmpty();
                continue;
            }
            if (espacio) {
                s.append(' ');
                espacio = false;
            }
            s.append(enMinusculas ? Character.toLowerCase(c) : c);
        }
        return s.toString();
    }

    /** Todo lo que recibió el agente, en un solo texto, para buscar cifras y nombres. */
    private static String textoDe(Seguir seguir) {
        StringBuilder s = new StringBuilder();
        s.append(seguir.vacante()).append('\n');
        for (Duda d : seguir.dudas()) {
            s.append(d.codigo()).append(' ').append(d.texto()).append(' ').append(d.clasificacion()).append(' ')
                    .append(d.preguntaDeValidacion()).append(' ').append(d.cita()).append(' ')
                    .append(d.explicacion()).append('\n');
        }
        for (Fuente f : seguir.fuentes()) {
            s.append(f.codigo()).append(' ');
            if (f.esRespuesta()) {
                s.append("Pregunta ").append(f.pregunta()).append(' ');
            } else {
                s.append("Entregable ").append(f.entregable()).append(' ');
            }
            s.append(f.enunciado()).append(' ').append(f.texto()).append(' ').append(f.porQueNoSePuedeLeer())
                    .append('\n');
        }
        return s.toString();
    }

    // ============================================================ el informe con los estados

    /** El informe de la evaluación o de la prueba con el seguimiento vigente puesto. */
    public static Informe aplicar(Informe base, Resultado resultado) {
        if (resultado == null) {
            return base;
        }
        boolean enLaPrueba = ETAPA_PRUEBA.equals(base.etapa());
        List<Bloque> bloques = new ArrayList<>();
        for (Bloque b : base.bloques()) {
            if (!enLaPrueba && BLOQUE_PIDIO_EL_CV.equals(b.codigo())) {
                bloques.add(conPuntos(b, resultado.dudas().stream()
                        .filter(d -> DEL_CV.equals(d.origen()))
                        .map(d -> new Punto(textoDe(d), loQuePidio(d), DEL_CV, etiqueta(d), null))
                        .toList()));
            } else if (BLOQUE_PENDIENTE.equals(b.codigo())) {
                List<Duda> aclaradas = resultado.dudas().stream().filter(Duda::resuelta).toList();
                if (enLaPrueba && !aclaradas.isEmpty()) {
                    bloques.add(LoComunDelInforme.bloque(BLOQUE_ACLARADO, TITULO_ACLARADO, "BIEN", aclaradas.stream()
                            .map(d -> new Punto(textoDe(d), conLaExplicacion(d, true), null, null, null))
                            .toList()));
                }
                bloques.add(conPuntos(b, enLaPrueba ? paraLaEntrevista(b.puntos(), resultado)
                        : paraLaPrueba(b.puntos(), resultado)));
            } else {
                bloques.add(b);
            }
        }
        return new Informe(base.etapa(), base.titulo(), base.empresa(), base.vacante(), base.postulacionId(),
                base.candidato(), base.correo(), base.generadoEn(), base.nota(), base.grupo(), base.datosClave(),
                base.recuentos(), base.experiencia(), base.resumen(), base.evaluado(), bloques, base.enlaces(),
                base.detalleTitulo(), base.detalle(), base.detallePie(), base.anexo(), base.aviso(),
                base.textosEnCamino(), base.nombreArchivo());
    }

    /** «Por verificar en la prueba del puesto»: delante, lo del CV que empeoró y lo que sigue pendiente. */
    private static List<Punto> paraLaPrueba(List<Punto> deLaEvaluacion, Resultado resultado) {
        List<Punto> puntos = new ArrayList<>(abiertasDelCv(resultado, null, false));
        puntos.addAll(deLaEvaluacion);
        return puntos;
    }

    /**
     * «Por verificar en la entrevista»: «Del CV» solo con lo que sigue abierto, y «De la
     * evaluación» sin lo que la prueba aclaró. «De la prueba» no cambia.
     */
    private static List<Punto> paraLaEntrevista(List<Punto> puntos, Resultado resultado) {
        Set<String> aclaradas = new HashSet<>();
        resultado.dudas().stream().filter(d -> d.resuelta() && DE_LA_EVALUACION.equals(d.origen()))
                .forEach(d -> aclaradas.add(clave(d.texto(), d.clasificacion())));
        List<Punto> delCv = abiertasDelCv(resultado, GRUPO_DEL_CV, true);
        List<Punto> salida = new ArrayList<>();
        boolean cvPuesto = false;
        for (Punto p : puntos) {
            if (GRUPO_DEL_CV.equals(p.grupo())) {
                if (!cvPuesto) {
                    salida.addAll(delCv);
                    cvPuesto = true;
                }
            } else if (!GRUPO_DE_LA_EVALUACION.equals(p.grupo())
                    || !aclaradas.contains(clave(p.texto(), p.etiqueta()))) {
                salida.add(p);
            }
        }
        if (!cvPuesto) {
            salida.addAll(delCv);
        }
        return salida;
    }

    private static String clave(String texto, String etiqueta) {
        return (texto == null ? "" : texto.strip()) + "|" + etiqueta;
    }

    /** Las dudas del CV que siguen abiertas: primero las que empeoraron, después las pendientes. */
    private static List<Punto> abiertasDelCv(Resultado resultado, String grupo, boolean enLaPrueba) {
        List<Punto> empeoradas = new ArrayList<>();
        List<Punto> pendientes = new ArrayList<>();
        for (Duda d : resultado.dudas()) {
            if (!DEL_CV.equals(d.origen()) || d.resuelta()) {
                continue;
            }
            if (d.empeoro()) {
                empeoradas.add(new Punto(textoDe(d), juntar(laCita(d, enLaPrueba), d.preguntaDeValidacion()),
                        DEL_CV, etiqueta(d), grupo));
            } else {
                pendientes.add(new Punto(textoDe(d), limpio(d.preguntaDeValidacion()), DEL_CV, etiqueta(d), grupo));
            }
        }
        empeoradas.addAll(pendientes);
        return empeoradas;
    }

    /** El detalle en «Lo que pidió el CV»: la cita con su explicación, o la pregunta de validación. */
    private static String loQuePidio(Duda d) {
        return d.resuelta() || d.empeoro() ? conLaExplicacion(d, false) : limpio(d.preguntaDeValidacion());
    }

    static String textoDe(Duda d) {
        return DEL_CV.equals(d.origen()) ? "«" + d.texto() + "»" : d.texto();
    }

    static String etiqueta(Duda d) {
        if (d.resuelta()) {
            return "Resuelto";
        }
        return d.empeoro() ? "Empeoró" : "Sigue pendiente";
    }

    /**
     * «Pregunta 3: «cita».» En el informe de la prueba, una cita que viene de la evaluación lo dice:
     * «En la evaluación, pregunta 7: «cita».», porque el anexo de la prueba tiene otras preguntas.
     */
    static String laCita(Duda d, boolean enLaPrueba) {
        if (!hay(d.cita())) {
            return null;
        }
        String donde = d.pregunta() != null ? "pregunta " + d.pregunta() : "entregable «" + d.entregable() + "»";
        donde = enLaPrueba && DE_LA_EVALUACION.equals(d.citadaEn()) ? "En la evaluación, " + donde
                : Character.toUpperCase(donde.charAt(0)) + donde.substring(1);
        return donde + ": «" + d.cita() + "».";
    }

    private static String conLaExplicacion(Duda d, boolean enLaPrueba) {
        String explicacion = limpio(d.explicacion());
        if (explicacion != null && ".!?".indexOf(explicacion.charAt(explicacion.length() - 1)) < 0) {
            explicacion = explicacion + ".";
        }
        return juntar(laCita(d, enLaPrueba), explicacion);
    }

    private static String juntar(String una, String otra) {
        if (!hay(una)) {
            return limpio(otra);
        }
        return hay(otra) ? una + " " + otra.strip() : una;
    }

    private static Bloque conPuntos(Bloque b, List<Punto> puntos) {
        return new Bloque(b.codigo(), b.titulo(), b.tono(), b.vacio(), puntos);
    }
}
