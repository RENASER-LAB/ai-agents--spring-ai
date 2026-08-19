package com.renaser.ai.ai_engine.auditoria.service;

import com.renaser.ai.ai_engine.auditoria.entity.Auditoria;
import com.renaser.ai.ai_engine.auditoria.repository.AuditoriaRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

// La auditoría se llama EXPLÍCITAMENTE desde cada caso de uso, no con un aspecto ni un
// listener de JPA: el valor anterior, el nuevo y el motivo son conocimiento del caso de
// uso, y un mecanismo automático no los tiene.
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioAuditoria {

    private final AuditoriaRepository auditorias;
    // Propio y no inyectado: Boot 4 no expone un ObjectMapper compartido en este classpath
    private final ObjectMapper json = new ObjectMapper();

    public void registrar(Long organizacionId, ContextoUsuario quien, String accion,
                          String entidad, Long entidadId,
                          Object valorAnterior, Object valorNuevo, String motivo) {
        Auditoria fila = Auditoria.builder()
                .organizacionId(organizacionId)
                .usuarioId(quien == null ? null : quien.usuarioId())
                // El primer rol del contexto: con qué sombrero actuó. Si hiciera falta
                // distinguir entre varios roles del mismo usuario, se pasa explícito.
                .rolId(quien == null || quien.rolIds().isEmpty() ? null : quien.rolIds().get(0))
                .accion(accion)
                .entidad(entidad)
                .entidadId(entidadId)
                .valorAnterior(comoJson(valorAnterior))
                .valorNuevo(comoJson(valorNuevo))
                .motivo(motivo)
                .ocurridaEn(Instant.now())
                .creadoEn(Instant.now())
                .build();
        auditorias.save(fila);
    }

    // Acción del sistema: sin usuario
    public void registrarDelSistema(Long organizacionId, String accion, String entidad,
                                    Long entidadId, Object valorNuevo) {
        registrar(organizacionId, null, accion, entidad, entidadId, null, valorNuevo, null);
    }

    private String comoJson(Object valor) {
        if (valor == null) return null;
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            // La auditoría nunca debe tumbar la operación que audita
            log.error("No se pudo serializar el valor auditado: {}", e.getMessage());
            return "{\"error\":\"no serializable\"}";
        }
    }
}
