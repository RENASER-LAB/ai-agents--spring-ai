package com.renaser.ai.ai_engine.seguridad.filter;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.ServicioContexto;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;

import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Resuelve la identidad UNA vez por petición: token -> usuario -> roles y permisos.
// No decide nada: deja el ContextoUsuario en el SecurityContext y las cadenas de
// seguridad y @PreAuthorize deciden con él.
@Component
@RequiredArgsConstructor
@Slf4j
public class FiltroIdentidad extends OncePerRequestFilter {

    private final ServicioToken tokens;
    private final ServicioContexto contextos;
    private final UsuarioRepository usuarios;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith("Bearer ")) {
            try {
                Jwt jwt = tokens.validar(cabecera.substring(7));
                Long usuarioId = Long.valueOf(jwt.getSubject());
                String tipo = jwt.getClaimAsString("tipo");
                usuarios.findById(usuarioId)
                        .filter(Usuario::isEsActivo)
                        .ifPresent(usuario -> {
                            ContextoUsuario contexto = contextos.armar(usuario, tipo);
                            // La autoridad TIPO_* es lo único que miran las cadenas;
                            // los permisos finos los mira el bean «permisos»
                            var auth = new UsernamePasswordAuthenticationToken(
                                    contexto, null,
                                    List.of(new SimpleGrantedAuthority("TIPO_" + tipo)));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            } catch (JwtException e) {
                // Token inválido o vencido: la petición sigue anónima y la cadena
                // responderá 401 si la ruta exige identidad
                log.debug("Token rechazado: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
