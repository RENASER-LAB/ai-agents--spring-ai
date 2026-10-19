package com.renaser.ai.ai_engine.whatsapp.controller;

import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.PeticionContexto;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Rechazo;
import com.renaser.ai.ai_engine.whatsapp.service.ReferenciasOpacas;
import com.renaser.ai.ai_engine.whatsapp.service.ServicioContextoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.SinAccesoWhatsappException;
import com.renaser.ai.ai_engine.whatsapp.service.TextosDeWhatsapp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La consulta v2 del bot de WhatsApp (V76): ficha pública, procedimiento, situación, acción y
 * resultado de las postulaciones de un número. Contrato v2, C-3.
 *
 * <p>Va por la misma puerta que {@code /api/v1/integraciones/**}: sin token, solo con la firma HMAC
 * del bot sobre la marca, <b>la ruta completa</b> y el cuerpo. Una firma de otra ruta no vale aquí.
 *
 * <p>Los errores llevan {@code {codigo, texto}}: {@code 400 VALIDACION} con un cuerpo que no
 * cumple, {@code 404 SIN_ACCESO} con una referencia o un cursor inexistente, ajeno o sin acceso
 * —el mismo en los tres casos—. Un {@code 5xx} es reintentable: el bot lo trata como «EX no
 * disponible», distinto de una lista vacía.
 */
@RestController
@RequestMapping("/api/v2/integraciones/whatsapp")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Integración: bot de WhatsApp",
        description = "Lo que el bot le pide a EX. Firmado con HMAC, sin token")
public class ContextoWhatsappController {

    static final String SE_COMBINAN = "postulacionRef y cursor no se combinan: sin referencia es el "
            + "listado (con su cursor), con referencia es el contexto de esa postulación";

    private final ServicioContextoWhatsapp contexto;

    @PostMapping("/contexto")
    @Operation(summary = "Sin postulacionRef, las postulaciones consultables de ese número (de 9 en 9, "
            + "con cursor); con ella, su contexto público: ficha, procedimiento, situación, acción y "
            + "resultado. Solo lectura. 404 SIN_ACCESO si la referencia o el cursor no son de ese número")
    public ResponseEntity<Object> consultar(@Valid @RequestBody PeticionContexto peticion) {
        String referencia = vacioANulo(peticion.postulacionRef());
        String cursor = vacioANulo(peticion.cursor());
        if (referencia != null && cursor != null) {
            throw new IllegalArgumentException(SE_COMBINAN);
        }
        if (referencia != null && referencia.length() > 64) {
            throw new IllegalArgumentException("postulacionRef mide como mucho 64 caracteres");
        }
        if (cursor != null && cursor.length() > ReferenciasOpacas.LARGO_DEL_CURSOR) {
            throw new IllegalArgumentException("cursor mide como mucho "
                    + ReferenciasOpacas.LARGO_DEL_CURSOR + " caracteres");
        }
        Object cuerpo = referencia == null
                ? contexto.listar(peticion.telefono(), cursor)
                : contexto.detalle(peticion.telefono(), referencia);
        return ResponseEntity.ok(cuerpo);
    }

    /** Una referencia o un cursor que no son de ese número: 404, sin decir si existen. */
    @ExceptionHandler(SinAccesoWhatsappException.class)
    public ResponseEntity<Rechazo> sinAcceso(SinAccesoWhatsappException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Rechazo(SinAccesoWhatsappException.CODIGO, SinAccesoWhatsappException.TEXTO));
    }

    /** Un cuerpo que no cumple el contrato: 400 {@code VALIDACION}, definitivo para el bot. */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Rechazo> peticionQueNoVale(Exception error) {
        log.warn("Consulta v2 del bot que no cumple el contrato: {}", error.getMessage());
        String texto = error instanceof IllegalArgumentException ? error.getMessage()
                : TextosDeWhatsapp.PETICION_QUE_NO_VALE;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Rechazo("VALIDACION", TextosDeWhatsapp.cortar(texto, TextosDeWhatsapp.LARGO_DEL_TEXTO)));
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
