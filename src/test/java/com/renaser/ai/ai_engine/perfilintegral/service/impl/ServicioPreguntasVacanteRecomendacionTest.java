package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa.Seguimiento;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CorregirInstrucciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.TextoDe;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos.PreguntaCalculada;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pedir recomendaciones a la IA, seguir cómo van, y corregir las instrucciones de la IA
 * sobre la versión publicada (V66): cada porqué de «no» y lo que se guarda cuando sí.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El editor de preguntas propias: recomendaciones e instrucciones de la IA")
class ServicioPreguntasVacanteRecomendacionTest {

    private static final long ORGANIZACION = 2L;
    private static final long VACANTE = 40L;
    private static final long BORRADOR = 80L;
    private static final long PUBLICADA = 70L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private Permisos permisos;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private PostulacionRepository postulaciones;
    @Mock private EvaluacionRepository evaluaciones;
    @Mock private RespuestaRepository respuestas;
    @Mock private NotaRespuestaRepository notasRespuesta;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private CriterioBancoRepository criteriosBanco;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private PropuestaPreguntasRepository propuestas;
    @Mock private CalificacionPorPuntos porPuntos;
    @Mock private PuenteCalificacionIa puente;
    @Mock private ColaCalificacionIa cola;
    @Mock private ServicioAuditoria auditoria;
    @InjectMocks private ServicioPreguntasVacanteImpl servicio;

    private final Vacante vacante = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
            .titulo("Cajero").estado("PUBLICADA").origenPreguntas("VACANTE").aplicaEvaluacion(true)
            .build();
    private final VersionBanco borrador = VersionBanco.builder().id(BORRADOR).estado("BORRADOR").build();
    private final VersionBanco publicada = VersionBanco.builder().id(PUBLICADA).estado("PUBLICADA")
            .guiaCalificacion("Guía").build();

    @BeforeEach
    void armar() {
        lenient().when(alcance.laVacanteVisible(eq(QUIEN), eq(VACANTE), any())).thenReturn(vacante);
    }

    /** Una versión cuyas preguntas suman {@code puntos}. */
    private void sumaDe(long version, int puntos) {
        PreguntaCalculada una = new PreguntaCalculada(Pregunta.builder().tipo("ABIERTA").build(),
                List.of(), null, null, puntos, null, false);
        when(porPuntos.calcular(version, null)).thenReturn(new CalificacionPorPuntos.Resultado(
                null, List.of(), List.of(una), BigDecimal.ZERO, false));
    }

    private PropuestaPreguntas ultima(String estado) {
        PropuestaPreguntas p = PropuestaPreguntas.builder().id(9L).vacanteId(VACANTE).estado(estado)
                .puntosQueFaltan(30).indicacion("Atención").motivoFallo("Se cortó").build();
        when(propuestas.findFirstByVacanteIdOrderByIdDesc(VACANTE)).thenReturn(Optional.of(p));
        return p;
    }

    // ---------------------------------------------------------------- pedirRecomendaciones

    @Test
    @DisplayName("Con el borrador ya en 100 no queda sitio para recomendar")
    void borradorLleno() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
        sumaDe(BORRADOR, 100);
        PedirRecomendaciones datos = new PedirRecomendaciones(null);

