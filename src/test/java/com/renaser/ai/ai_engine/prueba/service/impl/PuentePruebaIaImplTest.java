package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.postulacion.service.impl.ExtractorTextoCv;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.CriterioDeRubrica;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.EntregaDelCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.InsumoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.NotaCriterioPruebaIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.RespuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.ResultadoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.Entregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
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
 * <p>Y una quinta, que es de la ida y no de la vuelta: <b>un entregable que no se puede leer
 * no es un fallo, es un dato</b>. Un video, unas diapositivas exportadas como imagen o un
 * enlace a un repositorio son entregas perfectamente válidas. Si leerlas lanzara, una sola
 * dejaría sin calificar la prueba entera; y si se presentaran como vacías, el modelo pondría
 * un cero a alguien que sí entregó. Se le cuenta con palabras por qué no hay contenido, y ese
 * criterio se queda para una persona.
 *
 * <p>Cada test monta solo las piezas que su camino usa: lo que no se estimula devuelve el
 * vacío por defecto de Mockito, así que una calificación que de pronto empiece a leer algo
 * que antes no leía se nota aquí en vez de pasar inadvertida.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El puente entre la IA y la prueba del puesto")
class PuentePruebaIaImplTest {

    private static final long POSTULACION = 55L;
    private static final long VERSION = 3L;
    private static final long INTENTO = 8L;
    private static final String QUE_BUSCA = "Se busca...";

    @Mock private PostulacionRepository postulaciones;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private VersionPlantillaPruebaRepository versiones;
    @Mock private VarianteCambioRepository variantes;
    @Mock private EntregableRepository entregables;
    @Mock private EntregableRequeridoRepository entregablesRequeridos;
    @Mock private RespuestaPruebaRepository respuestas;
    @Mock private PreguntaPruebaRepository preguntasCatalogo;
    @Mock private PreguntaVersionPlantillaRepository preguntasElegidas;
    @Mock private CriterioRepository criterios;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private ExtractorTextoCv extractor;
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

    // ============ Cuando falta una pieza para armar el insumo ============

