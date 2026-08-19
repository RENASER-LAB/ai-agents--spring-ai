package com.renaser.ai.ai_engine.postulacion.repository;

import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatoCvRepository extends JpaRepository<DatoCv, Long> {

    Optional<DatoCv> findByPostulacionId(Long postulacionId);

    // La tanda entera de una vez: el ranking pinta una fila por candidato y pedir la ficha
    // de uno en uno haría una consulta por fila.
    List<DatoCv> findByPostulacionIdIn(List<Long> postulacionIds);
}
