package com.renaser.ai.ai_engine.organizacion.repository;

import com.renaser.ai.ai_engine.organizacion.entity.Sede;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SedeRepository extends JpaRepository<Sede, Long> {

    /** Todas, vivas y desactivadas: sin las desactivadas no habría desde dónde reactivarlas. */
    List<Sede> findByOrganizacionIdOrderByNombre(Long organizacionId);

    List<Sede> findByOrganizacionIdAndEsActivaTrueOrderByNombre(Long organizacionId);

    /** La de quien pregunta, o nada. Lo ajeno es un 404, no un 403. */
    Optional<Sede> findByIdAndOrganizacionId(Long id, Long organizacionId);

    /** Sin distinguir mayúsculas, igual que el índice único de la V64. */
    @Query("select count(s) > 0 from Sede s where s.organizacionId = :organizacionId "
            + "and lower(trim(s.nombre)) = lower(trim(:nombre)) and (:excepto is null or s.id <> :excepto)")
    boolean existeConElNombre(@Param("organizacionId") Long organizacionId,
                              @Param("nombre") String nombre,
                              @Param("excepto") Long excepto);
}
