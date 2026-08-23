package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.DefinirPlazoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.CalificacionIaEncolada;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pedirle al agente que califique la prueba del puesto.
 *
 * <p>Lo que se prueba aquí es casi todo lo que este método tiene que <b>negarse</b> a hacer, y
 * cada negativa cuesta dinero o cuesta credibilidad si desaparece:
 *
 * <ul>
 *   <li><b>Calificar una prueba a medio escribir.</b> Una prueba sin entregar tiene texto, así
 *       que el agente contestaría con una nota perfectamente creíble de un trabajo que el
 *       candidato todavía estaba haciendo. Nadie vería un error: vería una mala nota.
 *   <li><b>Encolar sin que haya nada que encolar.</b> Si la rúbrica no le reserva ni un
 *       criterio al agente, la vuelta de cola termina sin tocar una sola nota; y decir
 *       «encolada» deja a quien apretó el botón esperando un resultado que no va a llegar.
 *   <li><b>Enseñar una postulación que no es de quien pregunta.</b> El alcance se aplica dentro
 *       de la consulta, y un 404 también significa «esto no es tuyo».
 *   <li><b>Anunciar como encolado lo que la cola rechazó.</b> La cola contesta false cuando ya
 *       hay un trabajo vivo; repetirlo pagaría dos veces al proveedor por la misma prueba.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Pedir que la IA califique la prueba del puesto")
class ServicioCalificacionPruebaImplTest {

    private static final Long POSTULACION = 77L;
    private static final Long ORGANIZACION = 1L;
    private static final Long USUARIO = 12L;
    private static final Long OTRO_USUARIO = 99L;
    private static final Long VACANTE = 5L;
    private static final Long VERSION_PLANTILLA = 31L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            USUARIO, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private CriterioRepository criterios;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private ColaCalificacionIa cola;
    @Mock private Permisos permisos;
    @Mock private ServicioAuditoria auditoria;

    private ServicioCalificacionPruebaImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioCalificacionPruebaImpl(postulaciones, vacantes, intentos, criterios,
                notasCriterio, notasEtapa, versionesPesos, cola, permisos, auditoria);
    }

    // ============ Quién puede pedirlo ============

    @Test
    @DisplayName("una postulación de otra organización no existe para quien pregunta")
    void noSeCalificaLaDeOtraOrganizacion() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.calificarConIa(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("sin el permiso de ajustar notas no se llega a pedir nada")
    void sinPermisoNoSePideNada() {
        hayPostulacion();
        when(permisos.alcanceDe("ajustar_nota"))
                .thenThrow(new AccessDeniedException("No tienes el permiso «ajustar_nota»"));

        assertThatThrownBy(() -> servicio.calificarConIa(QUIEN, POSTULACION))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("con alcance «sus vacantes», la de otro responsable tampoco existe")
    void conAlcanceDeSusVacantesLaDeOtroNoSeVe() {
        hayPostulacion();
        alcance(FiltroAlcance.Tipo.SUS_VACANTES);
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).responsableUsuarioId(OTRO_USUARIO).build()));

        assertThatThrownBy(() -> servicio.calificarConIa(QUIEN, POSTULACION))
                .as("un 404 también significa «esto no es tuyo»")
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("con alcance «sus vacantes», la propia sí se puede pedir")
    void conAlcanceDeSusVacantesLaSuyaSiSePide() {
        hayPostulacion();
        alcance(FiltroAlcance.Tipo.SUS_VACANTES);
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).responsableUsuarioId(USUARIO).build()));
        hayIntento(Instant.now());
        hayRubricaCon("AGENTE");
        when(cola.encolarPruebaPuesto(POSTULACION)).thenReturn(true);

        assertThat(servicio.calificarConIa(QUIEN, POSTULACION).estado()).isEqualTo("ENCOLADA");
    }

    // ============ Lo que tiene que haber antes de gastar una llamada al modelo ============

    @Test
    @DisplayName("si esta postulación no llegó a tener prueba, no hay nada que calificar")
    void sinIntentoNoHayNadaQueCalificar() {
        hayPostulacion();
        alcance(FiltroAlcance.Tipo.TODO);
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.calificarConIa(QUIEN, POSTULACION))
                .as("el agente se plantaría igual, pero el mensaje moriría en el registro en vez "
                        + "de llegar a quien apretó el botón")
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("una prueba que todavía no se entregó no se califica")
    void noSeCalificaLoQueElCandidatoEstaEscribiendo() {
        hayPostulacion();
        alcance(FiltroAlcance.Tipo.TODO);
        hayIntento(null);

        assertThatThrownBy(() -> servicio.calificarConIa(QUIEN, POSTULACION))
                .as("el agente contestaría una nota creíble de un trabajo a medio hacer, y eso no "
                        + "se ve como un error: se ve como una mala nota")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está entregada");

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("si la rúbrica no le reserva ni un criterio al agente, no se encola")
    void sinCriteriosDelAgenteNoSeGastaUnaVueltaDeCola() {
        hayPostulacion();
        alcance(FiltroAlcance.Tipo.TODO);
        hayIntento(Instant.now());
        hayRubricaCon("PERSONA", "PERSONA");

        CalificacionIaEncolada respuesta = servicio.calificarConIa(QUIEN, POSTULACION);

        assertThat(respuesta.estado()).isEqualTo("SIN_CAMBIOS");
        assertThat(respuesta.mensaje())
                .as("hay que decir por qué no se hizo nada; si no, parece que el botón no funciona")
                .contains("persona");
        verifyNoInteractions(cola);
    }

    // ============ Lo que se contesta ============

    @Test
    @DisplayName("con la prueba entregada y criterios del agente, la calificación queda en cola")
    void seEncolaYSeAvisaQueTarda() {
        hayPostulacion();
        alcance(FiltroAlcance.Tipo.TODO);
        hayIntento(Instant.now());
        hayRubricaCon("PERSONA", "AGENTE");
        when(cola.encolarPruebaPuesto(POSTULACION)).thenReturn(true);

        CalificacionIaEncolada respuesta = servicio.calificarConIa(QUIEN, POSTULACION);

        assertThat(respuesta.estado()).isEqualTo("ENCOLADA");
        assertThat(respuesta.mensaje())
                .as("tarda decenas de segundos: si no se dice, se aprieta el botón otra vez")
                .isNotBlank();
        verify(cola).encolarPruebaPuesto(POSTULACION);
    }

    @Test
    @DisplayName("si la cola dice que no, no se anuncia que se encoló")
    void loQueLaColaRechazaNoSeAnunciaComoEncolado() {
        hayPostulacion();
        alcance(FiltroAlcance.Tipo.TODO);
        hayIntento(Instant.now());
        hayRubricaCon("AGENTE");
        when(cola.encolarPruebaPuesto(POSTULACION)).thenReturn(false);

        CalificacionIaEncolada respuesta = servicio.calificarConIa(QUIEN, POSTULACION);

        assertThat(respuesta.estado())
                .as("la cola contesta false cuando ya hay un trabajo vivo; decir «encolada» dejaría "
                        + "esperando un resultado que nadie va a producir")
                .isEqualTo("SIN_CAMBIOS");
    }

    // ============ Apoyo ============

    private void hayPostulacion() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE)
                        .usuarioId(OTRO_USUARIO).estadoCodigo("PRUEBA_CALIFICANDO")
                        .build()));
    }

    private void alcance(FiltroAlcance.Tipo tipo) {
        when(permisos.alcanceDe("ajustar_nota")).thenReturn(new FiltroAlcance(tipo, USUARIO));
    }

    /** Un intento de prueba; {@code entregadoEn} nulo es «todavía la está haciendo». */
    private void hayIntento(Instant entregadoEn) {
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                IntentoPrueba.builder()
                        .id(4L).postulacionId(POSTULACION)
                        .versionPlantillaPruebaId(VERSION_PLANTILLA)
                        .iniciadoEn(Instant.now()).entregadoEn(entregadoEn)
                        .build()));
    }

    // ============ La fecha de cierre de UN candidato ============

    @Test
    @DisplayName("le fija la fecha y deja escrito de qué fecha venía")
    void fijaLaFechaYAudita() {
        Instant antes = Instant.parse("2026-08-29T21:00:00Z");
        Instant nueva = Instant.parse("2026-08-24T05:00:00Z");
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE).build()));
        when(permisos.alcanceDe("mover_postulacion"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
        IntentoPrueba intento = IntentoPrueba.builder()
                .id(9L).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION_PLANTILLA)
                .iniciadoEn(Instant.now()).venceEn(antes)
                .build();
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento));

        var salida = servicio.definirPlazo(QUIEN, POSTULACION,
                new DefinirPlazoPrueba(nueva, "Todos cierran el domingo"));

        assertThat(intento.getVenceEn()).isEqualTo(nueva);
        assertThat(salida.yaEmpezo()).isTrue();
        verify(intentos).save(intento);
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "definir_plazo_prueba",
                "intento_prueba", 9L, Map.of("venceEn", antes.toString()),
                Map.of("venceEn", nueva.toString()), "Todos cierran el domingo");
    }

    @Test
    @DisplayName("una prueba ya entregada no cambia de plazo")
    void unaEntregadaNoCambiaDePlazo() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE).build()));
        when(permisos.alcanceDe("mover_postulacion"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                IntentoPrueba.builder().id(9L).postulacionId(POSTULACION)
                        .iniciadoEn(Instant.now()).entregadoEn(Instant.now())
                        .build()));

        assertThatThrownBy(() -> servicio.definirPlazo(QUIEN, POSTULACION,
                new DefinirPlazoPrueba(Instant.parse("2026-08-24T05:00:00Z"), "tarde")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya se entregó");
        verify(intentos, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("sin intento creado no hay plazo que fijar")
    void sinIntentoNoHayPlazo() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE).build()));
        when(permisos.alcanceDe("mover_postulacion"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, USUARIO));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.definirPlazo(QUIEN, POSTULACION,
                new DefinirPlazoPrueba(Instant.parse("2026-08-24T05:00:00Z"), "aún no le toca")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** La rúbrica publicada, con el método de verificación de cada criterio. */
    private void hayRubricaCon(String... metodos) {
        List<Criterio> rubrica = new java.util.ArrayList<>();
        for (int i = 0; i < metodos.length; i++) {
            rubrica.add(Criterio.builder()
                    .id((long) (i + 1)).nombre("Criterio " + (i + 1))
                    .versionPlantillaPruebaId(VERSION_PLANTILLA)
                    .puntos(BigDecimal.valueOf(50))
                    .metodoVerificacion(metodos[i])
                    .orden(i + 1)
                    .build());
        }
        when(criterios.findByVersionPlantillaPruebaId(VERSION_PLANTILLA)).thenReturn(rubrica);
    }
}
