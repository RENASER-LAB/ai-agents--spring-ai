package com.renaser.ai.ai_engine.vacante.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Qué se puede escribir en «Qué buscar en el CV» (V80), en un solo sitio del backend.
 *
 * <p>El panel tiene la misma regla en {@code src/panel/vacantes/reglasQueBuscar.ts}, con las
 * mismas palabras y las mismas pruebas de qué pasa y qué no: el formulario la dice mientras se
 * escribe, y el servidor la vuelve a aplicar porque la API se puede llamar sin el formulario.
 *
 * <ul>
 *   <li>Las líneas vacías no cuentan, y se recortan los espacios de los extremos.
 *   <li>Una repetida —sin distinguir mayúsculas ni espacios— cuenta una vez.
 *   <li>Se guardan las seis primeras; las que pasen de seis no se buscan.
 *   <li>Cada línea admite hasta 200 caracteres: más es un 400.
 *   <li>Lo que no sirve para elegir a nadie —edad, sexo o género, estado civil, hijos o embarazo,
 *       salud o discapacidad, religión, raza, etnia, nacionalidad u origen— es un 400. Los
 *       documentos que pide la ley para el puesto sí: «Carné de salud vigente», «Carné sanitario».
 *   <li>La edad también se pide con números: «Personas de 25 a 35 años», «Máximo 30 años»,
 *       «Mayor a 40», «Hasta los 35», también tras la experiencia: «2 años de experiencia y menor
 *       de 35 años». Los años de algo no son una edad: «Más de 3 años de experiencia», «15 años
 *       en el rubro», «2 años como cajero», «Experiencia general no menor de 15 años»,
 *       «Experiencia en cocina, al menos 15 años», «durante 15 años».
 *   <li>El sexo también se pide nombrando a la persona: «Chica para atención al cliente»,
 *       «Señor para vigilancia». «Manejo de caja chica» habla de la caja, no de la persona.
 * </ul>
 *
 * <p>La lista es cerrada: cubre las formas que se acordaron, no todas las que existen.
 */
public final class ReglasQueBuscarEnElCv {

    /** Cuántas líneas se buscan como mucho. */
    public static final int MAXIMO_LINEAS = 6;

    /** Cuántos caracteres admite una línea. */
    public static final int MAXIMO_CARACTERES = 200;

    /** Lo que se contesta a una línea que pide algo que no sirve para elegir a nadie. */
    public static final String MOTIVO_PROHIBIDA = "No se puede buscar: la edad, el sexo, el estado "
            + "civil, los hijos, la salud, la religión o el origen no sirven para elegir a nadie.";

