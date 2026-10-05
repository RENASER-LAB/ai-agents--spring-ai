package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.CrearPreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.ElegirPregunta;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El catálogo de preguntas de la prueba del puesto es solo de la plataforma
 * (spec {@code fuga-del-catalogo-de-preguntas}).
 *
 * <p>El catálogo no tiene dueño: abierto a cualquier empresa, la B leía el examen que escribió
 * la A. Lo que se protege aquí, a nivel de servicio, es que a otra empresa se le conteste
 * «no existe» <b>sin tocar nada</b>: ni el catálogo, ni la versión, ni las elecciones, ni la
 * auditoría. Y que la plataforma siga como antes. El recorrido por HTTP con dos empresas de
 * verdad, con el orden 403 → 404 → 400, vive en {@code CatalogoDePreguntasSoloPlataformaIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El catálogo de preguntas de prueba es solo de la plataforma")
class ServicioPlantillaPruebaCatalogoTest {

    private static final Long PLATAFORMA = 1L;
    private static final Long ACME = 7L;

    private static final ContextoUsuario DE_ACME = new ContextoUsuario(
            30L, 31L, ACME, "EQUIPO", List.of(5L), Map.of("editar_plantillas_prueba", "TODO"));
    private static final ContextoUsuario DE_LA_PLATAFORMA = new ContextoUsuario(
            10L, 11L, PLATAFORMA, "EQUIPO", List.of(1L), Map.of("editar_plantillas_prueba", "TODO"));

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
        // La plataforma se lee de `es_plataforma`, nunca de un id fijo.
        when(dueno.plataforma()).thenReturn(
                Organizacion.builder().id(PLATAFORMA).esPlataforma(true).build());
    }

    @Test
    @DisplayName("otra empresa que pide el catálogo recibe «no existe» y ningún texto")
    void otraEmpresaNoListaElCatalogo() {
        assertThatThrownBy(() -> servicio.listarPreguntasCatalogo(DE_ACME, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> servicio.listarPreguntasCatalogo(DE_ACME, "UNIVERSAL"))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(preguntasCatalogo);
    }

    @Test
    @DisplayName("otra empresa que crea una pregunta recibe «no existe» y no se escribe nada")
    void otraEmpresaNoCreaEnElCatalogo() {
        CrearPreguntaPrueba datos = new CrearPreguntaPrueba(
                "T01", "Describe cómo cerraste la caja", "UNIVERSAL", null, null);

        assertThatThrownBy(() -> servicio.crearPreguntaCatalogo(DE_ACME, datos))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(preguntasCatalogo, auditoria);
    }

    @Test
    @DisplayName("otra empresa que elige una pregunta por id recibe «no existe» sin mirar la versión")
    void otraEmpresaNoEligeDelCatalogo() {
        assertThatThrownBy(() -> servicio.elegirPregunta(DE_ACME, 40L, new ElegirPregunta(3L)))
                .isInstanceOf(ResourceNotFoundException.class);
        // Ni la versión ni la pregunta: contestar distinto según existan era la fuga.
        verifyNoInteractions(versiones, plantillas, preguntasCatalogo, preguntasElegidas);
    }

    @Test
    @DisplayName("la plataforma lista el catálogo como siempre, con tipo y sin él")
    void laPlataformaLista() {
        PreguntaPrueba pregunta = PreguntaPrueba.builder()
                .id(3L).codigo("T01").enunciado("¿Qué harías distinto?").tipo("UNIVERSAL").build();
        when(preguntasCatalogo.findAll()).thenReturn(List.of(pregunta));
        when(preguntasCatalogo.findByTipo("UNIVERSAL")).thenReturn(List.of(pregunta));

        assertThat(servicio.listarPreguntasCatalogo(DE_LA_PLATAFORMA, null))
                .singleElement().satisfies(p -> assertThat(p.codigo()).isEqualTo("T01"));
        assertThat(servicio.listarPreguntasCatalogo(DE_LA_PLATAFORMA, "UNIVERSAL")).hasSize(1);
    }

    @Test
    @DisplayName("la plataforma crea en el catálogo como siempre")
    void laPlataformaCrea() {
        when(preguntasCatalogo.save(any(PreguntaPrueba.class))).thenAnswer(i -> {
            PreguntaPrueba p = i.getArgument(0);
            p.setId(55L);
            return p;
        });

        Long id = servicio.crearPreguntaCatalogo(DE_LA_PLATAFORMA, new CrearPreguntaPrueba(
                "T01", "¿Qué harías distinto?", "UNIVERSAL", null, null));

        assertThat(id).isEqualTo(55L);
    }

    @Test
    @DisplayName("exigir el catálogo deja pasar a la plataforma y frena a las demás")
    void laGuarda() {
        servicio.exigirElCatalogoDeLaPlataforma(DE_LA_PLATAFORMA);
        assertThatThrownBy(() -> servicio.exigirElCatalogoDeLaPlataforma(DE_ACME))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
