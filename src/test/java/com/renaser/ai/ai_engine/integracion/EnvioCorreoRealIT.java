package com.renaser.ai.ai_engine.integracion;

import com.renaser.ai.ai_engine.notificacion.service.EnviadorCorreo;
import com.renaser.ai.ai_engine.notificacion.service.impl.EnviadorCorreoSmtp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Manda UN correo de verdad, para comprobar que las credenciales SMTP funcionan.
 *
 * <p>Es el equivalente de {@code CalificacionIaRealIT} para el correo: toca el mundo real, asi
 * que <b>no corre solo</b>. Ni {@code test} ni {@code verify} lo ejecutan si no se pide a
 * proposito, porque un test que le escribe a alguien cada vez que alguien compila es una mala
 * idea que solo se descubre tarde.
 *
 * <p>Para pedirlo, con la direccion de destino dentro:
 *
 * <pre>
 * RENASER_CORREO_REAL=tu.correo@gmail.com ./mvnw verify -Dit.test=EnvioCorreoRealIT
 * </pre>
 *
 * <p>Las credenciales las lee de {@code application-secrets.yaml}, que no se versiona. Si ese
 * archivo no tiene {@code spring.mail}, el test se salta con un aviso en vez de fallar: no
 * haber configurado el correo todavia no es un error.
 *
 * <p>No levanta Spring ni contenedores. Arma {@link EnviadorCorreoSmtp} a mano con un
 * {@code JavaMailSenderImpl}, que es exactamente lo que le inyecta la aplicacion. Asi la
 * comprobacion tarda segundos y prueba la clase de verdad, no una imitacion.
 */
class EnvioCorreoRealIT {

    private static final Path SECRETOS = Path.of("application-secrets.yaml");

    @Test
    @DisplayName("las credenciales SMTP funcionan y el correo sale")
    @EnabledIfEnvironmentVariable(named = "RENASER_CORREO_REAL", matches = ".+@.+")
    void mandaUnCorreoDeVerdad() throws Exception {
        String destino = System.getenv("RENASER_CORREO_REAL");

        assumeThat(Files.exists(SECRETOS))
                .as("hace falta application-secrets.yaml en la raiz del proyecto")
                .isTrue();

        Map<String, Object> mail = bloqueMail();
        assumeThat(mail)
                .as("application-secrets.yaml no tiene el bloque spring.mail; ver «Correo» en el README")
                .isNotNull();

        JavaMailSenderImpl transporte = new JavaMailSenderImpl();
        transporte.setHost((String) mail.get("host"));
        transporte.setPort(((Number) mail.getOrDefault("port", 587)).intValue());
        transporte.setUsername((String) mail.get("username"));
        transporte.setPassword((String) mail.get("password"));

        Properties propiedades = transporte.getJavaMailProperties();
        propiedades.put("mail.smtp.auth", "true");
        propiedades.put("mail.smtp.starttls.enable", "true");
        // Sin esto, un servidor que no responde deja el test colgado hasta que alguien lo mata.
        propiedades.put("mail.smtp.connectiontimeout", "10000");
        propiedades.put("mail.smtp.timeout", "10000");
        propiedades.put("mail.smtp.writetimeout", "10000");

        String remitente = remitenteConfigurado();
        assumeThat(remitente)
                .as("falta renaser.correo.remitente en application-secrets.yaml")
                .isNotNull();

        EnviadorCorreoSmtp enviador = new EnviadorCorreoSmtp(transporte, remitente, "Renaser");

        EnviadorCorreo.Resultado resultado = enviador.enviar(
                destino,
                "Prueba de envio · Renaser Talento",
                """
                Este es un correo de prueba del sistema de seleccion de Renaser.

                Si lo estas leyendo, las credenciales SMTP funcionan y los avisos a los
                candidatos ya pueden salir de verdad.

                Enviado el %s.
                """.formatted(Instant.now()));

        assertThat(resultado)
                .as("si sale FALLIDO, el motivo esta en el log de este test, con la excepcion entera")
                .isEqualTo(EnviadorCorreo.Resultado.ENVIADO);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bloqueMail() throws Exception {
        Map<String, Object> spring = (Map<String, Object>) leerSecretos().get("spring");
        return spring == null ? null : (Map<String, Object>) spring.get("mail");
    }

    @SuppressWarnings("unchecked")
    private String remitenteConfigurado() throws Exception {
        Map<String, Object> renaser = (Map<String, Object>) leerSecretos().get("renaser");
        if (renaser == null) {
            return null;
        }
        Map<String, Object> correo = (Map<String, Object>) renaser.get("correo");
        return correo == null ? null : (String) correo.get("remitente");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> leerSecretos() throws Exception {
        try (InputStream entrada = Files.newInputStream(SECRETOS)) {
            Map<String, Object> raiz = new Yaml().load(entrada);
            return raiz == null ? Map.of() : raiz;
        }
    }
}
