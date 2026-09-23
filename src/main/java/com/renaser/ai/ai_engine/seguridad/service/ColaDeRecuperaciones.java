package com.renaser.ai.ai_engine.seguridad.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Donde se atiende una solicitud de contraseña nueva: después de responder, no antes.
 *
 * <p><b>Por qué en segundo plano.</b> La respuesta tiene que ser la misma exista o no la
 * cuenta, y eso incluye cuánto tarda. Con la cuenta hay que crear el enlace y mandar el
 * correo —por SMTP, un segundo o dos—; sin ella no hay nada que hacer. Contestando antes de
 * hacer nada, las dos tardan lo mismo, que es más de lo que consigue un señuelo como el del
 * login ({@code hashSenuelo}): aquel iguala un BCrypt, esto iguala todo.
 *
 * <p><b>Por qué acotada.</b> Dos hilos y una cola de doscientas: una ráfaga de solicitudes
 * no puede quedarse con las conexiones de la base que necesita el resto del sistema. Si la
 * cola se llena, la solicitud se descarta con un aviso en el registro y quien la hizo ve
 * el mismo mensaje de siempre —no se le puede decir otra cosa sin revelar nada—.
 *
 * <p>Lo que queda en cola se pierde si el proceso se para. Es aceptable: la pantalla ofrece
 * reenviar el enlace pasado un minuto.
 */
@Component
@Slf4j
public class ColaDeRecuperaciones implements DisposableBean {

    private static final int HILOS = 2;
    private static final int CAPACIDAD = 200;

    private final ThreadPoolExecutor ejecutor;
    /** Encoladas y sin terminar. Contado aquí: los contadores del ejecutor son aproximados. */
    private final AtomicInteger pendientes = new AtomicInteger();

    public ColaDeRecuperaciones() {
        ejecutor = new ThreadPoolExecutor(HILOS, HILOS, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(CAPACIDAD),
                Thread.ofVirtual().name("recuperacion-clave-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        ejecutor.allowCoreThreadTimeOut(true);
    }

    /** Deja la tarea para después. Falso si la cola está llena y se descartó. */
    public boolean encolar(Runnable tarea) {
        pendientes.incrementAndGet();
        try {
            ejecutor.execute(() -> {
                try {
                    tarea.run();
                } catch (RuntimeException e) {
                    // Nadie espera la respuesta: si no se anota aquí, el fallo desaparece.
                    log.error("Falló una solicitud de contraseña nueva en segundo plano", e);
                } finally {
                    pendientes.decrementAndGet();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            pendientes.decrementAndGet();
            log.warn("Solicitud de contraseña nueva descartada: la cola está llena ({} en espera)",
                    ejecutor.getQueue().size());
            return false;
        }
    }

    /**
     * Si no queda nada en cola ni en marcha. Lo usan las pruebas de integración para saber
     * cuándo terminó lo que pidieron: la respuesta llega antes que el trabajo, así que sin
     * esto «no se creó ningún enlace» sería indistinguible de «todavía no se creó».
     */
    public boolean enReposo() {
        return pendientes.get() == 0;
    }

    @Override
    public void destroy() throws InterruptedException {
        ejecutor.shutdown();
        if (!ejecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            ejecutor.shutdownNow();
        }
    }
}
