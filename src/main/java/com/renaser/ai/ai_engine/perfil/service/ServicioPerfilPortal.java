package com.renaser.ai.ai_engine.perfil.service;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarCabecera;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarCertificacion;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarEducacion;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarEnlace;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarExperiencia;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarIdioma;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * El perfil visto y editado por su dueño, desde el portal.
 *
 * <p>La pertenencia se comprueba aquí, no con permisos: cada método trabaja sobre el perfil
 * de {@code quien.personaId()}, y un elemento que no es suyo responde 404 — decir 403 ya
 * confirmaría que existe.
 *
 * <p>Editar un elemento lo convierte en «escrito por mí» ({@code origen=PERSONA},
 * confirmado). Confirmar lo valida conservando que salió del currículum. Nada del perfil es
 * obligatorio: un perfil vacío responde 200 con todo vacío, nunca 404.
 */
public interface ServicioPerfilPortal {

    PerfilCompleto ver(ContextoUsuario quien);

    void editarCabecera(ContextoUsuario quien, EditarCabecera datos);

    /** El perfil entero en un JSON legible: el derecho de acceso de la ley 29733. */
    PerfilCompleto descargar(ContextoUsuario quien);

    Long crearExperiencia(ContextoUsuario quien, EditarExperiencia datos);

    void editarExperiencia(ContextoUsuario quien, Long id, EditarExperiencia datos);

    void borrarExperiencia(ContextoUsuario quien, Long id);

    void confirmarExperiencia(ContextoUsuario quien, Long id);

    void reordenarExperiencia(ContextoUsuario quien, List<Long> ids);

    Long crearEducacion(ContextoUsuario quien, EditarEducacion datos);

    void editarEducacion(ContextoUsuario quien, Long id, EditarEducacion datos);

    void borrarEducacion(ContextoUsuario quien, Long id);

    void confirmarEducacion(ContextoUsuario quien, Long id);

    void reordenarEducacion(ContextoUsuario quien, List<Long> ids);

    Long crearIdioma(ContextoUsuario quien, EditarIdioma datos);

    void editarIdioma(ContextoUsuario quien, Long id, EditarIdioma datos);

    void borrarIdioma(ContextoUsuario quien, Long id);

    void confirmarIdioma(ContextoUsuario quien, Long id);

    Long crearCertificacion(ContextoUsuario quien, EditarCertificacion datos);

    void editarCertificacion(ContextoUsuario quien, Long id, EditarCertificacion datos);

    void borrarCertificacion(ContextoUsuario quien, Long id);

    void confirmarCertificacion(ContextoUsuario quien, Long id);

    Long crearEnlace(ContextoUsuario quien, EditarEnlace datos);

    void borrarEnlace(ContextoUsuario quien, Long id);
}
