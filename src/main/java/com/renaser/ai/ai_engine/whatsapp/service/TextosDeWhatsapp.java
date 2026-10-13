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

    /** Lo que mide como mucho el título de la vacante en el mensaje ya escrito. */
    public static final int LARGO_DEL_TITULO = 60;

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
        if (vacio(vacante)) {
            return "";
        }
        String limpio = vacante.trim();
        if (limpio.length() <= LARGO_DEL_TITULO) {
            return limpio;
        }
        return limpio.substring(0, LARGO_DEL_TITULO - 1).trim() + "…";
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
