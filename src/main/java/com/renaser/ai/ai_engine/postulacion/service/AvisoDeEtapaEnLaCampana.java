package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * La otra mitad del correo de cada etapa: el aviso en la campana del portal (V70).
 *
 * <p>Hasta la V70 la campana solo sabía de la vacante (sueldo, cambios, eliminación) y de las
 * reseñas; ningún cambio de etapa llegaba a ella. Ahora <b>recibe lo mismo que el correo, ni más
 * ni menos</b>: si la transición manda correo, deja aviso; si se movió «sin avisar», nada. El
 * aviso dice lo mismo en corto y lleva al proceso al pulsarlo.
 *
 * <p>⚠️ <b>Se publica DESPUÉS de que la transición se confirme, y no dentro de ella.</b> Dos
 * razones, y la segunda es la que obliga:
 * <ol>
 *   <li>Un aviso de «tu prueba está disponible» de una transición que luego se deshace sería
 *       mentira, y el candidato lo leería.</li>
 *   <li>La campana escribe en su propia transacción ({@link ServicioAvisosPortal#publicar}), y
 *       al postular la postulación todavía no está confirmada: su aviso apuntaría a una fila
 *       que esa otra transacción no ve, y la clave foránea lo rechazaría. «Te toca tu
 *       evaluación» y «no continúa por un requisito» se perdían así, en silencio.</li>
 * </ol>
 *
 * <p>Y como el correo, <b>nunca tumba nada</b>: si no se puede escribir, se anota y se sigue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AvisoDeEtapaEnLaCampana {

    private final ServicioAvisosPortal avisos;

    /** Lo que dice el aviso: el título en una línea y el cuerpo en una o dos. */
    public record Texto(String titulo, String cuerpo) {
    }

    /**
     * El aviso de la campana para el correo que toca, o vacío si ese correo no tiene pareja.
     *
     * @param aviso   el código del correo que tocaba (no el texto propio de la vacante): es lo
     *                que dice qué pasó
     * @param nuevo   el estado al que entra
     * @param vacante el título de la vacante
     * @param plazo   lo que dice {@code {{plazo}}}, solo para la prueba
     */
    public static Optional<Texto> para(String aviso, EstadoPostulacion nuevo, String vacante,
                                       String plazo) {
        String deLaVacante = vacante == null || vacante.isBlank() ? "" : " · " + vacante;
        String paraLaVacante = vacante == null || vacante.isBlank() ? "" : " para " + vacante;
        return switch (aviso) {
            case AvisoPortal.PRUEBA_DISPONIBLE -> Optional.of(new Texto(
                    "Tu prueba del puesto está disponible" + deLaVacante,
                    (plazo == null || plazo.isBlank() ? "" : "Plazo: " + plazo + ". ")
                            + "El tiempo empieza a contar cuando la abras y confirmes."));
            case AvisoPortal.POSTULACION_AVANZA -> Optional.of(new Texto(
                    teToca(nuevo) + deLaVacante,
                    "Tu proceso pasó a «" + nombreDe(nuevo) + "». Entra para ver qué te toca."));
            case AvisoPortal.POSTULACION_NO_CONTINUA -> Optional.of(new Texto(
                    "Tu proceso" + paraLaVacante + " no continúa",
                    "Gracias por el tiempo que le dedicaste. En «Mis procesos» puedes ver hasta "
                            + "dónde llegaste."));
            case AvisoPortal.POSTULACION_CERRADA -> Optional.of(new Texto(
                    "Tu postulación" + paraLaVacante + " se cerró",
                    "El proceso terminó. Gracias por el tiempo que le dedicaste."));
            case AvisoPortal.RETIRO_CONFIRMADO -> Optional.of(new Texto(
                    "Confirmamos tu retiro" + (vacante == null || vacante.isBlank() ? ""
                            : " de " + vacante),
                    "Tu postulación quedó cerrada a tu pedido."));
            default -> Optional.empty();
        };
    }

    /**
     * Deja el aviso para cuando la transición se confirme (o ya, si no hay transacción).
     *
     * <p>No lanza nunca: un aviso perdido es malo, una transición deshecha por un aviso es peor.
     */
    public void publicar(Postulacion postulacion, String aviso, EstadoPostulacion nuevo,
                         String vacante, String plazo) {
        Optional<Texto> texto = para(aviso, nuevo, vacante, plazo);
        if (texto.isEmpty()) {
            return;
        }
        Long organizacionId = postulacion.getOrganizacionId();
        Long usuarioId = postulacion.getUsuarioId();
        Long postulacionId = postulacion.getId();
        Long vacanteId = postulacion.getVacanteId();
        Runnable dejarlo = () -> avisos.publicar(organizacionId, usuarioId, aviso,
                texto.get().titulo(), texto.get().cuerpo(), postulacionId, vacanteId);
        alConfirmar(dejarlo, aviso, postulacionId);
    }

    /**
     * Corre {@code accion} cuando la transacción en curso se confirme; sin transacción, ya.
     *
     * <p>Lo comparten los avisos de etapa y los recordatorios: los dos son noticias de algo que
     * acaba de quedar escrito, y ninguno puede adelantarse a que quede escrito de verdad.
     */
    public static void alConfirmar(Runnable accion, String queEs, Long postulacionId) {
        Runnable sinRomperNada = () -> {
            try {
                accion.run();
            } catch (RuntimeException e) {
                log.error("No se pudo dejar en la campana el aviso «{}» de la postulación {}: {}",
                        queEs, postulacionId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sinRomperNada.run();
                }
            });
        } else {
            sinRomperNada.run();
        }
    }

    /** «Te toca …» según la etapa en la que le toca algo. */
    private static String teToca(EstadoPostulacion nuevo) {
        String etapa = nuevo == null ? null : nuevo.getEtapaCodigo();
        if (etapa == null) {
            return "Te toca un paso nuevo";
        }
        return switch (etapa) {
            case "PERFIL_INTEGRAL" -> "Te toca tu evaluación";
            case "PRUEBA_PUESTO" -> "Te toca la prueba del puesto";
            case "SIMULACION" -> "Te toca la simulación";
            case "VALIDACION" -> "Te toca la validación";
            case "DECISION" -> "Te toca enviar una evidencia";
            default -> "Te toca un paso nuevo";
        };
    }

    private static String nombreDe(EstadoPostulacion nuevo) {
        return nuevo == null || nuevo.getNombre() == null ? "el siguiente paso" : nuevo.getNombre();
    }
}
