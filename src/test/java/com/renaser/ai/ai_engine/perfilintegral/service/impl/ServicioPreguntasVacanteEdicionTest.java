package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AgregarDeLaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarDatosDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El taller del borrador de las preguntas propias (V66): abrirlo, guardar sus datos,
 * descartarlo, quitar y mover criterios y preguntas, y los porqués cuando no se puede.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El editor de preguntas propias: el taller del borrador")
class ServicioPreguntasVacanteEdicionTest {

    private static final long ORGANIZACION = 2L;
    private static final long VACANTE = 40L;
    private static final long BORRADOR = 80L;
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
    private final VersionBanco borrador = VersionBanco.builder().id(BORRADOR).estado("BORRADOR")
            .metodoCalificacion("PUNTOS").versionGuia(1).build();

    @BeforeEach
    void armar() {
        lenient().when(alcance.laVacanteVisible(eq(QUIEN), eq(VACANTE), any())).thenReturn(vacante);
        lenient().when(porPuntos.calcular(anyLong(), isNull())).thenAnswer(inv ->
                new CalificacionPorPuntos.Resultado(borrador, List.of(), List.of(), BigDecimal.ZERO, false));
    }

    private void hayBorrador() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
    }

    private static Pregunta pregunta(long id, Long criterio, Integer orden) {
        return Pregunta.builder().id(id).versionBancoId(BORRADOR).criterioBancoId(criterio)
                .orden(orden).tipo("ABIERTA").enunciado("P" + id).puntos(10).build();
    }

    private static CriterioBanco criterio(long id, long version, int orden) {
        return CriterioBanco.builder().id(id).versionBancoId(version).nombre("C" + id).orden(orden).build();
    }

    // ---------------------------------------------------------------- leer

    @Test
    @DisplayName("El resumen de una vacante solo con borrador dice BORRADOR")
    void resumenConBorrador() {
        hayBorrador();
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());

        ResumenDePreguntas resumen = servicio.resumenDe(VACANTE);

        assertThat(resumen.estado()).isEqualTo("BORRADOR");
        assertThat(resumen.puntos()).isZero();
    }

    @Test
    @DisplayName("Sin borrador ni publicada, el resumen dice que no hay preguntas")
    void resumenSinNada() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());

        assertThat(servicio.resumenDe(VACANTE)).isEqualTo(new ResumenDePreguntas("SIN_PREGUNTAS", null, null, null));
    }

    // ---------------------------------------------------------------- el borrador

    @Test
    @DisplayName("Abrir el borrador que ya existe no crea otro")
    void abrirElQueYaExiste() {
        hayBorrador();

        EditorDePreguntas editor = servicio.abrirBorrador(QUIEN, VACANTE);

        assertThat(editor.borrador().id()).isEqualTo(BORRADOR);
        assertThat(editor.publicada()).isNull();
        verify(versionesBanco, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Una vacante cerrada no se edita")
    void cerradaNoSeEdita() {
        vacante.setEstado("CERRADA");

        assertThatThrownBy(() -> servicio.abrirBorrador(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Una vacante cerrada o archivada no se edita");
    }

    @Test
    @DisplayName("Guardar los datos del borrador limpia la guía y guarda los minutos")
    void guardarDatos() {
        hayBorrador();

        servicio.guardarDatosDelBorrador(QUIEN, VACANTE, new GuardarDatosDelBorrador("  Mide casos  ", 25));

        assertThat(borrador.getGuiaCalificacion()).isEqualTo("Mide casos");
        assertThat(borrador.getMinutosObjetivo()).isEqualTo(25);
        verify(versionesBanco).save(borrador);
    }

    @Test
    @DisplayName("Una guía en blanco queda vacía, no con espacios")
    void guiaEnBlanco() {
        hayBorrador();
        borrador.setGuiaCalificacion("vieja");

        servicio.guardarDatosDelBorrador(QUIEN, VACANTE, new GuardarDatosDelBorrador("   ", null));

        assertThat(borrador.getGuiaCalificacion()).isNull();
        assertThat(borrador.getMinutosObjetivo()).isNull();
    }

    @Test
    @DisplayName("Descartar el borrador borra sus opciones, preguntas y criterios, y la versión")
    void descartar() {
        hayBorrador();
        when(preguntas.findByVersionBancoIdOrderByOrden(BORRADOR))
                .thenReturn(List.of(pregunta(1L, 5L, 1), pregunta(2L, 5L, 2)));

        servicio.descartarBorrador(QUIEN, VACANTE);

        verify(opciones).deleteByPreguntaIdIn(List.of(1L, 2L));
        verify(preguntas).deleteByVersionBancoId(BORRADOR);
        verify(criteriosBanco).deleteByVersionBancoId(BORRADOR);
        verify(versionesBanco).delete(borrador);
        verify(versionesBanco).flush();
    }

    @Test
    @DisplayName("Descartar sin borrador no borra nada")
    void descartarSinBorrador() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());

        servicio.descartarBorrador(QUIEN, VACANTE);

        verify(versionesBanco, never()).delete(any());
    }

    @Test
    @DisplayName("Sin borrador no hay nada que cambiar, y se dice cómo abrir uno")
    void sinBorradorQueCambiar() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.quitarPregunta(QUIEN, VACANTE, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay borrador que cambiar");
    }

    @Test
    @DisplayName("Sin borrador y con candidatos sobre la publicada, la vara no se mueve")
    void sinBorradorConPostulantes() {
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA"))
                .thenReturn(Optional.of(VersionBanco.builder().id(70L).estado("PUBLICADA").build()));
        when(postulaciones.countByVacanteId(VACANTE)).thenReturn(3L);

        assertThatThrownBy(() -> servicio.quitarCriterio(QUIEN, VACANTE, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("misma vara");
    }

    // ---------------------------------------------------------------- criterios

    @Test
    @DisplayName("Quitar un criterio deja sus preguntas sin criterio, al final, y no las borra")
    void quitarCriterio() {
        hayBorrador();
        CriterioBanco suyo = criterio(5L, BORRADOR, 1);
        when(criteriosBanco.findById(5L)).thenReturn(Optional.of(suyo));
        Pregunta a = pregunta(1L, 5L, 1);
        Pregunta b = pregunta(2L, 5L, 2);
        Pregunta otra = pregunta(3L, 6L, 1);
        Pregunta suelta = pregunta(4L, null, 4);
        when(preguntas.findByVersionBancoIdOrderByOrden(BORRADOR)).thenReturn(List.of(a, b, otra, suelta));

        servicio.quitarCriterio(QUIEN, VACANTE, 5L);

        assertThat(a.getCriterioBancoId()).isNull();
        assertThat(b.getCriterioBancoId()).isNull();
        assertThat(a.getOrden()).isEqualTo(5);
        assertThat(b.getOrden()).isEqualTo(6);
        assertThat(otra.getCriterioBancoId()).isEqualTo(6L);
        verify(preguntas).saveAllAndFlush(List.of(a, b));
        verify(criteriosBanco).delete(suyo);
        verify(preguntas, never()).delete(any());
    }

    @Test
    @DisplayName("Un criterio de otra versión es un 404")
    void criterioDeOtraVersion() {
        hayBorrador();
        when(criteriosBanco.findById(5L)).thenReturn(Optional.of(criterio(5L, 999L, 1)));

        assertThatThrownBy(() -> servicio.quitarCriterio(QUIEN, VACANTE, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(criteriosBanco, never()).delete(any());
    }

    @Test
    @DisplayName("Subir un criterio lo cambia con el de arriba y renumera sin huecos")
    void subirCriterio() {
        hayBorrador();
        CriterioBanco uno = criterio(5L, BORRADOR, 1);
        CriterioBanco dos = criterio(6L, BORRADOR, 4);
        CriterioBanco tres = criterio(7L, BORRADOR, 9);
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(List.of(uno, dos, tres));

        servicio.moverCriterio(QUIEN, VACANTE, 6L, new Mover("arriba"));

        assertThat(dos.getOrden()).isEqualTo(1);
        assertThat(uno.getOrden()).isEqualTo(2);
        assertThat(tres.getOrden()).isEqualTo(3);
        verify(criteriosBanco).saveAll(List.of(dos, uno, tres));
    }

    @Test
    @DisplayName("Bajar el último criterio no hace nada")
    void bajarElUltimo() {
        hayBorrador();
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR))
                .thenReturn(List.of(criterio(5L, BORRADOR, 1), criterio(6L, BORRADOR, 2)));

        servicio.moverCriterio(QUIEN, VACANTE, 6L, new Mover("ABAJO"));

        verify(criteriosBanco, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Subir el primero tampoco hace nada")
    void subirElPrimero() {
        hayBorrador();
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR))
                .thenReturn(List.of(criterio(5L, BORRADOR, 1), criterio(6L, BORRADOR, 2)));

        servicio.moverCriterio(QUIEN, VACANTE, 5L, new Mover("ARRIBA"));

        verify(criteriosBanco, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Una dirección que no es ARRIBA ni ABAJO es un 400")
    void direccionInvalida() {
        hayBorrador();
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR))
                .thenReturn(List.of(criterio(5L, BORRADOR, 1)));

        Mover haciaUnLado = new Mover("izquierda");

        assertThatThrownBy(() -> servicio.moverCriterio(QUIEN, VACANTE, 5L, haciaUnLado))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La dirección es ARRIBA o ABAJO");
    }

    @Test
    @DisplayName("Mover un criterio que no está en el borrador es un 404")
    void moverCriterioQueNoEsta() {
        hayBorrador();
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR))
                .thenReturn(List.of(criterio(5L, BORRADOR, 1)));

        Mover abajo = new Mover("ABAJO");

        assertThatThrownBy(() -> servicio.moverCriterio(QUIEN, VACANTE, 99L, abajo))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- preguntas

    @Test
    @DisplayName("Quitar una pregunta borra también sus opciones")
    void quitarPregunta() {
        hayBorrador();
        Pregunta suya = pregunta(1L, 5L, 1);
        when(preguntas.findById(1L)).thenReturn(Optional.of(suya));

        servicio.quitarPregunta(QUIEN, VACANTE, 1L);

        verify(opciones).deleteByPreguntaIdIn(List.of(1L));
        verify(preguntas).delete(suya);
    }

    @Test
    @DisplayName("Una pregunta de otra versión es un 404")
    void preguntaDeOtraVersion() {
        hayBorrador();
        when(preguntas.findById(1L)).thenReturn(Optional.of(
                Pregunta.builder().id(1L).versionBancoId(999L).build()));

        assertThatThrownBy(() -> servicio.quitarPregunta(QUIEN, VACANTE, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(preguntas, never()).delete(any());
    }

    @Test
    @DisplayName("Bajar una pregunta la mueve dentro de su criterio; las sin orden van al final")
    void bajarPregunta() {
        hayBorrador();
        Pregunta a = pregunta(1L, 5L, 1);
        Pregunta b = pregunta(2L, 5L, null);
        Pregunta c = pregunta(3L, 5L, 2);
        Pregunta deOtro = pregunta(4L, 6L, 1);
        when(preguntas.findById(1L)).thenReturn(Optional.of(a));
        when(preguntas.findByVersionBancoIdOrderByOrden(BORRADOR)).thenReturn(List.of(a, b, c, deOtro));

        servicio.moverPregunta(QUIEN, VACANTE, 1L, new Mover("abajo"));

        assertThat(c.getOrden()).isEqualTo(1);
        assertThat(a.getOrden()).isEqualTo(2);
        assertThat(b.getOrden()).isEqualTo(3);
        assertThat(deOtro.getOrden()).isEqualTo(1);
        verify(preguntas).saveAll(List.of(c, a, b));
    }

    @Test
    @DisplayName("Subir la primera pregunta de su criterio no hace nada")
    void subirLaPrimeraPregunta() {
        hayBorrador();
        Pregunta a = pregunta(1L, 5L, 1);
        when(preguntas.findById(1L)).thenReturn(Optional.of(a));
        when(preguntas.findByVersionBancoIdOrderByOrden(BORRADOR)).thenReturn(List.of(a, pregunta(2L, 5L, 2)));

        servicio.moverPregunta(QUIEN, VACANTE, 1L, new Mover("ARRIBA"));

        assertThat(a.getOrden()).isEqualTo(1);
        verify(preguntas, never()).saveAll(anyList());
    }

    // ---------------------------------------------------------------- la propuesta

    @Test
    @DisplayName("Agregar de una propuesta que no es de la vacante es un 404")
    void propuestaQueNoEsDeLaVacante() {
        when(propuestas.findByIdAndVacanteId(9L, VACANTE)).thenReturn(Optional.empty());
        AgregarDeLaPropuesta datos = new AgregarDeLaPropuesta(List.of(0), List.of());

        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 9L, datos))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Agregar de una propuesta que aún no está lista es un 409")
    void propuestaNoLista() {
        when(propuestas.findByIdAndVacanteId(9L, VACANTE)).thenReturn(Optional.of(
                PropuestaPreguntas.builder().id(9L).vacanteId(VACANTE).estado(PropuestaPreguntas.PEDIDA)
                        .creadoEn(Instant.now()).build()));
        AgregarDeLaPropuesta datos = new AgregarDeLaPropuesta(List.of(0), List.of());

        assertThatThrownBy(() -> servicio.agregarDeLaPropuesta(QUIEN, VACANTE, 9L, datos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Esa propuesta todavía no está lista");
        verify(versionesBanco, never()).saveAndFlush(any());
    }
}
