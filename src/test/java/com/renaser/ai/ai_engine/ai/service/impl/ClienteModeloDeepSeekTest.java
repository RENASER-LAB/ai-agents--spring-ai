package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.prompt.ChatOptionsFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Qué modelo queda escrito en la bitácora (pieza E).
 *
 * <p>Lo que se protege: el nombre que viaja a {@code ejecucion_ia.modelo} es contra el
 * que se busca la tarifa, y por eso no puede mentir. Si el proveedor dice qué modelo usó,
 * manda él; si no lo dice, se anota <b>el pedido en esta llamada</b> — no el campo por
 * defecto. Antes el hueco caía siempre en el modelo que razona, y una pasada rápida sin
 * metadatos quedaba anotada (y tarifada) como la cara.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El cliente de DeepSeek y el modelo que anota en la bitácora")
class ClienteModeloDeepSeekTest {

    private static final String RAZONA = "deepseek-flash";
    private static final String RAPIDO = "deepseek-chat";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;
    @Mock
    private ChatOptionsFactory opciones;

    private ClienteModeloDeepSeek cliente() {
        return new ClienteModeloDeepSeek(chatClient, opciones, RAZONA, RAPIDO, 4000);
    }

    private void elProveedorContesta(String modeloReportado) {
        ChatResponse respuesta = new ChatResponse(
                List.of(new Generation(new AssistantMessage("{\"ok\":true}"))),
                ChatResponseMetadata.builder()
                        .model(modeloReportado)
                        .usage(new DefaultUsage(1200, 340))
                        .build());
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any())
                .call().chatResponse()).thenReturn(respuesta);
    }

    @Test
    @DisplayName("si el proveedor no reporta modelo, la bitácora guarda el PEDIDO de esta llamada")
    void sinModeloReportadoSeAnotaElPedido() {
        elProveedorContesta("");

        RespuestaModelo rapida = cliente().preguntar("DATOS_CV", "instrucción json", "datos", false);
        assertThat(rapida.modelo()).isEqualTo(RAPIDO);

        RespuestaModelo pensada = cliente().preguntar("EVALUADOR", "instrucción json", "datos", true);
        assertThat(pensada.modelo()).isEqualTo(RAZONA);
    }

    @Test
    @DisplayName("si el proveedor sí dice qué modelo usó, manda él sobre lo pedido")
    void elModeloReportadoManda() {
        elProveedorContesta("deepseek-flash");

        RespuestaModelo respuesta = cliente().preguntar("EVALUADOR", "instrucción json", "datos",
                false);

        // Este es el caso real desde el 10/09/2026, no un supuesto: se pidió la pasada
        // RÁPIDA ('deepseek-chat') y el proveedor contestó 'deepseek-flash', porque el
        // nombre pedido es un alias del nuevo. La bitácora dice la verdad del proveedor, y
        // la tarifa se busca por ella — por eso la V57 siembra 'deepseek-flash' y no el
        // nombre que está en la configuración.
        assertThat(respuesta.modelo()).isEqualTo("deepseek-flash");
        assertThat(respuesta.proveedor()).isEqualTo("deepseek");
        assertThat(respuesta.tokensEntrada()).isEqualTo(1200);
        assertThat(respuesta.tokensSalida()).isEqualTo(340);
    }
}
