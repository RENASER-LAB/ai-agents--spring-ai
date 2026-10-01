package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.service.impl.CopiadorDeInstrumentosImpl;
import com.renaser.ai.ai_engine.perfilintegral.entity.CuotaPlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.CuotaPlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La copia que ejecuta «encender una bandera».
 *
 * <p>Desde la V67 solo se copian los pesos y las plantillas de evaluación: el banco y las
 * pruebas ya no se personalizan (decisión 13) y su copia se retiró.
 *
 * <p>Lo que estas pruebas persiguen es que la copia no pierda filas por el camino: el
 * copiador devuelve conteos por tabla y aquí se comparan contra lo que había. Una copia
 * incompleta no revienta — se nota semanas después, cuando a un candidato le toca una
 * pregunta cuya clave no viajó.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El copiador de instrumentos")
class CopiadorDeInstrumentosTest {

    private static final Long PLATAFORMA = 1L;
    private static final Long EMPRESA = 2L;

    @Mock private DuenoDelInstrumento resolutor;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private PlantillaEvaluacionRepository plantillasEvaluacion;
    @Mock private CuotaPlantillaEvaluacionRepository cuotas;
    @Mock private JdbcTemplate jdbc;

    private CopiadorDeInstrumentosImpl copiador;

    @BeforeEach
    void armar() {
        copiador = new CopiadorDeInstrumentosImpl(resolutor, versionesPesos,
                plantillasEvaluacion, cuotas, jdbc);
        lenient().when(resolutor.plataforma())
                .thenReturn(Organizacion.builder().id(PLATAFORMA).esPlataforma(true).build());
    }

    /** Asigna ids crecientes a lo que se guarde, como haría la base. */
    private <T> void alGuardarAsignarId(org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                                        java.util.function.BiConsumer<T, Long> ponerId) {
        AtomicLong siguiente = new AtomicLong(100);
        lenient().when(repo.save(any())).thenAnswer(inv -> {
            T entidad = inv.getArgument(0);
            ponerId.accept(entidad, siguiente.getAndIncrement());
            return entidad;
        });
    }

    @Test
    @DisplayName("Los pesos copian la última publicada con sus cuatro repartos, estilo V17")
    void losPesosCopianSusCuatroRepartos() {
        when(versionesPesos.findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(PLATAFORMA, "PUBLICADA"))
                .thenReturn(Optional.of(VersionPesos.builder()
                        .id(50L).organizacionId(PLATAFORMA).etiqueta("v3 hito 3")
                        .estado("PUBLICADA").build()));
        alGuardarAsignarId(versionesPesos, VersionPesos::setId);
        when(jdbc.update(contains("peso_etapa"), anyLong(), eq(50L))).thenReturn(5);
        when(jdbc.update(contains("peso_componente_perfil"), anyLong(), eq(50L))).thenReturn(2);
        when(jdbc.update(contains("peso_dimension"), anyLong(), eq(50L))).thenReturn(36);
        when(jdbc.update(contains("peso_criterio"), anyLong(), eq(50L))).thenReturn(24);

        Map<String, Integer> conteos = copiador.copiarPesos(EMPRESA);

        assertThat(conteos).containsEntry("version_pesos", 1)
                .containsEntry("peso_etapa", 5)
                .containsEntry("peso_componente_perfil", 2)
                .containsEntry("peso_dimension", 36)
                .containsEntry("peso_criterio", 24);

        ArgumentCaptor<VersionPesos> version = ArgumentCaptor.forClass(VersionPesos.class);
        verify(versionesPesos).save(version.capture());
        assertThat(version.getValue().getOrganizacionId()).isEqualTo(EMPRESA);
        assertThat(version.getValue().getCopiadaDeVersionId()).isEqualTo(50L);
        assertThat(version.getValue().getEstado()).isEqualTo("PUBLICADA");
    }

    @Test
    @DisplayName("Las plantillas de evaluación copian solo las publicadas, con sus cuotas")
    void lasPlantillasCopianSoloLasPublicadas() {
        PlantillaEvaluacion publicada = PlantillaEvaluacion.builder()
                .id(60L).organizacionId(PLATAFORMA).nombre("Operativo v1")
                .nivelPuestoCodigo("OPERATIVO").version(1).estado("PUBLICADA")
                .minutosObjetivo(45).vigenciaMeses(6).build();
        PlantillaEvaluacion borrador = PlantillaEvaluacion.builder()
                .id(61L).organizacionId(PLATAFORMA).nombre("A medias").estado("BORRADOR").build();
        when(plantillasEvaluacion.findByOrganizacionIdOrderByCreadoEnDesc(PLATAFORMA))
                .thenReturn(List.of(publicada, borrador));
        alGuardarAsignarId(plantillasEvaluacion, PlantillaEvaluacion::setId);
        alGuardarAsignarId(cuotas, CuotaPlantillaEvaluacion::setId);
        when(cuotas.findByPlantillaEvaluacionId(60L)).thenReturn(List.of(
                CuotaPlantillaEvaluacion.builder().id(70L).plantillaEvaluacionId(60L)
                        .tipoBanco("NIVEL").cantidadMin(90).cantidadMax(90).build()));

        Map<String, Integer> conteos = copiador.copiarPlantillasEvaluacion(EMPRESA);

        // El borrador no viaja: nunca circuló y no hay con qué evaluar en él
        assertThat(conteos).containsEntry("plantilla_evaluacion", 1)
                .containsEntry("cuota_plantilla_evaluacion", 1);
    }
}
