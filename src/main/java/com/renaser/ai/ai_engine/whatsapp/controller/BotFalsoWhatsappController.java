package com.renaser.ai.ai_engine.whatsapp.controller;

import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.impl.ClienteConversacionFalso;
import com.renaser.ai.ai_engine.whatsapp.service.impl.ClienteConversacionFalso.Llamada;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * El registro del bot falso (modo {@code falso}): lo que EX le habría mandado al bot de verdad.
 *
 * <p>Solo existe con {@code app.whatsapp.modo=falso}, que ningún entorno de verdad tiene, y aun
 * así va detrás de la firma del bot como el resto de la integración. Lo usan los E2E para
 * comprobar que llegó un solo aviso, el anuncio del número o la revocación del anterior.
 */
@RestController
@RequestMapping("/api/v1/integraciones/whatsapp/falso")
@ConditionalOnProperty(name = "app.whatsapp.modo", havingValue = "falso")
@Tag(name = "Integración: bot de WhatsApp", description = "Lo que el bot le pide a EX. Firmado con HMAC, sin token")
public class BotFalsoWhatsappController {

    private final ClienteConversacion cliente;

    public BotFalsoWhatsappController(ClienteConversacion cliente) {
        this.cliente = cliente;
    }

    @GetMapping("/registro")
    @Operation(summary = "Solo en modo falso: lo que EX le mandó al bot (anuncios, avisos y "
            + "revocaciones), filtrado por número si se pide")
    public List<Llamada> registro(@RequestParam(required = false) String telefono) {
        if (!(cliente instanceof ClienteConversacionFalso falso)) {
            return List.of();
        }
        // Un «+» sin codificar en la dirección llega como espacio: se normaliza como lo manda el bot.
        return telefono == null || telefono.isBlank() ? falso.registro()
                : com.renaser.ai.ai_engine.whatsapp.service.Celular.delBot(telefono)
                        .map(falso::registroDe).orElse(List.of());
    }
}
