package com.renaser.ai.ai_engine.validacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.usuario.entity.Rol;
import com.renaser.ai.ai_engine.usuario.entity.UsuarioRol;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import com.renaser.ai.ai_engine.validacion.dto.DtosValidacion.*;
import com.renaser.ai.ai_engine.validacion.entity.Validacion;
import com.renaser.ai.ai_engine.validacion.repository.ValidacionRepository;
import com.renaser.ai.ai_engine.validacion.service.ServicioValidacion;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioValidacionImpl implements ServicioValidacion {

    private static final String ETAPA = "VALIDACION";
    private static final String POR_HABILITAR = "VALIDACION_POR_HABILITAR";
    private static final String TURNO_CANDIDATO = "VALIDACION_TURNO_CANDIDATO";
    private static final String POR_CONFIRMAR = "VALIDACION_POR_CONFIRMAR";

    private final ValidacionRepository validaciones;
    private final PostulacionRepository postulaciones;
    private final AlcanceSobreLaVacante alcance;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;
    private final CalificacionPorCriterio calificacion;
    private final MaquinaEstados maquina;
    private final ServicioParametros parametros;
    private final ServicioAuditoria auditoria;
    private final Permisos permisos;

    @Override
    @Transactional
    public Long crearAlEntrar(Long postulacionId, Long organizacionId) {
        int dias = parametros.entero(organizacionId, "dias_validacion_por_defecto", 7);
        Validacion fila = validaciones.save(Validacion.builder()
                .postulacionId(postulacionId)
                // Arranca en la modalidad que no necesita nada: la otra hay que habilitarla
                // a mano, con su figura contractual.
                .modalidad("SIMULACION_EXTENDIDA")
                .dias(dias)
                .estado("POR_HABILITAR")
                .creadoEn(Instant.now())
                .build());
        return fila.getId();
    }

    @Override
    public ValidacionResponse ver(ContextoUsuario quien, Long postulacionId) {
        laVisible(quien, postulacionId, "completar_metricas_validacion");
        return comoResponse(la(postulacionId));
    }

    @Override
    @Transactional
    public void habilitar(ContextoUsuario quien, Long postulacionId, HabilitarValidacion datos) {
        laVisible(quien, postulacionId, "habilitar_validacion");
        Validacion validacion = la(postulacionId);
        if (!"POR_HABILITAR".equals(validacion.getEstado())) {
            throw new IllegalStateException("Este periodo ya está " + validacion.getEstado());
        }

        // La regla que impide que una aceptación digital sustituya una obligación legal:
        // no se pone a nadie a trabajar de verdad sin figura contractual registrada (RF-107).
        if ("TRABAJO_REAL".equals(datos.modalidad())
                && (datos.tipoVinculacion() == null || datos.tipoVinculacion().isBlank())) {
            throw new IllegalArgumentException(
                    "El trabajo real exige registrar la figura contractual antes de habilitarlo");
        }

        validacion.setModalidad(datos.modalidad());
        validacion.setTipoVinculacion(datos.tipoVinculacion());
        if (datos.dias() != null) {
            validacion.setDias(datos.dias());
        }
        validacion.setResponsableUsuarioId(datos.responsableUsuarioId());
        validacion.setHabilitadaPorUsuarioId(quien.usuarioId());
        validaciones.save(validacion);

        auditoria.registrar(quien.organizacionId(), quien, "habilitar_validacion",
                "validacion", validacion.getId(), null,
                Map.of("modalidad", datos.modalidad()), null);
    }

    @Override
    @Transactional
    public void iniciar(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "iniciar_validacion");
        Validacion validacion = la(postulacionId);
        if (!"POR_HABILITAR".equals(validacion.getEstado())) {
            throw new IllegalStateException("Este periodo ya está " + validacion.getEstado());
        }
        if (validacion.getHabilitadaPorUsuarioId() == null) {
            throw new IllegalStateException("Antes de iniciar hay que habilitar el periodo");
        }

        Instant ahora = Instant.now();
        validacion.setEstado("EN_CURSO");
        validacion.setInicioEn(ahora);
        validacion.setFinEn(ahora.plus(validacion.getDias(), ChronoUnit.DAYS));
        validaciones.save(validacion);

        if (POR_HABILITAR.equals(postulacion.getEstadoCodigo())) {
            maquina.transicionar(postulacion, TURNO_CANDIDATO, quien,
                    "Empieza el periodo de validación práctica", false, false, null);
        }
    }

    @Override
    public List<MetricaResponse> verMetricas(ContextoUsuario quien, Long postulacionId) {
        laVisible(quien, postulacionId, "completar_metricas_validacion");
        return calificacion.verNotas(postulacionId, calificacion.rubricaGlobalDe(ETAPA)).stream()
                .map(v -> new MetricaResponse(v.criterioId(), v.nombre(), v.puntosMaximos(),
                        v.puntaje(), v.explicacion(), v.origen()))
                .toList();
    }

    @Override
    @Transactional
    public void completarMetrica(ContextoUsuario quien, Long postulacionId, Long criterioId,
                                 CompletarMetrica datos) {
        laVisible(quien, postulacionId, "completar_metricas_validacion");
        exigirRolQueCompleta(quien);

        List<Criterio> rubrica = calificacion.rubricaGlobalDe(ETAPA);
        calificacion.ponerNota(quien, postulacionId, rubrica, criterioId,
                datos.puntaje(), datos.explicacion());
    }

    @Override
    @Transactional
    public void cerrar(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "cerrar_validacion");
        Validacion validacion = la(postulacionId);

        calificacion.calcularNotaEtapa(postulacion, ETAPA, calificacion.rubricaGlobalDe(ETAPA));

        validacion.setEstado("TERMINADA");
        validaciones.save(validacion);

        if (POR_CONFIRMAR.equals(postulacion.getEstadoCodigo())) {
            maquina.transicionar(postulacion, "DECISION_POR_CONFIRMAR", quien,
                    "Periodo de validación cerrado y calificado", false, false, null);
        }
        auditoria.registrar(quien.organizacionId(), quien, "cerrar_validacion",
                "validacion", validacion.getId(), Map.of("estado", "EN_CURSO"),
                Map.of("estado", "TERMINADA"), null);
    }

    @Override
    @Transactional
    public void terminarVencidos() {
        List<Validacion> vencidas = validaciones.findByEstadoAndFinEnBefore("EN_CURSO", Instant.now());
        if (vencidas.isEmpty()) {
            return;
        }
        // Las postulaciones de toda la tanda de una vez. Nadie espera delante de esta tarea,
        // pero la tanda crece con los candidatos que lleguen a validación y cada consulta
        // suelta retiene el pool un poco más de lo necesario, justo cuando el portal también
        // lo está usando.
        Map<Long, Postulacion> porId = postulaciones
                .findAllById(vencidas.stream().map(Validacion::getPostulacionId).toList()).stream()
                .collect(Collectors.toMap(Postulacion::getId, Function.identity()));

        for (Validacion v : vencidas) {
            // El periodo se acabó, pero cerrarlo del todo exige las métricas: la postulación
            // pasa a esperar a una persona, no se cierra sola.
            Postulacion p = porId.get(v.getPostulacionId());
            if (p != null && TURNO_CANDIDATO.equals(p.getEstadoCodigo())) {
                maquina.transicionar(p, POR_CONFIRMAR, null, null, true, false, null);
            }
        }
    }

    // ============ Apoyo ============

    /**
     * Quién puede completar las métricas. El documento lo dice explícitamente:
     * «puede ser solo el responsable del área, solo Talento, o ambos. Arranca con ambos
     * habilitados» — así que vive en un parámetro, no en el código.
     */
    private void exigirRolQueCompleta(ContextoUsuario quien) {
        List<String> admitidos = parametros.lista(quien.organizacionId(),
                "roles_completan_metricas_validacion", List.of("TALENTO", "RESPONSABLE_AREA"));

        List<Long> rolIds = usuarioRoles.findByUsuarioId(quien.usuarioId()).stream()
                .map(UsuarioRol::getRolId).toList();
        List<String> susRoles = roles.findAllById(rolIds).stream().map(Rol::getCodigo).toList();

        if (susRoles.stream().noneMatch(admitidos::contains)) {
            throw new AccessDeniedException(
                    "Solo completan métricas los roles " + String.join(", ", admitidos)
                            + ". Se cambia en el parámetro roles_completan_metricas_validacion.");
        }
    }

    private Validacion la(Long postulacionId) {
        return validaciones.findByPostulacionId(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Validación", "postulación", postulacionId));
    }

    private ValidacionResponse comoResponse(Validacion v) {
        return new ValidacionResponse(v.getId(), v.getModalidad(), v.getTipoVinculacion(),
                v.getDias(), v.getInicioEn(), v.getFinEn(), v.getEstado(),
                v.getHabilitadaPorUsuarioId(), v.getResponsableUsuarioId());
    }

    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, String permiso) {
        return alcance.laPostulacionVisible(quien, postulacionId, permiso);
    }
}
