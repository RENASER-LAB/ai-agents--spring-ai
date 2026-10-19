package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.whatsapp.entity.EventoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.EventoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.FalloDelBot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El envío de los eventos v2 (V76): una vez, con el mismo contenido en cada intento, sin bucles
 * con un bot en v1 y sin salir si ya no toca. AC-12, AC-13, AC-16 y AC-18 del lado de EX.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El envío de los eventos v2 al bot")
class EnvioDeEventosTest {

    @Mock private EventoWhatsappRepository eventos;
    @Mock private ResultadosPorWhatsapp resultados;
    @Mock private ClienteConversacion cliente;
    @Mock private EnvioAlBot envio;
    @Mock private PlatformTransactionManager transacciones;

    private final PropiedadesWhatsapp propiedades = new PropiedadesWhatsapp();
    private EnvioDeEventos envioDeEventos;

    @BeforeEach
    void montar() {
        propiedades.setProductorV2(true);
        lenient().when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(cliente.estaEncendido()).thenReturn(true);
        envioDeEventos = new EnvioDeEventos(eventos, resultados, cliente, propiedades, envio, transacciones);
    }

    private static EventoWhatsapp pendiente() {
        Instant ocurrido = Instant.now().minus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
        return EventoWhatsapp.builder().id(UuidV7.nuevo()).postulacionId(70L).vinculoWhatsappId(7L)
                .tipo("RESULTADO").revision(1).estado("PENDIENTE").telefono("+51987654321")
                .referencia("pw_ref").nombre("Ana").vacante("Contador").ocurridoEn(ocurrido)
                .resultadoTipo("CONTRATADO").resultadoFecha(ocurrido)
                .resultadoConsultableHasta(ocurrido.plus(Duration.ofDays(30)))
                .avisoAutorizado(true).avisoCaducaEn(ocurrido.plus(Duration.ofHours(24))).cierraVinculo(true)
                .proximoIntentoEn(Instant.now()).build();
    }

    private void enLaCola(EventoWhatsapp evento) {
        when(eventos.pendientesPorEnviar(any(), any())).thenReturn(List.of(evento.getId()));
        when(eventos.paraElSondeo(evento.getId())).thenReturn(Optional.of(evento));
    }

    @Test
    @DisplayName("AC-12 · un pendiente sale con lo guardado y queda ACEPTADO (aceptado, no entregado)")
    void sale() {
        EventoWhatsapp evento = pendiente();
        enLaCola(evento);

        envioDeEventos.enviarPendientes();

        ArgumentCaptor<ClienteConversacion.Evento> mandado = ArgumentCaptor.forClass(ClienteConversacion.Evento.class);
        verify(cliente).publicarEvento(mandado.capture());
        assertThat(mandado.getValue().eventoId()).isEqualTo(evento.getId());
        assertThat(mandado.getValue().comoCuerpo()).containsKeys("eventoId", "tipo", "telefono", "postulacionRef",
                "nombre", "vacante", "ocurridoEn", "resultado", "aviso", "cierraVinculo")
                .doesNotContainKey("convocatoria");
        assertThat(evento.getEstado()).isEqualTo("ACEPTADO");
        assertThat(evento.getAceptadoEn()).isNotNull();
        verify(resultados).trasElEvento(evento, evento.getAceptadoEn());
    }

    @Test
    @DisplayName("AC-13 · un reintento lleva exactamente el mismo contenido que el primero")
    void mismoContenido() {
        EventoWhatsapp evento = pendiente();

        assertThat(EnvioDeEventos.comoEvento(evento).comoCuerpo())
                .isEqualTo(EnvioDeEventos.comoEvento(evento).comoCuerpo());
    }

    @Test
    @DisplayName("C-10 · con el bot en v1 (404 ruta_no_disponible) queda FALLIDO a la primera, sin bucle")
    void botEnV1() {
        EventoWhatsapp evento = pendiente();
        enLaCola(evento);
        FalloDelBot noDisponible = new FalloDelBot("404 ruta_no_disponible", false, "ruta_no_disponible");
        doThrow(noDisponible).when(cliente).publicarEvento(any());
        // Lo que decide EnvioAlBot para un 4xx: no hay siguiente intento.
        when(envio.siguienteIntento(any(), any(Integer.class), any())).thenReturn(null);

        envioDeEventos.enviarPendientes();

        assertThat(evento.getEstado()).isEqualTo("FALLIDO");
        assertThat(evento.getMotivo()).isEqualTo("ruta_no_disponible");
        assertThat(evento.getIntentos()).isEqualTo(1);
        assertThat(evento.getProximoIntentoEn()).isNull();
        verify(resultados).trasElEvento(any(), any());
    }

    @Test
    @DisplayName("Un 5xx se reintenta más tarde y el evento sigue pendiente")
    void reintenta() {
        EventoWhatsapp evento = pendiente();
        enLaCola(evento);
        doThrow(new FalloDelBot("503", true)).when(cliente).publicarEvento(any());
        Instant luego = Instant.now().plusSeconds(30);
        when(envio.siguienteIntento(any(), any(Integer.class), any())).thenReturn(luego);

        envioDeEventos.enviarPendientes();

        assertThat(evento.getEstado()).isEqualTo("PENDIENTE");
        assertThat(evento.getProximoIntentoEn()).isEqualTo(luego);
        assertThat(evento.getUltimoError()).isEqualTo("503");
    }

    @Test
    @DisplayName("AC-15/AC-16 · si ya no toca (caducado, BAJA, revocado) queda OMITIDO y no sale")
    void yaNoToca() {
        EventoWhatsapp evento = pendiente();
        enLaCola(evento);
        when(resultados.motivoParaNoEnviar(any(), any())).thenReturn("autorizacion_caducada");

        envioDeEventos.enviarPendientes();

        verify(cliente, never()).publicarEvento(any());
        assertThat(evento.getEstado()).isEqualTo("OMITIDO");
        assertThat(evento.getMotivo()).isEqualTo("autorizacion_caducada");
        verify(resultados).trasElEvento(any(), any());
    }

    @Test
    @DisplayName("AC-18 · con el productor apagado no sale nada; solo se cierran los que caducaron")
    void productorApagado() {
        propiedades.setProductorV2(false);
        EventoWhatsapp evento = pendiente();
        when(eventos.caducadosPorCerrar(any(), any())).thenReturn(List.of(evento.getId()));
        when(eventos.paraElSondeo(evento.getId())).thenReturn(Optional.of(evento));

        envioDeEventos.enviarPendientes();

        verify(eventos, never()).pendientesPorEnviar(any(), any());
        verify(cliente, never()).publicarEvento(any());
        // No caducó todavía: se queda esperando, sin tipos que el bot en v1 no entiende.
        assertThat(evento.getEstado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("AC-18 · en modo apagado no se mira la cola")
    void modoApagado() {
        when(cliente.estaEncendido()).thenReturn(false);

        envioDeEventos.enviarPendientes();

        verify(eventos, never()).pendientesPorEnviar(any(), any());
        verify(eventos, never()).caducadosPorCerrar(any(), any());
    }

    @Test
    @DisplayName("Uno ya aceptado o tomado por otro sondeo no se vuelve a mandar")
    void noDosVeces() {
        EventoWhatsapp evento = pendiente();
        evento.setEstado("ACEPTADO");
        enLaCola(evento);

        envioDeEventos.enviarPendientes();
        envioDeEventos.enviarPendientes();

        verify(cliente, never()).publicarEvento(any());
        verify(eventos, times(2)).paraElSondeo(evento.getId());
    }
}
