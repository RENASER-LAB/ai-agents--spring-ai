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
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.InscritoEnSesion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.MarcarAsistencia;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.PreguntaResponse;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.PreguntasEncoladas;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.SesionPanel;
import com.renaser.ai.ai_engine.simulacion.entity.InscripcionSesion;
import com.renaser.ai.ai_engine.simulacion.entity.PreguntaGenerada;
import com.renaser.ai.ai_engine.simulacion.entity.SesionSimulacion;
import com.renaser.ai.ai_engine.simulacion.entity.SesionVacante;
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
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
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

    // Quien crea las sesiones: Talento o Dirección. Lleva el permiso puesto porque el servicio
    // pregunta cuál de los dos trae antes de pedir su alcance.
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            USUARIO, 3L, ORGANIZACION, "EQUIPO", List.of(2L),
            Map.of("crear_sesiones_simulacion", "TODO"));

    // El responsable del área: no crea sesiones, solo mira a los inscritos de las suyas. Es el
    // caso que antes se quedaba fuera de la lista y por tanto sin forma de saber ningún id.
    private static final ContextoUsuario RESPONSABLE = new ContextoUsuario(
            USUARIO, 3L, ORGANIZACION, "EQUIPO", List.of(3L),
            Map.of("ver_inscritos_simulacion", "SUS_VACANTES"));

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
    @Mock private NombresDeUsuarios nombres;
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
                nombres, cola, roles, usuarioRoles, maquina, disponibilidad, parametros,
                auditoria, permisos);
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

    // ============ La lista de sesiones del panel ============

    @org.junit.jupiter.api.Nested
    @DisplayName("Al listar las sesiones en el panel")
    class ListarSesiones {

        @org.junit.jupiter.api.BeforeEach
        void quienMiraCreaSesiones() {
            when(permisos.alcanceDe("crear_sesiones_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
        }

        private com.renaser.ai.ai_engine.simulacion.entity.SesionSimulacion sesion(long id) {
            return com.renaser.ai.ai_engine.simulacion.entity.SesionSimulacion.builder()
                    .id(id).organizacionId(ORGANIZACION)
                    .fechaHora(Instant.parse("2026-09-01T15:00:00Z"))
                    .duracionMinutos(120).modalidad("GRUPAL").lugar("Sala 1").cupo(6)
                    .estado("PUBLICADA")
                    .build();
        }

        @Test
        @DisplayName("Sin ninguna sesión devuelve la lista vacía y no consulta nada más")
        void sinSesionesNoConsultaLoQueCuelga() {
            when(sesiones.findByOrganizacionIdOrderByFechaHora(ORGANIZACION))
                    .thenReturn(List.of());

            assertThat(servicio.listarSesiones(QUIEN)).isEmpty();

            // Si preguntara igual, serían cuatro consultas con una lista de ids vacía: no
            // devuelven nada y en algunos motores ni siquiera son SQL válido.
            verifyNoInteractions(inscripciones, sesionesVacante, responsables, tramos);
        }

        @Test
        @DisplayName("Con sesiones, cada una llega con sus inscritos, vacantes, responsables y tramos")
        void cadaSesionLlegaCompleta() {
            var una = sesion(1L);
            var otra = sesion(2L);
            when(sesiones.findByOrganizacionIdOrderByFechaHora(ORGANIZACION))
                    .thenReturn(List.of(una, otra));
            // La primera tiene dos inscritos; de la segunda la consulta no devuelve fila.
            when(inscripciones.contarVigentesPorSesion(List.of(1L, 2L)))
                    .thenReturn(java.util.List.<Object[]>of(new Object[]{1L, 2L}));
            when(sesionesVacante.findBySesionSimulacionIdIn(List.of(1L, 2L))).thenReturn(List.of(
                    com.renaser.ai.ai_engine.simulacion.entity.SesionVacante.builder()
                            .sesionSimulacionId(1L).vacanteId(70L).build()));
            when(responsables.findBySesionSimulacionIdIn(List.of(1L, 2L))).thenReturn(List.of(
                    com.renaser.ai.ai_engine.simulacion.entity.SesionResponsable.builder()
                            .sesionSimulacionId(1L).usuarioId(USUARIO).build()));
            when(tramos.findBySesionSimulacionIdInOrderByMinutoInicio(List.of(1L, 2L)))
                    .thenReturn(List.of());

            var lista = servicio.listarSesiones(QUIEN);

            assertThat(lista).hasSize(2);
            assertThat(lista.get(0).inscritos()).isEqualTo(2L);
            assertThat(lista.get(0).vacanteIds()).containsExactly(70L);
            assertThat(lista.get(0).responsableIds()).containsExactly(USUARIO);

            // Y la que no aparece en ninguna de las cuatro consultas no se cae de la lista:
            // una sesión recién creada, sin nadie inscrito ni nada colgando, existe igual.
            assertThat(lista.get(1).inscritos()).isZero();
            assertThat(lista.get(1).vacanteIds()).isEmpty();
            assertThat(lista.get(1).responsableIds()).isEmpty();
            assertThat(lista.get(1).tramos()).isEmpty();
        }

        @Test
        @DisplayName("Lo que cuelga se pide de una vez para todas, no una consulta por sesión")
        void unaConsultaPorTablaYNoPorFila() {
            when(sesiones.findByOrganizacionIdOrderByFechaHora(ORGANIZACION))
                    .thenReturn(List.of(sesion(1L), sesion(2L), sesion(3L)));
            when(inscripciones.contarVigentesPorSesion(anyList())).thenReturn(List.of());
            when(sesionesVacante.findBySesionSimulacionIdIn(anyList())).thenReturn(List.of());
            when(responsables.findBySesionSimulacionIdIn(anyList())).thenReturn(List.of());
            when(tramos.findBySesionSimulacionIdInOrderByMinutoInicio(anyList()))
                    .thenReturn(List.of());

            servicio.listarSesiones(QUIEN);

            // Esta lista no se pagina ni se filtra por fecha: son todas las sesiones que la
            // organización creó nunca. Con una consulta por fila, el día que haya un año de
            // sesiones dentro la pantalla se cae sola.
            verify(inscripciones, times(1)).contarVigentesPorSesion(anyList());
            verify(sesionesVacante, times(1)).findBySesionSimulacionIdIn(anyList());
            verify(responsables, times(1)).findBySesionSimulacionIdIn(anyList());
            verify(tramos, times(1)).findBySesionSimulacionIdInOrderByMinutoInicio(anyList());
        }
    }

    /**
     * La lista de sesiones vista por quien no las crea.
     *
     * <p>El responsable del área tiene {@code ver_inscritos_simulacion} y no
     * {@code crear_sesiones_simulacion}. Sin esto la lista le respondía 403, y entonces el
     * endpoint de inscritos era una puerta sin picaporte: podía leer los de una sesión, pero
     * no había forma de averiguar el id de ninguna.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("Al listar las sesiones con el alcance acotado")
    class ListarSesionesConAlcance {

        private static final Long MI_VACANTE = 70L;
        private static final Long VACANTE_AJENA = 71L;

        private SesionSimulacion sesion(Long id) {
            return SesionSimulacion.builder()
                    .id(id).organizacionId(ORGANIZACION)
                    .fechaHora(Instant.parse("2026-09-01T15:00:00Z"))
                    .duracionMinutos(120).modalidad("GRUPAL").cupo(6).estado("PUBLICADA").build();
        }

        /** La sesión 1 toca una vacante de RESPONSABLE; la 2, una ajena. */
        private void dosSesionesDeVacantesDistintas() {
            when(sesiones.findByOrganizacionIdOrderByFechaHora(ORGANIZACION))
                    .thenReturn(List.of(sesion(1L), sesion(2L)));
            when(sesionesVacante.findBySesionSimulacionIdIn(List.of(1L, 2L))).thenReturn(List.of(
                    SesionVacante.builder().sesionSimulacionId(1L).vacanteId(MI_VACANTE).build(),
                    SesionVacante.builder().sesionSimulacionId(2L).vacanteId(VACANTE_AJENA).build()));
            when(vacantes.findByOrganizacionIdAndResponsableUsuarioIdOrderByCreadoEnDesc(
                    ORGANIZACION, USUARIO))
                    .thenReturn(List.of(Vacante.builder().id(MI_VACANTE).titulo("Analista")
                            .responsableUsuarioId(USUARIO).build()));
        }

        @Test
        @DisplayName("Solo salen las sesiones que tocan una vacante suya")
        void soloLasSesionesDeSusVacantes() {
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
            dosSesionesDeVacantesDistintas();
            when(inscripciones.contarVigentesPorSesionDe(List.of(1L), USUARIO))
                    .thenReturn(java.util.List.<Object[]>of(new Object[]{1L, 2L}));
            when(responsables.findBySesionSimulacionIdIn(List.of(1L))).thenReturn(List.of());
            when(tramos.findBySesionSimulacionIdInOrderByMinutoInicio(List.of(1L)))
                    .thenReturn(List.of());

            var lista = servicio.listarSesiones(RESPONSABLE);

            assertThat(lista).extracting(SesionPanel::id).containsExactly(1L);
        }

        @Test
        @DisplayName("El conteo cuenta solo a los suyos, para que cuadre con la lista de inscritos")
        void elConteoSeRecortaIgualQueLaLista() {
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
            dosSesionesDeVacantesDistintas();
            // En la sesión 1 hay seis inscritos, pero solo dos son de su vacante.
            when(inscripciones.contarVigentesPorSesionDe(List.of(1L), USUARIO))
                    .thenReturn(java.util.List.<Object[]>of(new Object[]{1L, 2L}));
            when(responsables.findBySesionSimulacionIdIn(List.of(1L))).thenReturn(List.of());
            when(tramos.findBySesionSimulacionIdInOrderByMinutoInicio(List.of(1L)))
                    .thenReturn(List.of());

            var lista = servicio.listarSesiones(RESPONSABLE);

            assertThat(lista.get(0).inscritos())
                    .as("decir 6 y luego enseñar 2 no se lee como un permiso, se lee como un fallo")
                    .isEqualTo(2L);
            // Y el conteo sin recortar ni se pide: contar filas que se van a descartar es
            // traerlas para nada.
            verify(inscripciones, never()).contarVigentesPorSesion(anyList());
        }

        @Test
        @DisplayName("Si ninguna sesión es de sus vacantes, la lista sale vacía sin pedir nada más")
        void sinSesionesSuyasNoConsultaLoQueCuelga() {
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
            when(sesiones.findByOrganizacionIdOrderByFechaHora(ORGANIZACION))
                    .thenReturn(List.of(sesion(2L)));
            when(sesionesVacante.findBySesionSimulacionIdIn(List.of(2L))).thenReturn(List.of(
                    SesionVacante.builder().sesionSimulacionId(2L).vacanteId(VACANTE_AJENA).build()));
            when(vacantes.findByOrganizacionIdAndResponsableUsuarioIdOrderByCreadoEnDesc(
                    ORGANIZACION, USUARIO)).thenReturn(List.of());

            assertThat(servicio.listarSesiones(RESPONSABLE)).isEmpty();

            verifyNoInteractions(inscripciones, responsables, tramos);
        }

        @Test
        @DisplayName("Con PROPIO no sale ninguna: una sesión no es de nadie en particular")
        void conPropioNoSaleNinguna() {
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.PROPIO, USUARIO));
            when(sesiones.findByOrganizacionIdOrderByFechaHora(ORGANIZACION))
                    .thenReturn(List.of(sesion(1L)));

            assertThat(servicio.listarSesiones(RESPONSABLE)).isEmpty();

            verifyNoInteractions(inscripciones, sesionesVacante, responsables, tramos);
        }

        @Test
        @DisplayName("Abrir una sesión que no toca ninguna vacante suya responde 404")
        void unaSesionAjenaNoSeAbre() {
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
            when(sesiones.findByIdAndOrganizacionId(2L, ORGANIZACION))
                    .thenReturn(Optional.of(sesion(2L)));
            when(sesionesVacante.findBySesionSimulacionId(2L)).thenReturn(List.of(
                    SesionVacante.builder().sesionSimulacionId(2L).vacanteId(VACANTE_AJENA).build()));
            when(vacantes.findAllById(List.of(VACANTE_AJENA))).thenReturn(List.of(
                    Vacante.builder().id(VACANTE_AJENA).responsableUsuarioId(OTRO_USUARIO).build()));

            assertThatThrownBy(() -> servicio.verSesion(RESPONSABLE, 2L))
                    .as("un 403 confirmaría que esa sesión existe")
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("El detalle cuenta lo mismo que la lista, no el total de la sesión")
        void elDetalleCuentaLoMismoQueLaLista() {
            // El fallo que esto sujeta: la lista decía dos y el detalle de la misma sesión seis,
            // porque el detalle contaba por su cuenta y sin recortar. Tres cifras distintas para
            // la misma sesión no son un permiso: son una resta que nadie sabe hacer.
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
            when(sesiones.findByIdAndOrganizacionId(1L, ORGANIZACION))
                    .thenReturn(Optional.of(sesion(1L)));
            when(sesionesVacante.findBySesionSimulacionId(1L)).thenReturn(List.of(
                    SesionVacante.builder().sesionSimulacionId(1L).vacanteId(MI_VACANTE).build()));
            when(vacantes.findAllById(List.of(MI_VACANTE))).thenReturn(List.of(
                    Vacante.builder().id(MI_VACANTE).responsableUsuarioId(USUARIO).build()));
            when(inscripciones.contarVigentesPorSesionDe(List.of(1L), USUARIO))
                    .thenReturn(java.util.List.<Object[]>of(new Object[]{1L, 2L}));
            when(responsables.findBySesionSimulacionId(1L)).thenReturn(List.of());
            when(tramos.findBySesionSimulacionIdOrderByMinutoInicio(1L)).thenReturn(List.of());

            assertThat(servicio.verSesion(RESPONSABLE, 1L).inscritos()).isEqualTo(2L);

            verify(inscripciones, never()).countBySesionSimulacionIdAndEsVigenteTrue(anyLong());
        }

        @Test
        @DisplayName("Con PROPIO tampoco se abre ninguna, igual que no se lista ninguna")
        void conPropioNoSeAbreNinguna() {
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.PROPIO, USUARIO));
            when(sesiones.findByIdAndOrganizacionId(1L, ORGANIZACION))
                    .thenReturn(Optional.of(sesion(1L)));

            assertThatThrownBy(() -> servicio.verSesion(RESPONSABLE, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Y no se llega a mirar de quién son las vacantes: con PROPIO da igual.
            verifyNoInteractions(vacantes);
        }

        @Test
        @DisplayName("Con los dos permisos y alcances distintos, el conteo va por el de inscritos")
        void elConteoSigueAlPermisoDeInscritosYNoAlDeCrear() {
            // El reparto que la V37 no siembra pero que un PUT desde el panel de permisos deja
            // montado en un momento: crear sesiones en TODO y ver inscritos en SUS_VACANTES.
            // Abre todas las sesiones —por el primero— pero solo cuenta a los suyos, porque
            // contar inscritos es ver inscritos. Si el conteo siguiera al permiso con que se
            // llegó a la sesión, la sesión diría seis y la lista enseñaría dos.
            ContextoUsuario losDos = new ContextoUsuario(USUARIO, 3L, ORGANIZACION, "EQUIPO",
                    List.of(2L), Map.of("crear_sesiones_simulacion", "TODO",
                            "ver_inscritos_simulacion", "SUS_VACANTES"));
            when(permisos.alcanceDe("crear_sesiones_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
            when(sesiones.findByIdAndOrganizacionId(2L, ORGANIZACION))
                    .thenReturn(Optional.of(sesion(2L)));
            when(inscripciones.contarVigentesPorSesionDe(List.of(2L), USUARIO))
                    .thenReturn(java.util.List.<Object[]>of(new Object[]{2L, 2L}));
            when(sesionesVacante.findBySesionSimulacionId(2L)).thenReturn(List.of());
            when(responsables.findBySesionSimulacionId(2L)).thenReturn(List.of());
            when(tramos.findBySesionSimulacionIdOrderByMinutoInicio(2L)).thenReturn(List.of());

            // La abre aunque la sesión no toque ninguna vacante suya: eso lo decide el TODO
            // de crear sesiones.
            assertThat(servicio.verSesion(losDos, 2L).inscritos()).isEqualTo(2L);

            verify(inscripciones, never()).countBySesionSimulacionIdAndEsVigenteTrue(anyLong());
        }

        @Test
        @DisplayName("Quien crea sesiones las abre todas, sin comprobar de quién son las vacantes")
        void conTodoNoSeComprueban() {
            when(permisos.alcanceDe("crear_sesiones_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
            when(sesiones.findByIdAndOrganizacionId(2L, ORGANIZACION))
                    .thenReturn(Optional.of(sesion(2L)));
            when(sesionesVacante.findBySesionSimulacionId(2L)).thenReturn(List.of());
            when(responsables.findBySesionSimulacionId(2L)).thenReturn(List.of());
            when(tramos.findBySesionSimulacionIdOrderByMinutoInicio(2L)).thenReturn(List.of());
            when(inscripciones.countBySesionSimulacionIdAndEsVigenteTrue(2L)).thenReturn(0L);

            assertThat(servicio.verSesion(QUIEN, 2L).id()).isEqualTo(2L);

            // Con TODO no hace falta saber de quién son las vacantes de la sesión.
            verify(vacantes, never()).findAllById(anyList());
        }
    }

    /**
     * Quién eligió cada fecha.
     *
     * <p>Lo que se prueba aquí no es que la lista salga: es que salga <b>recortada por el
     * alcance que el rol tenga hoy</b>. Ese reparto se edita desde el panel —el permiso
     * {@code ver_inscritos_simulacion} puede pasar de TODO a SUS_VACANTES o a PROPIO sin que
     * nadie despliegue nada—, así que los tres casos se comprueban, incluido el que hoy no
     * se siembra: si mañana alguien lo cambia, el que falte un caso no daría un error, daría
     * la lista entera.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("Al listar quién eligió una fecha")
    class ListarInscritos {

        private static final Long MI_VACANTE = 70L;
        private static final Long VACANTE_AJENA = 71L;

        private void laSesionExiste() {
            when(sesiones.findByIdAndOrganizacionId(SESION, ORGANIZACION))
                    .thenReturn(Optional.of(SesionSimulacion.builder()
                            .id(SESION).organizacionId(ORGANIZACION)
                            .fechaHora(Instant.parse("2026-09-01T15:00:00Z"))
                            .duracionMinutos(120).modalidad("GRUPAL").cupo(6)
                            .estado("PUBLICADA").build()));
        }

        /** Dos inscripciones: la 1 en una vacante de QUIEN, la 2 en una que no es suya. */
        private void dosInscritosDeVacantesDistintas() {
            laSesionExiste();
            when(inscripciones.findBySesionSimulacionIdAndEsVigenteTrue(SESION)).thenReturn(List.of(
                    InscripcionSesion.builder().id(1L).postulacionId(101L).esVigente(true)
                            .inscritaEn(Instant.parse("2026-08-20T10:00:00Z")).asistio(true).build(),
                    InscripcionSesion.builder().id(2L).postulacionId(102L).esVigente(true)
                            .inscritaEn(Instant.parse("2026-08-19T10:00:00Z")).build()));
            when(postulaciones.findAllById(any())).thenReturn(List.of(
                    Postulacion.builder().id(101L).organizacionId(ORGANIZACION)
                            .usuarioId(501L).vacanteId(MI_VACANTE).build(),
                    Postulacion.builder().id(102L).organizacionId(ORGANIZACION)
                            .usuarioId(502L).vacanteId(VACANTE_AJENA).build()));
            when(vacantes.findAllById(any())).thenReturn(List.of(
                    Vacante.builder().id(MI_VACANTE).titulo("Analista")
                            .responsableUsuarioId(USUARIO).build(),
                    Vacante.builder().id(VACANTE_AJENA).titulo("Contador")
                            .responsableUsuarioId(OTRO_USUARIO).build()));
        }

        @Test
        @DisplayName("Con alcance TODO salen todos, con su inscripción y por orden de llegada")
        void conAlcanceTodoSalenTodos() {
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
            dosInscritosDeVacantesDistintas();
            when(nombres.porUsuario(any())).thenReturn(Map.of(501L, "Ana Ruiz", 502L, "Beto Paz"));

            var lista = servicio.listarInscritos(QUIEN, SESION);

            // Por inscritaEn y no por id: el orden en que eligieron es el que significa algo,
            // y sin orden explícito la lista dependería de lo que devolviera la base.
            assertThat(lista).extracting(InscritoEnSesion::candidato)
                    .containsExactly("Beto Paz", "Ana Ruiz");

            // El inscripcionId es la razón de ser de esta lista: es lo que piden marcas y
            // asistencia, y hasta ahora no había forma de averiguarlo desde el panel.
            assertThat(lista).extracting(InscritoEnSesion::inscripcionId)
                    .containsExactly(2L, 1L);
            assertThat(lista.get(1).vacante()).isEqualTo("Analista");

            // asistio es tri-estado: vacío es «nadie lo ha marcado», no «no vino».
            assertThat(lista.get(0).asistio()).as("a este nadie le ha marcado nada").isNull();
            assertThat(lista.get(1).asistio()).isTrue();
        }

        @Test
        @DisplayName("Con alcance SUS_VACANTES solo salen los de sus vacantes")
        void conAlcanceSusVacantesSoloLosSuyos() {
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
            dosInscritosDeVacantesDistintas();
            when(nombres.porUsuario(any())).thenReturn(Map.of(501L, "Ana Ruiz"));

            var lista = servicio.listarInscritos(QUIEN, SESION);

            assertThat(lista).extracting(InscritoEnSesion::inscripcionId).containsExactly(1L);

            // Y del que se cae no se pide el nombre: recortar después de haber traído los
            // datos de una persona que este usuario no puede ver no es recortar.
            ArgumentCaptor<java.util.Collection<Long>> pedidos =
                    ArgumentCaptor.forClass(java.util.Collection.class);
            verify(nombres).porUsuario(pedidos.capture());
            assertThat(pedidos.getValue()).containsExactly(501L);
        }

        @Test
        @DisplayName("Con alcance PROPIO no sale nadie, ni siquiera quien llama")
        void conAlcancePropioNoSaleNadie() {
            // PROPIO significa «lo tuyo», y en el panel nada de esto es de quien mira: son
            // candidatos. Los tres endpoints lo tratan igual —la lista de sesiones sale vacía,
            // el detalle da 404 y aquí no sale nadie—, y esa es la razón del cambio: antes
            // devolvía la inscripción de quien llamaba, un caso que además el panel no permite
            // alcanzar, porque exige TIPO_EQUIPO y quien entra por ahí no tiene postulación.
            laSesionExiste();
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.PROPIO, USUARIO));
            when(inscripciones.findBySesionSimulacionIdAndEsVigenteTrue(SESION)).thenReturn(List.of(
                    InscripcionSesion.builder().id(1L).postulacionId(101L).esVigente(true)
                            .inscritaEn(Instant.parse("2026-08-20T10:00:00Z")).build()));
            when(postulaciones.findAllById(any())).thenReturn(List.of(
                    Postulacion.builder().id(101L).organizacionId(ORGANIZACION)
                            .usuarioId(USUARIO).vacanteId(MI_VACANTE).build()));
            when(vacantes.findAllById(any())).thenReturn(List.of(
                    Vacante.builder().id(MI_VACANTE).titulo("Analista")
                            .responsableUsuarioId(OTRO_USUARIO).build()));

            assertThat(servicio.listarInscritos(QUIEN, SESION)).isEmpty();

            // Y no se pide ningún nombre: no hay a quién nombrar.
            verify(nombres, never()).porUsuario(any());
        }

        @Test
        @DisplayName("Una sesión a la que no se apuntó nadie no pregunta por ningún nombre")
        void sinInscritosNoPideNombres() {
            laSesionExiste();
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
            when(inscripciones.findBySesionSimulacionIdAndEsVigenteTrue(SESION))
                    .thenReturn(List.of());

            assertThat(servicio.listarInscritos(QUIEN, SESION)).isEmpty();

            verifyNoInteractions(nombres);
        }

        @Test
        @DisplayName("Los nombres se piden en una sola tanda, no uno por inscrito")
        void losNombresSePidenDeUnaVez() {
            when(permisos.alcanceDe("ver_inscritos_simulacion"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
            dosInscritosDeVacantesDistintas();
            when(nombres.porUsuario(any())).thenReturn(Map.of(501L, "Ana Ruiz", 502L, "Beto Paz"));

            servicio.listarInscritos(QUIEN, SESION);

            // Contra Supabase cada viaje cuesta ~140 ms: una consulta por inscrito es la
            // misma cadena que en su día dejó la bandeja en minuto y medio.
            verify(nombres, times(1)).porUsuario(any());
            verify(postulaciones, times(1)).findAllById(any());
            verify(vacantes, times(1)).findAllById(any());
        }

        @Test
        @DisplayName("De una sesión de otra organización no se ve ni que existe")
        void deOtraOrganizacionNoSeVeNada() {
            when(sesiones.findByIdAndOrganizacionId(99L, ORGANIZACION)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.listarInscritos(QUIEN, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Y el permiso ni se consulta: primero es si la sesión existe para este usuario.
            verifyNoInteractions(inscripciones, nombres);
        }
    }
}
