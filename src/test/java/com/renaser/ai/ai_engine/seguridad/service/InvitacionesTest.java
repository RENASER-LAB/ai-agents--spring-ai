package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.AceptarInvitacion;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.InvitacionCreada;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.service.impl.ServicioInvitacionesImpl;
import com.renaser.ai.ai_engine.usuario.entity.Invitacion;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Rol;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.InvitacionRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las invitaciones al panel: la única puerta, de un solo uso.
 *
 * <p>Las dos cosas que estas pruebas defienden: el token jamás se guarda —solo su hash—,
 * y una invitación gastada, vencida o revocada contesta siempre el mismo error, porque
 * distinguirlos le diría a un atacante cuál de sus tokens robados sigue vivo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Las invitaciones al panel")
class InvitacionesTest {

    private static final Long ORG = 2L;
    private static final ContextoUsuario ADMIN = new ContextoUsuario(
            10L, 20L, ORG, "EQUIPO", List.of(), Map.of());

    @Mock private InvitacionRepository invitaciones;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private PersonaRepository personas;
    @Mock private UsuarioRepository usuarios;
    @Mock private RolRepository roles;
    @Mock private UsuarioRolRepository usuarioRoles;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder codificador;
    @Mock private ServicioToken tokens;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioCorreo correo;
    @Mock private ServicioAuditoria auditoria;

