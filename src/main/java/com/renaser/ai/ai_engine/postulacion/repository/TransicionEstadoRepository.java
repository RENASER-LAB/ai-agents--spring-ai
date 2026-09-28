package com.renaser.ai.ai_engine.postulacion.repository;

import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransicionEstadoRepository extends JpaRepository<TransicionEstado, Long> {
    List<TransicionEstado> findByPostulacionIdOrderByOcurridaEnAsc(Long postulacionId);

    /**
     * La última llegada de una postulación a un estado.
     *
     * <p>Lo usan las reseñas para fechar la contratación: cuenta la transición a
     * {@code CONTRATADO}, venga de la decisión en verde o de la transición manual —el camino
     * manual no crea la decisión, y la contratación vale igual—.
     */
    Optional<TransicionEstado> findFirstByPostulacionIdAndEstadoNuevoCodigoOrderByOcurridaEnDesc(
            Long postulacionId, String estadoNuevoCodigo);
}