    @Test
    @DisplayName("una postulación que ya no existe no se califica")
    void unaPostulacionQueNoExisteNoSeCalifica() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.insumoPrueba(POSTULACION))
                .as("es un «no está», no una avería: quien llame tiene que poder distinguirlo")
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(intentos);
    }

    @Test
    @DisplayName("sin prueba rendida no hay nada que calificar")
    void sinIntentoDePruebaNoHayNadaQueCalificar() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion(null)));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.insumoPrueba(POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("si la versión de la plantilla desapareció, no se califica sin enunciado")
    void sinLaVersionDeLaPlantillaNoSeCalifica() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion(null)));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(entregado()));
        when(versiones.findById(VERSION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.insumoPrueba(POSTULACION))
                .as("sin enunciado el modelo puntuaría un trabajo sin saber qué se encargó")
                .isInstanceOf(IllegalStateException.class);
    }

    // ============ Contra qué se juzga la prueba ============

    @Test
    @DisplayName("el insumo lleva el puesto, la convocatoria y lo que se pidió")
    void elInsumoLlevaContraQueSeJuzga() {
        laPruebaEstaEntregada(entregado());

        InsumoPrueba insumo = puente.insumoPrueba(POSTULACION);

        assertThat(insumo.puesto()).isEqualTo("Analista de procesos");
        assertThat(insumo.nivelPuesto())
                .as("el mismo entregable es excelente para un operativo y flojo para una dirección")
                .isEqualTo("OPERATIVO");
        assertThat(insumo.queBuscaLaVacante()).isEqualTo(QUE_BUSCA);
        assertThat(insumo.queSePidio()).isEqualTo("Arma el tablero");
        assertThat(insumo.duracionMinutos()).isEqualTo(120);
        assertThat(insumo.seLeAcaboElTiempo()).isFalse();
    }

    @Test
    @DisplayName("una entrega hecha por el reloj viaja marcada como tal")
    void laEntregaAutomaticaSeLeCuentaAlModelo() {
        laPruebaEstaEntregada(IntentoPrueba.builder()
                .id(INTENTO).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION)
                .iniciadoEn(Instant.now()).entregadoEn(Instant.now())
                .esEntregaAutomatica(true)
                .build());

        assertThat(puente.insumoPrueba(POSTULACION).seLeAcaboElTiempo())
                .as("sin esto, una entrega corta porque se acabó el tiempo se lee como un "
                        + "candidato que no dio más")
                .isTrue();
    }

    @Test
    @DisplayName("los criterios se le enseñan en el orden de la rúbrica, y uno sin orden no la descoloca")
    void losCriteriosSalenEnElOrdenDeLaRubrica() {
        // El orden importa porque la rúbrica se lee de arriba abajo: es el mismo recorrido
        // que hará después la persona que confirme, y cuadrar las dos listas a ojo es lo que
        // permite discutir una nota criterio por criterio.
        laPruebaEstaEntregada(entregado());
        when(criterios.findByVersionPlantillaPruebaId(VERSION)).thenReturn(List.of(
                criterioEnOrden(1L, "PR_CIERRE", 3),
                criterioEnOrden(2L, "PR_SIN_ORDEN", null),
                criterioEnOrden(3L, "PR_CALIDAD", 1)));
        when(calificacion.maximosDe(eq(POSTULACION), anyList())).thenReturn(Map.of(
                1L, BigDecimal.valueOf(20), 2L, BigDecimal.valueOf(30),
                3L, BigDecimal.valueOf(50)));

        assertThat(puente.insumoPrueba(POSTULACION).criterios())
                .as("una fila vieja sin número de orden no puede colarse en medio ni tumbar la lista")
                .extracting(CriterioDeRubrica::codigo)
                .containsExactly("PR_SIN_ORDEN", "PR_CALIDAD", "PR_CIERRE");
    }

    // ============ El cambio inesperado ============

    @Test
    @DisplayName("un cambio sorteado que nunca llegó a aparecer en pantalla no se le cuenta al modelo")
    void elCambioQueNoSeMostroNoViaja() {
        laPruebaEstaEntregada(conCambio(4L, null));

        assertThat(puente.insumoPrueba(POSTULACION).cambioInesperado())
                .as("juzgar cómo reaccionó a algo que nunca vio no significa nada")
                .isNull();

        verifyNoInteractions(variantes);
    }

    @Test
    @DisplayName("el cambio que sí le apareció viaja con su texto")
    void elCambioMostradoViajaConSuTexto() {
        laPruebaEstaEntregada(conCambio(4L, Instant.now()));
        when(variantes.findById(4L)).thenReturn(Optional.of(VarianteCambio.builder()
                .id(4L).versionPlantillaPruebaId(VERSION)
                .texto("El cliente adelanta la entrega una semana")
                .build()));

        assertThat(puente.insumoPrueba(POSTULACION).cambioInesperado())
                .isEqualTo("El cliente adelanta la entrega una semana");
    }

    @Test
    @DisplayName("si la variante ya no está, el insumo sale sin cambio en vez de fallar")
    void siLaVarianteDesaparecioElInsumoSaleIgual() {
        laPruebaEstaEntregada(conCambio(4L, Instant.now()));
        when(variantes.findById(4L)).thenReturn(Optional.empty());

        assertThat(puente.insumoPrueba(POSTULACION).cambioInesperado())
                .as("una variante borrada no puede tumbar la calificación de toda una prueba")
                .isNull();
    }

    // ============ Lo que contestó ============

    @Test
    @DisplayName("las respuestas salen en el orden en que el candidato vio las preguntas")
    void lasRespuestasSalenEnElOrdenEnQueLasVio() {
        laPruebaEstaEntregada(entregado());
        when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of(elegida(11L, 1), elegida(12L, 2)));
        when(preguntasCatalogo.findByIdIn(List.of(11L, 12L))).thenReturn(List.of(
                pregunta(12L, "¿Qué dejaste fuera?"), pregunta(11L, "¿Por dónde empezaste?")));
        when(respuestas.findByIntentoPruebaId(INTENTO)).thenReturn(List.of(
                respondio(12L, "Dejé fuera el informe"), respondio(11L, "Por el alcance")));

        List<RespuestaDePrueba> contestadas = puente.insumoPrueba(POSTULACION).respuestas();

        assertThat(contestadas).extracting(RespuestaDePrueba::pregunta)
                .as("el orden lo manda la plantilla, no el orden en que la base devolvió las filas")
                .containsExactly("¿Por dónde empezaste?", "¿Qué dejaste fuera?");
        assertThat(contestadas.get(0).respuesta()).isEqualTo("Por el alcance");
    }

    @Test
    @DisplayName("lo que dejó en blanco no ocupa sitio en lo que se paga por token")
    void loQueNoContestoNoSeManda() {
        laPruebaEstaEntregada(entregado());
        when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of(elegida(11L, 1), elegida(12L, 2), elegida(13L, 3),
                        elegida(14L, 4)));
        when(preguntasCatalogo.findByIdIn(List.of(11L, 12L, 13L, 14L))).thenReturn(List.of(
                pregunta(11L, "Contestada"), pregunta(12L, "En blanco"),
                pregunta(14L, "Ni la tocó")));
        when(respuestas.findByIntentoPruebaId(INTENTO)).thenReturn(List.of(
                respondio(11L, "Algo dije"), respondio(12L, "   ")));

        assertThat(puente.insumoPrueba(POSTULACION).respuestas())
                .as("la 12 llegó en blanco, la 13 ya no está en el catálogo y la 14 ni se abrió: "
                        + "ninguna dice nada que la rúbrica pueda calificar")
                .extracting(RespuestaDePrueba::pregunta)
                .containsExactly("Contestada");
    }

    @Test
    @DisplayName("una versión sin preguntas elegidas no va a buscar respuestas a la base")
    void sinPreguntasElegidasNoSeConsultaNada() {
        laPruebaEstaEntregada(entregado());

        assertThat(puente.insumoPrueba(POSTULACION).respuestas()).isEmpty();

        verifyNoInteractions(preguntasCatalogo);
        verifyNoInteractions(respuestas);
    }

    // ============ Lo que entregó ============

    @Test
    @DisplayName("lo que no entregó se dice, y se dice aparte si además era obligatorio")
    void loQueNoEntregoSeDiceConSuGravedad() {
        laPruebaEstaEntregada(entregado());
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of(pedido(1L, "Tablero", true), pedido(2L, "Video", false)));

        List<EntregaDelCandidato> entregas = puente.insumoPrueba(POSTULACION).entregas();

        assertThat(entregas).extracting(EntregaDelCandidato::loEntrego)
                .containsExactly(false, false);
        assertThat(entregas.get(0).porQueNoSePuedeLeer()).contains("era obligatorio");
        assertThat(entregas.get(1).porQueNoSePuedeLeer())
                .as("faltar algo opcional no pesa lo mismo que faltar lo que se pidió de verdad")
                .doesNotContain("obligatorio");
    }

    @Test
    @DisplayName("de varias subidas del mismo entregable vale la última")
    void deVariasSubidasValeLaUltima() {
        laPruebaEstaEntregada(entregado());
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of(pedido(1L, "Repositorio", true)));
        when(entregables.findByIntentoPruebaId(INTENTO)).thenReturn(List.of(
                enlaceSubido(1L, null, "https://sin-numero.test"),
                enlaceSubido(1L, 2, "https://el-bueno.test"),
                enlaceSubido(1L, 1, "https://el-viejo.test")));

        assertThat(puente.insumoPrueba(POSTULACION).entregas().get(0).enlace())
                .as("pudo entregar tres veces antes de que se acabara el plazo, y una fila vieja "
                        + "sin número de versión no puede ganarle a la última")
                .isEqualTo("https://el-bueno.test");
    }

    @Test
    @DisplayName("un enlace se presenta diciendo que nadie lo ha abierto")
    void unEnlaceSePresentaComoLoQueEs() {
        laPruebaEstaEntregada(entregado());
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of(pedido(1L, "Repositorio", true)));
        when(entregables.findByIntentoPruebaId(INTENTO))
                .thenReturn(List.of(enlaceSubido(1L, 1, "https://repo.test/lo-mio")));

        EntregaDelCandidato entrega = puente.insumoPrueba(POSTULACION).entregas().get(0);

        assertThat(entrega.loEntrego()).isTrue();
        assertThat(entrega.contenido())
                .as("nadie abrió el enlace: enseñárselo al modelo como contenido sería inventarlo")
                .isNull();
        assertThat(entrega.porQueNoSePuedeLeer()).contains("Es un enlace");

        verifyNoInteractions(archivos);
        verifyNoInteractions(almacen);
    }

    @Test
    @DisplayName("un archivo que ya no está guardado no se confunde con uno que no se entregó")
    void unArchivoQueYaNoEstaNoEsUnaEntregaQueFalta() {
        laPruebaEstaEntregada(entregado());
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of(pedido(1L, "Tablero", true), pedido(2L, "Informe", true),
                        pedido(3L, "Anexo", true)));
        when(entregables.findByIntentoPruebaId(INTENTO)).thenReturn(List.of(
                archivoSubido(1L, 40L), archivoSubido(2L, 41L), archivoSubido(3L, 42L)));
        when(archivos.findById(40L)).thenReturn(Optional.of(Archivo.builder()
                .id(40L).nombreOriginal("tablero.pdf").ruta("prueba/tablero.pdf")
                .borradoEn(Instant.now())
                .build()));
        when(archivos.findById(41L)).thenReturn(Optional.empty());
        // La fila que deja la anonimización: se conserva para saber que existió, sin ruta.
        when(archivos.findById(42L)).thenReturn(Optional.of(Archivo.builder()
                .id(42L).nombreOriginal("anexo.pdf")
                .build()));

        List<EntregaDelCandidato> entregas = puente.insumoPrueba(POSTULACION).entregas();

        assertThat(entregas).extracting(EntregaDelCandidato::loEntrego)
                .as("entregó las tres: que después se borraran, se perdieran o se anonimizaran "
                        + "no es culpa suya")
                .containsExactly(true, true, true);
        assertThat(entregas).extracting(EntregaDelCandidato::porQueNoSePuedeLeer)
                .containsOnly("El archivo ya no está guardado");

        verifyNoInteractions(almacen);
    }

    @Test
    @DisplayName("un archivo demasiado grande no se llega a bajar")
    void unArchivoEnormeNiSeBaja() {
        laPruebaEstaEntregada(entregado());
        unSoloEntregableConArchivo("Video", archivo(40L, "demo.mp4", "video/mp4",
                200L * 1024 * 1024));

        EntregaDelCandidato entrega = puente.insumoPrueba(POSTULACION).entregas().get(0);

        assertThat(entrega.porQueNoSePuedeLeer())
                .as("bajar doscientos megas a memoria para descubrir que no tienen texto "
                        + "tumbaría el proceso entero")
                .contains("200 MB")
                .contains("una persona");
        assertThat(entrega.contenido()).isNull();

        verifyNoInteractions(almacen);
        verifyNoInteractions(extractor);
    }

    @Test
    @DisplayName("un entregable ilegible deja ese criterio sin nota, pero no tumba la calificación")
    void unEntregableIlegibleNoTumbaLaCalificacion() {
        laPruebaEstaEntregada(entregado());
        unSoloEntregableConArchivo("Video", archivo(40L, "demo.mov", "video/quicktime", 1024L));
        when(almacen.leer(any(Archivo.class))).thenReturn(new byte[] {1, 2, 3});
        when(extractor.extraer(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("No se puede leer el texto de «demo.mov»"));

        EntregaDelCandidato entrega = puente.insumoPrueba(POSTULACION).entregas().get(0);

        assertThat(entrega.porQueNoSePuedeLeer())
                .as("un video es una entrega válida: si esto lanzara, dejaría sin calificar la "
                        + "prueba entera")
                .startsWith("No se pudo leer");
        assertThat(entrega.contenido()).isNull();
        assertThat(entrega.loEntrego()).isTrue();
    }

    @Test
    @DisplayName("un archivo del que no sale texto se explica, no se presenta como vacío")
    void unArchivoSinTextoSeExplica() {
        laPruebaEstaEntregada(entregado());
        unSoloEntregableConArchivo("Diapositivas",
                archivo(40L, "tablero.pdf", "application/pdf", 1024L));
        when(almacen.leer(any(Archivo.class))).thenReturn(new byte[] {1, 2, 3});
        when(extractor.extraer(any(), anyString(), anyString())).thenReturn("   ");

        EntregaDelCandidato entrega = puente.insumoPrueba(POSTULACION).entregas().get(0);

        assertThat(entrega.porQueNoSePuedeLeer())
                .as("un PDF escaneado no tiene texto; decir que está vacío produciría un cero injusto")
                .isEqualTo("Del archivo no salió nada de texto");
        assertThat(entrega.contenido()).isNull();
    }

    @Test
    @DisplayName("lo que sí se puede leer viaja con su nombre de archivo y su contenido")
    void loQueSeLeeViajaEntero() {
        laPruebaEstaEntregada(entregado());
        // Sin tamaño anotado: pasa con lo que se subió por enlace firmado antes de que
        // alguien confirmara la subida. Un tamaño desconocido no es un archivo enorme.
        unSoloEntregableConArchivo("Tablero",
                archivo(40L, "tablero.pdf", "application/pdf", null));
        when(almacen.leer(any(Archivo.class))).thenReturn(new byte[] {1, 2, 3});
        when(extractor.extraer(any(), anyString(), anyString()))
                .thenReturn("Semana 1: levantamiento");

        EntregaDelCandidato entrega = puente.insumoPrueba(POSTULACION).entregas().get(0);

        assertThat(entrega.contenido()).isEqualTo("Semana 1: levantamiento");
        assertThat(entrega.archivo())
                .as("el nombre del archivo es la única forma que tiene el modelo de citar de "
                        + "cuál de las entregas está hablando")
                .isEqualTo("tablero.pdf");
        assertThat(entrega.porQueNoSePuedeLeer())
                .as("hay contenido: el motivo y el texto nunca viajan los dos")
                .isNull();
    }

    @Test
    @DisplayName("un documento larguísimo se corta, y se avisa de que se cortó")
    void unDocumentoLarguisimoSeCorta() {
        laPruebaEstaEntregada(entregado());
        unSoloEntregableConArchivo("Informe",
                archivo(40L, "informe.pdf", "application/pdf", 1024L));
        when(almacen.leer(any(Archivo.class))).thenReturn(new byte[] {1, 2, 3});
        when(extractor.extraer(any(), anyString(), anyString())).thenReturn("a".repeat(25_000));

        EntregaDelCandidato entrega = puente.insumoPrueba(POSTULACION).entregas().get(0);

        assertThat(entrega.contenido())
                .as("lo que entra al modelo se paga por token: cien páginas cuestan más que la "
                        + "nota que producen")
                .hasSize(20_000 + "\n[...cortado por lo largo]".length())
                .endsWith("[...cortado por lo largo]");
        assertThat(entrega.porQueNoSePuedeLeer())
                .as("cortado no es ilegible: hay texto de sobra para juzgar")
                .isNull();
    }

    // ============ Lo que se guarda: lo que no llega a entrar ============

    @Test
    @DisplayName("no se guardan notas de una prueba que no existe")
    void noSeGuardanNotasDeLoQueNoExiste() {
        when(postulaciones.findById(POSTULACION))
                .thenReturn(Optional.of(postulacion("PRUEBA_CALIFICANDO")));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.guardarNotasPrueba(POSTULACION, 77L,
                new ResultadoPrueba(List.of(), null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(notasCriterio);
        verifyNoInteractions(maquina);
    }

    @Test
    @DisplayName("un criterio que no está en esta rúbrica se ignora")
    void unCriterioQueNoEstaEnLaRubricaNoSeGuarda() {
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "PRUEBA_CALIFICANDO");

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_QUE_NO_EXISTE", BigDecimal.valueOf(10),
                        "Muy bien", null)),
                BigDecimal.valueOf(90)));

        verify(notasCriterio, never()).save(any());
    }

    @Test
    @DisplayName("una nota sin puntaje no se guarda como un cero")
    void unaNotaSinPuntajeNoSeGuarda() {
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "PRUEBA_CALIFICANDO");

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", null, "No pude juzgarlo", null)),
                BigDecimal.valueOf(90)));

        verify(notasCriterio, never()).save(any());
    }

    @Test
    @DisplayName("un criterio sin escala no se guarda aunque el modelo lo puntúe igual")
    void unCriterioSinEscalaNoSeGuarda() {
        // Se le enseña solo lo que tiene escala, pero el modelo puede devolver un código que
        // vio en otro sitio. Sin máximo no hay entre qué y qué, y la nota no sería comparable.
        montarLaRubrica(List.of(criterio(1L, "PR_SIN_PUNTOS", "AGENTE", null)),
                Map.of(), "PRUEBA_CALIFICANDO");

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_SIN_PUNTOS", BigDecimal.valueOf(70),
                        "Bien resuelto", null)),
                BigDecimal.valueOf(90)));

        verify(notasCriterio, never()).save(any());
    }

    @Test
    @DisplayName("un puntaje negativo se sube a cero y la confianza no pasa de cien")
    void loQueVieneFueraDeEscalaSeAcota() {
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "PRUEBA_CALIFICANDO");

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", BigDecimal.valueOf(-5),
                        "No entregó nada que juzgar", "   ")),
                BigDecimal.valueOf(250)));

        ArgumentCaptor<NotaCriterio> guardada = ArgumentCaptor.forClass(NotaCriterio.class);
        verify(notasCriterio).save(guardada.capture());
        assertThat(guardada.getValue().getPuntaje())
                .as("una nota negativa no existe en ninguna rúbrica")
                .isEqualByComparingTo("0");
        assertThat(guardada.getValue().getConfianza()).isEqualByComparingTo("100");
        assertThat(guardada.getValue().getExplicacion())
                .as("una evidencia en blanco no añade una línea suelta a la explicación")
                .isEqualTo("No entregó nada que juzgar");
    }

    @Test
    @DisplayName("una nota sin confianza se guarda igual, con la confianza vacía")
    void sinConfianzaLaNotaSeGuardaIgual() {
        // El modelo puede devolver la lista de notas y omitir la confianza. Poner un cien por
        // defecto haría pasar por segura una nota de la que nadie dijo nada.
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "PRUEBA_CALIFICANDO");

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", BigDecimal.valueOf(12),
                        "Cumple lo justo", null)),
                null));

        ArgumentCaptor<NotaCriterio> guardada = ArgumentCaptor.forClass(NotaCriterio.class);
        verify(notasCriterio).save(guardada.capture());
        assertThat(guardada.getValue().getConfianza()).isNull();
        assertThat(guardada.getValue().getPuntaje()).isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("una rúbrica con el mismo código dos veces no tumba la calificación entera")
    void unCodigoRepetidoEnLaRubricaNoTumbaLaCalificacion() {
        // Pasa si alguien duplica una fila al armar la rúbrica desde el panel. Agrupar por
        // código sin decidir qué hacer con el repetido lanzaría, y una prueba se quedaría sin
        // calificar por un dato mal escrito en otra pantalla.
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20),
                        criterio(2L, "PR_CALIDAD", "AGENTE", 30)),
                Map.of(1L, BigDecimal.valueOf(20), 2L, BigDecimal.valueOf(30)),
                "PRUEBA_CALIFICANDO");

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", BigDecimal.valueOf(18),
                        "Cumple", null)),
                BigDecimal.valueOf(90)));

        ArgumentCaptor<NotaCriterio> guardada = ArgumentCaptor.forClass(NotaCriterio.class);
        verify(notasCriterio).save(guardada.capture());
        assertThat(guardada.getValue().getCriterioId())
                .as("gana la primera de las dos, y se guarda una sola nota en vez de dos")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("sin ninguna nota que guardar, la prueba igual pasa a manos de una persona")
    void sinNingunaNotaLaPruebaNoSeQuedaEscondida() {
        // Pasa de verdad: una entrega que es solo un video no da para puntuar nada por texto.
        // Dejarla en «calificando» la escondería, y hace falta justo lo contrario.
        montarLaRubrica(List.of(criterio(1L, "PR_CALIDAD", "AGENTE", 20)),
                Map.of(1L, BigDecimal.valueOf(20)), "PRUEBA_CALIFICANDO");

        puente.guardarNotasPrueba(POSTULACION, 77L, new ResultadoPrueba(null, null));

        verify(notasCriterio, never()).save(any());
        verify(maquina).transicionar(any(Postulacion.class), eq("PRUEBA_POR_CONFIRMAR"),
                eq(null), eq(null), eq(true), eq(false), eq(null));
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

    /** Un criterio de agente con su sitio en la rúbrica, que puede no tenerlo. */
    private Criterio criterioEnOrden(Long id, String codigo, Integer orden) {
        return Criterio.builder()
                .id(id).codigo(codigo).nombre(codigo).descripcion("Qué mide")
                .etapaCodigo("PRUEBA_PUESTO").versionPlantillaPruebaId(VERSION)
                .puntos(BigDecimal.TEN).metodoVerificacion("AGENTE").orden(orden)
                .build();
    }

    /**
     * Una prueba entregada, sin rúbrica, sin preguntas y sin entregas.
     *
     * <p>Lo que no se estimula devuelve el vacío por defecto de Mockito, así que cada test
     * añade encima solo la pieza de la que habla y todo lo demás queda callado.
     */
    private void laPruebaEstaEntregada(IntentoPrueba intento) {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion(null)));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento));
        when(versiones.findById(VERSION)).thenReturn(Optional.of(version()));
        when(contexto.puestoDe(any(Postulacion.class))).thenReturn(puesto());
        when(contexto.queBuscaLaVacanteDe(any(Postulacion.class))).thenReturn(QUE_BUSCA);
    }

    /** Una prueba entregada a la que le tocó un cambio, visto o no. */
    private IntentoPrueba conCambio(Long varianteId, Instant mostradoEn) {
        return IntentoPrueba.builder()
                .id(INTENTO).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION)
                .iniciadoEn(Instant.now()).entregadoEn(Instant.now())
                .varianteCambioId(varianteId).minutoCambio(40).cambioMostradoEn(mostradoEn)
                .build();
    }

    /** Deja un único entregable pedido, ya subido como archivo. */
    private void unSoloEntregableConArchivo(String nombre, Archivo subido) {
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of(pedido(1L, nombre, true)));
        when(entregables.findByIntentoPruebaId(INTENTO))
                .thenReturn(List.of(archivoSubido(1L, subido.getId())));
        when(archivos.findById(subido.getId())).thenReturn(Optional.of(subido));
    }

    private PreguntaVersionPlantilla elegida(Long preguntaId, int orden) {
        return PreguntaVersionPlantilla.builder()
                .versionPlantillaPruebaId(VERSION).preguntaPruebaId(preguntaId).orden(orden)
                .build();
    }

    private PreguntaPrueba pregunta(Long id, String enunciado) {
        return PreguntaPrueba.builder()
                .id(id).codigo("P" + id).enunciado(enunciado).tipo("UNIVERSAL")
                .revela("Criterio")
                .build();
    }

    private RespuestaPrueba respondio(Long preguntaId, String texto) {
        return RespuestaPrueba.builder()
                .intentoPruebaId(INTENTO).preguntaPruebaId(preguntaId).texto(texto)
                .respondidaEn(Instant.now())
                .build();
    }

    private EntregableRequerido pedido(Long id, String nombre, boolean obligatorio) {
        return EntregableRequerido.builder()
                .id(id).versionPlantillaPruebaId(VERSION).nombre(nombre)
                .detalle("Máximo cinco minutos").formato("CUALQUIERA")
                .esObligatorio(obligatorio).orden(id.intValue())
                .build();
    }

    private Entregable enlaceSubido(Long requeridoId, Integer version, String url) {
        return Entregable.builder()
                .intentoPruebaId(INTENTO).entregableRequeridoId(requeridoId)
                .enlace(url).version(version).subidoEn(Instant.now())
                .build();
    }

    private Entregable archivoSubido(Long requeridoId, Long archivoId) {
        return Entregable.builder()
                .intentoPruebaId(INTENTO).entregableRequeridoId(requeridoId)
                .archivoId(archivoId).version(1).subidoEn(Instant.now())
                .build();
    }

    private Archivo archivo(Long id, String nombreOriginal, String tipo, Long tamano) {
        return Archivo.builder()
                .id(id).organizacionId(1L).ruta("prueba/" + nombreOriginal)
                .nombreOriginal(nombreOriginal).tipo(tipo).tamano(tamano)
                .subidoEn(Instant.now())
                .build();
    }
}
