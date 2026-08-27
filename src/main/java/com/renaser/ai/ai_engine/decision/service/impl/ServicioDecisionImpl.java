package com.renaser.ai.ai_engine.decision.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.decision.dto.DtosDecision.*;
import com.renaser.ai.ai_engine.decision.entity.BarreraCritica;
import com.renaser.ai.ai_engine.decision.entity.BarreraDetectada;
import com.renaser.ai.ai_engine.decision.entity.Decision;
import com.renaser.ai.ai_engine.decision.entity.EvidenciaAdicional;
import com.renaser.ai.ai_engine.decision.repository.BarreraCriticaRepository;
import com.renaser.ai.ai_engine.decision.repository.BarreraDetectadaRepository;
import com.renaser.ai.ai_engine.decision.repository.DecisionRepository;
import com.renaser.ai.ai_engine.decision.repository.EvidenciaAdicionalRepository;
import com.renaser.ai.ai_engine.decision.service.ServicioDecision;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.pesos.entity.PesoEtapa;
import com.renaser.ai.ai_engine.pesos.repository.PesoEtapaRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * La decisión final.
 *
 * <p>El semáforo que {@link #verSemaforo} propone sigue este orden, y no es solo un umbral:
 *
 * <ol>
 *   <li>Si falta la nota de alguna etapa que la vacante pesa (PERFIL_INTEGRAL o
 *       PRUEBA_PUESTO), la propuesta es {@code null} — no hay con qué calcular todavía.
 *   <li>Si hay una barrera crítica confirmada, {@code ROJO}: "ningún promedio alto la tapa"
 *       (RF-115).
 *   <li>Si no, se compara la Puntuación Global contra dos umbrales configurables.
 * </ol>
 *
 * <p>⚠️ Esos dos umbrales ({@code umbral_decision_verde}, {@code umbral_decision_rojo}) no
 * están en ningún documento — es una interpretación nuestra, igual que los del grupo de
 * prioridad del hito 2, y conviene que Renaser los confirme. {@code RESERVA} no se calcula
 * nunca solo: es un juicio de la persona que decide (RF-117), disponible como una de las
 * cinco opciones al decidir.
 */
@Service
@RequiredArgsConstructor
public class ServicioDecisionImpl implements ServicioDecision {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final VacanteRepository vacantes;
    private final AlcanceSobreLaVacante alcanceVacante;
    private final BarreraCriticaRepository barrerasCriticas;
    private final BarreraDetectadaRepository barrerasDetectadas;
    private final DecisionRepository decisiones;
    private final EvidenciaAdicionalRepository evidencias;
    private final NotaEtapaRepository notasEtapa;
    private final PesoEtapaRepository pesosEtapa;
    private final MaquinaEstados maquina;
    private final ServicioParametros parametros;
    private final ServicioAuditoria auditoria;
    private final Permisos permisos;

    @Override
    @Transactional
    public Long definirBarrera(ContextoUsuario quien, Long vacanteId, CrearBarrera datos) {
        Vacante vacante = vacanteVisible(quien, vacanteId, "definir_barreras_criticas");
        BarreraCritica b = barrerasCriticas.save(BarreraCritica.builder()
                .vacanteId(vacante.getId())
                .descripcion(datos.descripcion())
                .esActiva(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "definir_barrera_critica",
                "barrera_critica", b.getId(), null, Map.of("vacanteId", vacanteId), null);
        return b.getId();
    }

    @Override
    public List<BarreraResponse> listarBarrerasDeVacante(ContextoUsuario quien, Long vacanteId) {
        vacanteVisible(quien, vacanteId, "definir_barreras_criticas");
        return barrerasCriticas.findByVacanteIdAndEsActivaTrue(vacanteId).stream()
                .map(b -> new BarreraResponse(b.getId(), b.getDescripcion(), b.isEsActiva()))
                .toList();
    }

    @Override
    @Transactional
    public Long registrarBarreraDetectada(ContextoUsuario quien, Long postulacionId, RegistrarBarrera datos) {
        Postulacion postulacion = laVisible(quien, postulacionId, "decidir_contratacion");
        // La barrera tiene que ser de la MISMA vacante que la postulación: la barrera
        // cuelga de una vacante, y sin esta comprobación se podía marcar a un candidato
        // con la barrera de otra vacante — incluso de otra empresa.
        BarreraCritica barrera = barrerasCriticas.findById(datos.barreraCriticaId())
                .filter(b -> b.getVacanteId().equals(postulacion.getVacanteId()))
                .orElseThrow(() -> new ResourceNotFoundException("Barrera crítica", "id", datos.barreraCriticaId()));

        BarreraDetectada fila = BarreraDetectada.builder()
                .postulacionId(postulacion.getId())
                .barreraCriticaId(barrera.getId())
                .explicacion(datos.explicacion())
                .confirmadaPorUsuarioId(quien.usuarioId())
                .confirmadaEn(Instant.now())
                .creadoEn(Instant.now())
                .build();
        barrerasDetectadas.save(fila);
        auditoria.registrar(quien.organizacionId(), quien, "registrar_barrera_detectada",
                "barrera_detectada", fila.getId(), null,
                Map.of("barreraCriticaId", datos.barreraCriticaId()), datos.explicacion());
        return fila.getId();
    }

    @Override
    public SemaforoResponse verSemaforo(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ver_semaforo_decision");
        return calcular(postulacion);
    }

    @Override
    @Transactional
    public void decidir(ContextoUsuario quien, Long postulacionId, Decidir datos) {
        Postulacion postulacion = laVisible(quien, postulacionId, "ver_semaforo_decision");
        Decision existente = decisiones.findByPostulacionId(postulacionId).orElse(null);

        // La primera vez hace falta decidir_contratacion; corregir una ya tomada, cambiar_decision
        // (RF-121: "cualquier persona con permiso puede cambiar una decisión, en cualquier
        // dirección"). Son públicos distintos: Talento puede cambiar, pero no decidir la primera vez.
        String permisoNecesario = existente != null && existente.getDecididaPorUsuarioId() != null
                ? "cambiar_decision" : "decidir_contratacion";
        permisos.alcanceDe(permisoNecesario);

        SemaforoResponse propuesta = calcular(postulacion);
        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException("La vacante de esta postulación ya no existe"));

        Decision fila = existente != null ? existente : Decision.builder()
                .postulacionId(postulacionId).creadoEn(Instant.now()).build();
        fila.setSemaforo(datos.semaforo());
        fila.setNotaGlobal(propuesta.notaGlobal());
        fila.setVersionPesosId(vacante.getVersionPesosId());
        fila.setDecididaPorUsuarioId(quien.usuarioId());
        fila.setMotivo(datos.motivo());
        fila.setDecididaEn(Instant.now());
        decisiones.save(fila);

        auditoria.registrar(quien.organizacionId(), quien, "decidir_postulacion",
                "decision", fila.getId(),
                existente == null ? null : Map.of("semaforo", String.valueOf(existente.getSemaforo())),
                Map.of("semaforo", datos.semaforo()), datos.motivo());

        aplicarTransicion(postulacion, datos.semaforo(), quien, datos.motivo());
    }

    @Override
    @Transactional
    public void pedirEvidenciaAdicional(ContextoUsuario quien, Long postulacionId, PedirEvidencia datos) {
        Postulacion postulacion = laVisible(quien, postulacionId, "pedir_evidencia_adicional");
        int tope = parametros.entero(quien.organizacionId(), "tope_rondas_evidencia", 2);
        List<EvidenciaAdicional> previas = evidencias.findByPostulacionIdOrderByNumero(postulacionId);
        if (previas.size() >= tope) {
            throw new IllegalStateException(
                    "Ya se pidió evidencia adicional %d veces (el tope es %d): hay que decidir con lo que hay"
                            .formatted(previas.size(), tope));
        }

        EvidenciaAdicional fila = evidencias.save(EvidenciaAdicional.builder()
                .postulacionId(postulacionId)
                .numero(previas.size() + 1)
                .motivo(datos.motivo())
                .enunciado(datos.enunciado())
                .solicitadaPorUsuarioId(quien.usuarioId())
                .creadoEn(Instant.now())
                .build());

        maquina.transicionar(postulacion, "DECISION_TURNO_CANDIDATO", quien,
                "Se pidió evidencia adicional: " + datos.motivo(), false, false, null);
        auditoria.registrar(quien.organizacionId(), quien, "pedir_evidencia_adicional",
                "evidencia_adicional", fila.getId(), null, Map.of("numero", fila.getNumero()), datos.motivo());
    }

    // ============ El cálculo ============

    private SemaforoResponse calcular(Postulacion postulacion) {
        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException("La vacante de esta postulación ya no existe"));
        List<PesoEtapa> pesos = pesosEtapa.findByVersionPesosId(vacante.getVersionPesosId());
        Map<String, NotaEtapa> notasPorEtapa = notasEtapa.findByPostulacionId(postulacion.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(NotaEtapa::getEtapaCodigo, java.util.function.Function.identity()));

        List<String> faltan = new ArrayList<>();
        BigDecimal notaGlobal = BigDecimal.ZERO;
        for (PesoEtapa pe : pesos) {
            NotaEtapa nota = notasPorEtapa.get(pe.getEtapaCodigo());
            if (nota == null) {
                faltan.add(pe.getEtapaCodigo());
                continue;
            }
            notaGlobal = notaGlobal.add(nota.getPuntaje().multiply(pe.getPeso())
                    .divide(CIEN, 4, RoundingMode.HALF_UP));
        }

        List<BarreraDetectada> confirmadas = barrerasDetectadas
                .findByPostulacionIdAndConfirmadaPorUsuarioIdIsNotNullAndDescartadaEnIsNull(postulacion.getId());
        List<BarreraDetectadaResponse> barrerasResp = confirmadas.stream()
                .map(b -> new BarreraDetectadaResponse(b.getId(), b.getBarreraCriticaId(),
                        barrerasCriticas.findById(b.getBarreraCriticaId())
                                .map(BarreraCritica::getDescripcion).orElse(""),
                        b.getExplicacion(), b.getConfirmadaEn(), b.getDescartadaEn()))
                .toList();

        String propuesta = null;
        if (faltan.isEmpty()) {
            if (!confirmadas.isEmpty()) {
                propuesta = "ROJO";
            } else {
                int umbralVerde = parametros.entero(postulacion.getOrganizacionId(), "umbral_decision_verde", 75);
                int umbralRojo = parametros.entero(postulacion.getOrganizacionId(), "umbral_decision_rojo", 50);
                if (notaGlobal.compareTo(BigDecimal.valueOf(umbralVerde)) >= 0) {
                    propuesta = "VERDE";
                } else if (notaGlobal.compareTo(BigDecimal.valueOf(umbralRojo)) >= 0) {
                    propuesta = "AMBAR";
                } else {
                    propuesta = "ROJO";
                }
            }
        }

        Decision existente = decisiones.findByPostulacionId(postulacion.getId()).orElse(null);
        return new SemaforoResponse(propuesta, faltan.isEmpty() ? notaGlobal.setScale(2, RoundingMode.HALF_UP) : null,
                faltan, barrerasResp,
                existente == null ? null : existente.getDecididaPorUsuarioId(),
                existente == null ? null : existente.getMotivo(),
                existente == null ? null : existente.getDecididaEn());
    }

    /**
     * RF-117: el resultado decide qué pasa. Verde contrata, rojo y reserva cierran, ámbar
     * espera al candidato. "Sin datos" no mueve nada: hace falta más evidencia primero.
     */
    private void aplicarTransicion(Postulacion postulacion, String semaforo, ContextoUsuario quien, String motivo) {
        switch (semaforo) {
            case "VERDE" -> maquina.transicionar(postulacion, "CONTRATADO", quien, motivo, false, false, null);
            case "ROJO" -> maquina.transicionar(postulacion, "NO_CONTINUA", quien, motivo, false, false, "DECISION_ROJA");
            case "RESERVA" -> maquina.transicionar(postulacion, "NO_CONTINUA", quien, motivo, false, false, "PASA_A_RESERVA");
            case "AMBAR" -> {
                if (!"DECISION_TURNO_CANDIDATO".equals(postulacion.getEstadoCodigo())) {
                    maquina.transicionar(postulacion, "DECISION_TURNO_CANDIDATO", quien, motivo, false, false, null);
                }
            }
            default -> { /* SIN_DATOS: se registra la propuesta, pero no se mueve a nadie */ }
        }
    }

    // ============ Apoyo ============

    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, String permiso) {
        return alcanceVacante.laPostulacionVisible(quien, postulacionId, permiso);
    }

    private Vacante vacanteVisible(ContextoUsuario quien, Long vacanteId, String permiso) {
        return alcanceVacante.laVacanteVisible(quien, vacanteId, permiso);
    }
}
