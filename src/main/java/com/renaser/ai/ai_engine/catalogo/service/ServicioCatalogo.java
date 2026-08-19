package com.renaser.ai.ai_engine.catalogo.service;

import com.renaser.ai.ai_engine.catalogo.dto.DtosCatalogo.Catalogos;

/**
 * Los valores que admiten los formularios del panel, en una sola llamada.
 *
 * <p>Existe porque antes no había ninguno y cualquier pantalla tenía que llevar los códigos
 * escritos a mano. Eso ya falló una vez: los desplegables ofrecían familias que la base no
 * reconoce y guardar reventaba con una violación de clave foránea.
 */
public interface ServicioCatalogo {

    /** Niveles, familias, etapas, urgencias, tipos y motivos de cierre, y los 18 estados. */
    Catalogos todos();
}
