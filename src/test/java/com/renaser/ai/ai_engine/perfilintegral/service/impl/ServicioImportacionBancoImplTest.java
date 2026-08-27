package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.BancoLeido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.ErrorDeImportacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaCampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.ResultadoImportacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CampoCasoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.DimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ParConsistenciaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RangoPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ImportacionInvalidaException;
import com.renaser.ai.ai_engine.perfilintegral.service.LectorBancoCazatalentos;
import com.renaser.ai.ai_engine.perfilintegral.service.LectorPlantillaBanco;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("La importación del banco desde Excel")
class ServicioImportacionBancoImplTest {

    private static final Long ORGANIZACION = 1L;

    @Mock private LectorPlantillaBanco lector;
    // Real y no mock: es sin estado, y sobre los bytes de mentira de estos tests responde
    // que el archivo no es suyo — que es justo el camino que estos tests ejercitan.
    @Spy private LectorBancoCazatalentos lectorCazatalentos = new LectorBancoCazatalentos();
    @Mock private VersionBancoRepository versiones;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private RangoPreguntaRepository rangos;
    @Mock private CampoCasoRepository camposCaso;
    @Mock private ParConsistenciaRepository pares;
    @Mock private DimensionRepository dimensiones;
    @Mock private PreguntaDimensionRepository preguntaDimensiones;
    @Mock private ServicioAuditoria auditoria;
    @Mock private Permisos permisos;

    @InjectMocks
    private ServicioImportacionBancoImpl servicio;

    private ContextoUsuario quien;

    @BeforeEach
    void antes() {
        quien = new ContextoUsuario(10L, 20L, ORGANIZACION, "EQUIPO", List.of(1L),
                Map.of("editar_banco_preguntas", "TODO"));
    }

    private static BancoLeido leidoSano() {
        return new BancoLeido(
                List.of(new FilaPregunta(5, "X01", "CD", "Tu caso. (1 campo)", null,
                                (short) 1, false, List.of("INT"), (short) 1, null, null, "nota",
                                null, null, null),
                        new FilaPregunta(6, "X02", "PC", "¿Autorizas?", null,
                                (short) 0, true, List.of(), null, null, null, null,
                                null, null, null)),
                List.of(new FilaOpcion(5, "X02", "Sí", null, null, false, null),
                        new FilaOpcion(6, "X02", "No", null, null, false, null)),
                List.of(new FilaCampoCaso(5, "X01", "Nombre de la tarea", null)),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("con el archivo sano crea UN borrador, inserta por lotes y audita una vez")
    void conElArchivoSanoCreaUnBorrador() {
        when(dimensiones.findAllByOrderByOrden()).thenReturn(List.of());
        when(lector.leer(any(), any())).thenReturn(leidoSano());
        when(versiones.save(any())).thenAnswer(i -> {
            VersionBanco v = i.getArgument(0);
            v.setId(77L);
            return v;
        });
        when(preguntas.saveAll(anyList())).thenAnswer(i -> {
            List<Pregunta> lista = i.getArgument(0);
            long id = 100;
            for (Pregunta p : lista) {
                p.setId(id++);
            }
            return lista;
        });
        when(opciones.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(camposCaso.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(rangos.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(pares.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(preguntaDimensiones.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        ResultadoImportacion resultado = servicio.importar(quien, "SUPERVISION",
                "Banco de prueba", "banco.xlsx", new byte[]{1});

        assertThat(resultado.versionBancoId()).isEqualTo(77L);
        assertThat(resultado.preguntas()).isEqualTo(2);
        assertThat(resultado.opciones()).isEqualTo(2);
        assertThat(resultado.camposCaso()).isEqualTo(1);
        assertThat(resultado.dimensionesAsignadas()).isEqualTo(1);

        ArgumentCaptor<VersionBanco> version = ArgumentCaptor.forClass(VersionBanco.class);
        verify(versiones).save(version.capture());
        assertThat(version.getValue().getEstado()).isEqualTo("BORRADOR");
        assertThat(version.getValue().getTipoBanco()).isEqualTo("NIVEL");
        assertThat(version.getValue().getNivelPuestoCodigo()).isEqualTo("SUPERVISION");

        // Las letras se sintetizan por orden de fila, y el peso manda sobre esPuntuable
        ArgumentCaptor<List<Opcion>> lasOpciones = ArgumentCaptor.forClass(List.class);
        verify(opciones).saveAll(lasOpciones.capture());
        assertThat(lasOpciones.getValue()).extracting(Opcion::getLetra)
                .containsExactly("a", "b");
        ArgumentCaptor<List<Pregunta>> lasPreguntas = ArgumentCaptor.forClass(List.class);
        verify(preguntas).saveAll(lasPreguntas.capture());
        assertThat(lasPreguntas.getValue().get(0).isEsPuntuable()).isTrue();
        assertThat(lasPreguntas.getValue().get(1).isEsPuntuable()).isFalse();

        verify(auditoria, times(1)).registrar(eq(ORGANIZACION), eq(quien),
                eq("importar_banco_excel"), eq("version_banco"), eq(77L), any(), any(), any());
    }

    @Test
    @DisplayName("con errores del lector lanza la lista completa y no toca ningún repositorio")
    void conErroresDelLectorNoTocaNada() {
        when(dimensiones.findAllByOrderByOrden()).thenReturn(List.of());
        when(lector.leer(any(), any())).thenReturn(new BancoLeido(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ErrorDeImportacion("Preguntas", 7, "el peso debe ser 0, 1 o 2"),
                        new ErrorDeImportacion("Opciones", 9, "la pregunta X9 no existe"))));

        assertThatThrownBy(() -> servicio.importar(quien, "EJECUCION", "Banco roto",
                "roto.xlsx", new byte[]{1}))
                .isInstanceOf(ImportacionInvalidaException.class)
                .satisfies(e -> assertThat(
                        ((ImportacionInvalidaException) e).getErrores()).hasSize(2));

        verifyNoInteractions(versiones, preguntas, opciones, rangos, camposCaso, pares,
                preguntaDimensiones, auditoria);
    }

    @Test
    @DisplayName("sin etiqueta, sin extensión .xlsx o sin contenido, ni siquiera lee")
    void sinLoBasicoNiSiquieraLee() {
        assertThatThrownBy(() -> servicio.importar(quien, "EJECUCION", "  ",
                "banco.xlsx", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("etiqueta");
        assertThatThrownBy(() -> servicio.importar(quien, "EJECUCION", "Banco",
                "banco.docx", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> servicio.importar(quien, "EJECUCION", "Banco",
                "banco.xlsx", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
        verify(lector, never()).leer(any(), any());
        verifyNoInteractions(versiones, auditoria);
    }
}
