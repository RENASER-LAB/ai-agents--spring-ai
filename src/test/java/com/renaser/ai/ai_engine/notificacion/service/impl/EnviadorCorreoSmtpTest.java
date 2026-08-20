package com.renaser.ai.ai_engine.notificacion.service.impl;

import com.renaser.ai.ai_engine.notificacion.service.EnviadorCorreo;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El transporte que manda de verdad.
 *
 * <p>Lo que se prueba aquí no es que sepa hablar SMTP —eso lo hace la librería— sino la
 * decisión que sí es nuestra: <b>que un correo que no sale no tumbe lo que lo provocó</b>. El
 * candidato ya avanzó de etapa, y esa verdad no se deshace porque el servidor de correo esté
 * caído. Si esta clase relanzara, la transición se desharía con ella.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El transporte SMTP")
class EnviadorCorreoSmtpTest {

    @Mock private JavaMailSender correo;

    private EnviadorCorreoSmtp enviador() {
        return new EnviadorCorreoSmtp(correo, "no-responder@renaser.test", "Renaser");
    }

    private MimeMessage unMensajeVacio() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    @DisplayName("cuando el servidor lo acepta, devuelve ENVIADO")
    void enviaYLoDice() {
        when(correo.createMimeMessage()).thenReturn(unMensajeVacio());

        EnviadorCorreo.Resultado resultado =
                enviador().enviar("alguien@ejemplo.test", "Un asunto", "Un cuerpo");

        assertThat(resultado).isEqualTo(EnviadorCorreo.Resultado.ENVIADO);
        verify(correo).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("si el servidor falla, devuelve FALLIDO y NO relanza")
    void unFalloNoTumbaLaTransicion() {
        when(correo.createMimeMessage()).thenReturn(unMensajeVacio());
        doThrow(new MailSendException("el servidor no responde"))
                .when(correo).send(any(MimeMessage.class));

        EnviadorCorreo.Resultado resultado =
                enviador().enviar("alguien@ejemplo.test", "Un asunto", "Un cuerpo");

        assertThat(resultado)
                .as("si relanzara, se desharía la transición que provocó el aviso")
                .isEqualTo(EnviadorCorreo.Resultado.FALLIDO);
    }

    @Test
    @DisplayName("una dirección imposible tampoco relanza")
    void unaDireccionRotaTampocoRelanza() {
        when(correo.createMimeMessage()).thenReturn(unMensajeVacio());

        assertThat(enviador().enviar("esto no es un correo", "Asunto", "Cuerpo"))
                .isEqualTo(EnviadorCorreo.Resultado.FALLIDO);
    }
}
