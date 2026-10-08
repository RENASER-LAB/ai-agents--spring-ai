package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.notificacion.service.DireccionDelCandidato;
import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La campana en cada etapa y el plazo del aviso de la prueba (V70), desde la máquina de estados.
 *
 * <p>Las dos promesas de la Parte B que solo se pueden sostener aquí, donde nace el correo: la
 * campana recibe exactamente lo mismo que el correo (AC-10, AC-11) y los dos son independientes
 * (AC-12). Y la Parte D: {@code {{plazo}}} dice el tiempo y la fecha que rige (AC-21).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("La máquina de estados avisa por correo y por la campana")
class MaquinaEstadosCampanaTest {

    @Mock private EstadoPostulacionRepository estados;
    @Mock private PostulacionRepository postulaciones;
    @Mock private TransicionEstadoRepository transiciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;
    @Mock private VacanteRepository vacantes;
    @Mock private ServicioAuditoria auditoria;
    @Mock private ServicioCorreo correo;
    @Mock private DireccionDelCandidato direcciones;
    @Mock private ServicioEnlaceAcceso enlaces;
    @Mock private PlantillaCorreoVacanteRepository plantillasPorVacante;
    @Mock private VersionPlantillaPruebaRepository versionesDePrueba;
    @Mock private ServicioParametros parametros;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private FechaLimiteDeLaPersona fechasLimite;
    @Mock private AvisoDeEtapaEnLaCampana campana;

    private MaquinaEstados maquina;

    private static final Long VACANTE = 40L;
    private static final LocalDate VIERNES = LocalDate.now(FechaParaElCandidato.LIMA)
            .with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
    private static final Instant CIERRE = VIERNES.atTime(23, 59)
            .atZone(FechaParaElCandidato.LIMA).toInstant();
    private static final String CIERRE_DICHO = "vie %02d/%02d a las 23:59"
            .formatted(VIERNES.getDayOfMonth(), VIERNES.getMonthValue());

    @BeforeEach
    void montar() {
        maquina = new MaquinaEstados(estados, postulaciones, transiciones, usuarios, personas,
                vacantes, auditoria, correo, direcciones, enlaces, plantillasPorVacante,
                versionesDePrueba, parametros, versionesBanco, fechasLimite, campana);

        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setPersonaId(7L);
        when(usuarios.findById(anyLong())).thenReturn(Optional.of(usuario));
        Persona persona = new Persona();
        persona.setNombre("Ana");
        when(personas.findById(anyLong())).thenReturn(Optional.of(persona));
        when(direcciones.de(any(), anyLong())).thenReturn("ana@ejemplo.test");
        when(enlaces.generarEnlace(anyLong()))
                .thenReturn(new ServicioEnlaceAcceso.EnlaceGenerado("https://portal.test/acceso", null));
        when(parametros.texto(anyLong(), eq("whatsapp_evidencia"), anyString())).thenReturn("");
        when(estados.findById("PERFIL_POR_CONFIRMAR")).thenReturn(Optional.of(estado(
                "PERFIL_POR_CONFIRMAR", "PERFIL_INTEGRAL", "TALENTO", false)));
        when(estados.findById("PRUEBA_TURNO_CANDIDATO")).thenReturn(Optional.of(estado(
                "PRUEBA_TURNO_CANDIDATO", "PRUEBA_PUESTO", "CANDIDATO", false)));
        when(estados.findById("NO_CONTINUA")).thenReturn(Optional.of(estado(
                "NO_CONTINUA", null, "NADIE", true)));
        when(estados.findById("CERRADA")).thenReturn(Optional.of(estado(
                "CERRADA", null, "NADIE", true)));
    }

    private static EstadoPostulacion estado(String codigo, String etapa, String esperaA, boolean fin) {
        return EstadoPostulacion.builder().codigo(codigo).nombre(codigo).etapaCodigo(etapa)
                .esperaA(esperaA).esFinal(fin).build();
    }

    private Postulacion postulacionEn(String estado) {
        return Postulacion.builder().id(500L).organizacionId(1L).usuarioId(7L).vacanteId(VACANTE)
                .uuid(UUID.randomUUID()).estadoCodigo(estado).build();
    }

