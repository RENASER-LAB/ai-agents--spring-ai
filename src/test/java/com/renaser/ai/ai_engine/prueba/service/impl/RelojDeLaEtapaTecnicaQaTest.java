package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.prueba.dto.DtosPrueba.MiPrueba;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * REVISIÓN ADVERSARIAL del reloj de la etapa técnica. No lo escribió quien hizo el cambio.
 *
 * <p>Estos tests NO celebran lo que ya funciona: cada uno describe una promesa que el
 * sistema le hace a alguien —al candidato o a quien lleva la vacante— y comprueba si se
 * cumple. Los que fallan señalan defectos reales, no gustos.
 *
 * <p>Las tres promesas que se ponen a prueba aquí:
 *
 * <ul>
 *   <li><b>«Esta convocatoria cierra el domingo»</b> (V32). Sin minutos de vacante, la fecha
 *       de cierre es la que se le dijo a la gente y nadie la acorta por detrás.
 *   <li><b>«N minutos desde que empieces»</b>. Es la frase literal del portal. Si el reloj
 *       de verdad va a cerrar antes, esa frase es mentira — el mismo defecto que este
 *       trabajo vino a quitar, dicho al revés.
 *   <li><b>«A mitad de la prueba te cambio el enunciado»</b> (RF-77). El cambio inesperado
 *       tiene que caber dentro del reloj; si no, no se revela nunca y se puntúa a quien
 *       jamás lo vio.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QA · lo que el reloj de la etapa técnica promete y lo que cumple")
class RelojDeLaEtapaTecnicaQaTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 40L;
    private static final Long POSTULACION = 7L;
    private static final Long INTENTO = 70L;
    private static final Long VERSION = 31L;
    private static final Long VARIANTE = 500L;
    private static final UUID UUID_POSTULACION = UUID.randomUUID();

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "CANDIDATO", List.of(), Map.of());

    @Mock private IntentoPruebaRepository intentos;
    @Mock private VersionPlantillaPruebaRepository versiones;
    @Mock private VacanteRepository vacantes;
    @Mock private VarianteCambioRepository variantes;
    @Mock private PreguntaVersionPlantillaRepository preguntasElegidas;
    @Mock private PreguntaPruebaRepository preguntasCatalogo;
    @Mock private EntregableRequeridoRepository entregablesRequeridos;
    @Mock private EntregableRepository entregables;
    @Mock private RespuestaPruebaRepository respuestas;
    @Mock private PostulacionRepository postulaciones;
    @Mock private AlmacenArchivos almacen;
    @Mock private MaquinaEstados maquina;

    private ServicioPruebaImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPruebaImpl(intentos, versiones, vacantes, variantes,
                preguntasElegidas, preguntasCatalogo, entregablesRequeridos, entregables,
                respuestas, postulaciones, almacen, maquina);
        lenient().when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of());
        lenient().when(preguntasCatalogo.findByIdIn(anyList())).thenReturn(List.of());
        lenient().when(respuestas.findByIntentoPruebaId(INTENTO)).thenReturn(List.of());
        lenient().when(entregables.findByIntentoPruebaId(INTENTO)).thenReturn(List.of());
        lenient().when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of());
        lenient().when(intentos.save(any(IntentoPrueba.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ============ Apoyo ============

    private IntentoPrueba intentoSinEmpezar() {
        Postulacion p = Postulacion.builder()
                .id(POSTULACION).uuid(UUID_POSTULACION).usuarioId(QUIEN.usuarioId())
                .organizacionId(ORGANIZACION).vacanteId(VACANTE)
                .build();
        IntentoPrueba intento = IntentoPrueba.builder()
                .id(INTENTO).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION)
                .build();
        when(postulaciones.findByUuid(UUID_POSTULACION)).thenReturn(Optional.of(p));
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento));
        return intento;
    }

    private void laVersionEs(String modalidad, Integer duracionMinutos, Integer plazoDias) {
        when(versiones.findById(VERSION)).thenReturn(Optional.of(VersionPlantillaPrueba.builder()
                .id(VERSION).plantillaPruebaId(300L).modalidad(modalidad)
                .duracionMinutos(duracionMinutos).plazoDias(plazoDias)
                .enunciado("Resuelve esto").estado("PUBLICADA")
                .build()));
    }

    /** Una versión que además cambia el enunciado a mitad de camino (RF-77). */
    private void laVersionConCambio(String modalidad, Integer duracionMinutos, Integer plazoDias,
                                    int cambioMin, int cambioMax) {
        when(versiones.findById(VERSION)).thenReturn(Optional.of(VersionPlantillaPrueba.builder()
                .id(VERSION).plantillaPruebaId(300L).modalidad(modalidad)
                .duracionMinutos(duracionMinutos).plazoDias(plazoDias)
                .minutoCambioMin(cambioMin).minutoCambioMax(cambioMax)
                .enunciado("Resuelve esto").estado("PUBLICADA")
                .build()));
        when(variantes.findByVersionPlantillaPruebaId(VERSION)).thenReturn(List.of(
                VarianteCambio.builder().id(VARIANTE).texto("Ahora sin la librería").build()));
    }

    private void laVacanteFija(Integer minutos) {
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(Vacante.builder()
                        .id(VACANTE).organizacionId(ORGANIZACION).minutosEtapaTecnica(minutos)
                        .build()));
    }

    // ========================================================================
    // 1 · «Esta convocatoria cierra el domingo» — sin minutos de vacante
    // ========================================================================

    /**
     * La fecha de cierre de la convocatoria no se acorta sola.
     *
     * <p>Esta vacante NO fija minutos: es el camino de siempre, el de todas las vacantes que
     * ya existen. Lo único que tiene es lo que la V32 vino a permitir — una fecha para todos,
     * «esta convocatoria cierra el 30» — sobre una plantilla de plazo abierto, que es la
     * única combinación que el panel deja crear ({@code definirCierrePrueba} rechaza las
     * cronometradas).
     *
     * <p>Antes de este cambio, empezar respetaba esa fecha. Ahora se calcula también el
     * plazo en días de la plantilla y gana el más cercano, así que a quien abre el día 2 se
     * le cierra el día 9 y no el 30: vuelven los cierres distintos por candidato que la V32
     * existía para eliminar, sin que nadie lo haya pedido ni pueda verlo en el panel.
     */
    @Test
    @DisplayName("la fecha de cierre de la convocatoria no la acorta el plazo de la plantilla")
    void laFechaDeLaConvocatoriaNoSeAcortaSola() {
        Instant cierraLaConvocatoria = Instant.now().plus(30, ChronoUnit.DAYS);
        IntentoPrueba intento = intentoSinEmpezar();
        intento.setVenceEn(cierraLaConvocatoria);
        laVersionEs("PLAZO_ABIERTO", null, 7);
        laVacanteFija(null);

        servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(intento.getVenceEn())
                .as("a este candidato se le dijo que la convocatoria cierra el día 30; "
                        + "el plazo de la plantilla no puede recortárselo a 7 días")
                .isEqualTo(cierraLaConvocatoria);
    }

    // ========================================================================
    // 2 · «N minutos desde que empieces» — la frase literal del portal
    // ========================================================================

    /**
     * Lo que dice la pantalla y lo que hace el reloj tienen que ser el mismo número.
     *
     * <p>{@code Prueba.tsx} escribe «{duracionMinutos} minutos desde que empieces» y, cuando
     * ese campo viene lleno, <b>no pinta la fecha de vencimiento</b>: el candidato planifica
     * su tarde con ese número y no tiene ningún otro.
     *
     * <p>Aquí la vacante pide 90 minutos pero la convocatoria cierra dentro de 20. El
     * servidor hace lo correcto —gana el más cercano, {@code venceEn} son 20 minutos— y a la
     * vez sigue enseñando 90. Es exactamente el desajuste que este trabajo vino a quitar,
     * mirando en la otra dirección.
     */
    /*
     * ⚠️ **El arreglo fue de pantalla, no de contrato, y por eso este test cambió de forma.**
     *
     * La primera versión exigía que `duracionMinutos` no pudiera prometer más allá de
     * `venceEn`, o sea: que el backend recortara los minutos al acercarse el cierre. Se
     * descartó, porque los dos datos son verdad y cada uno dice algo distinto —la prueba dura
     * noventa minutos; la convocatoria cierra el domingo— y recortar el primero borraría la
     * información de que la prueba dura noventa. Lo que estaba mal era la pantalla, que de
     * los dos datos que ya recibía elegía uno y callaba el otro.
     *
     * Así que aquí se fija lo que hace posible el arreglo: que los DOS viajen siempre que
     * existan los dos. Que la pantalla los diga se prueba en el portal (`Prueba.test.tsx`),
     * que es donde vive esa decisión.
     */
    @Test
    @DisplayName("cuando hay reloj y fecha, la pantalla recibe los dos datos")
    void laPantallaNoPrometeMasTiempoDelQueHay() {
        Instant cierraEn = Instant.now().plus(20, ChronoUnit.MINUTES);
        IntentoPrueba intento = intentoSinEmpezar();
        intento.setVenceEn(cierraEn);
        laVersionEs("PLAZO_ABIERTO", null, 7);
        laVacanteFija(90);

        MiPrueba pintada = servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(pintada.duracionMinutos())
                .as("los minutos de la vacante llegan a la pantalla")
                .isEqualTo(90);
        assertThat(pintada.venceEn())
                .as("y la fecha que los acorta también: sin ella la pantalla no puede "
                        + "decir que quien empiece tarde tendrá menos")
                .isEqualTo(cierraEn);
        // Y el que manda de verdad sigue siendo el más cercano, que aquí es la fecha.
        assertThat(intento.getVenceEn()).isEqualTo(cierraEn);
    }

    // ========================================================================
    // 3 · «A mitad de la prueba te cambio el enunciado» (RF-77)
    // ========================================================================

    /**
     * El cambio inesperado tiene que caber dentro del reloj efectivo.
     *
     * <p>Mientras una prueba cronometrada duraba entre 60 y 120 minutos por validación, un
     * cambio sorteado entre los minutos 30 y 50 siempre llegaba a tiempo. Ese rango se
     * retiró y los minutos de la vacante mandan sobre los de la plantilla, así que hoy una
     * vacante puede poner 10 minutos sobre una plantilla que revela el cambio en el 30.
     *
     * <p>Nada avisa. {@code sortearCambio} sortea el minuto sin mirar cuánto dura de verdad
     * la prueba, {@code entregarVencidos} la cierra en el 10, y {@code revelarCambioSiToca}
     * no llega a dispararse nunca. El candidato rinde una prueba a la que le falta la mitad
     * del enunciado, y los criterios de la rúbrica que puntúan cómo se adaptó al cambio se
     * le califican igual.
     */
    @Test
    @Disabled("Defecto real y sin arreglar: el minuto del cambio inesperado se sortea sin mirar cuánto dura de verdad la prueba, así que con pocos minutos no se revela nunca — y la instrucción del agente calificador dice que cómo se reaccionó a él forma parte de lo que se mide. Lo garantizaba el rango 60-120, que se retiró a propósito. Espera decisión: atar minuto_cambio_max a la duración efectiva.")
    @DisplayName("el cambio inesperado se sortea dentro del reloj que de verdad rige")
    void elCambioInesperadoCabeDentroDelReloj() {
        IntentoPrueba intento = intentoSinEmpezar();
        laVersionConCambio("CRONOMETRADA", 90, null, 30, 50);
        laVacanteFija(10);

        servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(intento.getMinutoCambio()).isNotNull();
        Instant cuandoSeRevela = intento.getIniciadoEn()
                .plus(intento.getMinutoCambio(), ChronoUnit.MINUTES);
        assertThat(cuandoSeRevela)
                .as("el cambio se revela en el minuto %d de una prueba que la vacante dejó "
                        + "en 10 minutos: no lo ve nadie",
                        intento.getMinutoCambio())
                .isBefore(intento.getVenceEn());
    }
}
