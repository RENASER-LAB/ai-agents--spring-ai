package com.renaser.ai.ai_engine.colaborador.service;

import com.renaser.ai.ai_engine.colaborador.entity.PeriodoLaboral;
import com.renaser.ai.ai_engine.colaborador.repository.PeriodoLaboralRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lo que la ficha del postulante ofrece sobre su ficha de colaborador, una vez contratado.
 *
 * <p>Son pistas para pintar, como los demás {@code puedeX}: quien decide sigue siendo cada
 * endpoint de colaboradores.
 */
@Component
@RequiredArgsConstructor
public class ContratacionEnLaFicha {

    private final PeriodoLaboralRepository periodos;

    /**
     * @param colaboradorId       la ficha que salió de esta contratación, solo para quien puede
     *                            verla (con cualquier otro alcance no alcanza a nadie)
     * @param puedeDarDeAlta      contratado, sin ficha todavía y con editar_colaboradores
     * @param puedeVerColaborador ya tiene ficha y quien mira tiene ver_colaboradores
     */
    public record Vinculo(Long colaboradorId, boolean puedeDarDeAlta, boolean puedeVerColaborador) {}

    public Vinculo de(ContextoUsuario quien, Postulacion postulacion) {
        if (!"CONTRATADO".equals(postulacion.getEstadoCodigo())) {
            return new Vinculo(null, false, false);
        }
        Long colaboradorId = periodos.findFirstByPostulacionId(postulacion.getId())
                .map(PeriodoLaboral::getColaboradorId).orElse(null);
        boolean ve = "TODO".equals(quien.alcance("ver_colaboradores"));
        boolean edita = "TODO".equals(quien.alcance("editar_colaboradores"));
        return new Vinculo(ve ? colaboradorId : null, colaboradorId == null && edita,
                ve && colaboradorId != null);
    }
}
