package com.renaser.ai.ai_engine.decision.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.decision.dto.DtosDecision.RegistrarBarrera;
import com.renaser.ai.ai_engine.decision.entity.BarreraCritica;
import com.renaser.ai.ai_engine.decision.repository.BarreraCriticaRepository;
import com.renaser.ai.ai_engine.decision.repository.BarreraDetectadaRepository;
import com.renaser.ai.ai_engine.decision.repository.DecisionRepository;
import com.renaser.ai.ai_engine.decision.repository.EvidenciaAdicionalRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.pesos.repository.PesoEtapaRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La guarda de la barrera detectada (pieza B, fuga cuarta).
 *
 * <p>La barrera crítica cuelga de una vacante. Antes se buscaba por id suelto: se le
 * podía marcar a un candidato la barrera de OTRA vacante — incluso de otra empresa — y
 * la ficha la enseñaba después como propia. La barrera citada tiene que ser de la misma
 * vacante que la postulación, y la ajena responde «no existe», no «prohibido».
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Registrar una barrera detectada")
class ServicioDecisionImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long POSTULACION = 88L;
    private static final Long VACANTE = 40L;
    private static final Long USUARIO = 21L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            USUARIO, 33L, ORGANIZACION, "EQUIPO", List.of(), Map.of());

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private BarreraCriticaRepository barrerasCriticas;
    @Mock private BarreraDetectadaRepository barrerasDetectadas;
    @Mock private DecisionRepository decisiones;
    @Mock private EvidenciaAdicionalRepository evidencias;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private PesoEtapaRepository pesosEtapa;
    @Mock private MaquinaEstados maquina;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioAuditoria auditoria;
    @Mock private Permisos permisos;

    private ServicioDecisionImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioDecisionImpl(postulaciones, vacantes, barrerasCriticas,
                barrerasDetectadas, decisiones, evidencias, notasEtapa, pesosEtapa,
                maquina, parametros, auditoria, permisos);
    }

    private void hayPostulacionDeLaVacante(Long vacanteId) {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(vacanteId)
                        .build()));
        when(permisos.alcanceDe("decidir_contratacion"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
    }

    @Test
    @DisplayName("la barrera de otra vacante no se le marca a este candidato")
    void laBarreraDeOtraVacanteNoSeMarca() {
        hayPostulacionDeLaVacante(VACANTE);
        // La barrera existe, pero es de la vacante 99: para esta postulación, no existe
        when(barrerasCriticas.findById(7L)).thenReturn(Optional.of(BarreraCritica.builder()
                .id(7L).vacanteId(99L).descripcion("Antecedentes en caja").esActiva(true)
                .build()));

        assertThatThrownBy(() -> servicio.registrarBarreraDetectada(QUIEN, POSTULACION,
                new RegistrarBarrera(7L, "Se detectó en la entrevista")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(barrerasDetectadas, never()).save(any());
    }

    @Test
    @DisplayName("la barrera de la misma vacante sí se registra")
    void laBarreraDeLaMismaVacanteSeRegistra() {
        hayPostulacionDeLaVacante(VACANTE);
        when(barrerasCriticas.findById(7L)).thenReturn(Optional.of(BarreraCritica.builder()
                .id(7L).vacanteId(VACANTE).descripcion("Antecedentes en caja").esActiva(true)
                .build()));
        when(barrerasDetectadas.save(any())).thenAnswer(inv -> {
            var fila = (com.renaser.ai.ai_engine.decision.entity.BarreraDetectada) inv.getArgument(0);
            fila.setId(70L);
            return fila;
        });

        servicio.registrarBarreraDetectada(QUIEN, POSTULACION,
                new RegistrarBarrera(7L, "Se detectó en la entrevista"));

        verify(barrerasDetectadas).save(any());
    }
}
