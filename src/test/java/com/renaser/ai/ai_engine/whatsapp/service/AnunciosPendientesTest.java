package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.ConsentimientoDeLaEspera;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.Espera;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.FalloDelBot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El bot recoge los anuncios que no le llegaron")
class AnunciosPendientesTest {

    private static final String NUMERO = "+51922880230";

    @Mock private VinculoWhatsappRepository vinculos;
    @Mock private EnvioAlBot envio;
    @InjectMocks private AnunciosPendientes anuncios;

    private static VinculoWhatsapp pendiente(long id, Instant caduca) {
        return VinculoWhatsapp.builder().id(id).referencia("pw_" + id).telefono(NUMERO)
                .estado(VinculoWhatsapp.PENDIENTE).anuncioCaducaEn(caduca)
                .anuncioProximoIntentoEn(Instant.now()).build();
    }

    private static Espera espera(VinculoWhatsapp v) {
        return new Espera(v.getTelefono(), v.getReferencia(), v.getAnuncioCaducaEn(),
                new ConsentimientoDeLaEspera("WHATSAPP", "v1", "Acepto", Instant.parse("2026-11-24T09:00:00Z")));
    }

    @Test
    @DisplayName("Entrega el anuncio vigente que agotó los reintentos y lo deja confirmado")
    void entregaYConfirma() {
        VinculoWhatsapp v = pendiente(53, Instant.now().plusSeconds(3600));
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of(v));
        when(envio.esperaDe(v)).thenReturn(espera(v));

        var resultado = anuncios.delNumero("51922880230");

        assertThat(resultado.esperas()).hasSize(1);
        assertThat(resultado.esperas().get(0)).containsEntry("postulacionRef", "pw_53")
                .containsEntry("telefono", NUMERO).containsKey("consentimiento");
        assertThat(v.getAnuncioConfirmadoEn()).isNotNull();
        assertThat(v.getAnuncioProximoIntentoEn()).isNull();
        verify(vinculos).save(v);
    }

    @Test
    @DisplayName("No entrega lo vencido, lo ya vinculado ni lo que no tiene consentimiento")
    void filtra() {
        VinculoWhatsapp vencido = pendiente(1, Instant.now().minusSeconds(5));
        VinculoWhatsapp activo = pendiente(2, Instant.now().plusSeconds(3600));
        activo.setEstado(VinculoWhatsapp.ACTIVO);
        VinculoWhatsapp sinConsentimiento = pendiente(3, Instant.now().plusSeconds(3600));
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of(vencido, activo, sinConsentimiento));
        when(envio.esperaDe(sinConsentimiento)).thenThrow(new FalloDelBot("sin consentimiento", false));

        assertThat(anuncios.delNumero(NUMERO).esperas()).isEmpty();
        verify(vinculos, never()).save(any());
    }

    @Test
    @DisplayName("Un anuncio ya confirmado se vuelve a entregar sin tocarlo (el bot pudo perderlo)")
    void yaConfirmado() {
        VinculoWhatsapp v = pendiente(4, Instant.now().plusSeconds(3600));
        Instant antes = Instant.parse("2026-11-24T10:00:00Z");
        v.setAnuncioConfirmadoEn(antes);
        when(vinculos.vivosDelNumeroParaEscribir(NUMERO)).thenReturn(List.of(v));
        when(envio.esperaDe(v)).thenReturn(espera(v));

        assertThat(anuncios.delNumero(NUMERO).esperas()).hasSize(1);
        assertThat(v.getAnuncioConfirmadoEn()).isEqualTo(antes);
        verify(vinculos, never()).save(any());
    }

    @Test
    @DisplayName("Un número que no es E.164 es un 400")
    void numeroQueNoVale() {
        assertThatThrownBy(() -> anuncios.delNumero("hola")).isInstanceOf(IllegalArgumentException.class);
    }
}
