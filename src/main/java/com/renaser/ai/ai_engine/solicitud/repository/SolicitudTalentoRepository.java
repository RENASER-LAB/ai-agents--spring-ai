package com.renaser.ai.ai_engine.solicitud.repository;

import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudTalentoRepository extends JpaRepository<SolicitudTalento, Long> {
    List<SolicitudTalento> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);
    List<SolicitudTalento> findByOrganizacionIdAndResponsableUsuarioIdOrderByCreadoEnDesc(
            Long organizacionId, Long responsableUsuarioId);
    Optional<SolicitudTalento> findByIdAndOrganizacionId(Long id, Long organizacionId);

    // Qué solicitudes cuelgan de un área. `solicitud_talento.area_id` es NOT NULL y sin
    // ON DELETE: borrar el área sin mover estas filas antes es un error de clave ajena, y
    // vaciar la columna ni siquiera es una salida. Contar avisa; la lista reasigna.
    long countByOrganizacionIdAndAreaId(Long organizacionId, Long areaId);

    List<SolicitudTalento> findByOrganizacionIdAndAreaId(Long organizacionId, Long areaId);

    // ⚠️ SIN filtrar por organización, a propósito. La clave ajena tampoco filtra: le basta
    // con que la fila exista, venga de la empresa que venga. Es la última comprobación antes
    // del DELETE, para que una fila que el recuento por organización no ve no acabe saliendo
    // como un error crudo de integridad.
    long countByAreaId(Long areaId);
}
