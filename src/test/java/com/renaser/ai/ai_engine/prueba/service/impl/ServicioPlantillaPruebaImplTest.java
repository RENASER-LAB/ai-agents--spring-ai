package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Publicar una prueba que es puro cuestionario.
 *
 * <p>La cuota de RF-83 —8 a 10 universales, 3 a 5 específicas— existe para las preguntas
 * con que el candidato defiende lo que produjo. Una versión sin entregables no produce
 * nada: sus preguntas SON la prueba (el cuestionario técnico de Administrador tiene 20 y
 * ninguna es universal). Lo que se protege aquí es el límite exacto de esa excepción:
 *
 * <ul>
 *   <li>que un cuestionario de 20 preguntas propias se pueda publicar,
 *   <li>que un cuestionario vacío no pase — sin preguntas ni entregables no hay prueba,
 *   <li>y que a la prueba con entregables la cuota le siga rigiendo igual que siempre.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Publicar una prueba tipo cuestionario")
class ServicioPlantillaPruebaImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long PLANTILLA = 6L;
    private static final Long VERSION = 61L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private PlantillaPruebaRepository plantillas;
    @Mock private VersionPlantillaPruebaRepository versiones;
    @Mock private VarianteCambioRepository variantes;
    @Mock private PreguntaPruebaRepository preguntasCatalogo;
    @Mock private PreguntaVersionPlantillaRepository preguntasElegidas;
    @Mock private EntregableRequeridoRepository entregablesRequeridos;
    @Mock private CriterioRepository criterios;
    @Mock private ServicioAuditoria auditoria;
    @Mock private com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento dueno;

    private ServicioPlantillaPruebaImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPlantillaPruebaImpl(plantillas, versiones, variantes,
                preguntasCatalogo, preguntasElegidas, entregablesRequeridos, criterios,
                auditoria, dueno);
        // En estas pruebas la organizacion no personaliza nada: el resolutor contesta
        // que el dueño de todo instrumento es ella misma (aqui hace de plataforma).
        org.mockito.Mockito.lenient()
                .when(dueno.duenoDe(org.mockito.ArgumentMatchers.eq(ORGANIZACION),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(ORGANIZACION);
    }

    private VersionPlantillaPrueba versionEnBorrador() {
        VersionPlantillaPrueba version = VersionPlantillaPrueba.builder()
                .id(VERSION).plantillaPruebaId(PLANTILLA).version(1)
                .modalidad("PLAZO_ABIERTO").plazoDias(7).estado("BORRADOR")
                .build();
        when(versiones.findById(VERSION)).thenReturn(Optional.of(version));
        when(plantillas.findByIdAndOrganizacionId(PLANTILLA, ORGANIZACION))
                .thenReturn(Optional.of(PlantillaPrueba.builder()
                        .id(PLANTILLA).organizacionId(ORGANIZACION).build()));
        return version;
    }

    private List<PreguntaVersionPlantilla> preguntasElegidas(int cuantas) {
        return IntStream.rangeClosed(1, cuantas)
                .mapToObj(i -> PreguntaVersionPlantilla.builder()
                        .versionPlantillaPruebaId(VERSION).preguntaPruebaId((long) i).orden(i)
                        .build())
                .toList();
    }

    private void rubricaQueSuma100() {
        when(criterios.findByVersionPlantillaPruebaId(VERSION)).thenReturn(List.of(
                Criterio.builder().codigo("CAJA").puntos(BigDecimal.valueOf(60)).build(),
                Criterio.builder().codigo("SEDES").puntos(BigDecimal.valueOf(40)).build()));
    }

    @Test
    @DisplayName("un cuestionario de 20 preguntas propias, sin entregables, se publica")
    void unCuestionarioDe20SePublica() {
        VersionPlantillaPrueba version = versionEnBorrador();
        when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(preguntasElegidas(20));
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of());
        rubricaQueSuma100();

        servicio.publicarVersion(QUIEN, VERSION);

        assertThat(version.getEstado()).isEqualTo("PUBLICADA");
        verify(versiones).save(version);
    }

    @Test
    @DisplayName("un cuestionario sin ninguna pregunta no se publica")
    void unCuestionarioVacioNoPasa() {
        versionEnBorrador();
        when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of());
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of());

        assertThatThrownBy(() -> servicio.publicarVersion(QUIEN, VERSION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos una pregunta");
    }

    @Test
    @DisplayName("con entregables, la cuota de universales y específicas sigue rigiendo")
    void conEntregablesLaCuotaSigue() {
        versionEnBorrador();
        when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(preguntasElegidas(20));
        when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                .thenReturn(List.of(EntregableRequerido.builder()
                        .versionPlantillaPruebaId(VERSION).nombre("El documento").orden(1)
                        .build()));
        // Veinte específicas y ni una universal: exactamente el cuestionario, pero con entrega
        when(preguntasCatalogo.findByIdIn(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(IntStream.rangeClosed(1, 20)
                        .mapToObj(i -> PreguntaPrueba.builder()
                                .id((long) i).codigo("Q%02d".formatted(i))
                                .tipo("ESPECIFICA").orden(i)
                                .build())
                        .toList());

        assertThatThrownBy(() -> servicio.publicarVersion(QUIEN, VERSION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("universales");
    }
}
