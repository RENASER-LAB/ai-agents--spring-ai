package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.impl.ServicioPlataformaImpl;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.ServicioInvitaciones;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suspender una empresa la congela, no la borra (pieza F).
 *
 * <p>Lo que se protege: que la suspensión sea UN bit ({@code es_activa}) con su motivo en
 * la auditoría — el login, el filtro de identidad y el tablón lo leen, cada uno con su
 * prueba —, que la plataforma no pueda suspenderse a sí misma, y que el tope de IA se
 * ponga validado y auditado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La suspensión de empresas y el tope, desde la plataforma")
class SuspensionDeEmpresasTest {

    private static final Long PLATAFORMA = 1L;
    private static final Long EMPRESA = 2L;
    private static final ContextoUsuario DUENA = new ContextoUsuario(
            10L, 20L, PLATAFORMA, "EQUIPO", List.of(), Map.of());

    @Mock private OrganizacionRepository organizaciones;
    @Mock private ServicioInvitaciones invitaciones;
    @Mock private ServicioAuditoria auditoria;
    @Mock private JdbcTemplate jdbc;

    private ServicioPlataformaImpl servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioPlataformaImpl(organizaciones, invitaciones, auditoria, jdbc);
        lenient().when(organizaciones.findByEsPlataformaTrue())
                .thenReturn(Optional.of(Organizacion.builder()
                        .id(PLATAFORMA).codigo("RENASER").esPlataforma(true).esActiva(true).build()));
    }

    private Organizacion acme(boolean activa) {
        Organizacion acme = Organizacion.builder()
                .id(EMPRESA).codigo("ACME").nombre("Acme S.A.C.").esActiva(activa).build();
        lenient().when(organizaciones.findById(EMPRESA)).thenReturn(Optional.of(acme));
        return acme;
    }

    @Test
    @DisplayName("Suspender apaga es_activa y deja el motivo escrito en la auditoría")
    void suspenderApagaYAudita() {
        Organizacion acme = acme(true);

        servicio.suspender(DUENA, EMPRESA, "Impago de tres meses");

        assertThat(acme.isEsActiva()).isFalse();
        verify(organizaciones).save(acme);
        verify(auditoria).registrar(eq(PLATAFORMA), eq(DUENA), eq("suspender_empresa"),
                eq("organizacion"), eq(EMPRESA), any(), any(), eq("Impago de tres meses"));
    }

    @Test
    @DisplayName("La plataforma no puede suspenderse a sí misma")
    void laPlataformaNoSeSuspendeASiMisma() {
        // El candado de la puerta no puede quedarse dentro de la casa: sin plataforma
        // activa, nadie podría reactivar nada.
        when(organizaciones.findById(PLATAFORMA)).thenReturn(Optional.of(Organizacion.builder()
                .id(PLATAFORMA).esPlataforma(true).esActiva(true).build()));

        assertThatThrownBy(() -> servicio.suspender(DUENA, PLATAFORMA, "un descuido"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a sí misma");
        verify(organizaciones, never()).save(any());
    }

    @Test
    @DisplayName("Suspender dos veces avisa, y reactivar deja todo tal cual")
    void suspenderDosVecesAvisaYReactivarVuelve() {
        Organizacion dormida = acme(false);

        assertThatThrownBy(() -> servicio.suspender(DUENA, EMPRESA, "otra vez"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya está suspendida");

        servicio.reactivar(DUENA, EMPRESA, "Se puso al día");

        assertThat(dormida.isEsActiva()).isTrue();
        verify(auditoria).registrar(eq(PLATAFORMA), eq(DUENA), eq("reactivar_empresa"),
                eq("organizacion"), eq(EMPRESA), any(), any(), eq("Se puso al día"));
    }

    @Test
    @DisplayName("Solo la plataforma suspende: desde una empresa, ni con el permiso")
    void soloLaPlataformaSuspende() {
        ContextoUsuario intrusa = new ContextoUsuario(
                30L, 40L, EMPRESA, "EQUIPO", List.of(), Map.of());

        assertThatThrownBy(() -> servicio.suspender(intrusa, 3L, "competencia"))
                .isInstanceOf(AccessDeniedException.class);
        verify(organizaciones, never()).save(any());
    }

    @Test
    @DisplayName("El tope se guarda validado y auditado; uno ilegible o negativo no entra")
    void elTopeSeGuardaValidadoYAuditado() {
        acme(true);

        servicio.ponerTopeIa(DUENA, EMPRESA, "150.50");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("tope_mensual_ia"),
                eq(EMPRESA), eq("150.50"));
        verify(auditoria).registrar(eq(PLATAFORMA), eq(DUENA), eq("poner_tope_ia"),
                eq("organizacion"), eq(EMPRESA), any(), any(), any());

        assertThatThrownBy(() -> servicio.ponerTopeIa(DUENA, EMPRESA, "mucho"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("número");
        assertThatThrownBy(() -> servicio.ponerTopeIa(DUENA, EMPRESA, "-5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    @DisplayName("El tope en blanco significa quitárselo: valor vacío, que es «ausente»")
    void elTopeEnBlancoLoQuita() {
        acme(true);

        servicio.ponerTopeIa(DUENA, EMPRESA, "  ");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("tope_mensual_ia"),
                eq(EMPRESA), eq(""));
    }
}
