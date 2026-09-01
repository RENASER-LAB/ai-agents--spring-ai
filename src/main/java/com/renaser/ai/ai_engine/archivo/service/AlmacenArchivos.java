package com.renaser.ai.ai_engine.archivo.service;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;

import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;

/**
 * Dónde se guardan los currículums y las entregas.
 *
 * <p>La interfaz existe para poder cambiar de almacén sin tocar a quien la usa. Hay dos:
 * disco local para trabajar en tu máquina, y un bucket de Supabase para lo desplegado.
 *
 * <p><b>Los dos enlaces firmados son la parte que importa.</b> Un enlace firmado es una URL
 * que lleva dentro un permiso temporal para un solo archivo: el navegador habla directamente
 * con el almacén y el PDF no pasa por aquí ni en la subida ni en la bajada. Es lo mismo que
 * en S3 se llama <i>presigned URL</i>.
 *
 * <p>Sin eso, cada descarga obliga al backend a bajarse el archivo entero para volver a
 * mandarlo: paga el doble de tráfico, se queda sin memoria con varios currículums a la vez, y
 * un archivo grande bloquea un hilo durante todo el envío.
 */
public interface AlmacenArchivos {

    /** Guarda el archivo y devuelve la fila de {@code archivo} ya persistida. */
    Archivo guardar(Long organizacionId, MultipartFile archivo);

    /**
     * El contenido, en memoria.
     *
     * <p>Lo necesita quien tiene que <b>mirar dentro</b> del archivo: el extractor que saca
     * el texto del currículum para dárselo al modelo. Para entregárselo a una persona no se
     * usa esto, se usa {@link #urlDeDescarga}.
     */
    byte[] leer(Archivo archivo);

    /** Borra el contenido y anula la ruta; la fila se conserva (anonimización). */
    void borrarContenido(Archivo archivo);

    /**
     * Un enlace de descarga que caduca, para dárselo a un navegador.
     *
     * <p>Quien llama ya comprobó el permiso: este enlace no vuelve a preguntar nada, así que
     * es tan secreto como el archivo. Dura pocos minutos justamente por eso.
     *
     * @return vacío si este almacén no sabe firmar —el de disco no—, y entonces quien llama
     *         tiene que servir los bytes él mismo
     */
    Optional<EnlaceFirmado> urlDeDescarga(Archivo archivo);

    /**
     * El mismo enlace, pero para el enunciado de una prueba: pensado para durar lo que dura
     * una convocatoria.
     *
     * <p><b>Por qué no vale el de arriba.</b> Aquel dura cinco minutos a propósito, y el
     * motivo está escrito en {@code PropiedadesAlmacen}: un currículum que circule por un
     * chat es el dato de una persona. Este enlace no se le enseña a un navegador que lo abre
     * en el momento: se <b>guarda</b> en {@code version_plantilla_prueba.url_consigna} y lo
     * pega el correo {@code PRUEBA_DISPONIBLE}, que puede salir semanas después de la subida
     * y que el candidato abre cuando se sienta a hacer la prueba. Con cinco minutos, ese
     * correo sale siempre con un enlace muerto.
     *
     * <p><b>Y por qué se puede.</b> El enunciado no es el dato de nadie: es el examen que
     * reciben por correo todos los candidatos de esa vacante. Antes de esto se repartía como
     * un enlace de Drive abierto, que no caducaba nunca y lo abría cualquiera. Esto es menos
     * público que aquello, no más.
     *
     * <p>Por defecto es el enlace corriente: un almacén que no sepa firmar para tanto tiempo
     * no tiene que fingir que sí.
     */
    default Optional<EnlaceFirmado> urlDeConsigna(Archivo archivo) {
        return urlDeDescarga(archivo);
    }

    /**
     * Reserva un sitio en el almacén y devuelve por dónde subir sin pasar por aquí.
     *
     * <p>La fila de {@code archivo} se crea ya, con {@code subidoEn} vacío: existe el hueco,
     * pero todavía no hay nada dentro. Se llena cuando quien subió avisa, y eso lo comprueba
     * {@link #confirmarSubida}.
     *
     * @return vacío si este almacén no sabe firmar
     */
    Optional<SubidaFirmada> urlDeSubida(Long organizacionId, String nombreOriginal, String tipo);

    /**
     * Da por buena una subida hecha por enlace firmado.
     *
     * <p>Comprueba contra el almacén que el archivo está de verdad ahí y anota su tamaño
     * real. <b>No se fía de lo que diga quien subió</b>: entre pedir el enlace y usarlo puede
     * no haber pasado nada, y una fila que dice que hay un currículum donde no hay nada se
     * descubre semanas después, cuando alguien intenta abrirlo.
     *
     * @throws IllegalStateException si en el almacén no hay nada en esa ruta
     */
    Archivo confirmarSubida(Archivo archivo);

    /**
     * Por dónde subir, y hasta cuándo.
     *
     * @param archivo la fila ya creada, todavía sin contenido
     * @param url     la dirección a la que hay que mandar el archivo
     * @param expira  cuándo deja de valer
     */
    record SubidaFirmada(Archivo archivo, String url, Instant expira) {
    }

    /**
     * Un enlace y hasta cuando vale.
     *
     * <p>La caducidad viaja con el enlace y no la decide quien lo pide: la sabe quien firma,
     * porque es el que conoce su propia configuracion. Asi nadie de fuera tiene que adivinar
     * cuanto dura, ni acaba habiendo dos numeros que se contradicen.
     */
    record EnlaceFirmado(String url, Instant expira) {
    }
}
