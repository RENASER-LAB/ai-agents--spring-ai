package com.renaser.ai.ai_engine.whatsapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("El celular de la cuenta (V73)")
class CelularTest {

    @ParameterizedTest(name = "«{0}» -> {1}")
    @CsvSource({
            "'987654321', +51987654321",
            "'987 654 321', +51987654321",
            "' 987-654-321 ', +51987654321",
            "'(987) 654.321', +51987654321",
            "'51 987 654 321', +51987654321",
            "'+51 987 654 321', +51987654321",
            "'0051987654321', +51987654321",
            "'+34 612 345 678', +34612345678",
            "'0034612345678', +34612345678",
            "'+1 (415) 555-2671', +14155552671"})
    @DisplayName("Perú con nueve cifras que empiezan por 9; otro país, completo con su prefijo")
    void loQueValeSeGuardaEnE164(String escrito, String e164) {
        assertThat(Celular.comoE164(escrito)).contains(e164);
    }

    @ParameterizedTest(name = "«{0}»")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "87654321", "812345678", "9876543210", "98765432a",
            "34612345678", "+51 54 123456", "+51 812 345 678", "+0123456789", "+1234567",
            "+1234567890123456", "uno dos tres", "+51 987 654 321 ext 2"})
    @DisplayName("Lo demás no vale, con el mismo mensaje que el portal")
    void loQueNoValeDaElMensajeDelPortal(String escrito) {
        assertThat(Celular.comoE164(escrito)).isEmpty();
        assertThat(Celular.MENSAJE).isEqualTo("Escribe un celular de 9 cifras que empiece por 9, o "
                + "el número completo con su prefijo");
    }

    @Test
    @DisplayName("Se enseña agrupado, y enmascarado cuando ya está conectado")
    void comoSeEnsenia() {
        assertThat(Celular.paraMostrar("+51987654321")).isEqualTo("+51 987 654 321");
        assertThat(Celular.enmascarado("+51987654321")).isEqualTo("+51 9•• ••• 321");
        assertThat(Celular.paraMostrar("+34612345678")).isEqualTo("+34612345678");
        assertThat(Celular.enmascarado("+34612345678")).isEqualTo("+346•••••678");
        assertThat(Celular.paraEnlace("+51987654321")).isEqualTo("51987654321");
        assertThat(Celular.paraMostrar(null)).isNull();
        assertThat(Celular.enmascarado(null)).isNull();
    }

    @ParameterizedTest(name = "«{0}»")
    @ValueSource(strings = {"51987654321", "+51987654321", " 51987654321"})
    @DisplayName("El bot lo manda con o sin el «+» (Meta lo manda sin él)")
    void elDelBot(String numero) {
        assertThat(Celular.delBot(numero)).contains("+51987654321");
    }

    @Test
    @DisplayName("El bot no puede mandar un número a medias")
    void elDelBotAMedias() {
        assertThat(Celular.delBot("51812345678")).isEmpty();
        assertThat(Celular.delBot(null)).isEmpty();
        assertThat(Celular.delBot("+51")).isEmpty();
    }
}
