package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VersionPlantillaPruebaRepository extends JpaRepository<VersionPlantillaPrueba, Long> {

    List<VersionPlantillaPrueba> findByPlantillaPruebaIdOrderByVersionDesc(Long plantillaPruebaId);

    /**
     * Una versión de prueba, pero solo si su plantilla es de esta organización.
     *
     * <p><b>La versión no sabe de organizaciones</b>: el dueño vive en su plantilla. Hasta
     * ahora cada sitio que necesitaba una la buscaba por id suelto y comprobaba el padre en
     * la línea siguiente; esto hace las dos cosas de una consulta, que es lo que deja leerla
     * sin abrir un `findById` sin filtrar en un servicio del panel.
     *
     * <p>⚠️ <b>La organización que se pasa es la que resuelve {@code DuenoDelInstrumento}</b>,
     * no la de quien pregunta sin más: los instrumentos pueden ser de la plataforma y
     * compartirse, y filtrar por la empresa a secas escondería su propia prueba.
     *
     * <p>Vacío significa «no es tuya», que en el panel se lee igual que «no existe».
     */
    @Query("""
            select v from VersionPlantillaPrueba v
            where v.id = :id
              and v.plantillaPruebaId in (
                  select p.id from PlantillaPrueba p where p.organizacionId = :organizacionId)
            """)
    Optional<VersionPlantillaPrueba> laDeLaOrganizacion(@Param("id") Long id,
                                                        @Param("organizacionId") Long organizacionId);

    // La versión publicada más reciente de la plantilla que rige la vacante: es la que
    // se le fija al candidato al llegar a su turno, y a la que queda atado (RF-90).
    Optional<VersionPlantillaPrueba> findFirstByPlantillaPruebaIdAndEstadoOrderByPublicadaEnDesc(
            Long plantillaPruebaId, String estado);
}
