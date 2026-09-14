package com.renaser.ai.ai_engine.notificacion.repository;

import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AvisoPortalRepository extends JpaRepository<AvisoPortal, Long> {

    /** Los suyos, los nuevos arriba. Va contra el índice `aviso_portal_de_cada_uno`. */
    List<AvisoPortal> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);

    /** El número del punto rojo. Va contra el índice parcial `aviso_portal_sin_leer`. */
    long countByUsuarioIdAndLeidoEnIsNull(Long usuarioId);

    /** Los no leídos de un lote de postulaciones: el punto de cada fila de «mis procesos». */
    List<AvisoPortal> findByUsuarioIdAndPostulacionIdInAndLeidoEnIsNull(
            Long usuarioId, List<Long> postulacionIds);

    /**
     * Marca leídos todos los que le quedaban.
     *
     * <p>En una sola sentencia y no leyendo-y-guardando uno a uno: abrir la campana es el
     * gesto más frecuente del portal, y quien lleva meses sin entrar puede tener decenas.
     *
     * <p>⚠️ <b>Pone la misma marca de tiempo en todos, y es la correcta.</b> No se leyeron en
     * momentos distintos: se leyeron todos en el instante en que abrió la campana.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AvisoPortal a set a.leidoEn = :cuando "
            + "where a.usuarioId = :usuarioId and a.leidoEn is null")
    int marcarTodosLeidos(@Param("usuarioId") Long usuarioId, @Param("cuando") Instant cuando);
}
