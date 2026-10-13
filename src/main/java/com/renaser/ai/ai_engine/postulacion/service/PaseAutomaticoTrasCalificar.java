package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.service.NotaDelPerfilRecalculada;
import com.renaser.ai.ai_engine.perfilintegral.service.RetratoTerminado;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escuchar que la nota del Perfil Integral terminó —o se recalculó—, para dar el pase
 * automático.
 *
 * <p>No hace nada por su cuenta: solo llama a {@link PaseAutomatico} y se traga lo que
 * salga mal. Es fino a propósito — separar el oyente del trabajo es lo que permite que el
 * trabajo abra su propia transacción, porque un método transaccional al que su propia
 * clase llama directamente se salta el proxy de Spring y no abre nada.
 *
 * <p>⚠️ <b>A esta clase no la inyecta nadie, y no puede hacerlo nadie.</b> Es lo que rompe
 * el círculo de dependencias que impide llamar al pase directamente desde la calificación.
 * Ver {@link RetratoTerminado}, donde está contado entero.
 *
 * <p>Escucha los dos eventos del proyecto, y los dos acaban en {@link PaseAutomatico}: el
 * retrato terminado y la nota recalculada mientras la persona espera (V72).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaseAutomaticoTrasCalificar {

    private final PaseAutomatico pase;

    /**
     * Corre justo después de que la calificación quede guardada.
     *
     * <p><b>Esperar al commit no es un detalle de estilo.</b> Si corriera dentro de la
     * transacción de la calificación, un fallo aquí desharía las notas que el modelo acaba
     * de escribir — y esas se han pagado. Después del commit, lo calificado está a salvo
     * pase lo que pase.
     *
     * <p><b>Y por eso mismo se traga cualquier excepción.</b> Esto corre en el hilo del
     * consumidor de la cola: si dejara salir un error, el trabajo se daría por fallido, se
     * reintentaría, y se volvería a pagar el modelo por un retrato que ya estaba perfecto.
     * Peor: mientras da vueltas, el panel enseñaría «falló» sobre un candidato bien
     * calificado. Un pase que no se pudo dar deja la postulación esperando a una persona,
     * que es exactamente donde estaba antes de que esto existiera.
     */
    @TransactionalEventListener
    public void alTerminarElRetrato(RetratoTerminado evento) {
        try {
            pase.avanzarSiToca(evento.postulacionId());
        } catch (RuntimeException e) {
            log.error("PASE_AUTOMATICO: la postulación {} se queda esperando a una persona. "
                    + "Lo calificado está guardado: {}", evento.postulacionId(), e.getMessage(), e);
        }
    }

    /**
     * Corre justo después de que un recálculo de la nota quede guardado (V72).
     *
     * <p>Por lo mismo que {@link #alTerminarElRetrato}: después del commit, para que un fallo
     * aquí no deshaga el ajuste que una persona acaba de hacer, y tragándose cualquier error,
     * porque el ajuste ya está bien hecho y guardado. Si el pase no se puede dar, la
     * postulación sigue esperando a una persona.
     */
    @TransactionalEventListener
    public void alRecalcularLaNota(NotaDelPerfilRecalculada evento) {
        try {
            pase.avanzarTrasRecalcular(evento.postulacionId());
        } catch (RuntimeException e) {
            log.error("PASE_AUTOMATICO: la postulación {} se queda esperando a una persona tras "
                    + "recalcular su nota. Lo recalculado está guardado: {}",
                    evento.postulacionId(), e.getMessage(), e);
        }
    }
}
