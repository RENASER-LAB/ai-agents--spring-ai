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
import com.renaser.ai.ai_engine.organizacion.repository.AreaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioSolicitudesImpl implements ServicioSolicitudes {

    private final SolicitudTalentoRepository solicitudes;
    private final ResultadoEsperadoRepository resultados;
    private final PuestoRepository puestos;
    private final AreaRepository areas;
    private final ServicioAuditoria auditoria;
    private final Permisos permisos;

    @Override
    @Transactional
    public Long crear(ContextoUsuario quien, CrearSolicitud datos) {
        Puesto puesto = puestos.findByIdAndOrganizacionId(datos.puestoId(), quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Puesto", "id", datos.puestoId()));
        if (!puesto.isEsActivo()) {
            throw new IllegalStateException("El puesto seleccionado está inactivo");
        }
        exigirCoincidencia("nivel", datos.nivelPuestoCodigo(), puesto.getNivelPuestoCodigo());
        exigirCoincidencia("familia", datos.familiaCodigo(), puesto.getFamiliaCodigo());

        /*
         * ⚠️ El área, contra la organización, igual que el puesto de arriba. La clave ajena
         * `solicitud_talento.area_id -> area(id)` solo exige que el área exista, no que sea de
         * esta empresa: sin esto, mandar el id de un área ajena daba un 201 y dejaba una
         * solicitud de esta empresa colgando de la estructura de otra. Esa fila después es
         * invisible para todo lo que consulta por organización —entre otras cosas, para el
         * recuento que decide si un área se puede borrar—.
         */
        areas.findByIdAndOrganizacionId(datos.areaId(), quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Área", "id", datos.areaId()));

        SolicitudTalento solicitud = solicitudes.save(SolicitudTalento.builder()
                .organizacionId(quien.organizacionId())
                .origen("DIRECTA")
                .urgencia(datos.urgencia())
                .estado("BORRADOR")
                .areaId(datos.areaId())
                .puestoId(puesto.getId())
                .nivelPuestoCodigo(puesto.getNivelPuestoCodigo())
                .familiaCodigo(puesto.getFamiliaCodigo())
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
                Map.of("estado", "BORRADOR", "urgencia", datos.urgencia(),
                        "puesto", puesto.getId()), null);
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
        Map<Long, Puesto> puestosPorId = puestos.findAllById(filas.stream()
                        .map(SolicitudTalento::getPuestoId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Puesto::getId, Function.identity()));
        return filas.stream().map(s -> comoResumen(s, puestosPorId.get(s.getPuestoId()))).toList();
    }

    @Override
    public SolicitudDetalle detalle(ContextoUsuario quien, Long id) {
        SolicitudTalento s = laVisible(quien, id);
        List<ResultadoEsperadoDto> rs = resultados.findBySolicitudTalentoIdOrderByOrden(id).stream()
                .map(r -> new ResultadoEsperadoDto(r.getDescripcion(), r.getIndicador()))
                .toList();
        return new SolicitudDetalle(comoResumen(s, puestoDe(s, quien.organizacionId())), s.getMotivo(), s.getConsecuenciaNoContratar(),
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

    private Puesto puestoDe(SolicitudTalento solicitud, Long organizacionId) {
        if (solicitud.getPuestoId() == null) {
            return null;
        }
        return puestos.findByIdAndOrganizacionId(solicitud.getPuestoId(), organizacionId).orElse(null);
    }

    private void exigirCoincidencia(String campo, String recibido, String esperado) {
        if (recibido != null && !recibido.isBlank() && !recibido.equals(esperado)) {
            throw new IllegalArgumentException("El " + campo + " enviado no coincide con el puesto seleccionado");
        }
    }

    private SolicitudResumen comoResumen(SolicitudTalento s, Puesto puesto) {
        return new SolicitudResumen(s.getId(), s.getEstado(), s.getUrgencia(), s.getAreaId(),
                s.getPuestoId(), puesto == null ? null : puesto.getNombre(),
                s.getNivelPuestoCodigo(), s.getFamiliaCodigo(),
                s.getResultadoPrincipal(), s.getCreadoEn());
    }
}
