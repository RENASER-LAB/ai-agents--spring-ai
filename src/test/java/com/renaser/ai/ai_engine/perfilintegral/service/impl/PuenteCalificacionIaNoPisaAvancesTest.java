package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.pesos.repository.PesoComponentePerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El agente que cierra el Perfil Integral no puede devolver a nadie a una etapa que ya dejó.
 *
 * <p>Esto lo escribió un incidente real. Entre que la calificación empieza y termina pasan
 * minutos, y en ese rato una persona puede avanzar al candidato desde el panel. Al cerrar, el
 * agente lo mandaba a «Perfil Integral · por confirmar» sin mirar dónde estaba: le quitaba el
 * turno de la prueba que ya le habían dado y le dejaba el intento creado pero fuera de etapa.
 * Desde ahí, volver a avanzarlo chocaba contra la clave única de {@code intento_prueba} y el
 * panel solo sabía decir «ya existe un registro con postulacion_id X» — sin arreglo posible
 * desde la pantalla.
 *
 * <p>Las dos mitades que hay que sostener a la vez: <b>lo calificado se guarda siempre</b>
 * —el trabajo de la IA no se tira por llegar tarde— y <b>mover solo se mueve a quien sigue
 * en la etapa</b>.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El cierre del Perfil Integral no pisa un avance")
class PuenteCalificacionIaNoPisaAvancesTest {

    private static final Long POSTULACION = 735L;
    private static final Long VACANTE = 13L;
    private static final Long PUESTO = 4L;
    private static final Long EJECUCION_IA = 900L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private PerfilTalentoRepository perfiles;
    @Mock private HallazgoPerfilRepository hallazgos;
    @Mock private AlertaRepository alertas;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private PesoComponentePerfilRepository pesosComponente;
    // Sin stubs: lo que devuelven vacío deja la nota del currículum y las abiertas sin
    // valor, que es justo el caso más simple para lo que aquí se mira
    @Mock private CriterioRepository criterios;
    @Mock private PesoCriterioRepository pesosCriterio;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private RespuestaRepository respuestas;
    @Mock private PreguntaRepository preguntas;
    @Mock private NotaRespuestaRepository notasRespuesta;
    @Mock private ServicioCalificacion calificacion;
    @Mock private ServicioParametros parametros;
    @Mock private MaquinaEstados maquina;

    @InjectMocks
    private PuenteCalificacionIaImpl puente;

    private Postulacion postulacion;

    @BeforeEach
    void armar() {
        postulacion = Postulacion.builder()
                .id(POSTULACION).organizacionId(1L).vacanteId(VACANTE).evaluacionId(50L)
                .estadoCodigo("PERFIL_CALIFICANDO")
                .build();
        lenient().when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion));
        lenient().when(vacantes.findById(VACANTE)).thenReturn(Optional.of(Vacante.builder()
                .id(VACANTE).organizacionId(1L).puestoId(PUESTO).versionPesosId(2L).build()));
        lenient().when(puestos.findById(PUESTO)).thenReturn(Optional.of(Puesto.builder()
                .id(PUESTO).nombre("Analista").nivelPuestoCodigo("OPERATIVO").build()));
        lenient().when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        lenient().when(perfiles.save(any(PerfilTalento.class))).thenAnswer(i -> {
            PerfilTalento p = i.getArgument(0);
            p.setId(11L);
            return p;
        });
        lenient().when(pesosComponente.findByVersionPesosId(2L)).thenReturn(List.of());
        lenient().when(calificacion.resumenDeLoCerrado(POSTULACION))
                .thenReturn(new ServicioCalificacion.ResumenCerrado(new BigDecimal("70"), 20));
        lenient().when(notasEtapa.findByPostulacionIdAndEtapaCodigo(POSTULACION, "PERFIL_INTEGRAL"))
                .thenReturn(Optional.of(NotaEtapa.builder()
                        .postulacionId(POSTULACION).etapaCodigo("PERFIL_INTEGRAL")
                        .puntaje(new BigDecimal("70")).build()));
        lenient().when(parametros.entero(anyLong(), anyString(), anyInt())).thenReturn(80);
    }

    private ResultadoPerfil resultado() {
        return new ResultadoPerfil(new BigDecimal("70"), new BigDecimal("60"),
                new BigDecimal("55"), new BigDecimal("80"), "Va bien", List.of(), List.of());
    }

    @Test
    @DisplayName("si sigue en el Perfil Integral, se la mueve a «por confirmar» como siempre")
    void alQueSigueEnLaEtapaSeLeMueve() {
        when(maquina.sigueEnLaEtapa(postulacion, "PERFIL_INTEGRAL")).thenReturn(true);

        puente.cerrarPerfilIntegral(POSTULACION, EJECUCION_IA, resultado());

        verify(maquina).transicionar(eq(postulacion), eq("PERFIL_POR_CONFIRMAR"), any(), any(),
                eq(true), eq(false), any());
    }

    @Test
    @DisplayName("si ya la avanzaron a la prueba, se guarda su perfil pero no se la mueve")
    void alQueYaAvanzaronNoSeLeToca() {
        postulacion.setEstadoCodigo("PRUEBA_TURNO_CANDIDATO");
        when(maquina.sigueEnLaEtapa(postulacion, "PERFIL_INTEGRAL")).thenReturn(false);

        puente.cerrarPerfilIntegral(POSTULACION, EJECUCION_IA, resultado());

        // Nadie la devuelve a la bandeja anterior...
        verify(maquina, never()).transicionar(any(), anyString(), any(), any(),
                anyBoolean(), anyBoolean(), any());
        assertThat(postulacion.getEstadoCodigo()).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        // ...y su perfil, su nota y su grupo se guardan igual: el trabajo de la IA no se tira
        verify(perfiles).save(any(PerfilTalento.class));
        verify(notasEtapa).save(any(NotaEtapa.class));
        assertThat(postulacion.getGrupoPrioridad()).isNotNull();
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
