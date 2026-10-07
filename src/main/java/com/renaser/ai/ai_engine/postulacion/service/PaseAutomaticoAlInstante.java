package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.service.TurnoDelPerfilCumplido;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escuchar que el candidato hizo lo suyo, para abrirle la prueba al instante (V70).
 *
 * <p>Gemelo de {@link PaseAutomaticoTrasCalificar}: fino a propósito, solo llama a
 * {@link PaseAutomatico} y se traga lo que salga mal.
 *
 * <p>⚠️ <b>A esta clase no la inyecta nadie, y no puede hacerlo nadie.</b> Es lo que rompe el
 * círculo entre la entrega del banco y el pase. Ver {@link TurnoDelPerfilCumplido}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaseAutomaticoAlInstante {

    private final PaseAutomatico pase;

    /**
     * Corre justo después de que la entrega (o la postulación) quede guardada.
     *
     * <p><b>Después del commit, y no dentro.</b> Lo que el candidato entregó tiene que quedar
     * guardado pase lo que pase con el pase: si crear su prueba fallara dentro de la misma
     * transacción, se llevaría por delante sus cincuenta respuestas.
     *
     * <p><b>Y se traga cualquier excepción</b>: un pase que no se pudo dar deja la postulación
     * donde la dejaba la entrega, esperando a la IA y al pase de siempre, que es exactamente
     * donde estaba antes de que esto existiera. La respuesta al candidato no puede ser un error
     * por algo que él ya hizo bien.
     */
    @TransactionalEventListener
    public void alCumplirElPerfil(TurnoDelPerfilCumplido evento) {
        try {
            pase.alInstante(evento.postulacionId(), evento.momento().motivo());
        } catch (RuntimeException e) {
            log.error("PASE_AUTOMATICO: la postulación {} no pasó al instante; sigue esperando a "
                    + "la calificación: {}", evento.postulacionId(), e.getMessage(), e);
        }
    }
}
