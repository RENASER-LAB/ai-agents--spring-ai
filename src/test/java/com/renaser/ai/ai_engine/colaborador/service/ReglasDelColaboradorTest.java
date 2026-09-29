package com.renaser.ai.ai_engine.colaborador.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Las reglas de la ficha del colaborador (V64)")
class ReglasDelColaboradorTest {

    private static final LocalDate HOY = LocalDate.now();

    @Test
    @DisplayName("el documento se limpia de espacios, puntos y guiones antes de validarlo")
    void elDocumentoSeLimpia() {
        assertThat(ReglasDelColaborador.limpiarDocumento(" 45.123.456 ")).isEqualTo("45123456");
        assertThat(ReglasDelColaborador.limpiarDocumento("ab-12 34")).isEqualTo("AB1234");
        assertThat(ReglasDelColaborador.errorDelDocumento("01", "45123456")).isNull();
    }

    @Test
    @DisplayName("el DNI tiene 8 dígitos; los demás, de 4 a 15 letras o dígitos")
    void elDniYLosDemas() {
        assertThat(ReglasDelColaborador.errorDelDocumento("01", "4512345"))
                .isEqualTo("El DNI tiene que tener 8 dígitos");
        assertThat(ReglasDelColaborador.errorDelDocumento("01", "4512345A")).isNotNull();
        assertThat(ReglasDelColaborador.errorDelDocumento("07", "AB12")).isNull();
        assertThat(ReglasDelColaborador.errorDelDocumento("07", "AB1")).contains("de 4 a 15");
        assertThat(ReglasDelColaborador.errorDelDocumento("04", "1234567890123456")).contains("de 4 a 15");
        assertThat(ReglasDelColaborador.errorDelDocumento("99", "12345678")).contains("tipo de documento");
    }

    @Test
    @DisplayName("la fecha de nacimiento no es futura y la persona tiene al menos 14 años")
    void elNacimiento() {
        assertThat(ReglasDelColaborador.errorDelNacimiento(HOY.plusDays(1), HOY)).contains("futura");
        assertThat(ReglasDelColaborador.errorDelNacimiento(HOY.minusYears(14).plusDays(1), HOY))
                .contains("14 años");
        assertThat(ReglasDelColaborador.errorDelNacimiento(HOY.minusYears(14), HOY)).isNull();
    }

    @Test
    @DisplayName("un contrato a plazo pide fin; el indeterminado no lo admite; el fin va después del ingreso")
    void elContrato() {
        LocalDate ingreso = HOY.minusMonths(1);
        assertThat(ReglasDelColaborador.errorDelContrato("03", null, ingreso)).contains("necesita la fecha de fin");
        assertThat(ReglasDelColaborador.errorDelContrato("11", null, ingreso)).contains("necesita la fecha de fin");
        assertThat(ReglasDelColaborador.errorDelContrato("01", HOY.plusYears(1), ingreso)).contains("indeterminado");
        assertThat(ReglasDelColaborador.errorDelContrato("01", null, ingreso)).isNull();
        assertThat(ReglasDelColaborador.errorDelContrato("02", null, ingreso)).isNull();
        assertThat(ReglasDelColaborador.errorDelContrato("03", ingreso, ingreso)).contains("posterior");
        assertThat(ReglasDelColaborador.errorDelContrato("03", ingreso.plusDays(1), ingreso)).isNull();
        assertThat(ReglasDelColaborador.errorDelContrato("XX", null, ingreso)).contains("no es válido");
        assertThat(ReglasDelColaborador.errorDelPeriodoDePrueba(ingreso, ingreso)).contains("posterior");
    }

    @Test
    @DisplayName("el sueldo no es negativo y la moneda es soles o dólares")
    void elSueldo() {
        assertThat(ReglasDelColaborador.errorDelSueldo(new BigDecimal("-1"), "PEN")).contains("negativo");
        assertThat(ReglasDelColaborador.errorDelSueldo(new BigDecimal("4500.50"), "PEN")).isNull();
        assertThat(ReglasDelColaborador.errorDelSueldo(new BigDecimal("4500.505"), "PEN")).contains("decimales");
        assertThat(ReglasDelColaborador.errorDelSueldo(new BigDecimal("100"), "EUR")).contains("soles o dólares");
        assertThat(ReglasDelColaborador.errorDelSueldo(null, null)).isNull();
    }

    @Test
    @DisplayName("un nombre de solo espacios no es un nombre, y un correo sin arroba no es un correo")
    void textosYCorreos() {
        assertThat(ReglasDelColaborador.texto("   ")).isNull();
        assertThat(ReglasDelColaborador.texto(" Ana ")).isEqualTo("Ana");
        assertThat(ReglasDelColaborador.errorDelCorreo("ana.correo.pe")).isNotNull();
        assertThat(ReglasDelColaborador.errorDelCorreo("ana@correo.pe")).isNull();
    }
}
