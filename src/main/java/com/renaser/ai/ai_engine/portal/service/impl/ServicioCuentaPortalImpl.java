package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.consentimiento.entity.Consentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.SolicitudBorrado;
import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.SolicitudBorradoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.CrearCuenta;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.Login;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.Sesion;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.TextoConsentimientoPublico;
import com.renaser.ai.ai_engine.portal.service.ServicioCuentaPortal;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.exception.DemasiadosIntentosException;
import com.renaser.ai.ai_engine.seguridad.service.IntentosLogin;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.entity.UsuarioRol;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La cuenta y el acceso: ver {@link ServicioCuentaPortal}. Todo lo de aquí cuelga de la
 * organización plataforma —la resuelve {@link DuenoDelInstrumento#plataforma()}—, porque
 * el candidato es de la plataforma y no de ninguna empresa.
 */
@Service
@RequiredArgsConstructor
public class ServicioCuentaPortalImpl implements ServicioCuentaPortal {

    private final DuenoDelInstrumento duenos;
    private final PersonaRepository personas;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;
    private final TextoConsentimientoRepository textosConsentimiento;
    private final ConsentimientoRepository consentimientos;
    private final SolicitudBorradoRepository solicitudesBorrado;
    private final ServicioCorreo correo;
    private final ServicioAuditoria auditoria;
    private final ServicioParametros parametros;
    private final ServicioToken tokens;
    private final IntentosLogin intentos;
    private final PasswordEncoder codificador;

    @Override
    public List<TextoConsentimientoPublico> textosDeConsentimiento() {
        Long org = duenos.plataforma().getId();
        return List.of("PROCESO", "FUTUROS_CONTACTOS").stream()
                .map(tipo -> textosConsentimiento
                        .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(org, tipo))
                .flatMap(Optional::stream)
                .map(t -> new TextoConsentimientoPublico(t.getTipo(), t.getVersion(), t.getTexto()))
                .toList();
    }

    @Override
    @Transactional
    public void crearCuenta(CrearCuenta datos, String ip, String userAgent) {
        // Sin aceptar el tratamiento de datos no hay cuenta: no es una casilla decorativa
        if (!Boolean.TRUE.equals(datos.aceptaProceso())) {
            throw new IllegalArgumentException("Hay que aceptar el tratamiento de datos personales para crear la cuenta");
        }
        Organizacion org = duenos.plataforma();
        usuarios.buscarPorCorreo(org.getId(), datos.correo()).ifPresent(u -> {
            throw new IllegalStateException("Ya existe una cuenta con ese correo");
        });

        Persona persona = personas.save(Persona.builder()
                .nombre(datos.nombre())
                .apellidos(datos.apellidos())
                .creadoEn(Instant.now())
                .build());

        Usuario usuario = usuarios.save(Usuario.builder()
                .organizacionId(org.getId())
                .personaId(persona.getId())
                .correo(datos.correo().trim().toLowerCase())
                .contrasenaHash(codificador.encode(datos.contrasena()))
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());

        roles.findByOrganizacionIdAndCodigo(org.getId(), "CANDIDATO").ifPresent(rol ->
                usuarioRoles.save(UsuarioRol.builder()
                        .usuarioId(usuario.getId()).rolId(rol.getId()).creadoEn(Instant.now())
                        .build()));

        // El consentimiento del proceso es obligatorio; el de futuros contactos, opcional.
        // De cada uno queda la versión exacta del texto, la IP y el navegador.
        registrarConsentimiento(persona, org.getId(), "PROCESO", datos, ip, userAgent);
        if (Boolean.TRUE.equals(datos.aceptaFuturosContactos())) {
            registrarConsentimiento(persona, org.getId(), "FUTUROS_CONTACTOS", datos, ip, userAgent);
        }

        correo.enviar(org.getId(), usuario.getId(), usuario.getCorreo(), "CUENTA_CREADA",
                Map.of("nombre", datos.nombre()));
    }

    private void registrarConsentimiento(Persona persona, Long orgId, String tipo,
                                         CrearCuenta datos, String ip, String userAgent) {
        TextoConsentimiento texto = textosConsentimiento
                .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(orgId, tipo)
                .orElseThrow(() -> new IllegalStateException("No hay texto de consentimiento publicado: " + tipo));
        consentimientos.save(Consentimiento.builder()
                .personaId(persona.getId())
                .textoConsentimientoId(texto.getId())
                .nombreRegistrado(datos.nombre() + " " + datos.apellidos())
                .aceptadoEn(Instant.now())
                .ip(ip)
                .userAgent(userAgent)
                .creadoEn(Instant.now())
                .build());
    }

    @Override
    public Sesion entrar(Login datos) {
        Organizacion org = duenos.plataforma();
        long esperaPendiente = intentos.segundosDeBloqueo(datos.correo());
        if (esperaPendiente > 0) {
            throw new DemasiadosIntentosException(esperaPendiente);
        }
        int maximo = parametros.entero(org.getId(), "intentos_login_max", 5);
        int minutosBloqueo = parametros.entero(org.getId(), "minutos_bloqueo_login", 15);

        Usuario usuario = usuarios.buscarPorCorreo(org.getId(), datos.correo())
                // La línea simétrica a la del panel: una cuenta de equipo no es un
                // candidato. Desde la V37 el equipo también tiene contraseña, y sin este
                // filtro la gente del panel de la plataforma abría el portal como
                // candidata — dos mundos con la misma llave.
                .filter(u -> !u.isEsEquipo())
                .filter(Usuario::isEsActivo)
                .filter(u -> u.getContrasenaHash() != null
                        && codificador.matches(datos.contrasena(), u.getContrasenaHash()))
                .orElse(null);

        if (usuario == null) {
            intentos.registrarFallo(datos.correo(), maximo, minutosBloqueo);
            // El mismo mensaje exista o no el correo: no se regala información
            throw new CredencialesInvalidasException("Correo o contraseña incorrectos");
        }

        intentos.registrarExito(datos.correo());
        usuario.setUltimoAccesoEn(Instant.now());
        usuarios.save(usuario);
        return new Sesion(tokens.emitir(usuario.getId(), org.getId(), "CANDIDATO"), usuario.getId());
    }

    @Override
    @Transactional
    public void retirarConsentimientoFuturos(ContextoUsuario quien) {
        Consentimiento vigente = consentimientos.vigenteDeTipo(quien.personaId(), "FUTUROS_CONTACTOS")
                .orElseThrow(() -> new IllegalStateException("No tienes un consentimiento de futuros contactos vigente"));
        vigente.setRetiradoEn(Instant.now());
        consentimientos.save(vigente);
        auditoria.registrar(quien.organizacionId(), quien, "retiro_consentimiento_futuros",
                "consentimiento", vigente.getId(), null, Map.of("retirado", true), null);
    }

    @Override
    @Transactional
    public void pedirBorrado(ContextoUsuario quien, String motivo) {
        if (solicitudesBorrado.existsByPersonaIdAndEjecutadoEnIsNull(quien.personaId())) {
            throw new IllegalStateException("Ya tienes una solicitud de borrado pendiente");
        }
        SolicitudBorrado solicitud = solicitudesBorrado.save(SolicitudBorrado.builder()
                .personaId(quien.personaId())
                .motivo(motivo)
                .solicitadoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "solicitud_borrado",
                "solicitud_borrado", solicitud.getId(), null, Map.of("solicitado", true), motivo);
    }
}
