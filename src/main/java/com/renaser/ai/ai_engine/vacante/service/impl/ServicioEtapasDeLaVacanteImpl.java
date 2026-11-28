package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.RecorridoDeLaVacante;
import com.renaser.ai.ai_engine.vacante.service.ServicioEtapasDeLaVacante;
import com.renaser.ai.ai_engine.vacante.service.VacanteArchivada;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ver {@link ServicioEtapasDeLaVacante}. */
@Service
@RequiredArgsConstructor
public class ServicioEtapasDeLaVacanteImpl implements ServicioEtapasDeLaVacante {

    private static final String ESTADO_CERRADA = "CERRADA";

    private final VacanteRepository vacantes;
    private final PostulacionRepository postulaciones;
    private final RecorridoDeLaVacante recorridos;
    private final ServicioAuditoria auditoria;

    @Override
    @Transactional
    public void cambiar(ContextoUsuario quien, Long vacanteId, List<String> etapas) {
        // Bloqueada, como toda escritura de la tuerca: dos cambios seguidos se aplican en orden,
        // y el segundo parte de lo que dejó el primero.
        Vacante vacante = laQueAceptaOtrasEtapas(quien, vacanteId);

        String falla = RecorridoDeLaVacante.queFalla(etapas, recorridos.catalogo());
        if (falla != null) {
            throw new IllegalArgumentException(falla);
        }
        // Con una sola postulación, de cualquier estado, las etapas quedan fijas: alguien podría
        // estar a mitad de una, y quitarla o moverla le cambiaría el camino sin que lo sepa.
        if (postulaciones.countByVacanteId(vacante.getId()) > 0) {
            throw new IllegalStateException("Esta vacante ya tiene postulantes: sus etapas quedaron "
                    + "fijas desde el primero. Si hace falta otro recorrido, crea otra vacante.");
        }

        List<String> antes = recorridos.de(vacante.getId());
        if (antes.equals(etapas)) {
            return;
        }
        recorridos.escribir(vacante.getId(), List.copyOf(etapas));
        auditoria.registrar(quien.organizacionId(), quien, "cambiar_etapas_vacante", "vacante",
                vacante.getId(), Map.of("etapas", String.join(" → ", antes)),
                Map.of("etapas", String.join(" → ", etapas)), null);
    }

    /**
     * La vacante bloqueada, si todavía admite otras etapas: ni cerrada, ni archivada, ni
     * eliminada (409 las tres). La de otra empresa no existe: 404.
     */
    private Vacante laQueAceptaOtrasEtapas(ContextoUsuario quien, Long vacanteId) {
        Optional<Vacante> bloqueada = vacantes.bloquear(vacanteId, quien.organizacionId());
        if (bloqueada.isEmpty()) {
            boolean eliminada = vacantes.findByIdAndOrganizacionId(vacanteId, quien.organizacionId())
                    .map(v -> v.getEliminadaEn() != null)
                    .orElse(false);
            if (eliminada) {
                throw new IllegalStateException("Esta vacante se eliminó: sus etapas ya no se "
                        + "cambian");
            }
            throw new ResourceNotFoundException("Vacante", "id", vacanteId);
        }
        Vacante vacante = bloqueada.get();
        VacanteArchivada.exigirQueNoLoEste(vacante);
        if (ESTADO_CERRADA.equals(vacante.getEstado())) {
            throw new IllegalStateException("Esta vacante está cerrada: sus etapas ya no se cambian");
        }
        return vacante;
    }
}
