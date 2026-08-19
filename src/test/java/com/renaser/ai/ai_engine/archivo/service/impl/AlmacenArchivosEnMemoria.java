package com.renaser.ai.ai_engine.archivo.service.impl;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.archivo.service.TiposDeArchivo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El almacén de las pruebas: los archivos viven en un mapa y desaparecen con la JVM.
 *
 * <p><b>Vive en el código de pruebas a propósito.</b> El proyecto tiene un solo almacén de
 * verdad —el bucket— y no queremos que exista un segundo que alguien pueda encender por
 * descuido en un entorno real. Aquí no hay riesgo: este código no se empaqueta.
 *
 * <p>Reemplaza al de disco, que era el que usaban las pruebas antes. Escribir en un
 * directorio temporal funcionaba, pero obligaba a mantener en producción un almacén que
 * nadie debía usar, y a que ese almacén construyera rutas de fichero a partir de datos de la
 * base. Un doble en memoria hace el mismo trabajo sin arrastrar nada de eso.
 *
 * <p>Se enciende con {@code app.archivos.tipo=memoria}, que ninguna configuración de verdad
 * pone.
 */
@Service
@ConditionalOnProperty(prefix = "app.archivos", name = "tipo", havingValue = "memoria")
@RequiredArgsConstructor
public class AlmacenArchivosEnMemoria implements AlmacenArchivos {

    private final ArchivoRepository archivos;
    private final Map<String, byte[]> contenidos = new ConcurrentHashMap<>();

    @Override
    public Archivo guardar(Long organizacionId, MultipartFile archivo) {
        String nombreOriginal = archivo.getOriginalFilename() == null
                ? "" : archivo.getOriginalFilename();
        TiposDeArchivo.exigirValido(nombreOriginal, archivo.getContentType());

        String ruta = organizacionId + "/" + UUID.randomUUID() + "."
                + TiposDeArchivo.extensionDe(nombreOriginal);
        try {
            contenidos.put(ruta, archivo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo que llegó", e);
        }

        return archivos.save(Archivo.builder()
                .organizacionId(organizacionId)
                .ruta(ruta)
                .nombreOriginal(nombreOriginal)
                .tamano(archivo.getSize())
                .tipo(archivo.getContentType())
                .subidoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());
    }

    @Override
    public byte[] leer(Archivo archivo) {
        exigirContenido(archivo);
        byte[] bytes = contenidos.get(archivo.getRuta());
        if (bytes == null) {
            throw new IllegalStateException("No hay nada guardado en " + archivo.getRuta());
        }
        return bytes;
    }

    @Override
    public void borrarContenido(Archivo archivo) {
        if (archivo.getRuta() != null) {
            contenidos.remove(archivo.getRuta());
        }
        archivo.setRuta(null);
        archivo.setBorradoEn(Instant.now());
        archivos.save(archivo);
    }

    @Override
    public Optional<EnlaceFirmado> urlDeDescarga(Archivo archivo) {
        exigirContenido(archivo);
        // Una dirección de mentira, que nadie abre: lo que las pruebas comprueban es que el
        // permiso se miró antes de firmar y que la caducidad viaja con el enlace.
        return Optional.of(new EnlaceFirmado(
                "memoria://" + archivo.getRuta(),
                Instant.now().plus(Duration.ofMinutes(5))));
    }

    @Override
    public Optional<SubidaFirmada> urlDeSubida(Long organizacionId, String nombreOriginal,
                                               String tipo) {
        TiposDeArchivo.exigirValido(nombreOriginal, tipo);
        String ruta = organizacionId + "/" + UUID.randomUUID() + "."
                + TiposDeArchivo.extensionDe(nombreOriginal);
        Archivo hueco = archivos.save(Archivo.builder()
                .organizacionId(organizacionId)
                .ruta(ruta)
                .nombreOriginal(nombreOriginal)
                .tipo(tipo)
                .creadoEn(Instant.now())
                .build());
        return Optional.of(new SubidaFirmada(hueco, "memoria://" + ruta,
                Instant.now().plus(Duration.ofHours(2))));
    }

    @Override
    public Archivo confirmarSubida(Archivo archivo) {
        exigirContenido(archivo);
        byte[] bytes = contenidos.get(archivo.getRuta());
        if (bytes == null) {
            throw new IllegalStateException(
                    "En el almacén no hay nada en «" + archivo.getRuta() + "»: la subida no "
                            + "llegó a completarse, así que no se da por buena");
        }
        archivo.setTamano((long) bytes.length);
        archivo.setSubidoEn(Instant.now());
        return archivos.save(archivo);
    }

    private void exigirContenido(Archivo archivo) {
        if (archivo.getRuta() == null) {
            throw new IllegalStateException("El contenido de este archivo fue borrado");
        }
    }
}
