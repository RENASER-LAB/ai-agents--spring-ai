package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.GuardarBrochure;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.GuardarPresentacion;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.PresentacionPanel;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.springframework.web.multipart.MultipartFile;

/**
 * «Perfil de la empresa» (V85): lo que la empresa de quien ha entrado cuenta de sí misma.
 *
 * <p>⚠️ <b>Siempre la empresa de quien pregunta.</b> Ningún método recibe el id de una
 * organización: la sale del contexto, así que no hay forma de leer ni de guardar la de otra. La
 * plataforma tampoco edita aquí la de una empresa cliente.
 */
public interface ServicioPresentacion {

    PresentacionPanel ver(ContextoUsuario quien);

    /**
     * Guarda todo junto, o nada: si algún campo no vale, {@link PresentacionInvalidaException}
     * con todos. El cambio queda en la auditoría con quién, cuándo y el valor anterior; dos
     * guardados seguidos con lo mismo no vuelven a auditar.
     */
    PresentacionPanel guardar(ContextoUsuario quien, GuardarPresentacion datos);

    /**
     * Guarda solo la plantilla y el color del brochure, al elegirlos en la pestaña «Brochure»: lo
     * demás de la presentación no se toca. Fuera del catálogo, {@link PresentacionInvalidaException}
     * y no se guarda nada. Si cambió, queda en la auditoría como {@code elegir_brochure_empresa}.
     * La siguiente descarga ya sale así: la plantilla y el color están en la huella del PDF
     * recordado.
     */
    PresentacionPanel guardarBrochure(ContextoUsuario quien, GuardarBrochure eleccion);

    /** Sube el logo o la portada, re-codificados sin metadatos. Sustituye al anterior. */
    PresentacionPanel guardarImagen(ContextoUsuario quien, ImagenesDeLaEmpresa.Cual cual,
                                    MultipartFile archivo);

    PresentacionPanel quitarImagen(ContextoUsuario quien, ImagenesDeLaEmpresa.Cual cual);

    /** Si la empresa tiene video de presentación, para el chip del formulario de la vacante. */
    boolean tieneVideo(ContextoUsuario quien);
}
