package com.renaser.ai.ai_engine.resena.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ReporteParaModerar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResolverReporte;
import com.renaser.ai.ai_engine.resena.entity.ReporteResena;
import com.renaser.ai.ai_engine.resena.entity.Resena;
import com.renaser.ai.ai_engine.resena.entity.RespuestaResena;
import com.renaser.ai.ai_engine.resena.repository.ReporteResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.ResenaRepository;
import com.renaser.ai.ai_engine.resena.repository.RespuestaResenaRepository;
import com.renaser.ai.ai_engine.resena.service.AvisosDeResenas;
import com.renaser.ai.ai_engine.resena.service.RelojDeResenas;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Plataforma · Reseñas reportadas")
class ServicioModeracionResenasImplTest {

    private static final long PLATAFORMA = 1L;
    private static final long ANDINA = 2L;
    private static final long CANDIDATA = 100L;
    private static final long PERSONA = 200L;

    private final Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Mock private ReporteResenaRepository reportes;
    @Mock private ResenaRepository resenas;
    @Mock private RespuestaResenaRepository respuestas;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private PersonaRepository personas;
    @Mock private AvisosDeResenas avisos;
    @Mock private ServicioAuditoria auditoria;

    private ServicioModeracionResenasImpl servicio;
    private Resena resena;
    private RespuestaResena respuesta;

