package com.renaser.ai.ai_engine.notificacion.service.impl;

import com.renaser.ai.ai_engine.notificacion.service.EnviadorCorreo;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Manda el correo de verdad, por SMTP.
 *
 * <p>Se activa con {@code renaser.correo.transporte: smtp}. Sin eso manda
 * {@link EnviadorCorreoLog} y aqui no se instancia nada.
 *
 * <p><b>El cuerpo va como texto plano, no como HTML</b>, y es deliberado. Las plantillas de
 * {@code plantilla_correo} se escriben en texto, un correo de HTML mal armado puntua peor en
 * los filtros de spam, y lo unico que estos avisos necesitan llevar es una frase y un enlace.
 * El dia que Renaser quiera correos maquetados, se cambia aqui y las plantillas pasan a HTML.
 *
 * <p><b>Por que el remitente se declara aparte del usuario SMTP.</b> Con Gmail el usuario que
 * autentica y la direccion que ve el candidato suelen ser la misma, pero con un dominio propio
 * no: se autentica con una cuenta de servicio y se escribe desde {@code no-responder@}. Tenerlos
 * separados evita reescribir esta clase cuando Renaser confirme su dominio.
 */
@Service
@ConditionalOnProperty(name = "renaser.correo.transporte", havingValue = "smtp")
@Slf4j
public class EnviadorCorreoSmtp implements EnviadorCorreo {

    private final JavaMailSender correo;
    private final String remitente;
    private final String nombreRemitente;

    public EnviadorCorreoSmtp(
            JavaMailSender correo,
            @Value("${renaser.correo.remitente}") String remitente,
            @Value("${renaser.correo.nombre-remitente:Renaser}") String nombreRemitente) {
        this.correo = correo;
        this.remitente = remitente;
        this.nombreRemitente = nombreRemitente;
    }

    @Override
    public Resultado enviar(String correoDestino, String asunto, String cuerpo) {
        try {
            MimeMessage mensaje = correo.createMimeMessage();
            MimeMessageHelper armador =
                    new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            armador.setTo(correoDestino);
            armador.setSubject(asunto);
            armador.setText(cuerpo, false);
            // setFrom(direccion, nombre) declara UnsupportedEncodingException, pero la
            // codificacion es la del armador y arriba se fija en UTF-8, que toda JVM tiene.
            // Es decir: esa excepcion no puede ocurrir. Si algun dia ocurriera, cae en el
            // catch de abajo como cualquier otro fallo y el correo sale como FALLIDO.
            armador.setFrom(remitente, nombreRemitente);

            correo.send(mensaje);
            log.info("Correo enviado a {} · asunto «{}»", correoDestino, asunto);
            return Resultado.ENVIADO;

        } catch (Exception e) {
            // No se relanza a proposito: ver el javadoc de EnviadorCorreo.enviar. La excepcion
            // entera va al log —no a la consola— y el fallo queda escrito en correo_enviado.
            log.error("No se pudo enviar el correo a {} · asunto «{}»", correoDestino, asunto, e);
            return Resultado.FALLIDO;
        }
    }
}
