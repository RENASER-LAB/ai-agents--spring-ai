package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los cambios de estado de la cola, que son los que impiden calificar a alguien dos veces.
 *
 * <p>Lo importante está en {@code tomar}: la condición y la escritura viajan juntas en un
 * solo UPDATE. Leer y luego escribir no vale con ocho consumidores —dos pueden leer
 * «PENDIENTE» antes de que ninguno haya escrito— y el resultado sería pagar dos llamadas al
 * modelo por el mismo candidato y guardar la nota del que termine último.
 */
@ExtendWith(MockitoExtension.class)
class RegistroTrabajosIaTest {

    @Mock
    private TrabajoIaRepository trabajos;

    @InjectMocks
    private RegistroTrabajosIa registro;

    @Test
    void loTomaSoloSiLaBaseDiceQueLoTomoEste() {
        TrabajoIa suyo = trabajo(9L, "EN_CURSO", 1);
        when(trabajos.tomarSiEstaPendiente(eq(9L), any(Instant.class))).thenReturn(1);
        when(trabajos.findById(9L)).thenReturn(Optional.of(suyo));

        assertThat(registro.tomar(9L)).contains(suyo);
    }

    @Test
    void siOtroSeAdelantoNiSiquieraSeLeeElTrabajo() {
        // Cero filas cambiadas significa que otro consumidor ya lo tiene, o que ya terminó.
        // Es lo que hace que un mensaje entregado dos veces no califique dos veces.
        when(trabajos.tomarSiEstaPendiente(eq(9L), any(Instant.class))).thenReturn(0);

        assertThat(registro.tomar(9L)).isEmpty();
        verify(trabajos, never()).findById(anyLong());
    }

    @Test
    void terminarDejaLaHoraParaPoderVerCuantoTardo() {
        TrabajoIa suyo = trabajo(9L, "EN_CURSO", 1);
        when(trabajos.findById(9L)).thenReturn(Optional.of(suyo));

        registro.terminar(9L);

        assertThat(suyo.getEstado()).isEqualTo("TERMINADO");
        assertThat(suyo.getTerminadoEn()).isNotNull();
        verify(trabajos).save(suyo);
    }

    @Test
    void mientrasQuedeIntentoElTrabajoVuelveAPendiente() {
        TrabajoIa suyo = trabajo(9L, "EN_CURSO", 1);
        when(trabajos.findById(9L)).thenReturn(Optional.of(suyo));

        assertThat(registro.fallar(9L, 3, "el proveedor no responde")).isTrue();

        assertThat(suyo.getEstado()).isEqualTo("PENDIENTE");
        // Sin borrar la hora en que lo tomaron, el vigilante de atascados lo vería como un
        // EN_CURSO colgado y lo devolvería otra vez: el mismo trabajo contado dos veces.
        assertThat(suyo.getTomadoEn()).isNull();
        assertThat(suyo.getTerminadoEn()).isNull();
    }

    @Test
    void agotadosLosIntentosSeMarcaFallidoYNoSeInventaNingunaNota() {
        TrabajoIa suyo = trabajo(9L, "EN_CURSO", 3);
        when(trabajos.findById(9L)).thenReturn(Optional.of(suyo));

        assertThat(registro.fallar(9L, 3, "el proveedor no responde")).isFalse();

        assertThat(suyo.getEstado()).isEqualTo("FALLIDO");
        assertThat(suyo.getTerminadoEn()).isNotNull();
    }

    @Test
    void unTrabajoQueYaNoExisteNoSeReintenta() {
        // Pasaría si alguien borró la fila a mano mientras corría. Devolver «reintenta»
        // dejaría al llamador publicando un aviso por un id que no lleva a ninguna parte.
        when(trabajos.findById(9L)).thenReturn(Optional.empty());

        assertThat(registro.fallar(9L, 3, "da igual")).isFalse();
    }

    @Test
    void unTrabajoColgadoVuelveAPendienteYUnoQueYaTerminoNoSeToca() {
        TrabajoIa colgado = trabajo(9L, "EN_CURSO", 1);
        when(trabajos.findById(9L)).thenReturn(Optional.of(colgado));
        registro.devolverAPendiente(9L);
        assertThat(colgado.getEstado()).isEqualTo("PENDIENTE");
        assertThat(colgado.getTomadoEn()).isNull();

        // El vigilante mira por hora, no por estado actual: si entre que lee y que escribe
        // el trabajo acabó, devolverlo a la cola lo haría correr —y pagarse— dos veces.
        TrabajoIa yaHecho = trabajo(10L, "TERMINADO", 1);
        when(trabajos.findById(10L)).thenReturn(Optional.of(yaHecho));
        registro.devolverAPendiente(10L);
        assertThat(yaHecho.getEstado()).isEqualTo("TERMINADO");
        verify(trabajos, never()).save(yaHecho);
    }

    @Test
    void laBusquedaDeDuplicadosIncluyeElModo() {
        // Sin el modo, la pasada fina encontraría el trabajo que ya hizo la rápida y no
        // correría nunca: justo lo contrario de lo que se pide al pulsar el botón.
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "EVIDENCIA_CV", "FINA")).thenReturn(Optional.empty());
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(registro.crearSiHaceFalta(1L, 55L, "EVIDENCIA_CV", "FINA")).isPresent();

        ArgumentCaptor<TrabajoIa> creado = ArgumentCaptor.forClass(TrabajoIa.class);
        verify(trabajos).save(creado.capture());
        assertThat(creado.getValue().getEstado()).isEqualTo("PENDIENTE");
        assertThat(creado.getValue().getModo()).isEqualTo("FINA");
        assertThat(creado.getValue().getIntentos()).isZero();
    }

    @Test
    void loQueYaEstaHechoOEnMarchaNoSeVuelveACrear() {
        // Es lo que hace que pedir la criba dos veces no duplique nada.
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "EVIDENCIA_CV", "FINA"))
                .thenReturn(Optional.of(trabajo(9L, "TERMINADO", 1)));

        assertThat(registro.crearSiHaceFalta(1L, 55L, "EVIDENCIA_CV", "FINA")).isEmpty();
        verify(trabajos, never()).save(any(TrabajoIa.class));
    }

    @Test
    void loQueFalloSiSePuedeVolverAIntentar() {
        // Es lo que permite reencolar a mano una postulación que se quedó colgada porque el
        // proveedor del modelo estuvo caído.
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "EVIDENCIA_CV", "FINA"))
                .thenReturn(Optional.of(trabajo(9L, "FALLIDO", 3)));
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(registro.crearSiHaceFalta(1L, 55L, "EVIDENCIA_CV", "FINA")).isPresent();
    }

    private TrabajoIa trabajo(Long id, String estado, int intentos) {
        return TrabajoIa.builder()
                .id(id)
                .postulacionId(55L)
                .agenteCodigo("EVIDENCIA_CV")
                .modo("FINA")
                .estado(estado)
                .intentos(intentos)
                .tomadoEn(Instant.now())
                .build();
    }
}
