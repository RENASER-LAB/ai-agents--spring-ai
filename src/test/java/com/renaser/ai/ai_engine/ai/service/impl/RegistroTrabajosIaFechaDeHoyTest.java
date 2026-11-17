package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.repository.EjecucionIaRepository;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Quién se calificó ya con la fecha de hoy (spec la-ia-sabe-que-dia-es-hoy): se busca la regla
 * del bloque en el envío de cada ejecución, sin traer el envío entero.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El registro: las ejecuciones que ya llevaban la fecha de hoy")
class RegistroTrabajosIaFechaDeHoyTest {

    @Mock private TrabajoIaRepository trabajos;
    @Mock private EjecucionIaRepository ejecuciones;

    @Test
    @DisplayName("busca la regla del bloque en el envío y devuelve los ids que la llevan; sin ids no pregunta")
    void buscaLaRegla() {
        RegistroTrabajosIa registro = new RegistroTrabajosIa(trabajos, ejecuciones);
        when(ejecuciones.idsConEnvioQueLleva(Set.of(1L, 2L), FechaDeHoyParaLaIa.REGLA)).thenReturn(List.of(2L));

        assertThat(registro.conLaFechaDeHoy(Set.of(1L, 2L))).containsExactly(2L);
        assertThat(registro.conLaFechaDeHoy(Set.of())).isEmpty();
        assertThat(registro.conLaFechaDeHoy(null)).isEmpty();
        verify(ejecuciones, never()).idsConEnvioQueLleva(Set.of(), FechaDeHoyParaLaIa.REGLA);
        verify(ejecuciones).idsConEnvioQueLleva(any(), any());
    }

    @Test
    @DisplayName("la regla que se busca es la que va en todos los bloques")
    void laReglaVaEnElBloque() {
        assertThat(new FechaDeHoyParaLaIa().bloque()).contains(FechaDeHoyParaLaIa.REGLA);
    }
}
