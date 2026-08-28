package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.dto.DtosFichaVacante.FichaResponse;
import com.renaser.ai.ai_engine.vacante.dto.DtosFichaVacante.GuardarFicha;

// La ficha de vacante: las 10 preguntas al dueño y sus salidas (tamaño, familias,
// eliminatorias). COMPLETA es la llave que enciende «generar cuestionario técnico».
public interface ServicioFichaVacante {

    FichaResponse ver(ContextoUsuario quien, Long vacanteId);

    FichaResponse guardar(ContextoUsuario quien, Long vacanteId, GuardarFicha datos);
}
