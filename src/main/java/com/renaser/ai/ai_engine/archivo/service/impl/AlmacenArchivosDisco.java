package com.renaser.ai.ai_engine.archivo.service.impl;

import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

// Disco local, raíz configurable. El nombre físico es {organización}/{uuid}.{ext}:
// aleatorio para que nadie adivine rutas, y con la organización como carpeta para que
// el aislamiento multiempresa también exista en el disco.
@Service
@Slf4j
public class AlmacenArchivosDisco implements AlmacenArchivos {

    // Solo lo que el portal promete aceptar: PDF o Word
    private static final Set<String> EXTENSIONES = Set.of("pdf", "doc", "docx");
    private static final Set<String> TIPOS = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final Path raiz;
    private final ArchivoRepository archivos;

    public AlmacenArchivosDisco(@Value("${app.archivos.ruta}") String ruta, ArchivoRepository archivos) {
        this.raiz = Path.of(ruta);
        this.archivos = archivos;
    }

    @Override
    public Archivo guardar(Long organizacionId, MultipartFile archivo) {
        String nombreOriginal = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename();
        String extension = extensionDe(nombreOriginal);
        if (!EXTENSIONES.contains(extension) || !TIPOS.contains(archivo.getContentType())) {
            throw new IllegalArgumentException("El archivo debe ser PDF o Word (.pdf, .doc, .docx)");
        }
        try {
            Path carpeta = raiz.resolve(String.valueOf(organizacionId));
            Files.createDirectories(carpeta);
            Path destino = carpeta.resolve(UUID.randomUUID() + "." + extension);
            archivo.transferTo(destino.toAbsolutePath());

            return archivos.save(Archivo.builder()
                    .organizacionId(organizacionId)
                    .ruta(destino.toString())
                    .nombreOriginal(nombreOriginal)
                    .tamano(archivo.getSize())
                    .tipo(archivo.getContentType())
                    .subidoEn(Instant.now())
                    .creadoEn(Instant.now())
                    .build());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
    }

    @Override
    public byte[] leer(Archivo archivo) {
        if (archivo.getRuta() == null) {
            throw new IllegalStateException("El contenido de este archivo fue borrado");
        }
        try {
            return Files.readAllBytes(Path.of(archivo.getRuta()));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo", e);
        }
    }

    @Override
    public void borrarContenido(Archivo archivo) {
        if (archivo.getRuta() != null) {
            try {
                Files.deleteIfExists(Path.of(archivo.getRuta()));
            } catch (IOException e) {
                // Se anota y se sigue: la anonimización de la base no puede quedar a
                // medias por un archivo que no se dejó borrar
                log.error("No se pudo borrar el archivo físico {}: {}", archivo.getRuta(), e.getMessage());
            }
        }
        archivo.setRuta(null);
        archivo.setBorradoEn(Instant.now());
        archivos.save(archivo);
    }

    private String extensionDe(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase();
    }
}
