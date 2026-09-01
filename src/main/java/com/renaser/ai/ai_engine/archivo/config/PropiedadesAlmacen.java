package com.renaser.ai.ai_engine.archivo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dónde viven los currículums y las entregas: un bucket privado de Supabase.
 *
 * <p><b>Ya no hay opción de guardarlos en disco.</b> La hubo, y se quitó por tres razones que
 * aparecen todas en cuanto el sistema se usa de verdad: el archivo queda en la máquina que
 * atendió la subida —así que con dos copias del backend la mitad de las descargas contesta
 * «no existe»—, un despliegue que reemplaza el contenedor se los lleva por delante, y leerlo
 * obligaba a construir una ruta de fichero a partir de un dato guardado en la base.
 */
@Component
@ConfigurationProperties(prefix = "app.archivos")
@Getter @Setter
public class PropiedadesAlmacen {

    /**
     * Qué almacén se usa. En cualquier entorno de verdad, {@code supabase}.
     *
     * <p>Las pruebas ponen {@code memoria}, que es un doble que vive en el código de pruebas
     * y no escribe en ningún sitio. No existe ninguna opción que guarde en disco: la hubo y
     * se quitó, porque el archivo quedaba en la máquina que atendió la subida.
     */
    private String tipo = "supabase";

    private Supabase supabase = new Supabase();

    @Getter @Setter
    public static class Supabase {

        /**
         * La URL del proyecto, sin barra final: {@code https://<referencia>.supabase.co}.
         *
         * <p>⚠️ Es el proyecto <b>nuestro</b>, el de la base de datos del backend. No el de
         * los agentes de IA, que es otro y se lee por su propia API.
         */
        private String url;

        /**
         * La clave {@code service_role}.
         *
         * <p>Se salta las reglas de acceso por fila, así que es la que puede escribir en un
         * bucket privado. Por eso vive en {@code application-secrets.yaml} y nunca en el
         * repositorio, y por eso <b>no puede llegar nunca al navegador</b>: quien la tenga
         * lee el bucket entero. Lo que sí llega al navegador es un enlace firmado, que sirve
         * para un solo archivo y caduca.
         */
        private String clave;

        /** El bucket, que tiene que estar creado y marcado como privado. */
        private String bucket = "curriculums";

        /**
         * Cuánto vale un enlace de descarga.
         *
         * <p>Corto a propósito: el enlace no lleva permisos dentro, así que quien lo copie y
         * lo pegue en otro sitio abre el currículum de esa persona. Cinco minutos alcanzan
         * de sobra para que el navegador lo abra y no para que ande circulando por un chat.
         */
        private int minutosEnlace = 5;

        /**
         * Cuánto vale el enlace al enunciado de una prueba.
         *
         * <p>Meses y no minutos, y no es que aquí se relaje la regla de arriba: es que el
         * enunciado no es el dato de nadie. Es el examen que reciben por correo todos los
         * candidatos de una vacante, y ese enlace se <b>guarda</b> en la versión de la
         * plantilla — el correo puede salir semanas después de subirlo. Con cinco minutos
         * saldría siempre muerto.
         *
         * <p>⚠️ Ciento ochenta días cubren de sobra una convocatoria, pero <b>caduca</b>, y
         * subir el enunciado solo se puede sobre una versión en BORRADOR. Así que pasado ese
         * plazo el enlace de una versión publicada deja de abrir y la salida es la de
         * siempre: una versión nueva. Si algún día hiciera falta que no caducara, lo que hay
         * que cambiar no es este número, es guardar el id del archivo y firmar el enlace al
         * mandar el correo en vez de al subirlo.
         */
        private int diasEnlaceConsigna = 180;
    }
}
