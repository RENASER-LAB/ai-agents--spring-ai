package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Set;

/**
 * Lo que una vacante dice sobre el dinero, con sus reglas en un solo sitio.
 *
 * <p>Existe porque las mismas tres preguntas se hacen desde cuatro sitios distintos —crear
 * la vacante, editarla, publicar el tablón y recibir una postulación— y la respuesta tiene
 * que ser la misma en los cuatro:
 *
 * <ol>
 *   <li><b>¿Es válida?</b> Una vacante FIJA sin monto o un RANGO al revés no son estados
 *       que se puedan guardar. La base también lo impide (V55), pero un 500 de constraint
 *       no le dice a nadie qué escribió mal.
 *   <li><b>¿Obliga al candidato?</b> El trato de la V55: enseñar el sueldo obliga a quien
 *       postula a decir el suyo; esconderlo lo libera.
 *   <li><b>¿Cómo se escribe?</b> El aviso del cambio de sueldo dice «antes» y «ahora», y
 *       esa frase la arma este archivo — no la pantalla ni el texto de cada empresa, que
 *       tendrían que repetir el formato de moneda y acabarían diciéndolo distinto.
 * </ol>
 *
 * <p>Es una clase de utilidad sin estado a propósito: no necesita transacción, ni repositorio,
 * ni contexto de usuario. Solo conoce los números.
 */
public final class Remuneracion {

    private Remuneracion() {}

    /** No se publica sueldo, y por tanto tampoco se le exige al candidato. */
    public static final String OCULTA = "OCULTA";
    /** Un monto exacto, guardado en {@code remuneracionMin}. */
    public static final String FIJA = "FIJA";
    /** Una banda de {@code remuneracionMin} a {@code remuneracionMax}. */
    public static final String RANGO = "RANGO";

    private static final Set<String> TIPOS = Set.of(OCULTA, FIJA, RANGO);

    /**
     * Las monedas que se admiten.
     *
     * <p>Corta y deliberadamente cerrada: el portal es peruano y la empresa que contrata en
     * dólares lo hace por excepción. Una lista abierta deja entrar «soles», «S/.», «PEN » y
     * cuatro grafías más del mismo dinero, y entonces comparar dos vacantes deja de ser
     * posible sin un diccionario de sinónimos.
     */
    private static final Set<String> MONEDAS = Set.of("PEN", "USD");

    /**
     * El techo. Un sueldo mensual por encima de esto es casi seguro un dedo de más —alguien
     * que escribió 35000 donde quería 3500—, y aceptarlo en silencio publica una vacante que
     * promete diez veces lo que paga.
     */
    private static final BigDecimal MAXIMO = new BigDecimal("1000000");

    /**
     * Y el suelo, que hasta ahora no existía: bastaba con ser mayor que cero.
     *
     * <p>Un sueldo mensual de S/ 3.50 no es una oferta baja, es un error. Llegaba de dos
     * sitios: de un formulario que leía «3,500» como tres soles con cincuenta —ver
     * {@code dominio/dinero} en el portal—, y de cualquier cliente de la API que mandara la
     * cifra sin pasar por una pantalla. El suelo lo para en los dos casos.
     *
     * <p>Cien y no el sueldo mínimo legal: la cifra también viaja en dólares, y un umbral
     * pegado a la ley peruana rechazaría una práctica pagada en USD que es perfectamente
     * real. Lo que se para aquí es el error de magnitud, no la oferta modesta.
     */
    private static final BigDecimal MINIMO = new BigDecimal("100");

    /**
     * ¿Esta vacante enseña lo que paga?
     *
     * <p>Y por tanto —el trato de la V55— ¿le exige a quien postula que diga lo suyo?
     */
    public static boolean laEnsena(Vacante vacante) {
        return vacante != null && !OCULTA.equals(tipoDe(vacante));
    }

    /** El tipo de una vacante, tolerando el nulo de una fila anterior a la V55. */
    public static String tipoDe(Vacante vacante) {
        String tipo = vacante.getRemuneracionTipo();
        return tipo == null || tipo.isBlank() ? OCULTA : tipo;
    }

