package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.ParConsistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParConsistenciaRepository extends JpaRepository<ParConsistencia, Long> {

    // Los pares que se comparan entre sí dentro de una versión del banco. Si dos respuestas
    // que deberían parecerse se separan más de lo tolerado, sale una alerta.
    List<ParConsistencia> findByVersionBancoId(Long versionBancoId);

    // Al eliminar una pregunta de un borrador caen los pares que la referencian: la FK
    // no deja borrarla mientras alguno la apunte.
    void deleteByPreguntaAIdOrPreguntaBId(Long preguntaAId, Long preguntaBId);

    // Al descartar un borrador entero.
    void deleteByVersionBancoId(Long versionBancoId);
}
