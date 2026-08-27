package com.renaser.ai.ai_engine.archivo.service.impl;

import com.renaser.ai.ai_engine.archivo.config.PropiedadesAlmacen;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.archivo.service.HashContenido;
import com.renaser.ai.ai_engine.archivo.service.TiposDeArchivo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Los archivos viven en un bucket de Supabase, no en el disco del backend.
 *
 * <p><b>Por qué hizo falta.</b> Con el almacén de disco, el currículum queda en la máquina que
 * atendió la subida. Eso funciona mientras haya una sola: en cuanto corran dos copias del
 * backend, la mitad de las descargas contesta «no existe», y un despliegue que reemplaza el
 * contenedor se lleva los currículums por delante sin avisar.
 *
 * <p><b>La ruta.</b> {@code {organización}/{uuid}.{extensión}}. El uuid es aleatorio para que
 * nadie adivine rutas ajenas, y la organización va delante para que el aislamiento entre
 * empresas también exista dentro del bucket, no solo en la base.
 *
 * <p><b>El bucket es privado.</b> Sin eso, cualquiera con la ruta se descarga el currículum de
 * cualquiera, y las rutas acaban en registros y en capturas de pantalla. Lo que se entrega al
 * navegador es un enlace firmado que vale para un archivo y caduca en minutos.
 */
@Service
@ConditionalOnProperty(prefix = "app.archivos", name = "tipo", havingValue = "supabase")
@Slf4j
public class AlmacenArchivosSupabase implements AlmacenArchivos {

    private final ArchivoRepository archivos;
    private final PropiedadesAlmacen.Supabase config;
    private final RestClient cliente;

    /**
     * El constructor recibe el <b>constructor</b> del cliente y no un cliente ya hecho, para
     * que una prueba pueda meterle un servidor falso por ahí. Un almacén que solo se puede
     * probar contra el Supabase de verdad no se prueba nunca.
     */
    public AlmacenArchivosSupabase(ArchivoRepository archivos, PropiedadesAlmacen propiedades,
                                   RestClient.Builder constructor) {
        this.archivos = archivos;
        this.config = propiedades.getSupabase();
        exigirConfiguracion();
        // Un cliente propio, y no el que ya existe bajo ai/: aquel apunta al Supabase de
        // RENASER OS, que es otro proyecto. Compartirlo mandaría los currículums a la cuenta
        // equivocada, y el error no se vería hasta que alguien fuera a buscarlos.
        this.cliente = constructor
                .baseUrl(config.getUrl() + "/storage/v1")
                .defaultHeader("Authorization", "Bearer " + config.getClave())
                .defaultHeader("apikey", config.getClave())
                .build();
    }

    private void exigirConfiguracion() {
        if (esVacio(config.getUrl()) || esVacio(config.getClave()) || esVacio(config.getBucket())) {
            // Falla al arrancar y no en la primera subida. Descubrirlo cuando un candidato
            // ya pulsó «enviar» significa perder su currículum y su tiempo.
            throw new IllegalStateException(
                    "El almacén de archivos está puesto en «supabase» pero le falta "
                            + "configuración: app.archivos.supabase.url, .clave y .bucket");
        }
    }

    // ==================== Guardar y leer desde aquí ====================

    @Override
    public Archivo guardar(Long organizacionId, MultipartFile archivo) {
        String nombreOriginal = archivo.getOriginalFilename() == null
                ? "" : archivo.getOriginalFilename();
        TiposDeArchivo.exigirValido(nombreOriginal, archivo.getContentType());

        String ruta = rutaNueva(organizacionId, nombreOriginal);
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo que llegó", e);
        }
        subir(ruta, contenido, archivo.getContentType());

