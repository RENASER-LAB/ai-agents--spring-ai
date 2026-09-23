package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.CorreoEnviado;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreo;
import com.renaser.ai.ai_engine.notificacion.repository.CorreoEnviadoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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
        enviarCon(plantilla, usuarioId, correoDestino, variables);
    }

    /**
     * La plantilla activa de la organización o, si a ella le falta, la de la organización
     * de recambio —la plataforma—.
     *
     * <p>Existe para los correos que no pueden perderse en silencio. {@link #enviar} omite
     * el envío cuando falta la plantilla, y eso vale para un aviso de postulación; no para el
     * enlace de una contraseña nueva, donde la pantalla ya le dijo a la persona que revise su
     * correo. Las empresas dadas de alta antes de que existiera una plantilla no la tienen
     * (el alta copia las de la plataforma una sola vez), y la de la plataforma dice lo mismo.
     *
     * <p>Vacío solo si no la tiene ninguna de las dos; quien llama decide qué hacer, y lo
     * que no puede hacer es prometer un correo que no va a salir.
     */
    public Optional<PlantillaCorreo> plantillaConRecambio(Long organizacionId, Long recambioId,
                                                          String codigoPlantilla) {
        return plantillas
                .findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(organizacionId, codigoPlantilla)
                .or(() -> recambioId == null || recambioId.equals(organizacionId)
                        ? Optional.empty()
                        : plantillas.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                                recambioId, codigoPlantilla));
    }

    /**
     * Arma y envía con una plantilla ya elegida: la fila de {@code correo_enviado} con el
     * texto exacto, y después el transporte. Devuelve cómo acabó el intento, que es lo
     * mismo que queda escrito en {@code estado_entrega}.
     */
    public EnviadorCorreo.Resultado enviarCon(PlantillaCorreo plantilla, Long usuarioId,
                                              String correoDestino, Map<String, String> variables) {
        String asunto = reemplazar(plantilla.getAsunto(), variables);
        String cuerpo = reemplazar(plantilla.getCuerpo(), variables);

        CorreoEnviado registro = enviados.save(CorreoEnviado.builder()
                .usuarioId(usuarioId)
                .plantillaCorreoCodigo(plantilla.getCodigo())
                .versionPlantilla(plantilla.getVersion())
                .asunto(asunto)
                .cuerpo(cuerpo)
                .canal("CORREO")
                .enviadoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());

        // El registro se guarda ANTES de intentar el envio: lo que se le dijo al candidato
        // queda escrito aunque el servidor de correo se caiga a mitad. Despues se anota como
        // acabo el intento, porque una fila que dice «enviado» cuando nadie lo recibio es
        // peor que no tener la fila: se descubre cuando el candidato reclama.
        EnviadorCorreo.Resultado resultado = correoDestino == null
                ? EnviadorCorreo.Resultado.NO_ENVIADO
                : transporte.enviar(correoDestino, asunto, cuerpo);

        registro.setEstadoEntrega(resultado.name());
        enviados.save(registro);
        return resultado;
    }

    private String reemplazar(String texto, Map<String, String> variables) {
        String resultado = texto;
        for (var v : variables.entrySet()) {
            resultado = resultado.replace("{{" + v.getKey() + "}}", v.getValue() == null ? "" : v.getValue());
        }
        return resultado;
    }
}
