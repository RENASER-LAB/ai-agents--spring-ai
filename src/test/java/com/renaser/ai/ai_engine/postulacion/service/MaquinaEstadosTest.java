package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// El cálculo del estado siguiente, contra el catálogo real de 18 estados.
// Es lógica pura: sin Spring, sin base de datos.
@DisplayName("La máquina de estados de una postulación")
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

    // ============ El recorrido de cada vacante (V95) ============

    @Test
    @DisplayName("Perfil → Decisión: del «por confirmar» del Perfil se entra a «Decisión · por confirmar» (AC-07)")
    void perfilYDecision() {
        List<String> corto = List.of("PERFIL_INTEGRAL", "DECISION");
        assertThat(siguienteEn(corto, "PERFIL_POR_CONFIRMAR")).isEqualTo("DECISION_POR_CONFIRMAR");
        assertThat(siguienteEn(corto, "PERFIL_CALIFICANDO")).isEqualTo("PERFIL_POR_CONFIRMAR");
    }

    @Test
    @DisplayName("Perfil → Simulación → Prueba → Decisión: tras el Perfil, la Simulación; tras ella, la Prueba (AC-08)")
    void simulacionAntesDeLaPrueba() {
        List<String> reordenado = List.of("PERFIL_INTEGRAL", "SIMULACION", "PRUEBA_PUESTO", "DECISION");
        assertThat(siguienteEn(reordenado, "PERFIL_POR_CONFIRMAR")).isEqualTo("SIMULACION_POR_HABILITAR");
        assertThat(siguienteEn(reordenado, "SIMULACION_POR_CONFIRMAR")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(siguienteEn(reordenado, "PRUEBA_POR_CONFIRMAR")).isEqualTo("DECISION_POR_CONFIRMAR");
    }

    @Test
    @DisplayName("Con la Validación al final, de su «por confirmar» no se avanza (AC-09)")
    void laValidacionAlFinal() {
        List<String> sinDecision = List.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "VALIDACION");
        assertThat(siguienteEn(sinDecision, "VALIDACION_TURNO_CANDIDATO")).isEqualTo("VALIDACION_POR_CONFIRMAR");
        assertThat(siguienteEn(sinDecision, "VALIDACION_POR_CONFIRMAR")).isEqualTo("—");
    }

    @Test
    @DisplayName("Perfil → Validación → Prueba → Decisión: tras la Validación viene la Prueba (AC-10)")
    void laValidacionAntesDeLaPrueba() {
        List<String> recorrido = List.of("PERFIL_INTEGRAL", "VALIDACION", "PRUEBA_PUESTO", "DECISION");
        assertThat(siguienteEn(recorrido, "PERFIL_POR_CONFIRMAR")).isEqualTo("VALIDACION_POR_HABILITAR");
        assertThat(siguienteEn(recorrido, "VALIDACION_POR_CONFIRMAR")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
    }

    @Test
    @DisplayName("El Perfil solo: de su «por confirmar» no se avanza a ninguna parte")
    void soloElPerfil() {
        assertThat(siguienteEn(List.of("PERFIL_INTEGRAL"), "PERFIL_POR_CONFIRMAR")).isEqualTo("—");
        assertThat(siguienteEn(List.of("PERFIL_INTEGRAL"), "POSTULADA")).isEqualTo("PERFIL_TURNO_CANDIDATO");
    }

    @Test
    @DisplayName("Un estado de una etapa que el recorrido no tiene no se inventa a dónde ir")
    void unaEtapaFueraDelRecorrido() {
        List<String> sinPrueba = List.of("PERFIL_INTEGRAL", "DECISION");
        assertThat(siguienteEn(sinPrueba, "PRUEBA_POR_CONFIRMAR")).isEqualTo("—");
    }

    @Test
    @DisplayName("El momento de entrada sale del recorrido, no de un nombre de etapa escrito en la máquina")
    void elMomentoDeEntradaSaleDelRecorrido() {
        // Sin momento propio, una etapa se entra por el primero de los suyos: el turno.
        Recorrido sinEntradas = new Recorrido(RecorridosDePrueba.CINCO, java.util.Map.of());
        assertThat(calcular(sinEntradas, "VALIDACION_POR_CONFIRMAR").orElseThrow().getCodigo())
                .isEqualTo("DECISION_TURNO_CANDIDATO");
        // Con el de su pieza, por «por confirmar».
        assertThat(calcular(RecorridosDePrueba.deSiempre(), "VALIDACION_POR_CONFIRMAR")
                .orElseThrow().getCodigo()).isEqualTo("DECISION_POR_CONFIRMAR");
    }

    /**
     * Una etapa que solo existe en esta prueba (AC-19): con sus estados en la rejilla y en el
     * recorrido, la máquina entra, avanza por sus momentos y sale de ella sin saber su nombre.
     */
    @Test
    @DisplayName("Una etapa ficticia del recorrido se entra, se recorre y se deja sin código propio (AC-19)")
    void unaEtapaFicticia() {
        List<EstadoPostulacion> conEntrevista = new java.util.ArrayList<>(catalogo);
        conEntrevista.add(estado("ENTREVISTA_TURNO_CANDIDATO", "ENTREVISTA", "TURNO_CANDIDATO", "CANDIDATO", 30, false));
        conEntrevista.add(estado("ENTREVISTA_POR_CONFIRMAR", "ENTREVISTA", "POR_CONFIRMAR", "TALENTO", 31, false));
        Recorrido recorrido = RecorridosDePrueba.de(List.of("PERFIL_INTEGRAL", "ENTREVISTA", "DECISION"));

        assertThat(siguiente(recorrido, conEntrevista, "PERFIL_POR_CONFIRMAR")).isEqualTo("ENTREVISTA_TURNO_CANDIDATO");
        assertThat(siguiente(recorrido, conEntrevista, "ENTREVISTA_TURNO_CANDIDATO")).isEqualTo("ENTREVISTA_POR_CONFIRMAR");
        assertThat(siguiente(recorrido, conEntrevista, "ENTREVISTA_POR_CONFIRMAR")).isEqualTo("DECISION_POR_CONFIRMAR");
    }

    private static String siguiente(Recorrido recorrido, List<EstadoPostulacion> catalogo, String codigo) {
        EstadoPostulacion actual = catalogo.stream()
                .filter(e -> e.getCodigo().equals(codigo)).findFirst().orElseThrow();
        return MaquinaEstados.calcularSiguiente(recorrido, catalogo, actual).orElseThrow().getCodigo();
    }

    private String siguienteDe(String codigo) {
        return calcular(codigo).orElseThrow().getCodigo();
    }

    private Optional<EstadoPostulacion> calcular(String codigo) {
        return calcular(RecorridosDePrueba.deSiempre(), codigo);
    }

    private Optional<EstadoPostulacion> calcular(Recorrido recorrido, String codigo) {
        EstadoPostulacion actual = catalogo.stream()
                .filter(e -> e.getCodigo().equals(codigo)).findFirst().orElseThrow();
        return MaquinaEstados.calcularSiguiente(recorrido, catalogo, actual);
    }

    private String siguienteEn(List<String> etapas, String codigo) {
        return calcular(RecorridosDePrueba.de(etapas), codigo).map(EstadoPostulacion::getCodigo)
                .orElse("—");
    }

    private static EstadoPostulacion estado(String codigo, String etapa, String momento,
                                            String esperaA, int orden, boolean esFinal) {
        return EstadoPostulacion.builder()
                .codigo(codigo).etapaCodigo(etapa).momentoCodigo(momento)
                .esperaA(esperaA).orden(orden).esFinal(esFinal)
                .build();
    }
}
