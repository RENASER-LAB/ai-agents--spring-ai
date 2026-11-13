package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Lo que separa dos vacantes con el mismo título en una lista del panel: «San Isidro, Lima»,
 * «Arequipa» o «Remoto». Sale de la zona ({@code ubicacion}) y de la ciudad del catálogo
 * ({@code ciudad_ubigeo}, V62); sin ninguna de las dos, una vacante remota dice «Remoto» y las
 * demás no dicen nada. Se enseña, pero no se busca por ello.
 *
 * <p>El catálogo se pide una sola vez por lista y solo si alguna vacante tiene ciudad: es lo
 * mismo que hacen la lista de vacantes y el tablón.
 */
@Component
@RequiredArgsConstructor
public class LugarDeLaVacante {

    static final String REMOTO = "Remoto";

    private final CatalogosDelPerfil catalogos;

    /** El lugar de cada una de estas vacantes, con el catálogo pedido una vez. */
    public Function<Vacante, String> paraLas(Collection<Vacante> vacantes) {
        boolean hayCiudad = vacantes.stream().anyMatch(v -> v.getCiudadUbigeo() != null);
        Map<String, OpcionUbigeo> ciudades = hayCiudad ? catalogos.ciudadesPorCodigo() : Map.of();
        return v -> de(v, ciudades);
    }

    /** «Zona, Ciudad», solo la que haya, «Remoto», o nulo si no hay nada que decir. */
    static String de(Vacante vacante, Map<String, OpcionUbigeo> ciudades) {
        List<String> partes = new ArrayList<>(2);
        String zona = limpio(vacante.getUbicacion());
        if (zona != null) {
            partes.add(zona);
        }
        String ciudad = vacante.getCiudadUbigeo() == null ? null
                : limpio(ciudades.containsKey(vacante.getCiudadUbigeo())
                        ? ciudades.get(vacante.getCiudadUbigeo()).nombre()
                        : vacante.getCiudadUbigeo());
        // «Lima, Lima» no distingue nada: la zona que repite la ciudad se dice una vez.
        if (ciudad != null && partes.stream().noneMatch(p -> p.equalsIgnoreCase(ciudad))) {
            partes.add(ciudad);
        }
        if (!partes.isEmpty()) {
            return String.join(", ", partes);
        }
        String modalidad = limpio(vacante.getModalidad());
        return modalidad != null && REMOTO.equalsIgnoreCase(modalidad) ? REMOTO : null;
    }

    private static String limpio(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.isEmpty() ? null : limpio;
    }
}
