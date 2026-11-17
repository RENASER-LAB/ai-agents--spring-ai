package com.renaser.ai.ai_engine.perfilintegral.dto;

import java.util.List;

/**
 * Lo que dice el comando que recalifica a los afectados por la fecha o por los años tapados
 * (spec la-ia-sabe-que-dia-es-hoy). Primero en modo de prueba —la lista y el coste, sin llamar a
 * la IA— y después con confirmación.
 */
public final class DtosRecalificacionDelCv {

    private DtosRecalificacionDelCv() {
    }

    /**
     * Una postulación afectada que sigue en curso en una vacante abierta.
     *
     * @param porLaFecha     un hallazgo o una justificación del CV habla de fechas «futuras»,
     *                       «posteriores a hoy», «imposibles» o que «aún no ocurren»
     * @param porLosAnios    la regla vieja del anonimizador le tapaba algo que la nueva deja ver
     * @param estado         PENDIENTE (se recalifica al confirmar), EN_CURSO (ya en la cola: no se
     *                       vuelve a encolar) o DETENIDA (la vez anterior falló y conserva su
     *                       calificación: se reintenta al confirmar)
     * @param motivoDelFallo por qué se detuvo la vez anterior; nulo si no
     * @param llamadas       las llamadas a la IA que costará: datos, evidencia y retrato, más la
     *                       búsqueda si la vacante busca algo en el CV
     */
    public record Afectado(Long postulacionId, String candidato, Long vacanteId, String vacante,
                           boolean porLaFecha, boolean porLosAnios, String estado,
                           String motivoDelFallo, int llamadas) {
    }

    /** Cuántas hay de una vacante. */
    public record VacanteAfectada(Long vacanteId, String vacante, int afectados) {
    }

    /**
     * La lista previa, sin llamar a la IA.
     *
     * @param pendientes        las que se recalificarán al confirmar (incluye las fallidas)
     * @param enMarcha          las que ya están en la cola: no cuentan para el coste
     * @param fallidas          las que fallaron la vez anterior y se reintentarán
     * @param llamadasEstimadas lo que costará confirmar, en llamadas a la IA (sin reintentos)
     */
    public record ListaDeAfectados(int pendientes, int porLaFecha, int porLosAnios, int enMarcha,
                                   int fallidas, int llamadasEstimadas, List<VacanteAfectada> vacantes,
                                   List<Afectado> afectados) {
    }

    /** Una que no se pudo encolar: conserva su calificación y se reintenta en otra tanda. */
    public record NoEncolada(Long postulacionId, String candidato, String motivo) {
    }

    /**
     * Lo que pasó al confirmar.
     *
     * @param encoladas  quedaron en la cola; la IA las recalifica en los próximos minutos
     * @param yaEnMarcha ya tenían un trabajo vivo: no se pagan dos veces
     * @param fallidas   no se pudieron encolar; las demás siguieron
     */
    public record TandaDeRecalificacion(int encoladas, int yaEnMarcha, List<NoEncolada> fallidas,
                                        int llamadasEstimadas) {
    }
}
