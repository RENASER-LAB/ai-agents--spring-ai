package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * La nota mínima del Perfil Integral (V72): qué nota cuenta, cuándo llega y cómo se dice.
 *
 * <p>Con «Calificar y avanzar sola» encendido, la postulación pasa sola a la prueba del puesto
 * solo si su nota del Perfil Integral llega a la mínima de la vacante (60 por defecto). Quien
 * no llega, o no tiene nota completa, se queda en «por confirmar» y decide una persona:
 * <b>ninguna nota descarta a nadie</b>.
 *
 * <p>Vive en un solo sitio porque la usan dos que no pueden contradecirse: el pase automático,
 * que decide, y la ficha del panel, que explica por qué alguien espera. Si cada uno hiciera su
 * propia cuenta, un día la ficha diría «no llega» de alguien que sí pasó.
 */
@Component
@RequiredArgsConstructor
public class UmbralDelPerfilIntegral {

    /** La mínima de toda vacante que no fijó otra (decidido el 12/10/2026). */
    public static final int MINIMA_POR_DEFECTO = 60;

    private static final String ETAPA = "PERFIL_INTEGRAL";
    private static final String POR_CONFIRMAR = "PERFIL_POR_CONFIRMAR";

    private final PerfilTalentoRepository perfiles;
    private final NotaEtapaRepository notasEtapa;
    private final PuenteCalificacionIa puente;
    private final ColaCalificacionIa cola;

    /**
     * La nota del Perfil Integral que cuenta para el pase, o vacío si todavía no la hay.
     *
     * <p><b>Solo la completa:</b> la que se calculó con el retrato ya hecho. Al entregar un
     * banco clásico se escribe una nota provisional solo con lo cerrado, y esa nunca dispara
     * nada. Se distingue sin marcas nuevas: el cierre guarda el retrato y <i>después</i> vuelve
     * a calcular la nota, así que una nota completa es una calculada en el momento del retrato
     * o más tarde (un ajuste, una recalificación). La provisional es anterior al retrato.
     *
     * <p>Cubre también los cierres que no dan nota: las preguntas propias con alguna abierta sin
     * nota borran la fila, y unos pesos que no pesan nada que se pueda llenar la dejan como
     * estaba —anterior al retrato—. En los dos casos, vacío.
     */
    public Optional<BigDecimal> notaCompleta(Long postulacionId) {
        PerfilTalento retrato = perfiles.findByPostulacionId(postulacionId).orElse(null);
        if (retrato == null || retrato.getActualizadoEn() == null) {
            return Optional.empty();
        }
        return notasEtapa.findByPostulacionIdAndEtapaCodigo(postulacionId, ETAPA)
                .filter(n -> n.getPuntaje() != null && n.getCalculadaEn() != null
                        && !n.getCalculadaEn().isBefore(retrato.getActualizadoEn()))
                .map(NotaEtapa::getPuntaje);
    }

    /** La mínima de esta vacante, entera. Sin fijar, la de por defecto. */
    public static int minimaDe(Vacante vacante) {
        BigDecimal minima = vacante.getNotaMinima();
        return minima == null ? MINIMA_POR_DEFECTO : minima.intValue();
    }

    /**
     * Si la nota llega a la mínima: igual o más, con sus dos decimales. 59,99 no llega a 60 y
     * 60,00 sí.
     */
    public static boolean llega(BigDecimal nota, int minima) {
        return nota.compareTo(BigDecimal.valueOf(minima)) >= 0;
    }

    /** La nota como la lee una persona: con coma y dos decimales («72,40»). */
    public static String escrita(BigDecimal nota) {
        return nota.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Lo que dice el historial cuando pasa al terminar la nota. */
    public static String motivoAlTerminar(BigDecimal nota, int minima) {
        return "Pase automático: nota del Perfil Integral " + escrita(nota) + " (mínima " + minima + ")";
    }

    /** Lo que dice el historial cuando pasa porque su nota se recalculó mientras esperaba. */
    public static String motivoTrasRecalcular(BigDecimal nota, int minima) {
        return "Pase automático tras recalcular: nota " + escrita(nota) + " (mínima " + minima + ")";
    }

    /**
     * Por qué esta persona espera en «por confirmar» en vez de haber pasado sola, si la razón es
     * su nota; {@code null} si no aplica.
     *
     * <p>Solo habla de la nota, y solo cuando es la nota la que la para: vacante con el
     * interruptor encendido, postulación en «por confirmar», con el banco entregado si la
     * vacante lo lleva, y sin la IA trabajando —mientras trabaja, el panel dice que la nota
     * está en camino y esto la contradiría—. Las demás razones (sin prueba montada, vacante
     * archivada) no son de la nota y no se explican aquí.
     */
    public String porQueEspera(Postulacion postulacion, Vacante vacante) {
        if (vacante == null || !vacante.isCalificacionAutomatica()
                || !POR_CONFIRMAR.equals(postulacion.getEstadoCodigo())) {
            return null;
        }
        if (vacante.isAplicaEvaluacion() && !puente.tieneEvaluacionEntregada(postulacion.getId())) {
            return null;
        }
        if ("EN_CURSO".equals(cola.comoVa(postulacion.getId()))) {
            return null;
        }
        Optional<BigDecimal> nota = notaCompleta(postulacion.getId());
        if (nota.isEmpty()) {
            return "Sin nota completa: revisa el Perfil Integral";
        }
        int minima = minimaDe(vacante);
        if (llega(nota.get(), minima)) {
            return null;
        }
        return "Nota " + escrita(nota.get()) + ": no llega a la mínima de la vacante (" + minima + ")";
    }
}
