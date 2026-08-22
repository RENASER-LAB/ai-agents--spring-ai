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
//   el resto           -> módulo de agentes IA y documentación: también token EQUIPO
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
            .authorizeHttpRequests(auth -> {
                // El dev-login solo es público donde alguien lo ha encendido. Donde no, cae
                // en la regla de abajo y exige un token de equipo — que es justo lo que el
                // dev-login sirve para conseguir, así que en la práctica queda cerrado.
                if (propiedades.isDevLoginActivo()) {
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/panel/auth/dev-login").permitAll();
                }
                auth.anyRequest().hasAuthority("TIPO_EQUIPO");
            })
            .exceptionHandling(e -> e.authenticationEntryPoint(entradaSinIdentidad()));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain agentesYDocumentacion(HttpSecurity http) throws Exception {
        // El módulo de agentes IA no tiene autenticación propia. Hasta el 21/08/2026 esta
        // cadena era `.anyRequest().permitAll()`: hacía explícito que el módulo corría
        // abierto —Boot lo tapaba antes con la contraseña aleatoria del starter— y lo dejaba
        // así. El precio se vio al auditar el sprint: contra el despliegue, y sin ningún
        // token, `GET /api/v1/agent-runs/history/1` y `pending-approvals` devolvían 200. En
        // la misma cadena vive `PATCH /api/v1/agent-runs/{id}/approve`, que resuelve un
        // Human Gate: cualquiera en internet podía aprobar uno.
        //
        // Mientras el módulo no tenga identidad propia, la del panel vale: es el mismo
        // equipo. Y no rompe a nadie — ninguno de los dos frontales llama a estas rutas
        // (cero coincidencias de «agent-runs», «/flows», «/rag» y «/supabase» en RenaserOs
        // y en RenaserOsPostulantes), y el chequeo de despliegue/desplegar.sh pregunta por
        // /api/v1/portal/vacantes, que es de la primera cadena.
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(filtroIdentidad, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                // El reenvío interno a /error no vuelve a pedir identidad. Sin esto un 404
                // se convierte en un 401 y el error de verdad se pierde por el camino.
                auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                if (propiedades.isDocumentacionPublica()) {
                    auth.requestMatchers(RUTAS_DOCUMENTACION).permitAll();
                }
                auth.anyRequest().hasAuthority("TIPO_EQUIPO");
            })
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
