package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.usuario.entity.Usuario;

import java.util.Optional;

// La identidad del equipo viene de RENASER OS: ellos emiten el token y aquí solo se
// valida. Su contrato todavía no existe, así que esta interfaz aísla el punto de
// cambio: cuando llegue, se escribe otra implementación y nada más se toca.
public interface ProveedorIdentidadEquipo {

    // Valida las credenciales del login de desarrollo y devuelve el usuario del equipo
    Optional<Usuario> autenticarDesarrollo(String correo, String usuarioRenaserOsId);
}
