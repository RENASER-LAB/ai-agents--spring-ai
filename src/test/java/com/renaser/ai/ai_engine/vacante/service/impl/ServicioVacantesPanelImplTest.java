package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreo;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreoVacante;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.AsignarPlantillaCorreo;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.BancoFijo;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.PruebaPrestada;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.DefinirCierrePrueba;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.GuardarPuesto;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.GuardarVacante;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que cada vacante configura para sí sola: si aplica el banco (V30), qué textos de correo
 * manda (V31) y cuándo cierra su prueba (V32).
 *
 * <p>Lo que se protege aquí es el orden de los avisos: cada regla tiene que fallar en la
 * cara de quien configura, nunca en la del candidato.
 *
 * <ul>
 *   <li><b>Publicar sin plantilla de evaluación</b> solo puede pasar si la evaluación está
 *       apagada. Si está encendida y falta la plantilla, el primer candidato chocaría al
 *       postular; el error tiene que salir al publicar.
 *   <li><b>Volver a encender la evaluación</b> en una vacante publicada exige que la
 *       plantilla ya esté elegida, por el mismo motivo.
 *   <li><b>Los pesos de una vacante</b> solo se cambian a una versión publicada (RF-114):
 *       una en borrador todavía puede no sumar 100.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Lo que cada vacante configura para sí sola")
class ServicioVacantesPanelImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 40L;
    private static final Long VERSION_PESOS = 9L;
    private static final Long PLATAFORMA = 99L;
    private static final Long PUESTO = 5L;
    private static final String NIVEL = "EJECUCION";

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private RequisitoObjetivoRepository requisitos;
    @Mock private SolicitudTalentoRepository solicitudes;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private PlantillaEvaluacionRepository plantillas;
    @Mock private VersionPlantillaPruebaRepository versionesPrueba;
    @Mock private com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository plantillasPrueba;
    @Mock private PlantillaCorreoRepository plantillasCorreo;
    @Mock private PlantillaCorreoVacanteRepository plantillasPorVacante;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository evaluaciones;
    @Mock private ServicioAuditoria auditoria;
    @Mock private com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento dueno;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository postulaciones;
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository versionesBanco;

    // Las de contarle a la gente lo que cambió en su vacante (V56, V58). Son dobles: lo que
    // hacen de verdad lo comprueban sus propias pruebas; aquí se vigila A QUIÉN se llama.
    @Mock private com.renaser.ai.ai_engine.postulacion.service.PostulacionesEnCarrera enCarrera;
    @Mock private com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados maquina;
    @Mock private com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal avisos;
    @Mock private com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante alcance;
    @Mock private com.renaser.ai.ai_engine.seguridad.service.Permisos permisos;
    @Mock private com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil catalogos;

    private ServicioVacantesPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioVacantesPanelImpl(vacantes, puestos, requisitos, solicitudes,
                versionesPesos, plantillas, versionesPrueba, plantillasPrueba, plantillasCorreo,
                plantillasPorVacante, intentos, evaluaciones, versionesBanco,
                auditoria, dueno, postulaciones, enCarrera, maquina, avisos, alcance, permisos, catalogos,
                new com.renaser.ai.ai_engine.prueba.service.FechaLimiteDeLaVacante(vacantes, intentos,
                        versionesPrueba, versionesBanco));
        // En estas pruebas la organizacion no personaliza nada: el resolutor contesta
        // que el dueño de todo instrumento es ella misma (aqui hace de plataforma).
        org.mockito.Mockito.lenient()
                .when(dueno.duenoDe(org.mockito.ArgumentMatchers.eq(ORGANIZACION),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(ORGANIZACION);
        // RENASER, de donde vino lo que una vacante vieja conserva prestado (V74).
        org.mockito.Mockito.lenient().when(dueno.plataforma()).thenReturn(
                com.renaser.ai.ai_engine.organizacion.entity.Organizacion.builder()
                        .id(PLATAFORMA).esPlataforma(true).build());
        // El puesto de la vacante y el banco publicado de su nivel: sin los dos, publicar
        // con la evaluacion encendida falla, y eso lo comprueba su propia prueba apagando
        // este mock. Lenient porque la mitad de estas pruebas no llegan a mirarlos.
        org.mockito.Mockito.lenient()
                .when(puestos.findById(PUESTO))
                .thenReturn(Optional.of(com.renaser.ai.ai_engine.vacante.entity.Puesto.builder()
                        .id(PUESTO).nivelPuestoCodigo(NIVEL).build()));
        // El mismo puesto leído dentro de la empresa: con él se sabe si el banco del nivel es
        // PROPIO o prestado (V66). Sin este, el nivel sale nulo y toda vacante parece prestada.
        org.mockito.Mockito.lenient()
                .when(puestos.findByIdAndOrganizacionId(PUESTO, ORGANIZACION))
                .thenReturn(Optional.of(com.renaser.ai.ai_engine.vacante.entity.Puesto.builder()
                        .id(PUESTO).organizacionId(ORGANIZACION).nivelPuestoCodigo(NIVEL).build()));
        org.mockito.Mockito.lenient()
                .when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.of(com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco
                        .builder().id(15L).tipoBanco("NIVEL").nivelPuestoCodigo(NIVEL)
                        .estado("PUBLICADA").minutosObjetivo(35).build()));
        // Y su plantilla, que publicar tambien exige: crearAlPostular resuelve las dos.
        org.mockito.Mockito.lenient()
                .when(plantillas.laPublicadaDelNivel(ORGANIZACION, NIVEL))
                .thenReturn(Optional.of(
                        com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion
                                .builder().id(3L).nivelPuestoCodigo(NIVEL)
                                .estado("PUBLICADA").build()));
    }

    /**
     * La vacante como la leen las consultas (suelta) y las escrituras (con la fila bloqueada,
     * QA-F05). Cada prueba usa una de las dos, así que las dos van {@code lenient}.
     */
    private Vacante vacante(String estado, boolean aplicaEvaluacion, Long plantillaEvaluacionId) {
        Vacante v = nuevaVacante(estado, aplicaEvaluacion, plantillaEvaluacionId);
        org.mockito.Mockito.lenient()
                .when(vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(v));
        org.mockito.Mockito.lenient()
                .when(vacantes.bloquear(VACANTE, ORGANIZACION)).thenReturn(Optional.of(v));
        return v;
    }

    /**
     * La vacante como la leen el interruptor del pase y su nota mínima: con la fila bloqueada
     * (QA-F04). Sin bloquear, la lectura suelta devolvería vacío y el método contestaría 404.
     */
    private Vacante vacanteDelPase(String estado) {
        Vacante v = nuevaVacante(estado, true, 7L);
        when(vacantes.bloquear(VACANTE, ORGANIZACION)).thenReturn(Optional.of(v));
        return v;
    }

    private static Vacante nuevaVacante(String estado, boolean aplicaEvaluacion, Long plantillaEvaluacionId) {
        return Vacante.builder()
                .id(VACANTE)
                .organizacionId(ORGANIZACION)
                .estado(estado)
                .aplicaEvaluacion(aplicaEvaluacion)
                .plantillaEvaluacionId(plantillaEvaluacionId)
                .puestoId(PUESTO)
                .versionPlantillaPruebaId(31L)
                .build();
    }

    // ============ El puesto nace con la solicitud ============

    @Test
    @DisplayName("genera un código legible y resuelve colisiones dentro de la empresa")
    void generaCodigoDePuesto() {
        when(puestos.existsByOrganizacionIdAndCodigo(ORGANIZACION, "COORDINADOR_DE_SEDE"))
                .thenReturn(true);
        when(puestos.existsByOrganizacionIdAndCodigo(ORGANIZACION, "COORDINADOR_DE_SEDE_2"))
                .thenReturn(false);
        when(puestos.save(any())).thenAnswer(invocacion -> {
            Puesto puesto = invocacion.getArgument(0);
            puesto.setId(PUESTO);
            return puesto;
        });

        servicio.crearPuesto(QUIEN,
                new GuardarPuesto(null, "Coordinador de Sede", "SUPERVISION", "OPERACIONES"));

        ArgumentCaptor<Puesto> guardado = ArgumentCaptor.forClass(Puesto.class);
        verify(puestos).save(guardado.capture());
        assertThat(guardado.getValue().getCodigo()).isEqualTo("COORDINADOR_DE_SEDE_2");
    }

    @Test
    @DisplayName("la vacante hereda el puesto de su solicitud")
    void heredaElPuestoDeLaSolicitud() {
        SolicitudTalento solicitud = solicitud(PUESTO);
        when(solicitudes.findByIdAndOrganizacionId(30L, ORGANIZACION))
                .thenReturn(Optional.of(solicitud));
        when(puestos.findByIdAndOrganizacionId(PUESTO, ORGANIZACION))
                .thenReturn(Optional.of(puestoActivo()));
        when(versionesPesos.findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(
                ORGANIZACION, "PUBLICADA"))
                .thenReturn(Optional.of(VersionPesos.builder().id(VERSION_PESOS).build()));
        when(vacantes.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.crear(QUIEN, guardar(null));

        ArgumentCaptor<Vacante> guardada = ArgumentCaptor.forClass(Vacante.class);
        verify(vacantes).save(guardada.capture());
        assertThat(guardada.getValue().getPuestoId()).isEqualTo(PUESTO);
    }

    @Test
    @DisplayName("una solicitud moderna rechaza que el cliente cambie su puesto")
    void rechazaUnPuestoDistintoAlDeLaSolicitud() {
        when(solicitudes.findByIdAndOrganizacionId(30L, ORGANIZACION))
                .thenReturn(Optional.of(solicitud(PUESTO)));

        assertThatThrownBy(() -> servicio.crear(QUIEN, guardar(99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no coincide");
    }

    @Test
    @DisplayName("una solicitud histórica adopta el puesto elegido al crear la vacante")
    void resuelveElPuestoDeUnaSolicitudHistorica() {
        SolicitudTalento solicitud = solicitud(null);
        when(solicitudes.findByIdAndOrganizacionId(30L, ORGANIZACION))
                .thenReturn(Optional.of(solicitud));
        when(puestos.findByIdAndOrganizacionId(PUESTO, ORGANIZACION))
                .thenReturn(Optional.of(puestoActivo()));
        when(versionesPesos.findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(
                ORGANIZACION, "PUBLICADA"))
                .thenReturn(Optional.of(VersionPesos.builder().id(VERSION_PESOS).build()));
        when(vacantes.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.crear(QUIEN, guardar(PUESTO));

        assertThat(solicitud.getPuestoId()).isEqualTo(PUESTO);
        assertThat(solicitud.getNivelPuestoCodigo()).isEqualTo(NIVEL);
        assertThat(solicitud.getFamiliaCodigo()).isEqualTo("OPERACIONES");
        verify(solicitudes).save(solicitud);
    }

    private SolicitudTalento solicitud(Long puestoId) {
        return SolicitudTalento.builder()
                .id(30L)
                .organizacionId(ORGANIZACION)
                .puestoId(puestoId)
                .estado("ABIERTA")
                .build();
    }

    private Puesto puestoActivo() {
        return Puesto.builder()
                .id(PUESTO)
                .organizacionId(ORGANIZACION)
                .nivelPuestoCodigo(NIVEL)
                .familiaCodigo("OPERACIONES")
                .esActivo(true)
                .build();
    }

    private GuardarVacante guardar(Long puestoId) {
        return new GuardarVacante(30L, puestoId, "Coordinador de sede", "Descripción",
                null, null, null, null, null, null, null, null, "MANUAL", 1,
                null, null, QUIEN.usuarioId(), null);
    }

    // ============ Publicar ============

    @Test
    @DisplayName("con la evaluación apagada se publica sin plantilla de evaluación")
    void sinEvaluacionSePublicaSinPlantilla() {
        Vacante v = vacante("BORRADOR", false, null);

        servicio.publicar(QUIEN, VACANTE);

        assertThat(v.getEstado()).isEqualTo("PUBLICADA");
        verify(vacantes).save(v);
    }

    @Test
    @DisplayName("con la evaluación encendida se publica SIN plantilla: la resuelve el nivel")
    void conEvaluacionYaNoHaceFaltaElegirPlantilla() {
        // Era una pregunta con una sola respuesta legal —hay una plantilla publicada por
        // nivel, y asignarPlantillaEvaluacion ya rechazaba las de otro— y encima la plantilla
        // dejo de decidir que preguntas caen cuando se retiraron las cuotas. Bloquear la
        // publicacion por ella era pedir algo que el sistema sabe calcular.
        Vacante v = vacante("BORRADOR", true, null);

        servicio.publicar(QUIEN, VACANTE);

        assertThat(v.getEstado()).isEqualTo("PUBLICADA");
        verify(vacantes).save(v);
    }

    @Test
    @DisplayName("con la evaluación encendida y sin banco del nivel, publicar se frena")
    void conEvaluacionElBancoSiEsObligatorio() {
        // Lo que de verdad falta cuando no hay examen posible es el BANCO, y ese error salia
        // en crearAlPostular: encima del candidato que acababa de mandar su curriculum.
        vacante("BORRADOR", true, null);
        when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.publicar(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("banco de preguntas publicado para el nivel " + NIVEL);
    }

    @Test
    @DisplayName("sin banco del nivel pero con la evaluación apagada, se publica igual")
    void sinBancoPeroSinEvaluacionSePublica() {
        // Su unica evaluacion es la prueba del puesto: exigirle un banco seria frenarla por
        // algo que nadie va a responder.
        Vacante v = vacante("BORRADOR", false, null);
        org.mockito.Mockito.lenient()
                .when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());

        servicio.publicar(QUIEN, VACANTE);

        assertThat(v.getEstado()).isEqualTo("PUBLICADA");
    }

    @Test
    @DisplayName("sin plantilla publicada del nivel también se frena, y lo dice")
    void conEvaluacionLaPlantillaDelNivelTambienHaceFalta() {
        // ⚠️ `crearAlPostular` resuelve DOS instrumentos y los dos pueden faltar. Mientras la
        // vacante estaba obligada a elegir plantilla este camino no existía; desde que se
        // resuelve sola, no comprobarla aquí deja el error para el primer candidato.
        vacante("BORRADOR", true, null);
        when(plantillas.laPublicadaDelNivel(ORGANIZACION, NIVEL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.publicar(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plantilla de evaluación publicada para el nivel " + NIVEL);
    }

    // Aquí vivía «sin el texto legal publicado no se publica la vacante». Se cayó con la
    // V54: el texto de PROCESO es uno solo para todas, con un hueco donde va el nombre de
    // la empresa, así que ya no hay nada que publicar y el freno no podía saltar nunca.
    // La comprobación de que sin texto no se firma sigue viva un piso más abajo, en
    // ServicioPostulacionPortalImplTest#sinTextoPublicadoPostularSeFrena.

    // ============ El interruptor ============

    @Test
    @DisplayName("apagar la evaluación queda guardado y auditado")
    void apagarLaEvaluacionSeGuarda() {
        Vacante v = vacante("PUBLICADA", true, 7L);

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, false);

        assertThat(v.isAplicaEvaluacion()).isFalse();
        verify(vacantes).save(v);
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "definir_aplicacion_evaluacion",
                "vacante", VACANTE, Map.of("aplicaEvaluacion", true),
                Map.of("aplicaEvaluacion", false), null);
    }

    @Test
    @DisplayName("poner la vacante en automático queda guardado y auditado")
    void encenderElAutomaticoSeGuarda() {
        Vacante v = vacanteDelPase("PUBLICADA");

        servicio.activarCalificacionAutomatica(QUIEN, VACANTE, true);

        assertThat(v.isCalificacionAutomatica()).isTrue();
        verify(vacantes).save(v);
        // La lee bloqueada: la mínima puede estar guardándose a la vez (QA-F04)
        verify(vacantes).bloquear(VACANTE, ORGANIZACION);
        verify(vacantes, never()).findByIdAndOrganizacionIdAndEliminadaEnIsNull(VACANTE, ORGANIZACION);
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "activar_calificacion_automatica",
                "vacante", VACANTE, Map.of("calificacionAutomatica", false),
                Map.of("calificacionAutomatica", true), null);
    }

    @Test
    @DisplayName("el automático no se toca en una vacante cerrada")
    void elAutomaticoDeUnaCerradaNoSeToca() {
        vacanteDelPase("CERRADA");

        assertThatThrownBy(() -> servicio.activarCalificacionAutomatica(QUIEN, VACANTE, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cerrada");

        verify(vacantes, never()).save(any(Vacante.class));
    }

    // ============ La nota mínima del Perfil Integral (V72) ============

    @Test
    @DisplayName("la nota mínima se cambia con el interruptor encendido, y queda auditado de cuánto a cuánto (AC-09)")
    void laNotaMinimaSeGuardaYSeAudita() {
        Vacante v = vacanteDelPase("PUBLICADA");
        v.setCalificacionAutomatica(true);
        assertThat(v.getNotaMinima()).isEqualByComparingTo("60");

        servicio.fijarNotaMinima(QUIEN, VACANTE, new BigDecimal("70"));

        assertThat(v.getNotaMinima()).isEqualByComparingTo("70");
        verify(vacantes).save(v);
        // La lee bloqueada: el interruptor puede estar guardándose a la vez (QA-F04)
        verify(vacantes).bloquear(VACANTE, ORGANIZACION);
        verify(vacantes, never()).findByIdAndOrganizacionIdAndEliminadaEnIsNull(VACANTE, ORGANIZACION);
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "fijar_nota_minima", "vacante", VACANTE,
                Map.of("notaMinima", 60), Map.of("notaMinima", 70), null);
    }

    @Test
    @DisplayName("la nota mínima admite los extremos 0 y 100, y «70.0» es el entero 70")
    void laNotaMinimaAdmiteLosExtremos() {
        Vacante v = vacanteDelPase("PUBLICADA");
        v.setCalificacionAutomatica(true);

        servicio.fijarNotaMinima(QUIEN, VACANTE, BigDecimal.ZERO);
        assertThat(v.getNotaMinima()).isEqualByComparingTo("0");
        servicio.fijarNotaMinima(QUIEN, VACANTE, new BigDecimal("100"));
        assertThat(v.getNotaMinima()).isEqualByComparingTo("100");
        servicio.fijarNotaMinima(QUIEN, VACANTE, new BigDecimal("70.0"));
        assertThat(v.getNotaMinima()).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("la nota mínima rechaza decimales y lo que no está entre 0 y 100, sin guardar nada (AC-09)")
    void laNotaMinimaRechazaOtrosValores() {
        Vacante v = vacanteDelPase("PUBLICADA");
        v.setCalificacionAutomatica(true);

        for (String mala : new String[] {"70.5", "-1", "101", "59.99"}) {
            assertThatThrownBy(() -> servicio.fijarNotaMinima(QUIEN, VACANTE, new BigDecimal(mala)))
                    .as(mala)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La nota mínima es un número entero de 0 a 100");
        }
        assertThatThrownBy(() -> servicio.fijarNotaMinima(QUIEN, VACANTE, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(v.getNotaMinima()).isEqualByComparingTo("60");
        verify(vacantes, never()).save(any(Vacante.class));
        org.mockito.Mockito.verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("la nota mínima no se toca con el interruptor apagado ni en una vacante cerrada")
    void laNotaMinimaSoloConElInterruptor() {
        Vacante v = vacanteDelPase("PUBLICADA");

        assertThatThrownBy(() -> servicio.fijarNotaMinima(QUIEN, VACANTE, new BigDecimal("70")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Calificar y avanzar sola");

        v.setCalificacionAutomatica(true);
        v.setEstado("CERRADA");
        assertThatThrownBy(() -> servicio.fijarNotaMinima(QUIEN, VACANTE, new BigDecimal("70")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cerrada");

        verify(vacantes, never()).save(any(Vacante.class));
    }

    @Test
    @DisplayName("bajar la mínima no mueve a nadie: no toca postulaciones ni la máquina (AC-10)")
    void bajarLaMinimaNoMueveANadie() {
        Vacante v = vacanteDelPase("PUBLICADA");
        v.setCalificacionAutomatica(true);
        v.setNotaMinima(new BigDecimal("70"));

        servicio.fijarNotaMinima(QUIEN, VACANTE, new BigDecimal("60"));

        org.mockito.Mockito.verifyNoInteractions(maquina, postulaciones);
    }

    // La tuerca desactiva la mínima con este booleano (casos límite: «sin permiso, el campo se
    // ve desactivado»). Tiene que decir lo mismo que la puerta de los endpoints del pase: el
    // permiso del interruptor y una vacante que aún se puede tocar. Ni más estricto —dejaría
    // fuera a RESPONSABLE_AREA, que la fija— ni más laxo —enseñaría editable lo que da 403—.

    /** RESPONSABLE_AREA: configura el pase (acotado a sus vacantes) pero no edita la vacante. */
    private static final ContextoUsuario CONFIGURA_EL_PASE = new ContextoUsuario(
            13L, 4L, ORGANIZACION, "EQUIPO", List.of(5L),
            Map.of("ver_vacantes", "TODO", "elegir_plantilla_evaluacion", "SUS_VACANTES"));

    /** Ve las vacantes y nada más. */
    private static final ContextoUsuario SOLO_VE = new ContextoUsuario(
            14L, 6L, ORGANIZACION, "EQUIPO", List.of(7L), Map.of("ver_vacantes", "TODO"));

    @Test
    @DisplayName("con el permiso del interruptor, la vacante dice que puede configurar el pase aunque no pueda editarla (QA-F03)")
    void conElPermisoDelInterruptorConfiguraElPase() {
        vacante("PUBLICADA", true, 7L);

        var panel = servicio.detalle(CONFIGURA_EL_PASE, VACANTE);

        assertThat(panel.puedeConfigurarElPase()).isTrue();
        assertThat(panel.puedeEditar())
                .as("sin editar_vacante: el lápiz no, la mínima sí")
                .isFalse();
    }

    @Test
    @DisplayName("sin el permiso del interruptor no puede configurar el pase, ni en el detalle ni en la lista (QA-F03)")
    void sinElPermisoDelInterruptorNoConfiguraElPase() {
        Vacante v = vacante("PUBLICADA", true, 7L);
        when(vacantes.findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(
                ORGANIZACION)).thenReturn(List.of(v));
        when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of());

        assertThat(servicio.detalle(SOLO_VE, VACANTE).puedeConfigurarElPase()).isFalse();
        assertThat(servicio.listar(SOLO_VE, false).get(0).puedeConfigurarElPase()).isFalse();
        assertThat(servicio.listar(CONFIGURA_EL_PASE, false).get(0).puedeConfigurarElPase())
                .as("la fila de la lista lo calcula igual que el detalle")
                .isTrue();
    }

    @Test
    @DisplayName("una vacante cerrada o archivada no deja configurar el pase ni con el permiso: la API daría 409")
    void cerradaOArchivadaNoConfiguraElPase() {
        Vacante v = vacante("CERRADA", true, 7L);

        assertThat(servicio.detalle(CONFIGURA_EL_PASE, VACANTE).puedeConfigurarElPase()).isFalse();

        v.setArchivadaEn(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(servicio.detalle(CONFIGURA_EL_PASE, VACANTE).puedeConfigurarElPase()).isFalse();
    }

    @Test
    @DisplayName("volver a encenderla en una publicada sin banco propio pasa a sus preguntas "
            + "propias y exige tenerlas publicadas")
    void encenderlaPublicadaSinBancoAvisaAqui() {
        // El aviso tiene que salir aqui: encenderla sin de donde sacar las preguntas dejaria
        // al siguiente candidato chocando contra un error al postular. Desde la V66, sin banco
        // PROPIO del nivel no se rinde el banco: lo que falta son sus preguntas publicadas.
        vacante("PUBLICADA", false, null);
        when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("preguntas propias");
        verify(vacantes, never()).save(any(Vacante.class));
    }

    @Test
    @DisplayName("QA-PP-04: apagar por el interruptor viejo una que rinde el banco prestado lo "
            + "deja, y al encenderla no vuelve (AC-01c)")
    void elBancoPrestadoNoVuelvePorElInterruptorViejo() {
        // Sin banco PROPIO del nivel: lo que rendia era el de RENASER, prestado.
        Vacante v = vacante("BORRADOR", true, null);
        when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, false);

        assertThat(v.isAplicaEvaluacion()).isFalse();
        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_VACANTE);
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "definir_aplicacion_evaluacion",
                "vacante", VACANTE,
                Map.of("aplicaEvaluacion", true, "origenPreguntas", Vacante.ORIGEN_NIVEL),
                Map.of("aplicaEvaluacion", false, "origenPreguntas", Vacante.ORIGEN_VACANTE), null);

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true);

        assertThat(v.isAplicaEvaluacion()).isTrue();
        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_VACANTE);
    }

    @Test
    @DisplayName("QA-PP-04: encender una de antes que estaba apagada no estrena el banco prestado")
    void encenderUnaApagadaNoEstrenaElPrestado() {
        Vacante v = vacante("BORRADOR", false, null);
        when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true);

        assertThat(v.isAplicaEvaluacion()).isTrue();
        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_VACANTE);
    }

    @Test
    @DisplayName("QA-PP-04: dejarla encendida no toca el banco prestado que ya rinde (AC-01c)")
    void dejarlaEncendidaNoCambiaElOrigen() {
        Vacante v = vacante("BORRADOR", true, null);
        org.mockito.Mockito.lenient().when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true);

        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_NIVEL);
    }

    @Test
    @DisplayName("QA-PP-04: con banco propio del nivel, apagar y encender lo conserva")
    void conBancoPropioElOrigenSeConserva() {
        Vacante v = vacante("BORRADOR", true, null);

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, false);
        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true);

        assertThat(v.isAplicaEvaluacion()).isTrue();
        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_NIVEL);
    }

    @Test
    @DisplayName("QA-PP-08: con postulantes, apagar y encender por el interruptor viejo no "
            + "cambia de dónde salen las preguntas (AC-16)")
    void conPostulantesElInterruptorViejoNoMueveElOrigen() {
        // Rinde el banco prestado y ya hay alguien medido con él: apagarla y volver a
        // encenderla tiene que devolverle ese mismo banco, como antes de la V66.
        Vacante v = vacante("PUBLICADA", true, null);
        elBancoDelNivelEsPrestado(v);
        when(postulaciones.countByVacanteId(VACANTE)).thenReturn(1L);

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, false);

        assertThat(v.isAplicaEvaluacion()).isFalse();
        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_NIVEL);
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "definir_aplicacion_evaluacion",
                "vacante", VACANTE, Map.of("aplicaEvaluacion", true),
                Map.of("aplicaEvaluacion", false), null);

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true);

        assertThat(v.isAplicaEvaluacion()).isTrue();
        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_NIVEL);
        verify(versionesBanco, never()).preguntasPropiasDe(VACANTE, "PUBLICADA");
    }

    @Test
    @DisplayName("QA-PP-08: con postulantes, encender una que estaba apagada tampoco le cambia "
            + "el origen")
    void conPostulantesEncenderUnaApagadaConservaElOrigen() {
        Vacante v = vacante("PUBLICADA", false, null);
        elBancoDelNivelEsPrestado(v);
        when(postulaciones.countByVacanteId(VACANTE)).thenReturn(2L);

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true);

        assertThat(v.isAplicaEvaluacion()).isTrue();
        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_NIVEL);
        // Encenderla publicada exige banco: le basta su versión fija, aunque RENASER ya la
        // haya archivado al publicar otra (V74).
        assertThat(v.getVersionBancoFijaId()).isEqualTo(16L);
        verify(versionesBanco, never()).laPublicadaDelNivel(PLATAFORMA, "NIVEL", NIVEL);
        verify(vacantes).save(v);
    }

    /**
     * Sin banco PROPIO del nivel, el que rinde es el de RENASER: desde la V74 no lo resuelve
     * nadie en cada postulación, la vacante vieja lo tiene fijado en su versión fija.
     */
    private void elBancoDelNivelEsPrestado(Vacante v) {
        org.mockito.Mockito.lenient()
                .when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());
        v.setVersionBancoFijaId(16L);
        org.mockito.Mockito.lenient()
                .when(versionesBanco.findById(16L))
                .thenReturn(Optional.of(com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco
                        .builder().id(16L).organizacionId(PLATAFORMA).tipoBanco("NIVEL")
                        .nivelPuestoCodigo(NIVEL).etiqueta("Banco RENASER v3")
                        .estado("ARCHIVADA").minutosObjetivo(35).build()));
    }

    @Test
    @DisplayName("QA-PP-08: publicada y sin postulantes, apagar el banco prestado sí lo deja "
            + "(AC-01c)")
    void publicadaSinPostulantesElPrestadoSeDeja() {
        // La línea es la primera postulación, no la publicación: antes de ella, el
        // préstamo se deja al apagarla igual que en un borrador.
        Vacante v = vacante("PUBLICADA", true, null);
        when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());
        when(postulaciones.countByVacanteId(VACANTE)).thenReturn(0L);
        v.setVersionBancoFijaId(16L);

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, false);

        assertThat(v.isAplicaEvaluacion()).isFalse();
        assertThat(v.getOrigenPreguntas()).isEqualTo(Vacante.ORIGEN_VACANTE);
        // Y suelta la versión fija del banco de RENASER: ya no la va a rendir (V74)
        assertThat(v.getVersionBancoFijaId()).isNull();
    }

    @Test
    @DisplayName("una vacante sin banco propio ni versión fija no se publica con el banco del nivel: "
            + "el de RENASER ya no se presta (V74)")
    void sinBancoPropioNiFijaNoHayExamen() {
        Vacante v = vacante("BORRADOR", true, null);
        when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.publicar(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay ningún banco de preguntas publicado");
        assertThat(v.getEstado()).isEqualTo("BORRADOR");
        // Y no se busca en el de RENASER: el banco no es un instrumento prestable
        verify(versionesBanco, never()).laPublicadaDelNivel(PLATAFORMA, "NIVEL", NIVEL);
    }

    @Test
    @DisplayName("el detalle de una vacante vieja trae su versión fija del banco y la prueba de "
            + "RENASER que ya tenía, con su nombre (V74)")
    void elDetalleTraeLoQueConservaDeRenaser() {
        Vacante v = vacante("PUBLICADA", true, null);
        elBancoDelNivelEsPrestado(v);
        when(versionesPrueba.laDeLaOrganizacion(31L, ORGANIZACION)).thenReturn(Optional.empty());
        when(versionesPrueba.laDeLaOrganizacion(31L, PLATAFORMA)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(31L).plantillaPruebaId(300L).version(2)
                        .modalidad("CRONOMETRADA").duracionMinutos(60).build()));
        when(plantillasPrueba.findByIdAndOrganizacionId(300L, PLATAFORMA)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba.builder().id(300L)
                        .organizacionId(PLATAFORMA).nombre("Prueba de Administrador").build()));

        var panel = servicio.detalle(QUIEN, VACANTE);

        assertThat(panel.bancoFijo()).isEqualTo(
                new BancoFijo(16L, "Banco RENASER v3", 35));
        assertThat(panel.pruebaPrestada()).isEqualTo(
                new PruebaPrestada(31L, 300L, "Prueba de Administrador", 2));
        // La etapa técnica sigue leyendo esa misma prueba por su id: su plazo no se pierde (AC-12)
        assertThat(panel.modalidadPrueba()).isEqualTo("CRONOMETRADA");
        assertThat(panel.minutosPruebaVigentes()).isEqualTo(60);
    }

    @Test
    @DisplayName("con su propia prueba asignada, el detalle no habla de nada prestado")
    void conSuPropiaPruebaNadaPrestado() {
        vacante("PUBLICADA", false, null);
        laVersionDeLaEmpresa(VersionPlantillaPrueba.builder().id(31L)
                .modalidad("CRONOMETRADA").duracionMinutos(30).build());

        var panel = servicio.detalle(QUIEN, VACANTE);

        assertThat(panel.pruebaPrestada()).isNull();
        assertThat(panel.bancoFijo()).isNull();
    }

    @Test
    @DisplayName("con banco del nivel, encenderla en una publicada ya no pide plantilla")
    void encenderlaPublicadaConBancoBasta() {
        Vacante v = vacante("PUBLICADA", false, null);

        servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true);

        assertThat(v.isAplicaEvaluacion()).isTrue();
        verify(vacantes).save(v);
    }

    @Test
    @DisplayName("una vacante cerrada no cambia de interruptor")
    void unaCerradaNoSeToca() {
        vacante("CERRADA", true, 7L);

        assertThatThrownBy(() -> servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cerrada");
    }

    // ============ Los pesos de la vacante ============
    // Se eligen y se cambian en ServicioPesosDeLaVacante (V74): ver su propia prueba.

    // ============ Cuándo cierra la prueba de la vacante ============

    @Test
    @DisplayName("fijar la fecha mueve a los que ya están dentro, no solo a los que entren")
    void laFechaMueveALosQueYaEstanDentro() {
        Vacante v = vacante("PUBLICADA", false, null);
        // Relativa al reloj y no escrita a mano: definirCierrePrueba rechaza una fecha ya
        // pasada, así que un literal futuro caduca solo y revienta el día que le llega.
        Instant domingo = Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);
        IntentoPrueba heredado = IntentoPrueba.builder()
                .id(1L).postulacionId(11L)
                .venceEn(domingo.plus(5, java.time.temporal.ChronoUnit.DAYS))
                .plazoPropio(false).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(heredado));

        var salida = servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(domingo, "Cierre único de la convocatoria"));

        assertThat(v.getPruebaCierraEn()).isEqualTo(domingo);
        assertThat(heredado.getVenceEn())
                .as("si solo valiera para los que entran después, media tanda cerraría otro día")
                .isEqualTo(domingo);
        assertThat(salida.intentosMovidos()).isEqualTo(1);
        verify(intentos).save(heredado);
    }

    @Test
    @DisplayName("a quien le dieron más horas a mano no se le tocan")
    void aQuienTienePlazoPropioNoSeLeToca() {
        vacante("PUBLICADA", false, null);
        // La suya vence más tarde que el cierre de la convocatoria: eso es exactamente lo que
        // significa «más horas a mano». Las dos relativas al reloj, que la fecha que entra a
        // definirCierrePrueba tiene que ser futura y un literal deja de serlo con el tiempo.
        Instant cierre = Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);
        Instant suya = cierre.plus(2, java.time.temporal.ChronoUnit.DAYS);
        IntentoPrueba conLoSuyo = IntentoPrueba.builder()
                .id(2L).postulacionId(12L).venceEn(suya).plazoPropio(true).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(conLoSuyo));

        var salida = servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(cierre, "Cierre único"));

        assertThat(conLoSuyo.getVenceEn())
                .as("perder «más horas para esta persona» al mover la convocatoria sería silencioso")
                .isEqualTo(suya);
        assertThat(salida.intentosMovidos()).isZero();
        assertThat(salida.intentosConPlazoPropio()).isEqualTo(1);
        verify(intentos, org.mockito.Mockito.never()).save(conLoSuyo);
    }

    @Test
    @DisplayName("quitar la fecha NO deja sin vencimiento a quien ya empezó")
    void quitarLaFechaNoDejaSinVencimientoAQuienYaEmpezo() {
        Vacante v = vacante("PUBLICADA", false, null);
        v.setPruebaCierraEn(Instant.parse("2026-08-24T05:00:00Z"));
        Instant empezo = Instant.parse("2026-08-20T10:00:00Z");
        IntentoPrueba corriendo = IntentoPrueba.builder()
                .id(4L).postulacionId(14L).versionPlantillaPruebaId(31L)
                .iniciadoEn(empezo).venceEn(Instant.parse("2026-08-24T05:00:00Z"))
                .plazoPropio(false).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(corriendo));
        when(versionesPrueba.findById(31L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(31L)
                        .modalidad("PLAZO_ABIERTO").plazoDias(7).build()));

        servicio.definirCierrePrueba(QUIEN, VACANTE, new DefinirCierrePrueba(null, "Se quita"));

        assertThat(corriendo.getVenceEn())
                .as("dejárselo vacío lo dejaría sin vencimiento PARA SIEMPRE: empezar ya no "
                        + "vuelve a pasarle, y el barrido nunca casa contra un nulo")
                .isEqualTo(empezo.plus(7, java.time.temporal.ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("una fecha ya pasada se rechaza: entregaría sola la tanda entera")
    void unaFechaPasadaSeRechaza() {
        vacante("PUBLICADA", false, null);

        assertThatThrownBy(() -> servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(Instant.parse("2020-01-01T00:00:00Z"), "error de tecleo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya pasó");
    }

    @Test
    @DisplayName("una prueba cronometrada SÍ admite fecha: conviven, y rige la que caiga antes")
    void sobreUnaCronometradaSeFijaFecha() {
        // Aquí había una prueba que exigía lo contrario —«anularía el reloj»—, y dejó de ser
        // cierta cuando `ServicioPruebaImpl.iniciar` pasó a quedarse con el plazo más cercano
        // entre el reloj y la fecha de la vacante: quien abre temprano tiene sus 90 minutos
        // completos, y quien abre pegado a la fecha cierra a la fecha. Sin fecha no había
        // forma de impedir que alguien empezara el examen la semana siguiente.
        Vacante v = vacante("PUBLICADA", false, null);
        Instant domingo = Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);

        var salida = servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(domingo, "Nadie sigue después del domingo"));

        assertThat(v.getPruebaCierraEn()).isEqualTo(domingo);
        assertThat(salida.cierraEn()).isEqualTo(domingo);
        verify(vacantes).save(v);
    }

    @Test
    @DisplayName("sin prueba del puesto elegida no hay fecha que fijar, y se dice por qué")
    void sinPlantillaDePruebaNoSeFijaFecha() {
        // El otro camino que sigue cerrado, y que la pantalla usa para no ofrecer el control:
        // una vacante con cuestionario técnico no tiene versión de plantilla y su plazo son
        // los minutos de la vacante.
        Vacante v = vacante("PUBLICADA", false, null);
        v.setVersionPlantillaPruebaId(null);

        assertThatThrownBy(() -> servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(Instant.now().plusSeconds(86400), "cierre único")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no rinde una prueba del puesto");
    }

    @Test
    @DisplayName("quitar la fecha devuelve a contar los días de la plantilla")
    void quitarLaFechaVuelveALosDias() {
        Vacante v = vacante("PUBLICADA", false, null);
        v.setPruebaCierraEn(Instant.parse("2026-08-24T05:00:00Z"));
        IntentoPrueba sinEmpezar = IntentoPrueba.builder()
                .id(3L).postulacionId(13L).venceEn(Instant.parse("2026-08-24T05:00:00Z"))
                .plazoPropio(false).build();
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(sinEmpezar));

        servicio.definirCierrePrueba(QUIEN, VACANTE, new DefinirCierrePrueba(null, "Se amplía"));

        assertThat(v.getPruebaCierraEn()).isNull();
        assertThat(sinEmpezar.getVenceEn())
                .as("sin fecha, al empezar se vuelven a contar los días de la plantilla")
                .isNull();
    }

    // ============ Qué plazo rige hoy, para poder enseñarlo ============
    //
    // El panel ofrecía el campo de fecha en blanco y decía en voz alta que no podía saber
    // cuál regía. Estos cuatro casos son las cuatro frases de la pantalla, y ninguna se
    // puede deducir solo de `prueba_cierra_en`: los días viven en la plantilla, y unos
    // minutos de la vacante convierten en cronometrada hasta una de plazo abierto.

    @Test
    @DisplayName("plazo abierto sin fecha: el detalle trae los días de la plantilla")
    void elDetalleTraeLosDiasDeLaPlantilla() {
        vacante("PUBLICADA", false, null);
        // ⚠️ Se dobla el guardián, no `findById`: la versión no sabe de organizaciones —el
        // dueño vive en su plantilla— y el detalle la pide con el dueño ya resuelto.
        laVersionDeLaEmpresa(VersionPlantillaPrueba.builder().id(31L)
                .modalidad("PLAZO_ABIERTO").plazoDias(5).build());
        when(intentos.abiertosDeLaVacante(VACANTE)).thenReturn(List.of(
                IntentoPrueba.builder().id(1L).postulacionId(11L).plazoPropio(false).build(),
                IntentoPrueba.builder().id(2L).postulacionId(12L).plazoPropio(false).build(),
                IntentoPrueba.builder().id(3L).postulacionId(13L).plazoPropio(true).build()));

        var panel = servicio.detalle(QUIEN, VACANTE);

        assertThat(panel.pruebaCierraEn()).isNull();
        assertThat(panel.modalidadPrueba()).isEqualTo("PLAZO_ABIERTO");
        assertThat(panel.diasPruebaVigentes()).isEqualTo(5);
        assertThat(panel.minutosPruebaVigentes()).isNull();
        assertThat(panel.intentosAbiertosSinPlazoPropio())
                .as("es la cifra que la pantalla dice ANTES de guardar: «se moverán X»")
                .isEqualTo(2);
        assertThat(panel.intentosAbiertosConPlazoPropio()).isEqualTo(1);
    }

    @Test
    @DisplayName("cronometrada con fecha: viajan los minutos Y la fecha, que conviven")
    void elDetalleTraeLosMinutosYLaFecha() {
        Vacante v = vacante("PUBLICADA", false, null);
        Instant domingo = Instant.parse("2026-09-20T23:59:00Z");
        v.setPruebaCierraEn(domingo);
        laVersionDeLaEmpresa(VersionPlantillaPrueba.builder().id(31L)
                .modalidad("CRONOMETRADA").duracionMinutos(30).build());

        var panel = servicio.detalle(QUIEN, VACANTE);

        assertThat(panel.modalidadPrueba()).isEqualTo("CRONOMETRADA");
        assertThat(panel.minutosPruebaVigentes()).isEqualTo(30);
        assertThat(panel.diasPruebaVigentes()).isNull();
        assertThat(panel.pruebaCierraEn())
                .as("enseñar una sola de las dos es esconder la mitad del plazo")
                .isEqualTo(domingo);
    }

    @Test
    @DisplayName("con minutos propios de la vacante, la modalidad que rige es cronometrada")
    void losMinutosDeLaVacanteMandanSobreLaModalidad() {
        // La misma regla que `ServicioPruebaImpl` aplica al arrancar el reloj. Leyendo la
        // modalidad de la plantilla, la pantalla diría «7 días» sobre una prueba de 45
        // minutos.
        Vacante v = vacante("PUBLICADA", false, null);
        v.setMinutosEtapaTecnica(45);
        laVersionDeLaEmpresa(VersionPlantillaPrueba.builder().id(31L)
                .modalidad("PLAZO_ABIERTO").plazoDias(7).build());

        var panel = servicio.detalle(QUIEN, VACANTE);

        assertThat(panel.modalidadPrueba()).isEqualTo("CRONOMETRADA");
        assertThat(panel.minutosPruebaVigentes()).isEqualTo(45);
        assertThat(panel.diasPruebaVigentes()).isNull();
    }

    @Test
    @DisplayName("una versión que no es de esta empresa se lee como «sin dato», no como su plazo")
    void laVersionDeOtraEmpresaNoSeLee() {
        // Con una sola organización esto no pasa nunca, y por eso la fuga es invisible hasta
        // que hay dos: el guardián no la encuentra y la pantalla dice que no sabe, en vez de
        // pintar el reloj de la prueba de otra empresa.
        vacante("PUBLICADA", false, null);
        when(versionesPrueba.laDeLaOrganizacion(31L, ORGANIZACION)).thenReturn(Optional.empty());
        // Ni la que RENASER le hubiera prestado: es de una tercera
        when(versionesPrueba.laDeLaOrganizacion(31L, PLATAFORMA)).thenReturn(Optional.empty());

        var panel = servicio.detalle(QUIEN, VACANTE);

        assertThat(panel.pruebaPrestada()).isNull();
        assertThat(panel.modalidadPrueba()).isNull();
        assertThat(panel.minutosPruebaVigentes()).isNull();
        assertThat(panel.diasPruebaVigentes()).isNull();
    }

    @Test
    @DisplayName("con cuestionario técnico no hay modalidad ni días: son los minutos del banco")
    void elCuestionarioTecnicoSeMideEnMinutos() {
        Vacante v = vacante("PUBLICADA", false, null);
        v.setInstrumentoEtapaTecnica("CUESTIONARIO_TECNICO");
        v.setVersionPlantillaPruebaId(null);
        when(versionesBanco.cuestionarioTecnicoDe(VACANTE, "PUBLICADA"))
                .thenReturn(Optional.of(
                        com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco.builder()
                                .id(70L).estado("PUBLICADA").minutosObjetivo(60).build()));

        var panel = servicio.detalle(QUIEN, VACANTE);

        assertThat(panel.modalidadPrueba())
                .as("no rinde una plantilla: su tiempo son minutos y no admite fecha")
                .isNull();
        assertThat(panel.minutosPruebaVigentes()).isEqualTo(60);
        assertThat(panel.intentosAbiertosSinPlazoPropio())
                .as("el cuestionario no usa intento_prueba: no hay nada que mover")
                .isZero();
    }

    @Test
    @DisplayName("la lista trae la fecha, pero no gasta dos consultas por fila en el resto")
    void laListaNoResuelveElPlazoDeCadaFila() {
        Vacante v = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                .estado("PUBLICADA").puestoId(PUESTO).versionPlantillaPruebaId(31L)
                .pruebaCierraEn(Instant.parse("2026-09-20T23:59:00Z")).build();
        when(vacantes.findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(
                ORGANIZACION)).thenReturn(List.of(v));
        when(enCarrera.cuantasPorVacante(ORGANIZACION)).thenReturn(Map.of());

        var fila = servicio.listar(QUIEN, false).get(0);

        assertThat(fila.pruebaCierraEn()).isEqualTo(Instant.parse("2026-09-20T23:59:00Z"));
        assertThat(fila.modalidadPrueba())
                .as("en la lista llega vacío y el panel lo lee como «sin dato»")
                .isNull();
        verify(intentos, never()).abiertosDeLaVacante(VACANTE);
        verify(versionesPrueba, never()).laDeLaOrganizacion(31L, ORGANIZACION);
    }

    /**
     * La versión de prueba que esta empresa sí puede leer.
     *
     * <p>Va por {@code laDeLaOrganizacion} —el guardián que deriva al padre— y no por
     * {@code findById}: la versión no guarda organización, la guarda su plantilla, y el
     * dueño lo resuelve {@code DuenoDelInstrumento} porque los instrumentos pueden ser de la
     * plataforma y compartirse.
     */
    private void laVersionDeLaEmpresa(VersionPlantillaPrueba version) {
        when(versionesPrueba.laDeLaOrganizacion(version.getId(), ORGANIZACION))
                .thenReturn(Optional.of(version));
    }

    // ============ Los textos de correo de la vacante ============

    @Test
    @DisplayName("elegir un texto que no existe se rechaza al configurarlo, no al mandarlo")
    void unTextoInexistenteSeRechazaAqui() {
        vacante("PUBLICADA", false, null);
        when(plantillasCorreo.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                ORGANIZACION, "NO_EXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarPlantillaCorreo(QUIEN, VACANTE,
                new AsignarPlantillaCorreo("PRUEBA_DISPONIBLE", "NO_EXISTE")))
                .as("si se dejara pasar, el fallo saldría semanas después: un candidato "
                        + "avanzaría y su correo no saldría, sin que nada avisara")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_EXISTE");
        verify(plantillasPorVacante, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("sustituir un aviso por sí mismo no es un reemplazo")
    void sustituirlaPorSiMismaNoVale() {
        vacante("PUBLICADA", false, null);

        assertThatThrownBy(() -> servicio.asignarPlantillaCorreo(QUIEN, VACANTE,
                new AsignarPlantillaCorreo("PRUEBA_DISPONIBLE", "PRUEBA_DISPONIBLE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("con la plantilla activa, queda guardado y auditado")
    void elTextoPropioSeGuarda() {
        vacante("PUBLICADA", false, null);
        when(plantillasCorreo.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                ORGANIZACION, "PRUEBA_DISPONIBLE_ADMINISTRADOR"))
                .thenReturn(Optional.of(PlantillaCorreo.builder()
                        .id(4L).codigo("PRUEBA_DISPONIBLE_ADMINISTRADOR").esActiva(true).build()));
        when(plantillasPorVacante.findByVacanteIdAndAvisoCodigo(VACANTE, "PRUEBA_DISPONIBLE"))
                .thenReturn(Optional.empty());
        when(plantillasPorVacante.save(org.mockito.ArgumentMatchers.any(PlantillaCorreoVacante.class)))
                .thenAnswer(i -> i.getArgument(0));

        servicio.asignarPlantillaCorreo(QUIEN, VACANTE,
                new AsignarPlantillaCorreo("PRUEBA_DISPONIBLE", "PRUEBA_DISPONIBLE_ADMINISTRADOR"));

        verify(plantillasPorVacante).save(org.mockito.ArgumentMatchers.any(PlantillaCorreoVacante.class));
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "asignar_plantilla_correo_vacante",
                "vacante", VACANTE, null,
                Map.of("PRUEBA_DISPONIBLE", "PRUEBA_DISPONIBLE_ADMINISTRADOR"), null);
    }

    @Test
    @DisplayName("la prueba de otra empresa no se le puede colgar a una vacante propia")
    void laPruebaDeOtraEmpresaNoSeAsigna() {
        // La fuga cerrada en la pieza B: la versión de prueba no sabe de organizaciones
        // y antes se asignaba por id suelto. Se deriva a su plantilla y se valida contra
        // el dueño resuelto — ese examen se le sirve al candidato al postular, así que
        // colgarse la prueba ajena era servir el examen de otra empresa.
        Vacante v = vacante("PUBLICADA", false, null);
        when(versionesPrueba.findById(31L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(31L).plantillaPruebaId(300L)
                        .estado("PUBLICADA").build()));
        when(plantillasPrueba.findByIdAndOrganizacionId(300L, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarPlantillaPrueba(QUIEN, VACANTE, 31L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(vacantes, org.mockito.Mockito.never()).save(v);
    }

    // ============ Hasta cuándo se puede tocar la etapa técnica ============
    //
    // La línea se corrió de «la primera postulación» a «la primera rendición». Postular no
    // es rendir: quien no ha abierto su prueba no ha visto ni enunciado ni reloj, así que
    // corregir los minutos no le mueve nada. Con la línea vieja, un solo currículum dentro
    // congelaba la vacante hasta la siguiente convocatoria.

    @Test
    @DisplayName("con postulantes que aún no han empezado, la etapa técnica todavía se ajusta")
    void conPostulantesQueNoEmpezaronSeAjusta() {
        Vacante v = vacante("PUBLICADA", false, null);
        v.setMinutosEtapaTecnica(60);
        when(intentos.algunoEmpezadoDeLaVacante(VACANTE)).thenReturn(false);
        when(evaluaciones.algunaTecnicaEmpezadaDeLaVacante(VACANTE)).thenReturn(false);

        servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "PLANTILLA", 90);

        assertThat(v.getMinutosEtapaTecnica()).isEqualTo(90);
        verify(vacantes).save(v);
    }

    @Test
    @DisplayName("en cuanto alguien abrió su prueba del puesto, la vara se queda quieta")
    void conAlguienDentroDeLaPruebaNoSeCambia() {
        Vacante v = vacante("PUBLICADA", false, null);
        v.setMinutosEtapaTecnica(60);
        when(intentos.algunoEmpezadoDeLaVacante(VACANTE)).thenReturn(true);

        assertThatThrownBy(() -> servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "PLANTILLA", 90))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("misma vara");
        verify(vacantes, org.mockito.Mockito.never()).save(v);
    }

    @Test
    @DisplayName("el cuestionario técnico cuenta igual: quien lo abrió también rindió")
    void conAlguienDentroDelCuestionarioNoSeCambia() {
        // Se preguntan los dos instrumentos a propósito: una vacante que ya cambió antes
        // puede tener gente que empezó con el otro, y esa gente también se midió.
        Vacante v = vacante("PUBLICADA", false, null);
        v.setInstrumentoEtapaTecnica("CUESTIONARIO_TECNICO");
        when(intentos.algunoEmpezadoDeLaVacante(VACANTE)).thenReturn(false);
        when(evaluaciones.algunaTecnicaEmpezadaDeLaVacante(VACANTE)).thenReturn(true);

        assertThatThrownBy(() ->
                servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "CUESTIONARIO_TECNICO", 45))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("misma vara");
        verify(vacantes, org.mockito.Mockito.never()).save(v);
    }

    @Test
    @DisplayName("en borrador no se pregunta nada: la vacante todavía se está escribiendo")
    void enBorradorNiSeConsulta() {
        Vacante v = vacante("BORRADOR", false, null);

        servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "CUESTIONARIO_TECNICO", 45);

        assertThat(v.getInstrumentoEtapaTecnica()).isEqualTo("CUESTIONARIO_TECNICO");
        verify(intentos, org.mockito.Mockito.never()).algunoEmpezadoDeLaVacante(VACANTE);
        verify(evaluaciones, org.mockito.Mockito.never()).algunaTecnicaEmpezadaDeLaVacante(VACANTE);
    }

    @Test
    @DisplayName("guardar el mismo valor no despierta la guarda")
    void elMismoValorNoTocaLaGuarda() {
        // Volver a mandar lo que ya está guardado es lo que hace el panel al cambiar el
        // desplegable sin tocar los minutos. No cambia la vara de nadie.
        Vacante v = vacante("PUBLICADA", false, null);
        v.setMinutosEtapaTecnica(90);

        servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "PLANTILLA", 90);

        verify(vacantes).save(v);
        verify(intentos, org.mockito.Mockito.never()).algunoEmpezadoDeLaVacante(VACANTE);
    }

    @Test
    @DisplayName("un minuto no es un plazo: el suelo son cinco")
    void unMinutoNoEsUnPlazo() {
        // Estos minutos convierten cualquier prueba en cronometrada, así que un uno es una
        // prueba que el barrido entrega sola sesenta segundos después de abrirla.
        Vacante v = vacante("BORRADOR", false, null);

        assertThatThrownBy(() -> servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "PLANTILLA", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 5 minutos");
        verify(vacantes, org.mockito.Mockito.never()).save(v);
    }

    @Test
    @DisplayName("vacío sigue siendo un valor: rige lo que traiga el instrumento")
    void vacioSigueSiendoUnValor() {
        Vacante v = vacante("BORRADOR", false, null);
        v.setMinutosEtapaTecnica(90);

        servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "PLANTILLA", null);

        assertThat(v.getMinutosEtapaTecnica()).isNull();
        verify(vacantes).save(v);
    }

    // ============ La prueba del editor no se entra ni se sale por el instrumento (V67) ============
    //
    // Una vacante nueva rinde la prueba escrita en «Armar la prueba» (AC-02). Pasarla por la
    // API a una plantilla o al cuestionario CAZATALENTOS la dejaba rindiendo lo que ya no se
    // ofrece, y el panel le pintaba los desplegables viejos sin camino de vuelta al editor.
    // La única salida que queda es asignarle una plantilla, para los guiones de las vacantes
    // que ya existen, y solo mientras nadie haya rendido.

    /** Una vacante nueva: la prueba del editor y ninguna plantilla colgada. */
    private Vacante vacanteConPruebaPropia(String estado) {
        Vacante v = vacante(estado, false, null);
        v.setInstrumentoEtapaTecnica("PRUEBA_PROPIA");
        v.setVersionPlantillaPruebaId(null);
        return v;
    }

    @Test
    @DisplayName("una vacante con la prueba del editor no se pasa a una plantilla: 400 y la bandera no cambia")
    void laPruebaDelEditorNoSePasaAUnaPlantilla() {
        Vacante v = vacanteConPruebaPropia("BORRADOR");

        assertThatThrownBy(() -> servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "PLANTILLA", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("se escribe en «Armar la prueba»");

        assertThat(v.getInstrumentoEtapaTecnica()).isEqualTo("PRUEBA_PROPIA");
        assertThat(v.getMinutosEtapaTecnica()).isNull();
        verify(vacantes, never()).save(any(Vacante.class));
        org.mockito.Mockito.verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("ni al cuestionario CAZATALENTOS, aunque esté publicada y nadie haya rendido: 400 antes de la guarda")
    void laPruebaDelEditorNoSePasaAlCuestionario() {
        // Publicada y sin nadie dentro es justo el caso en que la guarda de la vara dejaría
        // pasar el cambio: el rechazo es de la regla, no del momento de la vacante.
        Vacante v = vacanteConPruebaPropia("PUBLICADA");

        assertThatThrownBy(() ->
                servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "CUESTIONARIO_TECNICO", 45))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("se escribe en «Armar la prueba»");

        assertThat(v.getInstrumentoEtapaTecnica()).isEqualTo("PRUEBA_PROPIA");
        assertThat(v.getMinutosEtapaTecnica()).isNull();
        verify(vacantes, never()).save(any(Vacante.class));
        verify(intentos, never()).algunoEmpezadoDeLaVacante(VACANTE);
        org.mockito.Mockito.verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("pedir la prueba del editor sobre ella misma es inocuo: se queda igual y sin minutos")
    void laPruebaDelEditorSobreSiMismaEsInocua() {
        Vacante v = vacanteConPruebaPropia("PUBLICADA");

        servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "PRUEBA_PROPIA", 45);

        assertThat(v.getInstrumentoEtapaTecnica()).isEqualTo("PRUEBA_PROPIA");
        // Su tiempo va dentro de la prueba: unos minutos de la vacante mentirían.
        assertThat(v.getMinutosEtapaTecnica()).isNull();
        verify(vacantes).save(v);
        // No cambia la vara de nadie, así que ni se pregunta quién empezó.
        verify(intentos, never()).algunoEmpezadoDeLaVacante(VACANTE);
    }

    @Test
    @DisplayName("una vacante de las de antes no se pasa a la prueba del editor: 400 y sigue con la suya")
    void unaDeLasDeAntesNoSePasaALaPruebaDelEditor() {
        // Ninguna pantalla ni guion lo pide: el panel solo ofrece sus dos instrumentos.
        for (String deAntes : List.of("PLANTILLA", "CUESTIONARIO_TECNICO")) {
            Vacante v = vacante("BORRADOR", false, null);
            v.setInstrumentoEtapaTecnica(deAntes);
            v.setMinutosEtapaTecnica(60);

            assertThatThrownBy(() ->
                    servicio.elegirInstrumentoTecnico(QUIEN, VACANTE, "PRUEBA_PROPIA", null))
                    .as(deAntes)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("es la de las vacantes nuevas");

            assertThat(v.getInstrumentoEtapaTecnica()).as(deAntes).isEqualTo(deAntes);
            assertThat(v.getMinutosEtapaTecnica()).as(deAntes).isEqualTo(60);
        }
        verify(vacantes, never()).save(any(Vacante.class));
        org.mockito.Mockito.verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("asignarle una plantilla sí la pasa a PLANTILLA mientras nadie haya rendido (guiones)")
    void asignarUnaPlantillaLaPasaAPlantilla() {
        Vacante v = vacanteConPruebaPropia("PUBLICADA");
        when(versionesPrueba.findById(32L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(32L).plantillaPruebaId(300L)
                        .estado("PUBLICADA").build()));
        when(plantillasPrueba.findByIdAndOrganizacionId(300L, ORGANIZACION)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba.builder().id(300L).build()));
        when(intentos.algunoEmpezadoDeLaVacante(VACANTE)).thenReturn(false);
        when(evaluaciones.algunaTecnicaEmpezadaDeLaVacante(VACANTE)).thenReturn(false);

        servicio.asignarPlantillaPrueba(QUIEN, VACANTE, 32L);

        assertThat(v.getInstrumentoEtapaTecnica()).isEqualTo("PLANTILLA");
        assertThat(v.getVersionPlantillaPruebaId()).isEqualTo(32L);
        verify(vacantes).save(v);
    }

    @Test
    @DisplayName("con alguien que ya rindió, asignarle una plantilla no le cambia el instrumento: 409")
    void conAlguienQueRindioLaPlantillaNoEntra() {
        Vacante v = vacanteConPruebaPropia("PUBLICADA");
        when(versionesPrueba.findById(32L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(32L).plantillaPruebaId(300L)
                        .estado("PUBLICADA").build()));
        when(plantillasPrueba.findByIdAndOrganizacionId(300L, ORGANIZACION)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba.builder().id(300L).build()));
        when(intentos.algunoEmpezadoDeLaVacante(VACANTE)).thenReturn(true);

        assertThatThrownBy(() -> servicio.asignarPlantillaPrueba(QUIEN, VACANTE, 32L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("misma vara");

        assertThat(v.getInstrumentoEtapaTecnica()).isEqualTo("PRUEBA_PROPIA");
        assertThat(v.getVersionPlantillaPruebaId()).isNull();
        verify(vacantes, never()).save(any(Vacante.class));
    }
}
