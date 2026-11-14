package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.FilaDePrueba;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Los filtros de «Pruebas» y su orden, sin tocar la base: se prueban sin servidor.
 *
 * <p>Dentro de un grupo basta con cumplir uno de los valores; entre grupos, todos. El
 * buscador mira solo el título de la vacante (decisión del 13/11/2026), sin tildes ni
 * mayúsculas, como «Copiar de otra vacante». Una vacante sin nivel no coincide con ningún
 * filtro de nivel. Un valor que no existe no coincide con nada.
 *
 * @param buscar      el texto del buscador, o nulo
 * @param niveles     códigos de nivel (EJECUCION…), o vacío
 * @param estados     BORRADOR · PUBLICADA · EN_USO, o vacío
 * @param situaciones SIN_PUBLICAR · PUBLICADA · CERRADA · ARCHIVADA, o vacío
 */
public record FiltrosDelIndice(String buscar, Set<String> niveles, Set<String> estados,
                               Set<String> situaciones) {

    public static final FiltrosDelIndice NINGUNO = new FiltrosDelIndice(null, Set.of(), Set.of(), Set.of());

    /**
     * Las publicadas primero, la más reciente arriba; al final, las que nunca se publicaron,
     * por título.
     */
    public static final Comparator<FilaDePrueba> ORDEN = Comparator
            .comparing(FilaDePrueba::publicadaEn, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(f -> sinTildes(f.titulo()))
            .thenComparing(FilaDePrueba::vacanteId, Comparator.reverseOrder());

    public FiltrosDelIndice {
        niveles = enMayusculas(niveles);
        estados = enMayusculas(estados);
        situaciones = enMayusculas(situaciones);
    }

    /** Lo que llega por la dirección: listas que pueden faltar o traer huecos. */
    public static FiltrosDelIndice de(String buscar, List<String> niveles, List<String> estados,
                                      List<String> situaciones) {
        return new FiltrosDelIndice(buscar, Set.copyOf(limpias(niveles)), Set.copyOf(limpias(estados)),
                Set.copyOf(limpias(situaciones)));
    }

    public boolean admite(FilaDePrueba fila) {
        String buscado = sinTildes(buscar);
        if (!buscado.isEmpty() && !sinTildes(fila.titulo()).contains(buscado)) {
            return false;
        }
        if (!niveles.isEmpty() && (fila.nivel() == null
                || !niveles.contains(fila.nivel().toUpperCase(Locale.ROOT)))) {
            return false;
        }
        if (!situaciones.isEmpty() && !situaciones.contains(fila.situacion())) {
            return false;
        }
        return estados.isEmpty() || estados.stream().anyMatch(e -> cumpleElEstado(fila, e));
    }

    private static boolean cumpleElEstado(FilaDePrueba fila, String estado) {
        return switch (estado) {
            case DtosIndiceDePruebas.EN_USO -> fila.laRindeAlguien();
            case DtosIndiceDePruebas.BORRADOR, DtosIndiceDePruebas.PUBLICADA -> estado.equals(fila.estado());
            default -> false;
        };
    }

    /** Sin tildes, en minúsculas y sin espacios en los bordes; vacío si no hay texto. */
    public static String sinTildes(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    private static Set<String> enMayusculas(Set<String> valores) {
        return valores == null ? Set.of()
                : valores.stream().map(v -> v.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> limpias(Collection<String> valores) {
        return valores == null ? List.of()
                : valores.stream().filter(Objects::nonNull).map(String::strip).filter(v -> !v.isEmpty())
                        .toList();
    }
}
