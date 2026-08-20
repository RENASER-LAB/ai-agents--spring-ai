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

    // Las otras PUBLICADA del mismo banco: las que publicar una nueva deja obsoletas y hay
    // que archivar para que el estado no mienta. Query explícita y no derivada porque tanto
    // el nivel (bancos ALINEACION) como la organización (biblioteca global) pueden ser null,
    // y un "= :param" derivado nunca casa contra NULL.
    @Query("""
            select v from VersionBanco v
             where v.tipoBanco = :tipoBanco and v.estado = 'PUBLICADA' and v.id <> :salvoId
               and ((:nivel is null and v.nivelPuestoCodigo is null) or v.nivelPuestoCodigo = :nivel)
               and ((:organizacionId is null and v.organizacionId is null) or v.organizacionId = :organizacionId)""")
    List<VersionBanco> findPublicadasHermanas(@Param("tipoBanco") String tipoBanco,
                                              @Param("nivel") String nivel,
                                              @Param("organizacionId") Long organizacionId,
                                              @Param("salvoId") Long salvoId);
}
