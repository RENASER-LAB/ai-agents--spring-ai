package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Celular;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El celular del candidato en el visor (spec la-ia-sabe-que-dia-es-hoy, AC-14 a AC-16): el de la
 * cuenta y, si no, el del CV; como 999 999 999; y WhatsApp solo para un celular peruano.
 */
@DisplayName("El celular en el visor de informes")
class CelularDelVisorTest {

    private static final Celular EL_DE_LA_CUENTA =
            new Celular("987 654 321", "tel:+51987654321", "https://wa.me/51987654321");

    @Test
    @DisplayName("AC-14 · con celular en la cuenta y otro en el CV sale el de la cuenta, como 999 999 999, con llamar y WhatsApp con el 51")
    void elDeLaCuenta() {
        assertThat(CelularDelVisor.de("+51987654321", "912 345 678")).contains(EL_DE_LA_CUENTA);
    }

    @ParameterizedTest(name = "«{0}»")
    @ValueSource(strings = {"987654321", "987 654 321", "+51 987 654 321", "51987654321", "(+51) 987-654-321",
            "0051 987654321", "  987.654.321  "})
    @DisplayName("un celular peruano escrito de cualquier forma sale igual")
    void cualquierForma(String escrito) {
        assertThat(CelularDelVisor.de(escrito, null)).contains(EL_DE_LA_CUENTA);
    }

    @Test
    @DisplayName("AC-15 · sin celular en la cuenta sale el del CV; sin ninguno, nada")
    void elDelCvONada() {
        assertThat(CelularDelVisor.de(null, "Cel. 912-345-678")).contains(
                new Celular("912 345 678", "tel:+51912345678", "https://wa.me/51912345678"));
        assertThat(CelularDelVisor.de("   ", "912345678")).map(Celular::texto).contains("912 345 678");
        assertThat(CelularDelVisor.de(null, null)).isEmpty();
        assertThat(CelularDelVisor.de("", "sin teléfono")).isEmpty();
    }

    @Test
    @DisplayName("AC-16 · un fijo sale para llamar y sin WhatsApp")
    void unFijo() {
        assertThat(CelularDelVisor.de(null, "(01) 456-7890"))
                .contains(new Celular("(01) 456-7890", "tel:014567890", null));
    }

    @Test
    @DisplayName("AC-16 · uno que no tiene 9 cifras, o uno extranjero, sale para llamar y sin WhatsApp")
    void sinNueveCifras() {
        assertThat(CelularDelVisor.de("98765432", null))
                .contains(new Celular("98765432", "tel:98765432", null));
        assertThat(CelularDelVisor.de("+34 612 345 678", null))
                .contains(new Celular("+34 612 345 678", "tel:+34612345678", null));
        assertThat(CelularDelVisor.de("0034 612 345 678", null)).map(Celular::llamar)
                .contains("tel:+34612345678");
        // Un 9 de 10 cifras tampoco es un celular peruano.
        assertThat(CelularDelVisor.de("9876543210", null)).map(Celular::whatsapp).isEmpty();
    }

    @Test
    @DisplayName("un CV con dos números: se prefiere el celular, y si no hay, el primero")
    void dosNumeros() {
        assertThat(CelularDelVisor.de(null, "01 4567890 / 987 654 321")).contains(EL_DE_LA_CUENTA);
        assertThat(CelularDelVisor.de(null, "01 4567890 / 01 4561234")).map(Celular::texto)
                .contains("01 4567890");
    }

    @Test
    @DisplayName("muy pocas cifras no dan para llamar")
    void pocasCifras() {
        assertThat(CelularDelVisor.de("123", null)).isEmpty();
    }
}