    /**
     * Palabras que, solas, ya piden algo prohibido. Se comparan sin tildes ni mayúsculas y como
     * palabra entera: «saludable» no es «salud», y «casado» no está dentro de «encasados».
     */
    private static final Set<String> PALABRAS_PROHIBIDAS = Set.of(
            // edad
            "edad", "joven", "jovenes", "juvenil", "anciano", "anciana", "ancianos", "ancianas",
            "nacido", "nacida", "nacimiento", "jovencito", "jovencita", "jovencitos", "jovencitas",
            "adolescente", "adolescentes",
            // sexo o género («chica» y «chico» van aparte: ver CHICO_O_CHICA)
            "sexo", "genero", "hombre", "hombres", "mujer", "mujeres", "varon", "varones",
            "masculino", "masculina", "masculinos", "masculinas", "femenino", "femenina",
            "femeninos", "femeninas", "femina", "feminas", "dama", "damas", "caballero",
            "caballeros", "senorita", "senoritas", "srta", "senora", "senoras", "sra", "senor",
            "senores", "muchacho", "muchacha", "muchachos", "muchachas",
            // estado civil
            "casado", "casada", "casados", "casadas", "soltero", "soltera", "solteros", "solteras",
            "divorciado", "divorciada", "viudo", "viuda", "conviviente",
            // hijos o embarazo
            "hijo", "hijos", "hija", "hijas", "embarazo", "embarazada", "embarazadas", "gestante",
            "gestantes", "gestando", "maternidad", "paternidad",
            // salud o discapacidad («salud» va aparte: ver SALUD)
            "discapacidad", "discapacitado", "discapacitada", "discapacitados", "discapacitadas",
            "minusvalido", "minusvalida", "minusvalidos", "minusvalidas", "enfermedad",
            "enfermedades", "enfermo", "enferma", "sano", "sana", "sanos", "sanas",
            // religión
            "religion", "religiosa", "religioso", "catolico", "catolica", "cristiano", "cristiana",
            "evangelico", "evangelica", "creyente", "ateo", "atea", "adventista", "adventistas",
            // raza o etnia
            "raza", "racial", "etnia", "etnico", "etnica", "tez", "mestizo", "mestiza",
            "indigena", "afroperuano", "afroperuana", "afrodescendiente", "afrodescendientes",
            // nacionalidad u origen
            "nacionalidad", "peruano", "peruana", "peruanos", "peruanas", "extranjero",
            "extranjera", "extranjeros", "extranjeras", "venezolano", "venezolana", "venezolanos",
            "venezolanas", "colombiano", "colombiana", "colombianos", "colombianas", "boliviano",
            "boliviana", "bolivianos", "bolivianas", "ecuatoriano", "ecuatoriana", "ecuatorianos",
            "ecuatorianas", "procedencia", "provinciano", "provinciana", "oriundo", "oriunda",
            "oriundos", "oriundas");

    /** Frases de dos o más palabras que también lo piden. */
    private static final List<String> FRASES_PROHIBIDAS = List.of(
            "estado civil", "carga familiar", "cargas familiares", "madre de familia",
            "padre de familia", "color de piel", "piel blanca", "piel clara", "piel morena",
            "piel oscura", "piel negra", "piel triguena", "natural de");

    /** La salud se nombra a secas, pero también en lo que sí se puede pedir. */
    private static final String SALUD = "salud";

    /**
     * Con alguna de estas al lado, «salud» habla de un documento que pide la ley o de un campo de
     * trabajo, no de la salud de la persona: «Carné de salud vigente», «Experiencia en el sector
     * salud», «Seguridad y salud en el trabajo».
     */
    private static final Set<String> SALUD_PERMITIDA_CON = Set.of(
            "carne", "carnet", "certificado", "certificacion", "constancia", "libreta",
            "tarjeta", "experiencia", "sector", "ocupacional", "seguridad", "publica",
            "conocimiento", "conocimientos", "curso", "cursos", "gestion", "programa",
            "programas", "area", "establecimiento", "establecimientos");

    /** El origen de la persona; ver {@link #ORIGEN_PERMITIDO_CON}. */
    private static final String ORIGEN = "origen";

    /**
     * Con alguna de estas al lado, «origen» habla de lo que se maneja en el puesto y no de la
     * persona: «Manejo de productos de origen animal».
     */
    private static final Set<String> ORIGEN_PERMITIDO_CON = Set.of(
            "animal", "vegetal", "producto", "productos", "alimento", "alimentos", "insumo",
            "insumos", "materia", "materias", "datos", "fuente", "fuentes");

    /**
     * Con alguna de estas al lado, el VIH es de quienes se atiende y no de la persona: «Experiencia
     * en la atención de pacientes con VIH». «Que no tenga VIH» o «Certificado de VIH», no.
     */
    private static final Set<String> VIH_PERMITIDO_CON = Set.of(
            "paciente", "pacientes", "prevencion", "consejeria");

    /** Palabras prohibidas salvo que alguna de su lista diga que no hablan de la persona. */
    private static final Map<String, Set<String>> PROHIBIDAS_SALVO_CON = Map.of(
            SALUD, SALUD_PERMITIDA_CON,
            ORIGEN, ORIGEN_PERMITIDO_CON,
            "vih", VIH_PERMITIDO_CON,
            "sida", VIH_PERMITIDO_CON);

