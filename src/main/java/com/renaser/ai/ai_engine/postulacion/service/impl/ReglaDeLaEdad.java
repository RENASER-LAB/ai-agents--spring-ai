package com.renaser.ai.ai_engine.postulacion.service.impl;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Cuándo una cifra con «años» es una edad —y se tapa— y cuándo son los años de lo que hizo la
 * persona —y llegan a la IA— (spec la-ia-sabe-que-dia-es-hoy, decisión D-3 del 16/11/2026, que
 * sustituye la lista de D-1, con la precisión de D-2).
 *
 * <p><b>Toda «N años» se tapa</b>, salvo una lista blanca mínima y literal. En este orden:
 * <ol>
 *   <li><b>«de experiencia», «de trayectoria» o «de servicio» justo detrás no se tapan nunca</b>
 *       (D-2): «Tengo 10 años de experiencia», «Profesional de 10 años de experiencia» o «Juan
 *       Pérez, 10 años de experiencia» llegan a la IA. Es la única excepción a las señales.</li>
 *   <li><b>Una señal de edad delante tapa siempre</b>, venga lo que venga detrás:
 *       <ul>
 *         <li>«Tengo», «Cumplo» o «Edad» justo delante, también con «más de» en medio y con
 *             cualquier signo entre la señal y la cifra ({@link #TRAS_LA_SENAL}): «Tengo más de 30
 *             años», «Cumplo 34 años en marzo», «Edad: más de 30 años», «Edad – 34 años como
 *             cajero», «Edad.- más de 30 años».</li>
 *         <li>Cualquier palabra + «de» justo delante, también con «más de» en medio: «Alumna de
 *             20 años», «Licenciada en Administración de 28 años», «Mujer de más de 40 años».
 *             La única palabra que no cuenta es el propio «más» de «más de 10 años en ventas».</li>
 *         <li>Un nombre y una coma: «Juan Pérez, 34 años», «Ana de la Cruz, 34 años», «Luis del
 *             Pino, 34 años». Basta con que la palabra pegada a la coma empiece por mayúscula —el
 *             apellido—, así que las partículas en minúscula no cortan el nombre. También una
 *             persona de {@link #PERSONAS} escrita en minúscula («peruana, 34 años»).</li>
 *         <li>«34 años» solo en su línea: lo que deja ver tiene que ir en la misma línea que
 *             «años» (la primera palabra detrás) o delante en esa línea («durante», «más de»), así
 *             que una cifra sola en su línea no tiene nada que la salve y se tapa. La primera
 *             palabra de la línea siguiente («En búsqueda de…», «Dedicada a…») nunca cuenta.</li>
 *       </ul>
 *       La señal puede estar en la línea de arriba («Joven de⏎34 años»): tapar de más es el lado
 *       seguro.</li>
 *   <li><b>Sin señal, solo deja ver la lista blanca</b>:
 *       <ul>
 *         <li>«durante» o «más de» justo delante, en la misma línea.</li>
 *         <li>Detrás, en la misma línea que «años»: «de experiencia/trayectoria/servicio/labor»;
 *             «en el/la cargo/puesto/área/rubro/sector»; «en &lt;área&gt;» con un área de
 *             {@link #AREAS}; «como &lt;cargo&gt;» con un cargo de {@link #CARGOS}; o un gerundio
 *             de {@link #GERUNDIOS_LABORALES}.</li>
 *       </ul></li>
 *   <li><b>Todo lo demás se tapa</b>: «en marzo», «en miraflores», «dedicada a mi familia», «a
 *       cargo de mis hijos», «al servicio de Dios», «de trabajo», «de antigüedad», «como madre».
 *       Mandar la edad a un tercero es peor que perder un dato; es aceptable que se tape alguna
 *       frase de experiencia poco habitual.</li>
 * </ol>
 *
 * <p>Las palabras se comparan sin tildes y en minúsculas (así «AÑOS», «DESEMPEÑÁNDOME» o «MÁS»
 * casan igual que en minúsculas). Lo que se mira alrededor de la cifra está acotado a
 * {@link #VENTANA} caracteres y todos los cuantificadores son posesivos: un texto hecho a mala no
 * puede hacer que el motor recorra el currículum entero por cada cifra.
 */
final class ReglaDeLaEdad {

    /** Ver {@code AnonimizadorCv#BANDERAS}: sin la bandera Unicode, «AÑOS» o «MÁS» no casan. */
    private static final int BANDERAS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

    /** Cuánto se mira delante y detrás de la cifra. Sobra para «Licenciada en Administración de». */
    private static final int VENTANA = 80;

    /** Cuántas palabras detrás de «años» hacen falta, como mucho, para decidir («en la búsqueda y selección»). */
    private static final int PALABRAS_DETRAS = 6;

    /**
     * Una cifra de 1 a 3 dígitos con «años»; «de edad» detrás va dentro y se tapa entero.
     *
     * <p>Los mismos bordes que la regla de antes: la cifra no va pegada a otra cifra —para no
     * partir un «1234»— y detrás no sigue una letra. Sin {@code \b} delante, para que «Nº34 años»
     * se tape: con la bandera Unicode la «º» cuenta como letra.
     */
    private static final Pattern CIFRA_CON_ANIOS = patron(
            "(?<!\\d)\\d{1,3}\\s*+a[ñn]os(?![\\p{L}\\p{N}])(\\s++de\\s++edad(?![\\p{L}\\p{N}]))?");

    // ------------------------------------------------------------------ delante de la cifra
    // Todas acaban en \z: lo que cuenta es lo que va JUSTO delante de la cifra. Se buscan en
    // una región de VENTANA caracteres con los bordes transparentes, para que la mirada hacia
    // atrás de «(?<![\p{L}\p{N}])» vea la letra de antes aunque quede fuera de la región.

    /** El «más de» que puede ir entre la señal y la cifra: «Tengo más de 30», «Mujer de más de 40». */
    private static final String MAS_DE = "(?:m[aá]s\\s++de\\s*+)?+";

    /** Cualquier tramo de signos y espacios, sin letras ni cifras; también saltos de línea. */
    private static final String SIGNOS = "[^\\p{L}\\p{N}]*+";

    /**
     * Lo que puede ir entre «Edad», «Tengo» o «Cumplo» y la cifra. No es una lista de signos: es
     * cualquier tramo de signos y espacios —«:», «-», la raya que pone Word al escribir « - »
     * («–», «—»), el «.-» de los formularios, «.», «=», «·», «|», un salto de línea…—; con un
     * «aprox.», «aproximada(mente)», «actual(mente)», «unos» o «casi» si acaso en medio, y luego el
     * «más de»: «Edad – 34 años», «Edad.- más de 30 años», «Edad aprox. 34 años», «Tengo
     * actualmente 34 años», «EDAD = MÁS DE 30 AÑOS». Fuera de esas palabras no cabe ninguna
     * letra: la señal solo cuenta cuando la cifra va justo detrás.
     *
     * <p>Lo usa también la edad rotulada de {@link AnonimizadorCv} («Edad – 34», sin «años»), para
     * que las dos reconozcan el rótulo igual.
     */
    static final String TRAS_LA_SENAL = SIGNOS
            + "(?:(?:(?:aprox|actual)\\p{L}*+|unos|casi)" + SIGNOS + ")?+" + MAS_DE;

    /** «Tengo 34 años», «Cumplo 34 años», «Edad: 34 años», «Edad – 34 años», y con «más de» en medio. */
    private static final Pattern TENGO_CUMPLO_EDAD = patron(
            "(?<![\\p{L}\\p{N}])(?:tengo|cumplo|edad)" + TRAS_LA_SENAL + "\\z");

    /**
     * Cualquier palabra + «de», y con «más de» en medio: «Alumna de 20», «Mujer de más de 40». El
     * grupo 1 es la palabra (falta si el «de» abre el texto o va tras un signo: «Hombre, de 34»).
     * El grupo de la palabra es voraz y no posesivo a propósito: si detrás de la palabra no viene
     * «de», se prueba sin ella.
     */
    private static final Pattern PALABRA_Y_DE = patron(
            "(?<![\\p{L}\\p{N}])(?:(\\p{L}++)\\s++)?de\\s*+" + MAS_DE + "\\z");

    /** Una palabra y una coma: «Pérez, 34», «Cruz, 34», «peruana, 34». El grupo 1 es la palabra. */
    private static final Pattern PALABRA_Y_COMA = patron(
            "(?<![\\p{L}\\p{N}])(\\p{L}[\\p{L}'’-]*+)\\h*+,\\s*+\\z");

    /** «durante 6 años», «más de 10 años»: en la misma línea, solo espacios horizontales entre medias. */
    private static final Pattern DURANTE_O_MAS_DE = patron(
            "(?<![\\p{L}\\p{N}])(?:durante|m[aá]s\\h++de)\\h++\\z");

    // ------------------------------------------------------------------ detrás de la cifra

    /** La primera palabra detrás de «años»: en la misma línea, solo espacios horizontales delante. */
    private static final Pattern PRIMERA_PALABRA = patron("\\h++(\\p{L}[\\p{L}.'’]*+)");

    /** Las siguientes: ahí sí cabe un salto, porque un PDF parte «5 años de / experiencia». */
    private static final Pattern OTRA_PALABRA = patron("\\s++(\\p{L}[\\p{L}.'’]*+)");

    private static final Pattern MARCAS = Pattern.compile("\\p{M}++");

    /** Personas que, escritas en minúscula y con una coma detrás, dicen que la cifra es su edad. */
    private static final Set<String> PERSONAS = Set.of(
            "joven", "mujer", "hombre", "profesional", "senor", "senora", "senorita", "varon", "dama",
            "caballero", "chico", "chica", "muchacho", "muchacha", "persona", "adulto", "adulta",
            "padre", "madre", "papa", "mama", "estudiante", "alumno", "alumna", "universitario",
            "universitaria", "practicante", "egresado", "egresada", "graduado", "graduada",
            "titulado", "titulada", "bachiller", "licenciado", "licenciada", "postulante",
            "candidato", "candidata", "ciudadano", "ciudadana", "peruano", "peruana", "extranjero",
            "extranjera", "venezolano", "venezolana", "colombiano", "colombiana", "ecuatoriano",
            "ecuatoriana", "boliviano", "boliviana", "chileno", "chilena", "argentino", "argentina",
            "soltero", "soltera", "casado", "casada", "divorciado", "divorciada", "viudo", "viuda",
            "conviviente");

    /** Lo único que, detrás de «N años de …», gana a una señal de edad delante (D-2). */
    private static final Set<String> GANAN_A_LA_SENAL = Set.of("experiencia", "trayectoria", "servicio");

    /** «N años de …» que deja ver sin señal. */
    private static final Set<String> DE_LO_QUE_HIZO = Set.of("experiencia", "trayectoria", "servicio", "labor");

    /** «N años en el/la …». */
    private static final Set<String> ARTICULO_DEL_CARGO = Set.of("el", "la");

    private static final Set<String> CARGO_O_SECTOR = Set.of("cargo", "puesto", "area", "rubro", "sector");

    /** El artículo que puede ir delante de un área o de un cargo: «en la docencia», «como un jefe». */
    private static final Set<String> ARTICULOS = Set.of("el", "la", "los", "las", "un", "una");

    /**
     * Las áreas de trabajo de «N años en &lt;área&gt;», sin tildes y en minúsculas; las de varias
     * palabras, separadas por un espacio. Lista fija: «en marzo», «en miraflores» o «en familia»
     * no están y se tapan. «búsqueda» va solo dentro de «búsqueda y selección»: suelta es una
     * búsqueda de empleo.
     */
    private static final List<List<String>> AREAS = Stream.of(
                    "ventas", "recursos humanos", "rrhh", "rr hh", "logistica", "administracion",
                    "contabilidad", "finanzas", "produccion", "atencion al cliente",
                    "atencion al publico", "servicio al cliente", "busqueda y seleccion", "seleccion",
                    "reclutamiento", "docencia", "educacion", "salud", "enfermeria", "farmacia",
                    "construccion", "almacen", "almacenes", "operaciones", "marketing", "mercadeo",
                    "publicidad", "sistemas", "ti", "informatica", "programacion", "soporte",
                    "telecomunicaciones", "comunicaciones", "periodismo", "mineria", "banca",
                    "seguros", "retail", "cobranzas", "compras", "tesoreria", "auditoria",
                    "planillas", "capacitacion", "mantenimiento", "transporte", "distribucion",
                    "importaciones", "exportaciones", "comercio exterior", "seguridad",
                    "telemarketing", "call center", "contact center", "consultoria", "ingenieria",
                    "manufactura", "gastronomia", "hoteleria", "turismo", "agroindustria",
                    "gestion humana")
            .map(area -> List.of(area.split(" ")))
            .toList();

    /** Los cargos de «N años como &lt;cargo&gt;». Lista fija: «como edad» o «como madre» se tapan. */
    private static final Set<String> CARGOS = Set.of(
            "supervisor", "supervisora", "cajero", "cajera", "analista", "asistente", "asistenta",
            "auxiliar", "jefe", "jefa", "gerente", "subgerente", "coordinador", "coordinadora",
            "administrador", "administradora", "administrativo", "administrativa", "contador",
            "contadora", "vendedor", "vendedora", "asesor", "asesora", "ejecutivo", "ejecutiva",
            "operario", "operaria", "operador", "operadora", "tecnico", "tecnica", "ingeniero",
            "ingeniera", "docente", "profesor", "profesora", "maestro", "maestra", "practicante",
            "encargado", "encargada", "recepcionista", "secretario", "secretaria", "almacenero",
            "almacenera", "chofer", "conductor", "conductora", "vigilante", "agente", "promotor",
            "promotora", "mercaderista", "reponedor", "reponedora", "despachador", "despachadora",
            "mozo", "moza", "cocinero", "cocinera", "ayudante", "digitador", "digitadora",
            "especialista", "consultor", "consultora", "director", "directora", "lider",
            "representante", "enfermero", "enfermera", "abogado", "abogada", "psicologo",
            "psicologa", "programador", "programadora", "desarrollador", "desarrolladora",
            "disenador", "disenadora", "reclutador", "reclutadora", "capacitador", "capacitadora",
            "instructor", "instructora", "auditor", "auditora", "tesorero", "tesorera", "cobrador",
            "cobradora", "teleoperador", "teleoperadora", "gestor", "gestora", "inspector",
            "inspectora", "mecanico", "mecanica", "electricista", "soldador", "obrero", "obrera",
            "residente", "telefonista", "empleado", "empleada", "trabajador", "trabajadora");

    /**
     * Los gerundios que dicen que trabajó. Lista fija: «Fernando», «Orlando» o «Armando» acaban
     * en «-ando» y son nombres; «estudiando», «buscando», «dedicándome», «sirviendo» o
     * «administrando (mi hogar)» no son un trabajo.
     */
    private static final Set<String> GERUNDIOS_LABORALES = Set.of(
            "trabajando", "laborando", "desempenando", "ejerciendo", "liderando", "gestionando",
            "dirigiendo", "coordinando", "supervisando", "vendiendo", "ensenando",
            "dictando", "impartiendo", "asesorando", "capacitando", "programando", "disenando",
            "ejecutando", "implementando", "analizando", "auditando", "reclutando", "seleccionando",
            "negociando", "operando", "produciendo", "fabricando", "instalando", "reparando");

    /** Lo que se le pega a un gerundio: «desempeñándome», «desempeñándose». */
    private static final List<String> PRONOMBRES = List.of("me", "se", "nos");

    private ReglaDeLaEdad() {
    }

    /** El texto con cada edad cambiada por {@code reemplazo}; los años de experiencia, enteros. */
    static String tapar(String texto, String reemplazo) {
        String literal = Matcher.quoteReplacement(reemplazo);
        return CIFRA_CON_ANIOS.matcher(texto).replaceAll(cifra ->
                esEdad(texto, cifra) ? literal : Matcher.quoteReplacement(cifra.group()));
    }

    private static boolean esEdad(String texto, MatchResult cifra) {
        if (cifra.group(1) != null) {
            return true; // «34 años de edad»
        }
        List<String> detras = palabrasDetras(texto, cifra.end());
        if (detrasVa(detras, GANAN_A_LA_SENAL)) {
            return false; // D-2
        }
        int desde = Math.max(0, cifra.start() - VENTANA);
        if (hayUnaSenalDeEdad(texto, desde, cifra.start())) {
            return true;
        }
        if (acabaEn(DURANTE_O_MAS_DE, texto, desde, cifra.start()) != null) {
            return false;
        }
        return !estaEnLaListaBlanca(detras);
    }

    private static boolean hayUnaSenalDeEdad(String texto, int desde, int hasta) {
        if (acabaEn(TENGO_CUMPLO_EDAD, texto, desde, hasta) != null) {
            return true;
        }
        Matcher palabraYDe = acabaEn(PALABRA_Y_DE, texto, desde, hasta);
        if (palabraYDe != null && !esSoloMasDe(palabraYDe)) {
            return true;
        }
        Matcher palabraYComa = acabaEn(PALABRA_Y_COMA, texto, desde, hasta);
        return palabraYComa != null && esUnNombreOAlguien(palabraYComa.group(1));
    }

    /**
     * «más de 10 años»: el «de» va con «más». No es una señal. («Mujer de más de 40» casa antes
     * por «Mujer», así que la palabra que queda en el grupo 1 es «Mujer» y sí lo es).
     */
    private static boolean esSoloMasDe(Matcher palabraYDe) {
        String palabra = palabraYDe.group(1);
        return palabra != null && "mas".equals(normal(palabra));
    }

    /** Un apellido o un nombre (con mayúscula) o una persona de la lista escrita en minúscula. */
    private static boolean esUnNombreOAlguien(String palabra) {
        return Character.isUpperCase(palabra.codePointAt(0)) || PERSONAS.contains(normal(palabra));
    }

    /** El matcher, si el patrón casa con lo que va justo delante de {@code hasta}; si no, nulo. */
    private static Matcher acabaEn(Pattern patron, String texto, int desde, int hasta) {
        Matcher m = patron.matcher(texto).region(desde, hasta).useTransparentBounds(true);
        return m.find() ? m : null;
    }

    /** Hasta {@link #PALABRAS_DETRAS} palabras detrás de «años», sin tildes y en minúsculas. */
    private static List<String> palabrasDetras(String texto, int desde) {
        int hasta = Math.min(texto.length(), desde + VENTANA);
        List<String> palabras = new ArrayList<>(PALABRAS_DETRAS);
        Matcher m = PRIMERA_PALABRA.matcher(texto).region(desde, hasta);
        while (palabras.size() < PALABRAS_DETRAS && m.lookingAt()) {
            palabras.add(normal(m.group(1)));
            m = OTRA_PALABRA.matcher(texto).region(m.end(), hasta);
        }
        return palabras;
    }

    /** «de» y una palabra de {@code segundas}: «de experiencia», «de servicio». */
    private static boolean detrasVa(List<String> palabras, Set<String> segundas) {
        return palabras.size() > 1 && "de".equals(palabras.get(0)) && segundas.contains(palabras.get(1));
    }

    private static boolean estaEnLaListaBlanca(List<String> palabras) {
        if (palabras.isEmpty()) {
            return false;
        }
        List<String> resto = palabras.subList(1, palabras.size());
        return switch (palabras.get(0)) {
            case "de" -> detrasVa(palabras, DE_LO_QUE_HIZO);
            case "en" -> enElCargoOElSector(resto) || enUnArea(resto);
            case "como" -> comoUnCargo(resto);
            default -> esUnGerundioLaboral(palabras.get(0));
        };
    }

    /** «3 años en el cargo», «10 años en el área comercial», «8 AÑOS EN EL SECTOR». */
    private static boolean enElCargoOElSector(List<String> resto) {
        return resto.size() > 1 && ARTICULO_DEL_CARGO.contains(resto.get(0))
                && CARGO_O_SECTOR.contains(resto.get(1));
    }

    /** «10 años en ventas», «en RR.HH.», «en la docencia», «en búsqueda y selección de personal». */
    private static boolean enUnArea(List<String> resto) {
        List<String> palabras = sinArticulo(resto);
        return AREAS.stream().anyMatch(area ->
                area.size() <= palabras.size() && palabras.subList(0, area.size()).equals(area));
    }

    /** «2 años como supervisor», «como jefa de tienda»; no «como edad» ni «como madre». */
    private static boolean comoUnCargo(List<String> resto) {
        List<String> palabras = sinArticulo(resto);
        return !palabras.isEmpty() && CARGOS.contains(palabras.get(0));
    }

    private static List<String> sinArticulo(List<String> palabras) {
        return !palabras.isEmpty() && ARTICULOS.contains(palabras.get(0))
                ? palabras.subList(1, palabras.size()) : palabras;
    }

    /** De la lista fija, también con el pronombre pegado: «desempeñándome». */
    private static boolean esUnGerundioLaboral(String palabra) {
        if (GERUNDIOS_LABORALES.contains(palabra)) {
            return true;
        }
        for (String pronombre : PRONOMBRES) {
            if (palabra.endsWith(pronombre)
                    && GERUNDIOS_LABORALES.contains(palabra.substring(0, palabra.length() - pronombre.length()))) {
                return true;
            }
        }
        return false;
    }

    /** Sin tildes, sin puntos ni apóstrofos y en minúsculas: «RR.HH.» → «rrhh», «AÑOS» → «anos». */
    static String normal(String palabra) {
        String sinTildes = MARCAS.matcher(Normalizer.normalize(palabra, Normalizer.Form.NFD)).replaceAll("");
        StringBuilder limpia = new StringBuilder(sinTildes.length());
        for (int i = 0; i < sinTildes.length(); i++) {
            char c = sinTildes.charAt(i);
            if (c != '.' && c != '\'' && c != '’') {
                limpia.append(c);
            }
        }
        return limpia.toString().toLowerCase(Locale.ROOT);
    }

    private static Pattern patron(String expresion) {
        return Pattern.compile(expresion, BANDERAS);
    }
}
