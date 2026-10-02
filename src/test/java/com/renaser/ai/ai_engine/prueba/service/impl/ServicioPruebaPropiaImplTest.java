package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.ResumenDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.PreguntasInvalidasException;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarCriterioDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarDatosDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarEntregable;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarPreguntaDePrueba;
import com.renaser.ai.ai_engine.prueba.entity.CriterioBancoEntregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.repository.CriterioBancoEntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.NotaCriterioPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.CriterioDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;
import com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

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
 * El taller del borrador de la prueba del puesto (V67): el resumen, el caso y el tiempo, el
 * enunciado adjunto, los criterios con su parte calificada y lo que miran, las preguntas, los
 * entregables y descartarlo entero, con los porqués cuando no se puede.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("La prueba del editor: el taller del borrador")
class ServicioPruebaPropiaImplTest {

    private static final long ORGANIZACION = 2L;
    private static final long VACANTE = 40L;
    private static final long BORRADOR = 80L;
    private static final long PUBLICADA = 81L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private Permisos permisos;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private CriterioBancoRepository criteriosBanco;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private EntregableRequeridoRepository entregables;
    @Mock private CriterioBancoEntregableRepository miradas;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private NotaCriterioPruebaRepository notas;
    @Mock private PropuestaPreguntasRepository propuestas;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private CalificacionDeLaPruebaPropia calculo;
    @Mock private CierreDeLaPruebaPropia cierre;
    @Mock private ColaCalificacionIa cola;
    @Mock private ServicioAuditoria auditoria;
    @InjectMocks private ServicioPruebaPropiaImpl servicio;

    private final Vacante vacante = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
            .titulo("Contador").estado("PUBLICADA").build();
    private final VersionBanco borrador = VersionBanco.builder().id(BORRADOR).organizacionId(ORGANIZACION)
            .estado("BORRADOR").versionGuia(1).build();
    private final VersionBanco publicada = VersionBanco.builder().id(PUBLICADA).organizacionId(ORGANIZACION)
            .estado("PUBLICADA").versionGuia(1).build();

