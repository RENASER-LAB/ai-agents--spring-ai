package com.renaser.ai.ai_engine.perfil.service;

import java.util.Set;

/**
 * Las portadas que ofrece la casa, para quien no quiere subir una suya.
 *
 * <p>No es un catálogo en tabla y es a propósito: <b>no son datos, son cinco dibujos que
 * pinta el navegador</b>. El backend solo guarda cuál eligió; los degradados viven en el CSS
 * del portal, que es donde se pueden cambiar sin migrar nada.
 *
 * <p>Los nombres salen del mundo visual del portal —el canto de nube que difracta la luz—,
 * y por eso son los mismos cuatro tonos del espectro más la bruma.
 */
public final class PortadasDeLaCasa {

    public static final Set<String> CODIGOS =
            Set.of("CANTO_MENTA", "CANTO_AQUA", "CANTO_ROSA", "CANTO_VIOLETA", "BRUMA");

    private PortadasDeLaCasa() {
    }

    /** @throws IllegalArgumentException si el código no es de los cinco */
    public static void exigirValido(String codigo) {
        if (codigo == null || !CODIGOS.contains(codigo)) {
            throw new IllegalArgumentException(
                    "Esa portada no existe. Elige una de las que ofrece el portal.");
        }
    }
}
