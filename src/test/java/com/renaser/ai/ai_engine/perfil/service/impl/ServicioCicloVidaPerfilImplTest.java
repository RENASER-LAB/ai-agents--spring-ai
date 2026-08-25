package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El borrado del perfil")
class ServicioCicloVidaPerfilImplTest {

    private static final long PERSONA = 30L;
    private static final long PERFIL = 40L;

    @Mock private PerfilCandidatoRepository perfiles;
    @Mock private ExperienciaPerfilRepository experiencias;
    @Mock private EducacionPerfilRepository educaciones;
    @Mock private IdiomaPerfilRepository idiomas;
    @Mock private CertificacionPerfilRepository certificaciones;
    @Mock private EnlacePerfilRepository enlaces;

    private ServicioCicloVidaPerfilImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioCicloVidaPerfilImpl(perfiles, experiencias, educaciones,
                idiomas, certificaciones, enlaces);
    }

    @Test
    @DisplayName("Borra las seis tablas, las hijas primero: las FK no dejan otro orden")
    void borraTodoLoQueCuelga() {
        PerfilCandidato perfil = PerfilCandidato.builder().id(PERFIL).personaId(PERSONA).build();
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(perfil));

        servicio.borrarPorPersona(PERSONA);

        verify(experiencias).deleteByPerfilCandidatoId(PERFIL);
        verify(educaciones).deleteByPerfilCandidatoId(PERFIL);
        verify(idiomas).deleteByPerfilCandidatoId(PERFIL);
        verify(certificaciones).deleteByPerfilCandidatoId(PERFIL);
        verify(enlaces).deleteByPerfilCandidatoId(PERFIL);
        verify(perfiles).delete(perfil);
    }

    @Test
    @DisplayName("Sin perfil no hay nada que borrar, y no es un error")
    void sinPerfilNoHaceNada() {
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.empty());

        servicio.borrarPorPersona(PERSONA);

        verifyNoInteractions(experiencias, educaciones, idiomas, certificaciones, enlaces);
    }
}
