package com.renaser.ai.ai_engine.validacion.service;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.validacion.dto.DtosValidacion.*;

import java.util.List;

/**
 * La validación práctica: el último paso de evidencia antes de decidir.
 *
 * <p>Dos modalidades con una diferencia que no es técnica sino legal: la simulación extendida
 * se puede usar desde el primer día, y el trabajo real exige figura contractual registrada.
 */
public interface ServicioValidacion {

    /**
     * Dónde entra de verdad la persona a Validación, y qué hay que contar de ello.
     *
     * @param validacionId el periodo con el que entra: el recién creado o el que ya tenía
     * @param paso         el estado de la postulación que corresponde a ese periodo
     * @param coletilla    lo que se añade al motivo para que el historial lo explique; nula
     *                     cuando entra a «por habilitar», que es lo que se espera
     */
    record Entrada(Long validacionId, String paso, String coletilla) {

        /**
         * El motivo escrito, con la coletilla detrás, igual que la de «sin avisar al
         * candidato». Un motivo vacío se queda vacío: la coletilla no cuenta como motivo y
         * no puede tapar que falta.
         */
        public String motivoCon(String motivo) {
            if (coletilla == null || motivo == null || motivo.isBlank()) {
                return motivo;
            }
            return motivo + " · " + coletilla;
        }
    }

    /**
     * El periodo de quien entra a la etapa: se crea en POR_HABILITAR, sin modalidad todavía,
     * o se reutiliza el que ya tenía <b>sin tocar ninguno de sus datos</b>.
     *
     * <p>Devuelve el paso de Validación que corresponde a ese periodo: quien vuelve con su
     * periodo en curso entra a su turno, y quien lo tenía vencido o cerrado, a «por
     * confirmar».
     */
    Entrada crearAlEntrar(Long postulacionId, Long organizacionId);

    ValidacionResponse ver(ContextoUsuario quien, Long postulacionId);

    /** Fija modalidad, días y responsable. Con TRABAJO_REAL exige la figura contractual. */
    void habilitar(ContextoUsuario quien, Long postulacionId, HabilitarValidacion datos);

    /** Arranca el periodo: fija inicio y fin, y el candidato pasa a su turno. */
    void iniciar(ContextoUsuario quien, Long postulacionId);

    List<MetricaResponse> verMetricas(ContextoUsuario quien, Long postulacionId);

    /** Completa una métrica que no se alimentó sola. La explicación es obligatoria. */
    void completarMetrica(ContextoUsuario quien, Long postulacionId, Long criterioId, CompletarMetrica datos);

    /** Cierra el periodo: pondera las métricas y manda la postulación a la decisión. */
    void cerrar(ContextoUsuario quien, Long postulacionId);

    /** Llamado por el sondeo: los periodos cuya fecha de fin ya pasó terminan solos. */
    void terminarVencidos();
}
