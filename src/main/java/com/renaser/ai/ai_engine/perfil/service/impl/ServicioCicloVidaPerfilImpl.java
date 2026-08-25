package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfil.service.ServicioCicloVidaPerfil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioCicloVidaPerfilImpl implements ServicioCicloVidaPerfil {

    private final PerfilCandidatoRepository perfiles;
    private final ExperienciaPerfilRepository experiencias;
    private final EducacionPerfilRepository educaciones;
    private final IdiomaPerfilRepository idiomas;
    private final CertificacionPerfilRepository certificaciones;
    private final EnlacePerfilRepository enlaces;

    @Override
    @Transactional
    public void borrarPorPersona(Long personaId) {
        perfiles.findByPersonaId(personaId).ifPresent(perfil -> {
            // Las hijas primero: las FK no dejan otro orden.
            experiencias.deleteByPerfilCandidatoId(perfil.getId());
            educaciones.deleteByPerfilCandidatoId(perfil.getId());
            idiomas.deleteByPerfilCandidatoId(perfil.getId());
            certificaciones.deleteByPerfilCandidatoId(perfil.getId());
            enlaces.deleteByPerfilCandidatoId(perfil.getId());
            perfiles.delete(perfil);
            log.info("Perfil de la persona {} borrado con todo lo que colgaba de el", personaId);
        });
    }
}
