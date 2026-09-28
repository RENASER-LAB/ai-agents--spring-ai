package com.renaser.ai.ai_engine.resena.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.PromedioResenas;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenaVisible;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.RespuestaVisible;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResumenResenas;
import com.renaser.ai.ai_engine.resena.entity.Resena;
import com.renaser.ai.ai_engine.resena.entity.RespuestaResena;
import com.renaser.ai.ai_engine.resena.repository.ResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.RespuestaResenaRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leer reseñas: la lista visible de una persona, su resumen y los promedios de una tanda.
 *
 * <p>Lo usan la ficha del panel, el perfil del portal y la tabla de la vacante, y por eso vive
 * aparte: «visible» —ni borrada ni ocultada— tiene que significar lo mismo en los tres, o la
 * cabecera del perfil diría un promedio y la tabla otro.
 *
 * <p>No recibe a quien pregunta, a propósito: quién puede leer lo decide cada servicio antes
 * de llamar aquí. Esta es la lectura entre empresas, la única excepción de la regla, y entra
 * siempre por la persona de una postulación que quien pregunta ya puede ver.
 */
@Component
@RequiredArgsConstructor
public class LectorDeResenas {

    /** Las más recientes arriba; a igualdad de fecha, la de id mayor. */
    public static final Comparator<Resena> MAS_RECIENTES = Comparator
            .comparing(Resena::getPublicadaEn, Comparator.reverseOrder())
            .thenComparing(Resena::getId, Comparator.reverseOrder());

    private final ResenaRepository resenas;
    private final RespuestaResenaRepository respuestas;
    private final OrganizacionRepository organizaciones;

    /** Las visibles de una persona, las más recientes arriba. */
    public List<Resena> visiblesDe(Long personaId) {
        return resenas.findByPersonaIdAndBorradaEnIsNull(personaId).stream()
                .filter(Resena::esVisible)
                .sorted(MAS_RECIENTES)
                .toList();
    }

    /** El resumen de la cabecera del perfil: promedio, cantidad y reparto de las visibles. */
    public ResumenResenas resumenDe(Long personaId) {
        return resumen(visiblesDe(personaId));
    }

    public ResumenResenas resumen(Collection<Resena> visibles) {
        return ReglasDeLaResena.resumen(visibles.stream().map(Resena::getEstrellas).toList());
    }

    /**
     * Las reseñas visibles de una persona tal como las lee cualquier empresa: con el nombre
     * de la autora, el puesto y la respuesta, si se ve.
     */
    public List<ResenaVisible> paraLeer(List<Resena> visibles) {
        if (visibles.isEmpty()) {
            return List.of();
        }
        Map<Long, String> empresas = empresasDe(visibles);
        Map<Long, String> puestos = puestosDe(visibles);
        Map<Long, RespuestaResena> respuestaPorResena = respuestasVivasDe(visibles);
        return visibles.stream()
                .map(r -> new ResenaVisible(r.getId(), r.getEstrellas(),
                        empresas.get(r.getOrganizacionId()), puestos.get(r.getId()),
                        r.getTexto(), r.getPublicadaEn(), r.getEditadaEn() != null,
                        visibleOVacia(respuestaPorResena.get(r.getId()))))
                .toList();
    }

    /**
     * El promedio de cada persona de una tanda, en una sola consulta. Las que no tienen
     * reseñas visibles no salen en el mapa.
     */
    public Map<Long, PromedioResenas> promediosDe(Collection<Long> personaIds) {
        Set<Long> ids = personaIds.stream().filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, PromedioResenas> promedios = new HashMap<>();
        for (Object[] fila : resenas.sumasVisiblesDePersonas(ids)) {
            long suma = ((Number) fila[1]).longValue();
            long cantidad = ((Number) fila[2]).longValue();
            promedios.put((Long) fila[0], new PromedioResenas(
                    ReglasDeLaResena.promedio(suma, cantidad), (int) cantidad));
        }
        return promedios;
    }

    /** El nombre ACTUAL de cada empresa autora: si cambia de nombre, sus reseñas lo dicen. */
    public Map<Long, String> empresasDe(Collection<Resena> lista) {
        Set<Long> ids = lista.stream().map(Resena::getOrganizacionId).collect(Collectors.toSet());
        return organizaciones.findAllById(ids).stream()
                .collect(Collectors.toMap(Organizacion::getId, Organizacion::getNombre));
    }

    /** El título de la vacante de cada contratación reseñada, en una consulta. */
    public Map<Long, String> puestosDe(Collection<Resena> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> puestos = new HashMap<>();
        for (Object[] fila : resenas.puestosDeLasResenas(
                lista.stream().map(Resena::getId).toList())) {
            puestos.put((Long) fila[0], (String) fila[1]);
        }
        return puestos;
    }

    /** La respuesta viva de cada reseña —visible u ocultada—, en una consulta. */
    public Map<Long, RespuestaResena> respuestasVivasDe(Collection<Resena> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        return respuestas.findByResenaIdInAndBorradaEnIsNull(
                        lista.stream().map(Resena::getId).toList()).stream()
                .collect(Collectors.toMap(RespuestaResena::getResenaId, Function.identity(),
                        (a, b) -> a));
    }

    /** Una respuesta ocultada por la plataforma no la ve ninguna empresa. */
    private static RespuestaVisible visibleOVacia(RespuestaResena respuesta) {
        if (respuesta == null || respuesta.getOcultadaEn() != null) {
            return null;
        }
        return new RespuestaVisible(respuesta.getTexto(), respuesta.getPublicadaEn(),
                respuesta.getEditadaEn() != null);
    }
}
