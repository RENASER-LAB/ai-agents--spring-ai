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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository
            textosConsentimiento;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private ServicioAuditoria auditoria;
    @Mock private com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento dueno;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository postulaciones;
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository versionesBanco;

    private ServicioVacantesPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioVacantesPanelImpl(vacantes, puestos, requisitos, solicitudes,
                versionesPesos, plantillas, versionesPrueba, plantillasPrueba, plantillasCorreo,
                plantillasPorVacante, textosConsentimiento, intentos, versionesBanco, auditoria,
                dueno, postulaciones);
        // En estas pruebas la organizacion no personaliza nada: el resolutor contesta
        // que el dueño de todo instrumento es ella misma (aqui hace de plataforma).
        org.mockito.Mockito.lenient()
                .when(dueno.duenoDe(org.mockito.ArgumentMatchers.eq(ORGANIZACION),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(ORGANIZACION);
        // Y tiene su texto legal publicado, como Renaser desde la V9: publicar una vacante
        // lo exige (pieza D), y la prueba que lo quita es la que comprueba el freno.
        org.mockito.Mockito.lenient()
                .when(textosConsentimiento
                        .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                                ORGANIZACION, "PROCESO"))
                .thenReturn(Optional.of(com.renaser.ai.ai_engine.consentimiento.entity
                        .TextoConsentimiento.builder().id(70L).tipo("PROCESO").build()));

        // El puesto de la vacante y el banco publicado de su nivel: sin los dos, publicar
        // con la evaluacion encendida falla, y eso lo comprueba su propia prueba apagando
        // este mock. Lenient porque la mitad de estas pruebas no llegan a mirarlos.
        org.mockito.Mockito.lenient()
                .when(puestos.findById(PUESTO))
                .thenReturn(Optional.of(com.renaser.ai.ai_engine.vacante.entity.Puesto.builder()
                        .id(PUESTO).nivelPuestoCodigo(NIVEL).build()));
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

    private Vacante vacante(String estado, boolean aplicaEvaluacion, Long plantillaEvaluacionId) {
        Vacante v = Vacante.builder()
                .id(VACANTE)
                .organizacionId(ORGANIZACION)
                .estado(estado)
                .aplicaEvaluacion(aplicaEvaluacion)
                .plantillaEvaluacionId(plantillaEvaluacionId)
                .puestoId(PUESTO)
                .versionPlantillaPruebaId(31L)
                .build();
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(v));
        return v;
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
                null, null, null, null, null, null, null, "MANUAL", 1,
                null, null, QUIEN.usuarioId());
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

    @Test
    @DisplayName("sin el texto legal publicado no se publica la vacante, y el error dice qué falta")
    void sinTextoLegalNoSePublica() {
        // El requisito del día uno de la pieza A: al postular se firma el texto PROCESO de
        // la empresa, y no puede firmarse lo que no existe. El error sale aquí, en la cara
        // de quien publica, no en la del primer candidato.
        Vacante v = vacante("BORRADOR", false, null);
        when(textosConsentimiento
                .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                        ORGANIZACION, "PROCESO"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.publicar(QUIEN, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("texto de consentimiento");
        assertThat(v.getEstado()).isEqualTo("BORRADOR");
    }

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
    @DisplayName("volver a encenderla en una vacante publicada exige banco del nivel")
    void encenderlaPublicadaSinBancoAvisaAqui() {
        // El aviso tiene que salir aqui: encenderla sin banco dejaria al siguiente candidato
        // chocando contra un error al postular.
        vacante("PUBLICADA", false, null);
        when(versionesBanco.laPublicadaDelNivel(ORGANIZACION, "NIVEL", NIVEL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.definirAplicacionEvaluacion(QUIEN, VACANTE, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("banco de preguntas publicado");
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

    @Test
    @DisplayName("los pesos solo se cambian a una versión publicada")
    void losPesosExigenVersionPublicada() {
        vacante("PUBLICADA", false, null);
        when(versionesPesos.findByIdAndOrganizacionId(VERSION_PESOS, ORGANIZACION))
                .thenReturn(Optional.of(VersionPesos.builder()
                        .id(VERSION_PESOS).estado("BORRADOR").build()));

        assertThatThrownBy(() -> servicio.asignarVersionPesos(QUIEN, VACANTE, VERSION_PESOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("borrador");
    }

    @Test
    @DisplayName("asignar una versión publicada queda guardado y auditado")
    void asignarUnaPublicadaSeGuarda() {
        // En borrador: publicada la vacante, la vara ya no se cambia (ver el test siguiente).
        Vacante v = vacante("BORRADOR", false, null);
        v.setVersionPesosId(2L);
        when(versionesPesos.findByIdAndOrganizacionId(VERSION_PESOS, ORGANIZACION))
                .thenReturn(Optional.of(VersionPesos.builder()
                        .id(VERSION_PESOS).estado("PUBLICADA").build()));

        servicio.asignarVersionPesos(QUIEN, VACANTE, VERSION_PESOS);

        assertThat(v.getVersionPesosId()).isEqualTo(VERSION_PESOS);
        verify(vacantes).save(v);
        verify(auditoria).registrar(ORGANIZACION, QUIEN, "asignar_version_pesos",
                "vacante", VACANTE, Map.of("versionPesosId", "2"),
                Map.of("versionPesosId", String.valueOf(VERSION_PESOS)), null);
    }

    @Test
    @DisplayName("con postulantes dentro, la versión de pesos ya no se cambia: una vacante, una versión")
    void conPostulantesLosPesosNoSeCambian() {
        // Todos los candidatos de una vacante se miden con la misma vara
        // (docs/DECISION-UNA-VACANTE-UNA-VERSION.md). Cambiarla a mitad dejaría a unos
        // calificados con la v1 y a otros con la v2, ordenados juntos en el mismo ranking.
        Vacante v = vacante("PUBLICADA", false, null);
        v.setVersionPesosId(2L);
        when(versionesPesos.findByIdAndOrganizacionId(VERSION_PESOS, ORGANIZACION))
                .thenReturn(Optional.of(VersionPesos.builder()
                        .id(VERSION_PESOS).estado("PUBLICADA").build()));
        when(postulaciones.countByVacanteId(VACANTE)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.asignarVersionPesos(QUIEN, VACANTE, VERSION_PESOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("misma vara");
        verify(vacantes, org.mockito.Mockito.never()).save(v);
    }

    @Test
    @DisplayName("publicada pero sin postulantes, la vacante todavía se termina de configurar")
    void publicadaSinPostulantesSeConfigura() {
        // La línea es la primera postulación, no la publicación: el flujo sin banco asigna
        // sus pesos después de publicar, antes de abrir la puerta a nadie.
        Vacante v = vacante("PUBLICADA", false, null);
        v.setVersionPesosId(2L);
        when(versionesPesos.findByIdAndOrganizacionId(VERSION_PESOS, ORGANIZACION))
                .thenReturn(Optional.of(VersionPesos.builder()
                        .id(VERSION_PESOS).estado("PUBLICADA").build()));
        when(postulaciones.countByVacanteId(VACANTE)).thenReturn(0L);

        servicio.asignarVersionPesos(QUIEN, VACANTE, VERSION_PESOS);

        assertThat(v.getVersionPesosId()).isEqualTo(VERSION_PESOS);
        verify(vacantes).save(v);
    }

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
    @DisplayName("sobre una prueba cronometrada no se fija fecha: anularía el reloj")
    void sobreUnaCronometradaNoSeFijaFecha() {
        vacante("PUBLICADA", false, null);
        when(versionesPrueba.findById(31L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(31L)
                        .modalidad("CRONOMETRADA").duracionMinutos(90).build()));

        assertThatThrownBy(() -> servicio.definirCierrePrueba(QUIEN, VACANTE,
                new DefinirCierrePrueba(Instant.now().plusSeconds(86400), "cierre único")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cronometrada");
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
    @DisplayName("una versión de pesos de otra organización no existe para quien pregunta")
    void losPesosDeOtraOrganizacionNoExisten() {
        vacante("PUBLICADA", false, null);
        when(versionesPesos.findByIdAndOrganizacionId(VERSION_PESOS, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarVersionPesos(QUIEN, VACANTE, VERSION_PESOS))
                .isInstanceOf(ResourceNotFoundException.class);
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
}
