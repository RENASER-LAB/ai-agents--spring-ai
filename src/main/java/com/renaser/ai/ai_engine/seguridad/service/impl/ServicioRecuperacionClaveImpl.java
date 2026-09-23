package com.renaser.ai.ai_engine.seguridad.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreo;
import com.renaser.ai.ai_engine.notificacion.service.DireccionDelCandidato;
import com.renaser.ai.ai_engine.notificacion.service.EnviadorCorreo;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.seguridad.dto.CabeEnBcrypt;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.service.ColaDeRecuperaciones;
import com.renaser.ai.ai_engine.seguridad.service.IntentosLogin;
import com.renaser.ai.ai_engine.seguridad.service.ServicioRecuperacionClave;
import com.renaser.ai.ai_engine.seguridad.service.SolicitudesPorIp;
import com.renaser.ai.ai_engine.usuario.entity.RecuperacionClave;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.RecuperacionClaveRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Ver {@link ServicioRecuperacionClave}. */
@Service
@Slf4j
public class ServicioRecuperacionClaveImpl implements ServicioRecuperacionClave {

    /** Cuántos minutos vale un enlace, si el parámetro no está (o no tiene sentido). */
    static final String PARAMETRO_MINUTOS = "minutos_vida_recuperacion";
    static final int MINUTOS_POR_DEFECTO = 60;
    /** Cuántos enlaces puede recibir una cuenta por hora. */
    static final String PARAMETRO_POR_HORA = "max_recuperaciones_por_hora";
    static final int POR_HORA_POR_DEFECTO = 3;
    /** Cuántas solicitudes se atienden por IP y hora. */
    static final String PARAMETRO_POR_IP = "max_recuperaciones_por_ip_hora";
    static final int POR_IP_POR_DEFECTO = 30;

    static final String PLANTILLA_CANDIDATO = "RECUPERAR_CLAVE_CANDIDATO";
    static final String PLANTILLA_EQUIPO = "RECUPERAR_CLAVE_EQUIPO";

    /** El mismo texto para inexistente, vencido, usado, reemplazado o de la otra puerta. */
    static final String ENLACE_NO_SIRVE = "Este enlace ya no sirve. Pide uno nuevo.";
    static final String IGUAL_A_LA_ANTERIOR = "Elige una contraseña distinta a la anterior";

    /** 32 bytes de azar, como la invitación y el enlace de acceso. */
    private static final int BYTES_DEL_TOKEN = 32;
    /** El largo máximo de una dirección de correo (RFC 5321). Lo demás no es un correo. */
    private static final int LARGO_MAXIMO_CORREO = 320;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FORMATO_VENCE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm", Locale.forLanguageTag("es-PE"));

    private final UsuarioRepository usuarios;
    private final RecuperacionClaveRepository recuperaciones;
    private final OrganizacionRepository organizaciones;
    private final DuenoDelInstrumento duenos;
    private final ServicioParametros parametros;
    private final ServicioCorreo correo;
    private final ServicioAuditoria auditoria;
    private final IntentosLogin intentos;
    private final PasswordEncoder codificador;
    private final SolicitudesPorIp solicitudesPorIp;
    private final ColaDeRecuperaciones cola;
    // Programática y no @Transactional: la parte que bloquea la cuenta tiene que acabar
    // ANTES de mandar el correo, y el correo sale desde la misma clase, en segundo plano.
    // Una llamada a un método propio no pasaría por el proxy de Spring.
    private final TransactionTemplate transacciones;

    private final SecureRandom azar = new SecureRandom();

    /** La dirección del portal del candidato, la del enlace del correo de candidato. */
    @Value("${renaser.portal.url:http://localhost:5174}")
    private String urlDelPortal;

    /** La del panel. Puede llevar el {@code /admin} o no: ver {@link #enlace}. */
    @Value("${renaser.panel.url:http://localhost:5173}")
    private String urlDelPanel;

