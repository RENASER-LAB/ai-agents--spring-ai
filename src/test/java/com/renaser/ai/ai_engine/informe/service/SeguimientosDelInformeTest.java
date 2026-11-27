package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.informe.entity.SeguimientoPendiente;
import com.renaser.ai.ai_engine.informe.repository.SeguimientoPendienteRepository;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Duda;
import com.renaser.ai.ai_engine.informe.service.PendientesEntreEtapas.Resultado;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El seguimiento guardado: solo vale para la huella con que se hizo, pedirlo dos veces no paga dos
 * veces (AC-18) y si la IA está apagada no se anota nada para volver a pedirlo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El seguimiento de pendientes guardado")
class SeguimientosDelInformeTest {

    private static final Long POSTULACION = 10L;
    private static final EtapaDelInforme ETAPA = EtapaDelInforme.EVALUACION;
    private static final Duda RESUELTA = new Duda("D1", "CV", "Excel avanzado", "DECLARADA", "¿Cuál?",
            PendientesEntreEtapas.RESUELTA, 3, null, "armé un tablero", "Lo armó ella.", "EVALUACION");

    @Mock private SeguimientoPendienteRepository seguimientos;
    @Mock private ColaCalificacionIa cola;

    @InjectMocks private SeguimientosDelInforme servicio;

    private void guardado(SeguimientoPendiente fila) {
        when(seguimientos.findByPostulacionIdAndEtapa(POSTULACION, ETAPA.name())).thenReturn(Optional.of(fila));
    }

    @Nested
    @DisplayName("El vigente")
    class Vigente {

        @Test
        @DisplayName("se lee solo si se hizo con la misma huella, con sus dudas, descartes y motivos")
        void conLaMismaHuella() {
            servicio.guardar(POSTULACION, ETAPA, "h1", new Resultado(List.of(RESUELTA), 1, List.of("D9: no existe")), 7L);
            ArgumentCaptor<SeguimientoPendiente> fila = ArgumentCaptor.forClass(SeguimientoPendiente.class);
            verify(seguimientos).save(fila.capture());
            guardado(fila.getValue());

            assertThat(servicio.vigente(POSTULACION, ETAPA, "h2")).isEmpty();
            assertThat(servicio.vigente(POSTULACION, ETAPA, "h1"))
                    .contains(new Resultado(List.of(RESUELTA), 1, List.of("D9: no existe")));
        }

        @Test
        @DisplayName("sin dudas guardadas (solo pedido), o con el JSON roto, no hay vigente; sin motivos ni descartes, vacíos")
        void sinDudasORoto() {
            guardado(SeguimientoPendiente.builder().huella("h1").build());
            assertThat(servicio.vigente(POSTULACION, ETAPA, "h1")).isEmpty();

            guardado(SeguimientoPendiente.builder().huella("h1").dudas("[{roto").build());
            assertThat(servicio.vigente(POSTULACION, ETAPA, "h1")).isEmpty();

            guardado(SeguimientoPendiente.builder().huella("h1").dudas("[]").build());
            assertThat(servicio.vigente(POSTULACION, ETAPA, "h1")).contains(new Resultado(List.of(), 0, List.of()));
        }

        @Test
        @DisplayName("se sabe si una huella ya se pidió, viniera o no")
        void yaPedido() {
            when(seguimientos.findByPostulacionIdAndEtapa(POSTULACION, ETAPA.name())).thenReturn(Optional.empty());
            assertThat(servicio.yaPedido(POSTULACION, ETAPA, "h1")).isFalse();
            guardado(SeguimientoPendiente.builder().huellaPedida("h1").build());
            assertThat(servicio.yaPedido(POSTULACION, ETAPA, "h1")).isTrue();
            assertThat(servicio.yaPedido(POSTULACION, ETAPA, "h2")).isFalse();
        }
    }

    @Nested
    @DisplayName("Pedirlo")
    class Pedirlo {

        @Test
        @DisplayName("la primera vez lo encola y anota la huella pedida")
        void laPrimeraVez() {
            when(seguimientos.findByPostulacionIdAndEtapa(POSTULACION, ETAPA.name())).thenReturn(Optional.empty());
            when(cola.encolarSeguimientoPendientes(POSTULACION, "EVALUACION")).thenReturn(true);

            assertThat(servicio.pedir(POSTULACION, ETAPA, "h1")).isTrue();

            ArgumentCaptor<SeguimientoPendiente> fila = ArgumentCaptor.forClass(SeguimientoPendiente.class);
            verify(seguimientos).saveAndFlush(fila.capture());
            assertThat(fila.getValue().getHuellaPedida()).isEqualTo("h1");
            assertThat(fila.getValue().getPedidaEn()).isNotNull();
            assertThat(fila.getValue().getEtapa()).isEqualTo("EVALUACION");
            assertThat(fila.getValue().getDescartados()).isZero();
        }

        @Test
        @DisplayName("AC-18 · la misma huella no se vuelve a encolar: contesta si viene en camino")
        void laMismaHuella() {
            guardado(SeguimientoPendiente.builder().huellaPedida("h1").build());
            when(cola.seguimientoPendientesVivo(POSTULACION, "EVALUACION")).thenReturn(true);

            assertThat(servicio.pedir(POSTULACION, ETAPA, "h1")).isTrue();

            verify(cola, never()).encolarSeguimientoPendientes(any(), any());
            verify(seguimientos, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("con la IA apagada no se anota nada, para pedirlo la próxima vez")
        void conLaIaApagada() {
            guardado(SeguimientoPendiente.builder().huellaPedida("h0").build());
            when(cola.encolarSeguimientoPendientes(POSTULACION, "EVALUACION")).thenReturn(false);
            when(cola.seguimientoPendientesVivo(POSTULACION, "EVALUACION")).thenReturn(false);

            assertThat(servicio.pedir(POSTULACION, ETAPA, "h1")).isFalse();
            verify(seguimientos, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("si otra petición lo creó a la vez, lo pedido ya está pedido")
        void carrera() {
            when(seguimientos.findByPostulacionIdAndEtapa(POSTULACION, ETAPA.name())).thenReturn(Optional.empty());
            when(cola.encolarSeguimientoPendientes(POSTULACION, "EVALUACION")).thenReturn(false);
            when(cola.seguimientoPendientesVivo(POSTULACION, "EVALUACION")).thenReturn(true);
            when(seguimientos.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicada"));

            assertThat(servicio.pedir(POSTULACION, ETAPA, "h1")).isTrue();
        }
    }

    @Test
    @DisplayName("guardar sobre una fila pedida conserva la huella pedida y anota la ejecución")
    void guardarSobreLaPedida() {
        guardado(SeguimientoPendiente.builder().id(3L).postulacionId(POSTULACION).etapa("EVALUACION")
                .huellaPedida("h0").build());

        servicio.guardar(POSTULACION, ETAPA, "h1", new Resultado(List.of(RESUELTA), 0, List.of()), null);

        ArgumentCaptor<SeguimientoPendiente> fila = ArgumentCaptor.forClass(SeguimientoPendiente.class);
        verify(seguimientos).save(fila.capture());
        assertThat(fila.getValue().getHuella()).isEqualTo("h1");
        assertThat(fila.getValue().getHuellaPedida()).isEqualTo("h0");
        assertThat(fila.getValue().getEjecucionIaId()).isNull();
        assertThat(fila.getValue().getEscritaEn()).isNotNull();
        assertThat(fila.getValue().getMotivos()).isEqualTo("[]");
    }
}
