package com.renaser.ai.ai_engine.seguridad.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * El CSRF de la puerta de integraciones (V73): solo mira las peticiones que traen cookies.
 *
 * <p>Se prueba el filtro con un {@link CsrfFilter} de verdad y el mismo repositorio que usa la
 * cadena, así que lo que se comprueba es el efecto —pasa o 403— y no la marca interna.
 */
@DisplayName("La puerta de integraciones pide CSRF solo con cookies")
class ConfiguracionSeguridadTest {

    private static final String RUTA = "/api/v1/integraciones/whatsapp/bajas";

    private final AtomicBoolean llego = new AtomicBoolean();

    private MockHttpServletResponse pasar(MockHttpServletRequest peticion) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        HttpServlet controlador = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                llego.set(true);
                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        };
        new MockFilterChain(controlador, ConfiguracionSeguridad.sinCookiesNoHayCsrf(),
                new CsrfFilter(new CookieCsrfTokenRepository())).doFilter(peticion, respuesta);
        return respuesta;
    }

    @Test
    @DisplayName("Sin cookies, como llama el bot: pasa sin token CSRF y sin abrir sesión")
    void sinCookiesPasa() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", RUTA);

        MockHttpServletResponse respuesta = pasar(peticion);

        assertThat(respuesta.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        assertThat(llego).isTrue();
        assertThat(peticion.getSession(false)).isNull();
        assertThat(respuesta.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    @DisplayName("Con Cookie y sin token CSRF: 403, no llega al controlador y no abre sesión")
    void conCookieSinTokenEs403() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", RUTA);
        peticion.addHeader(HttpHeaders.COOKIE, "SESSION=de-otra-web");

        MockHttpServletResponse respuesta = pasar(peticion);

        assertThat(respuesta.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(llego).isFalse();
        assertThat(peticion.getSession(false)).isNull();
    }

    @Test
    @DisplayName("Con Cookie pero solo leyendo (GET): el CSRF no se pide, como en cualquier otra ruta")
    void conCookieLeyendoPasa() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", RUTA);
        peticion.addHeader(HttpHeaders.COOKIE, "SESSION=de-otra-web");

        assertThat(pasar(peticion).getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        assertThat(llego).isTrue();
    }

    @Test
    @DisplayName("Una petición que no es HTTP sigue su camino sin marcarse")
    void loQueNoEsHttpSigue() throws Exception {
        Filter filtro = ConfiguracionSeguridad.sinCookiesNoHayCsrf();
        ServletRequest peticion = mock(ServletRequest.class);
        ServletResponse respuesta = mock(ServletResponse.class);
        FilterChain cadena = mock(FilterChain.class);

        filtro.doFilter(peticion, respuesta, cadena);

        verify(cadena).doFilter(peticion, respuesta);
        verify(peticion, never()).setAttribute(anyString(), any());
    }
}
