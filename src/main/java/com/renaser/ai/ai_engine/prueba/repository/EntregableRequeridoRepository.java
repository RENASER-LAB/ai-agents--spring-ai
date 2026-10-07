package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntregableRequeridoRepository extends JpaRepository<EntregableRequerido, Long> {

    List<EntregableRequerido> findByVersionPlantillaPruebaIdOrderByOrden(Long versionPlantillaPruebaId);

    /** Los entregables de una versión de la prueba escrita en el editor (V67), en su orden. */
    List<EntregableRequerido> findByVersionBancoIdOrderByOrdenAscIdAsc(Long versionBancoId);

    List<EntregableRequerido> findByVersionBancoIdIn(java.util.Collection<Long> versionBancoIds);

    void deleteByVersionBancoId(Long versionBancoId);

    /** El archivo que pide una pregunta de la prueba del editor (V68), si lo pide. */
    java.util.Optional<EntregableRequerido> findByPreguntaId(Long preguntaId);
}
