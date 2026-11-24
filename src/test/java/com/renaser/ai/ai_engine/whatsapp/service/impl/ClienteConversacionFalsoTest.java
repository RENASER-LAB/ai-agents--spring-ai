package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.FalloDelBot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El bot falso contesta {@code /v1/ex/vinculaciones} como el de verdad (V92): valida el cuerpo,
 * apunta la llamada en su registro y sabe contestar {@code 404 SIN_CONVERSACION} o caído.
 */
@DisplayName("El bot falso y las vinculaciones")
class ClienteConversacionFalsoTest {

    private final ClienteConversacionFalso bot = new ClienteConversacionFalso();

    private static ClienteConversacion.Vinculacion vinculacion(String telefono, String motivo,
                                                               List<ClienteConversacion.PostulacionAVincular> lista) {
        return new ClienteConversacion.Vinculacion(telefono, "Lucía", motivo, lista,
                new ClienteConversacion.ConsentimientoDeLaVinculacion("Al activarlo aceptas…", "1.0", Instant.now()));
    }

    private static List<ClienteConversacion.PostulacionAVincular> dos() {
        return List.of(new ClienteConversacion.PostulacionAVincular("pw_1", "Analista de Datos"),
                new ClienteConversacion.PostulacionAVincular("pw_2", "Analista"));
    }

    @Test
    @DisplayName("Vincula todas las que recibe, las devuelve y lo apunta en el registro del número")
    void vincula() {
        List<String> vinculadas = bot.vincular(vinculacion("+51987654321", "ACTIVACION", dos()));

        assertThat(vinculadas).containsExactly("pw_1", "pw_2");
        assertThat(bot.registroDe("+51987654321")).singleElement().satisfies(l -> {
            assertThat(l.tipo()).isEqualTo(ClienteConversacionFalso.VINCULACION);
            assertThat(l.cuerpo()).containsEntry("motivo", "ACTIVACION");
        });
    }

    @Test
    @DisplayName("Un cuerpo que no cumple el contrato es un 400 validacion: sin postulaciones, motivo raro o mal teléfono")
    void validaElCuerpo() {
        assertThatThrownBy(() -> bot.vincular(vinculacion("+51987654321", "ACTIVACION", List.of())))
                .isInstanceOfSatisfying(FalloDelBot.class, f -> assertThat(f.codigo()).isEqualTo("validacion"));
        assertThatThrownBy(() -> bot.vincular(vinculacion("+51987654321", "OTRA_COSA", dos())))
                .isInstanceOf(FalloDelBot.class);
        assertThatThrownBy(() -> bot.vincular(vinculacion("987654321", "ACTIVACION", dos())))
                .isInstanceOf(FalloDelBot.class);
        assertThat(bot.registro()).isEmpty();
    }

    @Test
    @DisplayName("Un número que el bot no conoce: 404 SIN_CONVERSACION; caído: reintentable")
    void sinConversacionYCaido() {
        bot.sinConversacion("+51987654321", true);
        assertThatThrownBy(() -> bot.vincular(vinculacion("+51987654321", "ACTIVACION", dos())))
                .isInstanceOfSatisfying(FalloDelBot.class, f -> {
                    assertThat(f.esSinConversacion()).isTrue();
                    assertThat(f.esReintentable()).isFalse();
                });
        bot.sinConversacion("+51987654321", false);

        bot.fallar(true);
        assertThatThrownBy(() -> bot.vincular(vinculacion("+51987654321", "NUEVA_POSTULACION", dos())))
                .isInstanceOfSatisfying(FalloDelBot.class, f -> assertThat(f.esReintentable()).isTrue());
        bot.fallar(false);
        assertThat(bot.vincular(vinculacion("+51987654321", "NUEVA_POSTULACION", dos()))).hasSize(2);
    }

    @Test
    @DisplayName("Con el número dado de baja: NUEVA_POSTULACION es 409 SUPRIMIDO; ACTIVACION la levanta y vincula")
    void suprimido() {
        bot.suprimido("+51987654321", true);
        assertThatThrownBy(() -> bot.vincular(vinculacion("+51987654321", "NUEVA_POSTULACION", dos())))
                .isInstanceOfSatisfying(FalloDelBot.class, f -> {
                    assertThat(f.esSuprimido()).isTrue();
                    assertThat(f.codigo()).isEqualTo("SUPRIMIDO");
                });

        assertThat(bot.vincular(vinculacion("+51987654321", "ACTIVACION", dos()))).hasSize(2);
        assertThat(bot.vincular(vinculacion("+51987654321", "NUEVA_POSTULACION", dos()))).hasSize(2);
    }
}
