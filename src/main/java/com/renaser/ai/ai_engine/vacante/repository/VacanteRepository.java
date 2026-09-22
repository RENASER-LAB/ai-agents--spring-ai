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
    //
    // ⚠️ Desde la V60 el tablón pide las publicadas QUE SIGUEN EXISTIENDO: ver
    // findByEstadoAndEliminadaEnIsNullOrderByPublicadaEnDesc. Esta se conserva porque sigue
    // habiendo quien necesita la lista cruda —soporte, guiones— y porque renombrarla no
    // habría impedido volver a usar la equivocada.
    List<Vacante> findByEstadoOrderByPublicadaEnDesc(String estado);

    /** El tablón de verdad: publicadas y no eliminadas (V60). */
    List<Vacante> findByEstadoAndEliminadaEnIsNullOrderByPublicadaEnDesc(String estado);

    List<Vacante> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);

    /**
     * La lista de todos los días: las de esta empresa que nadie ha archivado <b>ni
     * eliminado</b> (V59, V60).
     *
     * <p>El filtro es del servidor y no de la pantalla, y ahí está el punto: buscar, filtrar
     * por estado o paginar sobre una lista ya recortada en el navegador haría reaparecer las
     * archivadas en cuanto alguien escribiera en el buscador.
     */
    List<Vacante> findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(
            Long organizacionId);

    /**
     * Las archivadas, la última archivada arriba: es el orden en que se busca una.
     *
     * <p>Una eliminada no sale aquí aunque estuviera archivada: eliminar la retira de las
     * <b>dos</b> listas del panel, que es justo lo que la distingue de archivar.
     */
    List<Vacante> findByOrganizacionIdAndArchivadaEnIsNotNullAndEliminadaEnIsNullOrderByArchivadaEnDesc(
            Long organizacionId);

    /** Cuántas archivadas tiene la empresa: el número del botón «Archivadas (N)». */
    long countByOrganizacionIdAndArchivadaEnIsNotNullAndEliminadaEnIsNull(Long organizacionId);

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

    /**
     * Marca la vacante como eliminada <b>solo si todavía existía</b> (V60).
     *
     * <p>Aquí la condición dentro del UPDATE hace más que evitar una fila de auditoría
     * repetida. Eliminar arrastra tres cosas más —cerrar las postulaciones en carrera, dejar
     * un aviso a cada una y devolver la solicitud a {@code ABIERTA}—, así que dos peticiones
     * que se creyeran las dos «la primera» cerrarían lo ya cerrado y mandarían dos campanas
     * por el mismo hecho. Con la condición dentro, la segunda transacción espera a que la
     * primera confirme, vuelve a mirar el {@code where} contra la fila ya escrita y actualiza
     * cero filas; quien llama traduce ese cero en el 404 de «esta vacante ya no existe».
     *
     * <p>⚠️ <b>Va lo primero de la operación, no lo último.</b> Es el punto donde las dos
     * peticiones se ordenan: si se escribiera al final, las dos habrían cerrado ya las
     * postulaciones antes de que ninguna descubriera que llegaba tarde.
     *
     * <p>⚠️ {@code clearAutomatically} vacía el contexto de persistencia, así que la entidad
     * cargada antes queda desligada: lo que haga falta de ella —el título, la solicitud— se
     * copia a variables ANTES de llamar aquí.
     *
     * @return 1 si esta llamada fue la que la eliminó; 0 si otra se le adelantó
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Vacante v set v.eliminadaEn = :cuando "
            + "where v.id = :id and v.eliminadaEn is null")
    int eliminarSiSeguiaViva(@Param("id") Long id, @Param("cuando") Instant cuando);

    /**
     * El pestillo que sujeta la vacante viva mientras alguien postula a ella (V60).
     *
     * <p><b>El problema que resuelve.</b> Leer {@code eliminada_en} al empezar a postular no
     * basta: entre esa lectura y el {@code commit} caben varios segundos —se sube el
     * currículum, se firma el consentimiento, se manda a leer el CV— y en ese hueco cabe
     * entera una eliminación. Las dos transacciones se creen en lo cierto: la que elimina
     * lista a quién cerrar antes de que la postulación exista, y la que postula confirma
     * sobre una vacante que ya no existe. El resultado era una postulación abierta, sin
     * cerrar y sin aviso, colgando de una convocatoria retirada: no la ve el candidato en
     * «Mis procesos», no la ve el panel en su ranking, y nadie puede cerrarla.
     *
     * <p><b>Cómo lo resuelve.</b> {@code FOR SHARE} es un cerrojo COMPARTIDO: dos personas
     * pueden postular a la vez a la misma vacante sin esperarse —es lo normal y no puede
     * costar una cola—, pero choca con el {@code UPDATE} de {@link #eliminarSiSeguiaViva}.
     * A partir de ahí solo quedan dos desenlaces, y los dos son correctos:
     *
     * <ul>
     *   <li>La postulación llegó primero: la eliminación espera a que confirme y entonces
     *       encuentra su fila y la cierra con su aviso, como a las demás.
     *   <li>La eliminación llegó primero: el {@code where} se vuelve a mirar contra la fila
     *       ya escrita, no devuelve nada, y quien postulaba recibe el mismo 404 que si
     *       hubiera enviado el formulario un segundo más tarde — antes de subir nada.
     * </ul>
     *
     * <p>⚠️ <b>Va al principio de postular, no al final.</b> Al final el cerrojo duraría
     * menos, pero la postulación que perdiera la carrera se desharía DESPUÉS de haber
     * escrito el currículum en el almacén, y ese archivo se queda huérfano. Al principio,
     * quien llega tarde se entera antes de subir nada.
     *
     * @return una fila si la vacante existe y no está eliminada; vacío si no
     */
    @Query(value = "select 1 from vacante where id = :id and eliminada_en is null for share",
            nativeQuery = true)
    Optional<Integer> bloquearSiSigueViva(@Param("id") Long id);

    List<Vacante> findByOrganizacionIdAndResponsableUsuarioIdOrderByCreadoEnDesc(
            Long organizacionId, Long responsableUsuarioId);

    /**
     * La vacante de esta empresa, exista o no ya.
     *
     * <p>⚠️ <b>Casi nadie quiere esta</b>: desde la V60 lo que el panel busca es
     * {@link #findByIdAndOrganizacionIdAndEliminadaEnIsNull}, que es la que contesta 404
     * sobre una eliminada. Esta se queda para quien de verdad necesita leer una eliminada —el
     * conteo de avisos viejos, soporte— y para no romper lo que ya la usaba bien.
     */
    Optional<Vacante> findByIdAndOrganizacionId(Long id, Long organizacionId);

    /**
     * La vacante de esta empresa <b>que todavía existe</b> (V60).
     *
     * <p>Es la puerta por la que entra el panel entero: el guardián del alcance, el detalle,
     * el ranking, su Excel, la prueba, el cuestionario técnico y la selección de vacantes de
     * una sesión de simulación. Que una eliminada no salga de aquí es lo que hace que todas
     * esas pantallas contesten 404 sin que ninguna tenga que acordarse de preguntarlo.
     */
    Optional<Vacante> findByIdAndOrganizacionIdAndEliminadaEnIsNull(Long id, Long organizacionId);

    /**
     * Si esa vacante está eliminada, sin traerse la fila.
     *
     * <p>Mismo uso que su pariente del archivo: las acciones que llegan por una postulación
     * solo necesitan saber esto de su vacante, y pedirla entera por id suelto sería además la
     * búsqueda que la regla de arquitectura vigila.
     */
    boolean existsByIdAndEliminadaEnIsNotNull(Long id);

    /** De un lote de vacantes, las que están eliminadas: para apagar enlaces viejos. */
    @Query("select v.id from Vacante v where v.id in :ids and v.eliminadaEn is not null")
    List<Long> idsEliminadasDe(@Param("ids") List<Long> ids);

    boolean existsBySolicitudTalentoId(Long solicitudTalentoId);
}
