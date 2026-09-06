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

    /** Lo que se acepta como foto o portada. Ni GIF ni SVG: ver {@link #exigirImagen}. */
    private static final Set<String> EXTENSIONES_IMAGEN = Set.of("jpg", "jpeg", "png", "webp");

    private static final Set<String> TIPOS_IMAGEN = Set.of(
            "image/jpeg", "image/png", "image/webp");

    /** Dos megas para una foto de perfil. El curriculum sigue con sus diez. */
    public static final long MAXIMO_IMAGEN = 2L * 1024 * 1024;

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

    /**
     * Lo que vale como foto de perfil o como portada.
     *
     * <p>Está aparte del currículum a propósito: son dos promesas distintas y mezclarlas
     * acabaría aceptando un PDF como foto o una imagen como currículum.
     *
     * <p><b>Ni SVG ni GIF.</b> Un SVG es un documento que puede llevar scripts dentro y se
     * sirve desde el mismo origen que el portal; un GIF animado convierte una ficha de
     * candidato en una pantalla que se mueve. Ninguno de los dos aporta nada aquí.
     *
     * @throws IllegalArgumentException si no es una imagen de las que se aceptan, o si pesa
     *                                  más de {@link #MAXIMO_IMAGEN}
     */
    public static void exigirImagen(String nombreOriginal, String tipo, long tamano) {
        if (!EXTENSIONES_IMAGEN.contains(extensionDe(nombreOriginal)) || !TIPOS_IMAGEN.contains(tipo)) {
            throw new IllegalArgumentException(
                    "La imagen debe ser JPG, PNG o WebP (.jpg, .jpeg, .png, .webp)");
        }
        // ⚠️ Este límite solo llega a correr por debajo de los 10 MB del multipart de Spring:
        // por encima, la petición la corta el servidor antes y contesta
        // ManejadorErrorArchivoGrande con su propio texto. Son dos mensajes distintos y los
        // dos existen a propósito.
        if (tamano > MAXIMO_IMAGEN) {
            throw new IllegalArgumentException(
                    "La imagen no puede pesar más de 2 MB. Prueba a guardarla más pequeña.");
        }
    }

    public static String extensionDe(String nombre) {
        String limpio = nombre == null ? "" : nombre;
        int punto = limpio.lastIndexOf('.');
        return punto < 0 ? "" : limpio.substring(punto + 1).toLowerCase();
    }
}