    /** Nombrar a la persona así es pedir su sexo: «Chica para atención al cliente». */
    private static final Set<String> CHICO_O_CHICA = Set.of("chico", "chica", "chicos", "chicas");

    /**
     * Detrás de estas, «chica» o «chico» es un tamaño y no una persona: «Manejo de caja chica»,
     * «Uniforme talla chica».
     */
    private static final Set<String> CHICO_COMO_TAMANO_TRAS = Set.of(
            "caja", "cajas", "talla", "tallas", "tamano", "formato", "letra", "porcion", "porciones");

    /** La palabra que convierte un número en tiempo. */
    private static final Set<String> ANOS = Set.of("anos", "ano");

    /**
     * Ninguna edad para trabajar baja de 14, así que «2 años» es un tiempo y no una edad. Por
     * encima de 99 tampoco es una edad: «más de 500 soles».
     */
    private static final int EDAD_MINIMA_POSIBLE = 14;
    private static final int EDAD_MAXIMA_POSIBLE = 99;

    /** Los números escritos con letras que pueden ser una edad; «dieci…» y «veinti…» aparte. */
    private static final Set<String> EDADES_EN_LETRAS = Set.of(
            "catorce", "quince", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta",
            "ochenta", "noventa");

    /** Los que no llegan a una edad, para leer «treinta y cinco» entero. */
    private static final Set<String> NUMEROS_PEQUENOS_EN_LETRAS = Set.of(
            "un", "uno", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece");

    /** Lo que va entre los números de un rango y «años»: «25 a 35 años», «18 o más años». */
    private static final Set<String> DENTRO_DEL_RANGO = Set.of(
            "a", "y", "e", "o", "al", "mas", "menos", "hasta");

    /** «15 años de experiencia», «3 años de estudios»: detrás de «años de», los años son de algo. */
    private static final Set<String> ANOS_DE = Set.of(
            "experiencia", "trayectoria", "antiguedad", "servicio", "servicios", "trabajo", "labor",
            "labores", "estudio", "estudios", "carrera", "ejercicio", "practica", "practicas",
            "oficio", "permanencia", "formacion");

    /**
     * «Experiencia general no menor de 15 años», «haber trabajado 15 años», «durante 15 años»:
     * antes del número, en la misma parte, dicen que los años son de algo y no la edad de nadie.
     */
    private static final Set<String> TIEMPO_ANTES = Set.of(
            "experiencia", "trayectoria", "antiguedad", "permanencia", "trabajado", "laborado",
            "ejercido", "desempenado", "dedicado", "dedicada", "hace", "durante");

    /**
     * «Experiencia en cocina, al menos 15 años»: tras hablar de experiencia, un mínimo de años es
     * un tiempo aunque vaya en otra parte de la línea.
     */
    private static final List<String> MINIMOS = List.of(
            " al menos", " lo menos", " no menor de", " no menos de");

    /** «35 años como máximo» es un tope de edad; «2 años como cajero», un tiempo en algo. */
    private static final Set<String> TOPES = Set.of("maximo", "minimo", "tope", "limite");

    /** Acaban en «-ando» o «-iendo» y no dicen en qué se pasaron los años. */
    private static final Set<String> NO_SON_GERUNDIO = Set.of("cuando", "siendo", "teniendo");

    /** «menor de 35», «mayor a 40»: la edad dicha con un número, aunque falte «años». */
    private static final Set<String> COMPARAN_EDAD = Set.of("menor", "menores", "mayor", "mayores");
    private static final Set<String> ANTES_DEL_NUMERO = Set.of("de", "a", "al");

    /**
     * Lo que puede seguir a una edad sin «años»: «mayor de 18 y con secundaria». Cualquier otra
     * palabra detrás del número dice qué se cuenta: «mayores a 50 soles», «los 30 pedidos».
     */
    private static final Set<String> SIGUEN_A_UNA_EDAD = Set.of(
            "y", "e", "o", "u", "a", "con", "sin", "que", "para", "de", "preferentemente",
            "preferible", "cumplidos", "inclusive");

    /** Separan una parte de la línea de otra: «Experiencia en caja, menor de 35 años». */
    private static final String SEPARAN_PARTES = ",;:.()[]{}!?¿¡|·";

    /** También separan partes, salvo entre dos números: «Experiencia y menor de 35 años». */
    private static final Set<String> Y = Set.of("y", "e");

    private ReglasQueBuscarEnElCv() {
    }

    /**
     * Las líneas que se guardan: sin vacías, sin repetidas y como mucho seis, en su orden.
     *
     * @throws IllegalArgumentException si alguna línea —también una de las que pasan de seis—
     *                                  pide algo prohibido o pasa de 200 caracteres. No se guarda
     *                                  nada.
     */
    public static List<String> lasQueSeGuardan(List<String> recibidas) {
        if (recibidas == null) {
            return List.of();
        }
        List<String> validas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (String recibida : recibidas) {
            String linea = recibida == null ? "" : recibida.strip();
            if (linea.isEmpty()) {
                continue;
            }
            if (linea.length() > MAXIMO_CARACTERES) {
                throw new IllegalArgumentException("«" + recorte(linea) + "» pasa de "
                        + MAXIMO_CARACTERES + " caracteres: cada línea de «Qué buscar en el CV» "
                        + "admite hasta " + MAXIMO_CARACTERES + ".");
            }
            if (esProhibida(linea)) {
                throw new IllegalArgumentException("«" + linea + "». " + MOTIVO_PROHIBIDA);
            }
            if (vistas.add(claveDeRepeticion(linea))) {
                validas.add(linea);
            }
        }
        return validas.size() <= MAXIMO_LINEAS ? List.copyOf(validas)
                : List.copyOf(validas.subList(0, MAXIMO_LINEAS));
    }

    /** Si la línea pide algo que no sirve para elegir a nadie. */
    public static boolean esProhibida(String linea) {
        String normal = sinTildesNiMayusculas(linea);
        List<String> palabras = palabrasDe(normal);
        for (String palabra : palabras) {
            if (PALABRAS_PROHIBIDAS.contains(palabra)) {
                return true;
            }
        }
        String conEspacios = " " + String.join(" ", palabras) + " ";
        for (String frase : FRASES_PROHIBIDAS) {
            if (conEspacios.contains(" " + frase + " ")) {
                return true;
            }
        }
        for (Map.Entry<String, Set<String>> salvo : PROHIBIDAS_SALVO_CON.entrySet()) {
            if (palabras.contains(salvo.getKey()) && palabras.stream().noneMatch(salvo.getValue()::contains)) {
                return true;
            }
        }
        return nombraChicoOChica(palabras) || pideUnaEdad(normal);
    }

    /**
     * La edad con números, parte por parte: «Experiencia en caja, menor de 35 años», «Experiencia
     * y menor de 35 años». Se recuerda si una parte anterior habló de experiencia.
     */
    private static boolean pideUnaEdad(String normal) {
        boolean yaHabloDeTiempo = false;
        for (List<String> parte : partesDe(normal)) {
            if (pideUnaEdadConAnos(parte, yaHabloDeTiempo) || pideUnaEdadSinAnos(parte, yaHabloDeTiempo)) {
                return true;
            }
            yaHabloDeTiempo = yaHabloDeTiempo || parte.stream().anyMatch(TIEMPO_ANTES::contains);
        }
        return false;
    }

    /** «Chica para atención al cliente» sí; «Manejo de caja chica», no. */
    private static boolean nombraChicoOChica(List<String> palabras) {
        for (int i = 0; i < palabras.size(); i++) {
            if (CHICO_O_CHICA.contains(palabras.get(i))
                    && (i == 0 || !CHICO_COMO_TAMANO_TRAS.contains(palabras.get(i - 1)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * «Personas de 25 a 35 años», «Máximo 30 años», «18 años a más», «treinta y cinco años»: un
     * número que puede ser una edad seguido de «años», que no son años de algo.
     */
    private static boolean pideUnaEdadConAnos(List<String> palabras, boolean yaHabloDeTiempo) {
        for (int k = 0; k < palabras.size(); k++) {
            int inicio = ANOS.contains(palabras.get(k)) ? dondeEmpiezaLaEdad(palabras, k) : -1;
            if (inicio >= 0 && !sonAnosDeAlgo(palabras, k)
                    && !esUnTiempo(palabras, inicio, yaHabloDeTiempo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dónde empieza la cantidad que va antes de «años» —«25 a 35», «treinta y cinco»— si puede
     * ser una edad, o -1.
     */
    private static int dondeEmpiezaLaEdad(List<String> palabras, int anos) {
        int inicio = -1;
        boolean puedeSerEdad = false;
        for (int j = anos - 1; j >= 0 && j >= anos - 3; j--) {
            String palabra = palabras.get(j);
            if (esCantidad(palabra)) {
                inicio = j;
                puedeSerEdad = puedeSerEdad || puedeSerUnaEdad(palabra);
            } else if (!DENTRO_DEL_RANGO.contains(palabra)) {
                break;
            }
        }
        return puedeSerEdad ? inicio : -1;
    }

    /** «Menor de 35», «Mayor a 40», «Hasta los 35»: la edad sin decir «años». */
    private static boolean pideUnaEdadSinAnos(List<String> palabras, boolean yaHabloDeTiempo) {
        for (int i = 0; i + 1 < palabras.size(); i++) {
            int numero;
            if (COMPARAN_EDAD.contains(palabras.get(i)) && i + 2 < palabras.size()
                    && ANTES_DEL_NUMERO.contains(palabras.get(i + 1))) {
                numero = i + 2;
            } else if ("los".equals(palabras.get(i))) {
                numero = i + 1;
            } else {
                continue;
            }
            if (!puedeSerUnaEdad(palabras.get(numero))) {
                continue;
            }
            boolean nadaDetras = numero + 1 == palabras.size();
            if ((nadaDetras || SIGUEN_A_UNA_EDAD.contains(palabras.get(numero + 1)))
                    && !esUnTiempo(palabras, numero, yaHabloDeTiempo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Si lo que va detrás de «años» dice de qué son: «de experiencia», «en cocina», «como
     * cajero», «trabajando».
     */
    private static boolean sonAnosDeAlgo(List<String> palabras, int anos) {
        if (anos + 1 >= palabras.size()) {
            return false;
        }
        String detras = palabras.get(anos + 1);
        String luego = anos + 2 < palabras.size() ? palabras.get(anos + 2) : "";
        return ("de".equals(detras) && ANOS_DE.contains(luego))
                || ("en".equals(detras) && !"adelante".equals(luego))
                || ("como".equals(detras) && !TOPES.contains(luego))
                || esGerundio(detras);
    }

    /**
     * Si el número que empieza en {@code numero} es un tiempo y no una edad: antes, en la misma
     * parte, se habló de experiencia —«Experiencia mayor a 15 años»— o, ya dicho en otra parte,
     * va tras un mínimo: «Experiencia en cocina, al menos 15 años».
     */
    private static boolean esUnTiempo(List<String> palabras, int numero, boolean yaHabloDeTiempo) {
        return hayTiempoAntes(palabras, numero) || (yaHabloDeTiempo && vaTrasUnMinimo(palabras, numero));
    }

    /**
     * «Experiencia general no menor de 15 años» sí; «Experiencia de 2 años, máximo 35 años», no:
     * si antes ya se dijeron unos años, esos eran el tiempo de la experiencia.
     */
    private static boolean hayTiempoAntes(List<String> palabras, int numero) {
        for (int j = numero - 1; j >= 0; j--) {
            String palabra = palabras.get(j);
            if (ANOS.contains(palabra)) {
                return false;
            }
            if (TIEMPO_ANTES.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    private static boolean vaTrasUnMinimo(List<String> palabras, int numero) {
        String antes = " " + String.join(" ", palabras.subList(Math.max(0, numero - 3), numero));
        return MINIMOS.stream().anyMatch(antes::endsWith);
    }

    private static boolean esGerundio(String palabra) {
        return palabra.length() >= 6 && (palabra.endsWith("ando") || palabra.endsWith("iendo"))
                && !NO_SON_GERUNDIO.contains(palabra);
    }

    private static boolean esCantidad(String palabra) {
        return esNumero(palabra) || NUMEROS_PEQUENOS_EN_LETRAS.contains(palabra)
                || puedeSerUnaEdad(palabra);
    }

    private static boolean puedeSerUnaEdad(String palabra) {
        if (esNumero(palabra)) {
            if (palabra.length() > 3) {
                return false;
            }
            int valor = Integer.parseInt(palabra);
            return valor >= EDAD_MINIMA_POSIBLE && valor <= EDAD_MAXIMA_POSIBLE;
        }
        return EDADES_EN_LETRAS.contains(palabra)
                || (palabra.length() > 5 && (palabra.startsWith("dieci") || palabra.startsWith("veinti")));
    }

    /** La forma con la que se comparan dos líneas: sin mayúsculas ni espacios de más. */
    public static String claveDeRepeticion(String linea) {
        return String.join(" ", linea.toLowerCase(Locale.ROOT).trim().split("\\s++"));
    }

    private static List<String> palabrasDe(String normal) {
        List<String> palabras = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < normal.length(); i++) {
            char c = normal.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                actual.append(c);
            } else if (!actual.isEmpty()) {
                palabras.add(actual.toString());
                actual.setLength(0);
            }
        }
        if (!actual.isEmpty()) {
            palabras.add(actual.toString());
        }
        return palabras;
    }

    /**
     * Las palabras de cada parte de la línea, separadas por comas, puntos, paréntesis… y por «y»
     * o «e» salvo entre dos números: «entre 25 y 35», «treinta y cinco», «18 y más».
     */
    private static List<List<String>> partesDe(String normal) {
        List<List<String>> partes = new ArrayList<>();
        int desde = 0;
        for (int i = 0; i <= normal.length(); i++) {
            if (i == normal.length() || SEPARAN_PARTES.indexOf(normal.charAt(i)) >= 0) {
                partirPorLaY(palabrasDe(normal.substring(desde, i)), partes);
                desde = i + 1;
            }
        }
        return partes;
    }

    private static void partirPorLaY(List<String> palabras, List<List<String>> partes) {
        int desde = 0;
        for (int i = 0; i < palabras.size(); i++) {
            if (Y.contains(palabras.get(i)) && !uneDosNumeros(palabras, i)) {
                partes.add(palabras.subList(desde, i));
                desde = i + 1;
            }
        }
        partes.add(palabras.subList(desde, palabras.size()));
    }

    private static boolean uneDosNumeros(List<String> palabras, int y) {
        if (y == 0 || y + 1 >= palabras.size()) {
            return false;
        }
        String siguiente = palabras.get(y + 1);
        return esCantidad(palabras.get(y - 1)) && (esCantidad(siguiente) || "mas".equals(siguiente));
    }

    private static boolean esNumero(String palabra) {
        return !palabra.isEmpty() && palabra.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static String sinTildesNiMayusculas(String texto) {
        // La ñ se queda en n a propósito: «años» y «anos» se comparan igual, y «señorita» con su
        // forma escrita sin tilde. Ninguna palabra prohibida distingue n de ñ.
        String sinMarcas = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinMarcas.toLowerCase(Locale.ROOT);
    }

    private static String recorte(String linea) {
        return linea.length() <= 40 ? linea : linea.substring(0, 40) + "…";
    }
}
