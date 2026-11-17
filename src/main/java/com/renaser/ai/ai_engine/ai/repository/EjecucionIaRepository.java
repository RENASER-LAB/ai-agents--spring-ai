package com.renaser.ai.ai_engine.ai.repository;

import com.renaser.ai.ai_engine.ai.model.EjecucionIa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface EjecucionIaRepository extends JpaRepository<EjecucionIa, Long> {

    /**
     * Lo gastado por una organización en un tramo de tiempo (pieza E). Es la cuenta del
     * tope mensual: la suma de {@code costo} del mes natural corriente. Las ejecuciones
     * sin costo —sin tarifa registrada, o sin conteo de tokens— suman cero aquí: no se
     * les puede cobrar lo que no se pudo medir, y el hueco lo delata el aviso de la
     * calculadora, no esta suma.
     */
    @Query("""
            select coalesce(sum(e.costo), 0)
              from EjecucionIa e
             where e.organizacionId = :organizacionId
               and e.creadoEn >= :desde and e.creadoEn < :hasta
            """)
    BigDecimal costoDelPeriodo(@Param("organizacionId") Long organizacionId,
                               @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /**
     * El último intento fallido de un trabajo: su {@code error} es el porqué que se le enseña
     * a quien espera una recalificación (el mismo texto que el backend ya da cuando el
     * proveedor contesta 402 o se agota el tiempo).
     */
    java.util.Optional<EjecucionIa> findFirstByTrabajoIaIdAndEsExitosaFalseOrderByIdDesc(Long trabajoIaId);

    /**
     * Cuáles de estas ejecuciones llevan un texto en su envío. Solo devuelve los ids: el envío
     * entero es largo y aquí no se lee. Lo usa la recalificación del CV para saber quién se
     * calificó ya con la fecha de hoy (spec la-ia-sabe-que-dia-es-hoy).
     */
    @Query("""
            select e.id
              from EjecucionIa e
             where e.id in :ids
               and e.envio like concat('%', :texto, '%')
            """)
    java.util.List<Long> idsConEnvioQueLleva(@Param("ids") java.util.Collection<Long> ids,
                                             @Param("texto") String texto);
}
