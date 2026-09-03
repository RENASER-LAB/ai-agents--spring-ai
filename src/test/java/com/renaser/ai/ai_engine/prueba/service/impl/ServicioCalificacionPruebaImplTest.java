package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.DefinirPlazoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.CalificacionIaEncolada;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    /**
     * El mismo, pero con el permiso que abre el contenido de un entregable.
     *
     * ⚠️ `QUIEN` lleva `Map.of()`: no tiene ninguno. Esa es justo la mitad que
     * hay que poder probar —que sin el permiso el enlace y el archivo no viajan—
     * y por eso hacen falta los dos contextos y no uno.
     */
    private static final ContextoUsuario QUIEN_PUEDE_VER_EL_CONTENIDO = new ContextoUsuario(
            USUARIO, 3L, ORGANIZACION, "EQUIPO", List.of(2L),
            Map.of("descargar_entregables", "TODO"));

    @Mock private PostulacionRepository postulaciones;
    @Mock private com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante alcanceVacante;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private CriterioRepository criterios;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private CalificacionPorCriterio calificacion;
    @Mock private PreguntaVersionPlantillaRepository preguntasElegidas;
    @Mock private PreguntaPruebaRepository preguntasCatalogo;
    @Mock private RespuestaPruebaRepository respuestas;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private ColaCalificacionIa cola;
    @Mock private ServicioAuditoria auditoria;
    @Mock private com.renaser.ai.ai_engine.vacante.repository.VacanteRepository vacantes;
    @Mock private com.renaser.ai.ai_engine.perfilintegral.service.impl
            .CalificacionCuestionarioTecnico cuestionarioTecnico;
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository notasEtapa;

    @Mock private com.renaser.ai.ai_engine.prueba.repository.EntregableRepository entregables;
    @Mock private com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository entregablesRequeridos;
    @Mock private com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository archivos;

    private ServicioCalificacionPruebaImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioCalificacionPruebaImpl(postulaciones, alcanceVacante, intentos, criterios,
                preguntasElegidas, preguntasCatalogo, respuestas, notasCriterio, versionesPesos,
                cola, auditoria, vacantes, cuestionarioTecnico, notasEtapa, calificacion,
                entregables, entregablesRequeridos, archivos);
    }

    // ============ Quién puede pedirlo ============

    @Test
    @DisplayName("una postulación de otra organización no existe para quien pregunta")
    void noSeCalificaLaDeOtraOrganizacion() {
        fueraDeAlcance("ajustar_nota");

        assertThatThrownBy(() -> servicio.calificarConIa(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("sin el permiso de ajustar notas no se llega a pedir nada")
    void sinPermisoNoSePideNada() {
        // El 403 de no tener el permiso sale del guardián tal cual, sin disfrazarse de 404:
        // no tener el permiso y no alcanzar la fila son cosas distintas.
        when(alcanceVacante.laPostulacionVisible(any(), eq(POSTULACION), eq("ajustar_nota")))
                .thenThrow(new AccessDeniedException("No tienes el permiso «ajustar_nota»"));

        assertThatThrownBy(() -> servicio.calificarConIa(QUIEN, POSTULACION))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("con alcance «sus vacantes», la de otro responsable tampoco existe")
    void conAlcanceDeSusVacantesLaDeOtroNoSeVe() {
        fueraDeAlcance("ajustar_nota");

        assertThatThrownBy(() -> servicio.calificarConIa(QUIEN, POSTULACION))
                .as("un 404 también significa «esto no es tuyo»")
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("con alcance «sus vacantes», la propia sí se puede pedir")
    void conAlcanceDeSusVacantesLaSuyaSiSePide() {
        hayPostulacion();
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
        hayIntento(Instant.now());
        hayRubricaCon("AGENTE");
        when(cola.encolarPruebaPuesto(POSTULACION)).thenReturn(false);

        CalificacionIaEncolada respuesta = servicio.calificarConIa(QUIEN, POSTULACION);

        assertThat(respuesta.estado())
                .as("la cola contesta false cuando ya hay un trabajo vivo; decir «encolada» dejaría "
                        + "esperando un resultado que nadie va a producir")
                .isEqualTo("SIN_CAMBIOS");
    }

    // ============ Lo que entregó ============

    @Test
    @DisplayName("salen todos los pedidos, entregados o no, en el orden de la versión")
    void salenTodosLosPedidos() {
        conPostulacionVisible("abrir_ficha_candidato");
        hayIntento(Instant.now());
        hayRequeridos();
        when(entregables.findByIntentoPruebaId(4L)).thenReturn(List.of(
                unEntregable(10L, 1L, null, "https://ejemplo.pe/campana", 1)));

        var salida = servicio.verEntregables(QUIEN_PUEDE_VER_EL_CONTENIDO, POSTULACION);

        // El segundo NO se entregó, y tiene que salir igual: un hueco se leería
        // como una lista más corta, que parece completa.
        assertThat(salida).hasSize(2);
        assertThat(salida.get(0).loEntrego()).isTrue();
        assertThat(salida.get(0).enlace()).isEqualTo("https://ejemplo.pe/campana");
        assertThat(salida.get(1).loEntrego()).isFalse();
        assertThat(salida.get(1).porQueNoSeVe()).contains("obligatorio");
    }

    @Test
    @DisplayName("de un entregable con varias entregas gana la última")
    void ganaLaUltimaVersion() {
        conPostulacionVisible("abrir_ficha_candidato");
        hayIntento(Instant.now());
        hayRequeridos();
        when(entregables.findByIntentoPruebaId(4L)).thenReturn(List.of(
                unEntregable(10L, 1L, null, "https://ejemplo.pe/vieja", 1),
                unEntregable(11L, 1L, null, "https://ejemplo.pe/nueva", 3),
                unEntregable(12L, 1L, null, "https://ejemplo.pe/media", 2)));

        var salida = servicio.verEntregables(QUIEN_PUEDE_VER_EL_CONTENIDO, POSTULACION);

        assertThat(salida.get(0).enlace()).isEqualTo("https://ejemplo.pe/nueva");
        assertThat(salida.get(0).version()).isEqualTo(3);
    }

    @Test
    @DisplayName("sin «descargar_entregables» se dice qué entregó, pero no se reparte el contenido")
    void sinPermisoNoViajaElContenido() {
        conPostulacionVisible("abrir_ficha_candidato");
        hayIntento(Instant.now());
        hayRequeridos();
        when(entregables.findByIntentoPruebaId(4L)).thenReturn(List.of(
                unEntregable(10L, 1L, 55L, null, 1)));

        var salida = servicio.verEntregables(QUIEN, POSTULACION);

        assertThat(salida.get(0).loEntrego()).isTrue();
        assertThat(salida.get(0).subidoEn()).isNotNull();
        // Ni el enlace ni el id del archivo: llegar al contenido pide el mismo
        // permiso que las dos rutas de archivo.
        assertThat(salida.get(0).enlace()).isNull();
        assertThat(salida.get(0).archivoId()).isNull();
        assertThat(salida.get(0).porQueNoSeVe()).contains("descargar_entregables");
        // Y ni siquiera se pregunta por el archivo.
        verifyNoInteractions(archivos);
    }

    @Test
    @DisplayName("un archivo borrado, sin ruta o inexistente se dice, no se ofrece")
    void elArchivoQueYaNoEsta() {
        conPostulacionVisible("abrir_ficha_candidato");
        hayIntento(Instant.now());
        hayRequeridos();
        when(entregables.findByIntentoPruebaId(4L)).thenReturn(List.of(
                unEntregable(10L, 1L, 55L, null, 1)));
        when(archivos.findById(55L)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.archivo.entity.Archivo.builder().id(55L).ruta("x").borradoEn(Instant.now()).build()));

        var salida = servicio.verEntregables(QUIEN_PUEDE_VER_EL_CONTENIDO, POSTULACION);

        assertThat(salida.get(0).archivoId()).isNull();
        assertThat(salida.get(0).porQueNoSeVe()).isEqualTo("El archivo ya no está guardado");
    }

    @Test
    @DisplayName("con cuestionario técnico la lista es vacía, y no se busca ningún intento")
    void elCuestionarioNoEntregaNada() {
        conPostulacionVisible("abrir_ficha_candidato");
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).instrumentoEtapaTecnica("CUESTIONARIO_TECNICO").build()));

        assertThat(servicio.verEntregables(QUIEN, POSTULACION)).isEmpty();

        // Vacío y no un 404: esa modalidad se contesta escribiendo.
        verifyNoInteractions(intentos);
    }

    @Test
    @DisplayName("una postulación fuera de alcance no existe para quien pregunta")
    void fueraDeAlcanceNoEnseñaEntregables() {
        fueraDeAlcance("abrir_ficha_candidato");

        assertThatThrownBy(() -> servicio.verEntregables(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(entregables);
    }

    // ============ Apoyo ============

    /** Dos pedidos: el primero se entrega en las pruebas, el segundo nunca. */
    private void hayRequeridos() {
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION_PLANTILLA))
                .thenReturn(List.of(
                        com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido.builder().id(1L).nombre("La campaña")
                                .detalle("Con su segmentación").formato("CUALQUIERA")
                                .esObligatorio(true).orden(1).build(),
                        com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido.builder().id(2L).nombre("El vídeo")
                                .detalle("Cuatro minutos").formato("ENLACE")
                                .esObligatorio(true).orden(2).build()));
    }

    private com.renaser.ai.ai_engine.prueba.entity.Entregable unEntregable(
            Long id, Long requeridoId, Long archivoId, String enlace, Integer version) {
        return com.renaser.ai.ai_engine.prueba.entity.Entregable.builder()
                .id(id).intentoPruebaId(4L).entregableRequeridoId(requeridoId)
                .archivoId(archivoId).enlace(enlace).version(version)
                .subidoEn(Instant.now()).build();
    }


    /** Lo que el guardián devuelve cuando la postulación es de esta empresa y se alcanza. */
    private void hayPostulacion() {
        when(alcanceVacante.laPostulacionVisible(any(), eq(POSTULACION), eq("ajustar_nota")))
                .thenReturn(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE)
                        .usuarioId(OTRO_USUARIO).estadoCodigo("PRUEBA_CALIFICANDO")
                        .build());
    }

    /** Lo mismo, para los caminos que miran otro permiso. */
    private void conPostulacionVisible(String permiso) {
        when(alcanceVacante.laPostulacionVisible(any(), eq(POSTULACION), eq(permiso)))
                .thenReturn(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE)
                        .usuarioId(OTRO_USUARIO).estadoCodigo("PRUEBA_CALIFICANDO").build());
    }

    /** Y lo que devuelve cuando no: el mismo 404 que si no existiera. */
    private void fueraDeAlcance(String permiso) {
        when(alcanceVacante.laPostulacionVisible(any(), eq(POSTULACION), eq(permiso)))
                .thenThrow(new ResourceNotFoundException("Postulación", "id", POSTULACION));
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

    // ============ Quién puede LEER el desglose ============

    /**
     * ⚠️ <b>Leer el desglose ya no pide el permiso de CORREGIRLO.</b>
     *
     * <p>Pedía {@code ajustar_nota}, que solo tienen Talento y Dirección. Responsable de Área
     * abría el embudo de su vacante con {@code ver_embudo}, veía la nota de cada candidato —y
     * ahora también las columnas de la rúbrica en la tabla— y al pulsar la fila se topaba con
     * un 403 sobre el desglose de esa misma nota.
     *
     * <p>Ahora pide {@code abrir_ficha_candidato}, que es exactamente esto —abrir la ficha de
     * alguien— y que ese rol ya tiene con alcance {@code SUS_VACANTES}. Es el mismo permiso
     * con el que {@code verEntregables} deja ver lo que el candidato entregó.
     *
     * <p><b>Escribir no se toca</b>: lo comprueba el test de abajo.
     */
    @Test
    @DisplayName("leer el desglose pide el permiso de la ficha, no el de corregir la nota")
    void verLasNotasPideElPermisoDeLaFicha() {
        conPostulacionVisible("abrir_ficha_candidato");
        hayIntento(Instant.now());
        hayRubricaCon("AGENTE");
        when(notasCriterio.findByPostulacionId(POSTULACION)).thenReturn(List.of());

        assertThat(servicio.verNotas(QUIEN, POSTULACION)).hasSize(1);

        verify(alcanceVacante).laPostulacionVisible(any(), eq(POSTULACION),
                eq("abrir_ficha_candidato"));
        verify(alcanceVacante, never()).laPostulacionVisible(any(), eq(POSTULACION),
                eq("ajustar_nota"));
    }

    // ============ Ver lo que contestó ============

    @Test
    @DisplayName("devuelve las preguntas en su orden, con lo que contestó a cada una")
    void devuelveLasRespuestasEnOrden() {
        conPostulacionVisible("abrir_ficha_candidato");
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                IntentoPrueba.builder().id(9L).postulacionId(POSTULACION)
                        .versionPlantillaPruebaId(VERSION_PLANTILLA).build()));
        when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION_PLANTILLA))
                .thenReturn(List.of(
                        PreguntaVersionPlantilla.builder().preguntaPruebaId(7L).build(),
                        PreguntaVersionPlantilla.builder().preguntaPruebaId(3L).build()));
        // A propósito devueltas al revés: el orden lo manda la plantilla, no el repositorio.
        when(preguntasCatalogo.findByIdIn(List.of(7L, 3L))).thenReturn(List.of(
                PreguntaPrueba.builder().id(3L).codigo("P2").orden(2).tipo("ABIERTA")
                        .enunciado("¿Cómo cuadras una caja?").build(),
                PreguntaPrueba.builder().id(7L).codigo("P1").orden(1).tipo("ABIERTA")
                        .enunciado("¿Cuántas sedes llevaste?").build()));
        when(respuestas.findByIntentoPruebaId(9L)).thenReturn(List.of(
                RespuestaPrueba.builder().preguntaPruebaId(7L).texto("Cuatro sedes").build()));

        var salida = servicio.verRespuestas(QUIEN, POSTULACION);

        assertThat(salida).hasSize(2);
        assertThat(salida.get(0).codigo()).as("primero el que la plantilla puso primero").isEqualTo("P1");
        assertThat(salida.get(0).respuesta()).isEqualTo("Cuatro sedes");
        assertThat(salida.get(1).codigo()).isEqualTo("P2");
        assertThat(salida.get(1).respuesta())
                .as("la que dejó en blanco sale igual, vacía: omitirla la haría invisible")
                .isNull();
    }

    @Test
    @DisplayName("sin intento no hay respuestas que enseñar")
    void sinIntentoNoHayRespuestas() {
        conPostulacionVisible("abrir_ficha_candidato");
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.verRespuestas(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("la de otra organización tampoco enseña respuestas")
    void lasRespuestasDeOtraOrganizacionNoSeVen() {
        fueraDeAlcance("abrir_ficha_candidato");

        assertThatThrownBy(() -> servicio.verRespuestas(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============ La fecha de cierre de UN candidato ============

    @Test
    @DisplayName("le fija la fecha y deja escrito de qué fecha venía")
    void fijaLaFechaYAudita() {
        // Relativas al reloj: definirPlazo rechaza una fecha ya pasada, así que un literal
        // futuro es una bomba de tiempo — pasa hasta el día que llega y revienta sin que
        // nadie haya tocado el código. «antes» es el valor que TENÍA, no el más temprano.
        Instant nueva = Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);
        Instant antes = nueva.plus(5, java.time.temporal.ChronoUnit.DAYS);
        conPostulacionVisible("mover_postulacion");
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
        conPostulacionVisible("mover_postulacion");
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
        conPostulacionVisible("mover_postulacion");
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
        when(criterios.findByVersionPlantillaPruebaIdOrderByOrden(VERSION_PLANTILLA))
                .thenReturn(rubrica);
    }
}
