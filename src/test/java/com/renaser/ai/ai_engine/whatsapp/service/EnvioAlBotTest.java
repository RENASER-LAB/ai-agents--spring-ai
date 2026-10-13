package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.consentimiento.entity.Consentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.whatsapp.entity.AvisoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los envíos al bot (V73): AC-08 (sale una vez), AC-11 (reintenta con espera creciente hasta cinco
 * veces y queda fallido) y lo que ya no toca.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Lo que EX le manda al bot, con sus reintentos")
class EnvioAlBotTest {

    @Mock private AvisoWhatsappRepository avisos;
    @Mock private VinculoWhatsappRepository vinculos;
    @Mock private BajaWhatsappRepository bajas;
    @Mock private ConsentimientoRepository consentimientos;
    @Mock private TextoConsentimientoRepository textos;
    @Mock private ClienteConversacion cliente;
    @Mock private PlatformTransactionManager transacciones;

    private EnvioAlBot envio;
    private final PropiedadesWhatsapp propiedades = new PropiedadesWhatsapp();

    @BeforeEach
    void montar() {
        lenient().when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(cliente.estaEncendido()).thenReturn(true);
        envio = new EnvioAlBot(avisos, vinculos, bajas, consentimientos, textos, cliente, propiedades,
                transacciones);
    }

    private VinculoWhatsapp activo() {
        return VinculoWhatsapp.builder().id(7L).postulacionId(70L).referencia("pw_ref")
                .telefono("+51987654321").estado(VinculoWhatsapp.ACTIVO)
                .vinculadoEn(Instant.now()).build();
    }

    private AvisoWhatsapp pendiente(int intentosHechos) {
        return AvisoWhatsapp.builder().id(UUID.randomUUID()).postulacionId(70L).vinculoWhatsappId(7L)
                .transicionEstadoId(900L).tipo("PRUEBA_DISPONIBLE").estado(AvisoWhatsapp.PENDIENTE)
                .telefono("+51987654321").referencia("pw_ref").nombre("Ana").vacante("Contador")
                .texto("¡Felicidades, Ana!").intentos(intentosHechos).proximoIntentoEn(Instant.now())
                .build();
    }

    private void enLaCola(AvisoWhatsapp aviso) {
        when(avisos.pendientesPorEnviar(any(), any())).thenReturn(List.of(aviso.getId()));
        when(avisos.paraElSondeo(aviso.getId())).thenReturn(Optional.of(aviso));
    }

    @Test
    @DisplayName("AC-08 · sale con su avisoId y su contenido guardado, y queda ENVIADO")
    void saleUnaVez() {
        AvisoWhatsapp aviso = pendiente(0);
        enLaCola(aviso);
        when(vinculos.findById(7L)).thenReturn(Optional.of(activo()));

        envio.enviarPendientes();

        ArgumentCaptor<ClienteConversacion.Aviso> enviado = ArgumentCaptor.forClass(ClienteConversacion.Aviso.class);
        verify(cliente).avisar(enviado.capture());
        assertThat(enviado.getValue().avisoId()).isEqualTo(aviso.getId());
        assertThat(enviado.getValue().postulacionRef()).isEqualTo("pw_ref");
        assertThat(enviado.getValue().texto()).isEqualTo("¡Felicidades, Ana!");
        assertThat(aviso.getEstado()).isEqualTo(AvisoWhatsapp.ENVIADO);
        assertThat(aviso.getIntentos()).isEqualTo(1);
        assertThat(aviso.getProximoIntentoEn()).isNull();
    }

    @Test
    @DisplayName("AC-08 · uno que ya salió no vuelve a salir aunque el sondeo lo vuelva a mirar")
    void elYaEnviadoNoSale() {
        AvisoWhatsapp aviso = pendiente(1);
        aviso.setEstado(AvisoWhatsapp.ENVIADO);
        enLaCola(aviso);

        envio.enviarPendientes();

        verify(cliente, never()).avisar(any());
    }

