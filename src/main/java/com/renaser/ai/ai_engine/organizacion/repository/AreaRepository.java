package com.renaser.ai.ai_engine.organizacion.repository;

import com.renaser.ai.ai_engine.organizacion.entity.Area;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<Area, Long> {

    /**
     * Las que sirven HOY para registrar una solicitud.
     *
     * <p>⚠️ Filtra por activa a propósito y no debe dejar de hacerlo: alimenta el desplegable
     * de la Solicitud de Talento, y una inactiva ahí significa una solicitud nueva colgada de
     * un área que la organización dio por cerrada.
     */
    List<Area> findByOrganizacionIdAndEsActivaTrueOrderByNombre(Long organizacionId);

    /** Cuántas quedan encendidas. Se usa para no dejar a la empresa sin ninguna. */
    long countByOrganizacionIdAndEsActivaTrue(Long organizacionId);

    /**
     * Todas, vivas y retiradas.
     *
     * <p>Existe porque desactivar sin esto es un viaje sin retorno: el área desaparecería de
     * la única lista que hay y nadie podría volver a encenderla desde el panel.
     */
    List<Area> findByOrganizacionIdOrderByNombre(Long organizacionId);

    /** El área de quien pregunta, o nada. Lo ajeno es un 404, no un 403. */
    Optional<Area> findByIdAndOrganizacionId(Long id, Long organizacionId);

    /**
     * ⚠️ Sensible a mayúsculas, igual que el {@code UNIQUE (organizacion_id, nombre)} de la
     * V2. Se comprueba antes para poder explicar el choque con palabras; si esto fuera
     * {@code IgnoreCase} rechazaría nombres que la base sí admite, y el panel diría que existe
     * algo que no existe.
     */
    boolean existsByOrganizacionIdAndNombre(Long organizacionId, String nombre);
}
