package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.informe.entity.TextoInforme;
import com.renaser.ai.ai_engine.informe.repository.TextoInformeRepository;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Textos;

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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los textos del redactor: solo valen para la huella con la que se escribieron, pedirlos dos
 * veces no paga dos veces, y si la IA está apagada no se anota nada para volver a pedirlos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Los textos del redactor del informe")
class TextosDelInformeTest {

    private static final Long POSTULACION = 10L;
    private static final EtapaDelInforme ETAPA = EtapaDelInforme.EVALUACION;

    @Mock private TextoInformeRepository textos;
    @Mock private ColaCalificacionIa cola;

    @InjectMocks private TextosDelInforme servicio;

    @Nested
    @DisplayName("Los vigentes")
    class Vigentes {

        @Test
        @DisplayName("se leen solo si se escribieron con la misma huella")
        void conLaMismaHuella() {
            guardado(TextoInforme.builder().huella("h1").resumen("Bien.")
                    .criterios("{\"Orden\":\"Ordena bien.\"}").preguntas("[\"Lo hizo.\",null]").descartados(2).build());

            assertThat(servicio.vigentes(POSTULACION, ETAPA, "h2")).isEmpty();
            assertThat(servicio.vigentes(POSTULACION, ETAPA, "h1")).contains(
                    new Textos("Bien.", Map.of("Orden", "Ordena bien."), java.util.Arrays.asList("Lo hizo.", null), 2));
        }

        @Test
        @DisplayName("sin criterios, preguntas ni descartados guardados salen vacíos y con cero")
        void vacios() {
            guardado(TextoInforme.builder().huella("h1").build());
            assertThat(servicio.vigentes(POSTULACION, ETAPA, "h1"))
                    .contains(new Textos(null, Map.of(), List.of(), 0));
        }

        @Test
        @DisplayName("unos textos guardados que no se pueden leer no rompen el informe: valen como ninguno")
        void rotos() {
            guardado(TextoInforme.builder().huella("h1").resumen("Bien.").criterios("{roto").build());
            assertThat(servicio.vigentes(POSTULACION, ETAPA, "h1"))
                    .contains(new Textos(null, Map.of(), List.of(), 0));
        }
    }

    @Nested
    @DisplayName("Pedirlos")
    class Pedir {

        @Test
        @DisplayName("una huella ya pedida no se vuelve a pagar: solo dice si vienen en camino")
        void yaPedida() {
            guardado(TextoInforme.builder().huellaPedida("h1").build());
            when(cola.redactorDelInformeVivo(POSTULACION, "EVALUACION")).thenReturn(true);

            assertThat(servicio.pedir(POSTULACION, ETAPA, "h1")).isTrue();
            verify(cola, never()).encolarRedactorInforme(any(), any());
        }

        @Test
        @DisplayName("una huella nueva se encola y se anota en una fila nueva")
        void nueva() {
            when(cola.encolarRedactorInforme(POSTULACION, "EVALUACION")).thenReturn(true);

            assertThat(servicio.pedir(POSTULACION, ETAPA, "h2")).isTrue();

            ArgumentCaptor<TextoInforme> fila = ArgumentCaptor.forClass(TextoInforme.class);
            verify(textos).saveAndFlush(fila.capture());
            assertThat(fila.getValue().getHuellaPedida()).isEqualTo("h2");
            assertThat(fila.getValue().getEtapa()).isEqualTo("EVALUACION");
            assertThat(fila.getValue().getPedidaEn()).isNotNull();
        }

        @Test
        @DisplayName("con la IA apagada no se anota nada, para pedirlo la próxima vez")
        void conLaIaApagada() {
            assertThat(servicio.pedir(POSTULACION, ETAPA, "h2")).isFalse();
            verify(textos, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("si no se encoló porque ya había uno vivo, sí se anota")
        void yaHabiaUnoVivo() {
            guardado(TextoInforme.builder().huellaPedida("h1").build());
            when(cola.redactorDelInformeVivo(POSTULACION, "EVALUACION")).thenReturn(true);

            assertThat(servicio.pedir(POSTULACION, ETAPA, "h2")).isTrue();
            verify(textos).saveAndFlush(any());
        }

        @Test
        @DisplayName("si otra petición creó la fila a la vez, lo pedido ya está pedido")
        void carrera() {
            when(cola.encolarRedactorInforme(POSTULACION, "EVALUACION")).thenReturn(true);
            when(textos.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicada"));

            assertThat(servicio.pedir(POSTULACION, ETAPA, "h2")).isTrue();
        }
    }

    @Nested
    @DisplayName("Guardarlos")
    class Guardar {

        @Test
        @DisplayName("en una fila nueva quedan atados a su huella y la cuentan como pedida")
        void filaNueva() {
            servicio.guardar(POSTULACION, ETAPA, "h3", new Textos("Resumen.", Map.of("Orden", "Bien."),
                    List.of("Lo hizo."), 1), 77L);

            ArgumentCaptor<TextoInforme> fila = ArgumentCaptor.forClass(TextoInforme.class);
            verify(textos).save(fila.capture());
            TextoInforme guardada = fila.getValue();
            assertThat(guardada.getHuella()).isEqualTo("h3");
            assertThat(guardada.getHuellaPedida()).isEqualTo("h3");
            assertThat(guardada.getResumen()).isEqualTo("Resumen.");
            assertThat(guardada.getCriterios()).isEqualTo("{\"Orden\":\"Bien.\"}");
            assertThat(guardada.getPreguntas()).isEqualTo("[\"Lo hizo.\"]");
            assertThat(guardada.getDescartados()).isEqualTo(1);
            assertThat(guardada.getEjecucionIaId()).isEqualTo(77L);
            assertThat(guardada.getEscritaEn()).isNotNull();
        }

        @Test
        @DisplayName("en una fila ya pedida conservan la huella que se pidió")
        void filaPedida() {
            guardado(TextoInforme.builder().huellaPedida("h2").build());

            servicio.guardar(POSTULACION, ETAPA, "h3", new Textos(null, Map.of(), List.of(), 0), null);

            ArgumentCaptor<TextoInforme> fila = ArgumentCaptor.forClass(TextoInforme.class);
            verify(textos).save(fila.capture());
            assertThat(fila.getValue().getHuella()).isEqualTo("h3");
            assertThat(fila.getValue().getHuellaPedida()).isEqualTo("h2");
        }
    }

    private void guardado(TextoInforme fila) {
        fila.setPostulacionId(POSTULACION);
        fila.setEtapa(ETAPA.name());
        when(textos.findByPostulacionIdAndEtapa(POSTULACION, ETAPA.name())).thenReturn(Optional.of(fila));
    }
}
