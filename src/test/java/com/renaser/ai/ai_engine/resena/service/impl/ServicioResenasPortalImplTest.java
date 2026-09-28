package com.renaser.ai.ai_engine.resena.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.EscribirRespuesta;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.MisResenas;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.Reportar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenaMia;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenasEnLaDescarga;
import com.renaser.ai.ai_engine.resena.entity.ReporteResena;
import com.renaser.ai.ai_engine.resena.entity.Resena;
import com.renaser.ai.ai_engine.resena.entity.RespuestaResena;
import com.renaser.ai.ai_engine.resena.repository.ReporteResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.ResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.RespuestaResenaRepository;
import com.renaser.ai.ai_engine.resena.service.LectorDeResenas;
import com.renaser.ai.ai_engine.resena.service.RelojDeResenas;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las reseñas desde el perfil de la persona reseñada: leer, reportar y responder.
 *
 * <p>Con el reloj fijo en «ahora» y todo lo demás relativo a él: sin fechas quemadas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Portal · Mis reseñas de empresas")
class ServicioResenasPortalImplTest {

    private static final long PERSONA = 200L;
    private static final long USUARIO = 100L;
    private static final long ANDINA = 1L;
    private static final long ACME = 2L;
    private static final String OPINION =
            "Muy responsable con los plazos y con el equipo de obra en todo momento.";
    private static final String RESPUESTA =
            "Gracias por la oportunidad, aprendí mucho con todo el equipo de obra.";

    private final Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Mock private ResenaRepository resenas;
    @Mock private RespuestaResenaRepository respuestas;
    @Mock private ReporteResenaRepository reportes;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private ServicioAuditoria auditoria;

    private ServicioResenasPortalImpl servicio;
    private final List<Resena> suyas = new ArrayList<>();
    private final List<RespuestaResena> respuestasGuardadas = new ArrayList<>();
    private final List<ReporteResena> reportesGuardados = new ArrayList<>();

