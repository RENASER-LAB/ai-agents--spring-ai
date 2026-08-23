package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.DimensionResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.ResultadoImportacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * La entrada del banco de preguntas por Excel: el administrador sube la plantilla llena,
 * elige el rol, y aquí se convierte en una versión BORRADOR que revisará y publicará con
 * el ciclo de siempre ({@link ServicioBancoPreguntas}). Subir nunca publica.
 *
 * <p>Interfaz aparte y no un método más de ServicioBancoPreguntas: la importación tiene
 * su propio mundo (el lector del archivo, la lista de errores, la inserción por lotes) y
 * aquella interfaz ya carga con todo el ciclo de vida del banco.
 */
public interface ServicioImportacionBanco {

    /**
     * Convierte el archivo en una versión BORRADOR, o lanza
     * {@link ImportacionInvalidaException} con la lista completa de problemas sin dejar
     * nada en la base.
     */
    ResultadoImportacion importar(ContextoUsuario quien, String nivelPuestoCodigo,
                                  String etiqueta, String nombreArchivo, byte[] archivo);

    /** El catálogo de dimensiones, para que el panel sepa qué vale en «Qué mide». */
    List<DimensionResponse> listarDimensiones(ContextoUsuario quien);
}
