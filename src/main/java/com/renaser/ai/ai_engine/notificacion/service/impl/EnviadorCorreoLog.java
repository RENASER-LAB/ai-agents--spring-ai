package com.renaser.ai.ai_engine.notificacion.service.impl;

import com.renaser.ai.ai_engine.notificacion.service.EnviadorCorreo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No envia nada: escribe al log.
 *
 * <p>Sigue siendo <b>el transporte por defecto</b>. Se cambia poniendo
 * {@code renaser.correo.transporte: smtp}, y mientras no se ponga, la aplicacion se comporta
 * exactamente como antes de que existiera el envio real. Eso es a proposito: los tests, la
 * maquina de otro desarrollador y cualquier arranque sin credenciales no deben mandarle un
 * correo a un candidato de verdad por descuido.
 */
@Service
@ConditionalOnProperty(name = "renaser.correo.transporte", havingValue = "log", matchIfMissing = true)
@Slf4j
public class EnviadorCorreoLog implements EnviadorCorreo {

    @Override
    public Resultado enviar(String correoDestino, String asunto, String cuerpo) {
        log.info("[correo no enviado - transporte de log] para={} asunto={}", correoDestino, asunto);
        return Resultado.NO_ENVIADO;
    }
}
