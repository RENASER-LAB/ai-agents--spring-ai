package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.InsumoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.NotaCriterioPruebaIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.ResultadoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.service.ContextoDeLaVacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que el agente de la prueba puede escribir en la rúbrica, y lo que no.
 *
 * <p>Aquí viven las cuatro reglas que hacen que una nota de agente se pueda defender delante
 * de un candidato:
 *
 * <ul>
 *   <li><b>La rúbrica decide quién mira cada criterio</b>, no el modelo. Uno marcado para
 *       persona no se le enseña, y si aun así lo puntúa, no entra.
 *   <li><b>Una nota sin explicación no se guarda</b> (RF-150). No se pone un cero en su
 *       lugar: quedarse sin nota es distinto de valer cero.
 *   <li><b>Un ajuste a mano no se pisa nunca.</b> Alguien ya miró ese criterio y decidió; una
 *       segunda pasada del agente no puede borrar esa decisión sin que nadie se entere.
 *   <li><b>Cada criterio tiene su propia escala.</b> Un criterio que vale 20 no puede recibir
 *       un 80 porque el modelo se confundiera de escala.
 * </ul>
 *
 * <p>Se montan solo las piezas que cada método usa; las demás entran en nulo a propósito,
 * porque una calificación que de pronto necesite leer el currículum sería un cambio que hay
 * que ver, no uno que pase inadvertido.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El puente entre la IA y la prueba del puesto")
class PuentePruebaIaImplTest {

    private static final long POSTULACION = 55L;
    private static final long VERSION = 3L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private VersionPlantillaPruebaRepository versiones;
    @Mock private EntregableRepository entregables;
    @Mock private EntregableRequeridoRepository entregablesRequeridos;
    @Mock private PreguntaVersionPlantillaRepository preguntasElegidas;
    @Mock private CriterioRepository criterios;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private CalificacionPorCriterio calificacion;
    @Mock private ContextoDeLaVacante contexto;
    @Mock private MaquinaEstados maquina;

    @InjectMocks
    private PuentePruebaIaImpl puente;

    // ============ Lo que se le manda al agente ============

