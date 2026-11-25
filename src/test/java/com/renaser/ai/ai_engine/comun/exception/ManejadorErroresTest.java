package com.renaser.ai.ai_engine.comun.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qué contesta el sistema cuando algo va mal.
 *
 * <p>Importa más de lo que parece: el código de estado es lo único que un cliente automático
 * mira para decidir si reintentar. Un 500 le dice «vuelve a intentarlo, es cosa nuestra»; un
 * 404 le dice «revisa lo que pediste». Confundirlos hace que alguien reintente para siempre
 * algo que nunca va a funcionar.
 */
class ManejadorErroresTest {

    private final ManejadorErrores manejador = new ManejadorErrores();

    /**
     * Una URL que no existe se iba por el manejador de «error inesperado»: 500, y en el
     * registro un ERROR con traza entera. Las dos cosas mentían.
     *
     * <p>Se descubrió pidiendo {@code /panel/admin/parametros/...} cuando la ruta de verdad
     * es {@code /panel/parametros/...}: el sistema contestó que había un problema en el
     * servidor cuando el problema estaba en la dirección.
     */
    @Test
    void unaRutaQueNoExisteEsUn404YNoUnaAveria() {
        var peticion = new MockHttpServletRequest("PUT", "/api/v1/panel/admin/parametros/x");
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.PUT, "api/v1/panel/admin/parametros/x",
                        "/api/v1/panel/admin/parametros/x");

        ProblemDetail problema = manejador.rutaQueNoExiste(ex, new ServletWebRequest(peticion));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problema.getTitle()).contains("no existe");
        // El detalle dice cuál era la ruta y dónde está la lista buena: quien se equivocó de
        // dirección necesita eso, no un «contacte con el administrador».
        assertThat(problema.getDetail())
                .contains("api/v1/panel/admin/parametros/x")
                .contains("swagger-ui.html");
    }

    /**
     * Un parámetro obligatorio que no llega es un dato que falta, no una avería: 400 con el
     * formato de la casa y diciendo cuál falta.
     *
     * <p>Lo encontró la QA de las novedades por WhatsApp: {@code GET
     * /api/v1/integraciones/whatsapp/novedades} sin {@code telefono} contestaba 500, igual que
     * {@code /panel/agentes-ia/instrucciones} sin {@code agenteCodigo}.
     */
    @Test
    void unParametroQueFaltaEsUn400QueDiceCual() {
        var peticion = new MockHttpServletRequest("GET", "/api/v1/integraciones/whatsapp/novedades");
        var ex = new MissingServletRequestParameterException("telefono", "String");

        ProblemDetail problema = manejador.faltaUnParametro(ex, new ServletWebRequest(peticion));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getTitle()).isEqualTo("Falta un dato en la petición");
        assertThat(problema.getType()).hasToString("https://api.renaser.com/errors/parametro-que-falta");
        assertThat(problema.getDetail()).contains("«telefono»");
        assertThat(problema.getProperties()).containsKey("Timestamp");
    }

    /** Un celular que ya es de otra cuenta (V92): 409 con el código que el portal pinta junto al campo. */
    @Test
    void unCelularDeOtraCuentaEsUn409ConSuCodigo() {
        var peticion = new MockHttpServletRequest("PUT", "/api/v1/portal/cuenta/telefono");

        ProblemDetail problema = manejador.telefonoEnUso(
                new com.renaser.ai.ai_engine.portal.service.TelefonoEnUsoException(), new ServletWebRequest(peticion));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problema.getDetail()).isEqualTo("Ese número ya está en otra cuenta. Usa otro o entra con esa cuenta.");
        assertThat(problema.getProperties()).containsEntry("codigo", "TELEFONO_EN_USO");
    }

    /** Dos altas a la vez con el mismo celular: la segunda choca con el índice y es el mismo 409. */
    @Test
    void elIndiceDelCelularTambienEsUn409() {
        var peticion = new MockHttpServletRequest("POST", "/api/v1/portal/cuentas");
        var ex = new org.springframework.dao.DataIntegrityViolationException("duplicada",
                new RuntimeException("ERROR: duplicate key value violates unique constraint "
                        + "\"persona_telefono_unico\" Detail: Key (telefono)=(+51987654321) already exists."));

        ProblemDetail problema = manejador.datoQueNoCuadra(ex, new ServletWebRequest(peticion));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problema.getProperties()).containsEntry("codigo", "TELEFONO_EN_USO");
    }

    /** El bot falló al activar WhatsApp (V92): 503 con su código, para volver a intentarlo. */
    @Test
    void siElBotNoActivaWhatsappEsUn503() {
        var peticion = new MockHttpServletRequest("POST", "/api/v1/portal/notificaciones/whatsapp");

        ProblemDetail problema = manejador.whatsappNoActivado(
                new com.renaser.ai.ai_engine.whatsapp.service.WhatsappNoActivadoException(
                        "No pudimos activarlo. Vuelve a intentarlo en un momento.", new RuntimeException("caído")),
                new ServletWebRequest(peticion));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problema.getProperties()).containsEntry("codigo", "NO_SE_PUDO_ACTIVAR");
    }
}
