package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PreguntaElegida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDeOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PuntosDePregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.TextoDe;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.PreguntasInvalidasException;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AgregarDeLaPropuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CambiarPuntosDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CorregirInstruccionesDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PuntosDeCriterio;
import com.renaser.ai.ai_engine.prueba.entity.CriterioBancoEntregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.NotaCriterioPrueba;
import com.renaser.ai.ai_engine.prueba.repository.CriterioBancoEntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.NotaCriterioPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.CriterioDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.PreguntaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;
import com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La prueba del puesto ya publicada (V67): corregir las instrucciones de la IA, reintentar la
 * recalificación, cambiar los puntos y las recomendaciones de la IA, con sus porqués.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("La prueba del editor: lo que se cambia con candidatos dentro y las recomendaciones")
class ServicioPruebaPropiaPublicadaTest {

    private static final long ORGANIZACION = 2L;
    private static final long VACANTE = 40L;
    private static final long BORRADOR = 80L;
    private static final long PUBLICADA = 81L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private Permisos permisos;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private CriterioBancoRepository criteriosBanco;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private EntregableRequeridoRepository entregables;
    @Mock private CriterioBancoEntregableRepository miradas;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private NotaCriterioPruebaRepository notas;
    @Mock private PropuestaPreguntasRepository propuestas;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private CalificacionDeLaPruebaPropia calculo;
    @Mock private CierreDeLaPruebaPropia cierre;
    @Mock private ColaCalificacionIa cola;
    @Mock private ServicioAuditoria auditoria;
    @InjectMocks private ServicioPruebaPropiaImpl servicio;

    private final Vacante vacante = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
            .titulo("Contador").estado("PUBLICADA").build();
    private final VersionBanco borrador = VersionBanco.builder().id(BORRADOR).organizacionId(ORGANIZACION)
            .estado("BORRADOR").versionGuia(1).build();
    private final VersionBanco publicada = VersionBanco.builder().id(PUBLICADA).organizacionId(ORGANIZACION)
            .estado("PUBLICADA").versionGuia(1).build();

    private final IntentoPrueba deAna = intento(100L, 500L);
    private final IntentoPrueba deBeto = intento(101L, 501L);