    @Test
    void unaPruebaSinEntregarNoSeCalifica() {
        // El candidato podía estar escribiendo todavía. Lo que se leyera ahora no daría una
        // nota baja: daría una nota que no significa nada.
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion(null)));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(sinEntregar()));

        assertThatThrownBy(() -> puente.insumoPrueba(POSTULACION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está entregada");
    }

    @Test
    void soloSeLeEnsenanLosCriteriosQueLaRubricaLeReserva() {
        Criterio delAgente = criterio(1L, "PR_CALIDAD", "AGENTE", 20);
        Criterio deLaPersona = criterio(2L, "PR_PRESENTACION", "PERSONA", 30);
        montarElInsumo(List.of(delAgente, deLaPersona), Map.of(
                1L, BigDecimal.valueOf(20), 2L, BigDecimal.valueOf(30)));

        InsumoPrueba insumo = puente.insumoPrueba(POSTULACION);

        assertThat(insumo.criterios()).hasSize(1);
        assertThat(insumo.criterios().get(0).codigo()).isEqualTo("PR_CALIDAD");
        assertThat(insumo.criterios().get(0).puntosMaximos()).isEqualByComparingTo("20");
    }

    @Test
    void unCriterioSinEscalaSeQuedaFuera() {
        // Sin puntos y sin peso no hay entre qué y qué puntuar. Vale más dejarlo para una
        // persona que inventarle un máximo de cien y que la nota parezca comparable.
        Criterio sinEscala = criterio(1L, "PR_SIN_PUNTOS", "AGENTE", null);
        montarElInsumo(List.of(sinEscala), Map.of());

        assertThat(puente.insumoPrueba(POSTULACION).criterios()).isEmpty();
    }

    // ============ Lo que se guarda de lo que contesta ============

    @Test
    void unaNotaSinExplicacionNoSeGuarda() {
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "PRUEBA_CALIFICANDO");

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", BigDecimal.valueOf(15), " ", null)),
                BigDecimal.valueOf(90)));

        verify(notasCriterio, never()).save(any());
    }

    @Test
    void loQueLaRubricaReservaAUnaPersonaNoLoPisaElAgente() {
        // Aunque el modelo se salte el filtro y lo puntúe igual. La decisión de quién mira
        // cada criterio la tomó quien escribió la rúbrica, no el modelo.
        montarLaRubrica(List.of(criterio(1L, "PR_PRESENTACION", "PERSONA", 30)),
                Map.of(1L, BigDecimal.valueOf(30)), "PRUEBA_CALIFICANDO");

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_PRESENTACION", BigDecimal.valueOf(25),
                        "Se ve bien", null)),
                BigDecimal.valueOf(90)));

        verify(notasCriterio, never()).save(any());
    }

    @Test
    void unAjusteHechoAManoNoSeBorra() {
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "PRUEBA_CALIFICANDO");
        NotaCriterio yaAjustada = NotaCriterio.builder()
                .id(9L).postulacionId(POSTULACION).criterioId(1L)
                .puntaje(BigDecimal.valueOf(18)).ajustadaPorUsuarioId(4L)
                .build();
        when(notasCriterio.findByPostulacionIdAndCriterioId(POSTULACION, 1L))
                .thenReturn(Optional.of(yaAjustada));

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", BigDecimal.valueOf(4),
                        "A mí me parece flojo", null)),
                BigDecimal.valueOf(90)));

        verify(notasCriterio, never()).save(any());
    }

    @Test
    void elPuntajeSeAcotaAlMaximoDeEseCriterioYQuedaSelladoComoDeAgente() {
        // El modelo puede devolver 80 porque en el hito 2 la escala era otra. Se acota en vez
        // de fallar: un 80 sobre 20 no es un fallo del candidato, es un despiste del modelo.
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "PRUEBA_CALIFICANDO");
        when(notasCriterio.findByPostulacionIdAndCriterioId(POSTULACION, 1L))
                .thenReturn(Optional.empty());

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", BigDecimal.valueOf(80),
                        "Resolvió lo pedido", "La hoja cuadra")),
                BigDecimal.valueOf(90)));

        ArgumentCaptor<NotaCriterio> guardada = ArgumentCaptor.forClass(NotaCriterio.class);
        verify(notasCriterio).save(guardada.capture());
        assertThat(guardada.getValue().getPuntaje()).isEqualByComparingTo("20");
        assertThat(guardada.getValue().getOrigen()).isEqualTo("AGENTE");
        assertThat(guardada.getValue().getEjecucionIaId()).isEqualTo(77L);
        assertThat(guardada.getValue().getExplicacion()).contains("La hoja cuadra");
    }

    // ============ Dónde queda la postulación ============

    @Test
    void alTerminarLaPruebaPasaAManosDeUnaPersona() {
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "PRUEBA_CALIFICANDO");
        when(notasCriterio.findByPostulacionIdAndCriterioId(POSTULACION, 1L))
                .thenReturn(Optional.empty());

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", BigDecimal.valueOf(15),
                        "Cumple", null)),
                BigDecimal.valueOf(90)));

        verify(maquina).transicionar(any(Postulacion.class), eq("PRUEBA_POR_CONFIRMAR"),
                eq(null), eq(null), eq(true), eq(false), eq(null));
    }

    @Test
    void siAlguienYaLaMovioAManoNoSeLaDevuelveHaciaAtras() {
        // El trabajo puede pasar minutos en la cola. En ese rato alguien pudo calificarla a
        // mano y confirmarla: volver a moverla la sacaría de donde su dueño la dejó.
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "SIMULACION_POR_HABILITAR");
        when(notasCriterio.findByPostulacionIdAndCriterioId(POSTULACION, 1L))
                .thenReturn(Optional.empty());

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", BigDecimal.valueOf(15),
                        "Cumple", null)),
                BigDecimal.valueOf(90)));

        verifyNoInteractions(maquina);
    }

    @Test
    void unResultadoVacioNoSeGuardaNiMueveNada() {
        assertThatThrownBy(() -> puente.guardarNotasPrueba(POSTULACION, 77L, null))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(maquina);
        verifyNoInteractions(notasCriterio);
    }

    // ============ Apoyo ============

    /** Deja montado lo justo para pedir el insumo: la prueba entregada y su rúbrica. */
    private void montarElInsumo(List<Criterio> rubrica, Map<Long, BigDecimal> maximos) {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion(null)));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(entregado()));
        when(versiones.findById(VERSION)).thenReturn(Optional.of(version()));
        when(contexto.puestoDe(any(Postulacion.class))).thenReturn(puesto());
        when(contexto.queBuscaLaVacanteDe(any(Postulacion.class))).thenReturn("Se busca...");
        when(criterios.findByVersionPlantillaPruebaId(VERSION)).thenReturn(rubrica);
        when(calificacion.maximosDe(eq(POSTULACION), anyList())).thenReturn(maximos);
        when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of());
        when(entregables.findByIntentoPruebaId(8L)).thenReturn(List.of());
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of());
    }

    /** Deja montado lo justo para guardar: la rúbrica y dónde está la postulación. */
    private void montarLaRubrica(List<Criterio> rubrica, Map<Long, BigDecimal> maximos,
                                 String estado) {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion(estado)));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(entregado()));
        when(criterios.findByVersionPlantillaPruebaId(VERSION)).thenReturn(rubrica);
        when(calificacion.maximosDe(eq(POSTULACION), anyList())).thenReturn(maximos);
    }

    private Postulacion postulacion(String estado) {
        return Postulacion.builder()
                .id(POSTULACION).organizacionId(1L).vacanteId(7L)
                .estadoCodigo(estado)
                .build();
    }

    private IntentoPrueba entregado() {
        return IntentoPrueba.builder()
                .id(8L).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION)
                .iniciadoEn(Instant.now()).entregadoEn(Instant.now())
                .build();
    }

    private IntentoPrueba sinEntregar() {
        return IntentoPrueba.builder()
                .id(8L).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION)
                .iniciadoEn(Instant.now())
                .build();
    }

    private VersionPlantillaPrueba version() {
        return VersionPlantillaPrueba.builder()
                .id(VERSION).version(1).enunciado("Arma el tablero")
                .modalidad("CRONOMETRADA").duracionMinutos(120)
                .build();
    }

    private Puesto puesto() {
        return Puesto.builder()
                .id(3L).nombre("Analista de procesos").nivelPuestoCodigo("OPERATIVO")
                .build();
    }

    private Criterio criterio(Long id, String codigo, String metodo, Integer puntos) {
        return Criterio.builder()
                .id(id).codigo(codigo).nombre(codigo).descripcion("Qué mide")
                .etapaCodigo("PRUEBA_PUESTO").versionPlantillaPruebaId(VERSION)
                .puntos(puntos == null ? null : BigDecimal.valueOf(puntos))
                .metodoVerificacion(metodo).orden(1)
                .build();
    }
}
