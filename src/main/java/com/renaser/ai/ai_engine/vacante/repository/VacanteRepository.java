package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VacanteRepository extends JpaRepository<Vacante, Long> {
    List<Vacante> findByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(Long organizacionId, String estado);

    // El tablón público del portal: las publicadas de TODAS las empresas juntas. Es la
    // única consulta deliberadamente transversal — eso es ser plataforma tipo Indeed.
    List<Vacante> findByEstadoOrderByPublicadaEnDesc(String estado);
    List<Vacante> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);

    /**
     * La lista de todos los días: las de esta empresa que nadie ha archivado (V59).
     *
     * <p>El filtro es del servidor y no de la pantalla, y ahí está el punto: buscar, filtrar
     * por estado o paginar sobre una lista ya recortada en el navegador haría reaparecer las
     * archivadas en cuanto alguien escribiera en el buscador.
     */
    List<Vacante> findByOrganizacionIdAndArchivadaEnIsNullOrderByCreadoEnDesc(Long organizacionId);

    /** Las archivadas, la última archivada arriba: es el orden en que se busca una. */
    List<Vacante> findByOrganizacionIdAndArchivadaEnIsNotNullOrderByArchivadaEnDesc(
            Long organizacionId);

    /** Cuántas archivadas tiene la empresa: el número del botón «Archivadas (N)». */
    long countByOrganizacionIdAndArchivadaEnIsNotNull(Long organizacionId);

    /**
     * Si esa vacante está archivada, sin traerse la fila.
     *
     * <p>La usan las acciones que llegan por una postulación —moverla, reabrir su
     * evaluación—: de su vacante solo hace falta saber esto, y pedirla entera por id suelto
     * sería además la búsqueda que la regla de arquitectura vigila. Ver {@code
     * VacanteArchivada}.
     */
    boolean existsByIdAndArchivadaEnIsNotNull(Long id);

    /**
     * Marca la vacante como archivada <b>solo si todavía no lo estaba</b>.
     *
     * <p><b>Por qué no basta con mirar y luego escribir.</b> Archivar se pulsa con prisa y el
     * botón está donde el ratón ya estaba: un doble clic manda dos peticiones que llegan con
     * milisegundos de diferencia. Leyendo primero y escribiendo después, las dos leen «no
     * archivada», las dos archivan y la auditoría queda diciendo que la vacante pasó dos veces
     * de no archivada a archivada — y {@code archivada_en} se queda con la segunda fecha, así
     * que la traza ya no puede contestar cuándo se archivó.
     *
     * <p>La condición viaja <b>dentro</b> del UPDATE, que es lo único que Postgres evalúa de
     * forma atómica: la segunda transacción espera a que la primera confirme, vuelve a mirar
     * el {@code where} contra la fila ya escrita y actualiza cero filas. Quien llama traduce
     * ese cero en el rechazo «ya está archivada», sin efectos duplicados.
     *
     * <p>⚠️ {@code clearAutomatically} no es decorado: una consulta masiva no pasa por el
     * contexto de persistencia, así que la entidad cargada se quedaría con su
     * {@code archivadaEn} viejo y Hibernate lo escribiría encima al vaciar. Se limpia para que
     * no haya dos versiones de la misma fila.
     *
     * @return 1 si esta llamada fue la que archivó; 0 si otra se le adelantó
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Vacante v set v.archivadaEn = :cuando "
            + "where v.id = :id and v.archivadaEn is null")
    int archivarSiNoLoEstaba(@Param("id") Long id, @Param("cuando") Instant cuando);

    /** La vuelta, con la misma condición dentro: solo desarchiva quien la encuentre archivada. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Vacante v set v.archivadaEn = null "
            + "where v.id = :id and v.archivadaEn is not null")
    int desarchivarSiLoEstaba(@Param("id") Long id);

    List<Vacante> findByOrganizacionIdAndResponsableUsuarioIdOrderByCreadoEnDesc(
            Long organizacionId, Long responsableUsuarioId);
    Optional<Vacante> findByIdAndOrganizacionId(Long id, Long organizacionId);
    boolean existsBySolicitudTalentoId(Long solicitudTalentoId);
}
