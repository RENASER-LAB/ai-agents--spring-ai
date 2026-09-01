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
 * Cuánto tiempo tiene de verdad el candidato para su prueba del puesto.
 *
 * <p>Esto protege la decisión que arregló un campo mudo: «Cuánto tiempo tendrá» se pintaba en
 * la ficha de la vacante, se guardaba y se auditaba, pero <b>solo lo leía el cuestionario
 * técnico</b>. Con la prueba del puesto no viajaba a ninguna parte: el candidato tenía el
 * tiempo de la plantilla y nadie se enteraba.
 *
 * <p>Las cuatro cosas que hay que no romper, en orden de lo fácil que es romperlas:
 *
 * <ul>
 *   <li><b>{@code plazoPropio} es sagrado.</b> Marca a quien se le concedió su propia fecha a
 *       mano (V32). Los minutos de la vacante no la pisan — es la regresión más barata de
 *       introducir aquí, porque no la nota nadie hasta que un candidato pierde sus horas.
 *   <li><b>Gana el plazo más cercano</b>, no el que estuviera puesto.
 *   <li><b>Sin minutos de vacante, todo se comporta exactamente igual que antes.</b> Es lo
 *       que protege a todas las vacantes que ya existen.
 *   <li><b>El número que se pinta es el que se aplica.</b> Enseñar 60 y cerrar a los 90 es la
 *       misma mentira, dicha al revés.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cuánto dura de verdad la prueba del puesto")
class ServicioPruebaImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 40L;
    private static final Long POSTULACION = 7L;
    private static final Long INTENTO = 70L;
    private static final Long VERSION = 31L;
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
        // Lo que `pintar` consulta para armar la pantalla. Aquí no se mira nada de eso: lo
        // que se prueba es el reloj, y sin estos dobles ni se llega a la aserción.
        lenient().when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of());
        lenient().when(preguntasCatalogo.findByIdIn(anyList())).thenReturn(List.of());
        lenient().when(respuestas.findByIntentoPruebaId(INTENTO)).thenReturn(List.of());
        lenient().when(entregables.findByIntentoPruebaId(INTENTO)).thenReturn(List.of());
        lenient().when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of());
        lenient().when(intentos.save(any(IntentoPrueba.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    // ============ Apoyo ============

    /** El intento de este candidato, sin empezar y sin fecha, listo para que `iniciar` lo mire. */
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

    private void laVacanteFija(Integer minutos) {
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(Vacante.builder()
                        .id(VACANTE).organizacionId(ORGANIZACION).minutosEtapaTecnica(minutos)
                        .build()));
    }

    /** Minutos entre dos instantes, redondeando: los tests no compiten con el reloj. */
    private long minutosEntre(Instant desde, Instant hasta) {
        return Math.round(ChronoUnit.SECONDS.between(desde, hasta) / 60.0);
    }

    // ============ Los minutos de la vacante rigen ============

    @Test
    @DisplayName("los minutos de la vacante mandan sobre los de la plantilla")
    void losMinutosDeLaVacanteMandan() {
        IntentoPrueba intento = intentoSinEmpezar();
        laVersionEs("CRONOMETRADA", 60, null);
        laVacanteFija(90);

        MiPrueba pintada = servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(minutosEntre(intento.getIniciadoEn(), intento.getVenceEn())).isEqualTo(90);
        // ⚠️ Y el candidato ve 90, no 60. Si esto se cae, la pantalla volvió a mentir.
        assertThat(pintada.duracionMinutos()).isEqualTo(90);
    }

    @Test
    @DisplayName("se leen al empezar, no al crear: corregirlas alcanza a quien no ha entrado")
    void seLeenAlEmpezar() {
        // El intento nació hace días, cuando la vacante decía otra cosa. Nada de eso quedó
        // guardado en la fila: lo que rige es lo que la vacante diga AHORA.
        IntentoPrueba intento = intentoSinEmpezar();
        intento.setCreadoEn(Instant.now().minus(3, ChronoUnit.DAYS));
        laVersionEs("CRONOMETRADA", 60, null);
        laVacanteFija(120);

        servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(minutosEntre(intento.getIniciadoEn(), intento.getVenceEn())).isEqualTo(120);
    }

    @Test
    @DisplayName("plazo abierto con minutos de vacante: se vuelve cronometrada de hecho")
    void plazoAbiertoConMinutosSeCronometra() {
        // No hay ninguna rama que diga esto: sale de que los minutos se miran antes que los
        // días. Si un día alguien invierte ese orden, este test lo cuenta.
        IntentoPrueba intento = intentoSinEmpezar();
        laVersionEs("PLAZO_ABIERTO", null, 7);
        laVacanteFija(90);

        MiPrueba pintada = servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(minutosEntre(intento.getIniciadoEn(), intento.getVenceEn())).isEqualTo(90);
        assertThat(pintada.duracionMinutos()).isEqualTo(90);
    }

    // ============ Gana el plazo más cercano ============

    @Test
    @DisplayName("con fecha de cierre lejana, el reloj de la prueba la acerca")
    void elRelojAcercaLaFechaLejana() {
        // Era el fallo: tener fecha hacía salir sin mirar el reloj, así que una prueba de
        // noventa minutos abierta el lunes duraba hasta el domingo.
        IntentoPrueba intento = intentoSinEmpezar();
        intento.setVenceEn(Instant.now().plus(7, ChronoUnit.DAYS));
        laVersionEs("CRONOMETRADA", 60, null);
        laVacanteFija(90);

        servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(minutosEntre(intento.getIniciadoEn(), intento.getVenceEn())).isEqualTo(90);
    }

    @Test
    @DisplayName("con la vacante cerrando antes, gana la vacante: nadie entrega tarde")
    void laFechaCercanaGanaAlReloj() {
        Instant cierraEn = Instant.now().plus(20, ChronoUnit.MINUTES);
        IntentoPrueba intento = intentoSinEmpezar();
        intento.setVenceEn(cierraEn);
        laVersionEs("CRONOMETRADA", 60, null);
        laVacanteFija(90);

        servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(intento.getVenceEn()).isEqualTo(cierraEn);
    }

    // ============ plazoPropio es sagrado ============

    @Test
    @DisplayName("⚠️ a quien se le concedieron más horas a mano, no se le tocan")
    void elPlazoPropioNoSePisa() {
        // La regresión más barata de introducir aquí y la más cara de notar: la concesión
        // desaparece en silencio y el candidato pierde las horas que alguien le dio (V32).
        Instant suya = Instant.now().plus(2, ChronoUnit.DAYS);
        IntentoPrueba intento = intentoSinEmpezar();
        intento.setPlazoPropio(true);
        intento.setVenceEn(suya);
        laVersionEs("CRONOMETRADA", 60, null);
        laVacanteFija(30);

        servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(intento.getVenceEn()).isEqualTo(suya);
    }

    @Test
    @DisplayName("marcado pero sin fecha que proteger, se calcula como con cualquiera")
    void plazoPropioSinFechaSeCalcula() {
        // Respetar la marca sin fecha sería un plazo infinito, y eso no lo concedió nadie.
        IntentoPrueba intento = intentoSinEmpezar();
        intento.setPlazoPropio(true);
        laVersionEs("CRONOMETRADA", 60, null);
        laVacanteFija(90);

        servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(intento.getVenceEn()).isNotNull();
        assertThat(minutosEntre(intento.getIniciadoEn(), intento.getVenceEn())).isEqualTo(90);
    }

    // ============ Sin minutos de vacante, todo igual que antes ============

    @Test
    @DisplayName("sin minutos de vacante, una cronometrada dura lo que diga su plantilla")
    void sinMinutosLaCronometradaNoCambia() {
        IntentoPrueba intento = intentoSinEmpezar();
        laVersionEs("CRONOMETRADA", 60, null);
        laVacanteFija(null);

        MiPrueba pintada = servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(minutosEntre(intento.getIniciadoEn(), intento.getVenceEn())).isEqualTo(60);
        assertThat(pintada.duracionMinutos()).isEqualTo(60);
    }

    @Test
    @DisplayName("sin minutos de vacante, una de plazo abierto sigue contando días")
    void sinMinutosElPlazoAbiertoNoCambia() {
        IntentoPrueba intento = intentoSinEmpezar();
        laVersionEs("PLAZO_ABIERTO", null, 7);
        laVacanteFija(null);

        MiPrueba pintada = servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(ChronoUnit.DAYS.between(intento.getIniciadoEn(), intento.getVenceEn()))
                .isEqualTo(7);
        // Sin cronómetro no hay número de minutos que enseñar: la pantalla dice la fecha.
        assertThat(pintada.duracionMinutos()).isNull();
    }

    @Test
    @DisplayName("una vacante que ya no existe no impide terminar la prueba")
    void sinVacanteSeSigueRindiendo() {
        IntentoPrueba intento = intentoSinEmpezar();
        laVersionEs("CRONOMETRADA", 60, null);
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION))
                .thenReturn(Optional.empty());

        servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(minutosEntre(intento.getIniciadoEn(), intento.getVenceEn())).isEqualTo(60);
    }

    // ============ Empezar dos veces no reinicia el reloj ============

    @Test
    @DisplayName("volver a entrar no recalcula nada: el reloj se fijó al empezar")
    void volverAEntrarNoReiniciaElReloj() {
        // Sin esto, cada visita a la pantalla regalaría el plazo entero otra vez —y borraría
        // los minutos extra que el cambio inesperado ya hubiera sumado.
        Instant fijado = Instant.now().plus(10, ChronoUnit.MINUTES);
        IntentoPrueba intento = intentoSinEmpezar();
        intento.setIniciadoEn(Instant.now().minus(80, ChronoUnit.MINUTES));
        intento.setVenceEn(fijado);
        laVersionEs("CRONOMETRADA", 60, null);
        laVacanteFija(90);

        servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(intento.getVenceEn()).isEqualTo(fijado);
    }

    @Test
    @DisplayName("plazo abierto con minutos: la pantalla dice «cronometrada», no «plazo abierto»")
    void laModalidadQueSePintaEsLaEfectiva() {
        // El lateral del portal pinta la duracion y la modalidad una encima de otra. Decir
        // «90 minutos» y debajo «PLAZO_ABIERTO» es la misma contradiccion escrita dos veces.
        intentoSinEmpezar();
        laVersionEs("PLAZO_ABIERTO", null, 7);
        laVacanteFija(90);

        MiPrueba pintada = servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(pintada.modalidad()).isEqualTo("CRONOMETRADA");
    }

    @Test
    @DisplayName("sin minutos de vacante, la modalidad es la de la plantilla y no se toca")
    void sinMinutosLaModalidadNoCambia() {
        intentoSinEmpezar();
        laVersionEs("PLAZO_ABIERTO", null, 7);
        laVacanteFija(null);

        MiPrueba pintada = servicio.iniciar(QUIEN, UUID_POSTULACION);

        assertThat(pintada.modalidad()).isEqualTo("PLAZO_ABIERTO");
    }

    // ============ Los minutos extra se suman ENCIMA ============

    @Test
    @DisplayName("el cambio inesperado suma sus minutos extra sobre los de la vacante")
    void losMinutosExtraSeSumanEncima() {
        // Decision explicita: vacante 90 + 10 extra de la plantilla = 100. Los extra son el
        // tiempo para adaptarse al cambio, no parte del reloj original, asi que no compiten
        // con los minutos de la vacante: se suman a lo que haya.
        Instant iniciado = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant vence = iniciado.plus(90, ChronoUnit.MINUTES);
        IntentoPrueba intento = intentoSinEmpezar();
        intento.setIniciadoEn(iniciado);
        intento.setVenceEn(vence);
        // Ya le toco el cambio hace rato y aun no se le ha enseñado: `pintar` lo revela.
        intento.setVarianteCambioId(500L);
        intento.setMinutoCambio(10);
        when(versiones.findById(VERSION)).thenReturn(Optional.of(VersionPlantillaPrueba.builder()
                .id(VERSION).plantillaPruebaId(300L).modalidad("CRONOMETRADA")
                .duracionMinutos(60).minutosExtra(10).estado("PUBLICADA")
                .build()));
        laVacanteFija(90);
        when(variantes.findById(500L)).thenReturn(Optional.of(
                VarianteCambio.builder().id(500L).texto("Ahora hazlo sin la librería").build()));

        servicio.ver(QUIEN, UUID_POSTULACION);

        assertThat(minutosEntre(iniciado, intento.getVenceEn())).isEqualTo(100);
    }

    @Test
    @DisplayName("mirar la prueba sin empezarla ya enseña los minutos efectivos")
    void verEnsenaLosMinutosEfectivos() {
        // La portada la lee quien todavía no ha pulsado «empezar»: si ahí dice 60 y al
        // entrar son 90, el candidato planificó con el número equivocado.
        intentoSinEmpezar();
        laVersionEs("CRONOMETRADA", 60, null);
        laVacanteFija(90);

        MiPrueba pintada = servicio.ver(QUIEN, UUID_POSTULACION);

        assertThat(pintada.duracionMinutos()).isEqualTo(90);
        assertThat(pintada.estadoIntento()).isEqualTo("PENDIENTE");
    }
}
