package com.renaser.ai.ai_engine.seguridad.service.impl;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.seguridad.config.PropiedadesSeguridad;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Login;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.exception.DemasiadosIntentosException;
import com.renaser.ai.ai_engine.seguridad.service.IntentosLogin;
import com.renaser.ai.ai_engine.seguridad.service.ProveedorIdentidadEquipo;
import com.renaser.ai.ai_engine.seguridad.service.ServicioAccesoEquipo;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.entity.UsuarioRol;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Ver {@link ServicioAccesoEquipo}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioAccesoEquipoImpl implements ServicioAccesoEquipo {

    private final PropiedadesSeguridad propiedades;
    private final ProveedorIdentidadEquipo proveedor;
    private final ServicioToken tokens;
    private final OrganizacionRepository organizaciones;
    private final PersonaRepository personas;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;
    private final IntentosLogin intentos;
    private final ServicioParametros parametros;
    private final PasswordEncoder codificador;

    @Override
    @Transactional
    public Sesion entrar(Login datos) {
        long esperaPendiente = intentos.segundosDeBloqueo(datos.correo());
        if (esperaPendiente > 0) {
            throw new DemasiadosIntentosException(esperaPendiente);
        }
        // Los umbrales de bloqueo salen de la plataforma: antes de autenticar no se sabe
        // de qué empresa es quien escribe, y estos números no son de los que cada empresa
        // personaliza — son la defensa del login, que es uno solo.
        Long plataformaId = plataforma().getId();
        int maximo = parametros.entero(plataformaId, "intentos_login_max", 5);
        int minutosBloqueo = parametros.entero(plataformaId, "minutos_bloqueo_login", 15);

        // El correo puede existir en varias organizaciones (candidato en la plataforma y
        // reclutador en una empresa, por ejemplo). Solo cuentan las cuentas de EQUIPO:
        // esa es la línea que impide que un candidato entre al panel con su contraseña.
        List<Usuario> candidatas = usuarios.equipoPorCorreo(datos.correo());
        if (candidatas.isEmpty()) {
            // Comparación señuelo: sin ella, un correo sin cuenta contesta al instante
            // (ningún BCrypt que comprobar) y uno con cuenta tarda lo que tarda BCrypt.
            // El mensaje no distingue los dos casos; el reloj tampoco debería.
            codificador.matches(datos.contrasena(), hashSenuelo());
        }
        Usuario usuario = candidatas.stream()
                .filter(Usuario::isEsActivo)
                .filter(u -> u.getContrasenaHash() != null
                        && codificador.matches(datos.contrasena(), u.getContrasenaHash()))
                .findFirst()
                .orElse(null);

        if (usuario == null) {
            intentos.registrarFallo(datos.correo(), maximo, minutosBloqueo);
            // El mismo mensaje exista o no la cuenta, sea candidato o no sea nadie
            throw new CredencialesInvalidasException("Correo o contraseña incorrectos");
        }

        intentos.registrarExito(datos.correo());
        usuario.setUltimoAccesoEn(Instant.now());
        usuarios.save(usuario);
        return new Sesion(tokens.emitir(usuario.getId(), usuario.getOrganizacionId(), "EQUIPO"),
                usuario.getId());
    }

    @Override
    @Transactional
    public Sesion devLogin(String usuarioRenaserOsId) {
        if (!propiedades.isDevLoginActivo()) {
            throw new IllegalStateException("El login de desarrollo está apagado: el panel "
                    + "entra con correo y contraseña");
        }
        Usuario usuario = proveedor.autenticarDesarrollo(null, usuarioRenaserOsId)
                .orElseGet(() -> arrancarPrimerUsuario(usuarioRenaserOsId));
        return new Sesion(tokens.emitir(usuario.getId(), usuario.getOrganizacionId(), "EQUIPO"),
                usuario.getId());
    }

    // Bootstrap: en una base recién migrada no hay nadie del equipo y sin alguien del
    // equipo no se pueden crear usuarios. El primer id que entre por el dev-login se
    // crea con los roles operativos completos. Solo pasa una vez y solo en desarrollo.
    private Usuario arrancarPrimerUsuario(String usuarioRenaserOsId) {
        Organizacion org = plataforma();
        if (!usuarios.findByOrganizacionIdAndEsEquipoTrue(org.getId()).isEmpty()) {
            // Ya hay equipo: un id desconocido no entra, se crea desde administración
            throw new IllegalArgumentException("Ese id de RENASER OS no está registrado en el sistema");
        }
        log.warn("Bootstrap de desarrollo: creando el primer usuario del equipo ({})", usuarioRenaserOsId);
        Persona persona = personas.save(Persona.builder()
                .nombre("Equipo").apellidos("Desarrollo").creadoEn(Instant.now())
                .build());
        Usuario usuario = usuarios.save(Usuario.builder()
                .organizacionId(org.getId())
                .personaId(persona.getId())
                .usuarioRenaserOsId(usuarioRenaserOsId)
                .esEquipo(true)
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());
        for (String codigo : List.of("TALENTO", "DIRECCION", "ADMINISTRADOR")) {
            roles.findByOrganizacionIdAndCodigo(org.getId(), codigo).ifPresent(rol ->
                    usuarioRoles.save(UsuarioRol.builder()
                            .usuarioId(usuario.getId()).rolId(rol.getId()).creadoEn(Instant.now())
                            .build()));
        }
        return usuario;
    }

    private Organizacion plataforma() {
        return organizaciones.findByEsPlataformaTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "Ninguna organización está marcada como plataforma"));
    }

    /**
     * Un hash de nada, calculado una vez con el codificador de verdad para que la
     * comparación señuelo cueste lo mismo que una real. Perezoso y no en el constructor:
     * codificar en el arranque retrasaría a quien no va a fallar ningún login.
     */
    private String hashSenuelo() {
        String hash = senuelo;
        if (hash == null) {
            hash = codificador.encode("senuelo-que-no-abre-ninguna-puerta");
            senuelo = hash;
        }
        return hash;
    }

    private volatile String senuelo;
}
