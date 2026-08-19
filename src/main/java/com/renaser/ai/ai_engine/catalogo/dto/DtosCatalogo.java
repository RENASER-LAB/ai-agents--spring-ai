package com.renaser.ai.ai_engine.catalogo.dto;

import java.util.List;

/**
 * Los catálogos que necesita cualquier formulario del panel.
 *
 * <p>Existen porque el frontend los tenía copiados a mano y se desincronizaron: los desplegables
 * ofrecían códigos que la base no reconoce, y guardar fallaba. Un catálogo que vive en dos sitios
 * termina en dos versiones distintas.
 */
public final class DtosCatalogo {

    private DtosCatalogo() {}

    /** Una opción de un desplegable: el código que viaja y el nombre que se lee. */
    public record Opcion(String codigo, String nombre) {}

    /** Un estado, con lo que hace falta para dibujarlo en la rejilla de cinco por cuatro. */
    public record EstadoCatalogo(String codigo, String nombre, String etapaCodigo,
                                 String momentoCodigo, String esperaA, Integer orden,
                                 boolean esFinal) {}

    public record Catalogos(
            List<Opcion> nivelesPuesto,
            List<Opcion> familias,
            List<Opcion> etapas,
            List<Opcion> urgencias,
            List<Opcion> tiposCierre,
            List<Opcion> motivosCierre,
            List<EstadoCatalogo> estados) {}
}
