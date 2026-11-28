package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.VacanteEtapa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** El recorrido de cada vacante (V95). */
public interface VacanteEtapaRepository extends JpaRepository<VacanteEtapa, VacanteEtapa.Clave> {

    /** Las etapas de una vacante, en su orden. Vacía si la vacante no tiene filas. */
    List<VacanteEtapa> findByVacanteIdOrderByOrdenAsc(Long vacanteId);

    /** Las de varias vacantes de una vez, para las listas: una consulta y no una por fila. */
    List<VacanteEtapa> findByVacanteIdInOrderByVacanteIdAscOrdenAsc(Collection<Long> vacanteIds);

    /**
     * Borra el recorrido de una vacante para escribir el nuevo.
     *
     * <p>⚠️ Es una consulta y no un {@code deleteAll}: Hibernate escribe los borrados DESPUÉS de las
     * inserciones al vaciar, y el nuevo recorrido chocaría con la clave y con el orden único del
     * viejo. Así el borrado sale ya, y la caché se vacía para que ninguna fila vieja se confunda
     * con una nueva de la misma clave.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VacanteEtapa e where e.vacanteId = :vacanteId")
    int borrarElRecorrido(@Param("vacanteId") Long vacanteId);
}
