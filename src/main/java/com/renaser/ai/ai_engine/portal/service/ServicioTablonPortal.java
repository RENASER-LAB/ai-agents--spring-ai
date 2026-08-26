package com.renaser.ai.ai_engine.portal.service;

import com.renaser.ai.ai_engine.portal.dto.DtosPortal.ConsentimientoDeVacante;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.VacantePublica;

import java.util.List;

/**
 * El tablón público de vacantes: la única pantalla del sistema que mezcla empresas, a
 * propósito (pieza B). Se sirve sin haber entrado —es lo que hace plataforma a la
 * plataforma— y cada vacante dice de qué empresa es, porque el candidato tiene que
 * saber a quién le manda su currículum.
 */
public interface ServicioTablonPortal {

    List<VacantePublica> vacantesPublicadas();

    VacantePublica vacante(Long id);

    // El texto PROCESO publicado de la empresa de esa vacante: lo que el candidato
    // acepta al postular. 404 si la vacante no está publicada.
    ConsentimientoDeVacante consentimientoDeVacante(Long vacanteId);
}
