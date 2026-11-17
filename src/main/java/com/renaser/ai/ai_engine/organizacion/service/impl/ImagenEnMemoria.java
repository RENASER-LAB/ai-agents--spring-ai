package com.renaser.ai.ai_engine.organizacion.service.impl;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * La imagen ya re-codificada, con forma de archivo subido: es lo que recibe el almacén
 * ({@code AlmacenArchivos#guardarImagen}), y así el logo y la portada pasan por el mismo embudo
 * —con su huella y su fila de {@code archivo}— que la foto del perfil del candidato.
 */
final class ImagenEnMemoria implements MultipartFile {

    private final String nombre;
    private final String tipo;
    private final byte[] contenido;

    ImagenEnMemoria(String nombre, String tipo, byte[] contenido) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.contenido = contenido.clone();
    }

    @Override
    public String getName() {
        return "archivo";
    }

    @Override
    public String getOriginalFilename() {
        return nombre;
    }

    @Override
    public String getContentType() {
        return tipo;
    }

    @Override
    public boolean isEmpty() {
        return contenido.length == 0;
    }

    @Override
    public long getSize() {
        return contenido.length;
    }

    @Override
    public byte[] getBytes() {
        return contenido.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(contenido);
    }

    @Override
    public void transferTo(File destino) throws IOException {
        Files.write(destino.toPath(), contenido);
    }
}
