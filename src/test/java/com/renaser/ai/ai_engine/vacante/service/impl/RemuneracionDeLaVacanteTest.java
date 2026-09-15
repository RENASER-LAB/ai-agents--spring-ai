package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoVacanteRepository;
import com.renaser.ai.ai_engine.notificacion.service.DireccionDelCandidato;
import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PlantillaEvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.ServicioEnlaceAcceso;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.ActualizarRemuneracion;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.RemuneracionActualizadaResponse;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.RemuneracionDeLaVacante;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cambiar lo que la vacante dice que paga (V55).
 *
 * <p>Lo que se protege aquí es <b>a quién llega la noticia</b>. Cambiar un sueldo no es
 * guardar un campo: es escribirle a cada persona que tiene una candidatura abierta, y las
 * tres formas de equivocarse son avisar a quien ya no está, avisar de un cambio que no
 * ocurrió, y no avisar a nadie porque una dirección de correo falló.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La remuneración de la vacante")
class RemuneracionDeLaVacanteTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long VACANTE = 20L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            7L, 3L, ORGANIZACION, "EQUIPO", List.of(1L), Map.of());

    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private RequisitoObjetivoRepository requisitos;
    @Mock private SolicitudTalentoRepository solicitudes;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private PlantillaEvaluacionRepository plantillas;
    @Mock private VersionPlantillaPruebaRepository versionesPrueba;
    @Mock private PlantillaPruebaRepository plantillasPrueba;
    @Mock private PlantillaCorreoRepository plantillasCorreo;
    @Mock private PlantillaCorreoVacanteRepository plantillasPorVacante;
    @Mock private TextoConsentimientoRepository textosConsentimiento;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private EvaluacionRepository evaluaciones;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private ServicioAuditoria auditoria;
    @Mock private DuenoDelInstrumento dueno;
    @Mock private PostulacionRepository postulaciones;
    @Mock private ServicioAvisosPortal avisos;
    @Mock private ServicioCorreo correo;
    @Mock private DireccionDelCandidato direcciones;
    @Mock private ServicioEnlaceAcceso enlacesDeAcceso;
    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;

    private ServicioVacantesPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioVacantesPanelImpl(vacantes, puestos, requisitos, solicitudes,
                versionesPesos, plantillas, versionesPrueba, plantillasPrueba, plantillasCorreo,
                plantillasPorVacante, textosConsentimiento, intentos, evaluaciones, versionesBanco,
                auditoria, dueno, postulaciones, avisos, correo, direcciones, enlacesDeAcceso,
                usuarios, personas);
    }

    // ---------- el escenario ----------

    private Vacante vacantePublicada(String tipo, String min, String max) {
        Vacante v = Vacante.builder()
                .id(VACANTE)
                .organizacionId(ORGANIZACION)
                .titulo("Coordinador de sede")
                .estado("PUBLICADA")
                .remuneracionTipo(tipo)
                .remuneracionMin(min == null ? null : new BigDecimal(min))
                .remuneracionMax(max == null ? null : new BigDecimal(max))
                .remuneracionMoneda("OCULTA".equals(tipo) ? null : "PEN")
                .build();
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(v));
        return v;
    }

    /** Una candidatura en el estado que se le diga, con su usuario y su persona detrás. */
    private Postulacion postulacion(Long id, String estado) {
        return Postulacion.builder()
                .id(id).organizacionId(ORGANIZACION).vacanteId(VACANTE)
                .usuarioId(100L + id).estadoCodigo(estado)
                .build();
    }

    /**
     * Quiénes están postulados a esta vacante, con su usuario y su persona detrás.
     *
     * <p>Todo con {@code lenient()} a propósito: las pruebas que comprueban que NO se avisa
     * —la que guarda lo mismo, la del rango al revés— cortan antes de preguntar quién hay, y
     * el modo estricto de Mockito las tumbaría por dejar stubs sin usar. Que el escenario
     * tenga gente viva es justamente lo que da fuerza a esas pruebas.
     */
    private void hayPostulaciones(Postulacion... unas) {
        lenient().when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE))
                .thenReturn(List.of(unas));
        for (Postulacion p : unas) {
            lenient().when(usuarios.findById(p.getUsuarioId())).thenReturn(Optional.of(
                    Usuario.builder().id(p.getUsuarioId()).personaId(p.getUsuarioId())
                            .correo("candidato" + p.getId() + "@correo.pe").build()));
            lenient().when(personas.findById(p.getUsuarioId())).thenReturn(Optional.of(
                    Persona.builder().id(p.getUsuarioId()).nombre("Ana").build()));
        }
        lenient().when(direcciones.de(any(), anyLong())).thenReturn("ana@correo.pe");
        lenient().when(enlacesDeAcceso.generarEnlace(anyLong()))
                .thenReturn(new ServicioEnlaceAcceso.EnlaceGenerado("https://portal/e/abc",
                        Instant.now().plusSeconds(3600)));
        lenient().when(plantillasPorVacante.findByVacanteIdAndAvisoCodigo(eq(VACANTE), anyString()))
                .thenReturn(Optional.empty());
        // El aviso se publica de verdad. Hace falta decirlo porque el contador de
        // «a cuánta gente le llegó» sube por el AVISO y no por el correo: `publicar`
        // devuelve null cuando falla, y un doble que devuelve null por defecto haría que
        // el servicio contara cero con toda la razón.
        lenient().when(avisos.publicar(anyLong(), anyLong(), anyString(), anyString(),
                        anyString(), anyLong(), anyLong()))
                .thenAnswer(invocacion -> AvisoPortal.builder().id(1L).build());
    }

    private static ActualizarRemuneracion cambiarA(String tipo, String min, String max) {
        return new ActualizarRemuneracion(new RemuneracionDeLaVacante(tipo,
                min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max),
                "OCULTA".equals(tipo) ? null : "PEN"),
                "El presupuesto aprobado subió");
    }

    // ---------- guardar ----------

    @Test
    @DisplayName("de oculta a rango: se guardan los montos y queda la marca de cuándo cambió")
    void deOcultaARango() {
        // En BORRADOR, que es donde cruzar esa línea sigue estando permitido: publicada, la
        // decisión de enseñar el sueldo o no ya no se toca — ver `noSePuedeRevocarLaSimetria`.
        Vacante v = vacantePublicada("OCULTA", null, null);
        v.setEstado("BORRADOR");
        hayPostulaciones();

        RemuneracionActualizadaResponse respuesta =
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "3000", "4000"));

        assertThat(v.getRemuneracionTipo()).isEqualTo("RANGO");
        assertThat(v.getRemuneracionMin()).isEqualByComparingTo("3000");
        assertThat(v.getRemuneracionMax()).isEqualByComparingTo("4000");
        assertThat(v.getRemuneracionMoneda()).isEqualTo("PEN");
        assertThat(v.getRemuneracionActualizadaEn()).isNotNull();

        assertThat(respuesta.antes()).isEqualTo("sin publicar");
        assertThat(respuesta.ahora()).isEqualTo("S/ 3 000 a 4 000");
        verify(vacantes).save(v);
    }

    @Test
    @DisplayName("apagar la remuneración borra los montos, no los deja escondidos en la base")
    void apagarlaLimpiaLosMontos() {
        // Una vacante OCULTA con cifras guardadas es un sueldo esperando a que alguien lo lea
        // por descuido — y la restricción de la V55 tampoco lo admitiría.
        //
        // En BORRADOR: apagar la remuneración de una PUBLICADA ya no se puede.
        Vacante v = vacantePublicada("RANGO", "3000", "4000");
        v.setEstado("BORRADOR");
        hayPostulaciones();

        servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("OCULTA", null, null));

        assertThat(v.getRemuneracionTipo()).isEqualTo("OCULTA");
        assertThat(v.getRemuneracionMin()).isNull();
        assertThat(v.getRemuneracionMax()).isNull();
        assertThat(v.getRemuneracionMoneda()).isNull();
    }

    @Test
    @DisplayName("pasar a fija deja el máximo vacío aunque llegara uno")
    void laFijaNoGuardaMaximo() {
        Vacante v = vacantePublicada("RANGO", "3000", "4000");
        hayPostulaciones();

        servicio.actualizarRemuneracion(QUIEN, VACANTE,
                new ActualizarRemuneracion(
                        new RemuneracionDeLaVacante("FIJA", new BigDecimal("3500"), null, "PEN"),
                        "Se cerró el número"));

        assertThat(v.getRemuneracionTipo()).isEqualTo("FIJA");
        assertThat(v.getRemuneracionMin()).isEqualByComparingTo("3500");
        assertThat(v.getRemuneracionMax()).isNull();
    }

    @Test
    @DisplayName("una vacante cerrada no cambia de sueldo")
    void laCerradaNoSeToca() {
        Vacante v = vacantePublicada("RANGO", "3000", "4000");
        v.setEstado("CERRADA");

        assertThatThrownBy(() ->
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("FIJA", "3500", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cerrada no cambia de sueldo");

        verify(vacantes, never()).save(any());
        verifyNoInteractions(avisos, correo);
    }

    @Test
    @DisplayName("un rango al revés no se guarda ni avisa a nadie")
    void elRangoInvalidoNoLlegaANadie() {
        // Sobre una que ya publica el sueldo: así lo que corta es la validación del rango, no
        // la guarda de la simetría, que es lo que esta prueba quiere ver.
        vacantePublicada("FIJA", "3500", null);

        assertThatThrownBy(() ->
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "4000", "3000")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(vacantes, never()).save(any());
        verifyNoInteractions(avisos, correo, auditoria);
    }

    // ---------- la simetría no se revoca ----------

    /**
     * El trato se cobra por adelantado, así que no se puede deshacer a mitad.
     *
     * <p>Una vacante publicada enseñando el sueldo le exigió su cifra a cada persona que
     * postuló. Volver a OCULTA es quedarse con lo cobrado y retirar lo pagado — dos clics
     * para deshacer la única regla que sostiene esto.
     */
    @Test
    @DisplayName("una vacante publicada que enseña el sueldo ya no puede esconderlo")
    void noSePuedeRevocarLaSimetria() {
        vacantePublicada("RANGO", "3000", "4000");
        hayPostulaciones(postulacion(1L, "PERFIL_TURNO_CANDIDATO"));

        assertThatThrownBy(() ->
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("OCULTA", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin dar nada a cambio");

        verify(vacantes, never()).save(any());
        verifyNoInteractions(avisos, correo, auditoria);
    }

    @Test
    @DisplayName("y una publicada sin sueldo tampoco puede empezar a enseñarlo")
    void tampocoSePuedeEncenderDespues() {
        // A quienes ya postularon no se les pidió nada y no hay forma de volver atrás a
        // pedírselo: encenderlo dejaría media tanda con cifra y media sin ella.
        vacantePublicada("OCULTA", null, null);
        hayPostulaciones(postulacion(1L, "PERFIL_TURNO_CANDIDATO"));

        assertThatThrownBy(() ->
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "3000", "4000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("media tanda");

        verify(vacantes, never()).save(any());
    }

    @Test
    @DisplayName("pero entre rango y monto fijo sí se mueve: las dos publican")
    void entreRangoYFijaSeMueveLibre() {
        // Es el cambio más frecuente de todos —cerrar un rango en una cifra cuando ya se
        // sabe el número— y no revoca nada: el sueldo se sigue enseñando.
        Vacante v = vacantePublicada("RANGO", "3000", "4000");
        hayPostulaciones(postulacion(1L, "PERFIL_TURNO_CANDIDATO"));

        RemuneracionActualizadaResponse respuesta =
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("FIJA", "3800", null));

        assertThat(v.getRemuneracionTipo()).isEqualTo("FIJA");
        assertThat(respuesta.candidatosAvisados()).isEqualTo(1);
    }

    @Test
    @DisplayName("en borrador se decide libremente: es justo el momento de decidirlo")
    void enBorradorSeDecideLibremente() {
        Vacante v = vacantePublicada("RANGO", "3000", "4000");
        v.setEstado("BORRADOR");
        hayPostulaciones();

        servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("OCULTA", null, null));

        assertThat(v.getRemuneracionTipo()).isEqualTo("OCULTA");
    }

    // ---------- a quién llega ----------

    @Test
    @DisplayName("avisa a quien sigue en carrera, y a nadie más")
    void soloALosVivos() {
        vacantePublicada("RANGO", "3000", "4000");
        hayPostulaciones(
                postulacion(1L, "PERFIL_TURNO_CANDIDATO"),
                postulacion(2L, "PRUEBA_POR_CONFIRMAR"),
                // Estos tres ya no están: la noticia no les afecta, y recordarles el sueldo de
                // un puesto que perdieron es cruel sin ganar nada.
                postulacion(3L, "NO_CONTINUA"),
                postulacion(4L, "CERRADA"),
                postulacion(5L, "CONTRATADO"));

        RemuneracionActualizadaResponse respuesta =
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "3500", "4500"));

        assertThat(respuesta.candidatosAvisados()).isEqualTo(2);
        verify(avisos, times(2)).publicar(eq(ORGANIZACION), anyLong(),
                eq(AvisoPortal.REMUNERACION_ACTUALIZADA), anyString(), anyString(),
                anyLong(), eq(VACANTE));
        verify(correo, times(2)).enviar(eq(ORGANIZACION), anyLong(), anyString(),
                eq("REMUNERACION_ACTUALIZADA"), any());
    }

    @Test
    @DisplayName("el correo lleva el antes y el ahora, escritos igual que en el portal")
    void elCorreoDiceLasDosCifras() {
        vacantePublicada("FIJA", "3000", null);
        hayPostulaciones(postulacion(1L, "PERFIL_TURNO_CANDIDATO"));

        servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "3500", "4200"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(correo).enviar(eq(ORGANIZACION), anyLong(), anyString(),
                eq("REMUNERACION_ACTUALIZADA"), variables.capture());

        assertThat(variables.getValue())
                .containsEntry("antes", "S/ 3 000")
                .containsEntry("ahora", "S/ 3 500 a 4 200")
                .containsEntry("vacante", "Coordinador de sede")
                // El enlace por donde entrar: quien llegó por una carga de currículums no
                // tiene contraseña, y «entra a tu portal» a secas no le sirve de nada.
                .containsEntry("enlace", "https://portal/e/abc");
    }

    @Test
    @DisplayName("en borrador no hay a quién avisar, y rellenar el sueldo no manda nada")
    void enBorradorNadieRecibeNada() {
        Vacante v = vacantePublicada("OCULTA", null, null);
        v.setEstado("BORRADOR");
        hayPostulaciones();

        RemuneracionActualizadaResponse respuesta =
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "3000", "4000"));

        assertThat(respuesta.candidatosAvisados()).isZero();
        verifyNoInteractions(avisos, correo);
        // Pero sí se guarda: rellenar el sueldo antes de publicar es el camino normal.
        assertThat(v.getRemuneracionTipo()).isEqualTo("RANGO");
    }

    @Test
    @DisplayName("guardar lo mismo que ya había no avisa ni mueve la marca de actualizado")
    void guardarLoMismoNoEsUnCambio() {
        // Pasa más de lo que parece: el panel manda el formulario entero, y quien entra a
        // mirar y pulsa guardar sin tocar nada llega exactamente aquí.
        Vacante v = vacantePublicada("RANGO", "3000", "4000");
        Instant marcaVieja = Instant.parse("2026-09-01T10:00:00Z");
        v.setRemuneracionActualizadaEn(marcaVieja);
        hayPostulaciones(postulacion(1L, "PERFIL_TURNO_CANDIDATO"));

        RemuneracionActualizadaResponse respuesta =
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "3000", "4000"));

        assertThat(respuesta.candidatosAvisados()).isZero();
        verifyNoInteractions(avisos, correo);
        verify(vacantes, never()).save(any());
        // Y el portal sigue diciendo «actualizado el 1 de septiembre», no «hoy».
        assertThat(v.getRemuneracionActualizadaEn()).isEqualTo(marcaVieja);
    }

    @Test
    @DisplayName("que a uno no se le pueda escribir no deja sin aviso a los demás")
    void unFalloNoTumbaElResto() {
        vacantePublicada("RANGO", "3000", "4000");
        hayPostulaciones(
                postulacion(1L, "PERFIL_TURNO_CANDIDATO"),
                postulacion(2L, "PERFIL_TURNO_CANDIDATO"));
        // Al primero le revienta el correo; el segundo tiene que recibir el suyo igual.
        when(usuarios.findById(101L)).thenThrow(new RuntimeException("la base se cayó"));

        RemuneracionActualizadaResponse respuesta =
                servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "3500", "4500"));

        assertThat(respuesta.candidatosAvisados()).isEqualTo(1);
        verify(correo).enviar(eq(ORGANIZACION), eq(102L), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("la vacante puede sustituir el texto del aviso por el suyo, como cualquier otro")
    void laVacantePuedeElegirSuTexto() {
        vacantePublicada("RANGO", "3000", "4000");
        hayPostulaciones(postulacion(1L, "PERFIL_TURNO_CANDIDATO"));
        when(plantillasPorVacante.findByVacanteIdAndAvisoCodigo(VACANTE, "REMUNERACION_ACTUALIZADA"))
                .thenReturn(Optional.of(
                        com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreoVacante.builder()
                                .vacanteId(VACANTE)
                                .avisoCodigo("REMUNERACION_ACTUALIZADA")
                                .plantillaCodigo("SUELDO_SUBE_CON_CARINO")
                                .build()));

        servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "3500", "4500"));

        verify(correo).enviar(eq(ORGANIZACION), anyLong(), anyString(),
                eq("SUELDO_SUBE_CON_CARINO"), any());
    }

    // ---------- la auditoría ----------

    @Test
    @DisplayName("queda escrito qué había antes, qué hay ahora y por qué cambió")
    void laAuditoriaGuardaElMotivo() {
        vacantePublicada("FIJA", "3000", null);
        hayPostulaciones();

        servicio.actualizarRemuneracion(QUIEN, VACANTE, cambiarA("RANGO", "3500", "4200"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> anterior = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> nuevo = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("actualizar_remuneracion"),
                eq("vacante"), eq(VACANTE), anterior.capture(), nuevo.capture(),
                eq("El presupuesto aprobado subió"));

        assertThat(anterior.getValue()).containsEntry("tipo", "FIJA");
        assertThat(nuevo.getValue()).containsEntry("tipo", "RANGO");
        assertThat((BigDecimal) nuevo.getValue().get("max")).isEqualByComparingTo("4200");
    }
}
