package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recorridos para las pruebas de la máquina de estados y del avance (V95).
 *
 * <p>{@link #CINCO} es el de siempre: el que tienen todas las vacantes de antes de la V95 y el que
 * siguen probando los flujos de cinco etapas.
 */
public final class RecorridosDePrueba {

    public static final String PERFIL = "PERFIL_INTEGRAL";
    public static final String PRUEBA = "PRUEBA_PUESTO";
    public static final String SIMULACION = "SIMULACION";
    public static final String VALIDACION = "VALIDACION";
    public static final String DECISION = "DECISION";

    /** Las cinco, en el orden del catálogo. */
    public static final List<String> CINCO = List.of(PERFIL, PRUEBA, SIMULACION, VALIDACION, DECISION);

    /** La Decisión entra por «por confirmar», como dice su pieza. */
    public static final Map<String, String> ENTRADAS = Map.of(DECISION, "POR_CONFIRMAR");

    /** El catálogo de los 18 estados, el mismo que siembra V9__semillas.sql. */
    public static final List<EstadoPostulacion> CATALOGO = List.of(
            estado("POSTULADA", null, null, "SISTEMA", 1, false),
            estado("PERFIL_TURNO_CANDIDATO", PERFIL, "TURNO_CANDIDATO", "CANDIDATO", 2, false),
            estado("PERFIL_CALIFICANDO", PERFIL, "CALIFICANDO", "SISTEMA", 3, false),
            estado("PERFIL_POR_CONFIRMAR", PERFIL, "POR_CONFIRMAR", "TALENTO", 4, false),
            estado("PRUEBA_TURNO_CANDIDATO", PRUEBA, "TURNO_CANDIDATO", "CANDIDATO", 5, false),
            estado("PRUEBA_CALIFICANDO", PRUEBA, "CALIFICANDO", "SISTEMA", 6, false),
            estado("PRUEBA_POR_CONFIRMAR", PRUEBA, "POR_CONFIRMAR", "TALENTO", 7, false),
            estado("SIMULACION_POR_HABILITAR", SIMULACION, "POR_HABILITAR", "TALENTO", 8, false),
            estado("SIMULACION_TURNO_CANDIDATO", SIMULACION, "TURNO_CANDIDATO", "CANDIDATO", 9, false),
            estado("SIMULACION_POR_CONFIRMAR", SIMULACION, "POR_CONFIRMAR", "TALENTO", 10, false),
            estado("VALIDACION_POR_HABILITAR", VALIDACION, "POR_HABILITAR", "TALENTO", 11, false),
            estado("VALIDACION_TURNO_CANDIDATO", VALIDACION, "TURNO_CANDIDATO", "CANDIDATO", 12, false),
            estado("VALIDACION_POR_CONFIRMAR", VALIDACION, "POR_CONFIRMAR", "AREA", 13, false),
            estado("DECISION_POR_CONFIRMAR", DECISION, "POR_CONFIRMAR", "AREA", 14, false),
            estado("DECISION_TURNO_CANDIDATO", DECISION, "TURNO_CANDIDATO", "CANDIDATO", 15, false),
            estado("CONTRATADO", null, null, "NADIE", 16, true),
            estado("NO_CONTINUA", null, null, "NADIE", 17, true),
            estado("CERRADA", null, null, "NADIE", 18, true));

    private RecorridosDePrueba() {}

    /** Un estado del catálogo por su código. */
    public static Optional<EstadoPostulacion> estadoDe(String codigo) {
        return CATALOGO.stream().filter(e -> e.getCodigo().equals(codigo)).findFirst();
    }

    /**
     * Lo que contestaría {@link MaquinaEstados#siguiente} de verdad, con el catálogo de arriba: para
     * que una prueba con la máquina simulada siga calculando el siguiente por el recorrido.
     */
    public static Optional<EstadoPostulacion> siguienteDe(String codigo, Recorrido recorrido) {
        return MaquinaEstados.calcularSiguiente(recorrido, CATALOGO, estadoDe(codigo).orElseThrow());
    }

    private static EstadoPostulacion estado(String codigo, String etapa, String momento,
                                            String esperaA, int orden, boolean esFinal) {
        return EstadoPostulacion.builder()
                .codigo(codigo).etapaCodigo(etapa).momentoCodigo(momento)
                .esperaA(esperaA).orden(orden).esFinal(esFinal)
                .build();
    }

    /** El recorrido de siempre, con su entrada a la Decisión. */
    public static Recorrido deSiempre() {
        return de(CINCO);
    }

    /** Un recorrido con estas etapas, en este orden, y la entrada a la Decisión. */
    public static Recorrido de(List<String> etapas) {
        return new Recorrido(etapas, ENTRADAS);
    }
}
