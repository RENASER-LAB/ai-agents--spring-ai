package com.renaser.ai.ai_engine.seguridad.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.AceptarInvitacion;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.InvitacionCreada;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.InvitacionPanel;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.service.ServicioInvitaciones;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import com.renaser.ai.ai_engine.usuario.entity.Invitacion;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.entity.UsuarioRol;
import com.renaser.ai.ai_engine.usuario.repository.InvitacionRepository;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Ver {@link ServicioInvitaciones}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioInvitacionesImpl implements ServicioInvitaciones {

    /** Cuántos días vale una invitación, si el parámetro no está. */
    static final String PARAMETRO_DIAS = "dias_invitacion";
    static final int DIAS_POR_DEFECTO = 7;

    /** El mismo error para inexistente, vencida, revocada o ya canjeada: no se regala nada. */
    static final String INVITACION_NO_VALE = "La invitación no es válida o ya venció";

    /** 32 bytes de azar, como el enlace de acceso del candidato: adivinar no es estrategia. */
    private static final int BYTES_DEL_TOKEN = 32;

    private final InvitacionRepository invitaciones;
    private final OrganizacionRepository organizaciones;
    private final PersonaRepository personas;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;
    private final PasswordEncoder codificador;
    private final ServicioToken tokens;
    private final ServicioParametros parametros;
    private final ServicioCorreo correo;
    private final ServicioAuditoria auditoria;

    private final SecureRandom azar = new SecureRandom();

    /**
     * La dirección del panel de empresas, la que se pega en el correo de invitación. No es
     * la del portal del candidato ({@code renaser.portal.url}): son dos frontends distintos.
     */
    @Value("${renaser.panel.url:http://localhost:5173}")
    private String urlDelPanel;

    @Override
    @Transactional
    public InvitacionCreada crear(ContextoUsuario quien, String correo, List<String> roles) {
        return crearParaOrganizacion(quien, quien.organizacionId(), correo, roles);
    }

    @Override
    @Transactional
    public InvitacionCreada crearParaOrganizacion(ContextoUsuario quien, Long organizacionId,
                                                  String correoDestino, List<String> codigosRoles) {
        Organizacion organizacion = organizaciones.findById(organizacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Organización", "id", organizacionId));

        // Los roles se comprueban al invitar y no al canjear: un código equivocado tiene
        // que reventarle a quien invita, no al invitado días después con el enlace en mano.
        for (String codigo : codigosRoles) {
            roles.findByOrganizacionIdAndCodigo(organizacionId, codigo)
                    .orElseThrow(() -> new ResourceNotFoundException("Rol", "código", codigo));
        }
        String correoLimpio = correoDestino.trim().toLowerCase();
        usuarios.buscarPorCorreo(organizacionId, correoLimpio).ifPresent(u -> {
            throw new IllegalStateException("Ya existe una cuenta con ese correo");
        });

        int dias = parametros.entero(organizacionId, PARAMETRO_DIAS, DIAS_POR_DEFECTO);
        byte[] crudo = new byte[BYTES_DEL_TOKEN];
        azar.nextBytes(crudo);
        // Sin relleno y en el alfabeto de URL: el token viaja dentro de un enlace.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);

        Invitacion invitacion = invitaciones.save(Invitacion.builder()
                .organizacionId(organizacionId)
                .correo(correoLimpio)
                .roles(String.join(",", codigosRoles))
                .tokenHash(hashDe(token))
                .venceEn(Instant.now().plus(dias, ChronoUnit.DAYS))
                .creadaPorUsuarioId(quien.usuarioId())
                .creadoEn(Instant.now())
                .build());

        String url = baseDelPanel() + "/invitacion?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);

        // El correo sale con la plantilla de la organización invitada, no la de quien
        // invita: un correo firmado por Renaser a la gente de otra empresa es un error.
        // usuario_id va vacío porque el invitado todavía no tiene cuenta.
        String vence = DateTimeFormatter.ISO_LOCAL_DATE
                .format(invitacion.getVenceEn().atZone(ZoneId.of("America/Lima")).toLocalDate());
        correo.enviar(organizacionId, null, correoLimpio, "INVITACION_EQUIPO",
                Map.of("nombre_empresa", organizacion.getNombre(), "enlace", url, "vence", vence));

        // A propósito NO se registra el token, ni siquiera en depuración.
        log.info("Invitación creada para la organización {} · vence en {} días", organizacionId, dias);
        auditoria.registrar(organizacionId, quien, "crear_invitacion",
                "invitacion", invitacion.getId(), null,
                Map.of("roles", invitacion.getRoles()), null);

        return new InvitacionCreada(invitacion.getId(), url, invitacion.getVenceEn());
    }

    @Override
    @Transactional
    public Sesion aceptar(AceptarInvitacion datos) {
        if (datos.token() == null || datos.token().isBlank()) {
            throw new CredencialesInvalidasException(INVITACION_NO_VALE);
        }
        Instant ahora = Instant.now();
        Invitacion invitacion = invitaciones.findByTokenHash(hashDe(datos.token()))
                .filter(i -> i.estaVigente(ahora))
                .orElseThrow(() -> new CredencialesInvalidasException(INVITACION_NO_VALE));

        // El gasto va ANTES de crear nada, y en la base: dos canjes simultáneos leen los
        // dos la misma invitación vigente, pero el UPDATE condicional solo le da 1 al
        // primero — el segundo recibe el mismo error genérico que una invitación gastada.
        // Si algo de lo que sigue falla, la transacción entera se deshace y el gasto
        // también: la invitación no se pierde por un canje que no terminó.
        if (invitaciones.gastar(invitacion.getId(), ahora) == 0) {
            throw new CredencialesInvalidasException(INVITACION_NO_VALE);
        }

        // La carrera improbable: alguien creó esa cuenta entre la invitación y el canje.
        usuarios.buscarPorCorreo(invitacion.getOrganizacionId(), invitacion.getCorreo())
                .ifPresent(u -> {
                    throw new IllegalStateException("Ya existe una cuenta con ese correo");
                });

        Persona persona = personas.save(Persona.builder()
                .nombre(datos.nombre())
                .apellidos(datos.apellidos())
                .creadoEn(ahora)
                .build());
        Usuario usuario = usuarios.save(Usuario.builder()
                .organizacionId(invitacion.getOrganizacionId())
                .personaId(persona.getId())
                .correo(invitacion.getCorreo())
                .contrasenaHash(codificador.encode(datos.contrasena()))
                .esEquipo(true)
                .esActivo(true)
                .creadoEn(ahora)
                .build());
        for (String codigo : invitacion.getRoles().split(",")) {
            roles.findByOrganizacionIdAndCodigo(invitacion.getOrganizacionId(), codigo.trim())
                    .ifPresent(rol -> usuarioRoles.save(UsuarioRol.builder()
                            .usuarioId(usuario.getId())
                            .rolId(rol.getId())
                            .asignadoPorUsuarioId(invitacion.getCreadaPorUsuarioId())
                            .creadoEn(ahora)
                            .build()));
        }

        log.info("Invitación {} canjeada · nace el usuario {} en la organización {}",
                invitacion.getId(), usuario.getId(), invitacion.getOrganizacionId());
        return new Sesion(tokens.emitir(usuario.getId(), usuario.getOrganizacionId(), "EQUIPO"),
                usuario.getId());
    }

    @Override
    public List<InvitacionPanel> listar(ContextoUsuario quien) {
        return invitaciones.findByOrganizacionIdOrderByCreadoEnDesc(quien.organizacionId()).stream()
                .map(i -> new InvitacionPanel(i.getId(), i.getCorreo(),
                        Arrays.stream(i.getRoles().split(",")).map(String::trim).toList(),
                        i.getVenceEn(), i.getAceptadaEn(), i.getRevocadaEn()))
                .toList();
    }

    @Override
    @Transactional
    public void revocar(ContextoUsuario quien, Long invitacionId) {
        Invitacion invitacion = invitaciones.findByIdAndOrganizacionId(invitacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitación", "id", invitacionId));
        if (invitacion.getAceptadaEn() != null) {
            throw new IllegalStateException("Esta invitación ya se canjeó: la cuenta existe y se "
                    + "administra desde usuarios");
        }
        invitacion.setRevocadaEn(Instant.now());
        invitaciones.save(invitacion);
        auditoria.registrar(quien.organizacionId(), quien, "revocar_invitacion",
                "invitacion", invitacionId, null, Map.of("revocada", true), null);
    }

    private String baseDelPanel() {
        return urlDelPanel.endsWith("/")
                ? urlDelPanel.substring(0, urlDelPanel.length() - 1)
                : urlDelPanel;
    }

    /** SHA-256 en hexadecimal. Sin sal ni estirado, como el enlace de acceso: 32 bytes de azar. */
    private String hashDe(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Esta JVM no tiene SHA-256", e);
        }
    }
}
