package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VersionBancoRepository extends JpaRepository<VersionBanco, Long> {

    // Las versiones de UNA organización. Desde la V37 no hay filas sin dueño: el banco
    // compartido son las filas de la plataforma, y a esta consulta se llega con el
    // organizacionId que DuenoDelInstrumento resolvió.
    List<VersionBanco> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);

    // La versión publicada más reciente de un nivel PARA UNA ORGANIZACIÓN: es la que se
    // le fija al candidato al crear su evaluación, y a la que queda atado aunque después
    // se publique otra (RF-138). Query explícita: el nombre derivado con cuatro
    // condiciones y el orden ya no se puede leer de un vistazo.
    @Query("""
            select v from VersionBanco v
             where v.organizacionId = :organizacionId and v.tipoBanco = :tipoBanco
               and v.nivelPuestoCodigo = :nivel and v.estado = 'PUBLICADA'
             order by v.publicadaEn desc limit 1""")
    Optional<VersionBanco> laPublicadaDelNivel(@Param("organizacionId") Long organizacionId,
                                               @Param("tipoBanco") String tipoBanco,
                                               @Param("nivel") String nivel);

    // Las otras PUBLICADA del mismo banco: las que publicar una nueva deja obsoletas y hay
    // que archivar para que el estado no mienta. Query explícita y no derivada porque el
    // nivel (bancos ALINEACION) puede ser null, y un "= :param" derivado nunca casa
    // contra NULL.
    @Query("""
            select v from VersionBanco v
             where v.tipoBanco = :tipoBanco and v.estado = 'PUBLICADA' and v.id <> :salvoId
               and ((:nivel is null and v.nivelPuestoCodigo is null) or v.nivelPuestoCodigo = :nivel)
               and v.organizacionId = :organizacionId""")
    List<VersionBanco> findPublicadasHermanas(@Param("tipoBanco") String tipoBanco,
                                              @Param("nivel") String nivel,
                                              @Param("organizacionId") Long organizacionId,
                                              @Param("salvoId") Long salvoId);

    List<VersionBanco> findByOrganizacionIdAndEstado(Long organizacionId, String estado);
}