        assertThatThrownBy(() -> servicio.pedirRecomendaciones(QUIEN, VACANTE, datos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya suma 100 puntos");
        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("Sin borrador y con candidatos sobre la publicada, no se recomienda: la vara no se mueve")
    void publicadaConPostulantes() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.of(publicada));
        when(postulaciones.countByVacanteId(VACANTE)).thenReturn(1L);
        PedirRecomendaciones datos = new PedirRecomendaciones("x");

        assertThatThrownBy(() -> servicio.pedirRecomendaciones(QUIEN, VACANTE, datos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("misma vara");
    }

    @Test
    @DisplayName("Si ya hay una recomendación en marcha, no se pide otra")
    void yaHayUnaEnMarcha() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
        sumaDe(BORRADOR, 40);
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn(null);
        when(cola.comoVaElRecomendador(VACANTE)).thenReturn(new Seguimiento("EN_CURSO", null));

        RecomendacionPedida pedida = servicio.pedirRecomendaciones(QUIEN, VACANTE, new PedirRecomendaciones(null));

        assertThat(pedida.encolada()).isFalse();
        assertThat(pedida.mensaje()).contains("Ya hay una recomendación en marcha");
        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("Sin saldo de IA no se pide y se dice por qué")
    void sinSaldo() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
        sumaDe(BORRADOR, 40);
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn("La IA no tiene saldo.");

        RecomendacionPedida pedida = servicio.pedirRecomendaciones(QUIEN, VACANTE, new PedirRecomendaciones(null));

        assertThat(pedida.encolada()).isFalse();
        assertThat(pedida.mensaje()).isEqualTo("La IA no tiene saldo. No se pidieron recomendaciones.");
    }

    @Test
    @DisplayName("Pedida con cupo: se guarda PEDIDA con los puntos que faltan y se encola")
    void pedidaYEncolada() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
        sumaDe(BORRADOR, 70);
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn(null);
        when(cola.comoVaElRecomendador(VACANTE)).thenReturn(new Seguimiento("SIN_PEDIR", null));
        when(propuestas.save(any(PropuestaPreguntas.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cola.encolarRecomendador(ORGANIZACION, VACANTE)).thenReturn(true);

        RecomendacionPedida pedida = servicio.pedirRecomendaciones(QUIEN, VACANTE,
                new PedirRecomendaciones("  Más atención al cliente  "));

        assertThat(pedida.encolada()).isTrue();
        assertThat(pedida.mensaje()).contains("los 30 puntos que faltan");
        ArgumentCaptor<PropuestaPreguntas> guardada = ArgumentCaptor.forClass(PropuestaPreguntas.class);
        verify(propuestas).save(guardada.capture());
        assertThat(guardada.getValue().getEstado()).isEqualTo(PropuestaPreguntas.PEDIDA);
        assertThat(guardada.getValue().getPuntosQueFaltan()).isEqualTo(30);
        assertThat(guardada.getValue().getIndicacion()).isEqualTo("Más atención al cliente");
        assertThat(guardada.getValue().getPedidaPorUsuarioId()).isEqualTo(12L);
        assertThat(guardada.getValue().getOrganizacionId()).isEqualTo(ORGANIZACION);
    }

    @Test
    @DisplayName("Sin preguntas y sin indicación, se piden los 100; si no entra en la cola queda FALLIDA")
    void noEntraEnLaCola() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());
        when(cola.porQueNoSePuedeUsarLaIa(ORGANIZACION)).thenReturn(null);
        when(cola.comoVaElRecomendador(VACANTE)).thenReturn(new Seguimiento("SIN_PEDIR", null));
        when(propuestas.save(any(PropuestaPreguntas.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cola.encolarRecomendador(ORGANIZACION, VACANTE)).thenReturn(false);

        RecomendacionPedida pedida = servicio.pedirRecomendaciones(QUIEN, VACANTE, null);

        assertThat(pedida.encolada()).isFalse();
        assertThat(pedida.mensaje()).startsWith("No se pudo poner en la cola");
        ArgumentCaptor<PropuestaPreguntas> guardada = ArgumentCaptor.forClass(PropuestaPreguntas.class);
        verify(propuestas, times(2)).save(guardada.capture());
        PropuestaPreguntas ultima = guardada.getValue();
        assertThat(ultima.getEstado()).isEqualTo(PropuestaPreguntas.FALLIDA);
        assertThat(ultima.getPuntosQueFaltan()).isEqualTo(100);
        assertThat(ultima.getIndicacion()).isNull();
        assertThat(ultima.getTerminadaEn()).isNotNull();
        verify(porPuntos, never()).calcular(anyLong(), any());
    }

    // ---------------------------------------------------------------- comoVaLaRecomendacion

    @Test
    @DisplayName("Sin ninguna pedida, está sin pedir")
    void recomendacionSinPedir() {
        when(propuestas.findFirstByVacanteIdOrderByIdDesc(VACANTE)).thenReturn(Optional.empty());

        EstadoDeLaRecomendacion estado = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);

        assertThat(estado.estado()).isEqualTo("SIN_PEDIR");
        assertThat(estado.propuestaId()).isNull();
        assertThat(estado.propuesta()).isEmpty();
    }

    @Test
    @DisplayName("Lista: se enseña la propuesta leída de su contenido")
    void recomendacionLista() {
        PropuestaPreguntas p = ultima(PropuestaPreguntas.LISTA);
        p.setContenido("[{\"criterioExistenteId\":null,\"nombre\":\"Orden\",\"queEvalua\":null,"
                + "\"preguntas\":[{\"tipo\":\"ABIERTA\",\"enunciado\":\"¿Un caso?\",\"puntos\":10,"
                + "\"queDebeTener\":null,\"opciones\":[]}]}]");

        EstadoDeLaRecomendacion estado = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);

        assertThat(estado.estado()).isEqualTo("LISTA");
        assertThat(estado.motivo()).isNull();
        assertThat(estado.propuestaId()).isEqualTo(9L);
        assertThat(estado.puntosQueFaltan()).isEqualTo(30);
        assertThat(estado.indicacion()).isEqualTo("Atención");
        assertThat(estado.propuesta()).singleElement().satisfies(c -> {
            assertThat(c.nombre()).isEqualTo("Orden");
            assertThat(c.preguntas()).singleElement()
                    .satisfies(q -> assertThat(q.enunciado()).isEqualTo("¿Un caso?"));
        });
    }

