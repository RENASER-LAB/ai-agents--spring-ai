package com.renaser.ai.ai_engine.perfil.service;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.entity.Ubigeo;
import com.renaser.ai.ai_engine.perfil.repository.NivelEducativoRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelIdiomaRepository;
import com.renaser.ai.ai_engine.perfil.repository.UbigeoRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * El desplegable de ciudades: qué se ofrece, en qué orden, y qué se acepta de vuelta.
 *
 * <p>Las dos preguntas son la misma y por eso viven en la misma clase: lo que el catálogo
 * <b>ofrece</b> y lo que el registro <b>acepta</b> tienen que coincidir exactamente. Si se
 * separaran, un día se aceptaría por la API un código que ninguna pantalla puede producir.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El catálogo de ubigeo")
class CatalogosDelPerfilTest {

    @Mock private NivelEducativoRepository nivelesEducativos;
    @Mock private NivelIdiomaRepository nivelesIdioma;
    @Mock private UbigeoRepository ubigeo;

    @InjectMocks private CatalogosDelPerfil catalogos;

    @Test
    @DisplayName("Solo salen provincias y EXT: el departamento suelto no se puede elegir")
    void elDepartamentoSueltoNoSeOfrece() {
        // Dentro de Lima, la provincia de Lima y la de Cañete están a tres horas. Ofrecer
        // «Lima» a secas convertiría el filtro del panel en una respuesta inútil.
        when(ubigeo.findByActivoTrue()).thenReturn(elCatalogoDePrueba());

        List<OpcionUbigeo> opciones = catalogos.ubigeo();

        assertThat(opciones).extracting(OpcionUbigeo::codigo)
                .containsExactlyInAnyOrder("0401", "0402", "1501", "1505", "EXT")
                .doesNotContain("04", "15");
    }

    @Test
    @DisplayName("Van agrupadas por departamento, y EXT al final")
    void elOrdenAgrupaPorDepartamentoYDejaExtAlFinal() {
        // El desplegable es uno solo, agrupado. Sin este orden la pantalla tendría que
        // reordenar 196 líneas por su cuenta, y la primera vez que alguien lo olvidara la
        // lista saldría mezclada sin que nadie supiera de quién era la culpa.
        when(ubigeo.findByActivoTrue()).thenReturn(elCatalogoDePrueba());

        List<OpcionUbigeo> opciones = catalogos.ubigeo();

        assertThat(opciones).extracting(OpcionUbigeo::nombre)
                .containsExactly("Arequipa", "Camaná", "Cañete", "Lima", "Fuera del Perú");
        assertThat(opciones).extracting(OpcionUbigeo::departamento)
                .containsExactly("Arequipa", "Arequipa", "Lima", "Lima", null);
    }

    @Test
    @DisplayName("Cada provincia sabe de qué departamento es: sin eso, dos «Lima» idénticas")
    void cadaProvinciaLlevaSuDepartamento() {
        when(ubigeo.findByActivoTrue()).thenReturn(elCatalogoDePrueba());

        List<OpcionUbigeo> opciones = catalogos.ubigeo();

        assertThat(opciones).filteredOn(o -> "1501".equals(o.codigo()))
                .singleElement()
                .satisfies(o -> {
                    assertThat(o.nombre()).isEqualTo("Lima");
                    assertThat(o.departamento()).isEqualTo("Lima");
                });
    }

    @Test
    @DisplayName("Un departamento existe en la tabla y aun así no es una ciudad elegible")
    void unDepartamentoNoEsUnaCiudadElegible() {
        // Este es el caso que un existsById dejaría pasar: «04» es Arequipa y está en la
        // tabla. Aceptarlo dejaría a esa persona con una ciudad que el desplegable nunca
        // ofreció y que ningún filtro sabe encontrar.
        when(ubigeo.findById("04")).thenReturn(Optional.of(
                lugar("04", 1, null, "Arequipa", true)));

        assertThat(catalogos.esCiudadElegible("04")).isFalse();
    }

    @Test
    @DisplayName("Una provincia archivada deja de aceptarse, aunque su fila siga ahí")
    void unaProvinciaArchivadaNoSeAcepta() {
        // Las filas del catálogo no se borran —hay personas apuntando a ellas—, se apagan.
        when(ubigeo.findById("0402")).thenReturn(Optional.of(
                lugar("0402", 2, "04", "Camaná", false)));

        assertThat(catalogos.esCiudadElegible("0402")).isFalse();
    }

    @Test
    @DisplayName("Una provincia viva y EXT sí se aceptan")
    void loQueElCatalogoOfreceSeAcepta() {
        when(ubigeo.findById("0402")).thenReturn(Optional.of(
                lugar("0402", 2, "04", "Camaná", true)));
        when(ubigeo.findById("EXT")).thenReturn(Optional.of(
                lugar("EXT", 1, null, "Fuera del Perú", true)));

        assertThat(catalogos.esCiudadElegible("0402")).isTrue();
        assertThat(catalogos.esCiudadElegible("EXT")).isTrue();
    }

    @Test
    @DisplayName("Un código inventado no llega ni a preguntarle a la base si es null")
    void unCodigoNuloNoEsElegible() {
        assertThat(catalogos.esCiudadElegible(null)).isFalse();
    }

    /** Dos departamentos con dos provincias cada uno, más EXT. Basta para el orden. */
    private List<Ubigeo> elCatalogoDePrueba() {
        return List.of(
                lugar("15", 1, null, "Lima", true),
                lugar("1505", 2, "15", "Cañete", true),
                lugar("1501", 2, "15", "Lima", true),
                lugar("04", 1, null, "Arequipa", true),
                lugar("0402", 2, "04", "Camaná", true),
                lugar("0401", 2, "04", "Arequipa", true),
                lugar("EXT", 1, null, "Fuera del Perú", true));
    }

    private Ubigeo lugar(String codigo, int nivel, String padre, String nombre, boolean activo) {
        return Ubigeo.builder().codigo(codigo).nivel((short) nivel).padre(padre)
                .nombre(nombre).activo(activo).build();
    }
}
