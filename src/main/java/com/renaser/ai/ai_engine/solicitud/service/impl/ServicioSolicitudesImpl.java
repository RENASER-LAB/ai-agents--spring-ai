package com.renaser.ai.ai_engine.solicitud.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.solicitud.service.ServicioSolicitudes;
import com.renaser.ai.ai_engine.solicitud.dto.DtosSolicitud.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.solicitud.entity.*;
import com.renaser.ai.ai_engine.solicitud.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServicioSolicitudesImpl implements ServicioSolicitudes {

    private final SolicitudTalentoRepository solicitudes;
    private final ResultadoEsperadoRepository resultados;
    private final ServicioAuditoria auditoria;
    private final Permisos permisos;

    @Override
    @Transactional
    public Long crear(ContextoUsuario quien, CrearSolicitud datos) {
        SolicitudTalento solicitud = solicitudes.save(SolicitudTalento.builder()
                .organizacionId(quien.organizacionId())
                .origen("DIRECTA")
                .urgencia(datos.urgencia())
                .estado("BORRADOR")
                .areaId(datos.areaId())
                .nivelPuestoCodigo(datos.nivelPuestoCodigo())
                .familiaCodigo(datos.familiaCodigo())
                .resultadoPrincipal(datos.resultadoPrincipal())
                .motivo(datos.motivo())
                .consecuenciaNoContratar(datos.consecuenciaNoContratar())
                .requeridaPara(datos.requeridaPara())
                .analisisCapacidad(datos.analisisCapacidad())
                .capacidadesIndispensables(datos.capacidadesIndispensables())
                .capacidadesAprendibles(datos.capacidadesAprendibles())
                .modalidad(datos.modalidad())
                .horario(datos.horario())
                .compensacion(datos.compensacion())
                .solicitadaPorUsuarioId(quien.usuarioId())
                .responsableUsuarioId(datos.responsableUsuarioId())
                .creadoEn(Instant.now())
                .build());

        int orden = 1;
        for (ResultadoEsperadoDto r : datos.resultadosEsperados()) {
            resultados.save(ResultadoEsperado.builder()
                    .solicitudTalentoId(solicitud.getId())
                    .descripcion(r.descripcion())
                    .indicador(r.indicador())
                    .orden(orden++)
                    .creadoEn(Instant.now())
                    .build());
        }

        auditoria.registrar(quien.organizacionId(), quien, "crear_solicitud",
                "solicitud_talento", solicitud.getId(), null,
                Map.of("estado", "BORRADOR", "urgencia", datos.urgencia()), null);
        return solicitud.getId();
    }

    @Override
    public List<SolicitudResumen> listar(ContextoUsuario quien) {
        FiltroAlcance alcance = permisos.alcanceDe("ver_solicitudes");
        List<SolicitudTalento> filas = switch (alcance.tipo()) {
            case TODO -> solicitudes.findByOrganizacionIdOrderByCreadoEnDesc(quien.organizacionId());
            // «Solo lo suyo» para el responsable del área: las solicitudes a su cargo
            default -> solicitudes.findByOrganizacionIdAndResponsableUsuarioIdOrderByCreadoEnDesc(
                    quien.organizacionId(), quien.usuarioId());
        };
        return filas.stream().map(this::comoResumen).toList();
    }

    @Override
    public SolicitudDetalle detalle(ContextoUsuario quien, Long id) {
        SolicitudTalento s = laVisible(quien, id);
        List<ResultadoEsperadoDto> rs = resultados.findBySolicitudTalentoIdOrderByOrden(id).stream()
                .map(r -> new ResultadoEsperadoDto(r.getDescripcion(), r.getIndicador()))
                .toList();
        return new SolicitudDetalle(comoResumen(s), s.getMotivo(), s.getConsecuenciaNoContratar(),
                s.getAnalisisCapacidad(), s.getCapacidadesIndispensables(), s.getCapacidadesAprendibles(),
                s.getRequeridaPara(), s.getNivelPuestoCodigo(), s.getFamiliaCodigo(), rs);
    }

    @Override
    @Transactional
    public void aprobar(ContextoUsuario quien, Long id, String motivo) {
        SolicitudTalento s = laVisible(quien, id);
        if (!"BORRADOR".equals(s.getEstado())) {
            throw new IllegalStateException("Solo se aprueba una solicitud en borrador; esta está " + s.getEstado());
        }
        s.setEstado("ABIERTA");
        solicitudes.save(s);
        auditoria.registrar(quien.organizacionId(), quien, "aprobar_solicitud",
                "solicitud_talento", id, Map.of("estado", "BORRADOR"), Map.of("estado", "ABIERTA"), motivo);
    }

    @Override
    @Transactional
    public void rechazar(ContextoUsuario quien, Long id, String motivo) {
        SolicitudTalento s = laVisible(quien, id);
        if (!"BORRADOR".equals(s.getEstado()) && !"ABIERTA".equals(s.getEstado())) {
            throw new IllegalStateException("Esta solicitud ya no se puede rechazar: está " + s.getEstado());
        }
        String anterior = s.getEstado();
        s.setEstado("RECHAZADA");
        solicitudes.save(s);
        auditoria.registrar(quien.organizacionId(), quien, "rechazar_solicitud",
                "solicitud_talento", id, Map.of("estado", anterior), Map.of("estado", "RECHAZADA"), motivo);
    }

    private SolicitudTalento laVisible(ContextoUsuario quien, Long id) {
        SolicitudTalento s = solicitudes.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de Talento", "id", id));
        FiltroAlcance alcance = permisos.alcanceDe("ver_solicitudes");
        if (alcance.tipo() != FiltroAlcance.Tipo.TODO
                && !quien.usuarioId().equals(s.getResponsableUsuarioId())) {
            throw new ResourceNotFoundException("Solicitud de Talento", "id", id);
        }
        return s;
    }

    private SolicitudResumen comoResumen(SolicitudTalento s) {
        return new SolicitudResumen(s.getId(), s.getEstado(), s.getUrgencia(), s.getAreaId(),
                s.getResultadoPrincipal(), s.getCreadoEn());
    }
}
