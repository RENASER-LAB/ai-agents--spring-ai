package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.FalloDelBot;
import com.renaser.ai.ai_engine.whatsapp.service.FirmaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El cliente del bot de verdad (modo real), contra un servidor HTTP de mentira en esta máquina:
 * las rutas, la firma, y qué fallos se reintentan y cuáles no.
 */
@DisplayName("El cliente HTTP del bot de WhatsApp")
class ClienteConversacionHttpTest {

    private static final String SECRETO = "secreto-de-pruebas-hacia-el-bot-de-32-bytes";
    private static final Instant AHORA = Instant.parse("2026-10-12T15:00:00Z");

    private record Recibida(String ruta, Map<String, List<String>> cabeceras, String cuerpo) {}

    private HttpServer servidor;
    private final List<Recibida> recibidas = new CopyOnWriteArrayList<>();
    private final AtomicInteger estado = new AtomicInteger(202);
    private volatile long demora;
    private ClienteConversacionHttp cliente;

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            byte[] cuerpo = intercambio.getRequestBody().readAllBytes();
            recibidas.add(new Recibida(intercambio.getRequestURI().getPath(),
                    intercambio.getRequestHeaders(), new String(cuerpo, StandardCharsets.UTF_8)));
            try {
                Thread.sleep(demora);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] respuesta = "{}".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(estado.get(), respuesta.length);
            intercambio.getResponseBody().write(respuesta);
            intercambio.close();
        });
        servidor.start();
        PropiedadesWhatsapp propiedades = new PropiedadesWhatsapp();
        propiedades.setUrlServicio("http://127.0.0.1:" + servidor.getAddress().getPort() + "/");
        propiedades.setSecretoHaciaElBot(SECRETO);
        propiedades.setSegundosDeEspera(1);
        cliente = new ClienteConversacionHttp(propiedades,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("Cada llamada va a su ruta /v1/ex/…, con su cuerpo JSON y su firma")
    void rutasCuerposYFirma() {
        UUID avisoId = UUID.fromString("0199d5a2-0000-7000-8000-000000000001");
        cliente.anunciar(new ClienteConversacion.Espera("+51987654321", "pw_1", AHORA.plusSeconds(86_400)));
        cliente.avisar(new ClienteConversacion.Aviso(avisoId, "+51987654321", "pw_1",
                "PRUEBA_DISPONIBLE", "Ana", "Contador", "¡Felicidades, Ana!"));
        cliente.revocar(new ClienteConversacion.Revocacion("+51987654321", "pw_1", "DEJAR_DE_RECIBIR"));

        assertThat(recibidas).extracting(Recibida::ruta)
                .containsExactly("/v1/ex/esperas", "/v1/ex/avisos", "/v1/ex/revocaciones");
        assertThat(recibidas.get(0).cuerpo()).isEqualTo(
                "{\"telefono\":\"+51987654321\",\"postulacionRef\":\"pw_1\","
                        + "\"caducaEn\":\"2026-10-13T15:00:00Z\"}");
        assertThat(recibidas.get(1).cuerpo()).contains("\"avisoId\":\"" + avisoId + "\"")
                .contains("\"texto\":\"¡Felicidades, Ana!\"");
        for (Recibida recibida : recibidas) {
            String marca = recibida.cabeceras().get(FirmaWhatsapp.CABECERA_MARCA).get(0);
            String firma = recibida.cabeceras().get(FirmaWhatsapp.CABECERA_FIRMA).get(0);
            assertThat(marca).isEqualTo(String.valueOf(AHORA.getEpochSecond()));
            assertThat(FirmaWhatsapp.esValida(SECRETO, marca, firma,
                    recibida.cuerpo().getBytes(StandardCharsets.UTF_8), AHORA)).isTrue();
        }
    }

    @Test
    @DisplayName("Un 5xx o un 429 se reintentan; un 4xx, como el 409 de un aviso con otro contenido, no")
    void queSeReintenta() {
        ClienteConversacion.Revocacion revocacion =
                new ClienteConversacion.Revocacion("+51987654321", "pw_1", "PROCESO_TERMINADO");
        estado.set(503);
        assertThatThrownBy(() -> cliente.revocar(revocacion))
                .isInstanceOf(FalloDelBot.class)
                .matches(e -> ((FalloDelBot) e).esReintentable());
        estado.set(429);
        assertThatThrownBy(() -> cliente.revocar(revocacion))
                .matches(e -> ((FalloDelBot) e).esReintentable());
        estado.set(409);
        assertThatThrownBy(() -> cliente.revocar(revocacion))
                .isInstanceOf(FalloDelBot.class)
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .hasMessageContaining("409");
    }

    @Test
    @DisplayName("Si el bot tarda más de la cuenta, es un fallo que se reintenta")
    void siTardaSeReintenta() {
        demora = 1_500;
        assertThatThrownBy(() -> cliente.anunciar(
                new ClienteConversacion.Espera("+51987654321", "pw_1", AHORA.plusSeconds(60))))
                .isInstanceOf(FalloDelBot.class)
                .matches(e -> ((FalloDelBot) e).esReintentable());
    }

    @Test
    @DisplayName("El modo real no arranca sin HTTPS o sin un secreto de verdad")
    void elModoRealExigeSuConfiguracion() {
        PropiedadesWhatsapp sinHttps = new PropiedadesWhatsapp();
        sinHttps.setUrlServicio("http://bot.renaser.pe");
        sinHttps.setSecretoHaciaElBot(SECRETO);
        assertThatThrownBy(() -> ClienteConversacionHttp.exigirConfiguracion(sinHttps))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("https://");

        PropiedadesWhatsapp sinSecreto = new PropiedadesWhatsapp();
        sinSecreto.setUrlServicio("https://bot.renaser.pe");
        sinSecreto.setSecretoHaciaElBot("corto");
        assertThatThrownBy(() -> ClienteConversacionHttp.exigirConfiguracion(sinSecreto))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 caracteres");
    }

    @Test
    @DisplayName("Qué bot hay detrás según el modo: un modo desconocido es apagado")
    void elModo() {
        ConfiguracionClienteConversacion configuracion = new ConfiguracionClienteConversacion();
        PropiedadesWhatsapp propiedades = new PropiedadesWhatsapp();

        propiedades.setModo("falso");
        assertThat(configuracion.clienteConversacion(propiedades)).isInstanceOf(ClienteConversacionFalso.class);
        propiedades.setModo("apagado");
        assertThat(configuracion.clienteConversacion(propiedades)).isInstanceOf(ClienteConversacionApagado.class);
        propiedades.setModo("encendidisimo");
        ClienteConversacion desconocido = configuracion.clienteConversacion(propiedades);
        assertThat(desconocido.estaEncendido()).isFalse();
        assertThatThrownBy(() -> desconocido.revocar(
                new ClienteConversacion.Revocacion("+51987654321", "pw_1", "BAJA")))
                .matches(e -> !((FalloDelBot) e).esReintentable());
        propiedades.setModo("real");
        propiedades.setUrlServicio("https://bot.renaser.pe");
        propiedades.setSecretoHaciaElBot(SECRETO);
        assertThat(configuracion.clienteConversacion(propiedades)).isInstanceOf(ClienteConversacionHttp.class);
    }

    @Test
    @DisplayName("El bot falso apunta cada llamada y, caído, falla como uno de verdad")
    void elFalso() {
        ClienteConversacionFalso falso = new ClienteConversacionFalso();
        falso.anunciar(new ClienteConversacion.Espera("+51987654321", "pw_1", AHORA));
        falso.revocar(new ClienteConversacion.Revocacion("+51911111111", "pw_2", "BAJA"));

        assertThat(falso.registroDe("+51987654321")).extracting(ClienteConversacionFalso.Llamada::tipo)
                .containsExactly("ESPERA");
        assertThat(falso.registro()).hasSize(2);

        falso.fallar(true);
        assertThatThrownBy(() -> falso.avisar(new ClienteConversacion.Aviso(UUID.randomUUID(),
                "+51987654321", "pw_1", "PRUEBA_DISPONIBLE", "Ana", "Contador", "hola")))
                .matches(e -> ((FalloDelBot) e).esReintentable());
        falso.fallar(false);
        falso.olvidar();
        assertThat(falso.registro()).isEmpty();
    }
}
