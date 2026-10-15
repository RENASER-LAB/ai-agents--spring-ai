package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.perfilintegral.service.RecalculoConOtrosPesos;
import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import com.renaser.ai.ai_engine.pesos.entity.PesoComponentePerfil;
import com.renaser.ai.ai_engine.pesos.entity.PesoEtapa;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.EtapaRepository;
import com.renaser.ai.ai_engine.pesos.repository.PesoComponentePerfilRepository;
import com.renaser.ai.ai_engine.pesos.repository.PesoEtapaRepository;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.CambioDePesosHecho;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.CambioDePesosPrevisto;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.PesoAntesYDespues;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cambiar la versión de pesos de una vacante con candidatos dentro (V74, parte A).
 *
 * <p>Hasta la V74, desde la primera postulación contestaba 409 («una vacante, una versión»).
 * Ahora se cambia y se recalcula a todos en la misma operación: la vara cambia, pero para
 * todos. Lo que se defiende aquí es la puerta —qué vacantes y qué versiones lo admiten, que
 * la misma versión no haga nada, que quede registrado— y que el recálculo vaya dentro.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Los pesos de la vacante, con candidatos dentro")
class ServicioPesosDeLaVacanteImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long PLATAFORMA = 99L;
    private static final Long VACANTE = 40L;
    private static final Long PUESTO = 5L;
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

    private ServicioPesosDeLaVacanteImpl servicio;
    private Vacante vacante;

    @BeforeEach
    void armar() {
        servicio = new ServicioPesosDeLaVacanteImpl(vacantes, puestos, versionesPesos, pesosEtapa,
                pesosComponente, etapas, dueno, recalculo, auditoria);
        vacante = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION).estado("PUBLICADA")
                .puestoId(PUESTO).versionPesosId(ANTES).build();
        lenient().when(vacantes.bloquear(VACANTE, ORGANIZACION)).thenReturn(Optional.of(vacante));
        lenient().when(vacantes.findByIdAndOrganizacionIdAndEliminadaEnIsNull(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(vacante));
        lenient().when(puestos.findByIdAndOrganizacionId(PUESTO, ORGANIZACION)).thenReturn(Optional.of(
                Puesto.builder().id(PUESTO).organizacionId(ORGANIZACION).nivelPuestoCodigo("EJECUCION").build()));
        // Sin pesos propios: lee las versiones de la plataforma (decisión 6, AC-15)
        lenient().when(dueno.duenoDe(ORGANIZACION, Instrumento.PESOS)).thenReturn(PLATAFORMA);
        lenient().when(dueno.plataforma()).thenReturn(Organizacion.builder().id(PLATAFORMA).build());
    }

    private void laNuevaEs(String estado) {
        when(versionesPesos.findByIdAndOrganizacionId(AHORA, PLATAFORMA)).thenReturn(Optional.of(
                VersionPesos.builder().id(AHORA).organizacionId(PLATAFORMA)
                        .etiqueta("CAZATALENTOS · PYME").estado(estado).build()));
    }

    // ============ Cambiar ============

    @Test
    @DisplayName("con candidatos dentro se cambia y se recalcula a todos en la misma operación, "
            + "y queda registrado con las dos versiones y cuántas personas (AC-01, AC-08)")
    void conCandidatosSeCambiaYSeRecalcula() {
        laNuevaEs("PUBLICADA");
        when(recalculo.recalcular(vacante, ANTES, "EJECUCION")).thenReturn(23);

        CambioDePesosHecho hecho = servicio.cambiar(QUIEN, VACANTE, AHORA);

        assertThat(hecho).isEqualTo(new CambioDePesosHecho(AHORA, 23));
        assertThat(vacante.getVersionPesosId()).isEqualTo(AHORA);
        // La vacante ya tiene la nueva cuando se recalcula: las cuentas la leen de ella
        InOrder orden = inOrder(vacantes, recalculo, auditoria);
        orden.verify(vacantes).saveAndFlush(vacante);
        orden.verify(recalculo).recalcular(vacante, ANTES, "EJECUCION");
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("versionPesosId", "2");
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("versionPesosId", "9");
        despues.put("personasRecalculadas", 23);
        orden.verify(auditoria).registrar(ORGANIZACION, QUIEN, "asignar_version_pesos",
                "vacante", VACANTE, antes, despues, null);
    }

    @Test
    @DisplayName("elegir la versión que ya tiene no recalcula ni registra nada")
    void laMismaNoHaceNada() {
        vacante.setVersionPesosId(AHORA);
        laNuevaEs("PUBLICADA");

        assertThat(servicio.cambiar(QUIEN, VACANTE, AHORA)).isEqualTo(new CambioDePesosHecho(AHORA, 0));

        verify(vacantes, never()).saveAndFlush(any());
        verifyNoInteractions(recalculo, auditoria);
    }

    @Test
    @DisplayName("si el recálculo falla a mitad, el error sube y no se registra nada: la "
            + "transacción deshace la versión y las notas (AC-07)")
    void unFalloAMitadNoRegistraNada() {
        laNuevaEs("PUBLICADA");
        when(recalculo.recalcular(vacante, ANTES, "EJECUCION"))
                .thenThrow(new IllegalStateException("No se pudo recalcular la nota de SIMULACION"));

        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, AHORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SIMULACION");
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("una vacante cerrada contesta 409 y no se toca (AC-06)")
    void unaCerradaNoCambia() {
        vacante.setEstado("CERRADA");

        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, AHORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cerrada");
        assertThat(vacante.getVersionPesosId()).isEqualTo(ANTES);
        verifyNoInteractions(recalculo, auditoria);
    }

    @Test
    @DisplayName("una vacante archivada contesta 409 (AC-06)")
    void unaArchivadaNoCambia() {
        vacante.setEstado("CERRADA");
        vacante.setArchivadaEn(Instant.now());

        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, AHORA))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(recalculo, auditoria);
    }

    @Test
    @DisplayName("una vacante eliminada contesta 409, no 404: existe y es de la empresa (AC-06)")
    void unaEliminadaNoCambia() {
        when(vacantes.bloquear(VACANTE, ORGANIZACION)).thenReturn(Optional.empty());
        vacante.setEliminadaEn(Instant.now());
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(vacante));

        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, AHORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eliminó");
        verifyNoInteractions(recalculo, auditoria);
    }

    @Test
    @DisplayName("la vacante de otra empresa no existe: 404")
    void laDeOtraEmpresaNoExiste() {
        when(vacantes.bloquear(VACANTE, ORGANIZACION)).thenReturn(Optional.empty());
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, AHORA))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("solo una versión publicada (RF-114)")
    void soloUnaPublicada() {
        laNuevaEs("BORRADOR");

        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, AHORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("borrador");
        verifyNoInteractions(recalculo);
    }

    @Test
    @DisplayName("una versión de pesos de otra empresa no existe para quien pregunta: 404")
    void laVersionDeOtraEmpresaNoExiste() {
        when(versionesPesos.findByIdAndOrganizacionId(AHORA, PLATAFORMA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiar(QUIEN, VACANTE, AHORA))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(recalculo);
    }

    @Test
    @DisplayName("publicada y sin nadie con nota, se cambia igual: no hay a quién recalcular (AC-06)")
    void sinNadieConNotaSeCambia() {
        laNuevaEs("PUBLICADA");
        when(recalculo.recalcular(vacante, ANTES, "EJECUCION")).thenReturn(0);

        assertThat(servicio.cambiar(QUIEN, VACANTE, AHORA).personasRecalculadas()).isZero();
        assertThat(vacante.getVersionPesosId()).isEqualTo(AHORA);
    }

    // ============ Lo que enseña la confirmación ============

    @Test
    @DisplayName("la confirmación dice a cuántos se recalcula y los pesos de cada etapa y del "
            + "Perfil Integral, antes y después, sin escribir nada")
    void laConfirmacionCuentaYCompara() {
        laNuevaEs("PUBLICADA");
        when(versionesPesos.findByIdAndOrganizacionId(ANTES, ORGANIZACION)).thenReturn(Optional.empty());
        when(versionesPesos.findByIdAndOrganizacionId(ANTES, PLATAFORMA)).thenReturn(Optional.of(
                VersionPesos.builder().id(ANTES).etiqueta("CAZATALENTOS · MICRO").build()));
        when(recalculo.conAlgunaNota(VACANTE)).thenReturn(List.of(11L, 12L, 13L));
        when(etapas.findAllByOrderByOrdenAsc()).thenReturn(List.of(
                etapa("PERFIL_INTEGRAL", "Perfil Integral de Preselección", 1),
                etapa("PRUEBA_PUESTO", "Prueba del puesto", 2),
                etapa("SIMULACION", "Simulación de trabajo", 3),
                etapa("VALIDACION", "Validación práctica", 4),
                etapa("DECISION", "Decisión", 5)));
        when(pesosEtapa.findByVersionPesosId(ANTES)).thenReturn(List.of(
                pesoEtapa("PERFIL_INTEGRAL", "40"), pesoEtapa("PRUEBA_PUESTO", "30"),
                pesoEtapa("SIMULACION", "15"), pesoEtapa("VALIDACION", "15")));
        when(pesosEtapa.findByVersionPesosId(AHORA)).thenReturn(List.of(
                pesoEtapa("PERFIL_INTEGRAL", "40"), pesoEtapa("PRUEBA_PUESTO", "35"),
                pesoEtapa("SIMULACION", "15"), pesoEtapa("VALIDACION", "10")));
        when(pesosComponente.findByVersionPesosId(ANTES)).thenReturn(List.of(
                componente("CV", "12"), componente("PSICOMETRICO", "0"), componente("EVALUACION", "28")));
        when(pesosComponente.findByVersionPesosId(AHORA)).thenReturn(List.of(
                componente("CV", "20"), componente("EVALUACION", "20")));

        CambioDePesosPrevisto previsto = servicio.preverCambio(QUIEN, VACANTE, AHORA);

        assertThat(previsto.personasConNota()).isEqualTo(3);
        assertThat(previsto.cambia()).isTrue();
        assertThat(previsto.etiquetaAnterior()).isEqualTo("CAZATALENTOS · MICRO");
        assertThat(previsto.etiquetaNueva()).isEqualTo("CAZATALENTOS · PYME");
        assertThat(previsto.etapas()).extracting(PesoAntesYDespues::codigo).containsExactly(
                "PERFIL_INTEGRAL", "PRUEBA_PUESTO", "SIMULACION", "VALIDACION");
        assertThat(previsto.etapas().get(1).antes()).isEqualByComparingTo("30");
        assertThat(previsto.etapas().get(1).despues()).isEqualByComparingTo("35");
        // El psicométrico no existe y pesa 0 en las dos: no se enseña
        assertThat(previsto.perfilIntegral()).extracting(PesoAntesYDespues::nombre)
                .containsExactly("Currículum", "Preguntas");
        assertThat(previsto.perfilIntegral().get(0).antes()).isEqualByComparingTo("12");
        assertThat(previsto.perfilIntegral().get(0).despues()).isEqualByComparingTo("20");
        verify(recalculo, never()).recalcular(any(), any(), any());
        verify(vacantes, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("con la misma versión, la confirmación dice que no cambia y no cuenta a nadie")
    void laConfirmacionDeLaMisma() {
        vacante.setVersionPesosId(AHORA);
        laNuevaEs("PUBLICADA");
        when(versionesPesos.findByIdAndOrganizacionId(AHORA, ORGANIZACION)).thenReturn(Optional.empty());

        CambioDePesosPrevisto previsto = servicio.preverCambio(QUIEN, VACANTE, AHORA);

        assertThat(previsto.cambia()).isFalse();
        assertThat(previsto.personasConNota()).isZero();
        verify(recalculo, never()).conAlgunaNota(any());
    }

    private static Etapa etapa(String codigo, String nombre, int orden) {
        return Etapa.builder().codigo(codigo).nombre(nombre).orden(orden).build();
    }

    private static PesoEtapa pesoEtapa(String etapa, String peso) {
        return PesoEtapa.builder().etapaCodigo(etapa).peso(new BigDecimal(peso)).build();
    }

    private static PesoComponentePerfil componente(String componente, String peso) {
        return PesoComponentePerfil.builder().componente(componente).peso(new BigDecimal(peso)).build();
    }
}
