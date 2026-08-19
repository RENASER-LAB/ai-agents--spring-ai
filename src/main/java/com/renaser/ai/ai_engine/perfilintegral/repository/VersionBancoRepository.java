package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VersionBancoRepository extends JpaRepository<VersionBanco, Long> {

    // organizacionId vacío = biblioteca global de Renaser, visible para todas las
    // organizaciones. Ver docs/07-DICCIONARIO-DE-DATOS.md §16.
    @Query("select v from VersionBanco v where v.organizacionId is null or v.organizacionId = :organizacionId order by v.creadoEn desc")
    List<VersionBanco> findVisibles(@Param("organizacionId") Long organizacionId);

    // La versión publicada más reciente de un nivel: es la que se le fija al candidato al
    // crear su evaluación, y a la que queda atado aunque después se publique otra (RF-138).
    Optional<VersionBanco> findFirstByTipoBancoAndNivelPuestoCodigoAndEstadoOrderByPublicadaEnDesc(
            String tipoBanco, String nivelPuestoCodigo, String estado);
}
