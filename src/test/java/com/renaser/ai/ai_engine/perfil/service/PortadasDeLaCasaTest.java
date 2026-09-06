package com.renaser.ai.ai_engine.perfil.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Las portadas que ofrece la casa")
class PortadasDeLaCasaTest {

    @Test
    @DisplayName("Son las cinco del espectro del canto, ni una más")
    void sonCinco() {
        // Si alguien añade una aquí sin dibujarla en el CSS del portal, la
        // persona elige un fondo que no existe y ve un hueco gris.
        assertThat(PortadasDeLaCasa.CODIGOS)
                .containsExactlyInAnyOrder(
                        "CANTO_MENTA", "CANTO_AQUA", "CANTO_ROSA", "CANTO_VIOLETA", "BRUMA");
    }

    @Test
    @DisplayName("Una de las cinco pasa")
    void laBuenaPasa() {
        PortadasDeLaCasa.CODIGOS.forEach(PortadasDeLaCasa::exigirValido);
    }

    @Test
    @DisplayName("Una inventada se para aquí, no en la base")
    void laInventadaSePara() {
        assertThatThrownBy(() -> PortadasDeLaCasa.exigirValido("CANTO_DORADO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Elige una de las que ofrece el portal");
    }

    @Test
    @DisplayName("El nulo también, y con el mismo mensaje")
    void elNuloTambien() {
        // Llega de un JSON sin el campo: es el caso más probable de los dos.
        assertThatThrownBy(() -> PortadasDeLaCasa.exigirValido(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
