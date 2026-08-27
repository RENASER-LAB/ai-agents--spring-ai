package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CorregirEtiquetaVersion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CorregirTextoCampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CorregirTextoOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CorregirTextoPar;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CorregirTextoPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CorregirTextoRango;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CrearCampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CrearOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CrearParConsistencia;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CrearPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.CrearRango;
import com.renaser.ai.ai_engine.perfilintegral.entity.CampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.ParConsistencia;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.RangoPregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.mapper.CampoCasoMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.OpcionMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.ParConsistenciaMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.PreguntaMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.RangoPreguntaMapper;
import com.renaser.ai.ai_engine.perfilintegral.mapper.VersionBancoMapper;
import com.renaser.ai.ai_engine.perfilintegral.repository.CampoCasoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ParConsistenciaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RangoPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las reglas del ciclo BORRADOR → PUBLICADA → ARCHIVADA y la aduana de publicar.
 *
 * <p>Lo que hay que vigilar es doble. Primero, que <b>nada editable escape del borrador</b>:
 * durante meses agregarOpcion no lo comprobaba y la clave de una pregunta publicada se podía
 * alterar por debajo de un examen en curso. Segundo, que <b>publicar no deje pasar un formato
 * sin su clave completa</b>: el motor no revienta con un ítem a medias, hace algo peor — lo
 * salta en silencio y la nota sale como si el ítem no existiera.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El ciclo de vida del banco de preguntas")
class ServicioBancoPreguntasImplTest {

    private static final long ORGANIZACION = 1L;
    private static final long VERSION = 30L;
    private static final long PREGUNTA = 300L;

    @Mock private VersionBancoRepository versiones;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private RangoPreguntaRepository rangos;
    @Mock private CampoCasoRepository camposCaso;
    @Mock private ParConsistenciaRepository pares;
    @Mock private PreguntaDimensionRepository preguntaDimensiones;
    @Mock private EvaluacionRepository evaluaciones;
    @Mock private VersionBancoMapper versionBancoMapper;
    @Mock private PreguntaMapper preguntaMapper;
    @Mock private OpcionMapper opcionMapper;
    @Mock private RangoPreguntaMapper rangoMapper;
    @Mock private CampoCasoMapper campoCasoMapper;
    @Mock private ParConsistenciaMapper parMapper;
    @Mock private ServicioAuditoria auditoria;
    @Mock private Permisos permisos;

    @InjectMocks
    private ServicioBancoPreguntasImpl servicio;

    private ContextoUsuario quien;

    @BeforeEach
    void quienPregunta() {
        quien = new ContextoUsuario(10L, 20L, ORGANIZACION, "EQUIPO", List.of(1L),
                Map.of("editar_banco_preguntas", "TODO", "publicar_version_banco", "TODO"));
    }

    private VersionBanco version(String estado) {
        return VersionBanco.builder()
                .id(VERSION).organizacionId(ORGANIZACION)
                .tipoBanco("NIVEL").nivelPuestoCodigo("DIRECCION")
                .etiqueta("Banco de prueba").estado(estado)
                .build();
    }

    private Pregunta.PreguntaBuilder pregunta(String tipo) {
        return Pregunta.builder()
                .id(PREGUNTA).versionBancoId(VERSION).codigo("D01").tipo(tipo)
                .enunciado("¿...?").esPuntuable(true).peso((short) 1).orden(1);
    }

    private CrearPregunta crear(String tipo, Short peso, Short casosPedidos,
                                String rangosDe, String formula) {
        return new CrearPregunta("D01", "A1", tipo, "¿...?", null, "la clave secreta",
                true, 1, peso, true, false, casosPedidos, rangosDe, formula,
                null, null, null);
    }

    @Nested
    @DisplayName("Al crear una pregunta")
    class AlCrearUnaPregunta {

