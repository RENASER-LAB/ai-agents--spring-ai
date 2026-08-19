package com.renaser.ai.ai_engine.archivo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dónde viven los currículums y las entregas.
 *
 * <p>Hay dos almacenes y se elige con {@code app.archivos.tipo}. El de <b>disco</b> es el de
 * siempre y sigue siendo el que vale sin configurar nada: quien trabaja en su máquina no
 * necesita una cuenta de nada. El de <b>supabase</b> es el que se usa desplegado.
 *
 * <p><b>El del bucket es el que hay que usar en cuanto haya más de una máquina.</b> Con el de
 * disco, el archivo queda en el ordenador que atendió la subida: si mañana corren dos copias
 * del backend detrás de un balanceador, la mitad de las descargas devuelve «no existe», y un
 * despliegue que reemplaza el contenedor se lleva los currículums por delante.
 */
@Component
@ConfigurationProperties(prefix = "app.archivos")
@Getter @Setter
public class PropiedadesAlmacen {

    /** {@code disco} o {@code supabase}. */
    private String tipo = "disco";

    /** Solo para el de disco: la carpeta raíz. */
    private String ruta;

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
    }
}
