package com.renaser.ai.ai_engine.organizacion.service;

import java.util.Optional;

/**
 * Las tres plantillas del brochure en PDF (V86). Un catálogo fijo en el código y sin tabla, como
 * las portadas del perfil ({@code PortadasDeLaCasa}): no son datos, son tres dibujos que arma
 * {@code PdfDelBrochure}. La empresa solo guarda cuál eligió; el código es lo que exige el CHECK
 * de la columna, y el nombre, lo que se lee en el panel.
 *
 * <p>Añadir una plantilla es añadirla aquí (y en el CHECK y el panel): las empresas que ya
 * eligieron otra no cambian.
 */
public enum PlantillaDelBrochure {
    /** Solo tipografía, filetes, el color y el logo. La de por defecto: no necesita fotos. */
    CLASICA("Clásica"),
    /** La foto de portada a toda la primera hoja, con un velo oscuro y el propósito encima. */
    PORTADA("Portada"),
    /** La estructura del informe por etapa: una columna del color a la izquierda. */
    COLUMNAS("Columnas");

    public static final PlantillaDelBrochure POR_DEFECTO = CLASICA;

    private final String nombre;

    PlantillaDelBrochure(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }

    public static Optional<PlantillaDelBrochure> deCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        for (PlantillaDelBrochure plantilla : values()) {
            if (plantilla.name().equals(codigo.strip())) {
                return Optional.of(plantilla);
            }
        }
        return Optional.empty();
    }

    /** La guardada, o la de por defecto si lo guardado ya no se reconoce. */
    public static PlantillaDelBrochure deLoGuardado(String codigo) {
        return deCodigo(codigo).orElse(POR_DEFECTO);
    }
}
