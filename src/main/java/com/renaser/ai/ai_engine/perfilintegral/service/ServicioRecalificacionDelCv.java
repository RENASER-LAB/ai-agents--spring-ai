package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.ListaDeAfectados;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecalificacionDelCv.TandaDeRecalificacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.Set;

/**
 * Recalificar, de una sola vez, a quienes la IA calificó sin la fecha de hoy o sin ver sus años
 * de experiencia (spec la-ia-sabe-que-dia-es-hoy, decisión del 16/11/2026: un comando de
 * administración, no un botón del panel).
 *
 * <p><b>Solo la plataforma</b>, y solo sobre postulaciones que siguen en curso —ni contratadas,
 * ni descartadas, ni cerradas— en vacantes abiertas. Las de vacantes cerradas o archivadas quedan
 * como historia, con la nota con que se decidió.
 *
 * <p><b>Afectada es</b> la que ya tiene retrato y cumple una de estas:
 * <ul>
 *   <li>un hallazgo o una justificación de criterio del CV habla de fechas «futuras»,
 *       «posteriores a hoy», «imposibles» o que «aún no ocurren»;</li>
 *   <li>en su CV, la regla vieja del anonimizador tapaba algo que la nueva deja ver (perdía años
 *       de experiencia).</li>
 * </ul>
 * Quien ya se recalificó con esta tanda no vuelve a salir; quien falló sale para reintentarse.
 */
public interface ServicioRecalificacionDelCv {

    /** El modo de prueba: la lista y el coste estimado en llamadas, sin llamar a la IA. */
    default ListaDeAfectados afectados(ContextoUsuario quien) {
        return afectados(quien, Set.of());
    }

    /** Lo mismo, solo en esas vacantes; nulo o vacío es todas las abiertas. */
    ListaDeAfectados afectados(ContextoUsuario quien, Set<Long> soloVacantes);

    /**
     * Con la confirmación: reencola a cada afectado con el mecanismo de siempre —el CV y el
     * retrato, sin mover a nadie de etapa—. Lo ajustado a mano no se pisa. Un fallo con uno no
     * frena a los demás, y queda en la auditoría quién lo pidió.
     */
    default TandaDeRecalificacion recalificar(ContextoUsuario quien) {
        return recalificar(quien, Set.of());
    }

    /** Lo mismo, solo en esas vacantes; nulo o vacío es todas las abiertas. */
    TandaDeRecalificacion recalificar(ContextoUsuario quien, Set<Long> soloVacantes);
}
