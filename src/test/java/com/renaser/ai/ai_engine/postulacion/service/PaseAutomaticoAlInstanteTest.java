package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.service.TurnoDelPerfilCumplido;
import com.renaser.ai.ai_engine.perfilintegral.service.TurnoDelPerfilCumplido.Momento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** El oyente del pase al instante (V70): delega con el motivo que toca y se traga los errores. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Escuchar que el candidato hizo lo suyo")
class PaseAutomaticoAlInstanteTest {

    private static final Long POSTULACION = 21L;

    @Mock private PaseAutomatico pase;

    @InjectMocks private PaseAutomaticoAlInstante oyente;

    @Test
    @DisplayName("al entregar el banco pide el pase con el motivo de la entrega")
    void alEntregar() {
        oyente.alCumplirElPerfil(new TurnoDelPerfilCumplido(POSTULACION, Momento.AL_ENTREGAR));

        verify(pase).alInstante(POSTULACION, "Pase automático al entregar: la nota se calcula después");
    }

    @Test
    @DisplayName("al postular sin banco pide el pase con el motivo de la postulación")
    void alPostular() {
        oyente.alCumplirElPerfil(new TurnoDelPerfilCumplido(POSTULACION, Momento.AL_POSTULAR));

        verify(pase).alInstante(POSTULACION, "Pase automático al postular: la nota se calcula después");
    }

    @Test
    @DisplayName("un pase que falla no le devuelve un error al candidato que ya entregó bien")
    void elFalloNoSeEscapa() {
        doThrow(new IllegalStateException("la prueba no se pudo crear"))
                .when(pase).alInstante(POSTULACION, Momento.AL_ENTREGAR.motivo());

        assertThatCode(() -> oyente.alCumplirElPerfil(
                new TurnoDelPerfilCumplido(POSTULACION, Momento.AL_ENTREGAR)))
                .doesNotThrowAnyException();
    }
}
