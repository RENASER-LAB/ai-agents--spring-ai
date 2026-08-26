package com.renaser.ai.ai_engine.usuario.service;

import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cómo se llama cada usuario, respetando a quien ejerció su derecho al borrado.
 *
 * <p>Salió de {@code ServicioPostulacionesPanelImpl}, que lo resolvía en un método privado.
 * Mientras fue el único sitio que enseñaba nombres eso bastaba; en cuanto un segundo sitio
 * los enseña, un método privado se convierte en dos copias de la misma regla de
 * anonimización — y la que se olvide de actualizar es la que filtra un nombre borrado.
 *
 * <p>Aquí no hay permisos ni alcance, a propósito: esto traduce ids a nombres y nada más.
 * Quién puede ver a quién lo decide el servicio que llama, que es el único que sabe de qué
 * postulación o de qué vacante está hablando.
 */
@Service
@RequiredArgsConstructor
public class NombresDeUsuarios {

    /**
     * Lo que se enseña de una persona que ya no está: ni el nombre ni un hueco.
     *
     * <p>La fila tiene que seguir viéndose —la postulación existió y los conteos tienen que
     * cuadrar— pero el nombre no. Un id que la base no tiene y uno anonimizado dan lo mismo:
     * desde fuera son el mismo caso.
     */
    public static final String ANONIMO = "(anonimizado)";

    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;

    /**
     * Los nombres de una tanda entera, en dos consultas fijas.
     *
     * <p>En dos y no en dos por fila: contra Supabase cada viaje cuesta del orden de 140 ms
     * de ida y vuelta, y encadenarlos por candidato es lo que en su día dejó la bandeja en
     * minuto y medio. Los ids se piden sin repetir porque en una lista se repiten mucho.
     *
     * @return de cada id pedido a su nombre; el que no se pueda resolver sale {@link #ANONIMO}
     */
    public Map<Long, String> porUsuario(Collection<Long> usuarioIds) {
        Set<Long> ids = usuarioIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<Usuario> encontrados = usuarios.findAllById(ids);
        Map<Long, Persona> porPersona = personas.findAllById(
                        encontrados.stream().map(Usuario::getPersonaId)
                                .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Persona::getId, Function.identity()));

        Map<Long, String> resueltos = encontrados.stream().collect(Collectors.toMap(
                Usuario::getId,
                u -> Optional.ofNullable(u.getPersonaId())
                        .map(porPersona::get)
                        .map(NombresDeUsuarios::nombreDe)
                        .orElse(ANONIMO)));

        // Un id que se pidió y no salió de la base también tiene nombre: el de nadie. Así
        // quien llama hace get() y ya, sin repetir la comprobación del nulo en cada fila.
        return ids.stream().collect(Collectors.toMap(
                Function.identity(), id -> resueltos.getOrDefault(id, ANONIMO)));
    }

    /** El nombre de uno solo. Para una ficha, donde no hay tanda con la que ir. */
    public String de(Long usuarioId) {
        return porUsuario(List.of(usuarioId)).getOrDefault(usuarioId, ANONIMO);
    }

    private static String nombreDe(Persona persona) {
        if (persona.getAnonimizadoEn() != null) {
            return ANONIMO;
        }
        String completo = ((persona.getNombre() == null ? "" : persona.getNombre()) + " "
                + (persona.getApellidos() == null ? "" : persona.getApellidos())).trim();
        return completo.isEmpty() ? ANONIMO : completo;
    }
}