    /**
     * Comprueba que los cuatro campos cuenten una historia coherente, y devuelve la moneda
     * ya normalizada (o {@code null} si es OCULTA).
     *
     * @throws IllegalArgumentException con un mensaje que dice qué falta, en castellano y
     *         dirigido a quien está rellenando el formulario del panel.
     */
    public static String validar(String tipo, BigDecimal min, BigDecimal max, String moneda) {
        if (tipo == null || !TIPOS.contains(tipo)) {
            throw new IllegalArgumentException(
                    "La remuneración tiene que ser OCULTA, FIJA o RANGO; llegó «" + tipo + "»");
        }
        if (OCULTA.equals(tipo)) {
            // Se aceptan montos sueltos y se ignoran: quien apaga la remuneración desde una
            // pantalla que tenía las cifras escritas no debería recibir un error por unos
            // números que ya no significan nada. Lo que se guarda son nulos — ver `limpia`.
            return null;
        }

        String monedaNormal = moneda == null ? "" : moneda.trim().toUpperCase(Locale.ROOT);
        if (!MONEDAS.contains(monedaNormal)) {
            throw new IllegalArgumentException(
                    "La moneda tiene que ser PEN o USD; llegó «" + moneda + "»");
        }
        exigirMonto(min, FIJA.equals(tipo) ? "el monto" : "el mínimo");

        if (FIJA.equals(tipo)) {
            if (max != null) {
                throw new IllegalArgumentException(
                        "Un sueldo fijo lleva un solo monto: quita el máximo o cámbialo a rango");
            }
            return monedaNormal;
        }

        exigirMonto(max, "el máximo");
        if (max.compareTo(min) < 0) {
            throw new IllegalArgumentException(
                    "El máximo del rango no puede ser menor que el mínimo");
        }
        return monedaNormal;
    }

    private static void exigirMonto(BigDecimal monto, String cual) {
        if (monto == null) {
            throw new IllegalArgumentException("Falta " + cual + " de la remuneración");
        }
        if (monto.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Los montos de la remuneración tienen que ser mayores que cero");
        }
        exigirEntero(monto);
        if (monto.compareTo(MINIMO) < 0) {
            throw new IllegalArgumentException(
                    "Ese monto es demasiado bajo para ser un sueldo mensual: el mínimo "
                            + "admitido es " + escribirMonto(MINIMO)
                            + ". Escríbelo en cifras enteras, sin céntimos");
        }
        if (monto.compareTo(MAXIMO) > 0) {
            throw new IllegalArgumentException(
                    "Ese monto parece un error de tecleo: el máximo admitido es "
                            + escribirMonto(MAXIMO));
        }
    }

