package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * El resolutor: el único punto donde una bandera de personalización se interpreta.
 *
 * <p>La tabla de verdad completa es corta y vale la pena tenerla entera: bandera apagada
 * lee a la plataforma, encendida lee lo propio, las banderas son independientes entre sí,
 * y la plataforma siempre es dueña de lo suyo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El resolutor de dueño de instrumento")
class DuenoDelInstrumentoTest {

    private static final Long PLATAFORMA = 1L;
    private static final Long EMPRESA = 2L;

    @Mock private OrganizacionRepository organizaciones;

    private DuenoDelInstrumento resolutor;

    @BeforeEach
    void armar() {
        resolutor = new DuenoDelInstrumento(organizaciones);
        lenient().when(organizaciones.findByEsPlataformaTrue())
                .thenReturn(Optional.of(Organizacion.builder().id(PLATAFORMA).esPlataforma(true).build()));
    }

    private void laEmpresaTiene(Organizacion organizacion) {
        when(organizaciones.findById(EMPRESA)).thenReturn(Optional.of(organizacion));
    }

    @Test
    @DisplayName("Con todas las banderas apagadas, todo instrumento es de la plataforma")
    void banderasApagadasLeenLaPlataforma() {
        laEmpresaTiene(Organizacion.builder().id(EMPRESA).build());

        for (Instrumento instrumento : Instrumento.values()) {
            assertThat(resolutor.duenoDe(EMPRESA, instrumento)).isEqualTo(PLATAFORMA);
        }
    }

    @Test
    @DisplayName("Cada bandera manda solo sobre su instrumento: son independientes")
    void lasBanderasSonIndependientes() {
        // Pesos propios y banco compartido: el caso que motiva la independencia —
        // funciona porque los pesos van por dimensión y las dimensiones son globales
        laEmpresaTiene(Organizacion.builder().id(EMPRESA).pesosPropios(true).build());

        assertThat(resolutor.duenoDe(EMPRESA, Instrumento.PESOS)).isEqualTo(EMPRESA);
        assertThat(resolutor.duenoDe(EMPRESA, Instrumento.BANCO)).isEqualTo(PLATAFORMA);
        assertThat(resolutor.duenoDe(EMPRESA, Instrumento.PLANTILLA_EVALUACION)).isEqualTo(PLATAFORMA);
        assertThat(resolutor.duenoDe(EMPRESA, Instrumento.PRUEBA)).isEqualTo(PLATAFORMA);
    }

    @Test
    @DisplayName("La plataforma siempre es dueña de lo suyo, con las banderas como estén")
    void laPlataformaSiempreEsDuena() {
        when(organizaciones.findById(PLATAFORMA)).thenReturn(Optional.of(
                Organizacion.builder().id(PLATAFORMA).esPlataforma(true).build()));

        for (Instrumento instrumento : Instrumento.values()) {
            assertThat(resolutor.duenoDe(PLATAFORMA, instrumento)).isEqualTo(PLATAFORMA);
        }
    }

    @Test
    @DisplayName("Sin plataforma marcada, resolver revienta: mejor un error que leer a nadie")
    void sinPlataformaRevienta() {
        laEmpresaTiene(Organizacion.builder().id(EMPRESA).build());
        when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolutor.duenoDe(EMPRESA, Instrumento.BANCO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plataforma");
    }
}