    @BeforeEach
    void armar() {
        when(alcance.laVacanteVisible(eq(QUIEN), eq(VACANTE), any())).thenReturn(vacante);
        when(versionesBanco.pruebaPropiaDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.pruebaPropiaDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());
        when(versionesBanco.saveAndFlush(any(VersionBanco.class))).thenAnswer(inv -> {
            VersionBanco v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(BORRADOR);
            }
            return v;
        });
        when(criteriosBanco.save(any(CriterioBanco.class))).thenAnswer(inv -> {
            CriterioBanco c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(900L);
            }
            return c;
        });
        when(preguntas.save(any(Pregunta.class))).thenAnswer(inv -> {
            Pregunta p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(700L);
            }
            return p;
        });
    }

    private void hayBorrador() {
        when(versionesBanco.pruebaPropiaDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
    }

    private void hayPublicada() {
        when(versionesBanco.pruebaPropiaDe(VACANTE, "PUBLICADA")).thenReturn(Optional.of(publicada));
    }

    private static CriterioBanco criterio(long id, long version, int orden) {
        return CriterioBanco.builder().id(id).versionBancoId(version).nombre("C" + id).orden(orden)
                .puntosCalificados(0).build();
    }

    private static EntregableRequerido entregable(long id, long version, int orden) {
        return EntregableRequerido.builder().id(id).versionBancoId(version).nombre("E" + id)
                .detalle("Qué contiene").formato("ARCHIVO").esObligatorio(true).orden(orden).build();
    }

    private static Pregunta pregunta(long id, Long criterio, Integer orden) {
        return Pregunta.builder().id(id).versionBancoId(BORRADOR).criterioBancoId(criterio)
                .orden(orden).tipo("ABIERTA").enunciado("P" + id).puntos(0).build();
    }

    /** Una prueba con un criterio que vale 100 de parte calificada y un entregable. */
    private static Resultado conUnCriterio(VersionBanco v) {
        CriterioDeLaPrueba c = new CriterioDeLaPrueba(criterio(5L, v.getId(), 1), List.of(), List.of(),
                0, BigDecimal.ZERO, 100, "IA", null);
        return new Resultado(v, List.of(c), List.of(), List.of(entregable(30L, v.getId(), 1)));
    }

    // ---------------------------------------------------------------- el resumen

    @Test
    @DisplayName("Sin borrador ni publicada, el resumen dice que no hay prueba")
    void resumenSinPrueba() {
        assertThat(servicio.resumenDe(VACANTE)).isEqualTo(
                new ResumenDePreguntas("SIN_PRUEBA", null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("Con una publicada cronometrada manda ella, con sus minutos y sus entregables")
    void resumenDeLaPublicada() {
        hayBorrador();
        hayPublicada();
        publicada.setModalidad("CRONOMETRADA");
        publicada.setDuracionMinutos(90);
        publicada.setPlazoDias(3);
        when(calculo.estructura(PUBLICADA)).thenReturn(conUnCriterio(publicada));

        ResumenDePreguntas resumen = servicio.resumenDe(VACANTE);

        assertThat(resumen).isEqualTo(new ResumenDePreguntas("PUBLICADA", 100, 1, 0, 1, 90, null, false));
    }

    @Test
    @DisplayName("Un borrador de plazo abierto enseña sus días, no unos minutos")
    void resumenDelBorradorDePlazoAbierto() {
        hayBorrador();
        borrador.setModalidad("PLAZO_ABIERTO");
        borrador.setPlazoDias(3);
        borrador.setDuracionMinutos(45);
        when(calculo.estructura(BORRADOR)).thenReturn(new Resultado(borrador, List.of(), List.of(), List.of()));

        ResumenDePreguntas resumen = servicio.resumenDe(VACANTE);

        assertThat(resumen.estado()).isEqualTo("BORRADOR");
        assertThat(resumen.dias()).isEqualTo(3);
        assertThat(resumen.minutos()).isNull();
        assertThat(resumen.cuestionario()).isTrue();
    }

    // ---------------------------------------------------------------- el caso y el tiempo

    @Test
    @DisplayName("Un tiempo imposible no se guarda: 400 con su porqué y el borrador intacto")
    void tiempoImposible() {
        hayBorrador();

        assertThatThrownBy(() -> servicio.guardarDatos(QUIEN, VACANTE,
                new GuardarDatosDeLaPrueba(null, null, null, null, "CRONOMETRADA", 3, null)))
                .isInstanceOf(PreguntasInvalidasException.class)
                .hasMessage("El tiempo no se puede guardar así");
        verify(versionesBanco, never()).save(any());
    }

    @Test
    @DisplayName("Una cronometrada guarda sus minutos y olvida los días; los textos llegan limpios")
    void cronometradaGuardaMinutos() {
        hayBorrador();

        servicio.guardarDatos(QUIEN, VACANTE, new GuardarDatosDeLaPrueba("  Mide el cierre ", " Cuadra la caja ",
                "  ", "Excel", "CRONOMETRADA", 60, 4));

        assertThat(borrador.getGuiaCalificacion()).isEqualTo("Mide el cierre");
        assertThat(borrador.getEnunciado()).isEqualTo("Cuadra la caja");
        assertThat(borrador.getMateriales()).isNull();
        assertThat(borrador.getHerramientasPermitidas()).isEqualTo("Excel");
        assertThat(borrador.getDuracionMinutos()).isEqualTo(60);
        assertThat(borrador.getPlazoDias()).isNull();
        verify(versionesBanco).save(borrador);
    }

    @Test
    @DisplayName("Una de plazo abierto guarda sus días y olvida los minutos")
    void plazoAbiertoGuardaDias() {
        hayBorrador();

        servicio.guardarDatos(QUIEN, VACANTE, new GuardarDatosDeLaPrueba(null, null, null, null,
                "PLAZO_ABIERTO", 60, 4));

        assertThat(borrador.getModalidad()).isEqualTo("PLAZO_ABIERTO");
        assertThat(borrador.getPlazoDias()).isEqualTo(4);
        assertThat(borrador.getDuracionMinutos()).isNull();
    }

    @Test
    @DisplayName("Sin borrador, guardar el caso abre uno nuevo de prueba técnica")
    void guardarAbreElBorrador() {
        servicio.guardarDatos(QUIEN, VACANTE, new GuardarDatosDeLaPrueba(null, "Caso", null, null, null, null, null));

        ArgumentCaptor<VersionBanco> nuevo = ArgumentCaptor.forClass(VersionBanco.class);
        verify(versionesBanco).saveAndFlush(nuevo.capture());
        assertThat(nuevo.getValue().getProposito()).isEqualTo("PRUEBA_PUESTO");
        assertThat(nuevo.getValue().getEtiqueta()).isEqualTo("Prueba técnica · Contador");
        assertThat(nuevo.getValue().getEnunciado()).isEqualTo("Caso");
    }

    // ---------------------------------------------------------------- el enunciado adjunto

    @Test
    @DisplayName("Subir el enunciado sin archivo es 400 y no toca nada")
    void subirSinArchivo() {
        assertThatThrownBy(() -> servicio.subirConsigna(QUIEN, VACANTE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Falta el archivo del enunciado");
        MockMultipartFile vacio = new MockMultipartFile("archivo", "caso.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> servicio.subirConsigna(QUIEN, VACANTE, vacio))
                .isInstanceOf(IllegalArgumentException.class);
        verify(almacen, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("Un enunciado que no es PDF ni Word se rechaza antes de guardarlo")
    void subirOtroFormato() {
        MockMultipartFile texto = new MockMultipartFile("archivo", "caso.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> servicio.subirConsigna(QUIEN, VACANTE, texto))
                .isInstanceOf(IllegalArgumentException.class);
        verify(almacen, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("El enunciado en PDF queda en el borrador con su enlace, y se audita")
    void subirElEnunciado() {
        hayBorrador();
        MockMultipartFile pdf = new MockMultipartFile("archivo", "caso.pdf", "application/pdf", "%PDF".getBytes());
        Archivo guardado = Archivo.builder().id(55L).nombreOriginal("caso.pdf").build();
        when(almacen.guardar(ORGANIZACION, pdf)).thenReturn(guardado);
        when(almacen.urlDeConsigna(guardado)).thenReturn(Optional.of(
                new AlmacenArchivos.EnlaceFirmado("https://enlace/caso.pdf", Instant.now())));

        servicio.subirConsigna(QUIEN, VACANTE, pdf);

        assertThat(borrador.getConsignaArchivoId()).isEqualTo(55L);
        assertThat(borrador.getUrlConsigna()).isEqualTo("https://enlace/caso.pdf");
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("subir_consigna_prueba_propia"),
                eq("version_banco"), eq(BORRADOR), any(), any(), any());
    }

    @Test
    @DisplayName("Quitar el enunciado lo suelta del borrador, enlace incluido")
    void quitarElEnunciado() {
        hayBorrador();
        borrador.setConsignaArchivoId(55L);
        borrador.setUrlConsigna("https://enlace/caso.pdf");

        servicio.quitarConsigna(QUIEN, VACANTE);

        assertThat(borrador.getConsignaArchivoId()).isNull();
        assertThat(borrador.getUrlConsigna()).isNull();
        verify(versionesBanco).save(borrador);
    }

    @Test
    @DisplayName("Quitar el enunciado sin borrador dice que no hay nada que cambiar")
    void quitarSinBorrador() {
        assertThatThrownBy(() -> servicio.quitarConsigna(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("No hay borrador que cambiar");
    }

    @Test
    @DisplayName("Con alguien rindiendo la publicada, abrir un borrador es 409 por la vara")
    void varaQuietaAlAbrir() {
        hayPublicada();
        when(intentos.algunoEmpezadoDeLaVacante(VACANTE)).thenReturn(true);

        assertThatThrownBy(() -> servicio.abrirBorrador(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Alguien ya empezó a rendir esta prueba");
        assertThatThrownBy(() -> servicio.quitarConsigna(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Alguien ya empezó a rendir esta prueba");
    }

    // ---------------------------------------------------------------- criterios

    @Test
    @DisplayName("Un criterio que dice mirar un entregable de otra prueba es 404 y no se crea")
    void criterioQueMiraLoAjeno() {
        hayBorrador();
        when(entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(List.of(entregable(30L, BORRADOR, 1)));

        assertThatThrownBy(() -> servicio.agregarCriterio(QUIEN, VACANTE,
                new GuardarCriterioDePrueba("Excel", null, BigDecimal.TEN, "IA", List.of(30L, 99L))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(criteriosBanco, never()).save(any());
    }

    @Test
    @DisplayName("Un criterio nuevo guarda su parte calificada, quién la califica y lo que mira")
    void criterioConLoQueMira() {
        hayBorrador();
        when(entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(List.of(entregable(30L, BORRADOR, 1)));
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(List.of(criterio(5L, BORRADOR, 2)));

        servicio.agregarCriterio(QUIEN, VACANTE,
                new GuardarCriterioDePrueba(" Excel ", "Fórmulas", BigDecimal.valueOf(20), " IA ", List.of(30L, 30L)));

        ArgumentCaptor<CriterioBanco> nuevo = ArgumentCaptor.forClass(CriterioBanco.class);
        verify(criteriosBanco).save(nuevo.capture());
        assertThat(nuevo.getValue().getNombre()).isEqualTo("Excel");
        assertThat(nuevo.getValue().getOrden()).isEqualTo(3);
        assertThat(nuevo.getValue().getPuntosCalificados()).isEqualTo(20);
        assertThat(nuevo.getValue().getCalificador()).isEqualTo("IA");
        ArgumentCaptor<CriterioBancoEntregable> mira = ArgumentCaptor.forClass(CriterioBancoEntregable.class);
        verify(miradas).save(mira.capture());
        assertThat(mira.getValue().getEntregableRequeridoId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("Una parte calificada con decimales no se guarda")
    void parteCalificadaConDecimales() {
        assertThatThrownBy(() -> servicio.agregarCriterio(QUIEN, VACANTE,
                new GuardarCriterioDePrueba("Excel", null, new BigDecimal("2.5"), "IA", null)))
                .isInstanceOf(PreguntasInvalidasException.class)
                .hasMessage("El criterio no se puede guardar así");
    }

    @Test
    @DisplayName("Sin nombre, el criterio no se crea")
    void criterioSinNombre() {
        hayBorrador();

        assertThatThrownBy(() -> servicio.agregarCriterio(QUIEN, VACANTE,
                new GuardarCriterioDePrueba("  ", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El criterio necesita un nombre");
    }

    @Test
    @DisplayName("Editar un criterio sin parte calificada olvida a su calificador y rehace lo que mira")
    void editarCriterioSinParteCalificada() {
        hayBorrador();
        CriterioBanco c = criterio(5L, BORRADOR, 1);
        c.setPuntosCalificados(20);
        c.setCalificador("IA");
        when(criteriosBanco.findById(5L)).thenReturn(Optional.of(c));

        servicio.editarCriterio(QUIEN, VACANTE, 5L,
                new GuardarCriterioDePrueba("Cierre", "  ", BigDecimal.ZERO, "IA", List.of()));

        assertThat(c.getNombre()).isEqualTo("Cierre");
        assertThat(c.getQueEvalua()).isNull();
        assertThat(c.getPuntosCalificados()).isZero();
        assertThat(c.getCalificador()).isNull();
        verify(miradas).deleteByCriterioBancoId(5L);
        verify(miradas, never()).save(any());
    }

    @Test
    @DisplayName("Editar un criterio de otra versión es 404")
    void editarCriterioAjeno() {
        hayBorrador();
        when(criteriosBanco.findById(5L)).thenReturn(Optional.of(criterio(5L, 999L, 1)));

        assertThatThrownBy(() -> servicio.editarCriterio(QUIEN, VACANTE, 5L,
                new GuardarCriterioDePrueba("Cierre", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Quitar un criterio suelta sus preguntas al final, luego lo que mira, y al final lo borra")
    void quitarCriterioSueltaLoQueMira() {
        hayBorrador();
        CriterioBanco c = criterio(5L, BORRADOR, 1);
        when(criteriosBanco.findById(5L)).thenReturn(Optional.of(c));
        Pregunta suya = pregunta(1L, 5L, 1);
        Pregunta suelta = pregunta(2L, null, 4);
        when(preguntas.findByVersionBancoIdOrderByOrden(BORRADOR)).thenReturn(List.of(suya, suelta));

        servicio.quitarCriterio(QUIEN, VACANTE, 5L);

        assertThat(suya.getCriterioBancoId()).isNull();
        assertThat(suya.getOrden()).isEqualTo(5);
        InOrder orden = inOrder(preguntas, miradas, criteriosBanco);
        orden.verify(preguntas).saveAllAndFlush(List.of(suya));
        orden.verify(miradas).deleteByCriterioBancoId(5L);
        orden.verify(miradas).flush();
        orden.verify(criteriosBanco).delete(c);
    }

    // ---------------------------------------------------------------- preguntas

    @Test
    @DisplayName("La primera pregunta sin criterio crea «General» sin parte calificada")
    void primeraPreguntaCreaGeneral() {
        hayBorrador();

        servicio.agregarPregunta(QUIEN, VACANTE, new GuardarPreguntaDePrueba("ABIERTA", "¿Cómo cuadras?",
                null, null, "Que cuadre", null));

        ArgumentCaptor<CriterioBanco> general = ArgumentCaptor.forClass(CriterioBanco.class);
        verify(criteriosBanco).save(general.capture());
        assertThat(general.getValue().getNombre()).isEqualTo("General");
        assertThat(general.getValue().getPuntosCalificados()).isZero();
        assertThat(general.getValue().getCalificador()).isNull();
        ArgumentCaptor<Pregunta> nueva = ArgumentCaptor.forClass(Pregunta.class);
        verify(preguntas).save(nueva.capture());
        assertThat(nueva.getValue().getPuntos()).isZero();
        assertThat(nueva.getValue().getCriterioBancoId()).isEqualTo(900L);
        assertThat(nueva.getValue().getQueDebeTener()).isEqualTo("Que cuadre");
        assertThat(nueva.getValue().getCodigo()).isEqualTo("P1");
    }

    @Test
    @DisplayName("Una cerrada en un criterio que ya existe guarda sus opciones con letra")
    void cerradaConSusOpciones() {
        hayBorrador();
        when(criteriosBanco.findById(5L)).thenReturn(Optional.of(criterio(5L, BORRADOR, 1)));

        servicio.agregarPregunta(QUIEN, VACANTE, new GuardarPreguntaDePrueba("OPCION_UNICA", "¿Libro?",
                BigDecimal.TEN, 5L, "ignorado", List.of(new GuardarOpcion("Diario", BigDecimal.TEN),
                        new GuardarOpcion("Mayor", BigDecimal.ZERO))));

        ArgumentCaptor<Opcion> opcion = ArgumentCaptor.forClass(Opcion.class);
        verify(opciones, org.mockito.Mockito.times(2)).save(opcion.capture());
        assertThat(opcion.getAllValues()).extracting(Opcion::getLetra).containsExactly("A", "B");
        ArgumentCaptor<Pregunta> nueva = ArgumentCaptor.forClass(Pregunta.class);
        verify(preguntas).save(nueva.capture());
        assertThat(nueva.getValue().getPuntos()).isEqualTo(10);
        assertThat(nueva.getValue().getQueDebeTener()).isNull();
    }

    @Test
    @DisplayName("Una abierta con puntos no se guarda: en la prueba califican el criterio entero")
    void abiertaConPuntos() {
        assertThatThrownBy(() -> servicio.agregarPregunta(QUIEN, VACANTE, new GuardarPreguntaDePrueba(
                "ABIERTA", "¿Algo?", BigDecimal.ONE, null, null, null)))
                .isInstanceOf(PreguntasInvalidasException.class)
                .hasMessage("La pregunta no se puede guardar así");
        assertThatThrownBy(() -> servicio.editarPregunta(QUIEN, VACANTE, 1L, new GuardarPreguntaDePrueba(
                "ABIERTA", "¿Algo?", BigDecimal.ONE, null, null, null)))
                .isInstanceOf(PreguntasInvalidasException.class);
        verify(preguntas, never()).save(any());
    }

    @Test
    @DisplayName("Editar una pregunta y pasarla de criterio la pone al final del nuevo y rehace sus opciones")
    void editarPreguntaCambiaDeCriterio() {
        hayBorrador();
        Pregunta p = pregunta(1L, 5L, 1);
        when(preguntas.findById(1L)).thenReturn(Optional.of(p));
        when(criteriosBanco.findById(6L)).thenReturn(Optional.of(criterio(6L, BORRADOR, 2)));
        when(preguntas.findByVersionBancoIdOrderByOrden(BORRADOR)).thenReturn(List.of(p, pregunta(2L, 6L, 3)));

        servicio.editarPregunta(QUIEN, VACANTE, 1L, new GuardarPreguntaDePrueba("ABIERTA", " ¿Cómo? ",
                null, 6L, " Que explique ", null));

        assertThat(p.getCriterioBancoId()).isEqualTo(6L);
        assertThat(p.getOrden()).isEqualTo(4);
        assertThat(p.getEnunciado()).isEqualTo("¿Cómo?");
        assertThat(p.getPuntos()).isZero();
        assertThat(p.isEsPuntuable()).isFalse();
        assertThat(p.getQueDebeTener()).isEqualTo("Que explique");
        verify(opciones).deleteByPreguntaIdIn(List.of(1L));
        verify(opciones, never()).save(any());
    }

    @Test
    @DisplayName("Editar una pregunta de otra versión es 404")
    void editarPreguntaAjena() {
        hayBorrador();
        Pregunta ajena = pregunta(1L, 5L, 1);
        ajena.setVersionBancoId(999L);
        when(preguntas.findById(1L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> servicio.editarPregunta(QUIEN, VACANTE, 1L, new GuardarPreguntaDePrueba(
                "ABIERTA", "¿Algo?", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- entregables

    @Test
    @DisplayName("Un entregable sin formato no se guarda, ni al agregarlo ni al editarlo")
    void entregableSinFormato() {
        assertThatThrownBy(() -> servicio.agregarEntregable(QUIEN, VACANTE,
                new GuardarEntregable("Tablero", null, null, true, null)))
                .isInstanceOf(PreguntasInvalidasException.class)
                .hasMessage("El entregable no se puede guardar así");
        assertThatThrownBy(() -> servicio.editarEntregable(QUIEN, VACANTE, 30L,
                new GuardarEntregable(" ", null, "ARCHIVO", true, null)))
                .isInstanceOf(PreguntasInvalidasException.class);
        verify(entregables, never()).save(any());
    }

    @Test
    @DisplayName("Un entregable nuevo va al final, con sus textos limpios")
    void entregableNuevo() {
        hayBorrador();
        when(entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(List.of(entregable(30L, BORRADOR, 2)));

        servicio.agregarEntregable(QUIEN, VACANTE, new GuardarEntregable(" Video ", null, "ENLACE", false, " Claro "));

        ArgumentCaptor<EntregableRequerido> nuevo = ArgumentCaptor.forClass(EntregableRequerido.class);
        verify(entregables).save(nuevo.capture());
        assertThat(nuevo.getValue().getNombre()).isEqualTo("Video");
        assertThat(nuevo.getValue().getDetalle()).isEmpty();
        assertThat(nuevo.getValue().getOrden()).isEqualTo(3);
        assertThat(nuevo.getValue().isEsObligatorio()).isFalse();
        assertThat(nuevo.getValue().getQueDebeTener()).isEqualTo("Claro");
    }

    @Test
    @DisplayName("Editar un entregable cambia lo suyo; uno de otra prueba es 404")
    void editarEntregable() {
        hayBorrador();
        EntregableRequerido e = entregable(30L, BORRADOR, 1);
        when(entregables.findById(30L)).thenReturn(Optional.of(e));
        when(entregables.findById(31L)).thenReturn(Optional.of(entregable(31L, 999L, 1)));

        servicio.editarEntregable(QUIEN, VACANTE, 30L, new GuardarEntregable(" Tablero ", null, "CUALQUIERA",
                false, "  "));

        assertThat(e.getNombre()).isEqualTo("Tablero");
        assertThat(e.getDetalle()).isEmpty();
        assertThat(e.getFormato()).isEqualTo("CUALQUIERA");
        assertThat(e.isEsObligatorio()).isFalse();
        assertThat(e.getQueDebeTener()).isNull();
        verify(entregables).save(e);
        assertThatThrownBy(() -> servicio.editarEntregable(QUIEN, VACANTE, 31L,
                new GuardarEntregable("Otro", "x", "ARCHIVO", true, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Quitar un entregable lo saca antes de lo que miran sus criterios")
    void quitarEntregable() {
        hayBorrador();
        EntregableRequerido e = entregable(30L, BORRADOR, 1);
        when(entregables.findById(30L)).thenReturn(Optional.of(e));

        servicio.quitarEntregable(QUIEN, VACANTE, 30L);

        InOrder orden = inOrder(miradas, entregables);
        orden.verify(miradas).deleteByEntregableRequeridoId(30L);
        orden.verify(miradas).flush();
        orden.verify(entregables).delete(e);
    }

    @Test
    @DisplayName("Mover un entregable intercambia puestos y renumera; en el borde no hace nada")
    void moverEntregable() {
        hayBorrador();
        EntregableRequerido primero = entregable(30L, BORRADOR, 1);
        EntregableRequerido segundo = entregable(31L, BORRADOR, 2);
        when(entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(List.of(primero, segundo));

        servicio.moverEntregable(QUIEN, VACANTE, 30L, new Mover("abajo"));

        assertThat(primero.getOrden()).isEqualTo(2);
        assertThat(segundo.getOrden()).isEqualTo(1);
        verify(entregables).saveAll(anyList());

        // El primero de la lista no sube más: no se guarda nada nuevo.
        servicio.moverEntregable(QUIEN, VACANTE, 30L, new Mover("ARRIBA"));
        verify(entregables).saveAll(anyList());
    }

    @Test
    @DisplayName("Mover hacia un lado que no existe es 400, y un entregable ajeno, 404")
    void moverMal() {
        hayBorrador();
        when(entregables.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(List.of(entregable(30L, BORRADOR, 1)));

        assertThatThrownBy(() -> servicio.moverEntregable(QUIEN, VACANTE, 30L, new Mover("IZQUIERDA")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La dirección es ARRIBA o ABAJO");
        assertThatThrownBy(() -> servicio.moverEntregable(QUIEN, VACANTE, 99L, new Mover("ARRIBA")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- descartar

    @Test
    @DisplayName("Descartar el borrador suelta lo que miran sus criterios y borra todo lo suyo")
    void descartarElBorrador() {
        hayBorrador();
        when(criteriosBanco.findByVersionBancoIdOrderByOrdenAscIdAsc(BORRADOR)).thenReturn(List.of(criterio(5L, BORRADOR, 1)));
        when(preguntas.findByVersionBancoIdOrderByOrden(BORRADOR)).thenReturn(List.of(pregunta(1L, 5L, 1)));

        servicio.descartarBorrador(QUIEN, VACANTE);

        InOrder orden = inOrder(miradas, opciones, preguntas, entregables, criteriosBanco, versionesBanco);
        orden.verify(miradas).deleteByCriterioBancoIdIn(List.of(5L));
        orden.verify(opciones).deleteByPreguntaIdIn(List.of(1L));
        orden.verify(preguntas).deleteByVersionBancoId(BORRADOR);
        orden.verify(entregables).deleteByVersionBancoId(BORRADOR);
        orden.verify(criteriosBanco).deleteByVersionBancoId(BORRADOR);
        orden.verify(versionesBanco).delete(borrador);
    }

    @Test
    @DisplayName("Descartar un borrador vacío no busca opciones ni miradas")
    void descartarVacio() {
        hayBorrador();

        EditorDePreguntas editor = servicio.descartarBorrador(QUIEN, VACANTE);

        verify(miradas, never()).deleteByCriterioBancoIdIn(any());
        verify(opciones, never()).deleteByPreguntaIdIn(any());
        verify(versionesBanco).delete(borrador);
        assertThat(editor.proposito()).isEqualTo("PRUEBA_PUESTO");
    }
}
