package com.renaser.ai.ai_engine.organizacion.controller;

import com.renaser.ai.ai_engine.organizacion.service.ServicioPersonalizacion;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPlataforma;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.ListaDeAfectados;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.TandaDeRecalificacion;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioRecalificacionDelCv;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlataformaControllerRecalificacionTest {

    private final ServicioRecalificacionDelCv recalificacion = mock(ServicioRecalificacionDelCv.class);
    private final Permisos permisos = mock(Permisos.class);
    private final ContextoUsuario quien = mock(ContextoUsuario.class);
    private final PlataformaController controlador = new PlataformaController(
            mock(ServicioPlataforma.class), mock(ServicioPersonalizacion.class), recalificacion, permisos);

    @Test
    @DisplayName("las vacantes pedidas llegan al servicio tal cual, en la lista y en la tanda")
    void pasaLasVacantes() {
        when(permisos.actual()).thenReturn(quien);
        ListaDeAfectados lista = mock(ListaDeAfectados.class);
        TandaDeRecalificacion tanda = mock(TandaDeRecalificacion.class);
        when(recalificacion.afectados(quien, Set.of(29L, 27L))).thenReturn(lista);
        when(recalificacion.recalificar(quien, null)).thenReturn(tanda);

        assertThat(controlador.afectadosPorLaFecha(Set.of(29L, 27L))).isSameAs(lista);
        assertThat(controlador.recalificarAfectadosPorLaFecha(null)).isSameAs(tanda);
    }
}
