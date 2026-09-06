package com.renaser.ai.ai_engine.perfil.service;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.springframework.web.multipart.MultipartFile;

/**
 * Los archivos que el candidato cuelga de su perfil: su foto, su portada, su currículum y
 * los diplomas de sus certificaciones.
 *
 * <p>⚠️ <b>Los cuatro son suyos y solo suyos.</b> No viajan al panel del equipo ni al texto
 * que lee la IA. La foto está aquí y no en la ficha del equipo porque el RF-41 esconde foto,
 * edad, sexo y estado civil antes de que el modelo lea el currículum, justamente para no
 * sesgar por aspecto; enseñársela a la persona que decide desharía eso por la puerta de al
 * lado. Se decidió el 05/09/2026 y cambiarlo pide un RF nuevo, no un campo más en un mapper.
 *
 * <p><b>Se sirven los bytes, no un enlace firmado.</b> Un {@code <img src>} no manda cabecera
 * {@code Authorization}, y en local el almacén es el de memoria, cuya url {@code memoria://}
 * no la abre ningún navegador. Sirviendo el contenido por una ruta con token, la pantalla
 * funciona igual en local y en producción.
 */
public interface ServicioArchivosDelPerfil {

    /**
     * Lo que se devuelve para pintar o descargar: el contenido y con qué nombre y tipo.
     *
     * <p>⚠️ <b>Los tres métodos se escriben a mano porque el componente es un array.</b> Un
     * {@code record} con {@code byte[]} hereda el {@code equals} y el {@code hashCode} de la
     * referencia, así que dos contenidos idénticos salen distintos y meter uno en un
     * {@code Set} no hace lo que parece.
     *
     * <p>Y el {@code toString} <b>no imprime los bytes, dice cuántos son</b>: esto lleva
     * dentro el currículum de una persona, y el volcado por defecto de un array es basura en
     * el registro; el volcado de su contenido sería un dato personal en el registro.
     */
    record Contenido(byte[] bytes, String nombre, String tipo) {

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Contenido c
                    && java.util.Arrays.equals(bytes, c.bytes)
                    && java.util.Objects.equals(nombre, c.nombre)
                    && java.util.Objects.equals(tipo, c.tipo);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(java.util.Arrays.hashCode(bytes), nombre, tipo);
        }

        @Override
        public String toString() {
            return "Contenido[nombre=" + nombre + ", tipo=" + tipo
                    + ", bytes=" + (bytes == null ? 0 : bytes.length) + "]";
        }
    }

    void guardarFoto(ContextoUsuario quien, MultipartFile archivo);

    void quitarFoto(ContextoUsuario quien);

    Contenido foto(ContextoUsuario quien);

    void guardarPortada(ContextoUsuario quien, MultipartFile archivo);

    /** Elegir una del catálogo de la casa. Borra la propia si la había: son excluyentes. */
    void elegirPortadaDeGaleria(ContextoUsuario quien, String codigo);

    void quitarPortada(ContextoUsuario quien);

    Contenido portada(ContextoUsuario quien);

    /**
     * Sube su currículum al perfil y arranca la lectura.
     *
     * <p>Sustituye al anterior: solo se conserva el último (RF-162). Y si ese archivo ya se
     * leyó antes —la huella del contenido lo dice—, <b>no se vuelve a pagar la lectura</b>
     * (RF-161): se da por lista sin llamar al modelo.
     */
    void guardarCurriculum(ContextoUsuario quien, MultipartFile archivo);

    void quitarCurriculum(ContextoUsuario quien);

    Contenido curriculum(ContextoUsuario quien);

    void guardarDiploma(ContextoUsuario quien, Long certificacionId, MultipartFile archivo);

    void quitarDiploma(ContextoUsuario quien, Long certificacionId);

    Contenido diploma(ContextoUsuario quien, Long certificacionId);
}
