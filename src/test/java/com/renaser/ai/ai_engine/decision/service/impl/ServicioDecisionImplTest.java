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

    // ============ El alcance: qué postulaciones y qué vacantes alcanza quien mira ============

    /**
     * Caracterización antes de migrar al guardián compartido.
     *
     * <p>Este servicio tenía las dos formas de la regla escritas a mano y ni una prueba de
     * alcance, con seis llamadas y cinco permisos distintos. Es el archivo donde más fácil es
     * pegar el permiso equivocado al migrar, y el error no se vería: {@code @PreAuthorize}
     * seguiría guardando el endpoint y solo el <i>alcance</i> saldría del permiso de al lado.
     * Estas pruebas fijan qué permiso mira cada camino.
     */
    private static final Long OTRO_USUARIO = 77L;

    private void laPostulacionEsDeLaVacante() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE).build()));
    }

    private void laVacanteEsDe(Long responsable) {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.vacante.entity.Vacante.builder()
                        .id(VACANTE).organizacionId(ORGANIZACION)
                        .responsableUsuarioId(responsable).build()));
    }

    @Test
    @DisplayName("El semáforo de una postulación de vacante ajena responde 404")
    void elSemaforoDeUnaAjenaNoSeAbre() {
        laPostulacionEsDeLaVacante();
        when(permisos.alcanceDe("ver_semaforo_decision"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
        laVacanteEsDe(OTRO_USUARIO);

        assertThatThrownBy(() -> servicio.verSemaforo(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Pedir evidencia adicional mira su propio permiso, no el del semáforo")
    void pedirEvidenciaMiraSuPermiso() {
        // Si mirara el del semáforo, un rol con ver_semaforo_decision libre y
        // pedir_evidencia_adicional acotado pediría evidencia de convocatorias ajenas.
        laPostulacionEsDeLaVacante();
        when(permisos.alcanceDe("pedir_evidencia_adicional"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
        laVacanteEsDe(OTRO_USUARIO);

        assertThatThrownBy(() -> servicio.pedirEvidenciaAdicional(QUIEN, POSTULACION,
                new com.renaser.ai.ai_engine.decision.dto.DtosDecision.PedirEvidencia(
                        "Falta el certificado", "Adjunta el título")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(permisos).alcanceDe("pedir_evidencia_adicional");
        verify(permisos, never()).alcanceDe("ver_semaforo_decision");
    }

    @Test
    @DisplayName("Definir barreras en una vacante que no dirige responde 404")
    void lasBarrerasDeUnaVacanteAjena() {
        when(permisos.alcanceDe("definir_barreras_criticas"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.vacante.entity.Vacante.builder()
                        .id(VACANTE).organizacionId(ORGANIZACION)
                        .responsableUsuarioId(OTRO_USUARIO).build()));

        assertThatThrownBy(() -> servicio.listarBarrerasDeVacante(QUIEN, VACANTE))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
