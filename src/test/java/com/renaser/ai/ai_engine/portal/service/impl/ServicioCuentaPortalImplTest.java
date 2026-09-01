package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.SolicitudBorradoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.CrearCuenta;
import com.renaser.ai.ai_engine.seguridad.service.IntentosLogin;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El login del portal es SOLO de candidatos: la cuenta y sus credenciales cuelgan de la
 * organización plataforma, y una cuenta de equipo —aunque viva ahí desde la V37— no es
 * un candidato.
 *
 * <p>Y desde que la ciudad es obligatoria, crear la cuenta es también el único momento en
 * que se pregunta dónde vive quien postula: aquí se comprueba que el código llega al
 * catálogo antes de que nazca ninguna fila.
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
    @Mock private CatalogosDelPerfil catalogos;

    private ServicioCuentaPortalImpl servicio;

    @BeforeEach
    void crearElServicio() {
        // El resolutor de la plataforma va de verdad sobre el repositorio simulado: el
        // stub de findByEsPlataformaTrue cuenta la misma historia que antes del corte.
        servicio = new ServicioCuentaPortalImpl(new DuenoDelInstrumento(organizaciones),
                personas, usuarios, roles, usuarioRoles, textosConsentimiento, consentimientos,
                solicitudesBorrado, correo, auditoria, parametros, tokens, intentos, codificador,
                catalogos);
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

    // ============ La ciudad, al crear la cuenta ============

    @Test
    @DisplayName("una ciudad que el catálogo no ofrece no crea la cuenta: 400 y ni una fila")
    void unaCiudadQueNoExisteNoCreaLaCuenta() {
        // El caso que de verdad pasa no es un código inventado, sino uno REAL que el
        // desplegable nunca ofrece: «04» es Arequipa el departamento y existe en la tabla.
        // Con un existsById entraría, y esa persona quedaría con una ciudad que ninguna
        // pantalla sabe pintar ni ningún filtro sabe encontrar.
        when(catalogos.esCiudadElegible("04")).thenReturn(false);

        assertThatThrownBy(() -> servicio.crearCuenta(cuentaEn("04"), "10.0.0.1", "Firefox"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("04")
                .hasMessageContaining("/api/v1/portal/catalogos/ubigeo");

        // Antes que nada: si la validación llegara después del save, un código malo
        // dejaría media cuenta escrita y un correo enviado.
        verifyNoInteractions(personas, usuarios, correo);
    }

    @Test
    @DisplayName("la ciudad elegida queda escrita en la persona, no en el perfil")
    void laCiudadSeGuardaEnLaPersona() {
        // Va en persona y no en perfil_candidato porque el perfil se crea perezosamente:
        // en este instante la única fila que existe de esta candidata es la persona.
        when(catalogos.esCiudadElegible("0402")).thenReturn(true);
        when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.organizacion.entity.Organizacion.builder()
                        .id(ORGANIZACION).esPlataforma(true).build()));
        when(usuarios.buscarPorCorreo(ORGANIZACION, "camila@correo.pe"))
                .thenReturn(Optional.empty());
        when(personas.save(any(Persona.class)))
                .thenAnswer(i -> { Persona p = i.getArgument(0); p.setId(5L); return p; });
        when(usuarios.save(any(Usuario.class)))
                .thenAnswer(i -> { Usuario u = i.getArgument(0); u.setId(9L); return u; });
        when(textosConsentimiento
                .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                        ORGANIZACION, "PROCESO"))
                .thenReturn(Optional.of(com.renaser.ai.ai_engine.consentimiento.entity
                        .TextoConsentimiento.builder().id(1L).tipo("PROCESO").build()));
        lenient().when(codificador.encode(anyString())).thenReturn("$hash");

        servicio.crearCuenta(cuentaEn("0402"), "10.0.0.1", "Firefox");

        ArgumentCaptor<Persona> guardada = ArgumentCaptor.forClass(Persona.class);
        verify(personas).save(guardada.capture());
        assertThat(guardada.getValue().getCiudadUbigeo()).isEqualTo("0402");
    }

    private CrearCuenta cuentaEn(String ciudadUbigeo) {
        return new CrearCuenta("Camila", "Reyes", "camila@correo.pe", "unaClaveLarga123",
                ciudadUbigeo, true, false);
    }
}
