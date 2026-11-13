package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("El lugar que separa dos vacantes con el mismo título («Pruebas»)")
class LugarDeLaVacanteTest {

    private static final Map<String, OpcionUbigeo> CIUDADES = Map.of(
            "1501", new OpcionUbigeo("1501", "Lima", "Lima"),
            "0401", new OpcionUbigeo("0401", "Arequipa", "Arequipa"));

    @Test
    @DisplayName("Zona y ciudad juntas; solo la que haya; «Lima, Lima» se dice una vez")
    void zonaYCiudad() {
        assertThat(LugarDeLaVacante.de(Vacante.builder().ubicacion(" San Isidro ").ciudadUbigeo("1501").build(),
                CIUDADES)).isEqualTo("San Isidro, Lima");
        assertThat(LugarDeLaVacante.de(Vacante.builder().ciudadUbigeo("0401").build(), CIUDADES))
                .isEqualTo("Arequipa");
        assertThat(LugarDeLaVacante.de(Vacante.builder().ubicacion("Cayma").build(), CIUDADES))
                .isEqualTo("Cayma");
        assertThat(LugarDeLaVacante.de(Vacante.builder().ubicacion("lima").ciudadUbigeo("1501").build(),
                CIUDADES)).isEqualTo("lima");
    }

    @Test
    @DisplayName("Sin zona ni ciudad: «Remoto» si lo es y nada si no; una ciudad que el catálogo no conoce va por su código")
    void sinLugar() {
        assertThat(LugarDeLaVacante.de(Vacante.builder().modalidad("Remoto").build(), CIUDADES)).isEqualTo("Remoto");
        assertThat(LugarDeLaVacante.de(Vacante.builder().modalidad("Presencial").ubicacion("  ").build(), CIUDADES))
                .isNull();
        assertThat(LugarDeLaVacante.de(Vacante.builder().ciudadUbigeo("9999").build(), CIUDADES)).isEqualTo("9999");
    }

    @Test
    @DisplayName("El catálogo se pide una vez por lista, y ninguna si ninguna vacante tiene ciudad")
    void elCatalogoUnaVez() {
        CatalogosDelPerfil catalogos = mock(CatalogosDelPerfil.class);
        when(catalogos.ciudadesPorCodigo()).thenReturn(CIUDADES);
        LugarDeLaVacante lugares = new LugarDeLaVacante(catalogos);

        Vacante remota = Vacante.builder().modalidad("Remoto").build();
        assertThat(lugares.paraLas(List.of(remota)).apply(remota)).isEqualTo("Remoto");
        verify(catalogos, never()).ciudadesPorCodigo();

        Vacante enLima = Vacante.builder().ciudadUbigeo("1501").build();
        assertThat(lugares.paraLas(List.of(remota, enLima)).apply(enLima)).isEqualTo("Lima");
        verify(catalogos).ciudadesPorCodigo();
    }
}
