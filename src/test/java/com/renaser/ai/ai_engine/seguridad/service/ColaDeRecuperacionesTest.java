package com.renaser.ai.ai_engine.seguridad.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Donde se atiende una solicitud de contraseña nueva, después de responder. */
@DisplayName("La cola de las solicitudes de contraseña nueva")
class ColaDeRecuperacionesTest {

    private final ColaDeRecuperaciones cola = new ColaDeRecuperaciones();

    @AfterEach
    void cerrar() throws InterruptedException {
        cola.destroy();
    }

    private void esperarReposo() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!cola.enReposo() && System.nanoTime() < limite) {
            Thread.sleep(10);
        }
    }

    @Test
    @DisplayName("lo encolado se atiende en otro hilo, y al terminar la cola queda en reposo")
    void atiendeEnOtroHilo() throws InterruptedException {
        CountDownLatch hecho = new CountDownLatch(1);
        String[] hilo = new String[1];

        assertThat(cola.encolar(() -> {
            hilo[0] = Thread.currentThread().getName();
            hecho.countDown();
        })).isTrue();

        assertThat(hecho.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(hilo[0]).startsWith("recuperacion-clave-");
        esperarReposo();
        assertThat(cola.enReposo()).isTrue();
    }

    @Test
    @DisplayName("no está en reposo mientras quede algo en marcha")
    void noEstaEnReposoConAlgoEnMarcha() throws InterruptedException {
        CountDownLatch soltar = new CountDownLatch(1);
        cola.encolar(() -> {
            try {
                soltar.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(cola.enReposo()).isFalse();
        soltar.countDown();
        esperarReposo();
        assertThat(cola.enReposo()).isTrue();
    }

    @Test
    @DisplayName("una tarea que revienta no se lleva la cola: la siguiente se atiende igual")
    void unaTareaQueRevientaNoSeLlevaLaCola() throws InterruptedException {
        cola.encolar(() -> {
            throw new IllegalStateException("la base se cayó");
        });
        CountDownLatch hecho = new CountDownLatch(1);
        cola.encolar(hecho::countDown);

        assertThat(hecho.await(5, TimeUnit.SECONDS)).isTrue();
        esperarReposo();
        assertThat(cola.enReposo()).isTrue();
    }
}
