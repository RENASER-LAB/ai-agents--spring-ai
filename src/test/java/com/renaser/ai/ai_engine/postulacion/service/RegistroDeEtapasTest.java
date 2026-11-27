package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.decision.service.EtapaDeLaDecision;
import com.renaser.ai.ai_engine.perfilintegral.service.EtapaDelPerfilIntegral;
import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import com.renaser.ai.ai_engine.pesos.repository.EtapaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Las piezas de cada etapa, reunidas por código (V95), y la regla que las vigila: una etapa del
 * catálogo sin su pieza no deja arrancar la aplicación (AC-18).
 */
@DisplayName("El registro de las etapas")
class RegistroDeEtapasTest {

    private static Etapa etapa(String codigo, int orden) {
        return Etapa.builder().codigo(codigo).nombre(codigo).orden(orden).descripcion("…").build();
    }

    /** Un contexto con el registro, el catálogo que se le pase y solo las piezas del Perfil y la Decisión. */
    private static ApplicationContextRunner contextoCon(List<Etapa> catalogo) {
        EtapaRepository etapas = mock(EtapaRepository.class);
        when(etapas.findAllByOrderByOrdenAsc()).thenReturn(catalogo);
        return new ApplicationContextRunner()
                .withBean(EtapaRepository.class, () -> etapas)
                .withBean(EtapaDelPerfilIntegral.class)
                .withBean(EtapaDeLaDecision.class)
                .withBean(RegistroDeEtapas.class);
    }

    @Test
    @DisplayName("una etapa del catálogo sin pieza impide arrancar, con un mensaje que la nombra (AC-18)")
    void sinPiezaNoArranca() {
        contextoCon(List.of(etapa("PERFIL_INTEGRAL", 1), etapa("ENTREVISTA", 2), etapa("DECISION", 3)))
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    Throwable raiz = contexto.getStartupFailure();
                    while (raiz.getCause() != null) {
                        raiz = raiz.getCause();
                    }
                    assertThat(raiz)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("ENTREVISTA")
                            .hasMessageContaining("DefinicionDeEtapa");
                });
    }

    @Test
    @DisplayName("con una pieza por etapa del catálogo arranca, y cada etapa se encuentra por su código")
    void conTodasArranca() {
        contextoCon(List.of(etapa("PERFIL_INTEGRAL", 1), etapa("DECISION", 2)))
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    RegistroDeEtapas registro = contexto.getBean(RegistroDeEtapas.class);
                    assertThat(registro.de("DECISION")).containsInstanceOf(EtapaDeLaDecision.class);
                    assertThat(registro.de("ENTREVISTA")).isEmpty();
                    assertThat(registro.entradas()).containsEntry("DECISION", "POR_CONFIRMAR")
                            .doesNotContainKey("PERFIL_INTEGRAL");
                });
    }

    @Test
    @DisplayName("dos piezas para la misma etapa no se aceptan")
    void dosPiezasParaLaMisma() {
        assertThatThrownBy(() -> RegistroDeEtapas.con(List.of(new EtapaDeLaDecision(), new EtapaDeLaDecision())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DECISION");
    }
}
