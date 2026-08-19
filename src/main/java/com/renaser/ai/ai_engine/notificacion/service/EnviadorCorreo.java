package com.renaser.ai.ai_engine.notificacion.service;

// El transporte del correo, separado del registro. El dominio de correo de Renaser
// todavía no está confirmado: mientras tanto la implementación solo escribe al log,
// y el correo queda igualmente guardado en correo_enviado (eso es lo obligatorio).
// Cuando haya dominio, se escribe una implementación SMTP y nada más cambia.
public interface EnviadorCorreo {

    void enviar(String correoDestino, String asunto, String cuerpo);
}
