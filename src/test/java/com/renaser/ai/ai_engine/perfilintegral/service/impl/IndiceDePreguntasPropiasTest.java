package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.FilaDePrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.VacanteDestino;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
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
import com.renaser.ai.ai_engine.perfilintegral.service.FiltrosDelIndice;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import com.renaser.ai.ai_engine.vacante.service.LugarDeLaVacante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * «Pruebas», pestaña «Preguntas del Perfil Integral»: qué filas salen, qué dicen, la vista
 * previa y los destinos de la copia (spec pruebas-indice-de-la-empresa).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("«Pruebas»: el índice de las preguntas propias de la empresa")
class IndiceDePreguntasPropiasTest {

    private static final long ORGANIZACION = 2L;
    private static final ContextoUsuario TALENTO = new ContextoUsuario(12L, 3L, ORGANIZACION, "EQUIPO",
            List.of(2L), Map.of("ver_vacantes", "TODO", "editar_vacante", "TODO"));
    private static final ContextoUsuario SOLO_VER = new ContextoUsuario(13L, 4L, ORGANIZACION, "EQUIPO",
            List.of(3L), Map.of("ver_vacantes", "TODO"));
    private static final FiltroAlcance TODO = new FiltroAlcance(FiltroAlcance.Tipo.TODO, 12L);

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
    @Mock private LugarDeLaVacante lugares;
    @InjectMocks private ServicioPreguntasVacanteImpl servicio;

    // A: publicada con un borrador abierto y postulantes, archivada. B: solo borrador, sin puesto.
    // C: usa el banco por nivel (ya no usa el editor). D: sin evaluación. E: eliminada.
    // F: fuera del alcance. G: de otra empresa.
    private final Vacante a = vacante(1L, "Asistente contable").puestoId(50L)
            .archivadaEn(Instant.parse("2026-11-01T00:00:00Z")).estado("CERRADA").build();
    private final Vacante b = vacante(2L, "Cajero").estado("BORRADOR").build();
    private final Vacante c = vacante(3L, "Analista").origenPreguntas("NIVEL").build();
    private final Vacante d = vacante(4L, "Supervisor").aplicaEvaluacion(false).build();
    private final Vacante e = vacante(5L, "Eliminada").eliminadaEn(Instant.now()).build();
    private final Vacante f = vacante(6L, "De otro responsable").build();
    private final Vacante g = vacante(7L, "De otra empresa").organizacionId(99L).build();

    private final VersionBanco publicadaA = version(10L, 1L, "PUBLICADA", Instant.parse("2026-10-14T15:00:00Z"));
    private final VersionBanco borradorA = version(11L, 1L, "BORRADOR", null);
    private final VersionBanco borradorB = version(20L, 2L, "BORRADOR", null);

    private static Vacante.VacanteBuilder vacante(long id, String titulo) {
        return Vacante.builder().id(id).organizacionId(ORGANIZACION).titulo(titulo).estado("PUBLICADA")
                .origenPreguntas("VACANTE").aplicaEvaluacion(true);
    }

    private static VersionBanco version(long id, long vacanteId, String estado, Instant publicadaEn) {
        return VersionBanco.builder().id(id).vacanteId(vacanteId).organizacionId(ORGANIZACION)
                .proposito("PERFIL_INTEGRAL").estado(estado).publicadaEn(publicadaEn).build();
    }

    @BeforeEach
    void armar() {
        when(permisos.alcanceDe(any())).thenReturn(TODO);
        when(alcance.alcanzaALaVacante(any(), any(), any())).thenAnswer(i -> !f.equals(i.getArgument(2)));
        when(lugares.paraLas(anyCollection())).thenReturn(v -> v.getId() == 1L ? "San Isidro, Lima" : null);
        when(versionesBanco.enCursoDelProposito(ORGANIZACION, "PERFIL_INTEGRAL")).thenReturn(List.of(
                publicadaA, borradorA, borradorB, version(30L, 3L, "BORRADOR", null),
                version(40L, 4L, "PUBLICADA", Instant.now()), version(50L, 5L, "PUBLICADA", Instant.now()),
                version(60L, 6L, "PUBLICADA", Instant.now()), version(70L, 7L, "PUBLICADA", Instant.now())));
        when(vacantes.findAllById(any())).thenReturn(List.of(a, b, c, d, e, f, g));
        when(postulaciones.vacantesConPostulantes(anyCollection())).thenReturn(List.of(1L));
        when(puestos.findAllById(any())).thenReturn(List.of(Puesto.builder().id(50L).organizacionId(ORGANIZACION)
                .nivelPuestoCodigo("EJECUCION").build()));
        when(criteriosBanco.findByVersionBancoIdIn(any())).thenReturn(List.of(
                CriterioBanco.builder().id(1L).versionBancoId(10L).build(),
                CriterioBanco.builder().id(2L).versionBancoId(10L).build(),
                CriterioBanco.builder().id(3L).versionBancoId(20L).build()));
        when(preguntas.findByVersionBancoIdIn(any())).thenReturn(List.of(
                Pregunta.builder().id(1L).versionBancoId(10L).build(),
                Pregunta.builder().id(2L).versionBancoId(10L).build(),
                Pregunta.builder().id(3L).versionBancoId(10L).build(),
                Pregunta.builder().id(4L).versionBancoId(11L).build()));
    }

