package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.service.RetratoTerminado;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * El oyente que dispara el pase, y lo único que hace además de delegar: tragarse los errores.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Escuchar que la calificación terminó")
class PaseAutomaticoTrasCalificarTest {

    private static final Long POSTULACION = 12L;

    @Mock private PaseAutomatico pase;

    @InjectMocks private PaseAutomaticoTrasCalificar oyente;

    @Test
    @DisplayName("al terminar el retrato, pide el pase de esa postulación")
    void pideElPase() {
        oyente.alTerminarElRetrato(new RetratoTerminado(POSTULACION));

        verify(pase).avanzarSiToca(POSTULACION);
    }

    @Test
    @DisplayName("un pase que falla no sale de aquí, y por eso no se vuelve a pagar el modelo")
    void elFalloNoSeEscapa() {
        /*
          ⚠️ Esto corre en el hilo del consumidor de la cola.

          Si dejara salir el error, el trabajo se daría por fallido y se reintentaría: se
          volvería a pagar el modelo por un retrato que ya estaba perfecto y guardado, y
          mientras diera vueltas el panel enseñaría «falló» sobre un candidato bien
          calificado. Un pase que no se pudo dar deja la postulación esperando a una persona,
          que es exactamente donde estaba antes de que esto existiera.
        */
        doThrow(new IllegalStateException("la vacante se cerró a mitad"))
                .when(pase).avanzarSiToca(POSTULACION);

        assertThatCode(() -> oyente.alTerminarElRetrato(new RetratoTerminado(POSTULACION)))
                .doesNotThrowAnyException();
    }
}
