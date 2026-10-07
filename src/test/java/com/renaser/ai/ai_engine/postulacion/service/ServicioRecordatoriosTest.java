package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreo;
import com.renaser.ai.ai_engine.notificacion.service.DireccionDelCandidato;
import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;
import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.RecordatorioEnviado;
import com.renaser.ai.ai_engine.postulacion.repository.RecordatorioEnviadoRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TurnosParaRecordar;
import com.renaser.ai.ai_engine.postulacion.repository.TurnosParaRecordar.Turno;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Los recordatorios, con el reloj puesto a mano (V70, AC-13 a AC-19).
 *
 * <p>Lo que sale, a quién, con qué texto, y sobre todo cuándo NO sale. Las reglas del calendario
 * tienen su propia prueba; aquí se mira que se respeten de punta a punta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Los recordatorios del banco y de la prueba")
class ServicioRecordatoriosTest {

    @Mock private TurnosParaRecordar turnos;
    @Mock private RecordatorioEnviadoRepository registro;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioCorreo correo;
    @Mock private ServicioAvisosPortal avisos;
    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;
    @Mock private DireccionDelCandidato direcciones;
    @Mock private ServicioEnlaceAcceso enlaces;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private PlatformTransactionManager transacciones;

    private ServicioRecordatorios servicio;