    @Test
    @DisplayName("Solo las de la empresa, vivas, a su alcance y que usan el editor; la publicada manda sobre el borrador (AC-02, AC-03, AC-07)")
    void lasFilasQueSalen() {
        List<FilaDePrueba> filas = servicio.indice(TALENTO, FiltrosDelIndice.NINGUNO);

        assertThat(filas).extracting(FilaDePrueba::vacanteId).containsExactly(1L, 2L);
        FilaDePrueba conBorrador = filas.get(0);
        assertThat(conBorrador.estado()).isEqualTo("PUBLICADA");
        assertThat(conBorrador.hayBorradorAbierto()).isTrue();
        assertThat(conBorrador.laRindeAlguien()).isTrue();
        // Lo que se cuenta es la publicada, no el borrador
        assertThat(conBorrador.criterios()).isEqualTo(2);
        assertThat(conBorrador.preguntas()).isEqualTo(3);
        assertThat(conBorrador.nivel()).isEqualTo("EJECUCION");
        assertThat(conBorrador.situacion()).isEqualTo("ARCHIVADA");
        assertThat(conBorrador.sede()).isEqualTo("San Isidro, Lima");
        assertThat(conBorrador.publicadaEn()).isEqualTo(Instant.parse("2026-10-14T15:00:00Z"));
        assertThat(conBorrador.puedeUsarEnOtra()).isTrue();
        assertThat(conBorrador.entregables()).isNull();
        assertThat(conBorrador.modalidad()).isNull();

        FilaDePrueba soloBorrador = filas.get(1);
        assertThat(soloBorrador.estado()).isEqualTo("BORRADOR");
        assertThat(soloBorrador.hayBorradorAbierto()).isFalse();
        assertThat(soloBorrador.laRindeAlguien()).isFalse();
        assertThat(soloBorrador.publicadaEn()).isNull();
        assertThat(soloBorrador.nivel()).isNull();
        assertThat(soloBorrador.situacion()).isEqualTo("SIN_PUBLICAR");
        assertThat(soloBorrador.criterios()).isEqualTo(1);
        assertThat(soloBorrador.preguntas()).isZero();
        // Un borrador no se copia
        assertThat(soloBorrador.puedeUsarEnOtra()).isFalse();
    }

    @Test
    @DisplayName("Sin editar_vacante ninguna fila ofrece «Usar en otra vacante» (AC-13)")
    void sinEditarNoSeCopia() {
        assertThat(servicio.indice(SOLO_VER, FiltrosDelIndice.NINGUNO))
                .isNotEmpty().noneMatch(FilaDePrueba::puedeUsarEnOtra);
    }

    @Test
    @DisplayName("Los filtros se aplican en el servidor (AC-06)")
    void losFiltros() {
        assertThat(servicio.indice(TALENTO, FiltrosDelIndice.de("cajé", null, null, null)))
                .extracting(FilaDePrueba::vacanteId).containsExactly(2L);
        assertThat(servicio.indice(TALENTO, FiltrosDelIndice.de(null, List.of("EJECUCION"), List.of("EN_USO"),
                List.of("ARCHIVADA")))).extracting(FilaDePrueba::vacanteId).containsExactly(1L);
    }

    @Test
    @DisplayName("Una empresa sin nada escrito no lista nada y no busca vacantes (AC-14)")
    void sinNadaEscrito() {
        when(versionesBanco.enCursoDelProposito(ORGANIZACION, "PERFIL_INTEGRAL")).thenReturn(List.of());
        assertThat(servicio.indice(TALENTO, FiltrosDelIndice.NINGUNO)).isEmpty();
        verify(vacantes, never()).findAllById(any());
    }

