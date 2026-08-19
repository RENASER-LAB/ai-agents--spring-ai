package com.renaser.ai.ai_engine.comun.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
}
