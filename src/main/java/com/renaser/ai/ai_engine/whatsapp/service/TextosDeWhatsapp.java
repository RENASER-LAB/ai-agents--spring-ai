package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Lo que EX le da escrito al bot y al portal (V73): el mensaje ya preparado del enlace, el
 * texto de cada aviso «te toca» y las respuestas fijas de cada rechazo.
 *
 * <p>Lo del proceso en sí —en qué va— no se escribe aquí: sale de {@link TextosDelMomento},
 * que es lo mismo que pinta el portal.
 */
public final class TextosDeWhatsapp {

    /** Lo que mide como mucho el título de la vacante: en el mensaje ya escrito y en el contrato. */
    public static final int LARGO_DEL_TITULO = 60;

    // Los límites del contrato con el bot (specs/novedades-por-whatsapp.md, «Reglas comunes»).
    // Una respuesta de EX que los pasa, el bot la trata como error de protocolo.

    /** {@code nombre}: el nombre de pila. */
    public static final int LARGO_DEL_NOMBRE = 60;
    /** {@code vacante}: el título de la vacante. */
    public static final int LARGO_DE_LA_VACANTE = LARGO_DEL_TITULO;
    /** {@code novedades[].titulo}: el nombre corto del momento. */
    public static final int LARGO_DEL_MOMENTO = 80;
    /** {@code novedades[].texto}: lo que dice el portal de ese momento. */
    public static final int LARGO_DE_LA_NOVEDAD = 300;
    /** El {@code texto} de un aviso o de un error. */
    public static final int LARGO_DEL_TEXTO = 1000;
    /** Novedades por respuesta, como mucho. */
    public static final int MAXIMO_DE_NOVEDADES = 8;

    /** El 400 de una llamada del bot que no cumple el contrato. */
    public static final String PETICION_QUE_NO_VALE = "La petición no cumple el contrato: hace "
            + "falta un JSON con el teléfono en formato internacional (E.164), como +51987654321";

    public static final String SIN_ANUNCIO = "No encontramos una postulación esperando tu "
            + "mensaje desde este número. Si quieres recibir tus novedades por aquí, entra en el "
            + "portal, abre «Mis procesos» y pulsa «Escribir por WhatsApp».";

    /** Punto 13 de la spec: lo que contesta el bot a quien ya no tiene procesos con avisos. */
    public static final String SIN_PROCESOS_ACTIVOS = "No tienes procesos activos con avisos por "
            + "WhatsApp. Puedes ver todo en el portal, en «Mis procesos».";

    public static final String BETA_APAGADA = "Por ahora no enviamos las novedades de los "
            + "procesos por WhatsApp. Puedes ver cómo va el tuyo en el portal, en «Mis procesos».";

    /** La zona en que se dicen las fechas: la de quien postula en el Perú. */
    public static final ZoneId ZONA = ZoneId.of("America/Lima");

    /** El largo máximo del enlace fijo a «Mis procesos» (contrato v2, C-3). */
    public static final int LARGO_DEL_ENLACE = 300;

    /** Cómo se entra: con la cuenta de siempre. Ni el enlace ni el chat dan acceso por sí solos. */
    public static final String COMO_ENTRAR =
            "Inicia sesión con tu correo y contraseña y abre «Mis procesos».";

    private static final Locale ESPANOL = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter DIA_Y_HORA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'a las' HH:mm", ESPANOL);
    private static final DateTimeFormatter DIA_ANO_Y_HORA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy 'a las' HH:mm", ESPANOL);

    private TextosDeWhatsapp() {}

    /**
     * El enlace {@code wa.me} al chat del bot con el mensaje ya escrito.
     *
     * <p>El mensaje es una cortesía: el candidato puede cambiarlo o borrarlo entero, porque al
     * bot solo le importa desde qué número escribe.
     */
    public static String enlace(String numeroBot, String nombre, String apellidos, String vacante) {
        return "https://wa.me/" + numeroBot + "?text=" + codificar(mensaje(nombre, apellidos, vacante));
    }

    /** «Hola, soy Ana Pérez. Quiero saber cómo va mi postulación a Asistente Contable.» */
    public static String mensaje(String nombre, String apellidos, String vacante) {
        String quien = (vacio(nombre) ? "" : nombre.trim())
                + (vacio(apellidos) ? "" : " " + apellidos.trim());
        String saludo = quien.isBlank() ? "Hola." : "Hola, soy " + quien.trim() + ".";
        String titulo = recortar(vacante);
        return titulo.isEmpty()
                ? saludo + " Quiero saber cómo va mi postulación."
                : saludo + " Quiero saber cómo va mi postulación a " + titulo + ".";
    }

