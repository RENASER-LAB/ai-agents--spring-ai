package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Volver a entrar en la etapa de la prueba no puede reventar ni duplicar nada.
 *
 * <p>Pasa en el panel todos los días: se retrocede una postulación —para pedir un dato, para
 * revisar el Perfil Integral— y se la vuelve a avanzar. La segunda vez, crear el intento a
 * ciegas chocaba contra la clave única de {@code intento_prueba} y el panel devolvía «ya
 * existe un registro con postulacion_id = X»: un error que no dice qué pasó, deja al
 * candidato atascado en su etapa anterior y no tiene arreglo desde la pantalla.
 *
 * <p>Las tres cosas que este arreglo no puede romper:
 *
 * <ul>
 *   <li><b>Quien ya abrió su prueba se queda con la suya.</b> La versión se congela al
 *       empezar (RF-90): cambiársela sería moverle el enunciado a mitad de camino.
 *   <li><b>Quien no la ha abierto rinde la que la vacante tiene hoy.</b> Si no, cambiar la
 *       prueba de la vacante no le llegaría nunca y nadie lo vería.
 *   <li><b>{@code plazoPropio} no se toca.</b> Es la concesión de «a esta persona, más
 *       horas» (V32), y aquí es igual de fácil de borrar en silencio que en {@code iniciar}.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Volver a entrar en la prueba del puesto")
class VolverAEntrarALaPruebaTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long POSTULACION = 735L;
    private static final Long INTENTO = 90L;
    private static final Long VERSION_VIEJA = 21L;
    private static final Long VERSION_DE_HOY = 34L;

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
        lenient().when(intentos.save(any(IntentoPrueba.class))).thenAnswer(i -> i.getArgument(0));
    }

    private IntentoPrueba elQueYaTiene(IntentoPrueba intento) {
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento));
        return intento;
    }

    @Test
    @DisplayName("con un intento ya creado se reutiliza el suyo, no se crea otro")
    void reutilizaElIntentoQueYaTiene() {
        elQueYaTiene(IntentoPrueba.builder()
                .id(INTENTO).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION_DE_HOY)
                .build());

        Long id = servicio.crearAlEntrar(ORGANIZACION, POSTULACION, VERSION_DE_HOY, null);

        assertThat(id).isEqualTo(INTENTO);
        // Nada con id vacío: una fila nueva es justo lo que la clave única rechaza
        verify(intentos, never()).save(org.mockito.ArgumentMatchers.argThat(i -> i.getId() == null));
    }

    @Test
    @DisplayName("si todavía no la ha abierto, se le pone la prueba que la vacante rinde hoy")
    void alQueNoHaEmpezadoSeLeActualizaLaVersion() {
        Instant cierra = Instant.now().plus(5, ChronoUnit.DAYS);
        IntentoPrueba suyo = elQueYaTiene(IntentoPrueba.builder()
                .id(INTENTO).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION_VIEJA)
                .build());

        servicio.crearAlEntrar(ORGANIZACION, POSTULACION, VERSION_DE_HOY, cierra);

        assertThat(suyo.getVersionPlantillaPruebaId()).isEqualTo(VERSION_DE_HOY);
        assertThat(suyo.getVenceEn()).isEqualTo(cierra);
    }

    @Test
    @DisplayName("si ya la abrió, se le respeta la versión con la que empezó (RF-90)")
    void alQueYaEmpezoNoSeLeCambiaNada() {
        Instant empezo = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant vence = empezo.plus(90, ChronoUnit.MINUTES);
        IntentoPrueba suyo = elQueYaTiene(IntentoPrueba.builder()
                .id(INTENTO).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION_VIEJA)
                .iniciadoEn(empezo).venceEn(vence)
                .build());

        Long id = servicio.crearAlEntrar(ORGANIZACION, POSTULACION, VERSION_DE_HOY, null);

        assertThat(id).isEqualTo(INTENTO);
        assertThat(suyo.getVersionPlantillaPruebaId()).isEqualTo(VERSION_VIEJA);
        assertThat(suyo.getVenceEn()).isEqualTo(vence);
        verify(intentos, never()).save(any(IntentoPrueba.class));
    }

    @Test
    @DisplayName("a quien tiene su propia fecha concedida no se le mueve")
    void elPlazoPropioSobrevive() {
        Instant suya = Instant.now().plus(10, ChronoUnit.DAYS);
        IntentoPrueba suyo = elQueYaTiene(IntentoPrueba.builder()
                .id(INTENTO).postulacionId(POSTULACION).versionPlantillaPruebaId(VERSION_VIEJA)
                .plazoPropio(true).venceEn(suya)
                .build());

        servicio.crearAlEntrar(ORGANIZACION, POSTULACION, VERSION_DE_HOY,
                Instant.now().plus(2, ChronoUnit.DAYS));

        assertThat(suyo.getVenceEn()).isEqualTo(suya);
        assertThat(suyo.getVersionPlantillaPruebaId()).isEqualTo(VERSION_DE_HOY);
    }

    @Test
    @DisplayName("sin intento previo se crea el suyo, como siempre")
    void sinIntentoPrevioSeCrea() {
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        Instant cierra = Instant.now().plus(3, ChronoUnit.DAYS);

        servicio.crearAlEntrar(ORGANIZACION, POSTULACION, VERSION_DE_HOY, cierra);

        verify(intentos).save(org.mockito.ArgumentMatchers.argThat(i ->
                i.getId() == null
                        && POSTULACION.equals(i.getPostulacionId())
                        && VERSION_DE_HOY.equals(i.getVersionPlantillaPruebaId())
                        && cierra.equals(i.getVenceEn())));
    }
}
