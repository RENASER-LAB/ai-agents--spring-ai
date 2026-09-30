package com.renaser.ai.ai_engine.colaborador.service;

/**
 * Ese documento ya tiene ficha en esta empresa.
 *
 * <p>No es un 409 más porque lleva a dónde ir: la ficha que ya existe, y si está cesada, la
 * salida es reingresarla desde ahí. El manejador de errores pone el id en la respuesta para
 * que la pantalla enlace.
 */
public class DocumentoYaRegistradoException extends RuntimeException {

    private final Long colaboradorId;
    private final boolean cesado;

    public DocumentoYaRegistradoException(Long colaboradorId, boolean cesado) {
        super(cesado ? "Ya trabajó aquí. Reingrésalo desde su ficha" : "Ya es colaborador");
        this.colaboradorId = colaboradorId;
        this.cesado = cesado;
    }

    public Long getColaboradorId() {
        return colaboradorId;
    }

    public boolean isCesado() {
        return cesado;
    }
}