    @BeforeEach
    void armar() {
        when(alcance.laVacanteVisible(eq(QUIEN), eq(VACANTE), any())).thenReturn(vacante);
        when(versionesBanco.pruebaPropiaDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.pruebaPropiaDe(VACANTE, "PUBLICADA")).thenReturn(Optional.of(publicada));
        when(cola.comoVaElRecomendadorDePrueba(VACANTE)).thenReturn(new ColaCalificacionIa.Seguimiento("SIN_TRABAJO", null));
        when(propuestas.save(any(PropuestaPreguntas.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static IntentoPrueba intento(long id, long postulacion) {
        return IntentoPrueba.builder().id(id).postulacionId(postulacion).versionBancoId(PUBLICADA)
                .entregadoEn(Instant.now()).build();
    }

    private static NotaCriterioPrueba notaDeLaIa(long intento, int guia) {
        return NotaCriterioPrueba.builder().intentoPruebaId(intento).criterioBancoId(5L)
                .puntaje(BigDecimal.TEN).origen("IA").versionGuia(guia).build();
    }

    /** Ana y Beto entregaron; la IA calificó a los dos con la guía que se diga. */
    private void rindieronConNotaDeLaIa(int guiaDeAna, int guiaDeBeto) {
        IntentoPrueba sinCompletar = intento(102L, 502L);
        sinCompletar.setNoCompletada(true);
        when(intentos.findByVersionBancoId(PUBLICADA)).thenReturn(List.of(deAna, deBeto, sinCompletar));
        NotaCriterioPrueba ajustada = notaDeLaIa(101L, 1);
        ajustada.setAjustadaPorUsuarioId(9L);
        NotaCriterioPrueba dePersona = notaDeLaIa(101L, 1);
        dePersona.setOrigen("PERSONA");
        when(notas.findByIntentoPruebaIdIn(anyCollection())).thenReturn(List.of(
                notaDeLaIa(100L, guiaDeAna), notaDeLaIa(101L, guiaDeBeto), ajustada, dePersona));
    }

    // ---------------------------------------------------------------- corregir las instrucciones

    @Test
    @DisplayName("Corregir qué debe tener un entregable sube la guía, se audita y recalifica a quien tiene nota de la IA")
    void corregirUnEntregable() {
        rindieronConNotaDeLaIa(1, 1);
        EntregableRequerido tablero = EntregableRequerido.builder().id(30L).versionBancoId(PUBLICADA)
                .nombre("Tablero").formato("ARCHIVO").build();
        when(entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(PUBLICADA)).thenReturn(List.of(tablero));
        when(cola.recalificarPrueba(anyLong())).thenReturn(true);

        CambioAplicado cambio = servicio.corregirInstrucciones(QUIEN, VACANTE, new CorregirInstruccionesDePrueba(
                null, null, null, List.of(new TextoDe(30L, " Que cuadre la caja "))));

        assertThat(cambio.personas()).isEqualTo(2);
        assertThat(tablero.getQueDebeTener()).isEqualTo("Que cuadre la caja");
        assertThat(publicada.getVersionGuia()).isEqualTo(2);
        verify(entregables).save(tablero);
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("corregir_instrucciones_ia_prueba"),
                eq("version_banco"), eq(PUBLICADA), any(), any(), any());
        verify(cola).recalificarPrueba(500L);
        verify(cola).recalificarPrueba(501L);
        verify(cola, never()).recalificar(anyLong());
    }

    @Test
    @DisplayName("Un entregable, un criterio o una abierta que no son de la publicada son 400")
    void corregirLoAjeno() {
        when(entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(PUBLICADA)).thenReturn(List.of());
        Pregunta cerrada = Pregunta.builder().id(1L).versionBancoId(PUBLICADA).tipo("OPCION_UNICA").build();
        when(preguntas.findByVersionBancoIdOrderByOrden(PUBLICADA)).thenReturn(List.of(cerrada));

        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE, new CorregirInstruccionesDePrueba(
                null, null, null, List.of(new TextoDe(99L, "x")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El entregable 99 no es de la prueba publicada de esta vacante");
        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE, new CorregirInstruccionesDePrueba(
                null, List.of(new TextoDe(77L, "x")), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El criterio 77 no es de la prueba publicada de esta vacante");
        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE, new CorregirInstruccionesDePrueba(
                null, null, List.of(new TextoDe(1L, "x")), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La pregunta 1 no es una abierta de la prueba publicada de esta vacante");
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sin saldo de IA y con gente calificada por ella, no se guarda nada: 409 con el porqué")
    void corregirSinSaldo() {
        rindieronConNotaDeLaIa(1, 1);
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn("La IA está apagada.");

        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE, new CorregirInstruccionesDePrueba(
                "Nueva guía", null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("La IA está apagada. No se guardó el cambio");
        assertThat(publicada.getGuiaCalificacion()).isNull();
        assertThat(publicada.getVersionGuia()).isEqualTo(1);
    }

    @Test
    @DisplayName("Con una recalificación en curso no se corrige la guía")
    void corregirConRecalificacionEnCurso() {
        rindieronConNotaDeLaIa(1, 1);
        when(cola.recalificacionDePrueba(anyList())).thenReturn(Map.of(500L,
                new ColaCalificacionIa.Seguimiento("EN_CURSO", null)));

        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE, new CorregirInstruccionesDePrueba(
                "Nueva guía", null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Hay una recalificación de la IA en curso");
    }

    @Test
    @DisplayName("Si nada cambia, no sube la guía ni se recalifica a nadie")
    void corregirSinCambios() {
        publicada.setGuiaCalificacion("Mide el cierre");

        CambioAplicado cambio = servicio.corregirInstrucciones(QUIEN, VACANTE, new CorregirInstruccionesDePrueba(
                " Mide el cierre ", null, null, null));

        assertThat(cambio.personas()).isZero();
        assertThat(publicada.getVersionGuia()).isEqualTo(1);
        verify(versionesBanco, never()).save(any());
    }

    // ---------------------------------------------------------------- reintentar

    @Test
    @DisplayName("Reintentar con la IA apagada no encola a nadie y dice cuántos siguen con la guía anterior")
    void reintentarConLaIaApagada() {
        publicada.setVersionGuia(2);
        rindieronConNotaDeLaIa(1, 2);
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn("La IA está apagada.");

        CambioAplicado cambio = servicio.reintentarRecalificacion(QUIEN, VACANTE);

        assertThat(cambio).isEqualTo(new CambioAplicado(0, "La IA está apagada. No se volvió a pedir la "
                + "recalificación: 1 persona sigue con la nota de la guía anterior."));
        verify(cola, never()).recalificarPrueba(anyLong());
    }

    @Test
    @DisplayName("Reintentar encola solo a quien quedó atrás y no está en curso; dice a quién no se pudo")
    void reintentarSoloAQuienQuedoAtras() {
        publicada.setVersionGuia(3);
        rindieronConNotaDeLaIa(2, 2);
        when(cola.recalificarPrueba(500L)).thenReturn(true);
        when(cola.recalificarPrueba(501L)).thenReturn(false);

        CambioAplicado cambio = servicio.reintentarRecalificacion(QUIEN, VACANTE);

        assertThat(cambio).isEqualTo(new CambioAplicado(1,
                "1 persona ya tenía una recalificación en marcha: no se pidió otra."));
    }

    @Test
    @DisplayName("Quien ya se está recalificando no se vuelve a pedir; si no queda nadie, 0 personas")
    void reintentarSinPendientes() {
        publicada.setVersionGuia(2);
        rindieronConNotaDeLaIa(1, 2);
        when(cola.recalificacionDePrueba(anyList())).thenReturn(Map.of(500L,
                new ColaCalificacionIa.Seguimiento("EN_CURSO", null)));

        assertThat(servicio.reintentarRecalificacion(QUIEN, VACANTE)).isEqualTo(new CambioAplicado(0));
        verify(cola, never()).recalificarPrueba(anyLong());
    }

    @Test
    @DisplayName("El editor cuenta quién está al día, quién se recalifica y quién quedó atrás y por qué")
    void elEditorCuentaLaRecalificacion() {
        publicada.setVersionGuia(2);
        IntentoPrueba deCarla = intento(103L, 503L);
        when(intentos.findByVersionBancoId(PUBLICADA)).thenReturn(List.of(deAna, deBeto, deCarla));
        when(notas.findByIntentoPruebaIdIn(anyCollection())).thenReturn(List.of(
                notaDeLaIa(100L, 2), notaDeLaIa(101L, 1), notaDeLaIa(103L, 1)));
        when(cola.recalificacionDePrueba(anyList())).thenReturn(Map.of(
                501L, new ColaCalificacionIa.Seguimiento("EN_CURSO", null),
                503L, new ColaCalificacionIa.Seguimiento("DETENIDA", "Se acabó el saldo.")));

        EditorDePreguntas editor = servicio.ver(QUIEN, VACANTE);

        assertThat(editor.recalificacion().rindieron()).isEqualTo(3);
        assertThat(editor.recalificacion().conNota()).isEqualTo(3);
        assertThat(editor.recalificacion().alDia()).isEqualTo(1);
        assertThat(editor.recalificacion().recalificando()).isEqualTo(1);
        assertThat(editor.recalificacion().pendientes()).isEqualTo(1);
        assertThat(editor.recalificacion().motivos()).containsExactly("Se acabó el saldo.");
    }

    // ---------------------------------------------------------------- cambiar los puntos

    private final Pregunta unica = Pregunta.builder().id(1L).versionBancoId(PUBLICADA).criterioBancoId(5L)
            .tipo("OPCION_UNICA").enunciado("¿Qué libro?").puntos(60).build();
    private final Pregunta abierta = Pregunta.builder().id(2L).versionBancoId(PUBLICADA).criterioBancoId(5L)
            .tipo("ABIERTA").enunciado("¿Cómo cuadras?").puntos(0).build();
    private final Opcion diario = Opcion.builder().id(11L).preguntaId(1L).texto("Diario")
            .puntaje(BigDecimal.valueOf(60)).build();
    private final Opcion mayor = Opcion.builder().id(12L).preguntaId(1L).texto("Mayor")
            .puntaje(BigDecimal.ZERO).build();
    private final CriterioBanco contable = CriterioBanco.builder().id(5L).versionBancoId(PUBLICADA)
            .nombre("Contable").puntosCalificados(40).calificador("IA").build();

    /** Un criterio con una cerrada de 60 y una abierta, y 40 de parte calificada por la IA. */
    private void laPublicadaSuma100() {
        PreguntaDeLaPrueba deUnica = new PreguntaDeLaPrueba(unica, List.of(diario, mayor), null, 60, null, false);
        PreguntaDeLaPrueba deAbierta = new PreguntaDeLaPrueba(abierta, List.of(), null, 0, null, false);
        CriterioDeLaPrueba criterio = new CriterioDeLaPrueba(contable, List.of(deUnica, deAbierta), List.of(),
                60, BigDecimal.ZERO, 40, "IA", null);
        when(calculo.estructura(PUBLICADA)).thenReturn(new Resultado(publicada, List.of(criterio), List.of(), List.of()));
    }

    @Test
    @DisplayName("Una pregunta, una opción o un criterio que no son de la publicada son 400")
    void cambiarPuntosDeLoAjeno() {
        laPublicadaSuma100();

        assertThatThrownBy(() -> servicio.cambiarPuntos(QUIEN, VACANTE, new CambiarPuntosDePrueba(
                List.of(new PuntosDePregunta(99L, BigDecimal.TEN, null)), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La pregunta 99 no es de la prueba publicada de esta vacante");
        assertThatThrownBy(() -> servicio.cambiarPuntos(QUIEN, VACANTE, new CambiarPuntosDePrueba(
                List.of(new PuntosDePregunta(1L, BigDecimal.TEN, List.of(new PuntosDeOpcion(99L, BigDecimal.ONE)))),
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La opción 99 no es de la pregunta 1");
        assertThatThrownBy(() -> servicio.cambiarPuntos(QUIEN, VACANTE, new CambiarPuntosDePrueba(
                null, List.of(new PuntosDeCriterio(77L, BigDecimal.TEN)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El criterio 77 no es de la prueba publicada de esta vacante");
    }

    @Test
    @DisplayName("Puntos a una abierta y la parte calificada en 0 no pasan: la lista entera y nada se toca")
    void cambiarPuntosConFaltas() {
        laPublicadaSuma100();

        assertThatThrownBy(() -> servicio.cambiarPuntos(QUIEN, VACANTE, new CambiarPuntosDePrueba(
                List.of(new PuntosDePregunta(2L, BigDecimal.valueOf(5), null)),
                List.of(new PuntosDeCriterio(5L, BigDecimal.ZERO)))))
                .isInstanceOf(PreguntasInvalidasException.class)
                .hasMessage("Los puntos no se cambiaron: faltan 3 cosas")
                .satisfies(e -> assertThat(((PreguntasInvalidasException) e).getFaltas()).containsExactly(
                        "Los puntos suman 60 de 100: faltan 40.",
                        "La pregunta 2 («¿Cómo cuadras?»): en la prueba, una abierta no lleva puntos.",
                        "El criterio «Contable»: su parte calificada no puede quedar en 0, porque tiene "
                                + "abiertas o entregables que alguien califica."));
        verify(preguntas, never()).save(any());
        verify(criteriosBanco, never()).save(any());
    }

    @Test
    @DisplayName("Dar parte calificada a un criterio que no la tiene es cambiar la prueba, y no se puede")
    void cambiarPuntosDaParteCalificada() {
        CriterioBanco sinParte = CriterioBanco.builder().id(6L).versionBancoId(PUBLICADA).nombre("Excel")
                .puntosCalificados(0).build();
        PreguntaDeLaPrueba deUnica = new PreguntaDeLaPrueba(unica, List.of(diario, mayor), null, 100, null, false);
        unica.setPuntos(100);
        diario.setPuntaje(BigDecimal.valueOf(100));
        when(calculo.estructura(PUBLICADA)).thenReturn(new Resultado(publicada, List.of(
                new CriterioDeLaPrueba(sinParte, List.of(deUnica), List.of(), 100, BigDecimal.ZERO, 0, "IA", null)),
                List.of(), List.of()));

        assertThatThrownBy(() -> servicio.cambiarPuntos(QUIEN, VACANTE, new CambiarPuntosDePrueba(
                List.of(new PuntosDePregunta(1L, BigDecimal.valueOf(90),
                        List.of(new PuntosDeOpcion(11L, BigDecimal.valueOf(90))))),
                List.of(new PuntosDeCriterio(6L, BigDecimal.TEN)))))
                .isInstanceOf(PreguntasInvalidasException.class)
                .satisfies(e -> assertThat(((PreguntasInvalidasException) e).getFaltas()).anyMatch(f ->
                        f.startsWith("El criterio «Excel» no tiene parte calificada")));
    }

    @Test
    @DisplayName("Cambiar los puntos escala la parte calificada ya puesta y recalcula a quien entregó, sin IA")
    void cambiarPuntosEscalaYRecalcula() {
        laPublicadaSuma100();
        when(intentos.findByVersionBancoId(PUBLICADA)).thenReturn(List.of(deAna));
        NotaCriterioPrueba puesta = NotaCriterioPrueba.builder().intentoPruebaId(100L).criterioBancoId(5L)
                .puntaje(BigDecimal.valueOf(20)).puntajeIa(BigDecimal.valueOf(30)).origen("IA").build();
        when(notas.findByCriterioBancoIdIn(anyCollection())).thenReturn(List.of(puesta));

        CambioAplicado cambio = servicio.cambiarPuntos(QUIEN, VACANTE, new CambiarPuntosDePrueba(
                List.of(new PuntosDePregunta(1L, BigDecimal.valueOf(50),
                        List.of(new PuntosDeOpcion(11L, BigDecimal.valueOf(50))))),
                List.of(new PuntosDeCriterio(5L, BigDecimal.valueOf(50)))));

        assertThat(cambio.personas()).isEqualTo(1);
        assertThat(unica.getPuntos()).isEqualTo(50);
        assertThat(diario.getPuntaje()).isEqualByComparingTo("50");
        assertThat(contable.getPuntosCalificados()).isEqualTo(50);
        assertThat(puesta.getPuntaje()).isEqualByComparingTo("25.00");
        assertThat(puesta.getPuntajeIa()).isEqualByComparingTo("37.50");
        verify(cierre).recalcular(deAna, false);
        verify(cola, never()).recalificarPrueba(anyLong());
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("cambiar_puntos_prueba_propia"),
                eq("version_banco"), eq(PUBLICADA), any(), any(), any());
    }

    @Test
    @DisplayName("Con los mismos puntos no cambia nada ni se recalcula a nadie")
    void cambiarPuntosSinCambios() {
        laPublicadaSuma100();

        CambioAplicado cambio = servicio.cambiarPuntos(QUIEN, VACANTE, new CambiarPuntosDePrueba(
                List.of(new PuntosDePregunta(1L, BigDecimal.valueOf(60), null)),
                List.of(new PuntosDeCriterio(5L, BigDecimal.valueOf(40)))));

        assertThat(cambio.personas()).isZero();
        verify(cierre, never()).recalcular(any(), anyBoolean());
    }

    @Test
    @DisplayName("Sin la prueba publicada no hay puntos que cambiar")
    void cambiarPuntosSinPublicada() {
        when(versionesBanco.pruebaPropiaDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarPuntos(QUIEN, VACANTE, new CambiarPuntosDePrueba(null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Esta vacante todavía no tiene la prueba publicada");
    }

    // ---------------------------------------------------------------- pedir recomendaciones

    @Test
    @DisplayName("Sin borrador y con alguien rindiendo la publicada, pedir recomendaciones es 409")
    void pedirConLaVaraQuieta() {
        when(intentos.algunoEmpezadoDeLaVacante(VACANTE)).thenReturn(true);

        assertThatThrownBy(() -> servicio.pedirRecomendaciones(QUIEN, VACANTE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Alguien ya empezó a rendir esta prueba");
    }

    @Test
    @DisplayName("Con 100 puntos no queda sitio para la IA")
    void pedirSinSitio() {
        CriterioDeLaPrueba lleno = new CriterioDeLaPrueba(contable, List.of(), List.of(), 0, BigDecimal.ZERO,
                100, "IA", null);
        when(calculo.estructura(PUBLICADA)).thenReturn(new Resultado(publicada, List.of(lleno), List.of(), List.of()));

        assertThatThrownBy(() -> servicio.pedirRecomendaciones(QUIEN, VACANTE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("La prueba ya suma 100 puntos");
    }

    @Test
    @DisplayName("Con la IA apagada o una recomendación en marcha no se pide otra, y se dice por qué")
    void pedirSinPoder() {
        when(versionesBanco.pruebaPropiaDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn("La IA está apagada.");

        assertThat(servicio.pedirRecomendaciones(QUIEN, VACANTE, null)).isEqualTo(
                new RecomendacionPedida(false, "La IA está apagada. No se pidieron recomendaciones."));

        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn(null);
        when(cola.comoVaElRecomendadorDePrueba(VACANTE)).thenReturn(new ColaCalificacionIa.Seguimiento("EN_CURSO", null));
        assertThat(servicio.pedirRecomendaciones(QUIEN, VACANTE, null)).isEqualTo(
                new RecomendacionPedida(false, "Ya hay una recomendación de la prueba en marcha: espera a que termine."));
        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("Si la cola no la acepta, la propuesta pedida queda FALLIDA con su porqué")
    void pedirYLaColaNoLaAcepta() {
        when(versionesBanco.pruebaPropiaDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());

        RecomendacionPedida pedida = servicio.pedirRecomendaciones(QUIEN, VACANTE, new PedirRecomendaciones("  "));

        assertThat(pedida.encolada()).isFalse();
        ArgumentCaptor<PropuestaPreguntas> guardada = ArgumentCaptor.forClass(PropuestaPreguntas.class);
        verify(propuestas, times(2)).save(guardada.capture());
        assertThat(guardada.getValue().getEstado()).isEqualTo("FALLIDA");
        assertThat(guardada.getValue().getMotivoFallo()).isEqualTo(pedida.mensaje());
        assertThat(guardada.getValue().getTerminadaEn()).isNotNull();
    }

    @Test
    @DisplayName("Sobre un borrador a medias, la IA completa lo que falta y la propuesta es de la prueba")
    void pedirSobreElBorrador() {
        when(versionesBanco.pruebaPropiaDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
        CriterioDeLaPrueba aMedias = new CriterioDeLaPrueba(contable, List.of(), List.of(), 0, BigDecimal.ZERO,
                30, "IA", null);
        when(calculo.estructura(BORRADOR)).thenReturn(new Resultado(borrador, List.of(aMedias), List.of(), List.of()));
        when(cola.encolarRecomendadorDePrueba(ORGANIZACION, VACANTE)).thenReturn(true);

        RecomendacionPedida pedida = servicio.pedirRecomendaciones(QUIEN, VACANTE,
                new PedirRecomendaciones(" Que mida Excel "));

        assertThat(pedida).isEqualTo(new RecomendacionPedida(true,
                "La IA completará los 70 puntos que faltan. Tarda unos segundos."));
        ArgumentCaptor<PropuestaPreguntas> guardada = ArgumentCaptor.forClass(PropuestaPreguntas.class);
        verify(propuestas).save(guardada.capture());
        assertThat(guardada.getValue().getProposito()).isEqualTo("PRUEBA_PUESTO");
        assertThat(guardada.getValue().getIndicacion()).isEqualTo("Que mida Excel");
        assertThat(guardada.getValue().getPuntosQueFaltan()).isEqualTo(70);
    }

    // ---------------------------------------------------------------- cómo va la recomendación

    private PropuestaPreguntas ultima(String estado, String contenido) {
        PropuestaPreguntas p = PropuestaPreguntas.builder().id(60L).vacanteId(VACANTE).estado(estado)
                .contenido(contenido).puntosQueFaltan(70).indicacion("Excel").motivoFallo("Se cortó.")
                .proposito("PRUEBA_PUESTO").build();
        when(propuestas.findFirstByVacanteIdAndPropositoOrderByIdDesc(VACANTE, "PRUEBA_PUESTO"))
                .thenReturn(Optional.of(p));
        when(propuestas.findByIdAndVacanteIdAndProposito(60L, VACANTE, "PRUEBA_PUESTO")).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    @DisplayName("Sin pedir, lista con el caso y los entregables, o fallida con su porqué")
    void comoVaLaRecomendacion() {
        assertThat(servicio.comoVaLaRecomendacion(QUIEN, VACANTE).estado()).isEqualTo("SIN_PEDIR");

        ultima("LISTA", """
                {"caso":{"enunciado":"Cuadra la caja"},
                 "entregables":[{"nombre":"Tablero","formato":"ARCHIVO"}],
                 "criterios":[{"nombre":"Excel","preguntas":[]}]}""");
        EstadoDeLaRecomendacion lista = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);
        assertThat(lista.estado()).isEqualTo("LISTA");
        assertThat(lista.caso().enunciado()).isEqualTo("Cuadra la caja");
        assertThat(lista.entregables()).hasSize(1);
        assertThat(lista.propuesta()).hasSize(1);

        ultima("LISTA", " ");
        assertThat(servicio.comoVaLaRecomendacion(QUIEN, VACANTE).propuesta()).isEmpty();

        ultima("FALLIDA", null);
        EstadoDeLaRecomendacion fallida = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);
        assertThat(fallida.estado()).isEqualTo("FALLIDA");
        assertThat(fallida.motivo()).isEqualTo("Se cortó.");
    }

    @Test
    @DisplayName("Pedida: en curso mientras la cola trabaja; si terminó sin propuesta, fallida con el porqué de la cola o uno genérico")
    void comoVaUnaPedida() {
        ultima("PEDIDA", null);
        when(cola.comoVaElRecomendadorDePrueba(VACANTE)).thenReturn(new ColaCalificacionIa.Seguimiento("EN_CURSO", null));
        EstadoDeLaRecomendacion enCurso = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);
        assertThat(enCurso.estado()).isEqualTo("EN_CURSO");
        assertThat(enCurso.motivo()).isNull();

        when(cola.comoVaElRecomendadorDePrueba(VACANTE)).thenReturn(new ColaCalificacionIa.Seguimiento("DETENIDA", "Sin saldo."));
        assertThat(servicio.comoVaLaRecomendacion(QUIEN, VACANTE).motivo()).isEqualTo("Sin saldo.");

        when(cola.comoVaElRecomendadorDePrueba(VACANTE)).thenReturn(new ColaCalificacionIa.Seguimiento("SIN_TRABAJO", null));
        EstadoDeLaRecomendacion terminada = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);
        assertThat(terminada.estado()).isEqualTo("FALLIDA");
        assertThat(terminada.motivo()).isEqualTo("La IA terminó sin dejar ninguna propuesta.");
    }

    // ---------------------------------------------------------------- agregar de la propuesta

    private static final String PROPUESTA = """
            {"caso":{"enunciado":"Caso de la IA","materiales":"Datos de caja","herramientasPermitidas":"Excel"},
             "entregables":[{"nombre":"Tablero","detalle":"La hoja","formato":"ARCHIVO","queDebeTener":"Cuadre"}],
             "criterios":[
               {"nombre":"Excel","queEvalua":"Fórmulas","parteCalificada":20.4,"calificador":"IA",
                "entregables":[0],"entregablesExistentes":[31,77],
                "preguntas":[{"tipo":"OPCION_UNICA","enunciado":"¿Qué función busca?","puntos":10,
                              "opciones":[{"texto":"BUSCARV","puntos":10},{"texto":"SUMA","puntos":0}]}]},
               {"criterioExistenteId":5,"nombre":"ignorado","preguntas":[
                 {"tipo":"ABIERTA","enunciado":"¿Cómo cuadras?","queDebeTener":"Que cuadre"}]},
               {"nombre":"Mala","preguntas":[{"tipo":"OPCION_UNICA","enunciado":"¿Una sola?","puntos":5,
                 "opciones":[{"texto":"Sí","puntos":5}]}]}]}""";

    private void enElBorrador() {
        when(versionesBanco.pruebaPropiaDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
        when(entregables.save(any(EntregableRequerido.class))).thenAnswer(inv -> {
            EntregableRequerido e = inv.getArgument(0);
            e.setId(40L);
            return e;
        });
        when(criteriosBanco.save(any(CriterioBanco.class))).thenAnswer(inv -> {
            CriterioBanco c = inv.getArgument(0);
            c.setId(900L);
            return c;
        });
        when(preguntas.save(any(Pregunta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(List.of(
                EntregableRequerido.builder().id(31L).versionBancoId(BORRADOR).nombre("Video").orden(1).build()));
    }

    @Test
    @DisplayName("Una propuesta ajena es 404 y una que no está lista, 409")
    void agregarDeUnaPropuestaQueNoSirve() {
        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L,
                new AgregarDeLaPropuestaDePrueba(true, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        ultima("PEDIDA", null);
        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L,
                new AgregarDeLaPropuestaDePrueba(true, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Esa propuesta todavía no está lista");
    }

    @Test
    @DisplayName("Sin elegir nada, o eligiendo lo que la propuesta no tiene, es 400 y no se agrega nada")
    void agregarLoQueNoHay() {
        enElBorrador();
        ultima("LISTA", PROPUESTA);

        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Elige qué agregar de la propuesta");
        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L,
                new AgregarDeLaPropuestaDePrueba(false, List.of(4), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La propuesta no tiene ese entregable");
        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L,
                new AgregarDeLaPropuestaDePrueba(false, null, List.of(9), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La propuesta no tiene ese criterio");
        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L,
                new AgregarDeLaPropuestaDePrueba(false, null, null, List.of(new PreguntaElegida(0, 3)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La propuesta no tiene esa pregunta");
        verify(preguntas, never()).save(any());
    }

    @Test
    @DisplayName("Si el criterio al que iban las preguntas ya no está, 409; una pregunta propuesta mal formada, 400")
    void agregarAUnCriterioQueYaNoEsta() {
        enElBorrador();
        ultima("LISTA", PROPUESTA);

        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L,
                new AgregarDeLaPropuestaDePrueba(false, null, null, List.of(new PreguntaElegida(1, 0)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("El criterio al que iban esas preguntas ya no está");
        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L,
                new AgregarDeLaPropuestaDePrueba(false, null, List.of(2), null)))
                .isInstanceOf(PreguntasInvalidasException.class)
                .hasMessage("La propuesta no se puede agregar así");
    }

    @Test
    @DisplayName("Un criterio entero trae su parte calificada, sus entregables y sus preguntas; el caso solo llena lo vacío")
    void agregarUnCriterioEntero() {
        enElBorrador();
        borrador.setEnunciado("Mi caso");
        ultima("LISTA", PROPUESTA);

        servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L, new AgregarDeLaPropuestaDePrueba(true, null, List.of(0), null));

        assertThat(borrador.getEnunciado()).isEqualTo("Mi caso");
        assertThat(borrador.getMateriales()).isEqualTo("Datos de caja");
        assertThat(borrador.getHerramientasPermitidas()).isEqualTo("Excel");
        ArgumentCaptor<EntregableRequerido> entregable = ArgumentCaptor.forClass(EntregableRequerido.class);
        verify(entregables).save(entregable.capture());
        assertThat(entregable.getValue().getNombre()).isEqualTo("Tablero");
        assertThat(entregable.getValue().isEsObligatorio()).isTrue();
        assertThat(entregable.getValue().getOrden()).isEqualTo(2);
        ArgumentCaptor<CriterioBanco> criterio = ArgumentCaptor.forClass(CriterioBanco.class);
        verify(criteriosBanco).save(criterio.capture());
        assertThat(criterio.getValue().getPuntosCalificados()).isEqualTo(20);
        assertThat(criterio.getValue().getCalificador()).isEqualTo("IA");
        ArgumentCaptor<CriterioBancoEntregable> mira = ArgumentCaptor.forClass(CriterioBancoEntregable.class);
        verify(miradas, times(2)).save(mira.capture());
        assertThat(mira.getAllValues()).extracting(CriterioBancoEntregable::getEntregableRequeridoId)
                .containsExactly(40L, 31L);
        ArgumentCaptor<Pregunta> pregunta = ArgumentCaptor.forClass(Pregunta.class);
        verify(preguntas).save(pregunta.capture());
        assertThat(pregunta.getValue().getPuntos()).isEqualTo(10);
        assertThat(pregunta.getValue().getCriterioBancoId()).isEqualTo(900L);
    }

    @Test
    @DisplayName("Una pregunta suelta va al criterio que ya está en el borrador, sin crear otro")
    void agregarUnaPreguntaSuelta() {
        enElBorrador();
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(new ArrayList<>(List.of(contable)));
        ultima("LISTA", PROPUESTA);

        servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 60L,
                new AgregarDeLaPropuestaDePrueba(false, null, null, List.of(new PreguntaElegida(1, 0))));

        verify(criteriosBanco, never()).save(any());
        ArgumentCaptor<Pregunta> pregunta = ArgumentCaptor.forClass(Pregunta.class);
        verify(preguntas).save(pregunta.capture());
        assertThat(pregunta.getValue().getCriterioBancoId()).isEqualTo(5L);
        assertThat(pregunta.getValue().getPuntos()).isZero();
        assertThat(pregunta.getValue().getQueDebeTener()).isEqualTo("Que cuadre");
    }
}
