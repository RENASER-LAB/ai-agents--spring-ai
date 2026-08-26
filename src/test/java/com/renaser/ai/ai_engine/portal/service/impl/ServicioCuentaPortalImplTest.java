package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.SolicitudBorradoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.seguridad.service.IntentosLogin;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El login del portal es SOLO de candidatos: la cuenta y sus credenciales cuelgan de la
 * organización plataforma, y una cuenta de equipo —aunque viva ahí desde la V37— no es
 * un candidato.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La cuenta del candidato en el portal")
class ServicioCuentaPortalImplTest {

    private static final Long ORGANIZACION = 1L;

    @Mock private OrganizacionRepository organizaciones;
    @Mock private PersonaRepository personas;
    @Mock private UsuarioRepository usuarios;
    @Mock private RolRepository roles;
    @Mock private UsuarioRolRepository usuarioRoles;
    @Mock private TextoConsentimientoRepository textosConsentimiento;
    @Mock private ConsentimientoRepository consentimientos;
    @Mock private SolicitudBorradoRepository solicitudesBorrado;
    @Mock private ServicioCorreo correo;
    @Mock private ServicioAuditoria auditoria;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioToken tokens;
    @Mock private IntentosLogin intentos;
    @Mock private PasswordEncoder codificador;

    private ServicioCuentaPortalImpl servicio;

    @BeforeEach
    void crearElServicio() {
        // El resolutor de la plataforma va de verdad sobre el repositorio simulado: el
        // stub de findByEsPlataformaTrue cuenta la misma historia que antes del corte.
        servicio = new ServicioCuentaPortalImpl(new DuenoDelInstrumento(organizaciones),
                personas, usuarios, roles, usuarioRoles, textosConsentimiento, consentimientos,
                solicitudesBorrado, correo, auditoria, parametros, tokens, intentos, codificador);
    }

    @Test
    @DisplayName("una cuenta de equipo no entra al portal aunque su contraseña cuadre")
    void unaCuentaDeEquipoNoEntraAlPortal() {
        // El espejo del login del panel: desde la V37 el equipo también tiene contraseña,
        // y sin el filtro es_equipo la gente del panel de la plataforma abría el portal
        // como candidata. Ni siquiera se le llega a comprobar la contraseña.
        when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.organizacion.entity.Organizacion.builder()
                        .id(ORGANIZACION).esPlataforma(true).build()));
        when(usuarios.buscarPorCorreo(ORGANIZACION, "recluta@renaser.pe"))
                .thenReturn(Optional.of(Usuario.builder()
                        .id(60L).organizacionId(ORGANIZACION).correo("recluta@renaser.pe")
                        .contrasenaHash("$hash").esEquipo(true).esActivo(true)
                        .build()));

        assertThatThrownBy(() -> servicio.entrar(new com.renaser.ai.ai_engine.portal.dto
                        .DtosPortal.Login("recluta@renaser.pe", "su-contrasena-real")))
                .isInstanceOf(com.renaser.ai.ai_engine.seguridad.exception
                        .CredencialesInvalidasException.class)
                .hasMessageContaining("Correo o contraseña incorrectos");
        verifyNoInteractions(codificador);
    }
}
