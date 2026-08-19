package com.renaser.ai.ai_engine.integracion.soporte;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Apunta cada petición y cada respuesta de un flujo, para poder mirarlas después.
 *
 * <p><b>Para qué.</b> Las pruebas de punta a punta dicen si algo pasa o falla, pero no
 * enseñan lo que ocurrió por dentro. Con esto queda el recorrido entero —qué se pidió, con
 * qué cuerpo, qué contestó el sistema— y se puede revisar a mano, como quien mira la
 * pestaña de red del navegador. Sirve para validar que el sistema hace lo que se cree que
 * hace, sin leer el código de la prueba.
 *
 * <p><b>Apagado por defecto.</b> Se enciende pidiéndolo:
 *
 * <pre>{@code
 * ./mvnw verify -Dtraza=si -Dit.test=FlujoHito1IT
 * python3 scripts/reporte-pruebas.py
 * }</pre>
 *
 * <p>Se apaga por defecto por dos razones. Una, que escribir 65 pares de petición y
 * respuesta en cada compilación no le sirve a nadie más que a quien está validando. Y dos,
 * más importante: lo que se escribe aquí acabaría en un artefacto de un repositorio
 * <b>público</b>, y aunque sean datos inventados, un volcado permanente de todo lo que la
 * suite manda y recibe no es algo que convenga dejar encendido sin querer.
 *
 * <p><b>Qué no se guarda entero.</b> Los cuerpos grandes se recortan a 4 KB —un currículum
 * en PDF llenaría el archivo de bytes ilegibles— y los tokens se tapan: son de prueba y
 * están firmados con una clave de prueba, pero un token completo en un registro público
 * enseña a cualquiera cómo se arman los nuestros.
 *
 * <p>Se engancha con {@code @Import(TrazaHttp.class)} en la clase de la prueba.
 */
@TestConfiguration
public class TrazaHttp {

    /** Encendido solo si se pide por línea de órdenes. */
    private static final boolean ACTIVA = System.getProperty("traza") != null;

    /** Un currículum en PDF pesa cientos de kilobytes: se guarda el principio y se dice cuánto era. */
    private static final int TOPE_CUERPO = 4096;

    private static final Path CARPETA = Path.of("target", "traza-pruebas");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Bean
    MockMvcBuilderCustomizer apuntadorDeLlamadas() {
        return constructor -> constructor.alwaysDo(resultado -> {
            if (!ACTIVA) {
                return;
            }
            MockHttpServletRequest peticion = resultado.getRequest();
            MockHttpServletResponse respuesta = resultado.getResponse();

            ObjectNode linea = JSON.createObjectNode();
            String[] origen = deDondeSaleLaLlamada();
            linea.put("clase", origen[0]);
            linea.put("prueba", origen[1]);
            linea.put("metodo", peticion.getMethod());
            linea.put("uri", uriCompleta(peticion));
            linea.put("autorizada", peticion.getHeader("Authorization") != null);
            linea.put("tipoPeticion", peticion.getContentType() == null ? "" : peticion.getContentType());
            linea.put("peticion", recortar(cuerpoDePeticion(peticion), peticion.getContentType()));
            linea.put("estado", respuesta.getStatus());
            linea.put("tipoRespuesta", respuesta.getContentType() == null ? "" : respuesta.getContentType());
            linea.put("respuesta", recortar(cuerpoDeRespuesta(respuesta), respuesta.getContentType()));

            escribir(origen[0], linea.toString());
        });
    }

    /**
     * De qué prueba salió esta llamada.
     *
     * <p>Se saca de la pila. Hay que quedarse con el marco <b>más externo</b> de la clase de
     * la prueba, no con el primero: las pruebas se apoyan en métodos auxiliares suyos
     * —{@code conToken(...)} y compañía— y el primero que aparece es ese ayudante, con lo
     * que todas las llamadas saldrían atribuidas a él en vez de a la prueba que las provocó.
     */
    private static String[] deDondeSaleLaLlamada() {
        String[] encontrado = {"desconocida", "desconocida"};
        for (StackTraceElement marco : Thread.currentThread().getStackTrace()) {
            String clase = marco.getClassName();
            if ((clase.endsWith("IT") || clase.endsWith("Test")) && clase.contains("renaser")) {
                // No se corta: se sigue subiendo, y gana el último, que es el método @Test
                encontrado = new String[] {
                        clase.substring(clase.lastIndexOf('.') + 1), marco.getMethodName()};
            }
        }
        return encontrado;
    }

    private static String uriCompleta(MockHttpServletRequest peticion) {
        String consulta = peticion.getQueryString();
        return consulta == null ? peticion.getRequestURI() : peticion.getRequestURI() + "?" + consulta;
    }

    private static String cuerpoDePeticion(MockHttpServletRequest peticion) {
        try {
            byte[] bytes = peticion.getContentAsByteArray();
            return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String cuerpoDeRespuesta(MockHttpServletResponse respuesta) {
        try {
            return respuesta.getContentAsString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Recorta lo largo, avisa de lo binario y tapa los tokens. */
    private static String recortar(String cuerpo, String tipo) {
        if (cuerpo == null || cuerpo.isEmpty()) {
            return "";
        }
        boolean binario = tipo != null
                && (tipo.startsWith("multipart/") || tipo.startsWith("application/pdf")
                    || tipo.startsWith("application/octet-stream"));
        if (binario) {
            return "[" + tipo.split(";")[0] + " · " + (cuerpo.length() / 1024) + " KB · no se muestra]";
        }
        String tapado = cuerpo.replaceAll("(eyJ[A-Za-z0-9_-]{6})[A-Za-z0-9_.-]+", "$1…[token recortado]");
        if (tapado.length() > TOPE_CUERPO) {
            return tapado.substring(0, TOPE_CUERPO) + "\n… [recortado, eran " + tapado.length() + " caracteres]";
        }
        return tapado;
    }

    private static synchronized void escribir(String clase, String linea) {
        try {
            Files.createDirectories(CARPETA);
            Files.writeString(CARPETA.resolve(clase + ".jsonl"), linea + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Apuntar el recorrido es una ayuda, no parte de lo que la prueba comprueba:
            // si el disco falla, la prueba sigue su curso y no se inventa un fallo ajeno.
            System.err.println("[traza] no se pudo escribir: " + e.getMessage());
        }
    }
}
