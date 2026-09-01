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

    // Los criterios GLOBALES de una etapa: los ocho del currículum, los diez de la simulación
    // y las nueve métricas de la validación. Valen para cualquier vacante, a diferencia de los
    // de la prueba del puesto, que cuelgan de su plantilla.
    List<Criterio> findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(String etapaCodigo);
}
