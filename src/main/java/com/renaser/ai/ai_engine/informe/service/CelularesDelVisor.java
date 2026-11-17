package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Celular;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El celular de cada postulante del visor, de una vez para toda la lista.
 *
 * <p>Cuatro consultas para la tanda entera —postulaciones, usuarios, personas y fichas del CV—,
 * nunca una por fila. <b>No recibe a nadie conectado</b>: los ids salen de la lista del ranking,
 * que ya pasó por el permiso y el alcance de quien mira. La regla de cuál se enseña está en
 * {@link CelularDelVisor}.
 *
 * <p><b>De una persona anonimizada no sale ninguno</b> (decisión D-1 del 16/11/2026): ni el de la
 * cuenta —que el borrado ya vacía— ni el que la IA leyó del CV, que hoy sigue guardado en
 * {@code dato_cv.telefono}. Quien pidió borrar sus datos no puede seguir teniendo un número a un
 * clic en el visor.
 */
@Component
@RequiredArgsConstructor
public class CelularesDelVisor {

    private final PostulacionRepository postulaciones;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final DatoCvRepository datosCv;

    /** El celular de cada una; la que no tiene ninguno no sale en el mapa. */
    @Transactional(readOnly = true)
    public Map<Long, Celular> de(List<Long> postulacionIds) {
        if (postulacionIds == null || postulacionIds.isEmpty()) {
            return Map.of();
        }
        List<Postulacion> suyas = postulaciones.findAllById(postulacionIds);
        Map<Long, Usuario> usuarioPorId = porId(usuarios.findAllById(suyas.stream()
                .map(Postulacion::getUsuarioId).filter(Objects::nonNull).distinct().toList()), Usuario::getId);
        Map<Long, Persona> personaPorId = porId(personas.findAllById(usuarioPorId.values().stream()
                .map(Usuario::getPersonaId).filter(Objects::nonNull).distinct().toList()), Persona::getId);
        Map<Long, DatoCv> fichaPorPostulacion = porId(datosCv.findByPostulacionIdIn(postulacionIds),
                DatoCv::getPostulacionId);

        Map<Long, Celular> salida = new HashMap<>();
        for (Postulacion p : suyas) {
            Usuario usuario = p.getUsuarioId() == null ? null : usuarioPorId.get(p.getUsuarioId());
            Persona persona = usuario == null || usuario.getPersonaId() == null ? null
                    : personaPorId.get(usuario.getPersonaId());
            if (persona != null && persona.getAnonimizadoEn() != null) {
                continue;
            }
            DatoCv ficha = fichaPorPostulacion.get(p.getId());
            CelularDelVisor.de(persona == null ? null : persona.getTelefono(),
                            ficha == null ? null : ficha.getTelefono())
                    .ifPresent(celular -> salida.put(p.getId(), celular));
        }
        return salida;
    }

    private static <T> Map<Long, T> porId(List<T> filas, Function<T, Long> id) {
        return filas.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }
}