    public ServicioRecuperacionClaveImpl(UsuarioRepository usuarios,
                                         RecuperacionClaveRepository recuperaciones,
                                         OrganizacionRepository organizaciones,
                                         DuenoDelInstrumento duenos,
                                         ServicioParametros parametros,
                                         ServicioCorreo correo,
                                         ServicioAuditoria auditoria,
                                         IntentosLogin intentos,
                                         PasswordEncoder codificador,
                                         SolicitudesPorIp solicitudesPorIp,
                                         ColaDeRecuperaciones cola,
                                         PlatformTransactionManager transacciones) {
        this.usuarios = usuarios;
        this.recuperaciones = recuperaciones;
        this.organizaciones = organizaciones;
        this.duenos = duenos;
        this.parametros = parametros;
        this.correo = correo;
        this.auditoria = auditoria;
        this.intentos = intentos;
        this.codificador = codificador;
        this.solicitudesPorIp = solicitudesPorIp;
        this.cola = cola;
        this.transacciones = new TransactionTemplate(transacciones);
    }

    // ---------------------------------------------------------------------------------
    // Pedir el enlace
    // ---------------------------------------------------------------------------------

    @Override
    public void solicitar(Publico publico, String correoPedido, String ip) {
        // Lo mismo para todos antes de decidir nada: leer el tope y contar la IP. Lo que
        // depende de la cuenta se hace después de responder.
        Long plataformaId = duenos.plataforma().getId();
        int maximoPorIp = parametros.entero(plataformaId, PARAMETRO_POR_IP, POR_IP_POR_DEFECTO);
        if (!solicitudesPorIp.admitir(ip, maximoPorIp)) {
            // Ni el correo ni la IP en el registro: los dos son datos de una persona.
            log.info("Solicitud de contraseña nueva descartada: esa IP pasó su tope por hora");
            return;
        }
        String correoLimpio = normalizar(correoPedido);
        if (correoLimpio == null) {
            return;
        }
        cola.encolar(() -> atender(publico, correoLimpio));
    }

    /**
     * El trabajo de verdad de una solicitud, ya fuera de la petición. Paquete y no privado
     * para que las pruebas lo llamen sin hilos de por medio.
     */
    void atender(Publico publico, String correoLimpio) {
        Organizacion plataforma = duenos.plataforma();
        for (Usuario cuenta : cuentasDe(publico, correoLimpio, plataforma.getId())) {
            // Una cuenta que falla no deja sin enlace a las demás del mismo correo.
            try {
                emitir(publico, cuenta, plataforma);
            } catch (RuntimeException e) {
                log.error("No se pudo preparar el enlace de contraseña nueva de la cuenta {}",
                        cuenta.getId(), e);
            }
        }
    }

    /**
     * Las cuentas de esa puerta que pueden recibir el enlace: activas y con un correo al
     * que se pueda escribir. Las de carga masiva tienen uno inventado que no llega a nadie.
     *
     * <p>En el portal, la cuenta de candidato de la plataforma; en el panel, TODAS las de
     * equipo con ese correo —el login del panel ya admite el mismo correo en dos empresas—,
     * y cada una recibe su propio enlace.
     */
    private List<Usuario> cuentasDe(Publico publico, String correoLimpio, Long plataformaId) {
        Stream<Usuario> deLaPuerta = switch (publico) {
            case CANDIDATO -> usuarios.buscarPorCorreo(plataformaId, correoLimpio).stream()
                    .filter(u -> !u.isEsEquipo());
            case EQUIPO -> usuarios.equipoPorCorreo(correoLimpio).stream();
        };
        return deLaPuerta
                .filter(Usuario::isEsActivo)
                .filter(u -> DireccionDelCandidato.esEntregable(u.getCorreo()))
                .toList();
    }

