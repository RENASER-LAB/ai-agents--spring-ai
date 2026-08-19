package com.renaser.ai.ai_engine.ai.messaging;

import com.renaser.ai.ai_engine.ai.config.RabbitMQConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Cuándo sale el mensaje: nunca antes de que la fila esté guardada.
 *
 * <p><b>Era una carrera de verdad.</b> Quien encola casi siempre está dentro de una
 * transacción, así que la fila de {@code trabajo_ia} todavía no existe para nadie más. Del
 * otro lado hay ocho consumidores esperando: uno recogía el mensaje en milisegundos, iba a
 * la base, no encontraba nada pendiente y lo soltaba. El trabajo se quedaba en PENDIENTE
 * para siempre, sin error y sin nadie mirándolo, hasta que el vigilante de atascados lo
 * reencolaba quince minutos después.
 *
 * <p>Con un solo consumidor la carrera se ganaba por casualidad, así que el fallo no se veía
 * en desarrollo. Este test es la única forma de que no vuelva.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La publicación de trabajos en la cola")
class TrabajoIaPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TrabajoIaPublisher publicador;

    @BeforeEach
    void crearElPublicador() {
        publicador = new TrabajoIaPublisher(rabbitTemplate);
    }

    @AfterEach
    void cerrarLaTransaccionSimulada() {
        // El registro es estático y por hilo: si se queda abierto contamina al siguiente.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sinTransaccionAbiertaElMensajeSaleAlMomento() {
        // Es el caso del sondeo de atascados: la fila lleva quince minutos guardada y no
        // hay nada que esperar.
        publicador.publicar(7L);

        verificarQueSalioElAvisoDe(7L);
    }

    @Test
    void dentroDeUnaTransaccionElMensajeEsperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publicador.publicar(7L);

        // Todavía no: la fila solo existe dentro de esta transacción, y el consumidor que
        // recogiera el aviso ahora no la encontraría.
        verifyNoInteractions(rabbitTemplate);

        confirmarLaTransaccion();

        verificarQueSalioElAvisoDe(7L);
    }

    @Test
    void siLaTransaccionSeDeshaceElMensajeNoSale() {
        TransactionSynchronizationManager.initSynchronization();

        publicador.publicar(7L);
        // Nadie confirma: la fila nunca llegó a existir, así que mandar el aviso sería
        // poner a los ocho consumidores a buscar un id que no está en la base.
        TransactionSynchronizationManager.clearSynchronization();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void unaTandaEnteraSaleCompletaYNingunAvisoSeAdelanta() {
        // El panel pide la criba de una vacante entera dentro de una sola transacción: los
        // avisos de los tres candidatos tienen que salir todos, y ninguno antes de tiempo.
        TransactionSynchronizationManager.initSynchronization();

        publicador.publicar(1L);
        publicador.publicar(2L);
        publicador.publicar(3L);
        verifyNoInteractions(rabbitTemplate);

        confirmarLaTransaccion();

        verificarQueSalioElAvisoDe(1L);
        verificarQueSalioElAvisoDe(2L);
        verificarQueSalioElAvisoDe(3L);
    }

    private void verificarQueSalioElAvisoDe(long trabajoIaId) {
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.AGENT_EXCHANGE),
                eq(RabbitMQConfig.SELECCION_CALIFICACION_ROUTING_KEY),
                eq((Object) new TrabajoIaMessage(trabajoIaId)));
    }

    /** Lo que hace Spring al cerrar bien la transacción, sin tener que levantar Spring. */
    private void confirmarLaTransaccion() {
        for (TransactionSynchronization sincronizacion :
                TransactionSynchronizationManager.getSynchronizations()) {
            sincronizacion.afterCommit();
        }
    }
}