        @Test
        @DisplayName("los campos de puntuación del v3 llegan enteros a la base")
        void copiaLosCamposDelV3() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));
            when(preguntas.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.crearPregunta(quien, VERSION, crear("EF-4", (short) 2, null, null, null));

            ArgumentCaptor<Pregunta> guardada = ArgumentCaptor.forClass(Pregunta.class);
            verify(preguntas).save(guardada.capture());
            assertThat(guardada.getValue().getPeso()).isEqualTo((short) 2);
            assertThat(guardada.getValue().isEsClave()).isTrue();
            assertThat(guardada.getValue().getLogicaInterna()).isEqualTo("la clave secreta");
        }

        @Test
        @DisplayName("casosPedidos fuera de un CD se rechaza con 400, no se guarda basura")
        void rechazaCasosPedidosFueraDeUnCd() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));

            assertThatThrownBy(() -> servicio.crearPregunta(quien, VERSION,
                    crear("EF-4", (short) 1, (short) 7, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("casosPedidos");
            verify(preguntas, never()).save(any());
        }

        @Test
        @DisplayName("la fórmula y la referencia de rangos son solo de los ítems V")
        void rechazaLaFormulaFueraDeUnV() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));

            assertThatThrownBy(() -> servicio.crearPregunta(quien, VERSION,
                    crear("CD", (short) 1, null, null, "PEN/día × 2")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ítems V");
            verify(preguntas, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Al agregar una opción")
    class AlAgregarUnaOpcion {

        @Test
        @DisplayName("una versión publicada ya no admite opciones: la clave no se altera por debajo de un examen")
        void rechazaSiLaVersionYaNoEsBorrador() {
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(pregunta("EF-4").build()));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("PUBLICADA")));

            assertThatThrownBy(() -> servicio.agregarOpcion(quien, PREGUNTA,
                    new CrearOpcion("a", "texto", null, BigDecimal.ONE, null, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PUBLICADA");
            verify(opciones, never()).save(any());
        }

        @Test
        @DisplayName("el valor oculto, el distractor y el orden correcto llegan a la base")
        void copiaLosCamposDelV3() {
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(pregunta("SEC").build()));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));
            when(opciones.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.agregarOpcion(quien, PREGUNTA,
                    new CrearOpcion("3", "Paso tres", null, null, true, (short) 3));

            ArgumentCaptor<Opcion> guardada = ArgumentCaptor.forClass(Opcion.class);
            verify(opciones).save(guardada.capture());
            assertThat(guardada.getValue().getOrdenCorrecto()).isEqualTo((short) 3);
            assertThat(guardada.getValue().isEsDistractor()).isTrue();
        }
    }

    @Nested
    @DisplayName("Al publicar")
    class AlPublicar {

        private void versionEnBorradorCon(List<Pregunta> lasPreguntas, List<Opcion> lasOpciones) {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));
            when(preguntas.findByVersionBancoIdOrderByOrden(VERSION)).thenReturn(lasPreguntas);
            if (!lasPreguntas.isEmpty()) {
                when(opciones.findByPreguntaIdIn(anyList())).thenReturn(lasOpciones);
            }
        }

        private Opcion opcion(String letra, BigDecimal puntaje, BigDecimal valor,
                              boolean distractor, Short ordenCorrecto) {
            return Opcion.builder().preguntaId(PREGUNTA).letra(letra).texto("...")
                    .puntaje(puntaje).valor(valor).esDistractor(distractor)
                    .ordenCorrecto(ordenCorrecto).build();
        }

        @Test
        @DisplayName("un banco vacío no se publica")
        void rechazaUnBancoVacio() {
            versionEnBorradorCon(List.of(), List.of());

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("vacío");
        }

        @Test
        @DisplayName("una pregunta que puntúa sin peso se rechaza: el motor la saltaría en silencio")
        void rechazaUnaPuntuableSinPeso() {
            versionEnBorradorCon(List.of(pregunta("EF-4").peso(null).build()), List.of());

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("D01").hasMessageContaining("peso");
        }

        @Test
        @DisplayName("un EF-4 sin el valor oculto de cada opción no tiene con qué puntuarse")
        void rechazaUnEf4SinValores() {
            versionEnBorradorCon(List.of(pregunta("EF-4").build()),
                    List.of(opcion("a", null, BigDecimal.ONE, false, null),
                            opcion("b", null, null, false, null)));

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("D01").hasMessageContaining("valor");
        }

        @Test
        @DisplayName("un SEC cuyos pasos no cubren 1..n sin huecos no es un ordenamiento")
        void rechazaUnSecConHuecos() {
            versionEnBorradorCon(List.of(pregunta("SEC").build()),
                    List.of(opcion("1", null, null, false, (short) 1),
                            opcion("2", null, null, false, (short) 3),
                            opcion("3", null, null, false, (short) 3)));

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sin huecos ni repetidos");
        }

        @Test
        @DisplayName("un INV sin distractores no mide nada")
        void rechazaUnInvSinDistractores() {
            versionEnBorradorCon(List.of(pregunta("INV").build()),
                    List.of(opcion("a", null, null, false, null),
                            opcion("b", null, null, false, null)));

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("distractores");
        }

        @Test
        @DisplayName("una ABIERTA que puntúa sin su guía del evaluador no se publica")
        void rechazaUnaAbiertaSinGuia() {
            // El método CAZATALENTOS vive en C3/C4/señal: sin ellos el agente califica a ojo.
            versionEnBorradorCon(List.of(pregunta("ABIERTA")
                    .c3Esperado("el dato").c4Esperado(null).senalDeCero("nada").build()), List.of());

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("D01").hasMessageContaining("C4");
        }

        @Test
        @DisplayName("una ABIERTA con opciones es un error de datos: es de respuesta libre")
        void rechazaUnaAbiertaConOpciones() {
            versionEnBorradorCon(List.of(pregunta("ABIERTA")
                            .c3Esperado("d").c4Esperado("f").senalDeCero("s").build()),
                    List.of(opcion("a", null, null, false, null)));

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no lleva opciones");
        }

        @Test
        @DisplayName("una ABIERTA completa pasa la aduana, y una de cierre (peso 0) sin guía también")
        void unaAbiertaCompletaPasa() {
            versionEnBorradorCon(List.of(
                    pregunta("ABIERTA").c3Esperado("d").c4Esperado("f").senalDeCero("s").build(),
                    pregunta("ABIERTA").codigo("Z01").esPuntuable(false).peso((short) 0).build()),
                    List.of());
            when(versiones.findPublicadasHermanas(any(), any(), any(), any())).thenReturn(List.of());

            servicio.publicarVersion(quien, VERSION);

            verify(versiones).save(org.mockito.ArgumentMatchers.argThat(
                    v -> "PUBLICADA".equals(v.getEstado())));
        }

        @Test
        @DisplayName("un CD sin casosPedidos no tiene denominador con qué puntuarse")
        void rechazaUnCdSinCasosPedidos() {
            versionEnBorradorCon(List.of(pregunta("CD").casosPedidos(null).build()), List.of());

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("casosPedidos");
        }

        @Test
        @DisplayName("un V sin tabla, sin referencia y sin fórmula queda sin puntuar para siempre")
        void rechazaUnVSinTablaNiFormula() {
            versionEnBorradorCon(List.of(pregunta("V").build()), List.of());
            when(rangos.countByPreguntaId(PREGUNTA)).thenReturn(0L);

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tramos");
        }

        @Test
        @DisplayName("un formato del v0.1 no puede ser puntuable: el motor actual no lo sabe puntuar")
        void rechazaUnFormatoViejoPuntuable() {
            versionEnBorradorCon(List.of(pregunta("SITUACION").build()), List.of());

            assertThatThrownBy(() -> servicio.publicarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("v0.1");
        }

        @Test
        @DisplayName("publicar archiva a la que reemplaza y le pasa las evaluaciones que no empezaron")
        void archivaLaHermanaYRepunta() {
            versionEnBorradorCon(List.of(pregunta("EF-4").build()),
                    List.of(opcion("a", null, BigDecimal.ONE, false, null),
                            opcion("b", null, BigDecimal.valueOf(-1), false, null)));
            VersionBanco saliente = VersionBanco.builder()
                    .id(29L).organizacionId(ORGANIZACION).tipoBanco("NIVEL")
                    .nivelPuestoCodigo("DIRECCION").estado("PUBLICADA")
                    .publicadaEn(Instant.now().minusSeconds(3600)).build();
            when(versiones.findPublicadasHermanas("NIVEL", "DIRECCION", ORGANIZACION, VERSION))
                    .thenReturn(List.of(saliente));
            Evaluacion sinEmpezar = Evaluacion.builder().id(70L).versionBancoNivelId(29L).build();
            when(evaluaciones.findByVersionBancoNivelIdAndIniciadaEnIsNull(29L))
                    .thenReturn(List.of(sinEmpezar));

            servicio.publicarVersion(quien, VERSION);

            assertThat(saliente.getEstado()).isEqualTo("ARCHIVADA");
            assertThat(sinEmpezar.getVersionBancoNivelId()).isEqualTo(VERSION);
            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien), eq("archivar_version_banco"),
                    eq("version_banco"), eq(29L), any(), any(),
                    eq("reemplazada al publicar la versión " + VERSION));
        }
    }

    @Nested
    @DisplayName("Al archivar")
    class AlArchivar {

        @Test
        @DisplayName("un borrador no se archiva: no circula, abandonarlo no necesita endpoint")
        void soloUnaPublicadaSeArchiva() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));

            assertThatThrownBy(() -> servicio.archivarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BORRADOR");
        }

        @Test
        @DisplayName("con reemplazo publicado, quien no empezó pasa al banco vigente")
        void conReemplazoRepunta() {
            VersionBanco laQueSale = version("PUBLICADA");
            when(versiones.findById(VERSION)).thenReturn(Optional.of(laQueSale));
            VersionBanco reemplazo = VersionBanco.builder()
                    .id(31L).organizacionId(ORGANIZACION).tipoBanco("NIVEL")
                    .nivelPuestoCodigo("DIRECCION").estado("PUBLICADA")
                    .publicadaEn(Instant.now()).build();
            when(versiones.findPublicadasHermanas("NIVEL", "DIRECCION", ORGANIZACION, VERSION))
                    .thenReturn(List.of(reemplazo));
            Evaluacion sinEmpezar = Evaluacion.builder().id(70L).versionBancoNivelId(VERSION).build();
            when(evaluaciones.findByVersionBancoNivelIdAndIniciadaEnIsNull(VERSION))
                    .thenReturn(List.of(sinEmpezar));

            servicio.archivarVersion(quien, VERSION);

            assertThat(laQueSale.getEstado()).isEqualTo("ARCHIVADA");
            assertThat(sinEmpezar.getVersionBancoNivelId()).isEqualTo(31L);
        }

        @Test
        @DisplayName("sin reemplazo y con candidatos esperando, archivar se bloquea")
        void sinReemplazoConPendientesBloquea() {
            VersionBanco laQueSale = version("PUBLICADA");
            when(versiones.findById(VERSION)).thenReturn(Optional.of(laQueSale));
            when(versiones.findPublicadasHermanas("NIVEL", "DIRECCION", ORGANIZACION, VERSION))
                    .thenReturn(List.of());
            when(evaluaciones.findByVersionBancoNivelIdAndIniciadaEnIsNull(VERSION))
                    .thenReturn(List.of(Evaluacion.builder().id(70L).build()));

            assertThatThrownBy(() -> servicio.archivarVersion(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sin banco");
            assertThat(laQueSale.getEstado()).isEqualTo("PUBLICADA");
        }

        @Test
        @DisplayName("sin reemplazo pero sin nadie esperando, el nivel puede quedarse sin banco")
        void sinReemplazoSinPendientesPermite() {
            VersionBanco laQueSale = version("PUBLICADA");
            when(versiones.findById(VERSION)).thenReturn(Optional.of(laQueSale));
            when(versiones.findPublicadasHermanas("NIVEL", "DIRECCION", ORGANIZACION, VERSION))
                    .thenReturn(List.of());
            when(evaluaciones.findByVersionBancoNivelIdAndIniciadaEnIsNull(VERSION))
                    .thenReturn(List.of());

            servicio.archivarVersion(quien, VERSION);

            assertThat(laQueSale.getEstado()).isEqualTo("ARCHIVADA");
            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien), eq("archivar_version_banco"),
                    eq("version_banco"), eq(VERSION), any(), any(), eq((String) null));
        }
    }

    @Nested
    @DisplayName("Al editar un borrador")
    class AlEditarUnBorrador {

        @Test
        @DisplayName("reemplazar una pregunta guarda lo nuevo y deja constancia de lo viejo")
        void reemplazarUnaPreguntaDejaConstancia() {
            Pregunta laVieja = pregunta("EF-4").enunciado("Con la errata").build();
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(laVieja));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));

            servicio.actualizarPregunta(quien, PREGUNTA,
                    crear("EF-4", (short) 2, null, null, null));

            assertThat(laVieja.getEnunciado()).isEqualTo("¿...?");
            assertThat(laVieja.getPeso()).isEqualTo((short) 2);
            verify(preguntas).save(laVieja);
            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien), eq("editar_pregunta"),
                    eq("pregunta"), eq(PREGUNTA), any(), any(), eq((String) null));
        }

        @Test
        @DisplayName("editar mantiene las guardas de formato: un EF-4 no pide campos de caso")
        void editarMantieneLasGuardasDeFormato() {
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(pregunta("EF-4").build()));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));

            assertThatThrownBy(() -> servicio.actualizarPregunta(quien, PREGUNTA,
                    crear("EF-4", (short) 1, (short) 3, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("casosPedidos");
            verify(preguntas, never()).save(any());
        }

        @Test
        @DisplayName("eliminar una pregunta se lleva antes lo que la apunta, o la FK no deja")
        void eliminarUnaPreguntaSeLlevaLoQueLaApunta() {
            Pregunta laQueSeVa = pregunta("CD").build();
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(laQueSeVa));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));

            servicio.eliminarPregunta(quien, PREGUNTA);

            InOrder enOrden = inOrder(pares, opciones, rangos, camposCaso,
                    preguntaDimensiones, preguntas);
            enOrden.verify(pares).deleteByPreguntaAIdOrPreguntaBId(PREGUNTA, PREGUNTA);
            enOrden.verify(opciones).deleteByPreguntaIdIn(List.of(PREGUNTA));
            enOrden.verify(rangos).deleteByPreguntaIdIn(List.of(PREGUNTA));
            enOrden.verify(camposCaso).deleteByPreguntaIdIn(List.of(PREGUNTA));
            enOrden.verify(preguntaDimensiones).deleteByPreguntaIdIn(List.of(PREGUNTA));
            enOrden.verify(preguntas).delete(laQueSeVa);
        }

        @Test
        @DisplayName("sobre una publicada no se edita ni se elimina: eso ya circuló")
        void sobreUnaPublicadaNoSeEdita() {
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(pregunta("EF-4").build()));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("PUBLICADA")));

            assertThatThrownBy(() -> servicio.actualizarPregunta(quien, PREGUNTA,
                    crear("EF-4", (short) 1, null, null, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("solo un borrador se edita");
            assertThatThrownBy(() -> servicio.eliminarPregunta(quien, PREGUNTA))
                    .isInstanceOf(IllegalStateException.class);
            verify(preguntas, never()).delete(any());
        }

        @Test
        @DisplayName("descartar el borrador lo borra entero, hijas primero")
        void descartarElBorradorLoBorraEntero() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));
            when(evaluaciones.findByVersionBancoNivelIdAndIniciadaEnIsNull(VERSION))
                    .thenReturn(List.of());
            when(preguntas.findByVersionBancoIdOrderByOrden(VERSION))
                    .thenReturn(List.of(pregunta("EF-4").build()));

            servicio.descartarBorrador(quien, VERSION);

            InOrder enOrden = inOrder(pares, opciones, preguntas, versiones);
            enOrden.verify(pares).deleteByVersionBancoId(VERSION);
            enOrden.verify(opciones).deleteByPreguntaIdIn(List.of(PREGUNTA));
            enOrden.verify(preguntas).deleteByVersionBancoId(VERSION);
            enOrden.verify(versiones).delete(any());
            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien),
                    eq("descartar_borrador_banco"), eq("version_banco"), eq(VERSION),
                    any(), any(), eq((String) null));
        }

        @Test
        @DisplayName("cada pieza se reemplaza o se quita, y todas exigen que siga en borrador")
        void cadaPiezaSeReemplazaOSeQuita() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(pregunta("V").build()));

            Opcion opcion = Opcion.builder().id(50L).preguntaId(PREGUNTA).letra("a")
                    .texto("La vieja").build();
            when(opciones.findById(50L)).thenReturn(Optional.of(opcion));
            servicio.actualizarOpcion(quien, 50L, new CrearOpcion("b", "La nueva", 4.0,
                    new BigDecimal("1"), true, (short) 2));
            assertThat(opcion.getLetra()).isEqualTo("b");
            assertThat(opcion.getTexto()).isEqualTo("La nueva");
            assertThat(opcion.getPuntaje()).isEqualByComparingTo("4");
            servicio.eliminarOpcion(quien, 50L);
            verify(opciones).delete(opcion);

            RangoPregunta rango = RangoPregunta.builder().id(60L).preguntaId(PREGUNTA)
                    .orden(1).condicion("La vieja").puntaje(new BigDecimal("1")).build();
            when(rangos.findById(60L)).thenReturn(Optional.of(rango));
            servicio.actualizarRango(quien, 60L, new CrearRango(2, "La nueva",
                    new BigDecimal("3"), true));
            assertThat(rango.getOrden()).isEqualTo(2);
            assertThat(rango.getPuntaje()).isEqualByComparingTo("3");
            assertThat(rango.isGeneraBandera()).isTrue();
            servicio.eliminarRango(quien, 60L);
            verify(rangos).delete(rango);

            CampoCaso campo = CampoCaso.builder().id(70L).preguntaId(PREGUNTA)
                    .orden(1).etiqueta("La vieja").build();
            when(camposCaso.findById(70L)).thenReturn(Optional.of(campo));
            servicio.actualizarCampoCaso(quien, 70L, new CrearCampoCaso(3, "La nueva", "regla"));
            assertThat(campo.getOrden()).isEqualTo(3);
            assertThat(campo.getValidacion()).isEqualTo("regla");
            servicio.eliminarCampoCaso(quien, 70L);
            verify(camposCaso).delete(campo);

            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien), eq("editar_opcion"),
                    eq("opcion"), eq(50L), any(), any(), eq((String) null));
            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien), eq("eliminar_campo_caso"),
                    eq("campo_caso"), eq(70L), any(), any(), eq((String) null));
        }

        @Test
        @DisplayName("un par se reemplaza solo por preguntas de su misma versión")
        void unParSoloPorPreguntasDeSuVersion() {
            ParConsistencia par = ParConsistencia.builder().id(80L).versionBancoId(VERSION)
                    .preguntaAId(1L).preguntaBId(2L).build();
            when(pares.findById(80L)).thenReturn(Optional.of(par));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));
            when(preguntas.findById(3L)).thenReturn(Optional.of(
                    pregunta("EF-4").id(3L).build()));
            when(preguntas.findById(4L)).thenReturn(Optional.of(
                    pregunta("EF-4").id(4L).versionBancoId(999L).codigo("Z99").build()));

            assertThatThrownBy(() -> servicio.actualizarParConsistencia(quien, 80L,
                    new CrearParConsistencia(3L, 4L, null, new BigDecimal("5"),
                            (short) 15, "se contradicen")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no es de esta versión");
            assertThat(par.getPreguntaAId()).isEqualTo(1L);

            servicio.actualizarParConsistencia(quien, 80L,
                    new CrearParConsistencia(3L, 3L, null, new BigDecimal("5"),
                            (short) 15, "se contradicen"));
            assertThat(par.getPreguntaAId()).isEqualTo(3L);
            assertThat(par.getCondicion()).isEqualTo("se contradicen");

            servicio.eliminarParConsistencia(quien, 80L);
            verify(pares).delete(par);
        }

        @Test
        @DisplayName("editar una pieza que no existe es un 404, no un 500 ni un silencio")
        void editarUnaPiezaQueNoExiste() {
            when(opciones.findById(99L)).thenReturn(Optional.empty());
            when(rangos.findById(99L)).thenReturn(Optional.empty());
            when(camposCaso.findById(99L)).thenReturn(Optional.empty());
            when(pares.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.eliminarOpcion(quien, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> servicio.eliminarRango(quien, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> servicio.eliminarCampoCaso(quien, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> servicio.eliminarParConsistencia(quien, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("una versión publicada no se descarta: para eso está archivar")
        void unaPublicadaNoSeDescarta() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("PUBLICADA")));

            assertThatThrownBy(() -> servicio.descartarBorrador(quien, VERSION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("solo un borrador se edita");
            verify(versiones, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Al corregir el texto de una publicada")
    class AlCorregirElTextoDeUnaPublicada {

        @Test
        @DisplayName("cambia el enunciado y deja el anterior en la auditoría")
        void cambiaElEnunciadoYDejaRastro() {
            Pregunta laPublicada = pregunta("EF-4").enunciado("Con la herrata").build();
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(laPublicada));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("PUBLICADA")));

            servicio.corregirTextoPregunta(quien, PREGUNTA,
                    new CorregirTextoPregunta("Con la errata corregida", null, null, null, null, null));

            assertThat(laPublicada.getEnunciado()).isEqualTo("Con la errata corregida");
            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien),
                    eq("corregir_texto_pregunta"), eq("pregunta"), eq(PREGUNTA),
                    eq(Map.of("enunciado", "Con la herrata")), any(), eq((String) null));
        }

        @Test
        @DisplayName("lo que llega en nulo no se toca: corregir uno no borra los otros")
        void loQueLlegaEnNuloNoSeToca() {
            Pregunta laPublicada = pregunta("SJT-R")
                    .situacion("La situación de siempre").logicaInterna("La clave secreta").build();
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(laPublicada));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("PUBLICADA")));

            servicio.corregirTextoPregunta(quien, PREGUNTA,
                    new CorregirTextoPregunta("Enunciado nuevo", null, null, null, null, null));

            assertThat(laPublicada.getSituacion()).isEqualTo("La situación de siempre");
            assertThat(laPublicada.getLogicaInterna()).isEqualTo("La clave secreta");

            // Y lo que sí llega, se cambia: los tres campos a la vez
            servicio.corregirTextoPregunta(quien, PREGUNTA,
                    new CorregirTextoPregunta("Otro enunciado", "Otra situación", "Otra clave", null, null, null));
            assertThat(laPublicada.getEnunciado()).isEqualTo("Otro enunciado");
            assertThat(laPublicada.getSituacion()).isEqualTo("Otra situación");
            assertThat(laPublicada.getLogicaInterna()).isEqualTo("Otra clave");
        }

        @Test
        @DisplayName("la guía del evaluador se recalibra en una publicada: el candidato nunca la ve")
        void laGuiaSeRecalibra() {
            // Es la palanca de la calibración de CAZATALENTOS. Quien la toque debe
            // recalificar a la vacante entera; aquí solo se comprueba que el cambio entra.
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("PUBLICADA")));
            Pregunta abierta = pregunta("ABIERTA")
                    .c3Esperado("el plazo").c4Esperado("el nombre").senalDeCero("vieja").build();
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(abierta));

            servicio.corregirTextoPregunta(quien, PREGUNTA,
                    new CorregirTextoPregunta(null, null, null, null, null,
                            "«Le di retroalimentación y mejoró», sin plazo ni documento."));

            assertThat(abierta.getSenalDeCero()).startsWith("«Le di retroalimentación");
            assertThat(abierta.getC3Esperado()).as("lo no enviado no se toca").isEqualTo("el plazo");
            verify(preguntas).save(abierta);
        }

        @Test
        @DisplayName("un borrador no se corrige por aquí: se edita entero con el PUT")
        void unBorradorNoSeCorrigePorAqui() {
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(pregunta("EF-4").build()));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("BORRADOR")));

            assertThatThrownBy(() -> servicio.corregirTextoPregunta(quien, PREGUNTA,
                    new CorregirTextoPregunta("Otro", null, null, null, null, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("un borrador se edita entero");
        }

        @Test
        @DisplayName("una archivada tampoco: es la historia de quien ya la respondió")
        void unaArchivadaTampoco() {
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(pregunta("EF-4").build()));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("ARCHIVADA")));

            assertThatThrownBy(() -> servicio.corregirTextoPregunta(quien, PREGUNTA,
                    new CorregirTextoPregunta("Otro", null, null, null, null, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ya no se toca");
            verify(preguntas, never()).save(any());
        }

        @Test
        @DisplayName("campos, tramos, pares y la etiqueta también se corrigen, y solo su texto")
        void lasDemasPiezasTambienSeCorrigen() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("PUBLICADA")));
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(pregunta("CD").build()));

            CampoCaso campo = CampoCaso.builder().id(70L).preguntaId(PREGUNTA).orden(1)
                    .etiqueta("Nombre de la tarea (texo ≤ 40 car.)").build();
            when(camposCaso.findById(70L)).thenReturn(Optional.of(campo));
            servicio.corregirTextoCampoCaso(quien, 70L,
                    new CorregirTextoCampoCaso("Nombre de la tarea (texto ≤ 40 car.)", null));
            assertThat(campo.getEtiqueta()).contains("texto ≤ 40");
            assertThat(campo.getOrden()).isEqualTo(1);   // su sitio no se mueve
            // Y la regla de validación, cuando viene, también es texto corregible
            servicio.corregirTextoCampoCaso(quien, 70L,
                    new CorregirTextoCampoCaso(null, "si queda vacío → campo inválido"));
            assertThat(campo.getValidacion()).isEqualTo("si queda vacío → campo inválido");
            assertThat(campo.getEtiqueta()).contains("texto ≤ 40");   // lo otro, intacto

            RangoPregunta rango = RangoPregunta.builder().id(60L).preguntaId(PREGUNTA)
                    .orden(1).condicion("Directos 5-20").puntaje(new BigDecimal("3")).build();
            when(rangos.findById(60L)).thenReturn(Optional.of(rango));
            servicio.corregirTextoRango(quien, 60L,
                    new CorregirTextoRango("Directos 5–20 y niveles ≥ 2"));
            assertThat(rango.getCondicion()).isEqualTo("Directos 5–20 y niveles ≥ 2");
            assertThat(rango.getPuntaje()).isEqualByComparingTo("3");   // el puntaje, intacto

            ParConsistencia par = ParConsistencia.builder().id(80L).versionBancoId(VERSION)
                    .penalizacionPorcentaje(new BigDecimal("5")).condicion(null).build();
            when(pares.findById(80L)).thenReturn(Optional.of(par));
            servicio.corregirTextoParConsistencia(quien, 80L,
                    new CorregirTextoPar("dice una cosa y hace otra"));
            assertThat(par.getCondicion()).isEqualTo("dice una cosa y hace otra");
            assertThat(par.getPenalizacionPorcentaje()).isEqualByComparingTo("5");

            VersionBanco laVersion = version("PUBLICADA");
            when(versiones.findById(VERSION)).thenReturn(Optional.of(laVersion));
            servicio.corregirEtiquetaVersion(quien, VERSION,
                    new CorregirEtiquetaVersion("Banco RENASER v3 · Directivo"));
            assertThat(laVersion.getEtiqueta()).isEqualTo("Banco RENASER v3 · Directivo");
            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien),
                    eq("corregir_etiqueta_version"), eq("version_banco"), eq(VERSION),
                    eq(Map.of("etiqueta", "Banco de prueba")), any(), eq((String) null));
        }

        @Test
        @DisplayName("el texto de una opción se corrige sin rozar su clave")
        void elTextoDeUnaOpcionSinRozarSuClave() {
            Opcion opcion = Opcion.builder().id(50L).preguntaId(PREGUNTA).letra("a")
                    .texto("Texto con herrata").valor(new BigDecimal("2"))
                    .esDistractor(false).build();
            when(opciones.findById(50L)).thenReturn(Optional.of(opcion));
            when(preguntas.findById(PREGUNTA)).thenReturn(Optional.of(pregunta("EF-4").build()));
            when(versiones.findById(VERSION)).thenReturn(Optional.of(version("PUBLICADA")));

            servicio.corregirTextoOpcion(quien, 50L, new CorregirTextoOpcion("Texto corregido"));

            assertThat(opcion.getTexto()).isEqualTo("Texto corregido");
            // La clave sigue donde estaba: por aquí no hay manera de moverla
            assertThat(opcion.getValor()).isEqualByComparingTo("2");
        }
    }
}
