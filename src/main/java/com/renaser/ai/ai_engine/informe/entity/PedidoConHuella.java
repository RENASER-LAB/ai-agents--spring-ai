package com.renaser.ai.ai_engine.informe.entity;

import java.time.Instant;

/**
 * Una fila que se le pide a la IA para unos datos concretos (su huella): los textos del redactor
 * (V84) y el seguimiento de pendientes (V94). Lo que se pidió ya no se vuelve a pagar.
 */
public interface PedidoConHuella {

    /** La huella de lo último que se pidió, o nula si nunca se pidió. */
    String getHuellaPedida();

    void setHuellaPedida(String huella);

    void setPedidaEn(Instant cuando);
}
