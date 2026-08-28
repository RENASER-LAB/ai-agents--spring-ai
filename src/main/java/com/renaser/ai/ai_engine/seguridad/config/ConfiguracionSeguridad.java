package com.renaser.ai.ai_engine.seguridad.config;

import com.renaser.ai.ai_engine.seguridad.filter.FiltroIdentidad;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;



// Tres puertas, tres reglas:
//   /api/v1/portal/**  -> candidatos: público lo de mirar y registrarse, el resto con token CANDIDATO
//   /api/v1/panel/**   -> equipo de Renaser: todo con token EQUIPO
//   el resto           -> módulo de agentes IA con token EQUIPO; Swagger y el contrato, abiertos
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class ConfiguracionSeguridad {

    // Swagger y el esquema que lo alimenta. Van juntos porque se abren y se cierran juntos:
    // la interfaz sin el esquema no enseña nada, y el esquema es lo que de verdad importa.
    private static final String[] RUTAS_DOCUMENTACION = {
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml"
    };

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
                .requestMatchers(HttpMethod.POST, "/api/v1/portal/cuentas").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/portal/auth/login").permitAll()
                // El enlace del correo. Es público a la fuerza: el token que lleva ES la
                // credencial, y quien lo usa todavía no tiene sesión. Lo que acota el riesgo
                // está en ServicioEnlaceAcceso: 32 bytes de azar, solo se guarda el hash,
                // vence, y se puede revocar.
                .requestMatchers(HttpMethod.POST, "/api/v1/portal/auth/acceso").permitAll()
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
                .requestMatchers(HttpMethod.POST, "/api/v1/panel/auth/dev-login").permitAll()
                .anyRequest().hasAuthority("TIPO_EQUIPO"))
            .exceptionHandling(e -> e.authenticationEntryPoint(entradaSinIdentidad()));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain agentesYDocumentacion(HttpSecurity http) throws Exception {
        // Módulo de agentes IA protegido con token EQUIPO; Swagger y health check abiertos.
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(filtroIdentidad, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(RUTAS_DOCUMENTACION).permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().hasAuthority("TIPO_EQUIPO"))
            .exceptionHandling(e -> e.authenticationEntryPoint(entradaSinIdentidad()));
        return http.build();
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