    /**
     * Los sueldos van en cifras enteras, y eso es una regla y no una comodidad.
     *
     * <p>Nadie negocia los céntimos de un sueldo mensual. Admitirlos es admitir la
     * ambigüedad entera: con decimales sobre la mesa no hay forma de saber si un 3.50 que
     * llega por la API son tres soles y medio o un 3500 que alguien escribió como «3,50»
     * en un formulario que lo leyó mal. Sin decimales, esa duda no existe.
     */
    private static void exigirEntero(BigDecimal monto) {
        if (monto.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Los sueldos se escriben en cifras enteras, sin céntimos: llegó "
                            + monto.toPlainString() + ". Por ejemplo: 3500");
        }
    }

    /**
     * Valida lo que declara el candidato: un monto y su moneda.
     *
     * <p>Separada de {@link #validar} porque el candidato declara un número suelto, no una
     * de las tres formas de la vacante — pero comparten el suelo, el techo y las monedas, que
     * es justo lo que no debe divergir entre los dos lados del trato.
     */
    public static String validarPretension(BigDecimal monto, String moneda) {
        String monedaNormal = moneda == null ? "" : moneda.trim().toUpperCase(Locale.ROOT);
        if (!MONEDAS.contains(monedaNormal)) {
            throw new IllegalArgumentException(
                    "La moneda de tu pretensión tiene que ser PEN o USD");
        }
        if (monto == null) {
            throw new IllegalArgumentException("Dinos cuánto quieres ganar");
        }
        if (monto.signum() <= 0) {
            throw new IllegalArgumentException("Tu pretensión tiene que ser mayor que cero");
        }
        // Las mismas reglas que la vacante, y no por simetría estética: si el candidato
        // pudiera declarar céntimos y la empresa no, comparar las dos cifras —que es todo lo
        // que este trato existe para permitir— dejaría de ser una resta limpia.
        //
        // Los mensajes SÍ son distintos: este lo lee alguien que está postulando, no quien
        // configura una vacante, y «los montos de la remuneración» no es su idioma.
        exigirEntero(monto);
        if (monto.compareTo(MINIMO) < 0) {
            throw new IllegalArgumentException(
                    "Esa cifra es demasiado baja para un sueldo mensual. Escríbela en cifras "
                            + "enteras, sin céntimos: por ejemplo 3500");
        }
        if (monto.compareTo(MAXIMO) > 0) {
            throw new IllegalArgumentException(
                    "Esa cifra parece un error de tecleo: revísala antes de enviar");
        }
        return monedaNormal;
    }

    /**
     * Escribe la remuneración de una vacante en una frase, para el aviso y la auditoría.
     *
     * <p>Una OCULTA se escribe «sin publicar» y no «—»: el aviso de que el sueldo cambió
     * puede tener a la OCULTA en cualquiera de los dos lados, y un guion ahí deja al
     * candidato preguntándose si es que no le dijeron o es que no hay.
     */
    public static String escribir(String tipo, BigDecimal min, BigDecimal max, String moneda) {
        if (tipo == null || OCULTA.equals(tipo)) {
            return "sin publicar";
        }
        String simbolo = simboloDe(moneda);
        if (FIJA.equals(tipo)) {
            return simbolo + " " + escribirMonto(min);
        }
        return simbolo + " " + escribirMonto(min) + " a " + escribirMonto(max);
    }

    /** Lo mismo, leyendo directamente de la vacante. */
    public static String escribir(Vacante vacante) {
        return escribir(tipoDe(vacante), vacante.getRemuneracionMin(),
                vacante.getRemuneracionMax(), vacante.getRemuneracionMoneda());
    }

    /** Un monto suelto con su moneda: lo que declaró el candidato. */
    public static String escribirPretension(BigDecimal monto, String moneda) {
        if (monto == null) {
            return "sin declarar";
        }
        return simboloDe(moneda) + " " + escribirMonto(monto);
    }

    private static String simboloDe(String moneda) {
        return "USD".equals(moneda) ? "US$" : "S/";
    }

    /**
     * El número con separador de miles y sin decimales cuando son cero.
     *
     * <p>«S/ 3 500» y no «S/ 3500.00»: un sueldo se lee de un vistazo y los céntimos de un
     * sueldo mensual son ruido. Los decimales aparecen solo si los hay de verdad.
     *
     * <p>El formateador se crea en cada llamada y eso no es un descuido: {@link DecimalFormat}
     * no es seguro entre hilos, y guardarlo en un campo estático de una clase que llaman a la
     * vez el aviso de una vacante y el tablón de otra es una carrera silenciosa que produce
     * cifras mal escritas una vez cada mil.
     */
    private static String escribirMonto(BigDecimal monto) {
        BigDecimal limpio = monto.stripTrailingZeros();
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.ROOT);
        simbolos.setGroupingSeparator(' ');
        simbolos.setDecimalSeparator('.');
        DecimalFormat formato = new DecimalFormat(
                limpio.scale() > 0 ? "#,##0.00" : "#,##0", simbolos);
        return formato.format(limpio);
    }

    /**
     * El punto medio de la banda del perfil, que es con lo que se prellena el formulario de
     * postular.
     *
     * <p>Quien puso «3000 a 4000» en su perfil no está diciendo que quiera 3000: está
     * diciendo que su expectativa vive en esa banda, y el centro es la lectura más honesta de
     * eso. Prellenar con el mínimo le regalaría a la empresa el borde bajo de su propia
     * expectativa cada vez que alguien pulsa enviar sin mirar.
     *
     * <p>Devuelve {@code null} si no tiene nada guardado: entonces el campo sale vacío y lo
     * escribe él, que es el caso de casi todo el mundo.
     */
    public static BigDecimal sugerirDesdeElPerfil(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        if (min == null) {
            return max;
        }
        if (max == null) {
            return min;
        }
        return min.add(max).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
    }
}
