package com.renaser.ai.ai_engine.organizacion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Las plantillas y los colores del brochure (V86): el catálogo fijo y lo que promete. */
@DisplayName("El catálogo del brochure")
class CatalogoDelBrochureTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ColorDelBrochure.class)
    @DisplayName("cada color lleva texto blanco encima con un contraste de al menos 4,5:1")
    void contrasteConElBlanco(ColorDelBrochure tono) {
        assertThat(ColorDelBrochure.contraste(tono.color(), Color.WHITE)).isGreaterThanOrEqualTo(4.5);
        // Y su oscuro se lee sobre su halo: el propósito de «Clásica».
        assertThat(ColorDelBrochure.contraste(tono.oscuro(), tono.halo())).isGreaterThanOrEqualTo(4.5);
    }

    @Test
    @DisplayName("los seis colores de la spec, con el azul de Northstar por defecto")
    void losSeisColores() {
        assertThat(Arrays.stream(ColorDelBrochure.values()).map(c -> c.nombre() + " " + hex(c.color())))
                .containsExactly("Azul #1E3A8A", "Verde #0B7A63", "Rojo #C22A3F", "Noche #111312",
                        "Petróleo #155E75", "Ciruela #6B2D5C");
        assertThat(ColorDelBrochure.POR_DEFECTO).isEqualTo(ColorDelBrochure.AZUL);
        assertThat(ColorDelBrochure.deLoGuardado("MORADO")).isEqualTo(ColorDelBrochure.AZUL);
    }

    @Test
    @DisplayName("las tres plantillas, con «Clásica» por defecto")
    void lasTresPlantillas() {
        assertThat(Arrays.stream(PlantillaDelBrochure.values()).map(PlantillaDelBrochure::nombre))
                .containsExactly("Clásica", "Portada", "Columnas");
        assertThat(PlantillaDelBrochure.POR_DEFECTO).isEqualTo(PlantillaDelBrochure.CLASICA);
        assertThat(PlantillaDelBrochure.deCodigo("COLUMNAS")).contains(PlantillaDelBrochure.COLUMNAS);
        assertThat(PlantillaDelBrochure.deCodigo("Columnas")).isEmpty();
        assertThat(PlantillaDelBrochure.deLoGuardado(null)).isEqualTo(PlantillaDelBrochure.CLASICA);
    }

    @Test
    @DisplayName("la V86 exige en la base exactamente los códigos del catálogo")
    void laMigracionDiceLoMismo() throws Exception {
        String v86 = Files.readString(Path.of("src/main/resources/db/migration/V86__el_brochure_de_la_empresa.sql"));
        List<String> plantillas = Arrays.stream(PlantillaDelBrochure.values()).map(p -> "'" + p.name() + "'").toList();
        List<String> colores = Arrays.stream(ColorDelBrochure.values()).map(c -> "'" + c.name() + "'").toList();
        assertThat(v86).contains("plantilla_brochure IN (" + String.join(", ", plantillas) + ")")
                .contains("color_brochure IN (" + String.join(", ", colores) + ")")
                .contains("DEFAULT 'CLASICA'").contains("DEFAULT 'AZUL'");
    }

    @Test
    @DisplayName("las iniciales y la línea de datos, como las del portal")
    void inicialesYLinea() {
        assertThat(contenido("Cafetalera Valle Alto").iniciales()).isEqualTo("CV");
        assertThat(contenido("Textiles Ayllu S.A.C.").iniciales()).isEqualTo("TA");
        assertThat(contenido("Ñandú").iniciales()).isEqualTo("Ñ");
        assertThat(contenido("···").iniciales()).isEqualTo("·");
        ContenidoDelBrochure c = new ContenidoDelBrochure("Acme", " Textil ", null, 2004, "q", null,
                null, null, null, null, null, null, null, null, null, "17/11/2026");
        assertThat(c.linea()).isEqualTo("Textil · Fundada en 2004");
        assertThat(c.plantilla()).isEqualTo(PlantillaDelBrochure.CLASICA);
        assertThat(c.color()).isEqualTo(ColorDelBrochure.AZUL);
        assertThat(c.beneficios()).isEmpty();
    }

    private static ContenidoDelBrochure contenido(String nombre) {
        return new ContenidoDelBrochure(nombre, null, null, null, "q", null, null, null, List.of(),
                List.of(), List.of(), null, null, PlantillaDelBrochure.CLASICA, ColorDelBrochure.AZUL,
                "17/11/2026");
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
