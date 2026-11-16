package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.entity.Sede;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lo que la presentación de una empresa dice de sus sedes (V81). Se usa en el panel —la vista
 * previa— y en el perfil público, y tienen que decir lo mismo.
 */
public final class SedesDeLaEmpresa {

    private SedesDeLaEmpresa() {
    }

    /**
     * La ciudad de la cabecera: la provincia de la primera sede activa, la más antigua que la
     * tenga, con el nombre tal como lo guarda {@code ubigeo} («San Román (Juliaca)»). Nulo si
     * ninguna la tiene.
     */
    public static String ciudad(List<Sede> sedes, Map<String, OpcionUbigeo> provincias) {
        return sedes.stream()
                .filter(Sede::isEsActiva)
                .filter(s -> s.getProvinciaUbigeo() != null)
                .min(Comparator.comparing((Sede s) -> s.getCreadoEn() == null ? Instant.MAX
                        : s.getCreadoEn()).thenComparing(Sede::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> nombreDe(s.getProvinciaUbigeo(), provincias))
                .orElse(null);
    }

    /** Las provincias de las sedes activas, sin repetir y en el orden de las sedes. */
    public static List<String> provincias(List<Sede> sedes, Map<String, OpcionUbigeo> provincias) {
        LinkedHashSet<String> nombres = new LinkedHashSet<>();
        sedes.stream()
                .filter(Sede::isEsActiva)
                .map(Sede::getProvinciaUbigeo)
                .filter(Objects::nonNull)
                .map(codigo -> nombreDe(codigo, provincias))
                .forEach(nombres::add);
        return List.copyOf(nombres);
    }

    /** El nombre de la provincia; el código si el catálogo no la tiene, antes que un hueco. */
    public static String nombreDe(String codigo, Map<String, OpcionUbigeo> provincias) {
        if (codigo == null) {
            return null;
        }
        OpcionUbigeo opcion = provincias.get(codigo);
        return opcion == null ? codigo : opcion.nombre();
    }

    /** Si alguna sede tiene provincia: si no, no hace falta pedir el catálogo. */
    public static boolean algunaConProvincia(List<Sede> sedes) {
        return sedes.stream().anyMatch(s -> s.getProvinciaUbigeo() != null);
    }
}
