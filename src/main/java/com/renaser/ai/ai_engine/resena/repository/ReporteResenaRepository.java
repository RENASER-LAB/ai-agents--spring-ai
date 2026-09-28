package com.renaser.ai.ai_engine.resena.repository;

import com.renaser.ai.ai_engine.resena.entity.ReporteResena;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReporteResenaRepository extends JpaRepository<ReporteResena, Long> {

    /** Los reportes de una tanda de reseñas —de ellas y de sus respuestas—, el último arriba. */
    List<ReporteResena> findByResenaIdInOrderByReportadoEnDesc(Collection<Long> resenaIds);

    /** El pendiente de una reseña o de una respuesta, si lo hay. */
    Optional<ReporteResena> findByResenaIdAndObjetoAndEstado(Long resenaId, String objeto,
                                                            String estado);

    Optional<ReporteResena> findByRespuestaIdAndEstado(Long respuestaId, String estado);

    /** Los pendientes, de la más antigua a la más reciente: la cola de la moderación. */
    List<ReporteResena> findByEstadoOrderByReportadoEnAsc(String estado);

    /** Los resueltos, el último arriba. */
    List<ReporteResena> findByEstadoNotOrderByResueltoEnDesc(String estado);
}