    @Test
    @DisplayName("Lista pero sin contenido: una propuesta vacía, no un error")
    void recomendacionListaVacia() {
        ultima(PropuestaPreguntas.LISTA).setContenido("  ");

        assertThat(servicio.comoVaLaRecomendacion(QUIEN, VACANTE).propuesta()).isEmpty();
    }

    @Test
    @DisplayName("Fallida: se enseña su motivo")
    void recomendacionFallida() {
        ultima(PropuestaPreguntas.FALLIDA);

        EstadoDeLaRecomendacion estado = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);

        assertThat(estado.estado()).isEqualTo("FALLIDA");
        assertThat(estado.motivo()).isEqualTo("Se cortó");
        assertThat(estado.propuesta()).isEmpty();
    }

    @Test
    @DisplayName("Pedida y con el trabajo en curso: en curso, sin motivo")
    void recomendacionEnCurso() {
        ultima(PropuestaPreguntas.PEDIDA);
        when(cola.comoVaElRecomendador(VACANTE)).thenReturn(new Seguimiento("EN_CURSO", null));

        EstadoDeLaRecomendacion estado = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);

        assertThat(estado.estado()).isEqualTo("EN_CURSO");
        assertThat(estado.motivo()).isNull();
    }

    @Test
    @DisplayName("Pedida y con el trabajo detenido: fallida, con el motivo de la cola")
    void recomendacionDetenida() {
        ultima(PropuestaPreguntas.PEDIDA);
        when(cola.comoVaElRecomendador(VACANTE)).thenReturn(new Seguimiento("DETENIDA", "Sin saldo"));

        EstadoDeLaRecomendacion estado = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);

        assertThat(estado.estado()).isEqualTo("FALLIDA");
        assertThat(estado.motivo()).isEqualTo("Sin saldo");
    }

    @Test
    @DisplayName("Pedida y con el trabajo terminado sin propuesta: fallida, y se dice")
    void recomendacionTerminadaSinPropuesta() {
        ultima(PropuestaPreguntas.PEDIDA);
        when(cola.comoVaElRecomendador(VACANTE)).thenReturn(new Seguimiento("TERMINADA", null));

        EstadoDeLaRecomendacion estado = servicio.comoVaLaRecomendacion(QUIEN, VACANTE);

        assertThat(estado.estado()).isEqualTo("FALLIDA");
        assertThat(estado.motivo()).isEqualTo("La IA terminó sin dejar ninguna propuesta.");
    }

    // ---------------------------------------------------------------- corregirInstrucciones

    private void publicadaSinCandidatos() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.of(publicada));
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(List.of());
    }

    @Test
    @DisplayName("Un criterio que no es de la publicada es un 400")
    void criterioAjeno() {
        publicadaSinCandidatos();
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(PUBLICADA)).thenReturn(List.of());
        CorregirInstrucciones datos = new CorregirInstrucciones(null, List.of(new TextoDe(5L, "x")), null);

        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE, datos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El criterio 5 no es de las preguntas publicadas de esta vacante");
    }

    @Test
    @DisplayName("Una pregunta que no está en la publicada es un 400")
    void preguntaAjena() {
        publicadaSinCandidatos();
        when(preguntas.findByVersionBancoIdOrderByOrden(PUBLICADA)).thenReturn(List.of());
        CorregirInstrucciones datos = new CorregirInstrucciones(null, null, List.of(new TextoDe(8L, "x")));

        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE, datos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La pregunta 8 no es una abierta");
    }

    @Test
    @DisplayName("Una cerrada no tiene «qué debe tener»: es un 400")
    void preguntaCerrada() {
        publicadaSinCandidatos();
        when(preguntas.findByVersionBancoIdOrderByOrden(PUBLICADA)).thenReturn(List.of(
                Pregunta.builder().id(8L).tipo("OPCION_UNICA").build()));
        CorregirInstrucciones datos = new CorregirInstrucciones(null, null, List.of(new TextoDe(8L, "x")));

        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE, datos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La pregunta 8 no es una abierta");
    }

    @Test
    @DisplayName("Los mismos textos no son un cambio: ni se sube la guía ni se audita")
    void sinCambios() {
        publicadaSinCandidatos();
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(PUBLICADA)).thenReturn(List.of(
                CriterioBanco.builder().id(5L).queEvalua("Trato").build()));
        when(preguntas.findByVersionBancoIdOrderByOrden(PUBLICADA)).thenReturn(List.of(
                Pregunta.builder().id(8L).tipo("ABIERTA").queDebeTener("Un caso").build()));

        CambioAplicado aplicado = servicio.corregirInstrucciones(QUIEN, VACANTE, new CorregirInstrucciones(
                "Guía", List.of(new TextoDe(5L, " Trato ")), List.of(new TextoDe(8L, "Un caso"))));

        assertThat(aplicado.personas()).isZero();
        verify(versionesBanco, never()).save(any());
        verify(criteriosBanco, never()).save(any());
        verify(preguntas, never()).save(any());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cambiar el «qué evalúa» y el «qué debe tener» sube la guía y lo deja auditado")
    void cambiaCriterioYPregunta() {
        publicadaSinCandidatos();
        CriterioBanco criterio = CriterioBanco.builder().id(5L).queEvalua("Trato").build();
        Pregunta abierta = Pregunta.builder().id(8L).tipo("ABIERTA").queDebeTener("Un caso").build();
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(PUBLICADA)).thenReturn(List.of(criterio));
        when(preguntas.findByVersionBancoIdOrderByOrden(PUBLICADA)).thenReturn(List.of(abierta));

        CambioAplicado aplicado = servicio.corregirInstrucciones(QUIEN, VACANTE, new CorregirInstrucciones(
                null, List.of(new TextoDe(5L, "Trato y empatía")), List.of(new TextoDe(8L, "  "))));

        assertThat(aplicado.personas()).isZero();
        assertThat(criterio.getQueEvalua()).isEqualTo("Trato y empatía");
        assertThat(abierta.getQueDebeTener()).isNull();
        assertThat(publicada.getGuiaCalificacion()).isEqualTo("Guía");
        assertThat(publicada.getVersionGuia()).isEqualTo(2);
        verify(criteriosBanco).save(criterio);
        verify(preguntas).save(abierta);
        verify(versionesBanco).save(publicada);
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("corregir_instrucciones_ia"),
                eq("version_banco"), eq(PUBLICADA),
                argThat(antes -> antes.toString().contains("criterio 5")),
                argThat(despues -> despues.toString().contains("pregunta 8")),
                any());
        verify(cola, never()).recalificar(anyLong());
    }

    @Test
    @DisplayName("Sin versión publicada no hay instrucciones que corregir")
    void sinPublicada() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());
        CorregirInstrucciones datos = new CorregirInstrucciones("x", null, null);

        assertThatThrownBy(() -> servicio.corregirInstrucciones(QUIEN, VACANTE, datos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Esta vacante todavía no tiene preguntas publicadas");
    }
}
