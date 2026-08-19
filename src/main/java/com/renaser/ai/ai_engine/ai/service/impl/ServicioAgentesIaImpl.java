package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.dto.DtosAgentesIa.*;
import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.mapper.AgentesIaMapper;
import com.renaser.ai.ai_engine.ai.model.InstruccionIa;
import com.renaser.ai.ai_engine.ai.repository.AgenteRepository;
import com.renaser.ai.ai_engine.ai.repository.InstruccionIaRepository;
import com.renaser.ai.ai_engine.ai.service.ServicioAgentesIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServicioAgentesIaImpl implements ServicioAgentesIa {

    private final AgenteRepository agentes;
    private final InstruccionIaRepository instrucciones;
    private final AgentesIaMapper mapper;
    private final ServicioAuditoria auditoria;

    @Override
    public List<AgenteResponse> listarAgentes(ContextoUsuario quien) {
        return agentes.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public Long crearInstruccion(ContextoUsuario quien, CrearInstruccion datos) {
        agentes.findById(datos.agenteCodigo())
                .orElseThrow(() -> new ResourceNotFoundException("Agente", "codigo", datos.agenteCodigo()));
        int siguienteVersion = instrucciones.findByAgenteCodigoOrderByVersionDesc(datos.agenteCodigo()).stream()
                .findFirst().map(i -> i.getVersion() + 1).orElse(1);
        InstruccionIa instruccion = instrucciones.save(InstruccionIa.builder()
                .agenteCodigo(datos.agenteCodigo())
                .version(siguienteVersion)
                .texto(datos.texto())
                .esActiva(false)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_instruccion_ia",
                "instruccion_ia", instruccion.getId(), null,
                Map.of("agenteCodigo", datos.agenteCodigo(), "version", siguienteVersion), null);
        return instruccion.getId();
    }

    @Override
    @Transactional
    public void publicarInstruccion(ContextoUsuario quien, Long id) {
        InstruccionIa nueva = instrucciones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Instrucción de IA", "id", id));
        if (nueva.isEsActiva()) {
            throw new IllegalStateException("Esta instrucción ya está activa");
        }
        instrucciones.findFirstByAgenteCodigoAndEsActivaTrue(nueva.getAgenteCodigo())
                .ifPresent(anterior -> {
                    anterior.setEsActiva(false);
                    instrucciones.save(anterior);
                });
        nueva.setEsActiva(true);
        nueva.setPublicadaPorUsuarioId(quien.usuarioId());
        nueva.setPublicadaEn(Instant.now());
        instrucciones.save(nueva);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_instruccion_ia",
                "instruccion_ia", id, null, Map.of("agenteCodigo", nueva.getAgenteCodigo(),
                        "version", nueva.getVersion()), null);
    }

    @Override
    public List<InstruccionResponse> listarInstrucciones(ContextoUsuario quien, String agenteCodigo) {
        return instrucciones.findByAgenteCodigoOrderByVersionDesc(agenteCodigo).stream()
                .map(mapper::toResponse).toList();
    }
}
