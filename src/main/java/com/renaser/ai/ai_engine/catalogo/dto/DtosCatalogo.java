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
    /**
     * Una etapa del catálogo (V95): su explicación, la de la ⓘ, y sus marcas. Las que una vacante
     * no tiene se pintan apagadas en «Etapas de esta vacante» con esto.
     *
     * @param fijaPrimera      va siempre primera y no se quita ni se mueve
     * @param enPredeterminado forma parte del recorrido con el que nace una vacante
     */
    public record EtapaCatalogo(String codigo, String nombre, String descripcion,
                                boolean fijaPrimera, boolean enPredeterminado, Integer orden) {}

    public record EstadoCatalogo(String codigo, String nombre, String etapaCodigo,
                                 String momentoCodigo, String esperaA, Integer orden,
                                 boolean esFinal) {}

    public record Catalogos(
            List<Opcion> nivelesPuesto,
            List<Opcion> familias,
            List<EtapaCatalogo> etapas,
            List<Opcion> urgencias,
            List<Opcion> tiposCierre,
            List<Opcion> motivosCierre,
            List<EstadoCatalogo> estados) {}
}
