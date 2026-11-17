package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.AfirmacionIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;
import com.renaser.ai.ai_engine.perfilintegral.repository.AfirmacionCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las afirmaciones del CV con la marca del anonimizador no se guardan (spec
 * la-ia-sabe-que-dia-es-hoy, AC-08): hablan de un dato que la IA no vio, y en la ficha salían como
 * «Tiene más de [DATO NO UTILIZABLE] de experiencia», con una pregunta para validarla.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Las afirmaciones del CV con un dato tapado")
class PuenteCalificacionIaAfirmacionesTest {

    private static final Long POSTULACION = 40L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private CriterioRepository criterios;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private CvRepository cvs;
    @Mock private AfirmacionCvRepository afirmaciones;

    @InjectMocks private PuenteCalificacionIaImpl puente;

    @Test
    @DisplayName("AC-08 · la afirmación con la marca, o cuya pregunta la lleva, no se guarda; las demás sí")
    void noSeGuardaLaQueTraeLaMarca() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(
                Postulacion.builder().id(POSTULACION).vacanteId(5L).organizacionId(1L).build()));
        when(vacantes.findById(5L)).thenReturn(Optional.of(
                Vacante.builder().id(5L).puestoId(2L).versionPesosId(3L).build()));
        when(puestos.findById(2L)).thenReturn(Optional.of(Puesto.builder().id(2L).nivelPuestoCodigo("ANALISTA").build()));
        when(criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden("PERFIL_INTEGRAL"))
                .thenReturn(List.of());
        when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(Cv.builder().id(7L).build()));

        puente.guardarEvidenciaCv(POSTULACION, 900L, new ResultadoCv(List.of(), List.of(
                new AfirmacionIa("Tiene más de [DATO NO UTILIZABLE] de experiencia en Recursos Humanos",
                        "FALTA_INFO", "¿Cuántos años de experiencia tiene?"),
                new AfirmacionIa("Lideró un equipo de cinco", "DECLARADA",
                        "¿A qué edad [DATO NO UTILIZABLE] lo empezó?"),
                new AfirmacionIa("Tiene más de 5 años de experiencia en Recursos Humanos", "DEMOSTRADA", null)),
                BigDecimal.valueOf(80)));

        verify(afirmaciones).deleteByCvId(7L);
        ArgumentCaptor<AfirmacionCv> guardadas = ArgumentCaptor.forClass(AfirmacionCv.class);
        verify(afirmaciones, times(1)).save(guardadas.capture());
        assertThat(guardadas.getAllValues()).extracting(AfirmacionCv::getTexto)
                .containsExactly("Tiene más de 5 años de experiencia en Recursos Humanos");
    }
}
