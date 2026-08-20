package com.renaser.ai.ai_engine.notificacion.service;

/**
 * El transporte del correo, separado del registro.
 *
 * <p>El registro en {@code correo_enviado} es obligatorio y ocurre siempre: si un candidato
 * reclama meses despues, se lee lo que se le dijo. El transporte es lo que cambia segun donde
 * corra la aplicacion, y por eso vive detras de esta interfaz.
 *
 * <p>Hay dos implementaciones y las elige el parametro {@code renaser.correo.transporte}:
 * {@code log} —la de siempre, que no manda nada— y {@code smtp}, que manda de verdad. El
 * valor por defecto es {@code log}, asi que quien no configure nada no se entera de este
 * cambio: ni los tests, ni el entorno de otro desarrollador.
 */
public interface EnviadorCorreo {

    /**
     * Entrega el correo al servidor de salida.
     *
     * <p><b>No lanza.</b> Un aviso que no sale no puede tumbar la transicion que lo provoco:
     * el candidato ya avanzo de etapa y esa verdad no se deshace porque el servidor de correo
     * este caido. Lo que hace es devolver como fue, para que quede escrito en el registro en
     * vez de darse por bueno.
     */
    Resultado enviar(String correoDestino, String asunto, String cuerpo);

    /** Como acabo el intento. Se guarda en {@code correo_enviado.estado_entrega}. */
    enum Resultado {

        /** El servidor de salida lo acepto. No garantiza que llegue a la bandeja. */
        ENVIADO,

        /** Se intento y fallo. El motivo queda en el log con su excepcion. */
        FALLIDO,

        /** Ni se intento: la aplicacion corre con el transporte de log. */
        NO_ENVIADO
    }
}
