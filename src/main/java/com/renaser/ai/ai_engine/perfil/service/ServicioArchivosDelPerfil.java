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

    /** Lo que se devuelve para pintar o descargar: el contenido y con qué nombre y tipo. */
    record Contenido(byte[] bytes, String nombre, String tipo) {
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
