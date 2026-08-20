package com.renaser.ai.ai_engine.simulacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.DecidirSobreAusente;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.MarcarAsistencia;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.PreguntaResponse;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.PreguntasEncoladas;
import com.renaser.ai.ai_engine.simulacion.entity.InscripcionSesion;
import com.renaser.ai.ai_engine.simulacion.entity.PreguntaGenerada;
import com.renaser.ai.ai_engine.simulacion.repository.InformacionCriticaRepository;
import com.renaser.ai.ai_engine.simulacion.repository.InscripcionSesionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.MarcaTiempoSimulacionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.PreguntaGeneradaRepository;
import com.renaser.ai.ai_engine.simulacion.repository.SesionResponsableRepository;
import com.renaser.ai.ai_engine.simulacion.repository.SesionSimulacionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.SesionVacanteRepository;
import com.renaser.ai.ai_engine.simulacion.repository.TramoSimulacionRepository;
import com.renaser.ai.ai_engine.simulacion.service.ServicioDisponibilidadSimulacion;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La simulación de trabajo: pedirle a la IA el guion de la conversación final, y lo que pasa
 * con la postulación cuando alguien marca si el candidato se presentó o no.
 *
 * <p>Dos familias de cosas se prueban aquí, y las dos duelen en silencio si se rompen:
 *
 * <ul>
 *   <li><b>El estado solo lo mueve la máquina.</b> Es la regla dura del repositorio: nadie
 *       escribe {@code estado_codigo} a mano. Si este servicio lo hiciera, la postulación
 *       cambiaría sin dejar fila en el historial, sin auditoría y sin correo al candidato —y
 *       nada fallaría—: se descubriría meses después, al no poder explicar por qué alguien
 *       está donde está. Y solo se mueve <b>si sigue en el estado esperado</b>: marcar dos
 *       veces la misma asistencia, o marcarla sobre quien ya avanzó, no debe empujarlo otra vez.
 *   <li><b>Pedir las preguntas de la conversación final.</b> Cuesta una llamada al modelo, así
 *       que quien no puede ver esa postulación no puede provocarla, y lo que la cola rechaza no
 *       se anuncia como pedido: quien apretó el botón se quedaría esperando un guion que nadie
 *       está preparando.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La simulación de trabajo")
class ServicioSimulacionImplTest {

    private static final String ESPERANDO = "SIMULACION_POR_HABILITAR";
    private static final String PUEDE_ELEGIR = "SIMULACION_TURNO_CANDIDATO";
    private static final String POR_CONFIRMAR = "SIMULACION_POR_CONFIRMAR";

    private static final Long POSTULACION = 88L;
    private static final Long INSCRIPCION = 14L;
    private static final Long SESION = 6L;
    private static final Long ORGANIZACION = 1L;
    private static final Long USUARIO = 12L;
    private static final Long OTRO_USUARIO = 99L;
    private static final Long VACANTE = 5L;
    private static final java.util.UUID UUID_POSTULACION =
            java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            USUARIO, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private SesionSimulacionRepository sesiones;
    @Mock private SesionVacanteRepository sesionesVacante;
    @Mock private SesionResponsableRepository responsables;
    @Mock private TramoSimulacionRepository tramos;
    @Mock private InformacionCriticaRepository informacionCritica;
    @Mock private InscripcionSesionRepository inscripciones;
    @Mock private MarcaTiempoSimulacionRepository marcas;
    @Mock private PreguntaGeneradaRepository preguntas;
    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private ColaCalificacionIa cola;
    @Mock private RolRepository roles;
    @Mock private UsuarioRolRepository usuarioRoles;
    @Mock private MaquinaEstados maquina;
    @Mock private ServicioDisponibilidadSimulacion disponibilidad;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioAuditoria auditoria;
    @Mock private Permisos permisos;

