package com.renaser.ai.ai_engine.resena.repository;

import com.renaser.ai.ai_engine.resena.entity.RespuestaResena;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RespuestaResenaRepository extends JpaRepository<RespuestaResena, Long> {

    /** La viva de una reseña: como mucho una, lo garantiza un índice parcial. */
    Optional<RespuestaResena> findByResenaIdAndBorradaEnIsNull(Long resenaId);

    /** Las vivas de una tanda de reseñas, en una consulta. */
    List<RespuestaResena> findByResenaIdInAndBorradaEnIsNull(Collection<Long> resenaIds);

    /** Todas, también las borradas: para el borrado de datos. */
    List<RespuestaResena> findByResenaIdIn(Collection<Long> resenaIds);
}