    /** Una vacante con la prueba del editor publicada con este tiempo. */
    private void conPruebaPropia(String modalidad, Integer minutos, Integer dias) {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(Vacante.builder().id(VACANTE)
                .titulo("Administrador de tienda").instrumentoEtapaTecnica("PRUEBA_PROPIA").build()));
        when(versionesBanco.pruebaPropiaDe(VACANTE, "PUBLICADA")).thenReturn(Optional.of(
                VersionBanco.builder().id(3L).modalidad(modalidad).duracionMinutos(minutos)
                        .plazoDias(dias).build()));
    }

    private Map<String, String> variablesDelCorreo() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(correo).enviar(anyLong(), anyLong(), anyString(), anyString(), variables.capture());
        return variables.getValue();
    }

    private TransicionEstado transicionGuardada() {
        ArgumentCaptor<TransicionEstado> guardada = ArgumentCaptor.forClass(TransicionEstado.class);
        verify(transiciones).save(guardada.capture());
        return guardada.getValue();
    }

    // ============ La Parte B: la campana recibe lo mismo que el correo ============

    @Test
    @DisplayName("la prueba disponible deja aviso en la campana, con el título de la vacante y su plazo (AC-10)")
    void laPruebaDejaAviso() {
        conPruebaPropia("CRONOMETRADA", 90, null);
        when(fechasLimite.deSuPrueba(any(), any())).thenReturn(CIERRE);

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, "Pase automático", true, false, null);

        verify(campana).publicar(any(Postulacion.class), eq("PRUEBA_DISPONIBLE"),
                any(EstadoPostulacion.class), eq("Administrador de tienda"),
                eq("90 minutos desde que la empieces, hasta el " + CIERRE_DICHO));
        assertThat(transicionGuardada().getAvisoAlCandidato()).isEqualTo("CORREO");
    }

    @Test
    @DisplayName("no continúa: correo y aviso, los dos (AC-10)")
    void noContinuaLosDos() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).titulo("Cajero").build()));

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "NO_CONTINUA", null,
                "No encaja", false, false, "DECISION_PERSONA");

        verify(correo).enviar(anyLong(), anyLong(), anyString(), eq("POSTULACION_NO_CONTINUA"), any());
        verify(campana).publicar(any(Postulacion.class), eq("POSTULACION_NO_CONTINUA"),
                any(EstadoPostulacion.class), eq("Cajero"), isNull());
    }

    @Test
    @DisplayName("movido «sin avisar»: ni correo ni aviso, y la transición lo deja escrito (AC-11)")
    void sinAvisarNiCorreoNiAviso() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).titulo("Cajero").build()));

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "NO_CONTINUA", null,
                "Ya se lo dijimos", false, false, "DECISION_PERSONA", false);

        verifyNoInteractions(correo, campana);
        assertThat(transicionGuardada().getAvisoAlCandidato()).isEqualTo("NINGUNO");
    }

    @Test
    @DisplayName("el cierre por vacante eliminada no deja un aviso de etapa: ya lleva el suyo (AC-11)")
    void laEliminadaSoloElSuyo() {
        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "CERRADA", null,
                "La vacante se eliminó", true, true, "VACANTE_ELIMINADA",
                MaquinaEstados.AvisoDeLaTransicion.POR_LA_CAMPANA);

        verifyNoInteractions(correo, campana);
        assertThat(transicionGuardada().getAvisoAlCandidato()).isEqualTo("POR_LA_CAMPANA");
    }

    @Test
    @DisplayName("los pasos internos (por confirmar) no mandan nada: ni correo ni campana")
    void losPasosInternosCallan() {
        maquina.transicionar(postulacionEn("PERFIL_CALIFICANDO"), "PERFIL_POR_CONFIRMAR", null,
                "Pase automático al entregar: la nota se calcula después", true, false, null);

        verifyNoInteractions(correo, campana);
    }

    @Test
    @DisplayName("si el correo falla, el aviso sale y la transición se queda hecha (AC-12)")
    void siFallaElCorreoSaleElAviso() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).titulo("Cajero").build()));
        doThrow(new IllegalStateException("el servidor de correo no responde"))
                .when(correo).enviar(anyLong(), anyLong(), anyString(), anyString(), any());
        Postulacion p = postulacionEn("PERFIL_POR_CONFIRMAR");

        assertThatCode(() -> maquina.transicionar(p, "NO_CONTINUA", null, "No encaja", false,
                false, "DECISION_PERSONA")).doesNotThrowAnyException();

        verify(campana).publicar(any(Postulacion.class), eq("POSTULACION_NO_CONTINUA"),
                any(EstadoPostulacion.class), eq("Cajero"), isNull());
        verify(postulaciones).save(p);
        assertThat(p.getEstadoCodigo()).isEqualTo("NO_CONTINUA");
    }

    // ============ La Parte D: lo que dice {{plazo}} (AC-21) ============

    @Test
    @DisplayName("cronometrada con fecha: «90 minutos desde que la empieces, hasta el …»")
    void cronometradaConFecha() {
        conPruebaPropia("CRONOMETRADA", 90, null);
        when(fechasLimite.deSuPrueba(any(), any())).thenReturn(CIERRE);

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, null, true, false, null);

        assertThat(variablesDelCorreo())
                .containsEntry("plazo", "90 minutos desde que la empieces, hasta el " + CIERRE_DICHO)
                .containsEntry("nombre", "Ana")
                .containsEntry("vacante", "Administrador de tienda");
    }

    @Test
    @DisplayName("sin cronómetro con fecha: «hasta el …», y ya no vacío")
    void sinCronometroConFecha() {
        conPruebaPropia(null, null, null);
        when(fechasLimite.deSuPrueba(any(), any())).thenReturn(CIERRE);

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, null, true, false, null);

        assertThat(variablesDelCorreo()).containsEntry("plazo", "hasta el " + CIERRE_DICHO);
    }

    @Test
    @DisplayName("cronometrada sin fecha: «90 minutos desde que la empieces»")
    void cronometradaSinFecha() {
        conPruebaPropia("CRONOMETRADA", 90, null);

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, null, true, false, null);

        assertThat(variablesDelCorreo()).containsEntry("plazo", "90 minutos desde que la empieces");
    }

    @Test
    @DisplayName("plazo abierto con días y sin fecha: «7 días desde que la empieces»")
    void plazoAbiertoSinFecha() {
        conPruebaPropia("PLAZO_ABIERTO", null, 7);

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, null, true, false, null);

        assertThat(variablesDelCorreo()).containsEntry("plazo", "7 días desde que la empieces");
    }

    @Test
    @DisplayName("con plazo propio dice la fecha de la persona: la que da quien sabe cuál rige")
    void conPlazoPropioLaDeLaPersona() {
        conPruebaPropia("CRONOMETRADA", 90, null);
        Instant suya = CIERRE.plus(java.time.Duration.ofDays(3));
        when(fechasLimite.deSuPrueba(any(), any())).thenReturn(suya);

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, null, true, false, null);

        assertThat(variablesDelCorreo().get("plazo"))
                .endsWith(FechaParaElCandidato.dicha(suya))
                .doesNotContain(CIERRE_DICHO);
    }

    @Test
    @DisplayName("una plantilla sin días ni minutos ya no deja el hueco vacío: dice la fecha o «sin fecha límite»")
    void laPlantillaNuncaVacia() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(Vacante.builder().id(VACANTE)
                .titulo("Cajero").versionPlantillaPruebaId(17L).build()));
        when(versionesDePrueba.findById(17L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(17L).build()));

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, null, true, false, null);

        assertThat(variablesDelCorreo()).containsEntry("plazo", "sin fecha límite");
    }

    @Test
    @DisplayName("la plantilla con días sigue diciéndolo como siempre")
    void laPlantillaComoSiempre() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(Vacante.builder().id(VACANTE)
                .titulo("Cajero").versionPlantillaPruebaId(17L).build()));
        when(versionesDePrueba.findById(17L)).thenReturn(Optional.of(
                VersionPlantillaPrueba.builder().id(17L).plazoDias(5).build()));

        maquina.transicionar(postulacionEn("PERFIL_POR_CONFIRMAR"), "PRUEBA_TURNO_CANDIDATO",
                null, null, true, false, null);

        assertThat(variablesDelCorreo()).containsEntry("plazo", "5 dias");
    }
}