    private ServicioSimulacionImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioSimulacionImpl(sesiones, sesionesVacante, responsables, tramos,
                informacionCritica, inscripciones, marcas, preguntas, postulaciones, vacantes,
                cola, roles, usuarioRoles, maquina, disponibilidad, parametros, auditoria, permisos);
    }

    // ============ Las fechas que ve el candidato ============

    /**
     * Un candidato solo debe ver las fechas cuando de verdad le toca elegir una.
     *
     * <p>Antes se listaban siempre, y quien iba por el Perfil Integral veía la sesión de
     * simulación en su pantalla. No podía inscribirse —eso sí estaba comprobado— pero eso lo
     * hace peor, no mejor: se le enseña algo sobre lo que no puede actuar, y lo que aprende es
     * que la pantalla miente.
     */
    private void suPostulacionEstaEn(String estado) {
        when(postulaciones.findByUuid(UUID_POSTULACION)).thenReturn(Optional.of(
                Postulacion.builder().id(POSTULACION).uuid(UUID_POSTULACION)
                        .usuarioId(USUARIO).organizacionId(ORGANIZACION).vacanteId(VACANTE)
                        .estadoCodigo(estado).build()));
    }

    @Test
    @DisplayName("cuando le toca elegir fecha, ve las sesiones con sus plazas libres")
    void veLasFechasCuandoLeToca() {
        suPostulacionEstaEn(PUEDE_ELEGIR);
        when(sesiones.disponiblesPara(ORGANIZACION, VACANTE)).thenReturn(List.of(
                com.renaser.ai.ai_engine.simulacion.entity.SesionSimulacion.builder().id(SESION).fechaHora(Instant.now().plusSeconds(86400))
                        .duracionMinutos(120).modalidad("GRUPAL").lugar("Sala 1").cupo(6).build()));
        when(inscripciones.countBySesionSimulacionIdAndEsVigenteTrue(SESION)).thenReturn(2L);

        var fechas = servicio.sesionesDisponibles(QUIEN, UUID_POSTULACION);

        assertThat(fechas).hasSize(1);
        assertThat(fechas.get(0).plazasLibres())
                .as("seis de cupo menos dos ya inscritos")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("mientras va por el Perfil Integral no ve ninguna fecha")
    void noVeFechasAntesDeTiempo() {
        suPostulacionEstaEn("PERFIL_TURNO_CANDIDATO");

        assertThat(servicio.sesionesDisponibles(QUIEN, UUID_POSTULACION))
                .as("ensenar una fecha que no puede elegir solo confunde")
                .isEmpty();

        verifyNoInteractions(sesiones);
    }

    @Test
    @DisplayName("ni cuando la simulación aún no se le ha habilitado")
    void noVeFechasSiTodaviaNoSeLeHabilito() {
        suPostulacionEstaEn(ESPERANDO);

        assertThat(servicio.sesionesDisponibles(QUIEN, UUID_POSTULACION)).isEmpty();
        verifyNoInteractions(sesiones);
    }

    @Test
    @DisplayName("y una vez pasada la simulación, tampoco")
    void noVeFechasDespues() {
        suPostulacionEstaEn(POR_CONFIRMAR);

        assertThat(servicio.sesionesDisponibles(QUIEN, UUID_POSTULACION)).isEmpty();
        verifyNoInteractions(sesiones);
    }

    // ============ Pedirle a la IA las preguntas de la conversación final ============

    @Test
    @DisplayName("una postulación de otra organización no existe para quien pregunta")
    void noSePidenPreguntasDeOtraOrganizacion() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.generarPreguntas(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("sin el permiso de la conversación final no se llega a pedir nada")
    void sinPermisoNoSePideNada() {
        hayPostulacion(POR_CONFIRMAR);
        when(permisos.alcanceDe("hacer_conversacion_final"))
                .thenThrow(new AccessDeniedException("No tienes el permiso «hacer_conversacion_final»"));

        assertThatThrownBy(() -> servicio.generarPreguntas(QUIEN, POSTULACION))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("con alcance «sus vacantes», la de otro responsable tampoco existe")
    void conAlcanceDeSusVacantesLaDeOtroNoSeVe() {
        hayPostulacion(POR_CONFIRMAR);
        alcanceDeConversacion(FiltroAlcance.Tipo.SUS_VACANTES);
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).responsableUsuarioId(OTRO_USUARIO).build()));

        assertThatThrownBy(() -> servicio.generarPreguntas(QUIEN, POSTULACION))
                .as("un 404 también significa «esto no es tuyo»: nadie provoca una llamada al "
                        + "modelo sobre un candidato que no puede ver")
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("cuando la cola acepta, se contesta que quedó pedido y que tarda")
    void seEncolaYSeAvisaQueTarda() {
        hayPostulacion(POR_CONFIRMAR);
        alcanceDeConversacion(FiltroAlcance.Tipo.TODO);
        when(cola.encolarPreguntasSimulacion(POSTULACION)).thenReturn(true);

        PreguntasEncoladas respuesta = servicio.generarPreguntas(QUIEN, POSTULACION);

        assertThat(respuesta.estado()).isEqualTo("ENCOLADA");
        assertThat(respuesta.mensaje())
                .as("tarda decenas de segundos: si no se dice, se aprieta el botón otra vez")
                .isNotBlank();
    }

    @Test
    @DisplayName("lo que la cola rechaza no se anuncia como pedido")
    void loQueLaColaRechazaNoSeAnunciaComoPedido() {
        hayPostulacion(POR_CONFIRMAR);
        alcanceDeConversacion(FiltroAlcance.Tipo.TODO);
        when(cola.encolarPreguntasSimulacion(POSTULACION)).thenReturn(false);

        PreguntasEncoladas respuesta = servicio.generarPreguntas(QUIEN, POSTULACION);

        assertThat(respuesta.estado())
                .as("la cola contesta false si ya están preparadas o hay una petición viva; decir "
                        + "«encolada» dejaría al facilitador esperando un guion que nadie prepara")
                .isEqualTo("SIN_CAMBIOS");
    }

    @Test
    @DisplayName("cada pregunta viaja con el motivo del que salió")
    void lasPreguntasLlevanSuMotivo() {
        hayPostulacion(POR_CONFIRMAR);
        alcanceDeConversacion(FiltroAlcance.Tipo.TODO);
        when(preguntas.findByPostulacionIdOrderByOrden(POSTULACION)).thenReturn(List.of(
                PreguntaGenerada.builder()
                        .id(1L).postulacionId(POSTULACION).orden(1)
                        .texto("Dijiste que avisas los riesgos temprano, ¿qué pasó aquí?")
                        .motivo("Detectó el riesgo a las 10:41 y lo comunicó a las 10:49")
                        .build(),
                PreguntaGenerada.builder()
                        .id(2L).postulacionId(POSTULACION).orden(2)
                        .texto("Una que registró una persona a mano")
                        .build()));

        List<PreguntaResponse> vistas = servicio.verPreguntas(QUIEN, POSTULACION);

        assertThat(vistas).hasSize(2);
        assertThat(vistas.get(0).motivo())
                .as("sin el motivo el facilitador recibe una pregunta sin saber por qué se la dan, "
                        + "y no puede repreguntar bien")
                .isEqualTo("Detectó el riesgo a las 10:41 y lo comunicó a las 10:49");
        assertThat(vistas.get(1).motivo())
                .as("las que registra una persona a mano no traen motivo, y eso no es un fallo")
                .isNull();
    }

    // ============ La regla dura: el estado solo lo mueve la máquina ============

    @Test
    @DisplayName("quien asiste avanza, y avanza por la máquina de estados")
    void asistirAvanzaPorLaMaquina() {
        Postulacion postulacion = postulacionEn(PUEDE_ELEGIR);
        hayInscripcionDe(postulacion);

        servicio.marcarAsistencia(QUIEN, INSCRIPCION, new MarcarAsistencia(true));

        verify(maquina).transicionar(eq(postulacion), eq(POR_CONFIRMAR), eq(QUIEN),
                anyString(), eq(false), eq(false), isNull());
        assertThat(postulacion.getEstadoCodigo())
                .as("el servicio no toca estado_codigo: si lo tocara, el cambio no dejaría "
                        + "historial, ni auditoría, ni correo, y nada fallaría")
                .isEqualTo(PUEDE_ELEGIR);
        verify(postulaciones, never()).save(any(Postulacion.class));
    }

    @Test
    @DisplayName("a quien ya no está en su turno no se le vuelve a mover")
    void noSeMueveAQuienYaAvanzo() {
        Postulacion postulacion = postulacionEn(POR_CONFIRMAR);
        hayInscripcionDe(postulacion);

        servicio.marcarAsistencia(QUIEN, INSCRIPCION, new MarcarAsistencia(true));

        verifyNoInteractions(maquina);
        // Pero la asistencia sí se anota: corregir la marca es normal, empujar dos veces no.
        verify(inscripciones).save(any(InscripcionSesion.class));
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("marcar_asistencia_simulacion"),
                eq("inscripcion_sesion"), eq(INSCRIPCION), isNull(), any(), isNull());
    }

    @Test
    @DisplayName("faltar no es un estado: la inscripción deja de valer y vuelve a esperar fecha")
    void faltarDevuelveALaBandejaDelEquipo() {
        Postulacion postulacion = postulacionEn(PUEDE_ELEGIR);
        InscripcionSesion inscripcion = hayInscripcionDe(postulacion);

        servicio.marcarAsistencia(QUIEN, INSCRIPCION, new MarcarAsistencia(false));

        assertThat(inscripcion.isEsVigente())
                .as("su plaza se libera; no se le reinscribe solo, eso lo decide una persona")
                .isFalse();
        verify(maquina).transicionar(eq(postulacion), eq(ESPERANDO), eq(QUIEN),
                anyString(), eq(false), eq(false), isNull());
        assertThat(postulacion.getEstadoCodigo()).isEqualTo(PUEDE_ELEGIR);
    }

    @Test
    @DisplayName("una inscripción que no existe no marca nada")
    void sinInscripcionNoSeMarcaNada() {
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarAsistencia(QUIEN, INSCRIPCION, new MarcarAsistencia(true)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(maquina);
    }

    @Test
    @DisplayName("si la postulación de esa inscripción ya no está, no se inventa a quién mover")
    void sinPostulacionNoSeMueveANadie() {
        InscripcionSesion inscripcion = InscripcionSesion.builder()
                .id(INSCRIPCION).sesionSimulacionId(SESION).postulacionId(POSTULACION)
                .esVigente(true).inscritaEn(Instant.now()).build();
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.of(inscripcion));
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarAsistencia(QUIEN, INSCRIPCION, new MarcarAsistencia(true)))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(maquina);
    }

    // ============ Qué se hace con quien no se presentó ============

    @Test
    @DisplayName("cerrar a un ausente lo saca del proceso con motivo de persona, no del sistema")
    void cerrarAlAusenteLoSacaConMotivoDePersona() {
        Postulacion postulacion = hayPostulacion(ESPERANDO);
        alcanceDeDecidir(FiltroAlcance.Tipo.TODO);

        servicio.decidirSobreAusente(QUIEN, POSTULACION,
                new DecidirSobreAusente("CERRAR", "No se presentó ni avisó"));

        verify(maquina).transicionar(postulacion, "NO_CONTINUA", QUIEN, "No se presentó ni avisó",
                false, false, "DECISION_PERSONA");
    }

    @Test
    @DisplayName("darle otra fecha no lo mueve a mano: lo mueve el recálculo de disponibilidad")
    void darleOtraFechaLoDejaAlRecalculo() {
        hayPostulacion(ESPERANDO);
        alcanceDeDecidir(FiltroAlcance.Tipo.TODO);

        servicio.decidirSobreAusente(QUIEN, POSTULACION,
                new DecidirSobreAusente("OTRA_FECHA", "Avisó que estaba enfermo"));

        verify(disponibilidad).recalcularVacante(ORGANIZACION, VACANTE);
        verifyNoInteractions(maquina);
    }

    @Test
    @DisplayName("no se decide sobre quien no está esperando que se decida")
    void noSeDecideSobreQuienNoEstaEsperando() {
        hayPostulacion(POR_CONFIRMAR);
        alcanceDeDecidir(FiltroAlcance.Tipo.TODO);

        assertThatThrownBy(() -> servicio.decidirSobreAusente(QUIEN, POSTULACION,
                new DecidirSobreAusente("CERRAR", "Me equivoqué de ficha")))
                .as("el que sí asistió está a un clic del que faltó: sin esto, cerrarlo por error "
                        + "sería un solo botón")
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(maquina);
        verifyNoInteractions(disponibilidad);
        verifyNoInteractions(auditoria);
    }

    // ============ Apoyo ============

    private Postulacion postulacionEn(String estadoCodigo) {
        return Postulacion.builder()
                .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE)
                .usuarioId(OTRO_USUARIO).estadoCodigo(estadoCodigo)
                .build();
    }

    private Postulacion hayPostulacion(String estadoCodigo) {
        Postulacion postulacion = postulacionEn(estadoCodigo);
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(postulacion));
        return postulacion;
    }

    /** Una inscripción vigente y la postulación a la que pertenece, tal como las lee el servicio. */
    private InscripcionSesion hayInscripcionDe(Postulacion postulacion) {
        InscripcionSesion inscripcion = InscripcionSesion.builder()
                .id(INSCRIPCION).sesionSimulacionId(SESION).postulacionId(POSTULACION)
                .esVigente(true).inscritaEn(Instant.now())
                .build();
        when(inscripciones.findById(INSCRIPCION)).thenReturn(Optional.of(inscripcion));
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion));
        return inscripcion;
    }

    private void alcanceDeConversacion(FiltroAlcance.Tipo tipo) {
        when(permisos.alcanceDe("hacer_conversacion_final"))
                .thenReturn(new FiltroAlcance(tipo, USUARIO));
    }

    private void alcanceDeDecidir(FiltroAlcance.Tipo tipo) {
        when(permisos.alcanceDe("decidir_sobre_ausente"))
                .thenReturn(new FiltroAlcance(tipo, USUARIO));
    }
}
