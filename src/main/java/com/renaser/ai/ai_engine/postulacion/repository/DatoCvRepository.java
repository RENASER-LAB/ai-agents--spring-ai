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

    /**
     * La ficha ya leída de esta persona para un currículum con esta huella, la más
     * reciente. Es lo que permite no pagar dos lecturas del mismo archivo (RF-161):
     * si existe, se copia; solo un archivo nuevo dispara una lectura nueva.
     */
    @org.springframework.data.jpa.repository.Query("""
            select d from DatoCv d
              join Postulacion p on p.id = d.postulacionId
              join Usuario u on u.id = p.usuarioId
              join Cv c on c.postulacionId = p.id
              join Archivo a on a.id = c.archivoOriginalId
             where u.personaId = :personaId and a.contenidoHash = :hash
             order by d.actualizadoEn desc
            """)
    List<DatoCv> fichasDeLaPersonaConHash(
            @org.springframework.data.repository.query.Param("personaId") Long personaId,
            @org.springframework.data.repository.query.Param("hash") String hash);
}
