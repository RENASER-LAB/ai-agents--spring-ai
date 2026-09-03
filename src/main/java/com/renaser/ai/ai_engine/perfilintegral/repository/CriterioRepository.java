package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CriterioRepository extends JpaRepository<Criterio, Long> {

    // La rúbrica de una versión de prueba concreta: los del CV (hito 2) tienen este
    // campo vacío, así que no se mezclan.
    List<Criterio> findByVersionPlantillaPruebaId(Long versionPlantillaPruebaId);

    // La misma rúbrica, en el orden en que se escribió. La de arriba la usan quienes solo
    // suman puntos, y ahí el orden no importa; esta es la que se le enseña a una persona.
    List<Criterio> findByVersionPlantillaPruebaIdOrderByOrden(Long versionPlantillaPruebaId);

    /**
     * Las rúbricas de varias versiones a la vez, para el ranking de la prueba del puesto.
     *
     * <p>Hoy una tanda se mide con una sola versión —reconfigurar la etapa técnica se
     * bloquea en cuanto alguien abre su prueba—, pero la consulta va en plural igualmente:
     * una tanda que arrastre dos versiones tiene que seguir saliendo entera, y pedirlas de
     * una en una sería volver a la consulta por fila que la tabla existe para evitar.
     */
    List<Criterio> findByVersionPlantillaPruebaIdInOrderByOrden(
            java.util.Collection<Long> versionPlantillaPruebaIds);

    // Los criterios GLOBALES de una etapa: los ocho del currículum, los diez de la simulación
    // y las nueve métricas de la validación. Valen para cualquier vacante, a diferencia de los
    // de la prueba del puesto, que cuelgan de su plantilla.
    List<Criterio> findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(String etapaCodigo);
}
