package com.renaser.ai.ai_engine.postulacion.repository;

import com.renaser.ai.ai_engine.postulacion.entity.RecordatorioEnviado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecordatorioEnviadoRepository extends JpaRepository<RecordatorioEnviado, Long> {

    /** Lo que ya salió (o se omitió) para estos turnos: una consulta para toda la tanda. */
    List<RecordatorioEnviado> findByTransicionEstadoIdIn(Collection<Long> turnos);
}