    @Test
    @DisplayName("AC-11 · si el bot no responde, reintenta con espera creciente: 30 s, 2 min, 8 min, 32 min")
    void reintentaConEsperaCreciente() {
        Instant ahora = Instant.now();
        FalloDelBot caido = new FalloDelBot("no responde", true);
        assertThat(Duration.between(ahora, envio.siguienteIntento(caido, 1, ahora))).isEqualTo(Duration.ofSeconds(30));
        assertThat(Duration.between(ahora, envio.siguienteIntento(caido, 2, ahora))).isEqualTo(Duration.ofMinutes(2));
        assertThat(Duration.between(ahora, envio.siguienteIntento(caido, 3, ahora))).isEqualTo(Duration.ofMinutes(8));
        assertThat(Duration.between(ahora, envio.siguienteIntento(caido, 4, ahora))).isEqualTo(Duration.ofMinutes(32));
        assertThat(envio.siguienteIntento(caido, 5, ahora)).isNull();
        // Un 4xx es definitivo a la primera.
        assertThat(envio.siguienteIntento(new FalloDelBot("409", false), 1, ahora)).isNull();
    }

    @Test
    @DisplayName("AC-11 · un fallo deja el aviso pendiente para más tarde, con su error")
    void unFalloLoDejaParaMasTarde() {
        AvisoWhatsapp aviso = pendiente(0);
        enLaCola(aviso);
        when(vinculos.findById(7L)).thenReturn(Optional.of(activo()));
        doThrow(new FalloDelBot("El bot no respondió", true)).when(cliente).avisar(any());

        envio.enviarPendientes();

        assertThat(aviso.getEstado()).isEqualTo(AvisoWhatsapp.PENDIENTE);
        assertThat(aviso.getIntentos()).isEqualTo(1);
        assertThat(aviso.getUltimoError()).contains("no respondió");
        assertThat(aviso.getProximoIntentoEn()).isAfter(Instant.now().plusSeconds(25));
    }

    @Test
    @DisplayName("AC-11 · al quinto intento fallido queda FALLIDO y no se vuelve a probar")
    void alQuintoQuedaFallido() {
        AvisoWhatsapp aviso = pendiente(4);
        enLaCola(aviso);
        when(vinculos.findById(7L)).thenReturn(Optional.of(activo()));
        doThrow(new FalloDelBot("El bot no respondió", true)).when(cliente).avisar(any());

        envio.enviarPendientes();

        assertThat(aviso.getEstado()).isEqualTo(AvisoWhatsapp.FALLIDO);
        assertThat(aviso.getIntentos()).isEqualTo(5);
        assertThat(aviso.getProximoIntentoEn()).isNull();
    }

    @Test
    @DisplayName("Si el vínculo se cerró antes de salir, el aviso queda OMITIDO y no se manda")
    void sinVinculoQuedaOmitido() {
        AvisoWhatsapp aviso = pendiente(0);
        enLaCola(aviso);
        VinculoWhatsapp cerrado = activo();
        cerrado.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_DEJAR_DE_RECIBIR, Instant.now());
        when(vinculos.findById(7L)).thenReturn(Optional.of(cerrado));

        envio.enviarPendientes();

