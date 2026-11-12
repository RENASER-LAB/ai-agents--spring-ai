package com.renaser.ai.ai_engine.postulacion.service;

/**
 * Del currículum no sale texto: menos de 40 caracteres —una foto escaneada— o un formato que no
 * se lee, como el {@code .doc} antiguo (V80).
 *
 * <p>Es un {@link IllegalStateException}, como lo que se lanzaba antes, así que quien ya lo
 * trataba como un fallo cualquiera sigue igual. Existe para quien necesita distinguirlo: la
 * búsqueda de «Qué buscar en el CV» lo marca «CV sin texto» al primer intento, sin llamar a la
 * IA y sin reintentos, porque el archivo no va a cambiar solo.
 */
public class CvSinTexto extends IllegalStateException {

    public CvSinTexto(String motivo) {
        super(motivo);
    }
}
