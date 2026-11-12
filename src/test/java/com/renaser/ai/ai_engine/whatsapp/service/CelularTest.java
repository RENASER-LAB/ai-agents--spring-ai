package com.renaser.ai.ai_engine.whatsapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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

    /**
     * Los casos del contrato que comparte con el portal ({@code docs/contratos/celular-casos.json},
     * igual que {@code src/dominio/celularCasos.json} allí, donde lo comprueba
     * {@code celular.test.ts}). Si una regla cambia y la otra no, falla una de las dos pruebas.
     */
    static Stream<Arguments> casosDelContrato() throws IOException {
        JsonNode contrato = new ObjectMapper().readTree(
                Files.readString(Path.of("docs/contratos/celular-casos.json")));
        List<Arguments> casos = new ArrayList<>();
        contrato.get("casos").forEach(caso -> casos.add(Arguments.of(caso.get("escrito").asString(),
                caso.get("e164").isNull() ? null : caso.get("e164").asString())));
        return casos.stream();
    }

    @ParameterizedTest(name = "«{0}» -> {1}")
    @MethodSource("casosDelContrato")
    @DisplayName("Da lo mismo que celular.ts del portal para cada caso del contrato compartido")
    void loMismoQueElPortal(String escrito, String e164) {
        assertThat(Celular.comoE164(escrito)).isEqualTo(Optional.ofNullable(e164));
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
