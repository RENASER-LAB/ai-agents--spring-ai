package com.renaser.ai.ai_engine.seguridad.config;

import com.renaser.ai.ai_engine.seguridad.filter.FiltroFirmaWhatsapp;
import com.renaser.ai.ai_engine.seguridad.filter.FiltroIdentidad;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;



// Cuatro puertas, cuatro reglas:
//   /api/v1/portal/**  -> candidatos: público lo de mirar y registrarse, el resto con token CANDIDATO
//   /api/v1/panel/**   -> equipo de Renaser: todo con token EQUIPO
//   /api/v1/integraciones/** -> otros sistemas (el bot de WhatsApp): solo su firma HMAC, sin token
//   el resto           -> módulo de agentes IA con token EQUIPO; Swagger y el contrato, abiertos
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class ConfiguracionSeguridad {

    private final PropiedadesSeguridad propiedades;
    private final FiltroIdentidad filtroIdentidad;

    @Bean
    @Order(1)
    SecurityFilterChain portal(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/portal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(filtroIdentidad, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Lo que un candidato puede hacer ANTES de tener cuenta
                .requestMatchers(HttpMethod.GET, "/api/v1/portal/vacantes/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/portal/consentimientos/textos").permitAll()
                // La ciudad es obligatoria al crear la cuenta, así que su desplegable tiene
                // que cargar ANTES de que exista el token. La ruta exacta y no
                // /catalogos/**: los otros dos catálogos siguen pidiendo sesión, y un
                // comodín aquí los abriría de paso sin que nadie lo decidiera.
                .requestMatchers(HttpMethod.GET, "/api/v1/portal/catalogos/ubigeo").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/portal/cuentas").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/portal/auth/login").permitAll()
                // El enlace del correo. Es público a la fuerza: el token que lleva ES la
                // credencial, y quien lo usa todavía no tiene sesión. Lo que acota el riesgo
                // está en ServicioEnlaceAcceso: 32 bytes de azar, solo se guarda el hash,
                // vence, y se puede revocar.
                .requestMatchers(HttpMethod.POST, "/api/v1/portal/auth/acceso").permitAll()
                // «Me olvidé mi contraseña»: quien la usa, por definición, no puede entrar.
                // Pedir el enlace responde siempre lo mismo; elegir la contraseña lleva su
                // credencial dentro, el token de un solo uso del correo.
                .requestMatchers(HttpMethod.POST, "/api/v1/portal/auth/recuperacion").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/portal/auth/restablecer").permitAll()
                .anyRequest().hasAuthority("TIPO_CANDIDATO"))
            .exceptionHandling(e -> e.authenticationEntryPoint(entradaSinIdentidad()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain panel(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/panel/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(filtroIdentidad, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Las tres formas de conseguir un token de equipo, públicas a la fuerza:
                // quien entra todavía no tiene sesión. La invitación lleva su credencial
                // dentro (el token de un solo uso); el dev-login se apaga por propiedad.
                .requestMatchers(HttpMethod.POST, "/api/v1/panel/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/panel/auth/invitacion").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/panel/auth/dev-login").permitAll()
                // Y las dos de la contraseña olvidada, por lo mismo que en el portal.
                .requestMatchers(HttpMethod.POST, "/api/v1/panel/auth/recuperacion").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/panel/auth/restablecer").permitAll()
                .anyRequest().hasAuthority("TIPO_EQUIPO"))
            .exceptionHandling(e -> e.authenticationEntryPoint(entradaSinIdentidad()));
        return http.build();
    }

    /**
     * La puerta de otros sistemas (V73): hoy, el bot de WhatsApp.
     *
     * <p>Va antes de la cadena por defecto a propósito: esa deja pasar todo lo que no es portal ni
     * panel, y una ruta de integración que cayera ahí quedaría abierta. Aquí no vale ningún token
     * —ni de candidato ni de equipo—: solo la firma HMAC del bot, que comprueba
     * {@link FiltroFirmaWhatsapp} antes de llegar al controlador.
     */
    @Bean
    @Order(3)
    SecurityFilterChain integraciones(HttpSecurity http, PropiedadesWhatsapp whatsapp) throws Exception {
        FiltroFirmaWhatsapp firma = new FiltroFirmaWhatsapp(whatsapp::getSecretoDesdeElBot,
                java.time.Clock.systemUTC());
        http.securityMatcher("/api/v1/integraciones/**")
            // El CSRF sigue encendido, pero solo mira peticiones con cookies: es la credencial que
            // el navegador manda solo y la que una web ajena podría aprovechar. El bot firma con
            // HMAC y nunca manda cookies, así que su llamada se salta el chequeo y sigue a la firma;
            // una con Cookie y sin token CSRF se queda en 403. El token va en cookie y no en la
            // sesión: esta puerta no abre ninguna.
            .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
            .addFilterBefore(sinCookiesNoHayCsrf(), CsrfFilter.class)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(firma, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(FiltroFirmaWhatsapp.AUTORIDAD))
            .exceptionHandling(e -> e.authenticationEntryPoint(entradaSinIdentidad()));
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain agentesYDocumentacion(HttpSecurity http) throws Exception {
        // El módulo de agentes IA corrió mucho tiempo sin seguridad propia, abierto a
        // cualquiera. Lo que obligó a cerrarlo fue POST /api/v1/rag/ingest: aceptaba una ruta
        // del sistema de ficheros del servidor, la leía, y su texto quedaba consultable por
        // GET /api/v1/rag/search. Sin token, eso era leer cualquier fichero de la máquina
        // desde internet.
        //
        // Ahora pide identidad de equipo, la misma que el panel. No se pierde el fuzzing
        // nocturno: entra con un token de dev-login, que es TIPO_EQUIPO.
        //
        // Swagger y /v3/api-docs siguen abiertos: son el contrato, no datos, y el nocturno
        // los lee antes de tener token.
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(filtroIdentidad, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/rag/**", "/api/v1/agent-runs/**",
                        "/api/v1/flows/**")
                    .hasAuthority("TIPO_EQUIPO")
                .anyRequest().permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint(entradaSinIdentidad()));
        return http.build();
    }

    /** Sin cabecera Cookie no hay credencial del navegador que falsificar: el CSRF no la mira. */
    static Filter sinCookiesNoHayCsrf() {
        return (request, response, chain) -> {
            if (request instanceof HttpServletRequest peticion
                    && peticion.getHeader(HttpHeaders.COOKIE) == null) {
                CsrfFilter.skipRequest(peticion);
            }
            chain.doFilter(request, response);
        };
    }

    private org.springframework.security.web.AuthenticationEntryPoint entradaSinIdentidad() {
        // 401 en JSON plano: el frontend distingue «no has entrado» de «no puedes» (403)
        return (request, response, ex) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"title":"Hace falta identificarse","status":401,\
                    "detail":"Esta ruta exige un token válido en la cabecera Authorization"}""");
        };
    }

    @Bean
    PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder();
    }

}
