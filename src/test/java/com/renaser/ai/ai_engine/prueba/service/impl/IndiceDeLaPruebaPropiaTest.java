package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.FilaDePrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.VacanteDestino;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.FiltrosDelIndice;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.repository.CriterioBancoEntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableCubrePreguntaRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.NotaCriterioPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.FechaLimiteDeLaVacante;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import com.renaser.ai.ai_engine.vacante.service.LugarDeLaVacante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * «Pruebas», pestaña «Prueba del puesto»: el resumen con sus entregables y su tiempo, lo que
 * no sale, los destinos y el rechazo de copiar en una vacante que no usa el editor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("«Pruebas»: el índice de la prueba del puesto de la empresa")
class IndiceDeLaPruebaPropiaTest {

    private static final long ORGANIZACION = 2L;
    private static final ContextoUsuario TALENTO = new ContextoUsuario(12L, 3L, ORGANIZACION, "EQUIPO",
            List.of(2L), Map.of("ver_vacantes", "TODO", "editar_vacante", "TODO"));

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
    @Mock private EntregableCubrePreguntaRepository cubiertas;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private NotaCriterioPruebaRepository notas;
    @Mock private PropuestaPreguntasRepository propuestas;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private CalificacionDeLaPruebaPropia calculo;
    @Mock private CierreDeLaPruebaPropia cierre;
    @Mock private ColaCalificacionIa cola;
    @Mock private ServicioAuditoria auditoria;
    @Mock private FechaLimiteDeLaVacante fechaLimite;
    @Mock private LugarDeLaVacante lugares;
    @InjectMocks private ServicioPruebaPropiaImpl servicio;

    private static Vacante.VacanteBuilder vacante(long id, String titulo, String instrumento) {
        return Vacante.builder().id(id).organizacionId(ORGANIZACION).titulo(titulo).estado("PUBLICADA")
                .instrumentoEtapaTecnica(instrumento);
    }

    private static VersionBanco version(long id, long vacanteId, String estado, Instant publicadaEn,
                                        String modalidad, Integer minutos, Integer dias) {
        return VersionBanco.builder().id(id).vacanteId(vacanteId).organizacionId(ORGANIZACION)
                .proposito("PRUEBA_PUESTO").estado(estado).publicadaEn(publicadaEn).modalidad(modalidad)
                .duracionMinutos(minutos).plazoDias(dias).build();
    }

    private static EntregableRequerido entregable(long id, long versionId, String alcance) {
        return EntregableRequerido.builder().id(id).versionBancoId(versionId).alcance(alcance).build();
    }

