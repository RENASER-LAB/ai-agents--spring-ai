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
               and v.organizacionId = :organizacionId
               and v.vacanteId is null""")
    List<VersionBanco> findPublicadasHermanas(@Param("tipoBanco") String tipoBanco,
                                              @Param("nivel") String nivel,
                                              @Param("organizacionId") Long organizacionId,
                                              @Param("salvoId") Long salvoId);

    List<VersionBanco> findByOrganizacionIdAndEstado(Long organizacionId, String estado);

    /**
     * El cuestionario técnico de una vacante: a lo sumo un BORRADOR y una PUBLICADA (índices
     * parciales de V42, con el propósito desde la V66).
     *
     * <p>⚠️ <b>Filtra por propósito, y es lo que importa.</b> Desde la V66 una vacante tiene
     * además sus preguntas del Perfil Integral en la misma tabla; sin el filtro, la etapa
     * técnica podía tomar esas preguntas en vez de su cuestionario.
     */
    @Query("""
            select v from VersionBanco v
             where v.vacanteId = :vacanteId and v.estado = :estado
               and v.proposito = 'CUESTIONARIO_TECNICO'""")
    java.util.Optional<VersionBanco> cuestionarioTecnicoDe(@Param("vacanteId") Long vacanteId,
                                                           @Param("estado") String estado);

    /** Las preguntas propias del Perfil Integral de una vacante: una BORRADOR y una PUBLICADA. */
    @Query("""
            select v from VersionBanco v
             where v.vacanteId = :vacanteId and v.estado = :estado
               and v.proposito = 'PERFIL_INTEGRAL'""")
    java.util.Optional<VersionBanco> preguntasPropiasDe(@Param("vacanteId") Long vacanteId,
                                                        @Param("estado") String estado);

    /**
     * Las preguntas propias publicadas de una empresa, de todas sus vacantes: la biblioteca de
     * la que se copia. Por la organización de la fila, que es la de la vacante.
     */
    @Query("""
            select v from VersionBanco v
             where v.organizacionId = :organizacionId and v.estado = 'PUBLICADA'
               and v.proposito = 'PERFIL_INTEGRAL'""")
    List<VersionBanco> propiasPublicadasDe(@Param("organizacionId") Long organizacionId);

    /**
     * Las preguntas propias en curso de todas las vacantes de una empresa: su BORRADOR y su
     * PUBLICADA. La lista de vacantes las lee de una vez para decir en qué punto está cada
     * una, en vez de dos consultas por fila.
     */
    @Query("""
            select v from VersionBanco v
             where v.organizacionId = :organizacionId and v.vacanteId is not null
               and v.estado in ('BORRADOR', 'PUBLICADA')
               and v.proposito = 'PERFIL_INTEGRAL'""")
    List<VersionBanco> propiasEnCursoDe(@Param("organizacionId") Long organizacionId);

    /**
     * Los niveles para los que una empresa tiene un banco PROPIO publicado. Es la misma
     * regla que {@link #laPublicadaDelNivel} con {@code tipoBanco = 'NIVEL'}, pero de una vez
     * para todos los niveles: la lista de vacantes la necesita en cada fila.
     */
    @Query("""
            select distinct v.nivelPuestoCodigo from VersionBanco v
             where v.organizacionId = :organizacionId and v.tipoBanco = 'NIVEL'
               and v.estado = 'PUBLICADA' and v.nivelPuestoCodigo is not null""")
    List<String> nivelesConBancoPublicado(@Param("organizacionId") Long organizacionId);
}
