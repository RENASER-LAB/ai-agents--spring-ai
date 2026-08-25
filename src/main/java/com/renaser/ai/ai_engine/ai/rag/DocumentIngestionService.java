package com.renaser.ai.ai_engine.ai.rag;

public interface DocumentIngestionService {

    /**
     * Ingiere un PDF en el almacén vectorial.
     *
     * <p>Recibe la ruta como <b>texto</b> y no como {@code Path} a propósito: quien la
     * convierte es la implementación, después de atarla al directorio permitido. Aceptar un
     * {@code Path} ya construido invitaba a que cada llamador lo armara por su cuenta, que es
     * como se coló la lectura arbitraria de ficheros.
     *
     * @param rutaRelativa ruta del PDF, que debe quedar dentro de {@code renaser.rag.directorio-base}
     * @throws IllegalArgumentException si la ruta se sale del directorio permitido o no existe
     */
    void ingestPdf(String rutaRelativa);
}
