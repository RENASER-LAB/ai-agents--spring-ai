package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.VersionResponse;
import com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pedir las versiones de una plantilla de prueba.
 *
 * <p>Antes no se podía: solo existía «dame la versión con este id», así que el panel
 * <b>tanteaba ids</b> en tandas hasta encontrar un hueco. Los ids son una secuencia de toda
 * la plataforma, así que ese tanteo dejaba 404 legítimos en cada carga y, en una base donde
 * las versiones de la empresa vivieran altas, podía no encontrar ninguna. Lo que se protege
 * aquí:
 *
 * <ul>
 *   <li>que vengan <b>todas</b> las versiones de esa plantilla, borradores incluidos —quien
 *       compone necesita ver el borrador, y quien elige para una vacante necesita saber que
 *       existe pero todavía no se puede usar—;
 *   <li>que vengan de la más nueva a la más vieja, que es como se leen;
 *   <li>que una organización que <b>no personalizó</b> vea las versiones de la plataforma,
 *       igual que las ve en {@code verVersion} — si esta puerta fuera más estrecha que
 *       {@code listarPlantillas}, el desplegable saldría vacío justo para esas empresas;
 *   <li>y que una plantilla ajena responda 404 sin llegar a leer versión ninguna.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Listar las versiones de una plantilla de prueba")
class ServicioPlantillaPruebaListadoTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long PLATAFORMA = 99L;
    private static final Long PLANTILLA = 6L;

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
    @Mock private DuenoDelInstrumento dueno;
    @Mock private AlmacenArchivos almacen;

    private ServicioPlantillaPruebaImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPlantillaPruebaImpl(plantillas, versiones, variantes,
                preguntasCatalogo, preguntasElegidas, entregablesRequeridos, criterios,
                auditoria, dueno, almacen);
    }

    private static PlantillaPrueba plantillaDe(Long organizacionId) {
        return PlantillaPrueba.builder()
                .id(PLANTILLA).organizacionId(organizacionId).nombre("Prueba del puesto").build();
    }

    private static VersionPlantillaPrueba version(Long id, int numero, String estado) {
        return VersionPlantillaPrueba.builder()
                .id(id).plantillaPruebaId(PLANTILLA).version(numero).estado(estado)
                .enunciado("Resuelve esto").modalidad("CRONOMETRADA").duracionMinutos(90)
                .materiales("Los veinte currículums").herramientasPermitidas("Cualquiera")
                .minutosExtra(10)
                .build();
    }

    @Test
    @DisplayName("devuelve todo lo que se puede escribir, para que editar no borre nada")
    void devuelveLoQueSePuedeEscribir() {
        /*
         * ⚠️ Esto no es celo por la cobertura. `actualizarVersion` es un PUT que reemplaza
         * la version entera: un campo que se pueda escribir y no leer es un campo que
         * cualquier edicion desde un panel borra sola —se carga el formulario con lo que la
         * API da, se guarda, y `materiales` se va a nulo sin que nadie lo tocara—. Los tres
         * de aqui faltaban en `VersionResponse` justo por eso.
         */
        when(plantillas.findByIdAndOrganizacionId(PLANTILLA, ORGANIZACION))
                .thenReturn(Optional.of(plantillaDe(ORGANIZACION)));
        when(versiones.findByPlantillaPruebaIdOrderByVersionDesc(PLANTILLA))
                .thenReturn(List.of(version(61L, 1, "PUBLICADA")));

        VersionResponse v = servicio.listarVersiones(QUIEN, PLANTILLA).getFirst();

        assertThat(v.materiales()).isEqualTo("Los veinte currículums");
        assertThat(v.herramientasPermitidas()).isEqualTo("Cualquiera");
        assertThat(v.minutosExtra()).isEqualTo(10);
    }

    @Test
    @DisplayName("vienen todas, el borrador también, y de la más nueva a la más vieja")
    void vienenTodasYEnOrden() {
        when(plantillas.findByIdAndOrganizacionId(PLANTILLA, ORGANIZACION))
                .thenReturn(Optional.of(plantillaDe(ORGANIZACION)));
        when(versiones.findByPlantillaPruebaIdOrderByVersionDesc(PLANTILLA))
                .thenReturn(List.of(version(72L, 2, "BORRADOR"), version(61L, 1, "PUBLICADA")));

        List<VersionResponse> listado = servicio.listarVersiones(QUIEN, PLANTILLA);

        assertThat(listado).extracting(VersionResponse::id, VersionResponse::version,
                        VersionResponse::estado)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(72L, 2, "BORRADOR"),
                        org.assertj.core.groups.Tuple.tuple(61L, 1, "PUBLICADA"));
        assertThat(listado).allSatisfy(v -> assertThat(v.plantillaPruebaId()).isEqualTo(PLANTILLA));
    }

    @Test
    @DisplayName("quien no personalizó ve las versiones de la plataforma")
    void sinPersonalizarSeVenLasDeLaPlataforma() {
        // La plantilla no es de esta organización: es de la plataforma, y el resolutor
        // dice que de ahí salen las pruebas de quien no personalizó.
        when(plantillas.findByIdAndOrganizacionId(PLANTILLA, ORGANIZACION))
                .thenReturn(Optional.empty());
        when(dueno.duenoDe(ORGANIZACION, Instrumento.PRUEBA)).thenReturn(PLATAFORMA);
        when(plantillas.findByIdAndOrganizacionId(PLANTILLA, PLATAFORMA))
                .thenReturn(Optional.of(plantillaDe(PLATAFORMA)));
        when(versiones.findByPlantillaPruebaIdOrderByVersionDesc(PLANTILLA))
                .thenReturn(List.of(version(61L, 1, "PUBLICADA")));

        assertThat(servicio.listarVersiones(QUIEN, PLANTILLA))
                .extracting(VersionResponse::id).containsExactly(61L);
    }

    @Test
    @DisplayName("una plantilla ajena responde 404 y no se lee ninguna versión")
    void unaPlantillaAjenaNoEnsenaNada() {
        when(plantillas.findByIdAndOrganizacionId(PLANTILLA, ORGANIZACION))
                .thenReturn(Optional.empty());
        when(dueno.duenoDe(ORGANIZACION, Instrumento.PRUEBA)).thenReturn(ORGANIZACION);

        assertThatThrownBy(() -> servicio.listarVersiones(QUIEN, PLANTILLA))
                .isInstanceOf(ResourceNotFoundException.class);

        // Que el 404 salga antes de tocar las versiones no es un detalle: si se leyeran
        // primero, un fallo de la guarda dejaría la prueba de otra empresa ya cargada.
        verifyNoInteractions(versiones);
    }
}
