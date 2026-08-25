package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.Personalizacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * Encender y apagar las banderas de personalización (pieza A).
 *
 * <p>Encender = copiar el instrumento publicado de la plataforma y pasar a leer lo
 * propio, en una transacción. Apagar = volver a leer el de la plataforma; lo propio se
 * archiva o se queda, nunca se borra (RF-138): las notas de los ya evaluados siguen
 * apuntando a filas que existen. Reencender copia desde la plataforma actual, no
 * resucita la copia vieja.
 */
public interface ServicioPersonalizacion {

    Personalizacion ver(ContextoUsuario quien);

    void encender(ContextoUsuario quien, Instrumento instrumento);

    void apagar(ContextoUsuario quien, Instrumento instrumento);
}