    private void emitir(Publico publico, Usuario cuenta, Organizacion plataforma) {
        String codigo = publico == Publico.CANDIDATO ? PLANTILLA_CANDIDATO : PLANTILLA_EQUIPO;
        PlantillaCorreo plantilla = correo
                .plantillaConRecambio(cuenta.getOrganizacionId(), plataforma.getId(), codigo)
                .orElse(null);
        if (plantilla == null) {
            // Ruidoso a propósito. La pantalla ya le dijo que revise su correo, y no se le
            // puede decir otra cosa sin revelar que la cuenta existe; lo mínimo es no crear
            // un enlace que nadie va a recibir y dejarlo escrito donde se mira.
            log.error("⚠️ NO SALIÓ el enlace de contraseña nueva de la cuenta {}: no hay plantilla "
                    + "activa «{}» ni en su organización ({}) ni en la plataforma. Hay que "
                    + "activarla.", cuenta.getId(), codigo, cuenta.getOrganizacionId());
            return;
        }

        int minutos = parametros.entero(plataforma.getId(), PARAMETRO_MINUTOS, MINUTOS_POR_DEFECTO);
        if (minutos < 1) {
            minutos = MINUTOS_POR_DEFECTO;
        }
        int maximoPorHora = parametros.entero(plataforma.getId(), PARAMETRO_POR_HORA,
                POR_HORA_POR_DEFECTO);
        String token = nuevoToken();
        Instant ahora = Instant.now();
        Instant vence = ahora.plus(minutos, ChronoUnit.MINUTES);

        RecuperacionClave creada = transacciones.execute(estado -> {
            // La fila de la cuenta bloqueada: dos solicitudes simultáneas del mismo correo
            // cuentan de una en una y el tope no se pasa por llegar a la vez.
            usuarios.bloquear(cuenta.getId());
            long enLaUltimaHora = recuperaciones.countByUsuarioIdAndCreadoEnAfter(
                    cuenta.getId(), ahora.minus(1, ChronoUnit.HOURS));
            if (enLaUltimaHora >= maximoPorHora) {
                log.info("Enlace de contraseña nueva no enviado: la cuenta {} llegó a su tope "
                        + "de {} por hora", cuenta.getId(), maximoPorHora);
                return null;
            }
            // Primero se apagan los vivos y después nace el nuevo, y en ese orden llega a la
            // base: el UPDATE es una consulta que se ejecuta al momento, y el INSERT va con
            // saveAndFlush. Al revés, el índice de «solo uno vivo» lo rechazaría.
            recuperaciones.invalidarLosVivos(cuenta.getId(), ahora);
            RecuperacionClave fila = recuperaciones.saveAndFlush(RecuperacionClave.builder()
                    .usuarioId(cuenta.getId())
                    .tokenHash(hashDe(token))
                    .creadoEn(ahora)
                    .venceEn(vence)
                    .build());
            // Sin el token ni su hash: la cuenta, el momento y cuándo vence.
            auditoria.registrarDelSistema(cuenta.getOrganizacionId(), "solicitar_recuperacion_clave",
                    "usuario", cuenta.getId(),
                    Map.of("recuperacionId", fila.getId(), "puerta", publico.name(),
                            "venceEn", vence.toString()));
            return fila;
        });
        if (creada == null) {
            return;
        }

        String nombreEmpresa = publico == Publico.CANDIDATO
                ? plataforma.getNombre()
                : organizaciones.findById(cuenta.getOrganizacionId())
                        .map(Organizacion::getNombre)
                        .orElse(plataforma.getNombre());
        EnviadorCorreo.Resultado entrega = correo.enviarCon(plantilla, cuenta.getId(),
                cuenta.getCorreo(), Map.of(
                        "enlace", enlace(publico, token),
                        "vence", FORMATO_VENCE.format(vence.atZone(LIMA)) + " (hora de Lima)",
                        "nombre_empresa", nombreEmpresa));

        // A propósito NO se registra el token, ni siquiera en depuración.
        log.info("Enlace de contraseña nueva {} para la cuenta {} · vence en {} min · entrega {}",
                creada.getId(), cuenta.getId(), minutos, entrega);
    }