        return archivos.save(Archivo.builder()
                .organizacionId(organizacionId)
                .ruta(ruta)
                .nombreOriginal(nombreOriginal)
                .tamano(archivo.getSize())
                .tipo(archivo.getContentType())
                // La huella es lo que permite no pagar dos lecturas del mismo curriculum.
                // Se calcula aqui porque este es el unico embudo por el que pasan los bytes.
                .contenidoHash(HashContenido.sha256(contenido))
                .subidoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());
    }

    @Override
    public byte[] leer(Archivo archivo) {
        exigirContenido(archivo);
        byte[] bytes = cliente.get()
                .uri(objeto("/object", archivo.getRuta()))
                .retrieve()
                .body(byte[].class);
        if (bytes == null) {
            throw new IllegalStateException(
                    "El bucket no devolvió nada para " + archivo.getRuta());
        }
        return bytes;
    }

    @Override
    public void borrarContenido(Archivo archivo) {
        if (archivo.getRuta() != null) {
            try {
                cliente.delete()
                        .uri(objeto("/object", archivo.getRuta()))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException e) {
                // Se anota y se sigue, igual que con el disco: la anonimización de la base
                // no puede quedarse a medias porque el bucket no dejara borrar un objeto.
                log.error("No se pudo borrar del bucket el objeto {}: {}",
                        archivo.getRuta(), e.getMessage());
            }
        }
        archivo.setRuta(null);
        archivo.setBorradoEn(Instant.now());
        archivos.save(archivo);
    }

    // ==================== Los dos enlaces firmados ====================

    @Override
    public Optional<EnlaceFirmado> urlDeDescarga(Archivo archivo) {
        exigirContenido(archivo);
        Duration vida = Duration.ofMinutes(config.getMinutosEnlace());

        Map<String, Object> respuesta = cliente.post()
                .uri(objeto("/object/sign", archivo.getRuta()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", vida.toSeconds()))
                .retrieve()
                .body(MAPA);

        String firmada = respuesta == null ? null : (String) respuesta.get("signedURL");
        if (firmada == null) {
            throw new IllegalStateException(
                    "Supabase no devolvió enlace firmado para " + archivo.getRuta());
        }
        return Optional.of(new EnlaceFirmado(
                config.getUrl() + "/storage/v1" + firmada,
                Instant.now().plus(vida)));
    }

    @Override
    public Optional<SubidaFirmada> urlDeSubida(Long organizacionId, String nombreOriginal,
                                               String tipo) {
        TiposDeArchivo.exigirValido(nombreOriginal, tipo);
        String ruta = rutaNueva(organizacionId, nombreOriginal);

        Map<String, Object> respuesta = cliente.post()
                .uri(objeto("/object/upload/sign", ruta))
                .retrieve()
                .body(MAPA);

        String url = respuesta == null ? null : (String) respuesta.get("url");
        if (url == null) {
            throw new IllegalStateException("Supabase no devolvió enlace de subida para " + ruta);
        }

        // La fila se crea sin `subidoEn`: el hueco existe, el contenido todavía no. Quien
        // mire esta fila antes de la confirmación tiene que poder distinguir las dos cosas.
        Archivo hueco = archivos.save(Archivo.builder()
                .organizacionId(organizacionId)
                .ruta(ruta)
                .nombreOriginal(nombreOriginal)
                .tipo(tipo)
                .creadoEn(Instant.now())
                .build());

        // Las dos horas las fija Supabase para este tipo de enlace; no es cosa nuestra.
        return Optional.of(new SubidaFirmada(hueco,
                config.getUrl() + "/storage/v1" + url,
                Instant.now().plus(Duration.ofHours(2))));
    }

    @Override
    public Archivo confirmarSubida(Archivo archivo) {
        exigirContenido(archivo);
        long tamano = tamanoEnElBucket(archivo.getRuta());
        archivo.setTamano(tamano);
        archivo.setSubidoEn(Instant.now());
        return archivos.save(archivo);
    }

    /**
     * Cuánto ocupa de verdad el objeto, preguntándoselo al bucket.
     *
     * <p>Se pide con un HEAD, que trae las cabeceras y no el archivo: da igual que sean diez
     * megas. Y sirve de paso para saber si el objeto existe: si no está, Supabase contesta
     * 404 y la confirmación se niega en vez de dejar una fila que promete un currículum que
     * nadie subió.
     */
    private long tamanoEnElBucket(String ruta) {
        try {
            var cabeceras = cliente.head()
                    .uri(objeto("/object", ruta))
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders();
            long longitud = cabeceras.getContentLength();
            return longitud < 0 ? 0 : longitud;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "En el bucket no hay nada en «" + ruta + "»: la subida no llegó a "
                            + "completarse, así que no se da por buena", e);
        }
    }

    // ==================== Apoyo ====================

    private void subir(String ruta, byte[] contenido, String tipo) {
        cliente.post()
                .uri(objeto("/object", ruta))
                .header("Content-Type", tipo)
                // Sin esto, volver a subir sobre una ruta existente da 409. No debería
                // pasar nunca —cada archivo estrena uuid— pero si pasa, más vale que el
                // error diga «ya existe» y no que se pise un currículum ajeno en silencio.
                .header("x-upsert", "false")
                .body(contenido)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * La direccion de un objeto dentro del bucket.
     *
     * <p>La ruta lleva barras —{@code 1/abc.pdf}— y son separadores de verdad, no parte del
     * nombre. Metida en una plantilla de URL corriente, la barra se codifica como
     * {@code %2F} y se acaba pidiendo un objeto que no es el que se queria. Por eso se parte
     * en trozos y cada uno se codifica por separado.
     */
    private java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>
            objeto(String prefijo, String ruta) {
        return constructor -> constructor
                .path(prefijo + "/{bucket}")
                .pathSegment(ruta.split("/"))
                .build(config.getBucket());
    }

    private String rutaNueva(Long organizacionId, String nombreOriginal) {
        return organizacionId + "/" + UUID.randomUUID() + "."
                + TiposDeArchivo.extensionDe(nombreOriginal);
    }

    private void exigirContenido(Archivo archivo) {
        if (archivo.getRuta() == null) {
            throw new IllegalStateException("El contenido de este archivo fue borrado");
        }
    }

    private boolean esVacio(String s) {
        return s == null || s.isBlank();
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>>
            MAPA = new org.springframework.core.ParameterizedTypeReference<>() {
    };
}
