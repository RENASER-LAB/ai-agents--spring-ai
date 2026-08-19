package com.renaser.ai.ai_engine.notificacion.service.impl;

import com.renaser.ai.ai_engine.notificacion.service.EnviadorCorreo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// No envía nada: escribe al log. Es la implementación vigente mientras Renaser no
// confirme su dominio de correo (sin dominio bien configurado, todo cae en spam).
@Service
@Slf4j
public class EnviadorCorreoLog implements EnviadorCorreo {

    @Override
    public void enviar(String correoDestino, String asunto, String cuerpo) {
        log.info("[correo no enviado - sin dominio confirmado] para={} asunto={}", correoDestino, asunto);
    }
}