    private static final Long EMPRESA = 3L;
    private static final Long PLATAFORMA = 1L;
    /** El martes de la semana que viene a las 10:00 de Lima: de día, sin fechas escritas a mano. */
    private static final Instant AHORA = LocalDate.now(FechaParaElCandidato.LIMA)
            .with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).atTime(10, 0)
            .atZone(FechaParaElCandidato.LIMA).toInstant();

    @BeforeEach
    void montar() {
        servicio = new ServicioRecordatorios(turnos, registro, parametros, correo, avisos, usuarios,
                personas, direcciones, enlaces, organizaciones, transacciones);
        when(organizaciones.findByEsPlataformaTrue())
                .thenReturn(Optional.of(Organizacion.builder().id(PLATAFORMA).esPlataforma(true).build()));
        when(parametros.booleano(anyLong(), eq(ServicioRecordatorios.PARAMETRO_ACTIVOS), eq(true)))
                .thenReturn(true);
        when(parametros.entero(anyLong(), anyString(), eq(24))).thenReturn(24);
        Usuario usuario = Usuario.builder().id(7L).personaId(8L).correo("ana@correo.pe").build();
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));
        when(personas.findById(8L)).thenReturn(Optional.of(Persona.builder().id(8L).nombre("Ana").build()));
        when(direcciones.de(any(), anyLong())).thenReturn("ana@correo.pe");
        when(enlaces.generarEnlace(anyLong()))
                .thenReturn(new ServicioEnlaceAcceso.EnlaceGenerado("https://portal.test/acceso", null));
        when(correo.plantillaConRecambio(anyLong(), any(), anyString())).thenAnswer(inv ->
                Optional.of(PlantillaCorreo.builder().codigo(inv.getArgument(2)).version(1)
                        .asunto("{{aviso}}").cuerpo("Hola {{nombre}}: {{aviso}}.").build()));
        when(registro.findByTransicionEstadoIdIn(any())).thenReturn(List.of());
    }

    private static Turno banco(long id, Instant desde, Instant vence) {
        return new Turno(id, UUID.randomUUID(), EMPRESA, 7L, 40L, "Administrador de tienda",
                "PERFIL_TURNO_CANDIDATO", 1000 + id, desde, vence);
    }

    private static Turno prueba(long id, Instant desde, Instant vence) {
        return new Turno(id, UUID.randomUUID(), EMPRESA, 7L, 40L, "Administrador de tienda",
                "PRUEBA_TURNO_CANDIDATO", 1000 + id, desde, vence);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> variablesDelCorreo() {
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(correo).enviarCon(any(), eq(7L), eq("ana@correo.pe"), variables.capture());
        return variables.getValue();
    }

    private List<RecordatorioEnviado> guardados(int cuantos) {
        ArgumentCaptor<RecordatorioEnviado> captor = ArgumentCaptor.forClass(RecordatorioEnviado.class);
        verify(registro, times(cuantos)).saveAndFlush(captor.capture());
        return captor.getAllValues();
    }

    // ============ Lo que sale ============

    @Test
    @DisplayName("banco sin entregar a las 24 horas: correo y campana, y queda escrito (AC-13)")
    void elBancoALas24Horas() {
        when(turnos.abiertos()).thenReturn(List.of(banco(1, AHORA.minus(Duration.ofHours(25)), null)));

        assertThat(servicio.enviarPendientes(AHORA)).isEqualTo(1);

        RecordatorioEnviado escrito = guardados(1).get(0);
        assertThat(escrito.getTipo()).isEqualTo("TRAS_ENTRAR");
        assertThat(escrito.getResultado()).isEqualTo("ENVIADO");
        assertThat(escrito.getTransicionEstadoId()).isEqualTo(1001L);
        assertThat(escrito.getPlazoEn()).isNull();

        verify(correo).plantillaConRecambio(EMPRESA, PLATAFORMA, "RECORDATORIO_EVALUACION");
        assertThat(variablesDelCorreo())
                .containsEntry("aviso", "Aún no has respondido tu evaluación para Administrador de tienda")
                .containsEntry("nombre", "Ana")
                .containsEntry("enlace", "https://portal.test/acceso");
        verify(avisos).publicar(eq(EMPRESA), eq(7L), eq("RECORDATORIO_EVALUACION"),
                eq("Aún no has respondido tu evaluación para Administrador de tienda"), anyString(),
                eq(1L), eq(40L));
    }

    @Test
    @DisplayName("banco a 24 horas de vencer: dice cuándo vence, en hora de Lima (AC-14)")
    void elBancoAntesDeVencer() {
        Instant vence = AHORA.plus(Duration.ofHours(23));
        when(turnos.abiertos()).thenReturn(List.of(banco(2, AHORA.minus(Duration.ofDays(5)), vence)));
        // El primero ya salió hace días
        when(registro.findByTransicionEstadoIdIn(any())).thenReturn(List.of(RecordatorioEnviado.builder()
                .transicionEstadoId(1002L).tipo("TRAS_ENTRAR").resultado("ENVIADO").build()));

        assertThat(servicio.enviarPendientes(AHORA)).isEqualTo(1);

        RecordatorioEnviado escrito = guardados(1).get(0);
        assertThat(escrito.getTipo()).isEqualTo("ANTES_DEL_PLAZO");
        assertThat(escrito.getPlazoEn()).isEqualTo(vence);
        assertThat(variablesDelCorreo())
                .containsEntry("vence", FechaParaElCandidato.dicha(vence))
                .containsEntry("aviso", "Tu evaluación para Administrador de tienda vence el "
                        + FechaParaElCandidato.dicha(vence));
    }

    @Test
    @DisplayName("la prueba sin empezar: su texto y su plantilla (AC-15)")
    void laPruebaSinEmpezar() {
        when(turnos.abiertos()).thenReturn(List.of(prueba(3, AHORA.minus(Duration.ofHours(26)), null)));

        assertThat(servicio.enviarPendientes(AHORA)).isEqualTo(1);

        verify(correo).plantillaConRecambio(EMPRESA, PLATAFORMA, "RECORDATORIO_PRUEBA");
        assertThat(variablesDelCorreo()).containsEntry("aviso",
                "Aún no has empezado tu prueba del puesto para Administrador de tienda");
        verify(avisos).publicar(eq(EMPRESA), eq(7L), eq("RECORDATORIO_PRUEBA"), anyString(),
                anyString(), eq(3L), eq(40L));
    }

    @Test
    @DisplayName("si el del plazo se adelanta al primero a menos de 12 horas, el primero queda gastado sin salir (AC-17)")
    void elDelPlazoGastaAlPrimero() {
        // Entró el lunes 14:00: el del plazo toca el martes 8:00 y el primero el martes 14:00
        Instant vence = AHORA.plus(Duration.ofHours(22));
        when(turnos.abiertos()).thenReturn(List.of(prueba(4, AHORA.minus(Duration.ofHours(20)), vence)));

        assertThat(servicio.enviarPendientes(AHORA)).isEqualTo(1);

        List<RecordatorioEnviado> escritos = guardados(2);
        assertThat(escritos.get(0).getTipo()).isEqualTo("ANTES_DEL_PLAZO");
        assertThat(escritos.get(0).getResultado()).isEqualTo("ENVIADO");
        assertThat(escritos.get(1).getTipo()).isEqualTo("TRAS_ENTRAR");
        assertThat(escritos.get(1).getResultado()).isEqualTo("OMITIDO");
        verify(correo, times(1)).enviarCon(any(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("si el del plazo se adelanta al primero a 12 horas o más, el primero sigue pendiente (AC-15)")
    void elDelPlazoNoGastaAlPrimeroLejano() {
        // Entró el martes 0:00: el del plazo toca el martes 9:00 y el primero el miércoles 8:00
        Instant vence = AHORA.plus(Duration.ofHours(23));
        when(turnos.abiertos()).thenReturn(List.of(prueba(5, AHORA.minus(Duration.ofHours(10)), vence)));

        assertThat(servicio.enviarPendientes(AHORA)).isEqualTo(1);

        RecordatorioEnviado escrito = guardados(1).get(0);
        assertThat(escrito.getTipo()).isEqualTo("ANTES_DEL_PLAZO");
        assertThat(escrito.getResultado()).isEqualTo("ENVIADO");
    }

    // ============ Lo que no sale ============

    @Test
    @DisplayName("de noche no sale nada, y ni se consulta (AC-17)")
    void deNocheNada() {
        Instant noche = LocalDate.now(FechaParaElCandidato.LIMA)
                .with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).atTime(22, 0)
                .atZone(FechaParaElCandidato.LIMA).toInstant();

        assertThat(servicio.enviarPendientes(noche)).isZero();

        verifyNoInteractions(turnos, registro, correo, avisos);
    }

    @Test
    @DisplayName("lo que ya salió para el turno no se repite, ni al reiniciar (AC-18)")
    void loQueYaSalioNoSeRepite() {
        when(turnos.abiertos()).thenReturn(List.of(banco(5, AHORA.minus(Duration.ofHours(30)), null)));
        when(registro.findByTransicionEstadoIdIn(any())).thenReturn(List.of(RecordatorioEnviado.builder()
                .transicionEstadoId(1005L).tipo("TRAS_ENTRAR").resultado("ENVIADO").build()));

        assertThat(servicio.enviarPendientes(AHORA)).isZero();

        verify(registro, never()).saveAndFlush(any());
        verifyNoInteractions(correo, avisos);
    }

    @Test
    @DisplayName("con los recordatorios apagados en la empresa, ninguno (AC-16)")
    void apagadosNinguno() {
        when(turnos.abiertos()).thenReturn(List.of(banco(6, AHORA.minus(Duration.ofHours(30)), null)));
        when(parametros.booleano(EMPRESA, ServicioRecordatorios.PARAMETRO_ACTIVOS, true)).thenReturn(false);

        assertThat(servicio.enviarPendientes(AHORA)).isZero();

        verifyNoInteractions(correo, avisos);
        verify(registro, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("si la base no deja escribirlo (ya estaba), ese no sale y los demás siguen (AC-18)")
    void unoQueFallaNoParaALosDemas() {
        when(turnos.abiertos()).thenReturn(List.of(
                banco(7, AHORA.minus(Duration.ofHours(30)), null),
                banco(8, AHORA.minus(Duration.ofHours(30)), null)));
        when(registro.saveAndFlush(any())).thenAnswer(inv -> {
            RecordatorioEnviado r = inv.getArgument(0);
            if (r.getPostulacionId() == 7L) {
                throw new DataIntegrityViolationException("recordatorio_tras_entrar_una_vez");
            }
            return r;
        });

        assertThat(servicio.enviarPendientes(AHORA)).isEqualTo(1);

        verify(avisos).publicar(eq(EMPRESA), eq(7L), anyString(), anyString(), anyString(),
                eq(8L), eq(40L));
        verify(avisos, never()).publicar(any(), any(), any(), any(), any(), eq(7L), any());
    }

    @Test
    @DisplayName("si el correo falla, la campana sale igual (AC-12 en los recordatorios)")
    void siFallaElCorreoSaleLaCampana() {
        when(turnos.abiertos()).thenReturn(List.of(banco(9, AHORA.minus(Duration.ofHours(30)), null)));
        doThrow(new IllegalStateException("el servidor de correo no responde"))
                .when(correo).enviarCon(any(), anyLong(), anyString(), any());

        assertThat(servicio.enviarPendientes(AHORA)).isEqualTo(1);

        verify(avisos).publicar(eq(EMPRESA), eq(7L), eq("RECORDATORIO_EVALUACION"), anyString(),
                anyString(), eq(9L), eq(40L));
    }

    @Test
    @DisplayName("sin texto ni en la empresa ni en la plataforma: se anota y la campana sale igual")
    void sinTexto() {
        when(turnos.abiertos()).thenReturn(List.of(banco(10, AHORA.minus(Duration.ofHours(30)), null)));
        when(correo.plantillaConRecambio(anyLong(), any(), anyString())).thenReturn(Optional.empty());

        assertThat(servicio.enviarPendientes(AHORA)).isEqualTo(1);

        verify(correo, never()).enviarCon(any(), anyLong(), any(), any());
        verify(avisos).publicar(eq(EMPRESA), eq(7L), anyString(), anyString(), anyString(),
                eq(10L), eq(40L));
    }

    @Test
    @DisplayName("unas horas absurdas (cero) valen las de siempre")
    void horasAbsurdas() {
        when(turnos.abiertos()).thenReturn(List.of(banco(11, AHORA.minus(Duration.ofHours(23)), null)));
        when(parametros.entero(EMPRESA, ServicioRecordatorios.PARAMETRO_HORAS_TRAS, 24)).thenReturn(0);

        assertThat(servicio.enviarPendientes(AHORA)).as("a las 23 horas todavía no").isZero();
    }

    @Test
    @DisplayName("sin turnos abiertos no hace nada más")
    void sinTurnos() {
        when(turnos.abiertos()).thenReturn(List.of());

        assertThat(servicio.enviarPendientes(AHORA)).isZero();

        verifyNoInteractions(registro, correo, avisos);
    }
}
