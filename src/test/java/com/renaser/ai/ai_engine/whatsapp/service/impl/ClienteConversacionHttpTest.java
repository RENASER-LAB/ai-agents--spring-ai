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

    private static final ClienteConversacion.ConsentimientoDeLaEspera CONSENTIMIENTO =
            new ClienteConversacion.ConsentimientoDeLaEspera("WHATSAPP", "1.0",
                    "Al escribirnos aceptas recibir por WhatsApp las novedades de este proceso. "
                            + "Puedes dejarlo cuando quieras.",
                    Instant.parse("2026-10-12T14:59:00Z"));

    /** Lo que contesta el bot si la prueba no fuerza otra cosa: lo del contrato. */
    private static final int LO_DEL_CONTRATO = -1;

    private HttpServer servidor;
    private final List<Recibida> recibidas = new CopyOnWriteArrayList<>();
    private final AtomicInteger estado = new AtomicInteger(LO_DEL_CONTRATO);
    private volatile String cuerpoDelError = "{}";
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
            String ruta = intercambio.getRequestURI().getPath();
            int forzado = estado.get();
            if (forzado == LO_DEL_CONTRATO && !ruta.endsWith("/avisos") && !ruta.endsWith("/resultados")) {
                intercambio.sendResponseHeaders(204, -1);
                intercambio.close();
                return;
            }
            int codigo = forzado == LO_DEL_CONTRATO ? 202 : forzado;
            byte[] respuesta = (forzado == LO_DEL_CONTRATO ? "{\"avisoId\":\"x\"}" : cuerpoDelError)
                    .getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(codigo, respuesta.length);
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
    @DisplayName("Cada llamada va a su ruta /v1/ex/…, con su cuerpo JSON y su firma sobre la ruta")
    void rutasCuerposYFirma() {
        UUID avisoId = UUID.fromString("0199d5a2-0000-7000-8000-000000000001");
        cliente.anunciar(new ClienteConversacion.Espera("+51987654321", "pw_1", AHORA.plusSeconds(86_400),
                CONSENTIMIENTO));
        cliente.avisar(new ClienteConversacion.Aviso(avisoId, "+51987654321", "pw_1",
                "PRUEBA_DISPONIBLE", "Ana", "Contador", "¡Felicidades, Ana!"));
        cliente.revocar(new ClienteConversacion.Revocacion("+51987654321", "pw_1", "DEJAR_DE_RECIBIR"));

        assertThat(recibidas).extracting(Recibida::ruta)
                .containsExactly("/v1/ex/esperas", "/v1/ex/avisos", "/v1/ex/revocaciones");
        assertThat(recibidas.get(0).cuerpo()).isEqualTo(
                "{\"telefono\":\"+51987654321\",\"postulacionRef\":\"pw_1\","
                        + "\"caducaEn\":\"2026-10-13T15:00:00Z\","
                        + "\"consentimiento\":{\"tipo\":\"WHATSAPP\",\"version\":\"1.0\","
                        + "\"texto\":\"Al escribirnos aceptas recibir por WhatsApp las novedades de este "
                        + "proceso. Puedes dejarlo cuando quieras.\",\"aceptadoEn\":\"2026-10-12T14:59:00Z\"}}");
        assertThat(recibidas.get(2).cuerpo()).isEqualTo(
                "{\"telefono\":\"+51987654321\",\"postulacionRef\":\"pw_1\",\"motivo\":\"DEJAR_DE_RECIBIR\"}");
        assertThat(recibidas.get(1).cuerpo()).contains("\"avisoId\":\"" + avisoId + "\"")
                .contains("\"texto\":\"¡Felicidades, Ana!\"");
        for (Recibida recibida : recibidas) {
            String marca = recibida.cabeceras().get(FirmaWhatsapp.CABECERA_MARCA).get(0);
            String firma = recibida.cabeceras().get(FirmaWhatsapp.CABECERA_FIRMA).get(0);
            assertThat(marca).isEqualTo(String.valueOf(AHORA.getEpochSecond()));
            assertThat(FirmaWhatsapp.esValida(SECRETO, marca, firma, recibida.ruta(),
                    recibida.cuerpo().getBytes(StandardCharsets.UTF_8), AHORA)).isTrue();
            // La de otra ruta no valdría: la ruta entra en la firma.
            assertThat(FirmaWhatsapp.esValida(SECRETO, marca, firma, "/v1/ex/otra",
                    recibida.cuerpo().getBytes(StandardCharsets.UTF_8), AHORA)).isFalse();
        }
    }

    @Test
    @DisplayName("Con una dirección base con prefijo, se firma el path completo")
    void conPrefijo() {
        PropiedadesWhatsapp propiedades = new PropiedadesWhatsapp();
        propiedades.setUrlServicio("http://127.0.0.1:" + servidor.getAddress().getPort() + "/conversacion/");
        propiedades.setSecretoHaciaElBot(SECRETO);
        propiedades.setSegundosDeEspera(1);
        new ClienteConversacionHttp(propiedades,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                Clock.fixed(AHORA, ZoneOffset.UTC))
                .revocar(new ClienteConversacion.Revocacion("+51987654321", "pw_1", "PROCESO_TERMINADO"));

        Recibida recibida = recibidas.get(0);
        assertThat(recibida.ruta()).isEqualTo("/conversacion/v1/ex/revocaciones");
        assertThat(FirmaWhatsapp.esValida(SECRETO, String.valueOf(AHORA.getEpochSecond()),
                recibida.cabeceras().get(FirmaWhatsapp.CABECERA_FIRMA).get(0), "/conversacion/v1/ex/revocaciones",
                recibida.cuerpo().getBytes(StandardCharsets.UTF_8), AHORA)).isTrue();
    }

    @Test
    @DisplayName("Solo vale la respuesta del contrato: un 200 donde tocaba 204 es un fallo definitivo")
    void otraRespuestaNoVale() {
        estado.set(200);
        assertThatThrownBy(() -> cliente.revocar(
                new ClienteConversacion.Revocacion("+51987654321", "pw_1", "PROCESO_TERMINADO")))
                .isInstanceOf(FalloDelBot.class)
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .hasMessageContaining("204");
    }

    @Test
    @DisplayName("Un 5xx, un 429 o un 401 se reintentan; otro 4xx, como 409 aviso_distinto, no")
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
        // Un 401 puede ser un cambio de secreto a medias: se reintenta.
        estado.set(401);
        cuerpoDelError = "{\"code\":\"firma_invalida\",\"message\":\"Firma no válida\",\"retryable\":false}";
        assertThatThrownBy(() -> cliente.revocar(revocacion))
                .matches(e -> ((FalloDelBot) e).esReintentable())
                .matches(e -> "firma_invalida".equals(((FalloDelBot) e).codigo()));
        estado.set(408);
        cuerpoDelError = "{}";
        assertThatThrownBy(() -> cliente.revocar(revocacion))
                .matches(e -> !((FalloDelBot) e).esReintentable());
        estado.set(409);
        cuerpoDelError = "{\"code\":\"aviso_distinto\",\"message\":\"Otro contenido\"}";
        assertThatThrownBy(() -> cliente.avisar(new ClienteConversacion.Aviso(UUID.randomUUID(),
                "+51987654321", "pw_1", "PRUEBA_DISPONIBLE", "Ana", "Contador", "hola")))
                .isInstanceOf(FalloDelBot.class)
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .matches(e -> "aviso_distinto".equals(((FalloDelBot) e).codigo()))
                .hasMessageContaining("409 aviso_distinto");
        estado.set(400);
        cuerpoDelError = "no es json";
        assertThatThrownBy(() -> cliente.revocar(revocacion))
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .matches(e -> ((FalloDelBot) e).codigo() == null);
    }

    @Test
    @DisplayName("Si el bot tarda más de la cuenta, es un fallo que se reintenta")
    void siTardaSeReintenta() {
        demora = 1_500;
        assertThatThrownBy(() -> cliente.anunciar(
                new ClienteConversacion.Espera("+51987654321", "pw_1", AHORA.plusSeconds(60), CONSENTIMIENTO)))
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
                new ClienteConversacion.Revocacion("+51987654321", "pw_1", "DEJAR_DE_RECIBIR")))
                .matches(e -> !((FalloDelBot) e).esReintentable());
        propiedades.setModo("real");
        propiedades.setUrlServicio("https://bot.renaser.pe");
        propiedades.setSecretoHaciaElBot(SECRETO);
        assertThat(configuracion.clienteConversacion(propiedades)).isInstanceOf(ClienteConversacionHttp.class);
    }

    @Test
    @DisplayName("El bot falso apunta cada llamada, con el consentimiento de la espera, y caído falla como uno de verdad")
    void elFalso() {
        ClienteConversacionFalso falso = new ClienteConversacionFalso();
        falso.anunciar(new ClienteConversacion.Espera("+51987654321", "pw_1", AHORA, CONSENTIMIENTO));
        falso.revocar(new ClienteConversacion.Revocacion("+51911111111", "pw_2", "CAMBIO_CELULAR"));

        assertThat(falso.registroDe("+51987654321")).extracting(ClienteConversacionFalso.Llamada::tipo)
                .containsExactly("ESPERA");
        assertThat(falso.registroDe("+51987654321").get(0).cuerpo().get("consentimiento"))
                .isEqualTo(CONSENTIMIENTO.comoCuerpo());
        assertThat(falso.registro()).hasSize(2);

        falso.fallar(true);
        assertThatThrownBy(() -> falso.avisar(new ClienteConversacion.Aviso(UUID.randomUUID(),
                "+51987654321", "pw_1", "PRUEBA_DISPONIBLE", "Ana", "Contador", "hola")))
                .matches(e -> ((FalloDelBot) e).esReintentable());
        falso.fallar(false);
        falso.olvidar();
        assertThat(falso.registro()).isEmpty();
    }

    @Test
    @DisplayName("El bot falso contesta como el contrato: 400 validacion y 409 aviso_distinto, definitivos")
    void elFalsoCumpleElContrato() {
        ClienteConversacionFalso falso = new ClienteConversacionFalso();
        assertThatThrownBy(() -> falso.anunciar(
                new ClienteConversacion.Espera("+51987654321", "pw_1", AHORA, null)))
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .matches(e -> "validacion".equals(((FalloDelBot) e).codigo()));
        assertThatThrownBy(() -> falso.revocar(
                new ClienteConversacion.Revocacion("+51987654321", "pw_1", "CAMBIO_DE_CELULAR")))
                .matches(e -> "validacion".equals(((FalloDelBot) e).codigo()));
        assertThatThrownBy(() -> falso.revocar(
                new ClienteConversacion.Revocacion("51987654321", "pw_1", "DEJAR_DE_RECIBIR")))
                .matches(e -> "validacion".equals(((FalloDelBot) e).codigo()));
        assertThatThrownBy(() -> falso.avisar(new ClienteConversacion.Aviso(UUID.randomUUID(),
                "+51987654321", "pw_1", "PRUEBA_DISPONIBLE", "Ana", "x".repeat(61), "hola")))
                .matches(e -> "validacion".equals(((FalloDelBot) e).codigo()));

        UUID avisoId = UUID.randomUUID();
        ClienteConversacion.Aviso aviso = new ClienteConversacion.Aviso(avisoId, "+51987654321", "pw_1",
                "PRUEBA_DISPONIBLE", "Ana", "Contador", "hola");
        falso.avisar(aviso);
        falso.avisar(aviso);
        assertThatThrownBy(() -> falso.avisar(new ClienteConversacion.Aviso(avisoId, "+51987654321",
                "pw_1", "PRUEBA_DISPONIBLE", "Ana", "Contador", "otro texto")))
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .matches(e -> "aviso_distinto".equals(((FalloDelBot) e).codigo()));
        assertThat(falso.registro()).extracting(ClienteConversacionFalso.Llamada::tipo)
                .containsExactly("AVISO", "AVISO");
    }

    // ============ los eventos v2 (V76) ============

    private static final UUID EVENTO_ID = UUID.fromString("0199d5a2-0000-7000-8000-0000000000e1");

    private static ClienteConversacion.Evento resultado(UUID id, String tipo) {
        Instant ocurrido = Instant.parse("2026-10-12T14:00:00Z");
        return new ClienteConversacion.Evento(id, "RESULTADO", "+51987654321", "pw_1", "Ana", "Contador",
                ocurrido, new ClienteConversacion.ResultadoDelEvento(tipo, ocurrido, 1, null, false,
                        ocurrido.plus(Duration.ofDays(30))),
                null, true, ocurrido.plus(Duration.ofHours(24)), true);
    }

    private static ClienteConversacion.Evento convocatoria(UUID id) {
        Instant cerrada = Instant.parse("2026-10-12T14:00:00Z");
        return new ClienteConversacion.Evento(id, "CONVOCATORIA_CERRADA", "+51987654321", "pw_1", "Ana",
                "Contador", cerrada, null, new ClienteConversacion.ConvocatoriaDelEvento(cerrada, 1), true,
                cerrada.plus(Duration.ofHours(24)), false);
    }

    @Test
    @DisplayName("C-4 · el evento va a /v1/ex/resultados, firmado sobre esa ruta, con el cuerpo del contrato")
    void elEventoVaASuRuta() {
        cliente.publicarEvento(resultado(EVENTO_ID, "NO_CONTINUA"));

        Recibida recibida = recibidas.get(0);
        assertThat(recibida.ruta()).isEqualTo("/v1/ex/resultados");
        assertThat(recibida.cuerpo()).isEqualTo("{\"eventoId\":\"" + EVENTO_ID + "\",\"tipo\":\"RESULTADO\","
                + "\"telefono\":\"+51987654321\",\"postulacionRef\":\"pw_1\",\"nombre\":\"Ana\","
                + "\"vacante\":\"Contador\",\"ocurridoEn\":\"2026-10-12T14:00:00Z\","
                + "\"resultado\":{\"tipo\":\"NO_CONTINUA\",\"fecha\":\"2026-10-12T14:00:00Z\",\"revision\":1,"
                + "\"explicacion\":null,\"esCorreccion\":false,\"consultableHasta\":\"2026-11-11T14:00:00Z\"},"
                + "\"aviso\":{\"autorizado\":true,\"caducaEn\":\"2026-10-13T14:00:00Z\"},\"cierraVinculo\":true}");
        String marca = recibida.cabeceras().get(FirmaWhatsapp.CABECERA_MARCA).get(0);
        String firma = recibida.cabeceras().get(FirmaWhatsapp.CABECERA_FIRMA).get(0);
        assertThat(FirmaWhatsapp.esValida(SECRETO, marca, firma, "/v1/ex/resultados",
                recibida.cuerpo().getBytes(StandardCharsets.UTF_8), AHORA)).isTrue();
        assertThat(FirmaWhatsapp.esValida(SECRETO, marca, firma, "/v1/ex/avisos",
                recibida.cuerpo().getBytes(StandardCharsets.UTF_8), AHORA)).isFalse();
    }

    @Test
    @DisplayName("C-4/C-10 · un bot en v1 (404 ruta_no_disponible) o un 409 evento_distinto son definitivos; un 503 se reintenta")
    void losFallosDelEvento() {
        estado.set(404);
        cuerpoDelError = "{\"code\":\"ruta_no_disponible\",\"message\":\"v1\",\"retryable\":false}";
        assertThatThrownBy(() -> cliente.publicarEvento(resultado(EVENTO_ID, "CERRADA")))
                .isInstanceOf(FalloDelBot.class)
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .matches(e -> "ruta_no_disponible".equals(((FalloDelBot) e).codigo()));

        estado.set(409);
        cuerpoDelError = "{\"code\":\"evento_distinto\"}";
        assertThatThrownBy(() -> cliente.publicarEvento(convocatoria(EVENTO_ID)))
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .matches(e -> "evento_distinto".equals(((FalloDelBot) e).codigo()));

        estado.set(503);
        cuerpoDelError = "{\"code\":\"ex_cuenta_no_disponible\"}";
        assertThatThrownBy(() -> cliente.publicarEvento(convocatoria(EVENTO_ID)))
                .matches(e -> ((FalloDelBot) e).esReintentable());
    }

    @Test
    @DisplayName("C-9 · el bot falso valida el evento, lo reconoce por su eventoId y en v1 contesta 404")
    void elFalsoConEventos() {
        ClienteConversacionFalso falso = new ClienteConversacionFalso();
        UUID id = com.renaser.ai.ai_engine.whatsapp.service.UuidV7.nuevo();
        falso.publicarEvento(resultado(id, "CONTRATADO"));
        falso.publicarEvento(resultado(id, "CONTRATADO"));
        assertThatThrownBy(() -> falso.publicarEvento(resultado(id, "NO_CONTINUA")))
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .matches(e -> "evento_distinto".equals(((FalloDelBot) e).codigo()));
        assertThat(falso.registroDe("+51987654321")).extracting(ClienteConversacionFalso.Llamada::tipo)
                .containsExactly("EVENTO", "EVENTO");

        // Fuera de contrato: un id que no es UUIDv7, una caducidad de más de 24 horas, un resultado
        // que no existe o una convocatoria que dice cerrar el vínculo.
        assertThatThrownBy(() -> falso.publicarEvento(resultado(UUID.randomUUID(), "CONTRATADO")))
                .matches(e -> "validacion".equals(((FalloDelBot) e).codigo()));
        ClienteConversacion.Evento largo = resultado(com.renaser.ai.ai_engine.whatsapp.service.UuidV7.nuevo(),
                "CONTRATADO");
        assertThatThrownBy(() -> falso.publicarEvento(new ClienteConversacion.Evento(largo.eventoId(),
                largo.tipo(), largo.telefono(), largo.postulacionRef(), largo.nombre(), largo.vacante(),
                largo.ocurridoEn(), largo.resultado(), null, true, largo.ocurridoEn().plus(Duration.ofHours(25)),
                true))).matches(e -> "validacion".equals(((FalloDelBot) e).codigo()));
        assertThatThrownBy(() -> falso.publicarEvento(resultado(
                com.renaser.ai.ai_engine.whatsapp.service.UuidV7.nuevo(), "DESCARTADO")))
                .matches(e -> "validacion".equals(((FalloDelBot) e).codigo()));
        ClienteConversacion.Evento cierre = convocatoria(com.renaser.ai.ai_engine.whatsapp.service.UuidV7.nuevo());
        assertThatThrownBy(() -> falso.publicarEvento(new ClienteConversacion.Evento(cierre.eventoId(),
                cierre.tipo(), cierre.telefono(), cierre.postulacionRef(), cierre.nombre(), cierre.vacante(),
                cierre.ocurridoEn(), null, cierre.convocatoria(), true, cierre.avisoCaducaEn(), true)))
                .matches(e -> "validacion".equals(((FalloDelBot) e).codigo()));

        falso.responderComoVersion(1);
        assertThatThrownBy(() -> falso.publicarEvento(convocatoria(
                com.renaser.ai.ai_engine.whatsapp.service.UuidV7.nuevo())))
                .matches(e -> !((FalloDelBot) e).esReintentable())
                .matches(e -> "ruta_no_disponible".equals(((FalloDelBot) e).codigo()));
    }

    @Test
    @DisplayName("El modo apagado no publica eventos: definitivo, no hay a quién reintentárselo")
    void apagadoSinEventos() {
        assertThatThrownBy(() -> new ClienteConversacionApagado().publicarEvento(convocatoria(EVENTO_ID)))
                .matches(e -> !((FalloDelBot) e).esReintentable());
    }
}
