package com.renaser.ai.ai_engine.archivo.service.impl;

import com.renaser.ai.ai_engine.archivo.config.PropiedadesAlmacen;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.archivo.service.TiposDeArchivo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Disco local, raíz configurable. El almacén de trabajar en tu máquina.
 *
 * <p>El nombre físico es {@code {organización}/{uuid}.{extensión}}: aleatorio para que nadie
 * adivine rutas, y con la organización como carpeta para que el aislamiento entre empresas
 * también exista en el disco.
 *
 * <p><b>No sirve desplegado, y conviene saber por qué.</b> El archivo queda en la máquina que
 * atendió la subida. Con dos copias del backend, la mitad de las descargas contesta que el
 * archivo no existe; y un despliegue que reemplaza el contenedor se lleva los currículums.
 * Para eso está {@link AlmacenArchivosSupabase}.
 */
@Service
@ConditionalOnProperty(prefix = "app.archivos", name = "tipo",
        havingValue = "disco", matchIfMissing = true)
@Slf4j
public class AlmacenArchivosDisco implements AlmacenArchivos {

    private final Path raiz;
    private final ArchivoRepository archivos;

    public AlmacenArchivosDisco(PropiedadesAlmacen propiedades, ArchivoRepository archivos) {
        this.raiz = Path.of(propiedades.getRuta());
        this.archivos = archivos;
    }

    @Override
    public Archivo guardar(Long organizacionId, MultipartFile archivo) {
        String nombreOriginal = archivo.getOriginalFilename() == null
                ? "" : archivo.getOriginalFilename();
        TiposDeArchivo.exigirValido(nombreOriginal, archivo.getContentType());
        try {
            Path carpeta = raiz.resolve(String.valueOf(organizacionId));
            Files.createDirectories(carpeta);
            Path destino = carpeta.resolve(
                    UUID.randomUUID() + "." + TiposDeArchivo.extensionDe(nombreOriginal));
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
                log.error("No se pudo borrar el archivo físico {}: {}",
                        archivo.getRuta(), e.getMessage());
            }
        }
        archivo.setRuta(null);
        archivo.setBorradoEn(Instant.now());
        archivos.save(archivo);
    }

    // ==================== Lo que un disco no sabe hacer ====================

    /**
     * Vacío, siempre.
     *
     * <p>Un archivo en el disco de esta máquina no tiene ninguna dirección que un navegador
     * pueda abrir por su cuenta. Devolver vacío es la respuesta honesta, y quien llama ya
     * sabe qué hacer con ella: servir los bytes él mismo.
     */
    @Override
    public Optional<EnlaceFirmado> urlDeDescarga(Archivo archivo) {
        return Optional.empty();
    }

    /** Vacío, por lo mismo: aquí se sube por el endpoint de siempre. */
    @Override
    public Optional<SubidaFirmada> urlDeSubida(Long organizacionId, String nombreOriginal,
                                               String tipo) {
        return Optional.empty();
    }

    /**
     * No hay nada que confirmar: en este almacén no existe la subida por enlace, así que
     * cuando una fila llega aquí es que alguien la creó por un camino que no debería existir.
     */
    @Override
    public Archivo confirmarSubida(Archivo archivo) {
        throw new UnsupportedOperationException(
                "El almacén de disco no reparte enlaces de subida, así que no hay ninguna "
                        + "subida suya que confirmar");
    }
}
