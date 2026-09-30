package com.renaser.ai.ai_engine.colaborador.service;

import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.ResultadoCarga;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * La carga de colaboradores por Excel: altas nuevas y actualización de datos personales.
 *
 * <p>Todo o nada, como la importación del banco: primero se valida el archivo entero y, si hay
 * un solo error, no se guarda nada y se lanza {@link CargaInvalidaException} con todos.
 */
public interface ServicioCargaColaboradores {

    /** La plantilla de hoy. La columna de sueldo solo sale con {@code ver_sueldos}. */
    byte[] plantilla(ContextoUsuario quien);

    ResultadoCarga cargar(ContextoUsuario quien, String nombreDelArchivo, byte[] contenido);
}
