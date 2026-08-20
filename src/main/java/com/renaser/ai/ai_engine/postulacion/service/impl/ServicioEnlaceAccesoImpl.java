package com.renaser.ai.ai_engine.postulacion.service.impl;

import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.EnlaceAcceso;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EnlaceAccesoRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.ServicioEnlaceAcceso;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioEnlaceAccesoImpl implements ServicioEnlaceAcceso {

    /** Cuántos días vale un enlace, si el parámetro no está. */
    static final String PARAMETRO_DIAS = "dias_enlace_acceso";
    static final int DIAS_POR_DEFECTO = 30;

    /**
     * 32 bytes de azar. Es el mismo tamaño que usa cualquier token de sesión serio y hace
     * que adivinarlo por fuerza bruta no sea una estrategia: son 2^256 posibilidades.
     */
    private static final int BYTES_DEL_TOKEN = 32;

    private final EnlaceAccesoRepository enlaces;
    private final PostulacionRepository postulaciones;
    private final ServicioToken tokens;
    private final ServicioParametros parametros;

    private final SecureRandom azar = new SecureRandom();

    /**
     * La direccion del portal del candidato. En una maquina de desarrollo es el Vite local;
     * publicado es el dominio de Vercel. Se configura, no se adivina.
     */
    @org.springframework.beans.factory.annotation.Value("${renaser.portal.url:http://localhost:5174}")
    private String urlDelPortal;

    @Override
    @Transactional
    public String crear(Long postulacionId) {
        return nuevoEnlace(postulacionId).token();
    }

    @Override
    @Transactional
    public EnlaceGenerado generarEnlace(Long postulacionId) {
        Recien recien = nuevoEnlace(postulacionId);

        String base = urlDelPortal.endsWith("/")
                ? urlDelPortal.substring(0, urlDelPortal.length() - 1)
                : urlDelPortal;

        // El token va en la query y no en el camino porque es lo que el portal sabe leer sin
        // que su enrutador tenga que entenderlo como parte de la direccion.
        String url = base + "/acceso?token="
                + URLEncoder.encode(recien.token(), StandardCharsets.UTF_8);

        return new EnlaceGenerado(url, recien.fila().getVenceEn());
    }

    /** El enlace recien creado y su token en claro, que solo existe aqui. */
    private record Recien(EnlaceAcceso fila, String token) {}

    /**
     * Lo que hacen en comun los dos verbos publicos.
     *
     * <p>Es privado y no {@code @Transactional} a proposito. Si {@code generarEnlace} llamara
     * a {@code crear} —los dos publicos y los dos transaccionales—, esa llamada saldria por
     * {@code this} y <b>no pasaria por el proxy de Spring</b>: la anotacion del segundo no
     * haria nada. Funciona igual porque el primero ya abrio la transaccion, pero es la clase
     * de detalle que deja de ser cierto en cuanto alguien cambia una propagacion.
     */
    private Recien nuevoEnlace(Long postulacionId) {
        Postulacion postulacion = postulaciones.findById(postulacionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la postulación " + postulacionId));

        int dias = parametros.entero(postulacion.getOrganizacionId(), PARAMETRO_DIAS, DIAS_POR_DEFECTO);

        byte[] crudo = new byte[BYTES_DEL_TOKEN];
        azar.nextBytes(crudo);
        // Sin relleno y en el alfabeto de URL: el token viaja dentro de un enlace y un «+»
        // o un «=» ahí se rompe al copiarlo o al pasar por un cliente de correo.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);

        EnlaceAcceso fila = enlaces.save(EnlaceAcceso.builder()
                .postulacionId(postulacionId)
                .tokenHash(hashDe(token))
                .venceEn(Instant.now().plus(dias, ChronoUnit.DAYS))
                .usos(0)
                .creadoEn(Instant.now())
                .build());

        // A propósito NO se registra el token, ni siquiera en depuración: el log se copia,
        // se manda por chat y se sube a un gestor de registros. Solo el identificador.
        log.info("Enlace de acceso creado para la postulación {} · vence en {} días",
                postulacionId, dias);

        return new Recien(fila, token);
    }

    @Override
    @Transactional
    public Sesion canjear(String token) {
        if (token == null || token.isBlank()) {
            throw new CredencialesInvalidasException("El enlace no es válido o ya venció");
        }

        Instant ahora = Instant.now();
        EnlaceAcceso enlace = enlaces.findByTokenHash(hashDe(token))
                .filter(e -> e.estaVigente(ahora))
                .orElseThrow(() -> new CredencialesInvalidasException(
                        "El enlace no es válido o ya venció"));

        Postulacion postulacion = postulaciones.findById(enlace.getPostulacionId())
                .orElseThrow(() -> new CredencialesInvalidasException(
                        "El enlace no es válido o ya venció"));

        if (enlace.getPrimerUsoEn() == null) {
            enlace.setPrimerUsoEn(ahora);
        }
        enlace.setUltimoUsoEn(ahora);
        enlace.setUsos(enlace.getUsos() + 1);
        enlaces.save(enlace);

        log.info("Enlace de acceso canjeado · postulación {} · uso número {}",
                postulacion.getId(), enlace.getUsos());

        return new Sesion(
                tokens.emitir(postulacion.getUsuarioId(), postulacion.getOrganizacionId(), "CANDIDATO"),
                postulacion.getUsuarioId());
    }

    /**
     * SHA-256 en hexadecimal.
     *
     * <p>Sin sal y sin estirar, al revés que una contraseña, y es correcto: el token son 32
     * bytes de azar, no algo que alguien pueda haber elegido también en otro sitio. Un
     * diccionario no sirve contra eso, y estirarlo solo haría lenta cada entrada al portal.
     */
    private String hashDe(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM: si falta, el problema no es este método.
            throw new IllegalStateException("Esta JVM no tiene SHA-256", e);
        }
    }
}
