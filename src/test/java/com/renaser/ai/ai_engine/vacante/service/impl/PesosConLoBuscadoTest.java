package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.service.RecalculoConOtrosPesos;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.EtapaRepository;
import com.renaser.ai.ai_engine.pesos.repository.PesoComponentePerfilRepository;
import com.renaser.ai.ai_engine.pesos.repository.PesoEtapaRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.CambioDePesosPrevisto;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.PesoAntesYDespues;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** AC-22: la confirmación del cambio de versión enseña el peso de «Qué buscar en el CV». */
@ExtendWith(MockitoExtension.class)
@DisplayName("Los pesos de la vacante: la fila de «Qué buscar en el CV»")
class PesosConLoBuscadoTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long PLATAFORMA = 99L;
    private static final Long VACANTE = 40L;
    private static final Long ANTES = 2L;
    private static final Long AHORA = 9L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private PesoEtapaRepository pesosEtapa;
    @Mock private PesoComponentePerfilRepository pesosComponente;
    @Mock private EtapaRepository etapas;
    @Mock private DuenoDelInstrumento dueno;
    @Mock private RecalculoConOtrosPesos recalculo;
    @Mock private ServicioAuditoria auditoria;
    @Mock private LoBuscadoEnElCv loBuscado;

    private ServicioPesosDeLaVacanteImpl servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioPesosDeLaVacanteImpl(vacantes, puestos, versionesPesos, pesosEtapa,
                pesosComponente, etapas, dueno, recalculo, auditoria);
        servicio.setLoBuscado(loBuscado);
        Vacante vacante = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                .estado("PUBLICADA").versionPesosId(ANTES).build();
        when(vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(vacante));
        when(dueno.duenoDe(ORGANIZACION, Instrumento.PESOS)).thenReturn(PLATAFORMA);
        lenient().when(dueno.plataforma()).thenReturn(Organizacion.builder().id(PLATAFORMA).build());
        when(versionesPesos.findByIdAndOrganizacionId(AHORA, PLATAFORMA)).thenReturn(Optional.of(
                VersionPesos.builder().id(AHORA).organizacionId(PLATAFORMA).etiqueta("Con 30")
                        .estado("PUBLICADA").pesoBusquedaCv(BigDecimal.valueOf(30)).build()));
        lenient().when(recalculo.conAlgunaNota(VACANTE)).thenReturn(List.of(1L, 2L));
    }

    @Test
    @DisplayName("Con líneas en la vacante sale «Qué buscar en el CV 25 % → 30 %»")
    void conLineasSaleLaFila() {
        when(loBuscado.vigentes(VACANTE)).thenReturn(List.of(
                new LoBuscadoEnElCv.LineaVigente(1L, 1, "Controla costos", 1)));
        when(loBuscado.pesoDe(ANTES)).thenReturn(BigDecimal.valueOf(25));

        CambioDePesosPrevisto previsto = servicio.preverCambio(QUIEN, VACANTE, AHORA);

        assertThat(previsto.cambia()).isTrue();
        assertThat(previsto.personasConNota()).isEqualTo(2);
        PesoAntesYDespues fila = previsto.queBuscarEnElCv();
        assertThat(fila.nombre()).isEqualTo("Qué buscar en el CV");
        assertThat(fila.antes()).isEqualByComparingTo("25");
        assertThat(fila.despues()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("Sin líneas la fila no sale: no cambia ninguna nota")
    void sinLineasNoSale() {
        when(loBuscado.vigentes(VACANTE)).thenReturn(List.of());

        assertThat(servicio.preverCambio(QUIEN, VACANTE, AHORA).queBuscarEnElCv()).isNull();
    }
}
