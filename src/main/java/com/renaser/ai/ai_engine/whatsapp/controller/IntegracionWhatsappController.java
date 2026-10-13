package com.renaser.ai.ai_engine.whatsapp.controller;

import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Novedades;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.PeticionDelNumero;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.PeticionVinculo;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Rechazo;
import com.renaser.ai.ai_engine.whatsapp.service.ServicioIntegracionWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.ServicioIntegracionWhatsapp.ResultadoVinculo;
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
import org.springframework.web.bind.annotation.*;

/**
 * La puerta del bot de WhatsApp (V73): lo que el servicio de conversación del CRM le pide a EX.
 *
 * <p>No lleva token: lleva la firma HMAC del bot ({@code X-Renaser-Marca} y
 * {@code X-Renaser-Firma}, sobre la marca, la ruta y el cuerpo), que comprueba su propia cadena
 * de seguridad antes de llegar aquí. Sin firma, con firma mala o con una marca de más de cinco
 * minutos, 401 y no se hace nada. Ver docs/CONTRATO-NOVEDADES-POR-WHATSAPP.md.
 *
 * <p>Los errores van con {@code {codigo, texto}}, el formato del contrato, y no con el de la
 * casa: los maneja este mismo controlador, que Spring consulta antes que cualquier
 * {@code @RestControllerAdvice}.
 */
@RestController
@RequestMapping("/api/v1/integraciones/whatsapp")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Integración: bot de WhatsApp",
        description = "Lo que el bot le pide a EX. Firmado con HMAC, sin token")
public class IntegracionWhatsappController {

    private final ServicioIntegracionWhatsapp integracion;

    @PostMapping("/vinculos")
    @Operation(summary = "Un número anunciado escribió: vincula sus postulaciones anunciadas y "
            + "devuelve todas las activas de ese número con sus novedades (idempotente). "
            + "404 SIN_ANUNCIO, 409 PROCESO_TERMINADO o 409 BETA_APAGADA, con el texto que el bot "
            + "debe contestar")
    public ResponseEntity<Object> vincular(@Valid @RequestBody PeticionVinculo peticion) {
        ResultadoVinculo resultado = integracion.vincular(peticion);
        Object cuerpo = resultado.vinculo() != null ? resultado.vinculo() : resultado.rechazo();
        return ResponseEntity.status(resultado.estadoHttp()).body(cuerpo);
    }

    @PostMapping("/novedades")
    @Operation(summary = "Las novedades de cada proceso activo vinculado a ese número (la lista "
            + "puede ir vacía). POST para que el teléfono no vaya en la URL")
    public Novedades novedades(@Valid @RequestBody PeticionDelNumero peticion) {
        return integracion.novedades(peticion.telefono());
    }

    @PostMapping("/bajas")
    @Operation(summary = "El número escribió BAJA: desvincula todas sus postulaciones y queda "
            + "suprimido. 204, también si no había nada")
    public ResponseEntity<Void> baja(@Valid @RequestBody PeticionDelNumero peticion) {
        integracion.baja(peticion.telefono());
        return ResponseEntity.noContent().build();
    }

    /**
     * Un cuerpo que no vale —sin teléfono, con uno que no es E.164, o que ni siquiera es JSON—:
     * 400 {@code VALIDACION}. Es definitivo para el bot: repetir lo mismo daría lo mismo.
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Rechazo> peticionQueNoVale(Exception error) {
        log.warn("Llamada del bot que no cumple el contrato: {}", error.getMessage());
        String texto = error instanceof IllegalArgumentException ? error.getMessage()
                : TextosDeWhatsapp.PETICION_QUE_NO_VALE;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Rechazo("VALIDACION", TextosDeWhatsapp.cortar(texto, TextosDeWhatsapp.LARGO_DEL_TEXTO)));
    }
}
