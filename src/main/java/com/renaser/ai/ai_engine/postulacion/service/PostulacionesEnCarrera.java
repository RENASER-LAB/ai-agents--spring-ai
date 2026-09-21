package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quién sigue en carrera en una vacante, decidido en un solo sitio.
 *
 * <p><b>Por qué existe.</b> «A quién le afecta lo que acaba de pasar en esta vacante» es la
 * pregunta que se hacen todas las acciones que tocan una convocatoria viva: cambiar el
 * sueldo, corregir el texto que lee el candidato y —en las entregas siguientes— archivarla o
 * eliminarla. La respuesta tiene que ser la misma en todas. Escrita a mano en cada servicio,
 * basta que una olvide un estado para avisar a quien ya no continúa, que es la forma más
 * cruel de equivocarse: un aviso sobre el puesto que acaba de perder.
 *
 * <p><b>En carrera</b> significa que la postulación no terminó: no está en
 * {@code CONTRATADO}, {@code NO_CONTINUA} ni {@code CERRADA}. Los tres son los estados
 * finales del catálogo (ver «Estados de la postulación»); todo lo demás sigue esperando algo
 * —al candidato, al equipo o a un proceso—, y por eso sigue importándole lo que cambie.
 *
 * <p>No decide <b>qué</b> se le cuenta a esa gente ni <b>por dónde</b>: eso es de quien
 * provoca el hecho. Aquí solo está la lista.
 */
@Service
@RequiredArgsConstructor
public class PostulacionesEnCarrera {

    /**
     * Los tres estados finales. Fuera de ellos, la postulación sigue viva.
     *
     * <p>Público a propósito: hay consultas que necesitan el conjunto para un {@code not in}
     * y pruebas que necesitan nombrarlos sin volver a escribirlos.
     */
    public static final Set<String> ESTADOS_TERMINADOS =
            Set.of("CONTRATADO", "NO_CONTINUA", "CERRADA");

    private final PostulacionRepository postulaciones;

    /** Si esta postulación concreta sigue en carrera. */
    public static boolean sigueEnCarrera(Postulacion postulacion) {
        return postulacion != null
                && !ESTADOS_TERMINADOS.contains(postulacion.getEstadoCodigo());
    }

    /** Las de esta vacante que siguen en carrera, las nuevas arriba. */
    public List<Postulacion> deLaVacante(Long vacanteId) {
        return postulaciones.enCarreraDeLaVacante(vacanteId, ESTADOS_TERMINADOS);
    }

    /** Cuántas siguen en carrera en esta vacante. */
    public int cuantasEnLaVacante(Long vacanteId) {
        return (int) postulaciones.countByVacanteIdAndEstadoCodigoNotIn(
                vacanteId, ESTADOS_TERMINADOS);
    }

    /**
     * Cuántas siguen en carrera en cada vacante de la empresa, en una sola consulta.
     *
     * <p>Las vacantes sin nadie en carrera no salen en el mapa: quien lo lea tiene que
     * tratar la ausencia como un cero, y es lo que hace {@code getOrDefault}.
     */
    public Map<Long, Integer> cuantasPorVacante(Long organizacionId) {
        Map<Long, Integer> porVacante = new HashMap<>();
        for (Object[] fila : postulaciones.enCarreraPorVacante(organizacionId,
                ESTADOS_TERMINADOS)) {
            porVacante.put((Long) fila[0], ((Number) fila[1]).intValue());
        }
        return porVacante;
    }
}
