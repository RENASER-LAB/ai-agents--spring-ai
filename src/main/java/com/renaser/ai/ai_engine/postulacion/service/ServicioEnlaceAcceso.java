package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;

/**
 * Los enlaces que dejan entrar al portal sin contraseña.
 *
 * <p>Resuelve un agujero concreto: los candidatos que llegaron por una carga masiva de
 * currículums tienen cuenta, pero con un correo inventado y una contraseña que nadie les
 * dijo. No pueden entrar por la puerta normal y no hay pantalla de recuperar contraseña.
 * Sin esto, todo lo que el portal ya sabe hacer —responder la evaluación, ver la prueba,
 * subir el entregable— les queda fuera de alcance.
 *
 * <p>Son dos verbos y viven separados a propósito: <b>crear</b> lo hace el equipo desde el
 * panel al invitar, y <b>canjear</b> lo hace el candidato sin estar autenticado, que es
 * justamente el punto.
 */
public interface ServicioEnlaceAcceso {

    /**
     * Crea un enlace para una postulación y devuelve <b>el token en claro</b>.
     *
     * <p>Es la única vez que ese token existe fuera del correo: en la base solo queda su
     * hash. Quien llame a esto tiene que usarlo en el acto —meterlo en el mensaje que sale—
     * porque no hay forma de recuperarlo después.
     *
     * <p>No invalida los enlaces anteriores de la misma postulación. Reenviar una invitación
     * es normal y el candidato puede tener abierto el correo viejo; dejar vivos los dos
     * hasta que venzan evita el caso de alguien que hace clic en el primero y se encuentra
     * la puerta cerrada sin entender por qué.
     */
    String crear(Long postulacionId);

    /**
     * Lo mismo que {@link #crear}, pero devuelto como el enlace completo que se le pega al
     * candidato en el correo, con su fecha de vencimiento.
     *
     * <p>La direccion del portal no se arma en el correo ni en el frontend: sale del
     * parametro {@code renaser.portal.url}, porque no es la misma en la maquina de un
     * desarrollador que en el portal publicado, y una plantilla de correo con una direccion
     * escrita a mano acaba mandando a la gente a localhost.
     */
    EnlaceGenerado generarEnlace(Long postulacionId);

    /** El enlace listo para pegar, y hasta cuando vale. */
    record EnlaceGenerado(String url, java.time.Instant venceEn) {}

    /**
     * Canjea el token por una sesión de candidato.
     *
     * @throws com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException
     *         si el token no existe, ya venció o fue revocado. <b>El mismo error en los tres
     *         casos</b>: distinguirlos le diría a quien prueba tokens al azar cuáles
     *         existieron alguna vez.
     */
    Sesion canjear(String token);
}