    @BeforeEach
    void armar() {
        when(permisos.alcanceDe(any())).thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, 12L));
        when(alcance.alcanzaALaVacante(any(), any(), any())).thenReturn(true);
        when(lugares.paraLas(anyCollection())).thenReturn(v -> null);
    }

    @Test
    @DisplayName("El tiempo como el chip del editor y todos los entregables; sin plantilla ni cuestionario técnico (AC-02, AC-03)")
    void resumenYLoQueNoSale() {
        when(versionesBanco.enCursoDelProposito(ORGANIZACION, "PRUEBA_PUESTO")).thenReturn(List.of(
                version(10L, 1L, "PUBLICADA", Instant.parse("2026-10-18T15:00:00Z"), "CRONOMETRADA", 90, null),
                version(20L, 2L, "PUBLICADA", Instant.parse("2026-10-15T15:00:00Z"), "PLAZO_ABIERTO", null, null),
                version(30L, 3L, "PUBLICADA", Instant.parse("2026-03-12T15:00:00Z"), "PLAZO_ABIERTO", 30, 5),
                version(40L, 4L, "BORRADOR", null, "CRONOMETRADA", 60, null),
                version(50L, 5L, "BORRADOR", null, "CRONOMETRADA", 60, null)));
        when(vacantes.findAllById(any())).thenReturn(List.of(
                vacante(1L, "Analista de datos", "PRUEBA_PROPIA").build(),
                vacante(2L, "Asistente contable", "PRUEBA_PROPIA").build(),
                vacante(3L, "Supervisor de seguridad", "PRUEBA_PROPIA").build(),
                vacante(4L, "Con plantilla", "PLANTILLA").build(),
                vacante(5L, "Con cuestionario", "CUESTIONARIO_TECNICO").build()));
        when(intentos.vacantesConAlgunoEmpezado(anyCollection())).thenReturn(List.of(3L));
        when(entregables.findByVersionBancoIdIn(any())).thenReturn(List.of(
                entregable(1L, 10L, "PREGUNTA"), entregable(2L, 10L, "TODA_LA_PRUEBA"),
                entregable(3L, 30L, "PREGUNTAS")));

        List<FilaDePrueba> filas = servicio.indice(TALENTO, FiltrosDelIndice.NINGUNO);

        assertThat(filas).extracting(FilaDePrueba::vacanteId).containsExactly(1L, 2L, 3L);
        FilaDePrueba cronometrada = filas.get(0);
        assertThat(cronometrada.entregables()).isEqualTo(2);
        assertThat(cronometrada.modalidad()).isEqualTo("CRONOMETRADA");
        assertThat(cronometrada.minutos()).isEqualTo(90);
        assertThat(cronometrada.dias()).isNull();
        // Solo entregables y ninguna pregunta: la fila dice «0 preguntas»
        FilaDePrueba sinCronometro = filas.get(1);
        assertThat(sinCronometro.entregables()).isZero();
        assertThat(sinCronometro.preguntas()).isZero();
        assertThat(sinCronometro.modalidad()).isEqualTo("PLAZO_ABIERTO");
        assertThat(sinCronometro.minutos()).isNull();
        assertThat(sinCronometro.dias()).isNull();
        FilaDePrueba conDias = filas.get(2);
        assertThat(conDias.dias()).isEqualTo(5);
        assertThat(conDias.minutos()).isNull();
        assertThat(conDias.laRindeAlguien()).isTrue();
        assertThat(cronometrada.laRindeAlguien()).isFalse();
    }

    @Test
    @DisplayName("Los destinos usan la prueba del editor; la que ya se rinde sale deshabilitada con su motivo (AC-11, AC-15)")
    void destinos() {
        when(vacantes.findByOrganizacionIdAndArchivadaEnIsNullAndEliminadaEnIsNullOrderByCreadoEnDesc(ORGANIZACION))
                .thenReturn(List.of(vacante(1L, "Rindiéndose", "PRUEBA_PROPIA").build(),
                        vacante(2L, "Libre", "PRUEBA_PROPIA").estado("BORRADOR").build(),
                        vacante(3L, "Con plantilla", "PLANTILLA").build(),
                        vacante(4L, "Con cuestionario", "CUESTIONARIO_TECNICO").build()));
        when(versionesBanco.enCursoDelProposito(ORGANIZACION, "PRUEBA_PUESTO")).thenReturn(List.of(
                version(10L, 1L, "PUBLICADA", Instant.now(), "CRONOMETRADA", 90, null)));
        when(intentos.vacantesConAlgunoEmpezado(anyCollection())).thenReturn(List.of(1L));

        List<VacanteDestino> destinos = servicio.destinos(TALENTO, null);

        assertThat(destinos).extracting(VacanteDestino::vacanteId).containsExactly(2L, 1L);
        assertThat(destinos.get(0).puedeRecibir()).isTrue();
        assertThat(destinos.get(1).puedeRecibir()).isFalse();
        assertThat(destinos.get(1).motivo()).isEqualTo("Ya la rinden candidatos: su prueba no se reemplaza");
    }

    @Test
    @DisplayName("Copiar en una vacante con plantilla o con el cuestionario técnico: 409 y no se crea nada (AC-15)")
    void copiarAUnaQueNoUsaElEditor() {
        when(alcance.laVacanteVisible(eq(TALENTO), eq(30L), any()))
                .thenReturn(vacante(30L, "Con plantilla", "PLANTILLA").estado("BORRADOR").build());
        when(alcance.laVacanteVisible(eq(TALENTO), eq(31L), any()))
                .thenReturn(vacante(31L, "Con cuestionario", "CUESTIONARIO_TECNICO").estado("BORRADOR").build());

        for (long destino : List.of(30L, 31L)) {
            assertThatThrownBy(() -> servicio.copiar(TALENTO, destino, new CopiarDeOtraVacante(1L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no rinde la prueba del editor");
        }
        verify(versionesBanco, never()).saveAndFlush(any());
        verify(vacantes, never()).findByIdAndOrganizacionIdAndEliminadaEnIsNull(anyLong(), anyLong());
    }
}