    @Test
    @DisplayName("La vista previa enseña la publicada; si solo hay borrador, el borrador sin sus avisos (AC-07, AC-08)")
    void vistaPrevia() {
        when(alcance.laVacanteVisible(eq(TALENTO), eq(1L), any())).thenReturn(a);
        when(alcance.laVacanteVisible(eq(TALENTO), eq(2L), any())).thenReturn(b);
        when(versionesBanco.preguntasPropiasDe(1L, "PUBLICADA")).thenReturn(Optional.of(publicadaA));
        when(versionesBanco.preguntasPropiasDe(1L, "BORRADOR")).thenReturn(Optional.of(borradorA));
        when(versionesBanco.preguntasPropiasDe(2L, "PUBLICADA")).thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(2L, "BORRADOR")).thenReturn(Optional.of(borradorB));
        when(porPuntos.calcular(anyLong(), any())).thenAnswer(i -> new CalificacionPorPuntos.Resultado(
                i.getArgument(0).equals(10L) ? publicadaA : borradorB, List.of(), List.of(), BigDecimal.ZERO, true));

        VersionDePreguntas publicada = servicio.vistaPreviaDelIndice(TALENTO, 1L);
        assertThat(publicada.id()).isEqualTo(10L);
        assertThat(publicada.estado()).isEqualTo("PUBLICADA");

        VersionDePreguntas borrador = servicio.vistaPreviaDelIndice(TALENTO, 2L);
        assertThat(borrador.id()).isEqualTo(20L);
        assertThat(borrador.estado()).isEqualTo("BORRADOR");
        // Vacío tendría «Todavía no hay ninguna pregunta»: los avisos son del editor
        assertThat(borrador.avisos()).isEmpty();
    }

    @Test
    @DisplayName("La vista previa de una vacante que ya no usa el editor, o sin nada escrito, es 404")
    void vistaPreviaDeLoQueNoEstaEsNoEncontrada() {
        when(alcance.laVacanteVisible(eq(TALENTO), eq(3L), any())).thenReturn(c);
        when(alcance.laVacanteVisible(eq(TALENTO), eq(6L), any())).thenReturn(f);
        when(versionesBanco.preguntasPropiasDe(eq(6L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.vistaPreviaDelIndice(TALENTO, 3L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> servicio.vistaPreviaDelIndice(TALENTO, 6L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Los destinos: editables, que usan el editor, sin la de origen; con postulantes, deshabilitada con su motivo (AC-11, AC-15)")
    void destinos() {
        Vacante libre = vacante(8L, "Nueva").estado("BORRADOR").build();
        Vacante cerrada = vacante(9L, "Cerrada").estado("CERRADA").build();
        when(vacantes.findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(ORGANIZACION))
                .thenReturn(List.of(libre, cerrada, b, c, d, f, vacante(1L, "Con postulantes").build(),
                        vacante(12L, "Origen").build()));
        when(versionesBanco.enCursoDelProposito(ORGANIZACION, "PERFIL_INTEGRAL"))
                .thenReturn(List.of(publicadaA, borradorB));

        List<VacanteDestino> destinos = servicio.destinos(TALENTO, 12L);

        assertThat(destinos).extracting(VacanteDestino::vacanteId).containsExactly(8L, 2L, 1L);
        assertThat(destinos.get(0).puedeRecibir()).isTrue();
        assertThat(destinos.get(0).tieneBorrador()).isFalse();
        assertThat(destinos.get(1).tieneBorrador()).isTrue();
        assertThat(destinos.get(1).situacion()).isEqualTo("SIN_PUBLICAR");
        assertThat(destinos.get(2).puedeRecibir()).isFalse();
        assertThat(destinos.get(2).motivo()).isEqualTo("Ya tiene postulantes: sus preguntas no se reemplazan");
        verify(permisos).alcanceDe("editar_vacante");
        // La cerrada, la de otro responsable, la de origen y las que no usan el editor ni se preguntan
        verify(postulaciones).vacantesConPostulantes(List.of(8L, 2L, 1L));
    }

    @Test
    @DisplayName("Copiar en una vacante que no usa sus preguntas propias: 409 y no se crea nada (AC-15)")
    void copiarAUnaQueNoUsaElEditor() {
        Vacante porNivel = vacante(30L, "Por nivel").estado("BORRADOR").origenPreguntas("NIVEL").build();
        Vacante sinEvaluacion = vacante(31L, "Sin evaluación").estado("BORRADOR").aplicaEvaluacion(false).build();
        when(alcance.laVacanteVisible(eq(TALENTO), eq(30L), any())).thenReturn(porNivel);
        when(alcance.laVacanteVisible(eq(TALENTO), eq(31L), any())).thenReturn(sinEvaluacion);

        for (long destino : List.of(30L, 31L)) {
            assertThatThrownBy(() -> servicio.copiar(TALENTO, destino, new CopiarDeOtraVacante(1L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no responde preguntas propias");
        }
        verify(versionesBanco, never()).saveAndFlush(any());
        verify(vacantes, never()).findByIdAndOrganizacionIdAndEliminadaEnIsNull(anyLong(), anyLong());
    }
}
