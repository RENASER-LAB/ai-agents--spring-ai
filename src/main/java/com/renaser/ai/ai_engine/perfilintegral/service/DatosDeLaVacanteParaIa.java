package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.DatosDeLaVacante;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.entity.Familia;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.FamiliaRepository;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Los datos de la vacante que reciben la IA que califica lo abierto y la que recomienda
 * preguntas (método PUNTOS): el mismo conjunto para las dos, armado en un solo sitio.
 *
 * <p><b>Son contexto, no la vara.</b> Se leen como estén en el momento: editar la
 * descripción de la vacante no recalifica a nadie. Si lo que se exige cambia, lo que se
 * corrige es la guía o el «qué debe tener», y eso sí recalifica a todos.
 *
 * <p>Todo por la organización de la vacante, nunca por id suelto: la vacante ya se resolvió
 * y de ella sale de quién son su puesto y su solicitud.
 */
@Component
@RequiredArgsConstructor
public class DatosDeLaVacanteParaIa {

    private final PuestoRepository puestos;
    private final SolicitudTalentoRepository solicitudes;
    private final FamiliaRepository familias;

    public DatosDeLaVacante de(Vacante vacante) {
        Puesto puesto = vacante.getPuestoId() == null ? null
                : puestos.findByIdAndOrganizacionId(vacante.getPuestoId(),
                        vacante.getOrganizacionId()).orElse(null);
        SolicitudTalento solicitud = vacante.getSolicitudTalentoId() == null ? null
                : solicitudes.findByIdAndOrganizacionId(vacante.getSolicitudTalentoId(),
                        vacante.getOrganizacionId()).orElse(null);
        String familia = puesto == null || puesto.getFamiliaCodigo() == null ? null
                : familias.findById(puesto.getFamiliaCodigo()).map(Familia::getNombre)
                        .orElse(puesto.getFamiliaCodigo());
        return new DatosDeLaVacante(
                vacante.getTitulo(),
                vacante.getDescripcion(),
                vacante.getProposito(),
                vacante.getResponsabilidades(),
                vacante.getRequisitos(),
                puesto == null ? null : puesto.getNombre(),
                puesto == null ? null : puesto.getNivelPuestoCodigo(),
                familia,
                solicitud == null ? null : solicitud.getResultadoPrincipal(),
                solicitud == null ? null : solicitud.getCapacidadesIndispensables(),
                solicitud == null ? null : solicitud.getCapacidadesAprendibles());
    }
}
