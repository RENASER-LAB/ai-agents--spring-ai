package com.renaser.ai.ai_engine.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El motor deja los dos starters en el classpath a propósito: DeepSeek sirve el chat y
 * Gemini (Google GenAI) sirve los embeddings, porque DeepSeek no expone ese endpoint y ya
 * no se quiere depender de un modelo local (Ollama).
 * <p>
 * Ese arreglo depende enteramente de spring.ai.model.chat y spring.ai.model.embedding.text. Si
 * alguien los borra o los invierte, el síntoma no es un error de arranque claro: el chat se
 * va callado a un modelo por defecto y vuelven los tiempos que motivaron la migración.
 * Este test fija ese contrato.
 * <p>
 * Usa ApplicationContextRunner en vez de @SpringBootTest para no arrastrar JPA ni pgvector,
 * que necesitan un Postgres vivo.
 */
class ChatProviderRoutingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestClientAutoConfiguration.class,
                    ToolCallingAutoConfiguration.class,
                    DeepSeekChatAutoConfiguration.class,
                    GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
                    GoogleGenAiTextEmbeddingAutoConfiguration.class))
            .withPropertyValues(
                    "spring.ai.model.chat=deepseek",
                    "spring.ai.model.embedding.text=google-genai",
                    "spring.ai.deepseek.api-key=test-key",
                    "spring.ai.deepseek.chat.options.model=deepseek-v4-pro",
                    "spring.ai.google.genai.embedding.api-key=test-key",
                    "spring.ai.google.genai.embedding.text.model=gemini-embedding-2",
                    "spring.ai.google.genai.embedding.text.dimensions=1536");

    @Test
    void elChatLoSirveDeepSeekYNoOllama() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context).getBean(ChatModel.class).isInstanceOf(DeepSeekChatModel.class);
        });
    }

    @Test
    void losEmbeddingsLosSirveGeminiYNoOllama() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context).getBean(EmbeddingModel.class).isInstanceOf(GoogleGenAiTextEmbeddingModel.class);
        });
    }
}
