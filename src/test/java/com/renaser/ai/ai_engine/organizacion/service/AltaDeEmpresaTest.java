package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.CrearEmpresa;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaCreada;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.impl.ServicioPlataformaImpl;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.InvitacionCreada;
import com.renaser.ai.ai_engine.seguridad.service.ServicioInvitaciones;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El alta de una empresa: nace con su día uno completo, o no nace.
 *
 * <p>La siembra no es cortesía. Sin parámetros la empresa no puede configurarse (editar
 * un parámetro no crea filas); sin plantillas de correo sus avisos se pierden en
 * silencio; sin la matriz de roles su administrador no puede hacer nada. Y la matriz se
 * copia con una excepción a propósito: administrar_plataforma no viaja, porque dar de
 * alta empresas es de la dueña de la plataforma.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El alta de una empresa en la plataforma")
class AltaDeEmpresaTest {

    private static final Long PLATAFORMA = 1L;
    private static final ContextoUsuario DUENA = new ContextoUsuario(
            10L, 20L, PLATAFORMA, "EQUIPO", List.of(), Map.of());
    private static final ContextoUsuario INTRUSA = new ContextoUsuario(
            30L, 40L, 2L, "EQUIPO", List.of(), Map.of());

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
                        .id(PLATAFORMA).codigo("RENASER").esPlataforma(true).build()));
    }

    @Test
    @DisplayName("El alta crea la empresa, siembra sus cinco copias e invita a su administrador")
    void elAltaSiembraCompletoEInvita() {
        when(organizaciones.findByCodigo("ACME")).thenReturn(Optional.empty());
        when(organizaciones.save(any(Organizacion.class)))
                .thenAnswer(inv -> { Organizacion o = inv.getArgument(0); o.setId(2L); return o; });
        when(invitaciones.crearParaOrganizacion(DUENA, 2L, "admin@acme.pe", List.of("ADMINISTRADOR")))
                .thenReturn(new InvitacionCreada(99L, "https://panel/invitacion?token=x", Instant.now()));

        EmpresaCreada creada = servicio.crearEmpresa(DUENA,
                new CrearEmpresa("Acme S.A.", "ACME", "admin@acme.pe"));

        assertThat(creada.id()).isEqualTo(2L);
        assertThat(creada.urlInvitacion()).contains("token=");

        // Las cinco copias de la siembra, cada una contra su tabla
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(5)).update(sql.capture(), any(Object[].class));
        List<String> sentencias = sql.getAllValues();
        assertThat(sentencias.get(0)).contains("INSERT INTO rol ");
        assertThat(sentencias.get(1)).contains("INSERT INTO rol_permiso")
                // La excepción deliberada: el permiso de la dueña no viaja con la copia
                .contains("administrar_plataforma");
        assertThat(sentencias.get(2)).contains("INSERT INTO parametro");
        // Los textos legales nacen en borrador (publicado_en vacío): nombran a Renaser
        assertThat(sentencias.get(3)).contains("INSERT INTO texto_consentimiento").contains("NULL");
        // Los correos nacen activos: los avisos de sus vacantes tienen que salir
        assertThat(sentencias.get(4)).contains("INSERT INTO plantilla_correo").contains("true");
    }

    @Test
    @DisplayName("Alguien de una empresa no da de alta empresas, aunque tenga el permiso")
    void unaEmpresaNoDaDeAltaEmpresas() {
        assertThatThrownBy(() -> servicio.crearEmpresa(INTRUSA,
                new CrearEmpresa("Colada S.A.", "COLADA", "admin@colada.pe")))
                .isInstanceOf(AccessDeniedException.class);
        verify(organizaciones, never()).save(any());
        verify(invitaciones, never()).crearParaOrganizacion(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Un código repetido no crea nada: el código identifica a la empresa")
    void unCodigoRepetidoNoCreaNada() {
        when(organizaciones.findByCodigo("ACME"))
                .thenReturn(Optional.of(Organizacion.builder().id(7L).codigo("ACME").build()));

        assertThatThrownBy(() -> servicio.crearEmpresa(DUENA,
                new CrearEmpresa("Acme S.A.", "ACME", "admin@acme.pe")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACME");
        verify(organizaciones, never()).save(any());
    }
}
