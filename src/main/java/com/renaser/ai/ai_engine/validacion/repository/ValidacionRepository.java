package com.renaser.ai.ai_engine.validacion.repository;

import com.renaser.ai.ai_engine.validacion.entity.Validacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ValidacionRepository extends JpaRepository<Validacion, Long> {

    Optional<Validacion> findByPostulacionId(Long postulacionId);

    // Los periodos que ya vencieron y siguen en curso: el sondeo los cierra solo. Sin esto,
    // un periodo terminado se queda esperando a que alguien se acuerde.
    List<Validacion> findByEstadoAndFinEnBefore(String estado, Instant momento);
}