    @BeforeEach
    void armar() {
        servicio = new ServicioResenasPortalImpl(resenas, respuestas, reportes,
                new LectorDeResenas(resenas, respuestas, organizaciones), auditoria,
                new RelojDeResenas(Clock.fixed(ahora, ZoneOffset.UTC)));

        lenient().when(resenas.findByPersonaIdAndBorradaEnIsNull(PERSONA))
                .thenAnswer(i -> suyas.stream().filter(r -> r.getBorradaEn() == null).toList());
        lenient().when(resenas.findByPersonaId(PERSONA)).thenAnswer(i -> List.copyOf(suyas));
        lenient().when(resenas.findByIdAndPersonaIdAndBorradaEnIsNull(anyLong(), any()))
                .thenAnswer(i -> suyas.stream()
                        .filter(r -> r.getId().equals(i.getArgument(0)))
                        .filter(r -> r.getPersonaId().equals(i.getArgument(1)))
                        .filter(r -> r.getBorradaEn() == null)
                        .findFirst());
        lenient().when(organizaciones.findAllById(any())).thenReturn(List.of(
                Organizacion.builder().id(ANDINA).nombre("Constructora Andina").build(),
                Organizacion.builder().id(ACME).nombre("Acme").build()));
        lenient().when(respuestas.findByResenaIdAndBorradaEnIsNull(anyLong()))
                .thenAnswer(i -> respuestasGuardadas.stream()
                        .filter(r -> r.getResenaId().equals(i.getArgument(0)))
                        .filter(r -> r.getBorradaEn() == null)
                        .findFirst());
        lenient().when(respuestas.findByResenaIdInAndBorradaEnIsNull(anyCollection()))
                .thenAnswer(i -> respuestasGuardadas.stream()
                        .filter(r -> r.getBorradaEn() == null).toList());
        lenient().when(respuestas.saveAndFlush(any(RespuestaResena.class))).thenAnswer(i -> {
            RespuestaResena r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(950L + respuestasGuardadas.size());
                respuestasGuardadas.add(r);
            }
            return r;
        });
        lenient().when(reportes.findByResenaIdInOrderByReportadoEnDesc(anyCollection()))
                .thenAnswer(i -> reportesGuardados.stream()
                        .sorted((a, b) -> b.getReportadoEn().compareTo(a.getReportadoEn()))
                        .toList());
        lenient().when(reportes.saveAndFlush(any(ReporteResena.class))).thenAnswer(i -> {
            ReporteResena r = i.getArgument(0);
            r.setId(700L + reportesGuardados.size());
            reportesGuardados.add(r);
            return r;
        });
    }

    // ============ Leer ============

    @Test
    @DisplayName("AC-12: con 5, 5 y 4 visibles el resumen dice 4,7 · 3, y la ocultada no cuenta")
    void elResumenCuentaLasVisibles() {
        resena(1L, ANDINA, 5, 3);
        resena(2L, ACME, 5, 10);
        resena(3L, ANDINA, 4, 20);
        Resena ocultada = resena(4L, ACME, 1, 1);
        ocultada.setOcultadaEn(ahora);
        ocultada.setNotaOcultacion("Ofensiva");

        MisResenas mias = servicio.mias(candidata());

        assertThat(mias.resumen().promedio()).isEqualByComparingTo("4.7");
        assertThat(mias.resumen().cantidad()).isEqualTo(3);
        // Las más recientes arriba: son las que salen en la sección.
        assertThat(mias.resenas()).extracting(ResenaMia::id).containsExactly(1L, 2L, 3L);
        assertThat(mias.resenas().get(0).empresa()).isEqualTo("Constructora Andina");
        assertThat(servicio.resumen(PERSONA).cantidad()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-13: sin reseñas, el resumen va en cero y la lista vacía")
    void sinResenas() {
        MisResenas mias = servicio.mias(candidata());

        assertThat(mias.resumen().cantidad()).isZero();
        assertThat(mias.resumen().promedio()).isNull();
        assertThat(mias.resenas()).isEmpty();
    }

    // ============ Reportar ============

    @Nested
    @DisplayName("Reportar")
    class Reportando {

        @Test
        @DisplayName("AC-18: con «Otro» y sin comentario no se envía; con él, queda en revisión y sigue contando")
        void otroPideComentario() {
            resena(1L, ANDINA, 2, 3);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> servicio.reportar(candidata(), 1L, new Reportar("OTRO", "")));
            assertThat(reportesGuardados).isEmpty();

            servicio.reportar(candidata(), 1L, new Reportar("OTRO", "Nunca trabajé en esa obra"));

            ResenaMia tarjeta = servicio.mias(candidata()).resenas().get(0);
            assertThat(tarjeta.reportadaEnRevision()).isTrue();
            assertThat(tarjeta.puedeReportar()).isFalse();
            assertThat(servicio.mias(candidata()).resumen().cantidad()).isEqualTo(1);
            assertThat(reportesGuardados.get(0).getObjeto()).isEqualTo(ReporteResena.RESENA);
            assertThat(reportesGuardados.get(0).getReportadoPorUsuarioId()).isEqualTo(USUARIO);
        }

        @Test
        @DisplayName("AC-19: reportada y pendiente, no se reporta otra vez")
        void noDosVeces() {
            resena(1L, ANDINA, 2, 3);
            servicio.reportar(candidata(), 1L, new Reportar("OFENSIVA", null));

            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.reportar(candidata(), 1L,
                            new Reportar("OFENSIVA", null)))
                    .withMessageContaining("revisando");
        }

        @Test
        @DisplayName("Y si la base ve dos a la vez, el segundo recibe el mismo 409")
        void laCarreraLaGanaLaBase() {
            resena(1L, ANDINA, 2, 3);
            when(reportes.saveAndFlush(any(ReporteResena.class)))
                    .thenThrow(new DataIntegrityViolationException("reporte_uno_pendiente"));

            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.reportar(candidata(), 1L,
                            new Reportar("OFENSIVA", null)));
        }

        @Test
        @DisplayName("Mantenida, se puede volver a reportar solo si la empresa la edita después")
        void mantenidaYEditada() {
            Resena r = resena(1L, ANDINA, 2, 10);
            ReporteResena mantenido = ReporteResena.builder().id(600L).resenaId(1L)
                    .objeto(ReporteResena.RESENA).motivo("FALSA")
                    .estado(ReporteResena.MANTENIDA)
                    .reportadoEn(ahora.minus(Duration.ofDays(5)))
                    .resueltoEn(ahora.minus(Duration.ofDays(4))).notaRevision("Cumple").build();
            reportesGuardados.add(mantenido);

            assertThat(servicio.mias(candidata()).resenas().get(0).puedeReportar()).isFalse();
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.reportar(candidata(), 1L,
                            new Reportar("FALSA", null)))
                    .withMessageContaining("mantuvimos");

            r.setEditadaEn(ahora.minus(Duration.ofDays(1)));

            assertThat(servicio.mias(candidata()).resenas().get(0).puedeReportar()).isTrue();
            servicio.reportar(candidata(), 1L, new Reportar("FALSA", null));
            assertThat(reportesGuardados).hasSize(2);
        }

        @Test
        @DisplayName("La reseña de otra persona no existe para ella: 404")
        void laAjenaNoExiste() {
            Resena ajena = resena(1L, ANDINA, 2, 3);
            ajena.setPersonaId(999L);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> servicio.reportar(candidata(), 1L,
                            new Reportar("OFENSIVA", null)));
            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> servicio.responder(candidata(), 1L,
                            new EscribirRespuesta(RESPUESTA)));
        }
    }

    // ============ Responder ============

    @Nested
    @DisplayName("Responder")
    class Respondiendo {

        @Test
        @DisplayName("AC-33: responde de 30 a 500 caracteres, con 30 días para cambiarla, y «Responder» desaparece")
        void responde() {
            resena(1L, ANDINA, 3, 3);

            servicio.responder(candidata(), 1L, new EscribirRespuesta("  " + RESPUESTA + " "));

            RespuestaResena guardada = respuestasGuardadas.get(0);
            assertThat(guardada.getTexto()).isEqualTo(RESPUESTA);
            assertThat(guardada.getUsuarioId()).isEqualTo(USUARIO);
            assertThat(guardada.getEditableHasta()).isEqualTo(ahora.plus(Duration.ofDays(30)));
            ResenaMia tarjeta = servicio.mias(candidata()).resenas().get(0);
            assertThat(tarjeta.puedeResponder()).isFalse();
            assertThat(tarjeta.respuesta().texto()).isEqualTo(RESPUESTA);
            assertThat(tarjeta.respuesta().editable()).isTrue();
            // Responder no cambia las estrellas ni el promedio.
            assertThat(servicio.mias(candidata()).resumen().promedio()).isEqualByComparingTo("3.0");
        }

        @Test
        @DisplayName("AC-34: vacía, con 29 o con 501 caracteres no se envía; y una segunda respuesta se rechaza")
        void validaYUnaSola() {
            resena(1L, ANDINA, 3, 3);

            for (String malo : List.of("   ", "a".repeat(29), "a".repeat(501))) {
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> servicio.responder(candidata(), 1L,
                                new EscribirRespuesta(malo)));
            }
            assertThat(respuestasGuardadas).isEmpty();

            servicio.responder(candidata(), 1L, new EscribirRespuesta(RESPUESTA));
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.responder(candidata(), 1L,
                            new EscribirRespuesta(RESPUESTA)))
                    .withMessageContaining("Ya respondiste");
        }

        @Test
        @DisplayName("AC-26: si la empresa la borró o la plataforma la ocultó mientras escribía, 404")
        void laQueYaNoEstaDisponible() {
            Resena borrada = resena(1L, ANDINA, 3, 3);
            borrada.setBorradaEn(ahora);
            Resena ocultada = resena(2L, ANDINA, 3, 3);
            ocultada.setOcultadaEn(ahora);
            ocultada.setNotaOcultacion("Ofensiva");

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> servicio.responder(candidata(), 1L,
                            new EscribirRespuesta(RESPUESTA)));
            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> servicio.responder(candidata(), 2L,
                            new EscribirRespuesta(RESPUESTA)));
        }

        @Test
        @DisplayName("AC-41: responder a una reseña reportada y pendiente la publica y el reporte sigue pendiente")
        void respondeConElReportePendiente() {
            resena(1L, ANDINA, 2, 3);
            servicio.reportar(candidata(), 1L, new Reportar("OFENSIVA", null));

            servicio.responder(candidata(), 1L, new EscribirRespuesta(RESPUESTA));

            assertThat(respuestasGuardadas).hasSize(1);
            assertThat(reportesGuardados.get(0).getEstado()).isEqualTo(ReporteResena.PENDIENTE);
        }

        @Test
        @DisplayName("AC-36: a los 5 días se edita y sale «Editada»; a los 31 días, la API la rechaza")
        void editarDentroYFueraDelPlazo() {
            resena(1L, ANDINA, 3, 20);
            RespuestaResena suya = respuesta(1L, 5, 25);

            servicio.editarRespuesta(candidata(), 1L, new EscribirRespuesta(RESPUESTA + " Y más."));

            assertThat(suya.getEditadaEn()).isEqualTo(ahora);
            assertThat(servicio.mias(candidata()).resenas().get(0).respuesta().editada()).isTrue();

            suya.setEditableHasta(ahora.minus(Duration.ofDays(1)));

            assertThat(servicio.mias(candidata()).resenas().get(0).respuesta().editable()).isFalse();
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.editarRespuesta(candidata(), 1L,
                            new EscribirRespuesta(RESPUESTA + " Otra vez.")))
                    .withMessageContaining("Ya no se puede cambiar");
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.borrarRespuesta(candidata(), 1L));
        }

        @Test
        @DisplayName("AC-36: borrada dentro del plazo, puede volver a responder; el reporte de la empresa se retira")
        void borrarYVolverAResponder() {
            resena(1L, ANDINA, 3, 20);
            RespuestaResena suya = respuesta(1L, 5, 25);
            ReporteResena pendiente = ReporteResena.builder().id(600L).resenaId(1L)
                    .respuestaId(suya.getId()).objeto(ReporteResena.RESPUESTA)
                    .estado(ReporteResena.PENDIENTE).reportadoEn(ahora).build();
            when(reportes.findByRespuestaIdAndEstado(suya.getId(), ReporteResena.PENDIENTE))
                    .thenReturn(Optional.of(pendiente));

            servicio.borrarRespuesta(candidata(), 1L);

            assertThat(suya.getBorradaEn()).isEqualTo(ahora);
            assertThat(pendiente.getEstado()).isEqualTo(ReporteResena.RETIRADA);
            assertThat(servicio.mias(candidata()).resenas().get(0).puedeResponder()).isTrue();
            servicio.responder(candidata(), 1L, new EscribirRespuesta(RESPUESTA));
            assertThat(respuestasGuardadas).hasSize(2);
        }

        @Test
        @DisplayName("AC-39: ocultada por la plataforma, la ve atenuada con la nota, sin editar, borrar ni responder")
        void laOcultadaEsDefinitiva() {
            resena(1L, ANDINA, 3, 20);
            RespuestaResena suya = respuesta(1L, 5, 25);
            suya.setOcultadaEn(ahora);
            suya.setNotaOcultacion("Insulta a la empresa");

            ResenaMia tarjeta = servicio.mias(candidata()).resenas().get(0);

            assertThat(tarjeta.respuesta().ocultada()).isTrue();
            assertThat(tarjeta.respuesta().notaOcultacion()).isEqualTo("Insulta a la empresa");
            assertThat(tarjeta.respuesta().editable()).isFalse();
            assertThat(tarjeta.puedeResponder()).isFalse();
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.editarRespuesta(candidata(), 1L,
                            new EscribirRespuesta(RESPUESTA)));
            assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.responder(candidata(), 1L,
                            new EscribirRespuesta(RESPUESTA)))
                    .withMessageContaining("no puedes responder de nuevo");
        }
    }

    // ============ La descarga y el borrado de datos ============

    @Test
    @DisplayName("AC-29: la descarga lleva las visibles, sus respuestas —también la de una ocultada, sin el texto de la reseña— y sus reportes")
    void laDescargaLlevaLoSuyo() {
        resena(1L, ANDINA, 5, 3);
        Resena ocultada = resena(2L, ACME, 1, 10);
        ocultada.setOcultadaEn(ahora);
        ocultada.setNotaOcultacion("Ofensiva");
        respuesta(2L, 9, 21);
        resena(3L, ACME, 2, 12).setOcultadaEn(ahora);
        suyas.get(2).setNotaOcultacion("Discriminatoria");
        reportesGuardados.add(ReporteResena.builder().id(600L).resenaId(2L)
                .objeto(ReporteResena.RESENA).motivo("OFENSIVA").estado(ReporteResena.OCULTADA)
                .reportadoEn(ahora.minus(Duration.ofDays(2))).resueltoEn(ahora)
                .notaRevision("Lenguaje ofensivo").build());

        ResenasEnLaDescarga descarga = servicio.paraLaDescarga(PERSONA);

        // La ocultada SIN respuesta no va; la ocultada CON respuesta va sin su texto.
        assertThat(descarga.resenas()).hasSize(2);
        assertThat(descarga.resenas().get(0).texto()).isEqualTo(OPINION);
        assertThat(descarga.resenas().get(1).ocultadaPorLaPlataforma()).isTrue();
        assertThat(descarga.resenas().get(1).texto()).isNull();
        assertThat(descarga.resenas().get(1).respuesta().texto()).isEqualTo(RESPUESTA);
        assertThat(descarga.reportes()).hasSize(1);
        assertThat(descarga.reportes().get(0).estado()).isEqualTo(ReporteResena.OCULTADA);
        assertThat(descarga.reportes().get(0).empresa()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("AC-30: el borrado de datos se lleva reportes, respuestas y reseñas, en ese orden")
    void elBorradoSeLasLleva() {
        resena(1L, ANDINA, 5, 3);
        respuesta(1L, 2, 28);
        when(respuestas.findByResenaIdIn(anyCollection())).thenReturn(respuestasGuardadas);

        Map<String, Integer> cuantas = servicio.borrarDeLaPersona(PERSONA);

        assertThat(cuantas).containsEntry("resenas", 1).containsEntry("respuestas", 1);
        InOrder orden = inOrder(reportes, respuestas, resenas);
        orden.verify(reportes).deleteAllInBatch(any());
        orden.verify(respuestas).deleteAllInBatch(any());
        orden.verify(resenas).deleteAllInBatch(any());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any(), any(),
                any());
    }

    // ============ Ayudantes ============

    private ContextoUsuario candidata() {
        return new ContextoUsuario(USUARIO, PERSONA, 1L, "CANDIDATO", List.of(9L), Map.of());
    }

    private Resena resena(Long id, Long empresa, int estrellas, int haceDias) {
        Resena r = Resena.builder().id(id).postulacionId(50L + id).organizacionId(empresa)
                .personaId(PERSONA).estrellas(estrellas).texto(OPINION)
                .publicadaEn(ahora.minus(Duration.ofDays(haceDias))).build();
        suyas.add(r);
        return r;
    }

    private RespuestaResena respuesta(Long resenaId, int haceDias, int quedanDias) {
        RespuestaResena r = RespuestaResena.builder().id(940L + resenaId).resenaId(resenaId)
                .usuarioId(USUARIO).texto(RESPUESTA)
                .publicadaEn(ahora.minus(Duration.ofDays(haceDias)))
                .editableHasta(ahora.plus(Duration.ofDays(quedanDias))).build();
        respuestasGuardadas.add(r);
        return r;
    }
}
