package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    private static final String COMO_ENTRAR =
            "Entra en el portal con tu correo y tu contraseña y abre «Mis procesos».";

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
     * El aviso de la prueba: «¡Felicidades, Ana! Tu prueba del puesto para Asistente Contable ya
     * está disponible.», y cómo entrar.
     */
    public static String pruebaDisponible(String nombre, String vacante, boolean esCuestionario) {
        String saludo = vacio(nombre) ? "¡Felicidades!" : "¡Felicidades, " + nombre.trim() + "!";
        String cual = esCuestionario ? "Tu prueba técnica" : "Tu prueba del puesto";
        String para = vacio(vacante) ? "" : " para " + vacante.trim();
        return saludo + " " + cual + para + " ya está disponible.\n" + COMO_ENTRAR;
    }

    /**
     * El aviso de un avance en que le toca algo: lo que dice el portal de ese momento, y cómo
     * entrar. «Hola, Ana. En tu postulación a X te toca: Confirma tu simulación. Elige una de…»
     */
    public static String avance(String nombre, String vacante, TextosDelMomento.Texto momento) {
        String saludo = vacio(nombre) ? "Hola." : "Hola, " + nombre.trim() + ".";
        String donde = vacio(vacante) ? "En tu postulación" : "En tu postulación a " + vacante.trim();
        return saludo + " " + donde + " te toca: " + momento.titulo() + ". " + momento.texto()
                + "\n" + COMO_ENTRAR;
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
