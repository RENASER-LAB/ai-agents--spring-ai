package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.DeUnaPersona;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.LineaVigente;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.LineaBusquedaCv;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.LineaBusquedaCvRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("«Qué buscar en el CV» de la vacante: guardar y cambiar las líneas")
class QueBuscarEnElCvDeLaVacanteTest {

    private static final long VACANTE = 4L;
    private static final long ORGANIZACION = 1L;

    @Mock private LineaBusquedaCvRepository lineas;
    @Mock private VacanteRepository vacantes;
    @Mock private PostulacionRepository postulaciones;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private CriterioRepository criterios;
    @Mock private LoBuscadoEnElCv loBuscado;
    @Mock private PuenteCalificacionIa puente;
    @Mock private ColaCalificacionIa cola;
    @Mock private ServicioAuditoria auditoria;

    private QueBuscarEnElCvDeLaVacante servicio;
    private final ContextoUsuario quien = mock(ContextoUsuario.class);
    private final Vacante vacante = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
            .estado("PUBLICADA").build();
    private final List<LineaVigente> antes = List.of(
            new LineaVigente(1L, 1, "Controla costos y mermas", 2),
            new LineaVigente(2L, 2, "Certificado BPM", 2));

    @BeforeEach
    void armar() {
        servicio = new QueBuscarEnElCvDeLaVacante(lineas, vacantes, postulaciones, notasCriterio,
                criterios, loBuscado, puente, cola, auditoria);
        lenient().when(quien.organizacionId()).thenReturn(ORGANIZACION);
        lenient().when(loBuscado.vigentes(VACANTE)).thenReturn(antes);
        lenient().when(vacantes.bloquear(VACANTE, ORGANIZACION)).thenReturn(Optional.of(vacante));
        lenient().when(lineas.ultimaVersion(VACANTE)).thenReturn(2);
        // Tres personas: dos con el CV calificado, una de ellas con «CV sin texto», y otra sin nada.
        lenient().when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(List.of(
                Postulacion.builder().id(10L).build(), Postulacion.builder().id(11L).build(),
                Postulacion.builder().id(12L).build(), Postulacion.builder().id(13L).build()));
        lenient().when(criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(
                "PERFIL_INTEGRAL")).thenReturn(List.of(Criterio.builder().id(100L).build()));
        lenient().when(notasCriterio.findByPostulacionIdIn(any())).thenReturn(List.of(
                NotaCriterio.builder().postulacionId(10L).criterioId(100L).puntaje(BigDecimal.TEN).build()));
        lenient().when(cola.estadoDe(any())).thenReturn(Map.of(
                11L, new ColaCalificacionIa.Estado("EN_CURSO", null)));
        lenient().when(cola.comoVaLaBusqueda(any())).thenReturn(Map.of(12L, "TERMINADA"));
        lenient().when(loBuscado.deLaTanda(eq(antes), any())).thenReturn(Map.of(
                12L, new DeUnaPersona("SIN_TEXTO", "El PDF no tiene texto", Map.of())));
    }

