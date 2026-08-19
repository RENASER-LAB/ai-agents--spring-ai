package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.CorreoEnviado;
import com.renaser.ai.ai_engine.notificacion.repository.CorreoEnviadoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

// Plantilla activa -> reemplazo de {{variables}} -> fila en correo_enviado con el texto
// EXACTO que salió -> transporte. Si un candidato reclama meses después, se lee lo que
// se le dijo, no lo que la plantilla dice hoy.
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioCorreo {

    private final PlantillaCorreoRepository plantillas;
    private final CorreoEnviadoRepository enviados;
    private final EnviadorCorreo transporte;

    public void enviar(Long organizacionId, Long usuarioId, String correoDestino,
                       String codigoPlantilla, Map<String, String> variables) {
        var plantilla = plantillas
                .findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(organizacionId, codigoPlantilla)
                .orElse(null);
        if (plantilla == null) {
            // Que falte una plantilla no puede frenar una postulación. Se anota y se sigue.
            log.error("No hay plantilla activa «{}» para la organización {}", codigoPlantilla, organizacionId);
            return;
        }

        String asunto = reemplazar(plantilla.getAsunto(), variables);
        String cuerpo = reemplazar(plantilla.getCuerpo(), variables);

        enviados.save(CorreoEnviado.builder()
                .usuarioId(usuarioId)
                .plantillaCorreoCodigo(plantilla.getCodigo())
                .versionPlantilla(plantilla.getVersion())
                .asunto(asunto)
                .cuerpo(cuerpo)
                .canal("CORREO")
                .enviadoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());

        if (correoDestino != null) {
            transporte.enviar(correoDestino, asunto, cuerpo);
        }
    }

    private String reemplazar(String texto, Map<String, String> variables) {
        String resultado = texto;
        for (var v : variables.entrySet()) {
            resultado = resultado.replace("{{" + v.getKey() + "}}", v.getValue() == null ? "" : v.getValue());
        }
        return resultado;
    }
}
