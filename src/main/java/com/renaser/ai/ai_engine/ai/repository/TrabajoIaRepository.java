package com.renaser.ai.ai_engine.ai.repository;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrabajoIaRepository extends JpaRepository<TrabajoIa, Long> {

    List<TrabajoIa> findByPostulacionIdOrderByIdAsc(Long postulacionId);

    Optional<TrabajoIa> findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
            Long postulacionId, String agenteCodigo, String modo);

    Optional<TrabajoIa> findFirstByPostulacionIdAndAgenteCodigoOrderByIdDesc(
            Long postulacionId, String agenteCodigo);

    // Los que el sondeo tiene que volver a empujar: o nadie recogió el mensaje, o quien lo
    // recogió se murió a mitad. Ver ReintentoTrabajosIa.
    List<TrabajoIa> findByEstadoAndCreadoEnBefore(String estado, Instant limite);

    List<TrabajoIa> findByEstadoAndTomadoEnBefore(String estado, Instant limite);

    /**
     * Toma el trabajo solo si sigue pendiente, en una sola sentencia.
     *
     * <p><b>Leer y luego escribir no vale aquí.</b> Con varios consumidores a la vez, dos
     * hilos pueden leer «PENDIENTE» antes de que ninguno haya escrito, y entonces los dos
     * llaman al modelo por el mismo candidato: se paga dos veces y se guarda la nota del
     * que termine último. La condición viaja dentro del UPDATE y la resuelve la base.
     *
     * @return 1 si lo tomó este, 0 si otro se le adelantó o ya no estaba pendiente
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TrabajoIa t
               set t.estado = 'EN_CURSO',
                   t.intentos = coalesce(t.intentos, 0) + 1,
                   t.tomadoEn = :ahora
             where t.id = :id and t.estado = 'PENDIENTE'
            """)
    int tomarSiEstaPendiente(@Param("id") Long id, @Param("ahora") Instant ahora);

    // En bloque, para el ranking. Pedirlo de una en una son once consultas por
    // candidato, y esa pantalla existe justamente para ver la tanda entera.
    List<TrabajoIa> findByPostulacionIdInOrderByIdAsc(List<Long> postulacionIds);
}