    private ServicioInvitacionesImpl servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioInvitacionesImpl(invitaciones, organizaciones, personas, usuarios,
                roles, usuarioRoles, codificador, tokens, parametros, correo, auditoria);
        ReflectionTestUtils.setField(servicio, "urlDelPanel", "https://panel.ejemplo.test");
        lenient().when(invitaciones.save(any(Invitacion.class)))
                .thenAnswer(inv -> {
                    Invitacion i = inv.getArgument(0);
                    if (i.getId() == null) i.setId(99L);
                    return i;
                });
    }

    private void hayOrganizacion() {
        when(organizaciones.findById(ORG)).thenReturn(Optional.of(
                Organizacion.builder().id(ORG).codigo("ACME").nombre("Acme S.A.").build()));
    }

    // ---------- Crear ----------

    @Test
    @DisplayName("Crear guarda el hash y no el token, y el enlace de la respuesta sí lo lleva")
    void crearGuardaElHashNoElToken() {
        hayOrganizacion();
        when(roles.findByOrganizacionIdAndCodigo(ORG, "TALENTO"))
                .thenReturn(Optional.of(Rol.builder().id(5L).codigo("TALENTO").build()));
        when(usuarios.buscarPorCorreo(ORG, "nuevo@acme.pe")).thenReturn(Optional.empty());
        when(parametros.entero(ORG, "dias_invitacion", 7)).thenReturn(7);

        InvitacionCreada creada = servicio.crear(ADMIN, "Nuevo@Acme.pe ", List.of("TALENTO"));

        ArgumentCaptor<Invitacion> guardada = ArgumentCaptor.forClass(Invitacion.class);
        verify(invitaciones).save(guardada.capture());
        String token = creada.url().substring(creada.url().indexOf("token=") + 6);
        // El hash es SHA-256 en hexadecimal: 64 caracteres que no son el token
        assertThat(guardada.getValue().getTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(guardada.getValue().getCorreo()).isEqualTo("nuevo@acme.pe");
        assertThat(guardada.getValue().getRoles()).isEqualTo("TALENTO");
        assertThat(creada.url()).startsWith("https://panel.ejemplo.test/invitacion?token=");

        // El correo sale con la plantilla de la organización invitada y sin usuario:
        // el invitado todavía no tiene cuenta
        verify(correo).enviar(eq(ORG), isNull(), eq("nuevo@acme.pe"),
                eq("INVITACION_EQUIPO"), any());
    }

    @Test
    @DisplayName("Un rol equivocado le revienta a quien invita, no al invitado días después")
    void unRolEquivocadoRevientaAlInvitar() {
        hayOrganizacion();
        when(roles.findByOrganizacionIdAndCodigo(ORG, "GERENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(ADMIN, "nuevo@acme.pe", List.of("GERENTE")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(invitaciones, never()).save(any());
    }

    @Test
    @DisplayName("No se invita a un correo que ya tiene cuenta en la organización")
    void noSeInvitaAUnCorreoConCuenta() {
        hayOrganizacion();
        when(roles.findByOrganizacionIdAndCodigo(ORG, "TALENTO"))
                .thenReturn(Optional.of(Rol.builder().id(5L).build()));
        when(usuarios.buscarPorCorreo(ORG, "ya@acme.pe"))
                .thenReturn(Optional.of(Usuario.builder().id(1L).build()));

        assertThatThrownBy(() -> servicio.crear(ADMIN, "ya@acme.pe", List.of("TALENTO")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ya existe una cuenta");
    }

    // ---------- Aceptar ----------

    private Invitacion vigente() {
        return Invitacion.builder()
                .id(99L).organizacionId(ORG).correo("nuevo@acme.pe")
                .roles("ADMINISTRADOR,TALENTO")
                .tokenHash("da-igual").venceEn(Instant.now().plus(7, ChronoUnit.DAYS))
                .creadaPorUsuarioId(10L).creadoEn(Instant.now())
                .build();
    }

    private AceptarInvitacion datosDeCanje() {
        return new AceptarInvitacion("un-token", "Ana", "Torres", "contrasena-de-doce!");
    }

    @Test
    @DisplayName("Canjear crea la cuenta de equipo con los roles invitados y gasta la invitación")
    void canjearCreaLaCuentaYGastaLaInvitacion() {
        Invitacion invitacion = vigente();
        when(invitaciones.findByTokenHash(anyString())).thenReturn(Optional.of(invitacion));
        when(usuarios.buscarPorCorreo(ORG, "nuevo@acme.pe")).thenReturn(Optional.empty());
        when(personas.save(any(Persona.class)))
                .thenAnswer(inv -> { Persona p = inv.getArgument(0); p.setId(70L); return p; });
        when(usuarios.save(any(Usuario.class)))
                .thenAnswer(inv -> { Usuario u = inv.getArgument(0); u.setId(80L); return u; });
        when(roles.findByOrganizacionIdAndCodigo(eq(ORG), anyString()))
                .thenReturn(Optional.of(Rol.builder().id(5L).build()));
        when(codificador.encode("contrasena-de-doce!")).thenReturn("$hash");
        when(tokens.emitir(80L, ORG, "EQUIPO")).thenReturn("el-token");

        Sesion sesion = servicio.aceptar(datosDeCanje());

        assertThat(sesion.token()).isEqualTo("el-token");
        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(usuario.capture());
        assertThat(usuario.getValue().isEsEquipo()).isTrue();
        assertThat(usuario.getValue().getContrasenaHash()).isEqualTo("$hash");
        assertThat(invitacion.getAceptadaEn()).isNotNull();
        // Un rol por cada código invitado
        verify(usuarioRoles, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    @DisplayName("Una invitación vencida contesta el mismo error que una inexistente")
    void unaVencidaContestaElMismoError() {
        Invitacion caducada = vigente();
        caducada.setVenceEn(Instant.now().minus(1, ChronoUnit.DAYS));
        when(invitaciones.findByTokenHash(anyString())).thenReturn(Optional.of(caducada));

        assertThatThrownBy(() -> servicio.aceptar(datosDeCanje()))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("La invitación no es válida o ya venció");
    }

    @Test
    @DisplayName("Una invitación revocada tampoco entra, con el mismo error")
    void unaRevocadaTampocoEntra() {
        Invitacion revocada = vigente();
        revocada.setRevocadaEn(Instant.now());
        when(invitaciones.findByTokenHash(anyString())).thenReturn(Optional.of(revocada));

        assertThatThrownBy(() -> servicio.aceptar(datosDeCanje()))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("La invitación no es válida o ya venció");
    }

    @Test
    @DisplayName("Canjear dos veces no crea dos cuentas: la segunda vez ya está gastada")
    void canjearDosVecesNoCreaDosCuentas() {
        Invitacion gastada = vigente();
        gastada.setAceptadaEn(Instant.now());
        when(invitaciones.findByTokenHash(anyString())).thenReturn(Optional.of(gastada));

        assertThatThrownBy(() -> servicio.aceptar(datosDeCanje()))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("La invitación no es válida o ya venció");
        verify(usuarios, never()).save(any());
    }

    // ---------- Revocar ----------

    @Test
    @DisplayName("Una invitación ya canjeada no se revoca: la cuenta existe")
    void unaCanjeadaNoSeRevoca() {
        Invitacion gastada = vigente();
        gastada.setAceptadaEn(Instant.now());
        when(invitaciones.findByIdAndOrganizacionId(99L, ORG)).thenReturn(Optional.of(gastada));

        assertThatThrownBy(() -> servicio.revocar(ADMIN, 99L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Revocar una ajena responde «no existe», no «prohibido»")
    void revocarUnaAjenaEs404() {
        when(invitaciones.findByIdAndOrganizacionId(99L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.revocar(ADMIN, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