    @Test
    @DisplayName("Cuenta a quien tiene el CV calificado o calificándose, sin los de «CV sin texto»")
    void quienesSeVuelvenABuscar() {
        assertThat(servicio.paraVolverABuscar(VACANTE)).containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("Un formulario que no manda las líneas no las toca")
    void sinLineasNoCambiaNada() {
        assertThat(servicio.alEditar(quien, vacante, null, false).cambiaron()).isFalse();
        verify(vacantes, never()).bloquear(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Las mismas líneas, con otros espacios o vacías de más, no son un cambio")
    void lasMismasNoSonCambio() {
        assertThat(servicio.alEditar(quien, vacante,
                List.of("  Controla costos y mermas ", "", "Certificado BPM"), false).cambiaron()).isFalse();
        verify(lineas, never()).archivarLasVivas(anyLong(), any());
    }

    @Test
    @DisplayName("Cambiarlas con CV calificados sin confirmar es un 409 y no cambia nada")
    void sinConfirmarEsUn409() {
        assertThatThrownBy(() -> servicio.alEditar(quien, vacante,
                List.of("Controla costos y mermas", "Ha dirigido un equipo"), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 personas");
        verify(lineas, never()).archivarLasVivas(anyLong(), any());
        verify(cola, never()).buscarDeNuevoEnElCv(anyLong());
    }

    @Test
    @DisplayName("Una línea prohibida es un 400 antes de bloquear ni preguntar nada")
    void prohibidaEsUn400() {
        assertThatThrownBy(() -> servicio.alEditar(quien, vacante, List.of("Menor de 35 años"), true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(vacantes, never()).bloquear(anyLong(), anyLong());
    }

    @Test
    @DisplayName("AC-13: al confirmar se archivan, se recalcula sin lo buscado y se encola cada búsqueda")
    void alConfirmar() {
        when(loBuscado.archivarAlCambiarLasLineas(eq(antes), eq(3), any())).thenReturn(List.of(10L));

        QueBuscarEnElCvDeLaVacante.Cambio cambio = servicio.alEditar(quien, vacante,
                List.of("Controla costos y mermas", "Ha dirigido un equipo"), true);

        assertThat(cambio).isEqualTo(new QueBuscarEnElCvDeLaVacante.Cambio(true, 2));
        verify(vacantes).bloquear(VACANTE, ORGANIZACION);
        verify(lineas).archivarLasVivas(eq(VACANTE), any());
        ArgumentCaptor<LineaBusquedaCv> nuevas = ArgumentCaptor.forClass(LineaBusquedaCv.class);
        verify(lineas, times(2)).save(nuevas.capture());
        assertThat(nuevas.getAllValues()).extracting(LineaBusquedaCv::getTexto)
                .containsExactly("Controla costos y mermas", "Ha dirigido un equipo");
        assertThat(nuevas.getAllValues()).extracting(LineaBusquedaCv::getVersionLineas)
                .containsOnly(3);
        verify(puente).recalcularSiYaTieneNota(10L);
        verify(cola).buscarDeNuevoEnElCv(10L);
        verify(cola).buscarDeNuevoEnElCv(11L);
        verify(cola, never()).buscarDeNuevoEnElCv(12L);
        verify(auditoria).registrar(eq(ORGANIZACION), eq(quien), eq("editar_que_buscar_en_el_cv"),
                eq("vacante"), eq(VACANTE), anyMap(), anyMap(), isNull());
    }

    @Test
    @DisplayName("Quitar todas archiva lo encontrado y deja la nota como hoy, sin buscar nada")
    void quitarTodas() {
        when(loBuscado.archivarAlCambiarLasLineas(eq(antes), isNull(), any())).thenReturn(List.of(10L));

        QueBuscarEnElCvDeLaVacante.Cambio cambio = servicio.alEditar(quien, vacante, List.of(), true);

        assertThat(cambio.cambiaron()).isTrue();
        assertThat(cambio.cvsABuscar()).isZero();
        verify(lineas, never()).save(any());
        verify(puente).recalcularSiYaTieneNota(10L);
        verify(cola, never()).buscarDeNuevoEnElCv(anyLong());
    }

    @Test
    @DisplayName("Sin nadie calificado se guardan sin preguntar")
    void sinCalificadosNoPregunta() {
        when(notasCriterio.findByPostulacionIdIn(any())).thenReturn(List.of());
        when(cola.estadoDe(any())).thenReturn(Map.of());
        when(cola.comoVaLaBusqueda(any())).thenReturn(Map.of());
        when(loBuscado.deLaTanda(eq(antes), any())).thenReturn(Map.of());

        assertThat(servicio.alEditar(quien, vacante, List.of("Otra cosa"), false).cambiaron()).isTrue();
        verify(cola, never()).buscarDeNuevoEnElCv(anyLong());
    }

    @Test
    @DisplayName("Al crear, se guardan las limpias con la primera versión")
    void alCrear() {
        when(lineas.ultimaVersion(VACANTE)).thenReturn(0);

        servicio.alCrear(VACANTE, List.of("Uno", "uno ", "Dos"));

        ArgumentCaptor<LineaBusquedaCv> nuevas = ArgumentCaptor.forClass(LineaBusquedaCv.class);
        verify(lineas, times(2)).save(nuevas.capture());
        assertThat(nuevas.getAllValues()).extracting(LineaBusquedaCv::getOrden).containsExactly(1, 2);
        assertThat(nuevas.getAllValues()).extracting(LineaBusquedaCv::getVersionLineas).containsOnly(1);
    }
}
