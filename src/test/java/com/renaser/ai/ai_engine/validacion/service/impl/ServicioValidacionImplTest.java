package com.renaser.ai.ai_engine.validacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import com.renaser.ai.ai_engine.validacion.entity.Validacion;
import com.renaser.ai.ai_engine.validacion.repository.ValidacionRepository;
import com.renaser.ai.ai_engine.validacion.service.ServicioValidacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Quién puede mirar y mover el periodo de validación de un candidato.
 *
 * <p>Esta clase no tenía ninguna prueba unitaria, y guarda seis caminos con cuatro permisos
 * distintos. La regla del alcance está escrita a mano dentro, igual que en otros seis
 * servicios, y va a migrar a un guardián compartido: estas pruebas existen para que esa
 * migración sea verificable, porque el error probable no es de compilación sino <b>pegar el
 * permiso de al lado</b>. Si eso pasa nada falla —el endpoint sigue guardado por su
 * {@code @PreAuthorize}— y lo único que cambia es de qué permiso sale el alcance: un rol con
 * uno acotado y otro libre usaría el segundo para saltarse el primero.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El alcance sobre el periodo de validación")
class ServicioValidacionImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long POSTULACION = 88L;
    private static final Long VACANTE = 40L;
    private static final Long USUARIO = 21L;
    private static final Long OTRO_USUARIO = 77L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            USUARIO, 33L, ORGANIZACION, "EQUIPO", List.of(), Map.of());

    @Mock private ValidacionRepository validaciones;
    @Mock private PostulacionRepository postulaciones;
    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private RolRepository roles;
    @Mock private UsuarioRolRepository usuarioRoles;
    @Mock private CalificacionPorCriterio calificacion;
    @Mock private MaquinaEstados maquina;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioAuditoria auditoria;

    private ServicioValidacionImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioValidacionImpl(validaciones, postulaciones, alcance, roles,
                usuarioRoles, calificacion, maquina, parametros, auditoria);
    }

    /** El guardián deja pasar: la postulación es de esta empresa y el alcance llega. */
    private void alcanzable(String permiso) {
        when(alcance.laPostulacionVisible(any(), eq(POSTULACION), eq(permiso)))
                .thenReturn(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE).build());
    }

    /** El guardián dice que no, con el mismo 404 que si la postulación no existiera. */
    private void fueraDeAlcance(String permiso) {
        when(alcance.laPostulacionVisible(any(), eq(POSTULACION), eq(permiso)))
                .thenThrow(new ResourceNotFoundException("Postulación", "id", POSTULACION));
    }

    @Test
    @DisplayName("Ver el periodo de una vacante ajena responde 404, no 403")
    void verElDeUnaVacanteAjena() {
        fueraDeAlcance("completar_metricas_validacion");

        assertThatThrownBy(() -> servicio.ver(QUIEN, POSTULACION))
                .as("un 403 confirmaría que ese candidato está en validación")
                .isInstanceOf(ResourceNotFoundException.class);

        // Y no se llega a leer el periodo de alguien que no le toca.
        verify(validaciones, never()).findByPostulacionId(POSTULACION);
    }

    @Test
    @DisplayName("Habilitar mira su propio permiso, no el de ver las métricas")
    void habilitarMiraSuPermiso() {
        fueraDeAlcance("habilitar_validacion");

        assertThatThrownBy(() -> servicio.habilitar(QUIEN, POSTULACION, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(alcance).laPostulacionVisible(any(), eq(POSTULACION), eq("habilitar_validacion"));
        verify(alcance, never()).laPostulacionVisible(
                any(), eq(POSTULACION), eq("completar_metricas_validacion"));
    }

    @Test
    @DisplayName("Cerrar mira el suyo, que es el más caro de confundir")
    void cerrarMiraSuPermiso() {
        // Cerrar termina el periodo del candidato. Si el alcance saliera de un permiso de
        // lectura, quien solo puede mirar acabaría cerrando periodos de convocatorias ajenas.
        fueraDeAlcance("cerrar_validacion");

        assertThatThrownBy(() -> servicio.cerrar(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(alcance).laPostulacionVisible(any(), eq(POSTULACION), eq("cerrar_validacion"));
        verify(maquina, never()).transicionar(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Con la vacante suya sí pasa del guardián")
    void conLaVacanteSuyaPasa() {
        alcanzable("completar_metricas_validacion");
        when(validaciones.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        // Pasa el guardián y falla más adelante, al no haber periodo: es la prueba de que el
        // 404 de arriba lo lanzaba el alcance y no la falta de datos.
        assertThatThrownBy(() -> servicio.ver(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(validaciones).findByPostulacionId(POSTULACION);
    }

    // ============ Entrar a la etapa: crear o reutilizar el periodo ============

    /*
     * Volver a Validación tras retroceder a alguien chocaba contra la clave única de la V18:
     * «ya existe un registro con postulacion_id X», y la persona se quedaba en Simulación. Lo
     * que se vigila aquí es la tabla del paso 3 de la spec —a qué paso entra según cómo estaba
     * su periodo— y, sobre todo, que el periodo que ya existía NO se guarda: ni una fecha, ni
     * lo habilitado, ni el estado.
     */

    private static final Long PERIODO = 501L;

    /** Un periodo ya habilitado como trabajo real, con todo lo que no se puede perder. */
    private static Validacion periodo(String estado, Instant inicio, Instant fin) {
        return Validacion.builder()
                .id(PERIODO).postulacionId(POSTULACION)
                .modalidad("TRABAJO_REAL").tipoVinculacion("Locación de servicios")
                .dias(10).inicioEn(inicio).finEn(fin).estado(estado)
                .habilitadaPorUsuarioId(USUARIO).responsableUsuarioId(OTRO_USUARIO)
                .creadoEn(Instant.now().minus(20, ChronoUnit.DAYS))
                .build();
    }

    /** Entra con el periodo que ya tenía, y comprueba que la fila no se toca. */
    private ServicioValidacion.Entrada entraCon(Validacion suyo) {
        Validacion antes = periodo(suyo.getEstado(), suyo.getInicioEn(), suyo.getFinEn());
        antes.setCreadoEn(suyo.getCreadoEn());
        when(validaciones.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(suyo));

        ServicioValidacion.Entrada entrada = servicio.crearAlEntrar(POSTULACION, ORGANIZACION);

        assertThat(entrada.validacionId()).as("se reutiliza el mismo periodo").isEqualTo(PERIODO);
        verify(validaciones, never()).save(any());
        assertThat(suyo).as("ningún dato del periodo cambia al volver")
                .usingRecursiveComparison().isEqualTo(antes);
        // Ni siquiera se pregunta por los días por defecto: no hay nada que crear.
        verifyNoInteractions(parametros);
        return entrada;
    }

    @Test
    @DisplayName("Sin periodo, se crea «por habilitar» y entra a ese paso sin coletilla")
    void sinPeriodoSeCrea() {
        when(validaciones.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(parametros.entero(ORGANIZACION, "dias_validacion_por_defecto", 7)).thenReturn(7);
        when(validaciones.save(any(Validacion.class))).thenAnswer(inv -> {
            Validacion nueva = inv.getArgument(0);
            nueva.setId(PERIODO);
            return nueva;
        });

        ServicioValidacion.Entrada entrada = servicio.crearAlEntrar(POSTULACION, ORGANIZACION);

        assertThat(entrada).isEqualTo(new ServicioValidacion.Entrada(
                PERIODO, "VALIDACION_POR_HABILITAR", null));
        ArgumentCaptor<Validacion> guardada = ArgumentCaptor.forClass(Validacion.class);
        verify(validaciones).save(guardada.capture());
        assertThat(guardada.getValue().getEstado()).isEqualTo("POR_HABILITAR");
        assertThat(guardada.getValue().getModalidad()).isEqualTo("SIMULACION_EXTENDIDA");
        assertThat(guardada.getValue().getDias()).isEqualTo(7);
        assertThat(guardada.getValue().getInicioEn()).isNull();
    }

    @Test
    @DisplayName("Por habilitar y ya habilitado: entra a «por habilitar» y lo habilitado se conserva")
    void porHabilitarSeReutiliza() {
        ServicioValidacion.Entrada entrada = entraCon(periodo("POR_HABILITAR", null, null));

        assertThat(entrada.paso()).isEqualTo("VALIDACION_POR_HABILITAR");
        assertThat(entrada.coletilla()).as("es lo que se espera: no hay nada que explicar").isNull();
    }

    @Test
    @DisplayName("En curso con el fin por delante: entra a su turno y el reloj no se reinicia")
    void enCursoEntraASuTurno() {
        Instant inicio = Instant.now().minus(2, ChronoUnit.DAYS);
        ServicioValidacion.Entrada entrada = entraCon(
                periodo("EN_CURSO", inicio, inicio.plus(10, ChronoUnit.DAYS)));

        assertThat(entrada.paso()).isEqualTo("VALIDACION_TURNO_CANDIDATO");
        assertThat(entrada.coletilla()).isEqualTo("su periodo de validación ya estaba en curso");
    }

    @Test
    @DisplayName("En curso con el fin ya pasado: entra a «por confirmar»")
    void enCursoVencidoEntraAPorConfirmar() {
        Instant inicio = Instant.now().minus(12, ChronoUnit.DAYS);
        ServicioValidacion.Entrada entrada = entraCon(
                periodo("EN_CURSO", inicio, inicio.plus(10, ChronoUnit.DAYS)));

        assertThat(entrada.paso()).isEqualTo("VALIDACION_POR_CONFIRMAR");
        assertThat(entrada.coletilla()).isEqualTo("su periodo de validación ya había vencido");
    }

    @Test
    @DisplayName("En curso sin fecha de fin: cuenta como vencido, porque el sondeo no lo vería nunca")
    void enCursoSinFinCuentaComoVencido() {
        ServicioValidacion.Entrada entrada = entraCon(
                periodo("EN_CURSO", Instant.now().minus(1, ChronoUnit.DAYS), null));

        assertThat(entrada.paso()).isEqualTo("VALIDACION_POR_CONFIRMAR");
        assertThat(entrada.coletilla()).isEqualTo("su periodo de validación ya había vencido");
    }

    @Test
    @DisplayName("Terminado: entra a «por confirmar» con sus métricas, para revisarlas y cerrar")
    void terminadoEntraAPorConfirmar() {
        Instant inicio = Instant.now().minus(15, ChronoUnit.DAYS);
        ServicioValidacion.Entrada entrada = entraCon(
                periodo("TERMINADA", inicio, inicio.plus(10, ChronoUnit.DAYS)));

        assertThat(entrada.paso()).isEqualTo("VALIDACION_POR_CONFIRMAR");
        assertThat(entrada.coletilla()).isEqualTo("su periodo de validación ya estaba cerrado");
        // Las métricas viven aparte del periodo: entrar no las lee ni las toca.
        verifyNoInteractions(calificacion);
    }

    @Test
    @DisplayName("La coletilla va detrás del motivo, y no rellena un motivo vacío")
    void laColetillaNoCuentaComoMotivo() {
        var conColetilla = new ServicioValidacion.Entrada(
                PERIODO, "VALIDACION_TURNO_CANDIDATO", "su periodo de validación ya estaba en curso");
        var sinColetilla = new ServicioValidacion.Entrada(PERIODO, "VALIDACION_POR_HABILITAR", null);

        assertThat(conColetilla.motivoCon("Vuelve tras revisar su simulación"))
                .isEqualTo("Vuelve tras revisar su simulación · su periodo de validación ya estaba en curso");
        assertThat(sinColetilla.motivoCon("Vuelve")).isEqualTo("Vuelve");
        // Sin motivo, sigue sin motivo: la máquina de estados es quien lo exige, y una
        // coletilla sola le haría creer que alguien lo escribió.
        assertThat(conColetilla.motivoCon("  ")).isEqualTo("  ");
        assertThat(conColetilla.motivoCon(null)).isNull();
    }

    // ============ Una vacante eliminada (V60) ============

    /**
     * Las cuatro escrituras del periodo de validación: antes, las que transicionan se
     * frenaban en la máquina de estados con su propio texto, y cerrar ni siquiera eso —dejaba
     * el periodo TERMINADO y la nota calculada—. Ahora contestan 404 antes de llegar ahí.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("Si su vacante se eliminó, su periodo de validación no se toca")
    class DeUnaVacanteEliminada {

        @BeforeEach
        void suVacanteSeElimino() {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("Vacante", "id", VACANTE))
                    .when(alcance).exigirQueSuVacanteSigaExistiendo(any());
        }

        private void contestaComoLaVacante(
                org.assertj.core.api.ThrowableAssert.ThrowingCallable accion) {
            assertThatThrownBy(accion)
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Vacante")
                    .hasMessageContaining(String.valueOf(VACANTE));
        }

        @Test
        @DisplayName("habilitar, iniciar, completar una métrica y cerrar contestan 404")
        void ningunaEscritura() {
            alcanzable("habilitar_validacion");
            alcanzable("iniciar_validacion");
            alcanzable("completar_metricas_validacion");
            alcanzable("cerrar_validacion");

            contestaComoLaVacante(() -> servicio.habilitar(QUIEN, POSTULACION,
                    new com.renaser.ai.ai_engine.validacion.dto.DtosValidacion.HabilitarValidacion(
                            "SIMULACION_EXTENDIDA", null, 5, USUARIO)));
            contestaComoLaVacante(() -> servicio.iniciar(QUIEN, POSTULACION));
            contestaComoLaVacante(() -> servicio.completarMetrica(QUIEN, POSTULACION, 3L,
                    new com.renaser.ai.ai_engine.validacion.dto.DtosValidacion.CompletarMetrica(
                            8.0, "Cumplió")));
            contestaComoLaVacante(() -> servicio.cerrar(QUIEN, POSTULACION));

            org.mockito.Mockito.verifyNoInteractions(validaciones, calificacion, maquina,
                    auditoria, roles, usuarioRoles);
        }
    }
}
