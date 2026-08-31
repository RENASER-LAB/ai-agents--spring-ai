package com.renaser.ai.ai_engine.perfil.service;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionCatalogo;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.entity.Ubigeo;
import com.renaser.ai.ai_engine.perfil.repository.NivelEducativoRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelIdiomaRepository;
import com.renaser.ai.ai_engine.perfil.repository.UbigeoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Los catálogos del perfil, servidos como opciones. Concreto y sin interfaz, como
 * {@code ServicioParametros}: reenviar listas no necesita más ceremonia, y la regla de
 * capas (un controlador no toca repositorios) queda cumplida.
 */
@Service
@RequiredArgsConstructor
public class CatalogosDelPerfil {

    /** Fuera del Perú. Es nivel 1 y sin padre, pero se ofrece junto a las provincias. */
    public static final String FUERA_DEL_PERU = "EXT";

    private static final short DEPARTAMENTO = 1;
    private static final short PROVINCIA = 2;

    private final NivelEducativoRepository nivelesEducativos;
    private final NivelIdiomaRepository nivelesIdioma;
    private final UbigeoRepository ubigeo;

    public List<OpcionCatalogo> nivelesEducativos() {
        return nivelesEducativos.findAllByOrderByOrden().stream()
                .map(n -> new OpcionCatalogo(n.getCodigo(), n.getNombre())).toList();
    }

    public List<OpcionCatalogo> nivelesIdioma() {
        return nivelesIdioma.findAllByOrderByOrden().stream()
                .map(n -> new OpcionCatalogo(n.getCodigo(), n.getNombre())).toList();
    }

    /**
     * Dónde se puede decir que uno vive: las 196 provincias y «fuera del Perú».
     *
     * <p>Provincia y no distrito porque es la unidad con la que de verdad se filtra una
     * tanda —nadie criba por distrito— y son 196 líneas en vez de 1874. Tampoco
     * departamento: dentro de Lima, la provincia de Lima y la de Cañete están a tres horas.
     *
     * <p>Va en una sola consulta y se ordena en memoria: el nombre del departamento está en
     * otra fila del mismo catálogo, así que separarlo por niveles costaría dos viajes para
     * ahorrar 26 filas de las 222 que tiene la tabla entera.
     *
     * <p>{@code EXT} sale al final, con el departamento en null: es la opción de escape y no
     * compite por su letra con las provincias reales.
     */
    public List<OpcionUbigeo> ubigeo() {
        List<Ubigeo> catalogo = ubigeo.findByActivoTrue();
        Map<String, String> departamentos = catalogo.stream()
                .filter(u -> u.getNivel() != null && u.getNivel() == DEPARTAMENTO)
                .collect(Collectors.toMap(Ubigeo::getCodigo, Ubigeo::getNombre, (a, b) -> a));

        return catalogo.stream()
                .filter(CatalogosDelPerfil::seOfrece)
                .map(u -> new OpcionUbigeo(u.getCodigo(), u.getNombre(),
                        u.getPadre() == null ? null : departamentos.get(u.getPadre())))
                .sorted(Comparator
                        .comparing(OpcionUbigeo::departamento,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OpcionUbigeo::nombre))
                .toList();
    }

    /**
     * Si ese código es uno de los que el catálogo ofrece de verdad.
     *
     * <p>No es {@code existsById}: «01» existe en la tabla —es el departamento de
     * Amazonas— y nunca aparece en el desplegable. Aceptarlo dejaría entrar por la API un
     * valor que ninguna pantalla puede producir, y la ciudad de esa persona se quedaría sin
     * pintar sin que nadie supiera por qué.
     */
    public boolean esCiudadElegible(String codigo) {
        return codigo != null && ubigeo.findById(codigo)
                .filter(CatalogosDelPerfil::seOfrece)
                .isPresent();
    }

    /** Las provincias vivas y «fuera del Perú»: exactamente lo que sale por el catálogo. */
    private static boolean seOfrece(Ubigeo u) {
        return u.isActivo()
                && (FUERA_DEL_PERU.equals(u.getCodigo())
                        || (u.getNivel() != null && u.getNivel() == PROVINCIA));
    }
}
