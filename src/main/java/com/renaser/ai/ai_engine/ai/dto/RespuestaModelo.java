package com.renaser.ai.ai_engine.ai.dto;

/**
 * Lo que devuelve una llamada al modelo, con todo lo que hay que dejar auditado.
 *
 * <p>No es solo el texto: {@code ejecucion_ia} guarda además qué modelo respondió, de qué
 * proveedor y cuántos tokens costó (RF-146). Si eso no viaja junto con la respuesta, se
 * pierde en el momento de escribir la bitácora.
 */
public record RespuestaModelo(
        String texto,
        String modelo,
        String proveedor,
        String versionModelo,
        Integer tokensEntrada,
        Integer tokensSalida) {
}