    /**
     * El aviso de la prueba (regla 19 de specs/Ready/contexto-y-resultados-por-whatsapp.md): el
     * saludo, la vacante en negrita, qué prueba es, hasta cuándo puede empezarla —solo si EX lo
     * sabe— y el enlace fijo a «Mis procesos». Nada de la prueba en sí: eso llega al pulsar
     * «Empezar» en el portal.
     *
     * <pre>
     * ¡Felicidades, Ana! 🎉
     *
     * 📌 *Asistente Contable*
     * Tu *prueba del puesto* ya está disponible.
     * ⏰ Puedes empezarla hasta el *20 de octubre a las 23:59* (hora de Lima).
     *
     * 👉 https://…/procesos
     * Inicia sesión con tu correo y contraseña y abre «Mis procesos».
     * </pre>
     *
     * @param empezarAntesDe el último momento para empezarla, o nulo si no hay uno: entonces la
     *                       línea del plazo no sale
     * @param enlace         el enlace fijo a «Mis procesos»; si no vale, la línea no sale
     */
    public static String pruebaDisponible(String nombre, String vacante, boolean esCuestionario,
                                          Instant empezarAntesDe, String enlace) {
        StringBuilder texto = new StringBuilder(vacio(nombre) ? "¡Felicidades! 🎉"
                : "¡Felicidades, " + sinNegritas(nombre.trim()) + "! 🎉");
        texto.append("\n\n");
        if (!vacio(vacante)) {
            texto.append("📌 *").append(sinNegritas(vacante.trim())).append("*\n");
        }
        texto.append(esCuestionario ? "Tu *prueba técnica*" : "Tu *prueba del puesto*")
                .append(" ya está disponible.");
        if (empezarAntesDe != null) {
            texto.append("\n⏰ Puedes empezarla hasta el *").append(fechaYHora(empezarAntesDe, Instant.now()))
                    .append("* (hora de Lima).");
        }
        return cortar(texto + alFinal(enlace), LARGO_DEL_TEXTO);
    }

    /**
     * El aviso de un avance en que le toca algo (regla 19): el saludo, la vacante en negrita, lo
     * que dice el portal de ese momento ({@link TextosDelMomento}, sin cambiarlo) y el enlace fijo.
     *
     * <pre>
     * Hola, Ana 👋
     *
     * 📌 *Contador*
     * Te toca: *Confirma tu simulación*
     * Elige una de las fechas disponibles. La sesión es grupal y dura dos horas.
     *
     * 👉 https://…/procesos
     * Inicia sesión con tu correo y contraseña y abre «Mis procesos».
     * </pre>
     */
    public static String avance(String nombre, String vacante, TextosDelMomento.Texto momento,
                                String enlace) {
        StringBuilder texto = new StringBuilder(vacio(nombre) ? "Hola 👋"
                : "Hola, " + sinNegritas(nombre.trim()) + " 👋");
        texto.append("\n\n");
        if (!vacio(vacante)) {
            texto.append("📌 *").append(sinNegritas(vacante.trim())).append("*\n");
        }
        texto.append("Te toca: *").append(sinNegritas(momento.titulo())).append("*\n")
                .append(momento.texto());
        return cortar(texto + alFinal(enlace), LARGO_DEL_TEXTO);
    }

    /**
     * El enlace fijo a «Mis procesos», o {@code null} si lo configurado no lo es: tiene que ser
     * https, sin usuario ni contraseña, sin parámetros ({@code ?}) ni fragmento ({@code #}) y de
     * hasta 300 caracteres. Un enlace con un token o un identificador no sale nunca.
     */
    public static String enlaceFijo(String configurado) {
        if (vacio(configurado)) {
            return null;
        }
        String enlace = configurado.trim();
        if (enlace.length() > LARGO_DEL_ENLACE || enlace.indexOf('?') >= 0 || enlace.indexOf('#') >= 0
                || enlace.indexOf('@') >= 0 || enlace.indexOf(' ') >= 0) {
            return null;
        }
        try {
            URI uri = URI.create(enlace);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    ? enlace : null;
        } catch (IllegalArgumentException noEsUnaDireccion) {
            return null;
        }
    }

    /**
     * «20 de octubre a las 23:59», en hora de Lima. Con el año solo si no es el de {@code ahora}:
     * un plazo del año que viene no puede leerse como de este.
     */
    public static String fechaYHora(Instant momento, Instant ahora) {
        ZonedDateTime enLima = momento.atZone(ZONA);
        boolean otroAno = enLima.getYear() != ahora.atZone(ZONA).getYear();
        return (otroAno ? DIA_ANO_Y_HORA : DIA_Y_HORA).format(enLima);
    }

    /** El enlace y cómo entrar, en su bloque; sin enlace válido, solo cómo entrar. */
    private static String alFinal(String enlace) {
        String fijo = enlaceFijo(enlace);
        return fijo == null ? "\n\n" + COMO_ENTRAR : "\n\n👉 " + fijo + "\n" + COMO_ENTRAR;
    }

    /** Un asterisco suelto en un título rompería las negritas de WhatsApp. */
    private static String sinNegritas(String texto) {
        return texto == null ? "" : texto.replace("*", "");
    }

    static String recortar(String vacante) {
        return vacio(vacante) ? "" : cortar(vacante, LARGO_DEL_TITULO);
    }

    /**
     * El texto sin espacios alrededor y, si pasa de {@code largo}, cortado con «…» dentro de ese
     * largo. Nulo sigue nulo.
     */
    public static String cortar(String texto, int largo) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.length() <= largo) {
            return limpio;
        }
        return limpio.substring(0, largo - 1).trim() + "…";
    }

    private static String codificar(String texto) {
        // URLEncoder es de formularios y escribe los espacios como «+»; en un wa.me eso sale
        // como un «+» literal en el mensaje.
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
