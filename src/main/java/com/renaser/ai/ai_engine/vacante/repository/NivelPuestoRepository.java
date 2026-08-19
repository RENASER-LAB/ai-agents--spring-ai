package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.NivelPuesto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Se sembraba en la migración y nadie lo leía: el frontend llevaba los códigos
// copiados a mano y se desincronizaron. Ahora se sirven desde aquí.
public interface NivelPuestoRepository extends JpaRepository<NivelPuesto, String> {
    List<NivelPuesto> findAllByOrderByOrdenAsc();
}
