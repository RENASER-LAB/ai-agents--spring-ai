package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// El cálculo del estado siguiente, contra el catálogo real de 18 estados.
// Es lógica pura: sin Spring, sin base de datos.
class MaquinaEstadosTest {

    // El mismo catálogo que siembra V9__semillas.sql
    private final List<EstadoPostulacion> catalogo = List.of(
            estado("POSTULADA", null, null, "SISTEMA", 1, false),
            estado("PERFIL_TURNO_CANDIDATO", "PERFIL_INTEGRAL", "TURNO_CANDIDATO", "CANDIDATO", 2, false),
            estado("PERFIL_CALIFICANDO", "PERFIL_INTEGRAL", "CALIFICANDO", "SISTEMA", 3, false),
            estado("PERFIL_POR_CONFIRMAR", "PERFIL_INTEGRAL", "POR_CONFIRMAR", "TALENTO", 4, false),
            estado("PRUEBA_TURNO_CANDIDATO", "PRUEBA_PUESTO", "TURNO_CANDIDATO", "CANDIDATO", 5, false),
            estado("PRUEBA_CALIFICANDO", "PRUEBA_PUESTO", "CALIFICANDO", "SISTEMA", 6, false),
            estado("PRUEBA_POR_CONFIRMAR", "PRUEBA_PUESTO", "POR_CONFIRMAR", "TALENTO", 7, false),
            estado("SIMULACION_POR_HABILITAR", "SIMULACION", "POR_HABILITAR", "TALENTO", 8, false),
            estado("SIMULACION_TURNO_CANDIDATO", "SIMULACION", "TURNO_CANDIDATO", "CANDIDATO", 9, false),
            estado("SIMULACION_POR_CONFIRMAR", "SIMULACION", "POR_CONFIRMAR", "TALENTO", 10, false),
            estado("VALIDACION_POR_HABILITAR", "VALIDACION", "POR_HABILITAR", "TALENTO", 11, false),
            estado("VALIDACION_TURNO_CANDIDATO", "VALIDACION", "TURNO_CANDIDATO", "CANDIDATO", 12, false),
            estado("VALIDACION_POR_CONFIRMAR", "VALIDACION", "POR_CONFIRMAR", "AREA", 13, false),
            estado("DECISION_POR_CONFIRMAR", "DECISION", "POR_CONFIRMAR", "AREA", 14, false),
            estado("DECISION_TURNO_CANDIDATO", "DECISION", "TURNO_CANDIDATO", "CANDIDATO", 15, false),
            estado("CONTRATADO", null, null, "NADIE", 16, true),
            estado("NO_CONTINUA", null, null, "NADIE", 17, true),
            estado("CERRADA", null, null, "NADIE", 18, true));

    @Test
    void elRecorridoNormalCompleto() {
        // Cada estado del camino normal lleva exactamente al siguiente
        assertThat(siguienteDe("POSTULADA")).isEqualTo("PERFIL_TURNO_CANDIDATO");
        assertThat(siguienteDe("PERFIL_TURNO_CANDIDATO")).isEqualTo("PERFIL_CALIFICANDO");
        assertThat(siguienteDe("PERFIL_CALIFICANDO")).isEqualTo("PERFIL_POR_CONFIRMAR");
        assertThat(siguienteDe("PERFIL_POR_CONFIRMAR")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(siguienteDe("PRUEBA_TURNO_CANDIDATO")).isEqualTo("PRUEBA_CALIFICANDO");
        assertThat(siguienteDe("PRUEBA_CALIFICANDO")).isEqualTo("PRUEBA_POR_CONFIRMAR");
        assertThat(siguienteDe("PRUEBA_POR_CONFIRMAR")).isEqualTo("SIMULACION_POR_HABILITAR");
        assertThat(siguienteDe("SIMULACION_POR_HABILITAR")).isEqualTo("SIMULACION_TURNO_CANDIDATO");
        assertThat(siguienteDe("SIMULACION_TURNO_CANDIDATO")).isEqualTo("SIMULACION_POR_CONFIRMAR");
        assertThat(siguienteDe("SIMULACION_POR_CONFIRMAR")).isEqualTo("VALIDACION_POR_HABILITAR");
        assertThat(siguienteDe("VALIDACION_POR_HABILITAR")).isEqualTo("VALIDACION_TURNO_CANDIDATO");
        assertThat(siguienteDe("VALIDACION_TURNO_CANDIDATO")).isEqualTo("VALIDACION_POR_CONFIRMAR");
    }

    @Test
    void laDecisionSeEntraPorPorConfirmarNoPorSuPrimerMomento() {
        // La única excepción al cálculo: el TURNO_CANDIDATO de la decisión existe solo
        // para el ámbar, no es la entrada de la etapa
        assertThat(siguienteDe("VALIDACION_POR_CONFIRMAR")).isEqualTo("DECISION_POR_CONFIRMAR");
    }

    @Test
    void elAmbarVuelveAPorConfirmar() {
        // Cuando el candidato entrega la evidencia, vuelve a manos del área
        assertThat(siguienteDe("DECISION_TURNO_CANDIDATO")).isEqualTo("DECISION_POR_CONFIRMAR");
    }

    @Test
    void salirDeLaDecisionNoEsUnAvance() {
        // Contratar, rechazar o reservar son decisiones de una persona con motivo,
        // no el resultado de calcular un siguiente
        assertThat(calcular("DECISION_POR_CONFIRMAR")).isEmpty();
    }

    @Test
    void losFinalesNoTienenSiguiente() {
        assertThat(calcular("CONTRATADO")).isEmpty();
        assertThat(calcular("NO_CONTINUA")).isEmpty();
        assertThat(calcular("CERRADA")).isEmpty();
    }

    private String siguienteDe(String codigo) {
        return calcular(codigo).orElseThrow().getCodigo();
    }

    private Optional<EstadoPostulacion> calcular(String codigo) {
        EstadoPostulacion actual = catalogo.stream()
                .filter(e -> e.getCodigo().equals(codigo)).findFirst().orElseThrow();
        return MaquinaEstados.calcularSiguiente(catalogo, actual);
    }

    private static EstadoPostulacion estado(String codigo, String etapa, String momento,
                                            String esperaA, int orden, boolean esFinal) {
        return EstadoPostulacion.builder()
                .codigo(codigo).etapaCodigo(etapa).momentoCodigo(momento)
                .esperaA(esperaA).orden(orden).esFinal(esFinal)
                .build();
    }
}
