package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.InsumoRedactor;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.PreguntaGenerada;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.ResultadoRedactor;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.RecetaCuestionarioTecnico;
import com.renaser.ai.ai_engine.vacante.entity.FichaVacante;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.FichaVacanteRepository;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * El puente del REDACTOR: el insumo sale de la ficha COMPLETA con los temas puestos, y el
 * borrador se guarda como banco de la vacante — archivando al anterior, sin borrar nada.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El puente del REDACTOR")
class PuenteRedactorImplTest {

    private static final Long VACANTE = 50L;

    @Mock private VacanteRepository vacantes;
    @Mock private FichaVacanteRepository fichas;
    @Mock private PuestoRepository puestos;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private PreguntaRepository preguntas;

    @InjectMocks
    private PuenteRedactorImpl puente;

    private void conVacante() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(Vacante.builder()
                .id(VACANTE).organizacionId(1L).puestoId(7L)
                .titulo("Administrador de sedes").descripcion("Tres sedes").build()));
        when(puestos.findById(7L)).thenReturn(Optional.of(Puesto.builder()
                .id(7L).nivelPuestoCodigo("DIRECCION").build()));
    }

    private static FichaVacante fichaCompleta() {
        return FichaVacante.builder()
                .id(9L).vacanteId(VACANTE).organizacionId(1L).estado("COMPLETA")
                .q1Resultado("Rentabilidad al alza").q9Requerimientos("Excel y contabilidad")
                .riesgo1("Caja").riesgo2("Margen en divisas").riesgo3("Control a distancia")
                .riesgo4("Personal")
                .build();
    }

    @Test
    @DisplayName("El insumo pone el tema de cada bloque: los riesgos del dueño, en su orden")
    void elInsumoConSusTemas() {
        conVacante();
        when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.of(fichaCompleta()));

        InsumoRedactor insumo = puente.insumo(VACANTE);

        assertThat(insumo.nivel()).isEqualTo("DIRECCION");
        assertThat(insumo.estructura())
                .filteredOn(b -> RecetaCuestionarioTecnico.RIESGO_1.equals(b.bloque()))
                .singleElement().satisfies(b -> assertThat(b.tema()).isEqualTo("Caja"));
        assertThat(insumo.estructura())
                .filteredOn(b -> RecetaCuestionarioTecnico.PRESENCIAL.equals(b.bloque()))
                .singleElement()
                .satisfies(b -> assertThat(b.tema()).isEqualTo("Rentabilidad al alza"));
    }

    @Test
    @DisplayName("Media ficha no es un insumo: BORRADOR revienta antes de llamar al modelo")
    void mediaFichaNoEsInsumo() {
        // Sin conVacante(): la ficha frena antes de llegar a preguntar el nivel del puesto.
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(Vacante.builder()
                .id(VACANTE).organizacionId(1L).puestoId(7L).build()));
        when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.of(FichaVacante.builder()
                .vacanteId(VACANTE).estado("BORRADOR").build()));

        assertThatThrownBy(() -> puente.insumo(VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a medias");
    }

    @Test
    @DisplayName("Sin ficha tampoco hay insumo")
    void sinFichaNoHayInsumo() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(Vacante.builder()
                .id(VACANTE).organizacionId(1L).puestoId(7L).build()));
        when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.insumo(VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene ficha");
    }

    @Test
    @DisplayName("Guardar el borrador crea el banco VACANTE·CRITERIOS y archiva al anterior")
    void guardarArchivaYCrea() {
        conVacante();
        VersionBanco anterior = VersionBanco.builder()
                .id(30L).vacanteId(VACANTE).estado("BORRADOR").build();
        when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "BORRADOR"))
                .thenReturn(Optional.of(anterior));
        when(versionesBanco.save(any())).thenAnswer(i -> {
            VersionBanco v = i.getArgument(0);
            if (v.getId() == null) {
                v.setId(31L);
            }
            return v;
        });
        when(versionesBanco.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        puente.guardarBorrador(VACANTE, new ResultadoRedactor(List.of(
                new PreguntaGenerada("T01", "EXPERIENCIA", "Experiencia y escala",
                        "¿Cuántos años administrando?", "años y sedes", "lo que falló",
                        "respuesta genérica", false),
                new PreguntaGenerada("T02", "PRESENCIAL", "Muestra de trabajo",
                        "¿Qué necesitarías conocer en 30 días?", null, null, null, true))));

        assertThat(anterior.getEstado()).isEqualTo("ARCHIVADA");
        // El archivado va con saveAndFlush a propósito (el orden de flush de Hibernate
        // rompería el índice de un-borrador-vivo); la creación va con save normal.
        org.mockito.Mockito.verify(versionesBanco).saveAndFlush(anterior);

        ArgumentCaptor<VersionBanco> version = ArgumentCaptor.forClass(VersionBanco.class);
        org.mockito.Mockito.verify(versionesBanco).save(version.capture());
        VersionBanco creada = version.getValue();
        assertThat(creada.getTipoBanco()).isEqualTo("VACANTE");
        assertThat(creada.getMetodoCalificacion()).isEqualTo("CRITERIOS");
        assertThat(creada.getVacanteId()).isEqualTo(VACANTE);
        assertThat(creada.getEstado()).isEqualTo("BORRADOR");
        assertThat(creada.getNivelPuestoCodigo()).isEqualTo("DIRECCION");

        ArgumentCaptor<Pregunta> pregunta = ArgumentCaptor.forClass(Pregunta.class);
        org.mockito.Mockito.verify(preguntas, org.mockito.Mockito.times(2))
                .save(pregunta.capture());
        Pregunta laAbierta = pregunta.getAllValues().get(0);
        assertThat(laAbierta.getTipo()).isEqualTo("ABIERTA");
        assertThat(laAbierta.isEsPuntuable()).isTrue();
        assertThat(laAbierta.getPeso()).isEqualTo((short) 1);
        assertThat(laAbierta.getOrden()).isEqualTo(1);
        Pregunta laMuestra = pregunta.getAllValues().get(1);
        // La muestra se guarda pero no puntúa ni viaja: es del dueño.
        assertThat(laMuestra.isPresencial()).isTrue();
        assertThat(laMuestra.isEsPuntuable()).isFalse();
        assertThat(laMuestra.getOrden()).isEqualTo(2);
    }
}
