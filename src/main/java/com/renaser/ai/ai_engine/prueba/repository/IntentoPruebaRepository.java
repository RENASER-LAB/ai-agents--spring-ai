package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IntentoPruebaRepository extends JpaRepository<IntentoPrueba, Long> {

    Optional<IntentoPrueba> findByPostulacionId(Long postulacionId);

    /**
     * Los intentos de una tanda entera, de una sola consulta.
     *
     * <p>Es lo que deja que el ranking sepa con qué versión de la plantilla se midió cada
     * candidato sin preguntarlo fila a fila. Quien todavía no ha llegado a la etapa técnica
     * simplemente no sale en la lista: <b>no</b> es un error, es lo normal en una tanda a
     * medio recorrer, y por eso esto devuelve una lista y no revienta como
     * {@link #findByPostulacionId}.
     */
    List<IntentoPrueba> findByPostulacionIdIn(java.util.Collection<Long> postulacionIds);

    /**
     * ¿Alguien abrió ya una prueba de esta versión del editor (V67)? Es la frontera de la
     * decisión 9: hasta la primera rendición se puede publicar otra versión.
     */
    boolean existsByVersionBancoIdAndIniciadoEnIsNotNull(Long versionBancoId);

    /** Los intentos de una versión del editor: a quienes recalcula un cambio de puntos o de guía. */
    List<IntentoPrueba> findByVersionBancoId(Long versionBancoId);

    // Los que ya vencieron y nadie entregó: el sondeo los cierra solo (RF: "no existe
    // entregar tarde").
    List<IntentoPrueba> findByEntregadoEnIsNullAndIniciadoEnIsNotNullAndVenceEnBefore(Instant momento);

    /**
     * Los intentos sin entregar de una vacante, para moverles la fecha de cierre.
     *
     * <p>Va por la postulación porque el intento no guarda la vacante: la sabe su
     * postulación, y duplicarla aquí sería un segundo sitio donde se puede desincronizar.
     */
    @Query("""
            select i from IntentoPrueba i
            where i.entregadoEn is null
              and i.postulacionId in (select p.id from Postulacion p where p.vacanteId = :vacanteId)
            """)
    List<IntentoPrueba> abiertosDeLaVacante(@Param("vacanteId") Long vacanteId);

    /**
     * ¿Alguien de esta vacante ya abrió su prueba del puesto?
     *
     * <p>Es la línea que decide si la etapa técnica todavía se puede reconfigurar. La
     * frontera es {@code iniciadoEn}, no la postulación: postular no es rendir, y quien no
     * ha abierto la prueba no ha visto ningún reloj que se le pueda mover.
     *
     * <p>⚠️ <b>No sirve {@link #abiertosDeLaVacante}</b>, aunque lo parezca: aquella filtra
     * {@code entregadoEn is null} para moverles la fecha, y quien ya entregó es justamente
     * el caso más claro de «ya se midió con esta vara».
     */
    @Query("""
            select count(i) > 0 from IntentoPrueba i
            where i.iniciadoEn is not null
              and i.postulacionId in (select p.id from Postulacion p where p.vacanteId = :vacanteId)
            """)
    boolean algunoEmpezadoDeLaVacante(@Param("vacanteId") Long vacanteId);

    /**
     * De estas vacantes, las que tienen a alguien que ya abrió su prueba: la misma frontera que
     * {@link #algunoEmpezadoDeLaVacante}, de una vez para una lista («Pruebas» y sus destinos).
     */
    @Query("""
            select distinct p.vacanteId from Postulacion p
            where p.vacanteId in :vacanteIds
              and exists (select 1 from IntentoPrueba i
                          where i.postulacionId = p.id and i.iniciadoEn is not null)
            """)
    List<Long> vacantesConAlgunoEmpezado(@Param("vacanteIds") Collection<Long> vacanteIds);

    /**
     * ¿Hay alguien de esta vacante en la etapa técnica? El intento se crea al entrar en ella,
     * aunque no la haya abierto todavía (V68): desde ahí, cambiar la fecha límite pide un
     * motivo, porque ya hay a quien se le mueve.
     */
    @Query("""
            select count(i) > 0 from IntentoPrueba i
            where i.postulacionId in (select p.id from Postulacion p where p.vacanteId = :vacanteId)
            """)
    boolean algunoDeLaVacante(@Param("vacanteId") Long vacanteId);

    /**
     * La acción con la que la auditoría anota «El plazo de esta persona» (V32). La escribe
     * {@code ServicioCalificacionPruebaImpl.definirPlazo} y la lee
     * {@link #plazoPuestoAManoDespuesDe}: una sola constante para que no se desvíen.
     */
    String PLAZO_PUESTO_A_MANO = "definir_plazo_prueba";

    /**
     * ¿Alguien le cambió la fecha con «El plazo de esta persona» después de ese momento? (V89)
     *
     * <p>Es el desempate entre las dos formas de dejar {@code plazoPropio}: la fecha de «Darle
     * más tiempo» es solo el tope de su cronómetro, y la de «El plazo de esta persona» se
     * respeta tal cual (punto 16 de la spec). Se pregunta con {@code masTiempoEn}: si hay una
     * fecha puesta a mano más reciente, esa es la que rige.
     *
     * <p>⚠️ <b>Se lee de la auditoría a propósito, y no de una columna nueva.</b> Es la historia
     * completa de quién fijó cada fecha (la spec la nombra así), no se puede tocar —un trigger
     * de la V8 impide editarla o borrarla— y se escribe en la misma transacción que la fecha. Y
     * {@code masTiempoEn} sigue diciendo quién dio más tiempo, que es lo que leen la ficha y el
     * recordatorio previo al plazo. Solo se consulta al empezar, y solo si hubo más tiempo.
     */
    @Query("select count(a) > 0 from Auditoria a"
            + " where a.entidad = 'intento_prueba' and a.entidadId = :intentoId"
            + " and a.accion = '" + PLAZO_PUESTO_A_MANO + "' and a.ocurridaEn > :desde")
    boolean plazoPuestoAManoDespuesDe(@Param("intentoId") Long intentoId, @Param("desde") Instant desde);
}
