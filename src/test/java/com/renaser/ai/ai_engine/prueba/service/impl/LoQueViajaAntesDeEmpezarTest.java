package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.prueba.dto.DtosPrueba.EntregableRequeridoCandidato;
import com.renaser.ai.ai_engine.prueba.dto.DtosPrueba.MiPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPrueba.PreguntaCandidato;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.PreguntaDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;
import com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que viaja al portal antes de pulsar «Empezar», y lo que llega después.
 *
 * <p>La decisión del usuario del 10/10/2026: <b>el caso se lee dentro del reloj</b>. Hasta
 * entonces la prueba viajaba entera desde que se abría la pantalla previa —las preguntas con sus
 * opciones, el caso, los materiales, la consigna y el detalle de los entregables— y, aunque el
 * portal no pintara las preguntas, se leían en la respuesta. Con eso se podía trabajar el encargo
 * sin que corriera el tiempo.
 *
 * <p>Lo que se comprueba, en la prueba del editor y en la de plantilla:
 *
 * <ul>
 *   <li><b>Antes de empezar</b> no viaja nada que dé ventaja, ni en el JSON: ni las preguntas (sí
 *       cuántas son), ni el enunciado, ni los materiales, ni la consigna, ni el detalle de los
 *       entregables. Sí el tiempo, la modalidad, las fechas, los días, las herramientas y qué
 *       archivos habrá que entregar (nombre, obligatorio, formato y su pregunta).
 *   <li><b>Al empezar</b> llega todo en la respuesta de {@code iniciar}, y en las lecturas que
 *       siguen.
 *   <li>La consigna del editor <b>ni se busca</b> antes de empezar: su enlace firmado es el caso.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Lo que viaja de la prueba antes y después de empezar")
class LoQueViajaAntesDeEmpezarTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 40L;
    private static final Long POSTULACION = 7L;
    private static final Long INTENTO = 70L;
    private static final UUID UUID_POSTULACION = UUID.randomUUID();
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "CANDIDATO", List.of(), Map.of());

    private static final String CASO = "La tienda cerró marzo con un descuadre de S/ 1 250.";
    private static final String MATERIALES = "El mayor de marzo, en la carpeta compartida.";
    private static final String HERRAMIENTAS = "Excel o Google Sheets.";
    private static final String PREGUNTA_ABIERTA = "¿Cómo hallaste el descuadre de marzo?";
    private static final String PREGUNTA_CERRADA = "¿Qué libro registra primero una venta al crédito?";
    private static final String OPCION = "Libro diario";
    private static final String DETALLE = "Con las partidas en tránsito separadas de los errores.";
    private static final String URL_CONSIGNA = "https://almacen.test/firmado/caso.pdf?token=abc";

    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();

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
    @Mock private com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa cola;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private PreguntaRepository preguntasBanco;
    @Mock private ArchivoRepository archivos;
    @Mock private CalificacionDeLaPruebaPropia calculo;
    @Mock private CierreDeLaPruebaPropia cierre;

    private ServicioPruebaImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPruebaImpl(intentos, versiones, vacantes, variantes, preguntasElegidas,
                preguntasCatalogo, entregablesRequeridos, entregables, respuestas, postulaciones,
                almacen, maquina, cola, versionesBanco, preguntasBanco, archivos, calculo, cierre);
        when(postulaciones.findByUuid(UUID_POSTULACION)).thenReturn(Optional.of(Postulacion.builder()
                .id(POSTULACION).uuid(UUID_POSTULACION).usuarioId(QUIEN.usuarioId())
                .organizacionId(ORGANIZACION).vacanteId(VACANTE).build()));
        when(intentos.save(any(IntentoPrueba.class))).thenAnswer(i -> i.getArgument(0));
        when(entregables.findByIntentoPruebaId(INTENTO)).thenReturn(List.of());
        when(respuestas.findByIntentoPruebaId(INTENTO)).thenReturn(List.of());
    }

    private IntentoPrueba suIntento(IntentoPrueba intento) {
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento));
        return intento;
    }

    private static String enJson(MiPrueba prueba) {
        return JSON.writeValueAsString(prueba);
    }

    /** Lo que nunca puede leerse antes de empezar, ni en la pantalla ni en la respuesta. */
    private static void sinNadaQueDeVentaja(MiPrueba prueba) {
        assertThat(prueba.preguntas()).as("las preguntas, solo cuántas son").isEmpty();
        assertThat(prueba.enunciado()).as("el caso").isNull();
        assertThat(prueba.materiales()).as("los materiales").isNull();
        assertThat(prueba.consigna()).as("la consigna").isNull();
        assertThat(prueba.cambioTexto()).isNull();
        assertThat(prueba.entregables()).extracting(EntregableRequeridoCandidato::detalle).containsOnlyNulls();
        assertThat(enJson(prueba)).doesNotContain(CASO, MATERIALES, PREGUNTA_ABIERTA, PREGUNTA_CERRADA,
                OPCION, DETALLE, URL_CONSIGNA, "caso.pdf", "descuadre", "hallaste", "carpeta compartida");
    }

    // ============ La prueba escrita en el editor (V67) ============

    @Nested
    @DisplayName("La prueba del editor")
    class DelEditor {

        private static final Long VERSION = 250L;
        private static final Long CONSIGNA = 900L;
        private static final Long ABIERTA = 501L;
        private static final Long CERRADA = 502L;

        private final Instant cierraEn = Instant.now().plus(5, ChronoUnit.DAYS);

        @BeforeEach
        void laVersionYSuResultado() {
            VersionBanco version = VersionBanco.builder().id(VERSION).organizacionId(ORGANIZACION)
                    .estado("PUBLICADA").enunciado(CASO).materiales(MATERIALES)
                    .herramientasPermitidas(HERRAMIENTAS).modalidad("CRONOMETRADA").duracionMinutos(90)
                    .consignaArchivoId(CONSIGNA).build();
            when(versionesBanco.findById(VERSION)).thenReturn(Optional.of(version));
            when(versionesBanco.pruebaPropiaDe(VACANTE, "PUBLICADA")).thenReturn(Optional.of(version));
            when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(
                    Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION).pruebaCierraEn(cierraEn).build()));
            List<PreguntaDeLaPrueba> preguntas = List.of(
                    new PreguntaDeLaPrueba(Pregunta.builder().id(CERRADA).versionBancoId(VERSION)
                            .tipo("OPCION_UNICA").enunciado(PREGUNTA_CERRADA).build(),
                            List.of(Opcion.builder().id(1L).preguntaId(CERRADA).texto(OPCION).orden(1).build()),
                            null, 10, BigDecimal.ZERO, false),
                    new PreguntaDeLaPrueba(Pregunta.builder().id(ABIERTA).versionBancoId(VERSION)
                            .tipo("ABIERTA").enunciado(PREGUNTA_ABIERTA).build(),
                            List.of(), null, 0, BigDecimal.ZERO, false));
            List<EntregableRequerido> pedidos = List.of(
                    EntregableRequerido.builder().id(301L).versionBancoId(VERSION).nombre("Conciliación.xlsx")
                            .detalle(DETALLE).formato("ARCHIVO").esObligatorio(true).preguntaId(ABIERTA).build(),
                    EntregableRequerido.builder().id(302L).versionBancoId(VERSION).nombre("Informe final")
                            .formato("ENLACE").esObligatorio(false).build());
            when(calculo.calcular(any(IntentoPrueba.class)))
                    .thenReturn(new Resultado(version, List.of(), preguntas, pedidos));
            when(archivos.findByIdAndOrganizacionId(CONSIGNA, ORGANIZACION)).thenReturn(Optional.of(
                    Archivo.builder().id(CONSIGNA).organizacionId(ORGANIZACION).ruta("pruebas/caso.pdf")
                            .nombreOriginal("caso.pdf").build()));
            when(almacen.urlDeDescarga(any(Archivo.class))).thenReturn(Optional.of(
                    new AlmacenArchivos.EnlaceFirmado(URL_CONSIGNA, Instant.now().plusSeconds(300))));
        }

        private IntentoPrueba sinEmpezar() {
            return suIntento(IntentoPrueba.builder().id(INTENTO).postulacionId(POSTULACION)
                    .versionBancoId(VERSION).venceEn(cierraEn).build());
        }

        @Test
        @DisplayName("antes de empezar: el tiempo, las fechas, cuántas son, las herramientas y qué "
                + "archivos —sin detalle—; nada del caso, ni en el JSON")
        void antesDeEmpezar() {
            sinEmpezar();

            MiPrueba prueba = servicio.ver(QUIEN, UUID_POSTULACION);

            assertThat(prueba.estadoIntento()).isEqualTo("PENDIENTE");
            assertThat(prueba.delEditor()).isTrue();
            sinNadaQueDeVentaja(prueba);
            assertThat(prueba.totalPreguntas()).isEqualTo(2);
            assertThat(prueba.modalidad()).isEqualTo("CRONOMETRADA");
            assertThat(prueba.duracionMinutos()).isEqualTo(90);
            assertThat(prueba.fechaLimite()).isEqualTo(cierraEn);
            assertThat(prueba.venceEn()).isEqualTo(cierraEn);
            assertThat(prueba.herramientasPermitidas()).isEqualTo(HERRAMIENTAS);
            assertThat(prueba.entregables()).containsExactly(
                    new EntregableRequeridoCandidato(301L, "Conciliación.xlsx", null, "ARCHIVO", true, false, ABIERTA, 2),
                    new EntregableRequeridoCandidato(302L, "Informe final", null, "ENLACE", false, false, null, null));
            // Su enlace firmado es el caso entero: ni se pide antes de empezar.
            verify(almacen, never()).urlDeDescarga(any());
            verify(archivos, never()).findByIdAndOrganizacionId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("al empezar llega todo en la misma respuesta: el caso, los materiales, la consigna, "
                + "las preguntas con sus opciones y el detalle")
        void alEmpezarLlegaTodo() {
            IntentoPrueba intento = sinEmpezar();

            MiPrueba prueba = servicio.iniciar(QUIEN, UUID_POSTULACION);

            assertThat(intento.getIniciadoEn()).isNotNull();
            assertThat(prueba.estadoIntento()).isEqualTo("EN_CURSO");
            assertThat(prueba.enunciado()).isEqualTo(CASO);
            assertThat(prueba.materiales()).isEqualTo(MATERIALES);
            assertThat(prueba.herramientasPermitidas()).isEqualTo(HERRAMIENTAS);
            assertThat(prueba.consigna().url()).isEqualTo(URL_CONSIGNA);
            assertThat(prueba.consigna().nombre()).isEqualTo("caso.pdf");
            assertThat(prueba.preguntas()).extracting(PreguntaCandidato::enunciado)
                    .containsExactly(PREGUNTA_CERRADA, PREGUNTA_ABIERTA);
            assertThat(prueba.preguntas().get(0).opciones()).extracting(o -> o.texto()).containsExactly(OPCION);
            assertThat(prueba.totalPreguntas()).isEqualTo(2);
            assertThat(prueba.entregables()).extracting(EntregableRequeridoCandidato::detalle)
                    .containsExactly(DETALLE, null);
            // RF-53 sigue: ni puntos ni criterios en lo que viaja.
            assertThat(enJson(prueba)).doesNotContain("puntos", "puntaje", "criterio", "queDebeTener");
        }

        @Test
        @DisplayName("empezada, volver a leerla trae todo, igual que al empezar")
        void empezadaSeLeeEntera() {
            suIntento(IntentoPrueba.builder().id(INTENTO).postulacionId(POSTULACION).versionBancoId(VERSION)
                    .iniciadoEn(Instant.now().minus(5, ChronoUnit.MINUTES))
                    .venceEn(Instant.now().plus(85, ChronoUnit.MINUTES)).build());

            MiPrueba prueba = servicio.ver(QUIEN, UUID_POSTULACION);

            assertThat(prueba.estadoIntento()).isEqualTo("EN_CURSO");
            assertThat(prueba.enunciado()).isEqualTo(CASO);
            assertThat(prueba.materiales()).isEqualTo(MATERIALES);
            assertThat(prueba.consigna().url()).isEqualTo(URL_CONSIGNA);
            assertThat(prueba.preguntas()).hasSize(2);
            assertThat(prueba.entregables().get(0).detalle()).isEqualTo(DETALLE);
        }

        @Test
        @DisplayName("con un almacén que no firma, tras empezar va el nombre del adjunto sin enlace: "
                + "ya no hay plan B con un enlace guardado (AC-19)")
        void sinFirmaVaElNombreSinEnlace() {
            when(almacen.urlDeDescarga(any(Archivo.class))).thenReturn(Optional.empty());
            sinEmpezar();

            MiPrueba prueba = servicio.iniciar(QUIEN, UUID_POSTULACION);

            assertThat(prueba.consigna().nombre()).isEqualTo("caso.pdf");
            assertThat(prueba.consigna().url()).isNull();
            assertThat(enJson(prueba)).doesNotContain("https://");
        }

        @Test
        @DisplayName("sin cronómetro, antes de empezar viajan los días")
        void sinCronometroViajanLosDias() {
            VersionBanco sinReloj = VersionBanco.builder().id(VERSION).organizacionId(ORGANIZACION)
                    .enunciado(CASO).modalidad("PLAZO_ABIERTO").plazoDias(3).build();
            when(versionesBanco.findById(VERSION)).thenReturn(Optional.of(sinReloj));
            when(calculo.calcular(any(IntentoPrueba.class)))
                    .thenReturn(new Resultado(sinReloj, List.of(), List.of(), List.of()));
            sinEmpezar();

            MiPrueba prueba = servicio.ver(QUIEN, UUID_POSTULACION);

            assertThat(prueba.plazoDias()).isEqualTo(3);
            assertThat(prueba.duracionMinutos()).isNull();
            assertThat(prueba.enunciado()).isNull();
            assertThat(prueba.totalPreguntas()).isZero();
        }
    }

    // ============ La prueba de plantilla ============

    @Nested
    @DisplayName("La prueba de plantilla")
    class DePlantilla {

        private static final Long VERSION = 31L;

        @BeforeEach
        void laVersion() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(VersionPlantillaPrueba.builder()
                    .id(VERSION).plantillaPruebaId(300L).enunciado(CASO).materiales(MATERIALES)
                    .herramientasPermitidas(HERRAMIENTAS).urlConsigna(URL_CONSIGNA)
                    .modalidad("CRONOMETRADA").duracionMinutos(60).plazoDias(7).build()));
            when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(
                    Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION).build()));
            when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION)).thenReturn(List.of(
                    PreguntaVersionPlantilla.builder().versionPlantillaPruebaId(VERSION).preguntaPruebaId(81L).orden(1).build(),
                    PreguntaVersionPlantilla.builder().versionPlantillaPruebaId(VERSION).preguntaPruebaId(82L).orden(2).build()));
            when(preguntasCatalogo.findByIdIn(anyList())).thenReturn(List.of(
                    PreguntaPrueba.builder().id(81L).tipo("PREVIA").enunciado(PREGUNTA_ABIERTA).build(),
                    PreguntaPrueba.builder().id(82L).tipo("POSTERIOR").enunciado(PREGUNTA_CERRADA).build()));
            when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION)).thenReturn(List.of(
                    EntregableRequerido.builder().id(401L).versionPlantillaPruebaId(VERSION).nombre("El tablero")
                            .detalle(DETALLE).formato("ARCHIVO").esObligatorio(true).build()));
        }

        @Test
        @DisplayName("antes de empezar: el tiempo, cuántas son, las herramientas y qué archivos; ni el "
                + "encargo, ni los materiales, ni las preguntas, ni el detalle")
        void antesDeEmpezar() {
            suIntento(IntentoPrueba.builder().id(INTENTO).postulacionId(POSTULACION)
                    .versionPlantillaPruebaId(VERSION).build());

            MiPrueba prueba = servicio.ver(QUIEN, UUID_POSTULACION);

            assertThat(prueba.estadoIntento()).isEqualTo("PENDIENTE");
            assertThat(prueba.delEditor()).isFalse();
            sinNadaQueDeVentaja(prueba);
            assertThat(prueba.totalPreguntas()).isEqualTo(2);
            assertThat(prueba.duracionMinutos()).isEqualTo(60);
            assertThat(prueba.modalidad()).isEqualTo("CRONOMETRADA");
            // Con reloj, los días de la plantilla no viajan: serían dos plazos.
            assertThat(prueba.plazoDias()).isNull();
            assertThat(prueba.herramientasPermitidas()).isEqualTo(HERRAMIENTAS);
            assertThat(prueba.entregables()).containsExactly(
                    new EntregableRequeridoCandidato(401L, "El tablero", null, "ARCHIVO", true, false));
        }

        @Test
        @DisplayName("de plazo abierto, antes de empezar viajan sus días")
        void dePlazoAbiertoViajanLosDias() {
            when(versiones.findById(VERSION)).thenReturn(Optional.of(VersionPlantillaPrueba.builder()
                    .id(VERSION).enunciado(CASO).modalidad("PLAZO_ABIERTO").plazoDias(7).build()));
            suIntento(IntentoPrueba.builder().id(INTENTO).postulacionId(POSTULACION)
                    .versionPlantillaPruebaId(VERSION).build());

            MiPrueba prueba = servicio.ver(QUIEN, UUID_POSTULACION);

            assertThat(prueba.plazoDias()).isEqualTo(7);
            assertThat(prueba.duracionMinutos()).isNull();
            assertThat(prueba.enunciado()).isNull();
        }

        @Test
        @DisplayName("al empezar llega todo: el encargo, los materiales, las preguntas y el detalle")
        void alEmpezarLlegaTodo() {
            IntentoPrueba intento = suIntento(IntentoPrueba.builder().id(INTENTO).postulacionId(POSTULACION)
                    .versionPlantillaPruebaId(VERSION).build());

            MiPrueba prueba = servicio.iniciar(QUIEN, UUID_POSTULACION);

            assertThat(intento.getIniciadoEn()).isNotNull();
            assertThat(prueba.estadoIntento()).isEqualTo("EN_CURSO");
            assertThat(prueba.enunciado()).isEqualTo(CASO);
            assertThat(prueba.materiales()).isEqualTo(MATERIALES);
            assertThat(prueba.preguntas()).extracting(PreguntaCandidato::enunciado)
                    .containsExactly(PREGUNTA_ABIERTA, PREGUNTA_CERRADA);
            assertThat(prueba.totalPreguntas()).isEqualTo(2);
            assertThat(prueba.entregables()).extracting(EntregableRequeridoCandidato::detalle)
                    .containsExactly(DETALLE);
        }

        @Test
        @DisplayName("empezada, volver a leerla trae todo")
        void empezadaSeLeeEntera() {
            suIntento(IntentoPrueba.builder().id(INTENTO).postulacionId(POSTULACION)
                    .versionPlantillaPruebaId(VERSION).iniciadoEn(Instant.now().minus(1, ChronoUnit.MINUTES))
                    .venceEn(Instant.now().plus(59, ChronoUnit.MINUTES)).build());

            MiPrueba prueba = servicio.ver(QUIEN, UUID_POSTULACION);

            assertThat(prueba.enunciado()).isEqualTo(CASO);
            assertThat(prueba.materiales()).isEqualTo(MATERIALES);
            assertThat(prueba.preguntas()).hasSize(2);
            assertThat(prueba.entregables().get(0).detalle()).isEqualTo(DETALLE);
        }
    }
}