        verify(cliente, never()).avisar(any());
        assertThat(aviso.getEstado()).isEqualTo(AvisoWhatsapp.OMITIDO);
    }

    @Test
    @DisplayName("La revocación de un vínculo cerrado sale con su motivo y queda marcada")
    void laRevocacion() {
        VinculoWhatsapp cerrado = activo();
        cerrado.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_CAMBIO_DE_CELULAR, Instant.now());
        when(vinculos.paraElSondeo(7L)).thenReturn(Optional.of(cerrado));

        envio.revocarAhora(7L);

        ArgumentCaptor<ClienteConversacion.Revocacion> revocada =
                ArgumentCaptor.forClass(ClienteConversacion.Revocacion.class);
        verify(cliente).revocar(revocada.capture());
        assertThat(revocada.getValue()).isEqualTo(new ClienteConversacion.Revocacion(
                "+51987654321", "pw_ref", "CAMBIO_CELULAR"));
        assertThat(ClienteConversacion.MOTIVOS_DE_REVOCACION).contains(revocada.getValue().motivo());
        assertThat(cerrado.getRevocacionEnviadaEn()).isNotNull();
    }

    @Test
    @DisplayName("Los motivos de cierre que se revocan son los del contrato, tal cual")
    void losMotivosSonLosDelContrato() {
        assertThat(List.of(VinculoWhatsapp.POR_DEJAR_DE_RECIBIR, VinculoWhatsapp.POR_CAMBIO_DE_CELULAR,
                VinculoWhatsapp.POR_PROCESO_TERMINADO, VinculoWhatsapp.POR_BORRADO_DE_DATOS))
                .containsExactlyInAnyOrderElementsOf(ClienteConversacion.MOTIVOS_DE_REVOCACION);
    }

    private VinculoWhatsapp anunciado() {
        Instant ahora = Instant.now();
        return VinculoWhatsapp.builder().id(8L).postulacionId(80L).referencia("pw_anuncio")
                .telefono("+51987654321").estado(VinculoWhatsapp.PENDIENTE).consentimientoId(33L)
                .anunciadoEn(ahora).anuncioCaducaEn(ahora.plus(Duration.ofHours(24))).build();
    }

    @Test
    @DisplayName("El anuncio lleva el consentimiento que firmó: tipo, versión, el texto que vio y cuándo")
    void elAnuncioLlevaSuConsentimiento() {
        VinculoWhatsapp vinculo = anunciado();
        Instant aceptado = Instant.parse("2026-10-13T10:00:00Z");
        when(vinculos.paraElSondeo(8L)).thenReturn(Optional.of(vinculo));
        when(consentimientos.findById(33L)).thenReturn(Optional.of(Consentimiento.builder().id(33L)
                .textoConsentimientoId(44L).aceptadoEn(aceptado).build()));
        when(textos.findById(44L)).thenReturn(Optional.of(TextoConsentimiento.builder().id(44L)
                .tipo("WHATSAPP").version("1.0").texto("Al escribirnos aceptas…").build()));

        envio.anunciarAhora(8L);

        ArgumentCaptor<ClienteConversacion.Espera> espera = ArgumentCaptor.forClass(ClienteConversacion.Espera.class);
        verify(cliente).anunciar(espera.capture());
        assertThat(espera.getValue().postulacionRef()).isEqualTo("pw_anuncio");
        assertThat(espera.getValue().caducaEn()).isEqualTo(vinculo.getAnuncioCaducaEn());
        assertThat(espera.getValue().consentimiento()).isEqualTo(new ClienteConversacion.ConsentimientoDeLaEspera(
                "WHATSAPP", "1.0", "Al escribirnos aceptas…", aceptado));
        assertThat(vinculo.getAnuncioConfirmadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Sin su consentimiento no se anuncia, y no se reintenta")
    void sinConsentimientoNoSeAnuncia() {
        VinculoWhatsapp vinculo = anunciado();
        when(vinculos.paraElSondeo(8L)).thenReturn(Optional.of(vinculo));
        when(consentimientos.findById(33L)).thenReturn(Optional.empty());

        envio.anunciarAhora(8L);

        verify(cliente, never()).anunciar(any());
        assertThat(vinculo.getAnuncioConfirmadoEn()).isNull();
        assertThat(vinculo.getAnuncioProximoIntentoEn()).isNull();
    }

    @Test
    @DisplayName("Un 409 aviso_distinto es definitivo: el aviso queda FALLIDO a la primera")
    void avisoDistintoEsDefinitivo() {
        AvisoWhatsapp aviso = pendiente(0);
        enLaCola(aviso);
        when(vinculos.findById(7L)).thenReturn(Optional.of(activo()));
        doThrow(new FalloDelBot("El bot respondió 409 aviso_distinto", false, "aviso_distinto"))
                .when(cliente).avisar(any());

        envio.enviarPendientes();

        assertThat(aviso.getEstado()).isEqualTo(AvisoWhatsapp.FALLIDO);
        assertThat(aviso.getUltimoError()).contains("aviso_distinto");
        assertThat(aviso.getProximoIntentoEn()).isNull();
    }

    @Test
    @DisplayName("Una baja no se revoca: el bot es quien nos la contó")
    void laBajaNoSeRevoca() {
        VinculoWhatsapp suprimido = activo();
        suprimido.cerrar(VinculoWhatsapp.SUPRIMIDO, VinculoWhatsapp.POR_BAJA, Instant.now());
        when(vinculos.paraElSondeo(7L)).thenReturn(Optional.of(suprimido));

        envio.revocarAhora(7L);

        verify(cliente, never()).revocar(any());
    }

    @Test
    @DisplayName("Con el bot apagado el sondeo no hace nada")
    void apagadoNoHaceNada() {
        when(cliente.estaEncendido()).thenReturn(false);

        envio.enviarPendientes();

        verify(avisos, never()).pendientesPorEnviar(any(), any());
    }
}
