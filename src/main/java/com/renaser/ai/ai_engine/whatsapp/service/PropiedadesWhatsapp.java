package com.renaser.ai.ai_engine.whatsapp.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * La configuración del bot de WhatsApp (V73). No vive en la base: el número, la dirección y los
 * secretos son del despliegue, y los secretos van en sus variables, nunca en el repositorio.
 *
 * <p>Lo que SÍ vive en la base es el interruptor de la beta ({@code whatsapp_novedades_activo},
 * de la plataforma): ese lo enciende Renaser desde el panel el día que el número pase a NUEVO.
 */
@Component
@ConfigurationProperties(prefix = "app.whatsapp")
@Getter @Setter
public class PropiedadesWhatsapp {

    /** Ni anuncia, ni avisa, ni revoca: el portal no ofrece el botón. Lo de todo entorno de verdad hasta que se diga. */
    public static final String APAGADO = "apagado";
    /** Un bot de mentira en memoria, con su registro: desarrollo, pruebas y E2E. */
    public static final String FALSO = "falso";
    /** El servicio de conversación del CRM, por HTTPS y con firma. */
    public static final String REAL = "real";

    /** {@value #APAGADO}, {@value #FALSO} o {@value #REAL}. */
    private String modo = APAGADO;

    /** El número del bot, como va en {@code wa.me}: solo cifras, con el 51 delante. */
    private String numeroBot = "51928439930";

    /** La dirección base del servicio de conversación (sin barra final). Solo en {@value #REAL}. */
    private String urlServicio;

    /** Con lo que EX firma lo que manda al bot. */
    private String secretoHaciaElBot;

    /** Con lo que el bot firma lo que manda a EX. Sin él, toda llamada del bot es 401. */
    private String secretoDesdeElBot;

    /** Lo máximo que se espera al bot en cada llamada. */
    private int segundosDeEspera = 3;

    /** Cuánto dura el anuncio de un número si no escribe. */
    private int horasDelAnuncio = 24;

    /** Intentos de cada envío al bot antes de darlo por fallido. */
    private int maximoDeIntentos = 5;

    /** La espera antes del segundo intento; las siguientes crecen por cuatro. */
    private int segundosAntesDelPrimerReintento = 30;
}
