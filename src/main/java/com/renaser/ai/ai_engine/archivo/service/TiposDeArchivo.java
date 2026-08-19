package com.renaser.ai.ai_engine.archivo.service;

import java.util.Set;

/**
 * Lo que el portal promete aceptar: PDF o Word.
 *
 * <p>Está aparte porque la regla es la misma se guarde donde se guarde, y porque ya costó una
 * vez: un script que subía currículums los mandaba como {@code application/octet-stream} y
 * todos rebotaban con un 400 que no explicaba cuál de las dos comprobaciones había fallado.
 *
 * <p>Se miran <b>las dos</b>, extensión y tipo declarado, a propósito. La extensión la pone
 * quien sube y no prueba nada; el tipo lo pone el navegador y tampoco. Exigir que coincidan
 * no convierte esto en una garantía, pero descarta el descuido honesto sin leer el archivo.
 */
public final class TiposDeArchivo {

    private static final Set<String> EXTENSIONES = Set.of("pdf", "doc", "docx");

    private static final Set<String> TIPOS = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private TiposDeArchivo() {
    }

    /** @throws IllegalArgumentException si no es uno de los tres formatos */
    public static void exigirValido(String nombreOriginal, String tipo) {
        if (!EXTENSIONES.contains(extensionDe(nombreOriginal)) || !TIPOS.contains(tipo)) {
            throw new IllegalArgumentException(
                    "El archivo debe ser PDF o Word (.pdf, .doc, .docx)");
        }
    }

    public static String extensionDe(String nombre) {
        String limpio = nombre == null ? "" : nombre;
        int punto = limpio.lastIndexOf('.');
        return punto < 0 ? "" : limpio.substring(punto + 1).toLowerCase();
    }
}