    @BeforeEach
    void armar() {
        servicio = new ServicioModeracionResenasImpl(reportes, resenas, respuestas,
                organizaciones, personas, avisos, auditoria,
                new RelojDeResenas(Clock.fixed(ahora, ZoneOffset.UTC)));
        lenient().when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(
                Organizacion.builder().id(PLATAFORMA).esPlataforma(true).build()));
        lenient().when(organizaciones.findById(ANDINA)).thenReturn(Optional.of(
                Organizacion.builder().id(ANDINA).nombre("Constructora Andina").build()));
        lenient().when(organizaciones.findAllById(any())).thenReturn(List.of(
                Organizacion.builder().id(ANDINA).nombre("Constructora Andina").build()));
        lenient().when(personas.findAllById(any())).thenReturn(List.of(
                Persona.builder().id(PERSONA).nombre("Luis").apellidos("Perez").build()));
        resena = Resena.builder().id(900L).organizacionId(ANDINA).personaId(PERSONA)
                .estrellas(1).texto("Llegaba tarde y hablaba mal de sus compañeros de obra.")
                .publicadaEn(ahora.minus(Duration.ofDays(3))).build();
        respuesta = RespuestaResena.builder().id(950L).resenaId(900L).usuarioId(CANDIDATA)
                .texto("No es cierto, cumplí cada turno que se me asignó en la obra.")
                .publicadaEn(ahora.minus(Duration.ofDays(2)))
                .editableHasta(ahora.plus(Duration.ofDays(28))).build();
        lenient().when(resenas.findById(900L)).thenReturn(Optional.of(resena));
        lenient().when(resenas.findAllById(any())).thenReturn(List.of(resena));
        lenient().when(respuestas.findById(950L)).thenReturn(Optional.of(respuesta));
        lenient().when(respuestas.findAllById(any())).thenReturn(List.of(respuesta));
    }

    @Test
    @DisplayName("AC-22: una empresa que no es la plataforma no la ve, aunque tenga el permiso")
    void soloLaPlataforma() {
        ContextoUsuario deAndina = new ContextoUsuario(30L, 31L, ANDINA, "EQUIPO", List.of(4L),
                Map.of("moderar_resenas", "TODO"));

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> servicio.reportes(deAndina, false));
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> servicio.resolver(deAndina, 1L,
                        new ResolverReporte("OCULTAR", "Ofensiva")));
        verifyNoInteractions(reportes);
    }

    @Test
    @DisplayName("Las pendientes van de la más antigua a la más reciente, con Empresa → candidato")
    void laListaDePendientes() {
        when(reportes.findByEstadoOrderByReportadoEnAsc(ReporteResena.PENDIENTE))
                .thenReturn(List.of(reporteDeLaResena(), reporteDeLaRespuesta()));

        List<ReporteParaModerar> lista = servicio.reportes(administrador(), false);

        assertThat(lista).extracting(ReporteParaModerar::objeto)
                .containsExactly(ReporteResena.RESENA, ReporteResena.RESPUESTA);
        assertThat(lista.get(0).empresa()).isEqualTo("Constructora Andina");
        assertThat(lista.get(0).persona()).isEqualTo("Luis Perez");
        assertThat(lista.get(0).textoRespuesta()).isNull();
        // Si se reporta la respuesta, va ella —que es lo que se juzga— y la reseña encima.
        assertThat(lista.get(1).textoRespuesta()).startsWith("No es cierto");
        assertThat(lista.get(1).textoResena()).startsWith("Llegaba tarde");
    }

    @Test
    @DisplayName("AC-20: ocultar la reseña con una nota la saca de todas partes y avisa a la persona")
    void ocultarLaResena() {
        ReporteResena reporte = reporteDeLaResena();
        when(reportes.findById(1L)).thenReturn(Optional.of(reporte));

        servicio.resolver(administrador(), 1L, new ResolverReporte("OCULTAR", " Revela salud "));

        assertThat(resena.getOcultadaEn()).isEqualTo(ahora);
        assertThat(resena.getNotaOcultacion()).isEqualTo("Revela salud");
        assertThat(reporte.getEstado()).isEqualTo(ReporteResena.OCULTADA);
        assertThat(reporte.getResueltoPorUsuarioId()).isEqualTo(5L);
        assertThat(reporte.getNotaRevision()).isEqualTo("Revela salud");
        verify(avisos).reporteResuelto(resena, "Constructora Andina", CANDIDATA, true);
    }

    @Test
    @DisplayName("AC-21: mantenerla no cambia nada y avisa «la mantuvimos»")
    void mantenerLaResena() {
        ReporteResena reporte = reporteDeLaResena();
        when(reportes.findById(1L)).thenReturn(Optional.of(reporte));

        servicio.resolver(administrador(), 1L, new ResolverReporte("MANTENER", "Cumple"));

        assertThat(resena.getOcultadaEn()).isNull();
        assertThat(reporte.getEstado()).isEqualTo(ReporteResena.MANTENIDA);
        verify(avisos).reporteResuelto(resena, "Constructora Andina", CANDIDATA, false);
    }

    @Test
    @DisplayName("AC-39: ocultar la respuesta la esconde y le avisa a la persona; mantenerla, sin aviso")
    void laRespuesta() {
        ReporteResena reporte = reporteDeLaRespuesta();
        when(reportes.findById(2L)).thenReturn(Optional.of(reporte));

        servicio.resolver(administrador(), 2L, new ResolverReporte("OCULTAR", "Insultos"));

        assertThat(respuesta.getOcultadaEn()).isEqualTo(ahora);
        assertThat(respuesta.getNotaOcultacion()).isEqualTo("Insultos");
        assertThat(resena.getOcultadaEn()).isNull();
        verify(avisos).respuestaOcultada(resena, "Constructora Andina", CANDIDATA);

        ReporteResena otro = reporteDeLaRespuesta();
        otro.setId(3L);
        when(reportes.findById(3L)).thenReturn(Optional.of(otro));
        respuesta.setOcultadaEn(null);
        respuesta.setNotaOcultacion(null);

        servicio.resolver(administrador(), 3L, new ResolverReporte("MANTENER", "Cumple"));

        assertThat(respuesta.getOcultadaEn()).isNull();
        verify(avisos, never()).reporteResuelto(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Sin nota, con una decisión inventada o ya resuelto, se rechaza")
    void loQueSeRechaza() {
        ReporteResena reporte = reporteDeLaResena();
        when(reportes.findById(1L)).thenReturn(Optional.of(reporte));

        assertThatIllegalArgumentException().isThrownBy(() ->
                servicio.resolver(administrador(), 1L, new ResolverReporte("OCULTAR", "  ")));
        assertThatIllegalArgumentException().isThrownBy(() ->
                servicio.resolver(administrador(), 1L, new ResolverReporte("BORRAR", "Nota")));

        reporte.setEstado(ReporteResena.RETIRADA);
        reporte.setResueltoEn(ahora);
        assertThatIllegalStateException().isThrownBy(() ->
                servicio.resolver(administrador(), 1L, new ResolverReporte("OCULTAR", "Nota")));
        assertThat(resena.getOcultadaEn()).isNull();
    }

    private ContextoUsuario administrador() {
        return new ContextoUsuario(5L, 6L, PLATAFORMA, "EQUIPO", List.of(1L),
                Map.of("moderar_resenas", "TODO"));
    }

    private ReporteResena reporteDeLaResena() {
        return ReporteResena.builder().id(1L).resenaId(900L).objeto(ReporteResena.RESENA)
                .reportadoPorUsuarioId(CANDIDATA).motivo("DATOS_PERSONALES")
                .comentario("Habla de mi salud").reportadoEn(ahora.minus(Duration.ofDays(1)))
                .estado(ReporteResena.PENDIENTE).build();
    }

    private ReporteResena reporteDeLaRespuesta() {
        return ReporteResena.builder().id(2L).resenaId(900L).respuestaId(950L)
                .objeto(ReporteResena.RESPUESTA).reportadoPorUsuarioId(30L)
                .organizacionReportanteId(ANDINA).motivo("OFENSIVA")
                .reportadoEn(ahora).estado(ReporteResena.PENDIENTE).build();
    }
}
