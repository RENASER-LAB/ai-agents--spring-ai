package com.renaser.ai.ai_engine.seguridad.filter;

import com.renaser.ai.ai_engine.whatsapp.service.FirmaWhatsapp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** AC-14: la puerta de las integraciones solo deja pasar lo firmado por el bot, y a tiempo. */
@DisplayName("La puerta firmada de /api/v1/integraciones")
class FiltroFirmaWhatsappTest {

    private static final String SECRETO = "secreto-de-pruebas-desde-el-bot-32b";
    private static final Instant AHORA = Instant.ofEpochSecond(1_760_000_000L);
    private static final String CUERPO = "{\"telefono\":\"+51987654321\",\"wamid\":\"w\"}";

    private final FiltroFirmaWhatsapp filtro =
            new FiltroFirmaWhatsapp(() -> SECRETO, Clock.fixed(AHORA, ZoneOffset.UTC));
    private final FilterChain cadena = mock(FilterChain.class);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest peticion(String marca, String firma) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", RUTA);
        peticion.setContent(CUERPO.getBytes(StandardCharsets.UTF_8));
        if (marca != null) {
            peticion.addHeader(FirmaWhatsapp.CABECERA_MARCA, marca);
        }
        if (firma != null) {
            peticion.addHeader(FirmaWhatsapp.CABECERA_FIRMA, firma);
        }
        return peticion;
    }

    private static final String RUTA = "/api/v1/integraciones/whatsapp/vinculos";

    private static String firmar(String marca) {
        return FirmaWhatsapp.firmar(SECRETO, marca, RUTA, CUERPO.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Firmada y a tiempo: pasa con la autoridad del bot y el controlador lee el mismo cuerpo")
    void firmadaPasa() throws Exception {
        String marca = String.valueOf(AHORA.getEpochSecond());
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion(marca, firmar(marca)), respuesta, cadena);

        ArgumentCaptor<HttpServletRequest> pasada = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(cadena).doFilter(pasada.capture(), any());
        assertThat(new String(pasada.getValue().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo(CUERPO);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly(FiltroFirmaWhatsapp.AUTORIDAD);
    }

    @Test
    @DisplayName("Sin firma: 401 con {codigo, texto} y no llega a nadie")
    void sinFirma() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion(null, null), respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"codigo\":\"FIRMA_INVALIDA\"").contains("\"texto\":");
        verify(cadena, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Con la firma de otra ruta: 401")
    void firmaDeOtraRuta() throws Exception {
        String marca = String.valueOf(AHORA.getEpochSecond());
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        String deBajas = FirmaWhatsapp.firmar(SECRETO, marca, "/api/v1/integraciones/whatsapp/bajas",
                CUERPO.getBytes(StandardCharsets.UTF_8));

        filtro.doFilter(peticion(marca, deBajas), respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        verify(cadena, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("La query no entra en la firma: se firma solo el path")
    void laQueryNoCuenta() throws Exception {
        String marca = String.valueOf(AHORA.getEpochSecond());
        MockHttpServletRequest conQuery = peticion(marca, firmar(marca));
        conQuery.setQueryString("telefono=%2B51987654321");

        filtro.doFilter(conQuery, new MockHttpServletResponse(), cadena);

        verify(cadena).doFilter(any(), any());
    }

    @Test
    @DisplayName("Con la firma mala: 401")
    void firmaMala() throws Exception {
        String marca = String.valueOf(AHORA.getEpochSecond());
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        String buena = firmar(marca);
        String mala = (buena.charAt(0) == '0' ? "1" : "0") + buena.substring(1);
        filtro.doFilter(peticion(marca, mala), respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        verify(cadena, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Con una marca de hace más de cinco minutos: 401")
    void marcaVieja() throws Exception {
        String marca = String.valueOf(AHORA.getEpochSecond() - 301);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion(marca, firmar(marca)), respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        verify(cadena, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Con una marca de más de cinco minutos hacia delante: 401")
    void marcaDelFuturo() throws Exception {
        String marca = String.valueOf(AHORA.getEpochSecond() + 301);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion(marca, firmar(marca)), respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        verify(cadena, never()).doFilter(any(), any());
    }
}