    /**
     * El enlace del correo, a la pantalla de su puerta y a ninguna otra.
     *
     * <p>El del panel es {@code …/admin/restablecer} siempre. {@code renaser.panel.url} puede
     * llevar el {@code /admin} o no —el panel y el portal son la misma aplicación web, ver el
     * comentario de {@code rutas.ts} sobre {@code /invitacion}—, y aquí no se puede confiar
     * en que el frontend lo arregle como hace con la invitación: {@code /restablecer} a secas
     * ES la pantalla del candidato, así que un enlace de equipo sin el {@code /admin}
     * aterrizaría en la puerta equivocada.
     */
    private String enlace(Publico publico, String token) {
        String query = "/restablecer?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        if (publico == Publico.CANDIDATO) {
            return sinBarraFinal(urlDelPortal) + query;
        }
        String panel = sinBarraFinal(urlDelPanel);
        return (panel.endsWith("/admin") ? panel : panel + "/admin") + query;
    }

    // ---------------------------------------------------------------------------------
    // Elegir la contraseña nueva
    // ---------------------------------------------------------------------------------

    @Override
    @Transactional
    public void restablecer(Publico publico, String token, String contrasenaNueva) {
        // La puerta ya lo valida (@CabeEnBcrypt); esto es por si se llega sin pasar por ella.
        // Antes de todo: pasado el tope, BCrypt lanza su propio texto en inglés, tanto al
        // comparar con la actual como al cifrar, y ese texto acababa bajo el campo.
        if (!CabeEnBcrypt.Validador.cabe(contrasenaNueva)) {
            throw new IllegalArgumentException(CabeEnBcrypt.MENSAJE);
        }
        if (token == null || token.isBlank()) {
            throw new CredencialesInvalidasException(ENLACE_NO_SIRVE);
        }
        Instant ahora = Instant.now();
        RecuperacionClave recuperacion = recuperaciones.findByTokenHash(hashDe(token))
                .filter(r -> r.sirve(ahora))
                .orElseThrow(() -> new CredencialesInvalidasException(ENLACE_NO_SIRVE));
        // Una cuenta desactivada después de pedir el enlace (un borrado de datos, por
        // ejemplo), o un enlace de la otra puerta: el mismo texto.
        Usuario cuenta = usuarios.findById(recuperacion.getUsuarioId())
                .filter(Usuario::isEsActivo)
                .filter(u -> esDeLaPuerta(u, publico))
                .orElseThrow(() -> new CredencialesInvalidasException(ENLACE_NO_SIRVE));

        // Antes de gastar el enlace: repetir la contraseña de siempre es un error de quien
        // escribe, y no tiene por qué costarle pedir otro correo.
        if (cuenta.getContrasenaHash() != null
                && codificador.matches(contrasenaNueva, cuenta.getContrasenaHash())) {
            throw new IllegalArgumentException(IGUAL_A_LA_ANTERIOR);
        }

        // El gasto va en la base y es condicional: dos pestañas con el mismo enlace leen las
        // dos «sirve», y solo la primera consigue la fila.
        if (recuperaciones.gastar(recuperacion.getId(), ahora) == 0) {
            throw new CredencialesInvalidasException(ENLACE_NO_SIRVE);
        }

        cuenta.setContrasenaHash(codificador.encode(contrasenaNueva));
        usuarios.save(cuenta);
        // Quien estaba bloqueado por intentos fallidos acaba de demostrar que la cuenta es
        // suya: entra ya con la nueva, sin esperar los quince minutos.
        intentos.registrarExito(cuenta.getCorreo());
        auditoria.registrarDelSistema(cuenta.getOrganizacionId(), "restablecer_clave",
                "usuario", cuenta.getId(),
                Map.of("recuperacionId", recuperacion.getId(), "puerta", publico.name()));
        log.info("Contraseña cambiada con el enlace {} · cuenta {}", recuperacion.getId(),
                cuenta.getId());
    }

    /** Lo mismo que separa los dos logins: el candidato es de la plataforma; el equipo, no. */
    private boolean esDeLaPuerta(Usuario cuenta, Publico publico) {
        return switch (publico) {
            case CANDIDATO -> !cuenta.isEsEquipo()
                    && duenos.plataforma().getId().equals(cuenta.getOrganizacionId());
            case EQUIPO -> cuenta.isEsEquipo();
        };
    }

    // ---------------------------------------------------------------------------------

    /** El correo como lo guarda el sistema, o nulo si no puede ser un correo. */
    private static String normalizar(String correo) {
        if (correo == null) {
            return null;
        }
        String limpio = correo.trim().toLowerCase(Locale.ROOT);
        if (limpio.isEmpty() || limpio.length() > LARGO_MAXIMO_CORREO || limpio.indexOf('@') < 1) {
            return null;
        }
        return limpio;
    }

    private String nuevoToken() {
        byte[] crudo = new byte[BYTES_DEL_TOKEN];
        azar.nextBytes(crudo);
        // Sin relleno y en el alfabeto de URL: el token viaja dentro de un enlace.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);
    }

    private static String sinBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** SHA-256 en hexadecimal. Sin sal ni estirado: son 32 bytes de azar, no una contraseña. */
    private static String hashDe(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Esta JVM no tiene SHA-256", e);
        }
    }
}
