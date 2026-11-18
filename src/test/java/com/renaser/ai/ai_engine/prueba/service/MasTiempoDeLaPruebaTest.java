package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.RecordatorioEnviado;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.RecordatorioEnviadoRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.DarMasTiempo;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.MasTiempoDado;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * «Darle más tiempo» a quien no completó la prueba (V89): qué se guarda, qué se rechaza y qué
 * recordatorios deja dados por omitidos.
 *
 * <p>Las fechas son relativas al momento de la prueba: una fecha escrita a mano caduca, y el
 * día que caduque el fallo no dirá nada de esta regla.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Darle más tiempo a quien no completó la prueba")
class MasTiempoDeLaPruebaTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long POSTULACION = 70L;
    private static final Long VACANTE = 9L;
    private static final Long TURNO = 500L;
    private static final ContextoUsuario ANA = new ContextoUsuario(33L, 3L, ORGANIZACION, "EQUIPO",
            List.of(), Map.of("mover_postulacion", "TODO", "abrir_ficha_candidato", "TODO"));
    private static final ContextoUsuario SIN_MOVER = new ContextoUsuario(34L, 4L, ORGANIZACION, "EQUIPO",
            List.of(), Map.of("abrir_ficha_candidato", "TODO"));

    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private PostulacionRepository postulaciones;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private VacanteRepository vacantes;
    @Mock private MaquinaEstados maquina;
    @Mock private ServicioAuditoria auditoria;
    @Mock private NombresDeUsuarios nombres;
    @Mock private CalificacionDeLaPruebaPropia calculo;
    @Mock private TransicionEstadoRepository transiciones;
    @Mock private RecordatorioEnviadoRepository recordatorios;
    @Mock private ServicioParametros parametros;
    @Mock private AvisoDeMasTiempo avisos;

    @InjectMocks private MasTiempoDeLaPrueba servicio;

    private Postulacion postulacion;
    private Vacante vacante;

    @BeforeEach
    void montar() {
        postulacion = Postulacion.builder().id(POSTULACION).organizacionId(ORGANIZACION)
                .vacanteId(VACANTE).usuarioId(80L).estadoCodigo("PRUEBA_TURNO_CANDIDATO").build();
        vacante = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                .titulo("Analista de operaciones").estado("CERRADA").build();
        lenient().when(alcance.laPostulacionVisible(ANA, POSTULACION, "mover_postulacion"))
                .thenReturn(postulacion);
        lenient().when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(vacante));
        lenient().when(postulaciones.estadoBloqueandoLaFila(POSTULACION))
                .thenReturn("PRUEBA_TURNO_CANDIDATO");
        lenient().when(parametros.entero(eq(ORGANIZACION), anyString(), anyInt())).thenReturn(24);
        lenient().when(transiciones.findFirstByPostulacionIdOrderByIdDesc(POSTULACION))
                .thenReturn(Optional.of(TransicionEstado.builder().id(TURNO).postulacionId(POSTULACION)
                        .estadoNuevoCodigo("PRUEBA_TURNO_CANDIDATO").build()));
        lenient().when(recordatorios.findByTransicionEstadoIdIn(List.of(TURNO))).thenReturn(List.of());
        lenient().when(avisos.canales(postulacion)).thenReturn(List.of("correo", "campana"));
    }

    private static IntentoPrueba noCompletada() {
        return IntentoPrueba.builder().id(40L).postulacionId(POSTULACION).versionBancoId(7L)
                .iniciadoEn(Instant.now().minus(Duration.ofHours(3)))
                .venceEn(Instant.now().minus(Duration.ofHours(2)))
                .entregadoEn(Instant.now().minus(Duration.ofHours(2)))
                .esEntregaAutomatica(true).noCompletada(true).build();
    }

    private static IntentoPrueba vencidaSinAbrir() {
        return IntentoPrueba.builder().id(41L).postulacionId(POSTULACION).versionBancoId(7L)
                .venceEn(Instant.now().minus(Duration.ofDays(1))).build();
    }

    private void conIntento(IntentoPrueba intento) {
        lenient().when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(intento));
    }

    private static DarMasTiempo hasta(Duration desdeAhora, Boolean avisar) {
        // Con la precisión de la base, como la guarda el servicio.
        return new DarMasTiempo(Instant.now().plus(desdeAhora).truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                "  Se le cortó la luz  ", avisar);
    }

    @Nested
    @DisplayName("Al guardar")
    class AlGuardar {

        @Test
        @DisplayName("la que no completó vuelve a «En curso» con lo suyo y la fecha como propia (AC-07)")
        void vuelveAEnCurso() {
            IntentoPrueba intento = noCompletada();
            conIntento(intento);
            DarMasTiempo datos = hasta(Duration.ofDays(2), true);

            MasTiempoDado dado = servicio.dar(ANA, POSTULACION, datos);

            assertThat(dado.estadoPrueba()).isEqualTo("EN_CURSO");
            assertThat(dado.venceEn()).isEqualTo(datos.venceEn());
            assertThat(dado.avisadoPor()).containsExactly("correo", "campana");
            assertThat(intento.isNoCompletada()).isFalse();
            assertThat(intento.getEntregadoEn()).isNull();
            assertThat(intento.isEsEntregaAutomatica()).isFalse();
            assertThat(intento.isPlazoPropio()).isTrue();
            assertThat(intento.getVenceEn()).isEqualTo(datos.venceEn());
            assertThat(intento.getIniciadoEn()).isNotNull();
            assertThat(intento.getMasTiempoPorUsuarioId()).isEqualTo(33L);
            assertThat(intento.getMasTiempoMotivo()).isEqualTo("Se le cortó la luz");
            verify(intentos).save(intento);
            verify(avisos).avisar(postulacion, intento, "Analista de operaciones");
        }

        @Test
        @DisplayName("queda en la auditoría la fecha anterior, la nueva y el motivo")
        void quedaEnLaAuditoria() {
            IntentoPrueba intento = noCompletada();
            Instant anterior = intento.getVenceEn();
            conIntento(intento);
            DarMasTiempo datos = hasta(Duration.ofDays(2), true);

            servicio.dar(ANA, POSTULACION, datos);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> antes = ArgumentCaptor.forClass(Map.class);
            verify(auditoria).registrar(eq(ORGANIZACION), eq(ANA), eq("dar_mas_tiempo_prueba"),
                    eq("intento_prueba"), eq(40L), antes.capture(),
                    eq(Map.of("venceEn", datos.venceEn().toString())), eq("Se le cortó la luz"));
            assertThat(antes.getValue()).containsEntry("venceEn", anterior.toString())
                    .containsEntry("noCompletada", true);
        }

        @Test
        @DisplayName("quien no la había abierto vuelve a «Sin empezar» y su reloj aún no corre (AC-10)")
        void vuelveASinEmpezar() {
            IntentoPrueba intento = vencidaSinAbrir();
            conIntento(intento);

            MasTiempoDado dado = servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(2), true));

            assertThat(dado.estadoPrueba()).isEqualTo("SIN_EMPEZAR");
            assertThat(intento.getIniciadoEn()).isNull();
            assertThat(intento.isPlazoPropio()).isTrue();
        }

        @Test
        @DisplayName("sin «Avisar» no sale nada por ningún canal (AC-08)")
        void sinAvisarNoSaleNada() {
            conIntento(noCompletada());

            MasTiempoDado dado = servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(2), false));

            assertThat(dado.avisadoPor()).isEmpty();
            verifyNoInteractions(avisos);
        }

        @Test
        @DisplayName("con la vacante cerrada sí se puede: la prueba conserva su fecha (decisión 4)")
        void conLaVacanteCerradaSiSePuede() {
            conIntento(noCompletada());

            assertThat(servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(1), true)).estadoPrueba())
                    .isEqualTo("EN_CURSO");
        }

        /** La carrera con el barrido: si llega antes que él, el plazo nuevo se aplica igual. */
        @Test
        @DisplayName("la que venció empezada y el barrido aún no cerró también se reabre")
        void laQueElBarridoNoCerroSeReabre() {
            IntentoPrueba intento = IntentoPrueba.builder().id(42L).postulacionId(POSTULACION)
                    .versionBancoId(7L).iniciadoEn(Instant.now().minus(Duration.ofHours(1)))
                    .venceEn(Instant.now().minus(Duration.ofSeconds(20))).build();
            conIntento(intento);

            assertThat(servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(1), true)).estadoPrueba())
                    .isEqualTo("EN_CURSO");
        }
    }

    @Nested
    @DisplayName("Los recordatorios del turno (AC-18)")
    class LosRecordatorios {

        @Test
        @DisplayName("el de «a las 24 h» queda dado por omitido: no se repite")
        void elDeLas24hNoSeRepite() {
            conIntento(noCompletada());

            servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(3), true));

            ArgumentCaptor<RecordatorioEnviado> escrito = ArgumentCaptor.forClass(RecordatorioEnviado.class);
            verify(recordatorios, times(1)).save(escrito.capture());
            assertThat(escrito.getValue().getTipo()).isEqualTo(RecordatorioEnviado.TRAS_ENTRAR);
            assertThat(escrito.getValue().getResultado()).isEqualTo(RecordatorioEnviado.OMITIDO);
            assertThat(escrito.getValue().getTransicionEstadoId()).isEqualTo(TURNO);
        }

        @Test
        @DisplayName("a más de 24 h, el previo al plazo queda libre para salir con la fecha nueva")
        void aMasDe24hElPrevioSale() {
            conIntento(noCompletada());
            when(recordatorios.findByTransicionEstadoIdIn(List.of(TURNO))).thenReturn(List.of(
                    RecordatorioEnviado.builder().tipo(RecordatorioEnviado.TRAS_ENTRAR)
                            .resultado(RecordatorioEnviado.ENVIADO).build()));

            servicio.dar(ANA, POSTULACION, hasta(Duration.ofHours(30), true));

            verify(recordatorios, never()).save(any());
        }

        @Test
        @DisplayName("a menos de 24 h, el previo de esa fecha se da por omitido: el aviso ya cumple")
        void aMenosDe24hNoHayPrevio() {
            conIntento(noCompletada());
            when(recordatorios.findByTransicionEstadoIdIn(List.of(TURNO))).thenReturn(List.of(
                    RecordatorioEnviado.builder().tipo(RecordatorioEnviado.TRAS_ENTRAR)
                            .resultado(RecordatorioEnviado.ENVIADO).build()));
            DarMasTiempo datos = hasta(Duration.ofHours(5), true);

            servicio.dar(ANA, POSTULACION, datos);

            ArgumentCaptor<RecordatorioEnviado> escrito = ArgumentCaptor.forClass(RecordatorioEnviado.class);
            verify(recordatorios).save(escrito.capture());
            assertThat(escrito.getValue().getTipo()).isEqualTo(RecordatorioEnviado.ANTES_DEL_PLAZO);
            assertThat(escrito.getValue().getPlazoEn()).isEqualTo(datos.venceEn());
            assertThat(escrito.getValue().getResultado()).isEqualTo(RecordatorioEnviado.OMITIDO);
        }
    }

    @Nested
    @DisplayName("Lo que no se guarda (AC-11)")
    class LoQueSeRechaza {

        @Test
        @DisplayName("con la vacante archivada: 409 con el motivo, sin tocar la prueba")
        void conLaVacanteArchivada() {
            vacante.setArchivadaEn(Instant.now().minus(Duration.ofDays(1)));

            assertThatThrownBy(() -> servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(1), true)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("La vacante está archivada: desarchívala para darle más tiempo");
            verifyNoInteractions(intentos, auditoria, avisos);
        }

        @Test
        @DisplayName("con el proceso cerrado: un proceso cerrado no se reabre")
        void conElProcesoCerrado() {
            when(maquina.yaTermino(postulacion)).thenReturn(true);

            assertThatThrownBy(() -> servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(1), true)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ya está cerrado");
            verifyNoInteractions(intentos, auditoria, avisos);
        }

        @Test
        @DisplayName("si el proceso cambió desde otra pantalla mientras tanto, no se guarda")
        void siElProcesoCambio() {
            when(postulaciones.estadoBloqueandoLaFila(POSTULACION)).thenReturn("NO_CONTINUA");

            assertThatThrownBy(() -> servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(1), true)))
                    .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(intentos, auditoria, avisos);
        }

        @Test
        @DisplayName("con una fecha pasada: 400 y nada guardado")
        void conUnaFechaPasada() {
            conIntento(noCompletada());

            assertThatThrownBy(() -> servicio.dar(ANA, POSTULACION, hasta(Duration.ofMinutes(-5), true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ya pasó");
            verify(intentos, never()).save(any());
            verifyNoInteractions(auditoria, avisos);
        }

        @Test
        @DisplayName("el doble envío ya no la encuentra sin completar y no repite los avisos")
        void elDobleEnvioNoRepite() {
            IntentoPrueba yaReabierta = IntentoPrueba.builder().id(40L).postulacionId(POSTULACION)
                    .versionBancoId(7L).iniciadoEn(Instant.now().minus(Duration.ofHours(3)))
                    .venceEn(Instant.now().plus(Duration.ofDays(2))).plazoPropio(true).build();
            conIntento(yaReabierta);

            assertThatThrownBy(() -> servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(2), true)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ya no está sin completar");
            verify(intentos, never()).save(any());
            verifyNoInteractions(avisos);
        }

        @Test
        @DisplayName("una prueba entregada no se reabre")
        void unaEntregadaNoSeReabre() {
            conIntento(IntentoPrueba.builder().id(40L).postulacionId(POSTULACION).versionBancoId(7L)
                    .iniciadoEn(Instant.now().minus(Duration.ofHours(3)))
                    .venceEn(Instant.now().minus(Duration.ofHours(1)))
                    .entregadoEn(Instant.now().minus(Duration.ofHours(2))).build());

            assertThatThrownBy(() -> servicio.dar(ANA, POSTULACION, hasta(Duration.ofDays(2), true)))
                    .isInstanceOf(IllegalStateException.class);
            verify(intentos, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Lo que dice la ficha (AC-09, AC-15)")
    class LaFicha {

        @Test
        @DisplayName("quien no la completó: cuándo se le acabó, qué le faltó y que se le puede dar")
        void quienNoLaCompleto() {
            IntentoPrueba intento = noCompletada();
            when(calculo.loQueFaltaTanda(List.of(intento))).thenReturn(Map.of(40L,
                    new CalificacionDeLaPruebaPropia.LoQueFalta(List.of(2, 3, 4, 5), List.of("Tablero.xlsx"))));

            var ficha = servicio.deLaFicha(ANA, postulacion, vacante, intento);

            assertThat(ficha.sinCompletar().seAcaboEn()).isEqualTo(intento.getVenceEn());
            assertThat(ficha.sinCompletar().sinAbrir()).isFalse();
            assertThat(ficha.sinCompletar().queFalto()).isEqualTo("le faltaron 4 preguntas y «Tablero.xlsx»");
            assertThat(ficha.sinCompletar().puedeMover()).isTrue();
            assertThat(ficha.sinCompletar().bloqueo()).isNull();
            assertThat(ficha.masTiempo()).isNull();
        }

        @Test
        @DisplayName("quien no la abrió: «sin abrir», sin lista de faltas")
        void quienNoLaAbrio() {
            var ficha = servicio.deLaFicha(ANA, postulacion, vacante, vencidaSinAbrir());

            assertThat(ficha.sinCompletar().sinAbrir()).isTrue();
            assertThat(ficha.sinCompletar().queFalto()).isNull();
            verifyNoInteractions(calculo);
        }

        @Test
        @DisplayName("sin el permiso no se ofrece; con la vacante archivada o el proceso cerrado, apagado con su motivo")
        void sinPermisoOBloqueada() {
            assertThat(servicio.deLaFicha(SIN_MOVER, postulacion, vacante, vencidaSinAbrir())
                    .sinCompletar().puedeMover()).isFalse();

            when(maquina.yaTermino(postulacion)).thenReturn(true);
            assertThat(servicio.deLaFicha(ANA, postulacion, vacante, vencidaSinAbrir())
                    .sinCompletar().bloqueo()).isEqualTo("Proceso cerrado");

            vacante.setArchivadaEn(Instant.now());
            assertThat(servicio.deLaFicha(ANA, postulacion, vacante, vencidaSinAbrir())
                    .sinCompletar().bloqueo()).isEqualTo("Vacante archivada");
        }

        @Test
        @DisplayName("tras darle más tiempo: quién, cuándo y por qué, mientras siga abierta")
        void trasDarleMasTiempo() {
            Instant dado = Instant.now().minus(Duration.ofMinutes(10));
            IntentoPrueba reabierta = IntentoPrueba.builder().id(40L).postulacionId(POSTULACION)
                    .iniciadoEn(Instant.now().minus(Duration.ofHours(3)))
                    .venceEn(Instant.now().plus(Duration.ofDays(2))).plazoPropio(true)
                    .masTiempoEn(dado).masTiempoPorUsuarioId(33L).masTiempoMotivo("Se le cortó la luz").build();
            when(nombres.de(33L)).thenReturn("Ana Ríos");

            var ficha = servicio.deLaFicha(ANA, postulacion, vacante, reabierta);

            assertThat(ficha.sinCompletar()).isNull();
            assertThat(ficha.masTiempo().dadoEn()).isEqualTo(dado);
            assertThat(ficha.masTiempo().dadoPor()).isEqualTo("Ana Ríos");
            assertThat(ficha.masTiempo().motivo()).isEqualTo("Se le cortó la luz");
        }

        @Test
        @DisplayName("una vez entregada, la ficha ya no habla del más tiempo")
        void entregadaNoLoDice() {
            IntentoPrueba entregada = IntentoPrueba.builder().id(40L).postulacionId(POSTULACION)
                    .iniciadoEn(Instant.now().minus(Duration.ofHours(3)))
                    .venceEn(Instant.now().plus(Duration.ofDays(2)))
                    .entregadoEn(Instant.now().minus(Duration.ofMinutes(5)))
                    .masTiempoEn(Instant.now().minus(Duration.ofDays(1))).masTiempoMotivo("x").build();

            var ficha = servicio.deLaFicha(ANA, postulacion, vacante, entregada);

            assertThat(ficha.sinCompletar()).isNull();
            assertThat(ficha.masTiempo()).isNull();
        }
    }
}
