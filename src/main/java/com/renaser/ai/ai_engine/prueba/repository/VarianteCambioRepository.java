package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VarianteCambioRepository extends JpaRepository<VarianteCambio, Long> {

    List<VarianteCambio> findByVersionPlantillaPruebaId(Long versionPlantillaPruebaId);

    // La misma consulta, pero en el orden que alguien decidió. La de arriba se queda para
    // el sorteo del cambio inesperado, al que el orden le da igual porque elige al azar.
    //
    // ⚠️ Sin el ORDER BY el orden lo decide Postgres, y entonces reordenar las variantes no
    // cambiaría nada de lo que se ve: la columna `orden` existiría sin que nadie la leyera.
    List<VarianteCambio> findByVersionPlantillaPruebaIdOrderByOrden(Long versionPlantillaPruebaId);
}
