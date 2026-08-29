package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlantillaEvaluacionRepository extends JpaRepository<PlantillaEvaluacion, Long> {
    List<PlantillaEvaluacion> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);
    Optional<PlantillaEvaluacion> findByIdAndOrganizacionId(Long id, Long organizacionId);

    /**
     * La plantilla publicada de un nivel, para una organización.
     *
     * <p>Existe porque la vacante dejó de elegirla: desde que las cuotas se retiraron
     * (ver {@code ServicioEvaluacionImpl.armarOrden}) la plantilla no decide qué preguntas
     * caen, y el backend ya exigía que su nivel fuera el del puesto. Con una sola publicada
     * por nivel, elegirla era una pregunta con una única respuesta legal — así que se
     * resuelve aquí, igual que ya se resolvía el banco.
     *
     * <p>Gemela de {@code VersionBancoRepository.laPublicadaDelNivel}, y con el mismo
     * desempate: si un día hay dos del mismo nivel, gana la publicada más recientemente. No
     * inventa una regla nueva, y {@code asignarPlantillaEvaluacion} sigue siendo el escape
     * para fijar otra a mano.
     */
    @Query("""
            select p from PlantillaEvaluacion p
             where p.organizacionId = :organizacionId and p.nivelPuestoCodigo = :nivel
               and p.estado = 'PUBLICADA'
             order by p.publicadaEn desc limit 1""")
    Optional<PlantillaEvaluacion> laPublicadaDelNivel(@Param("organizacionId") Long organizacionId,
                                                      @Param("nivel") String nivel);
}
