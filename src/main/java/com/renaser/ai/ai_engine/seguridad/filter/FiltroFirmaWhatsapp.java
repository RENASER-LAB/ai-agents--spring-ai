package com.renaser.ai.ai_engine.seguridad.filter;

import com.renaser.ai.ai_engine.whatsapp.service.FirmaWhatsapp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

/**
 * La puerta de {@code /api/v1/integraciones/**}: solo pasa lo que viene con la firma del bot
 * (V73). Ver {@link FirmaWhatsapp}.
 *
 * <p>Lee el cuerpo entero antes de dejar pasar —la firma es sobre él— y se lo vuelve a servir al
 * controlador tal cual. Con la firma mala, sin ella o con una marca de más de cinco minutos,
 * contesta 401 aquí mismo: la petición no llega a ningún controlador, así que no se hace nada.
 *
 * <p>⚠️ No es un {@code @Component}, a propósito: Spring Boot registraría un filtro así para
 * TODAS las rutas, y el portal y el panel empezarían a pedir una firma que no tienen. Lo crea
 * {@code ConfiguracionSeguridad} solo para su cadena.
 */
@Slf4j
public class FiltroFirmaWhatsapp extends OncePerRequestFilter {

    /** La autoridad que da una firma válida: es lo único que acepta la cadena de integraciones. */
    public static final String AUTORIDAD = "INTEGRACION_WHATSAPP";

    /** Un mensaje del bot es pequeño; esto evita que alguien sin firma nos haga leer megas. */
    static final int MAXIMO_DE_BYTES = 64 * 1024;

    private final Supplier<String> secreto;
    private final Clock reloj;

    public FiltroFirmaWhatsapp(Supplier<String> secreto, Clock reloj) {
        this.secreto = secreto;
        this.reloj = reloj;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        byte[] cuerpo = request.getInputStream().readNBytes(MAXIMO_DE_BYTES + 1);
        boolean valida = cuerpo.length <= MAXIMO_DE_BYTES && FirmaWhatsapp.esValida(secreto.get(),
                request.getHeader(FirmaWhatsapp.CABECERA_MARCA),
                request.getHeader(FirmaWhatsapp.CABECERA_FIRMA), cuerpo, reloj.instant());
        if (!valida) {
            log.warn("Llamada de integración rechazada por firma: {} {}", request.getMethod(),
                    request.getRequestURI());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"title":"Firma no válida","status":401,\
                    "detail":"Esta ruta solo acepta llamadas firmadas por el bot de WhatsApp"}""");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "bot-whatsapp", null, List.of(new SimpleGrantedAuthority(AUTORIDAD))));
        chain.doFilter(new CuerpoRepetible(request, cuerpo), response);
    }

    /** La misma petición, con el cuerpo ya leído servido otra vez. */
    static final class CuerpoRepetible extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        CuerpoRepetible(HttpServletRequest request, byte[] cuerpo) {
            super(request);
            this.cuerpo = cuerpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream origen = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return origen.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Lectura síncrona solamente");
                }

                @Override
                public int read() {
                    return origen.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return origen.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }
    }
}
