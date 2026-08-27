package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.service.impl.CopiadorDeInstrumentosImpl;
import com.renaser.ai.ai_engine.perfilintegral.entity.CuotaPlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.ParConsistencia;
import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CampoCasoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.CuotaPlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ParConsistenciaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RangoPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La copia que ejecuta «encender una bandera».
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
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private RangoPreguntaRepository rangos;
    @Mock private CampoCasoRepository camposCaso;
    @Mock private ParConsistenciaRepository pares;
    @Mock private PreguntaDimensionRepository preguntaDimensiones;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private PlantillaEvaluacionRepository plantillasEvaluacion;
    @Mock private CuotaPlantillaEvaluacionRepository cuotas;
    @Mock private PlantillaPruebaRepository plantillasPrueba;
    @Mock private VersionPlantillaPruebaRepository versionesPrueba;
    @Mock private VarianteCambioRepository variantes;
    @Mock private PreguntaVersionPlantillaRepository preguntasElegidas;
    @Mock private EntregableRequeridoRepository entregablesRequeridos;
    @Mock private CriterioRepository criterios;
    @Mock private JdbcTemplate jdbc;

    private CopiadorDeInstrumentosImpl copiador;

    @BeforeEach
    void armar() {
        copiador = new CopiadorDeInstrumentosImpl(resolutor, versionesBanco, preguntas, opciones,
                rangos, camposCaso, pares, preguntaDimensiones, versionesPesos,
                plantillasEvaluacion, cuotas, plantillasPrueba, versionesPrueba, variantes,
                preguntasElegidas, entregablesRequeridos, criterios, jdbc);
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
    @DisplayName("El banco copia versión, preguntas, opciones, pares y reglas, y lo cuenta todo")
    void elBancoCopiaCompleto() {
        VersionBanco publicada = VersionBanco.builder()
                .id(10L).organizacionId(PLATAFORMA).tipoBanco("NIVEL")
                .nivelPuestoCodigo("OPERATIVO").etiqueta("v3").estado("PUBLICADA")
                .publicadaEn(Instant.now()).build();
        when(versionesBanco.findByOrganizacionIdAndEstado(PLATAFORMA, "PUBLICADA"))
                .thenReturn(List.of(publicada));
        alGuardarAsignarId(versionesBanco, VersionBanco::setId);
        alGuardarAsignarId(preguntas, Pregunta::setId);
        alGuardarAsignarId(opciones, Opcion::setId);
        alGuardarAsignarId(pares, ParConsistencia::setId);

        Pregunta p1 = Pregunta.builder().id(21L).versionBancoId(10L).codigo("O01").tipo("SJT-R")
                .enunciado("uno").esPuntuable(true).orden(1).build();
        Pregunta p2 = Pregunta.builder().id(22L).versionBancoId(10L).codigo("O02").tipo("PC")
                .enunciado("dos").esPuntuable(false).orden(2).build();
        when(preguntas.findByVersionBancoIdOrderByOrden(10L)).thenReturn(List.of(p1, p2));
        when(opciones.findByPreguntaIdIn(List.of(21L, 22L))).thenReturn(List.of(
                Opcion.builder().id(31L).preguntaId(21L).letra("A").texto("a").build(),
                Opcion.builder().id(32L).preguntaId(21L).letra("B").texto("b").build(),
                Opcion.builder().id(33L).preguntaId(22L).letra("A").texto("c").build()));
        when(pares.findByVersionBancoId(10L)).thenReturn(List.of(
                ParConsistencia.builder().id(41L).versionBancoId(10L)
                        .preguntaAId(21L).preguntaBId(22L).build()));
        when(preguntaDimensiones.findByPreguntaIdIn(List.of(21L, 22L))).thenReturn(List.of());
        when(rangos.findByPreguntaIdOrderByOrden(anyLong())).thenReturn(List.of());
        when(camposCaso.findByPreguntaIdOrderByOrden(anyLong())).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(10L))).thenReturn(List.of(
                Map.of("opcion_id", 31L, "dimension_codigo", "LIDERAZGO", "incremento", 2)));
        when(jdbc.update(contains("multiplicador_bloque"), anyLong(), eq(10L))).thenReturn(4);
        when(jdbc.update(contains("umbral_nivel"), anyLong(), eq(10L))).thenReturn(3);
        when(jdbc.update(contains("filtro_eliminatorio"), anyLong(), eq(10L))).thenReturn(5);
        when(jdbc.update(contains("opcion_dimension"), any(), any(), any())).thenReturn(1);

        Map<String, Integer> conteos = copiador.copiarBanco(EMPRESA);

        assertThat(conteos).containsEntry("version_banco", 1)
                .containsEntry("pregunta", 2)
                .containsEntry("opcion", 3)
                .containsEntry("opcion_dimension", 1)
                .containsEntry("par_consistencia", 1)
                .containsEntry("multiplicador_bloque", 4)
                .containsEntry("umbral_nivel", 3)
                .containsEntry("filtro_eliminatorio", 5);

        // La copia nace PUBLICADA, del destino, y sabe de qué versión salió
        ArgumentCaptor<VersionBanco> version = ArgumentCaptor.forClass(VersionBanco.class);
        verify(versionesBanco).save(version.capture());
        assertThat(version.getValue().getOrganizacionId()).isEqualTo(EMPRESA);
        assertThat(version.getValue().getEstado()).isEqualTo("PUBLICADA");
        assertThat(version.getValue().getCopiadaDeVersionId()).isEqualTo(10L);

        // El par de consistencia apunta a las preguntas NUEVAS, no a las de la plataforma
        ArgumentCaptor<ParConsistencia> par = ArgumentCaptor.forClass(ParConsistencia.class);
        verify(pares).save(par.capture());
        assertThat(par.getValue().getPreguntaAId()).isNotEqualTo(21L);
        assertThat(par.getValue().getPreguntaBId()).isNotEqualTo(22L);
    }

    @Test
    @DisplayName("Sin banco publicado no se copia nada: encender fallaría entero")
    void sinBancoPublicadoNoSeCopiaNada() {
        when(versionesBanco.findByOrganizacionIdAndEstado(PLATAFORMA, "PUBLICADA"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> copiador.copiarBanco(EMPRESA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("banco publicado");
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
