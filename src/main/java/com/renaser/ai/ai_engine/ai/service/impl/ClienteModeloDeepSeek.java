package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.service.ClienteModelo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La conversación real con DeepSeek para los tres agentes de calificación.
 *
 * <p><b>Se pide JSON, no texto libre.</b> DeepSeek admite modo JSON pero no esquema estricto:
 * garantiza que la salida sea JSON válido, no que respete el contrato. Por eso el formato
 * exacto que se espera va escrito en la instrucción, y quien lee la respuesta comprueba campo
 * por campo antes de guardar nada. El modo JSON exige además que la palabra «json» aparezca
 * en el mensaje; la plantilla de formato de cada agente la incluye siempre.
 *
 * <p><b>Cada fallo se cuenta de una manera distinta, y eso importa.</b> Antes cualquier
 * problema —clave mal puesta, respuesta truncada, proveedor caído— llegaba al registro como
 * el mismo mensaje genérico, y desde fuera era imposible saber cuál de los tres era. Ahora
 * cada uno dice qué pasó y qué hay que tocar:
 *
 * <ul>
 *   <li><b>No autenticado.</b> La clave no llegó o no vale. No es un fallo pasajero:
 *       reintentar no arregla nada, hay que poner la clave.
 *   <li><b>Respuesta vacía por truncamiento.</b> La trampa de esta familia de modelos:
 *       <b>razonan, y ese razonamiento gasta presupuesto de salida</b>. Si se agota, el
 *       contenido vuelve vacío con motivo de cierre «length» — y desde el código se parece
 *       exactamente a un proveedor caído, aunque la causa sea la contraria: el modelo
 *       trabajó de más. Se arregla subiendo {@code renaser.ai.chat.max-tokens}.
 *   <li><b>Tiempo agotado.</b> Tardó más de lo que espera el cliente HTTP.
 *   <li><b>Error del proveedor.</b> Todo lo demás.
 * </ul>
 */
@Component
@Slf4j
public class ClienteModeloDeepSeek implements ClienteModelo {

    private static final String PROVEEDOR = "deepseek";
    private static final ResponseFormat JSON = ResponseFormat.builder()
            .type(ResponseFormat.Type.JSON_OBJECT)
            .build();

    private final ChatClient chatClient;
    private final String modelo;
    private final String modeloRapido;
    private final Integer maxTokens;

    public ClienteModeloDeepSeek(ChatClient chatClient,
                                 @Value("${renaser.ai.chat.default-model}") String modelo,
                                 @Value("${renaser.ai.chat.modelo-rapido}") String modeloRapido,
                                 @Value("${renaser.ai.chat.max-tokens}") Integer maxTokens) {
        this.chatClient = chatClient;
        this.modelo = modelo;
        this.modeloRapido = modeloRapido;
        this.maxTokens = maxTokens;
    }

    @Override
    public RespuestaModelo preguntar(String agenteCodigo, String instruccion, String contenido) {
        return preguntar(agenteCodigo, instruccion, contenido, true);
    }

    @Override
    public RespuestaModelo preguntar(String agenteCodigo, String instruccion, String contenido,
                                     boolean razona) {
        ChatResponse respuesta = llamar(agenteCodigo, instruccion, contenido,
                razona ? modelo : modeloRapido);

        if (respuesta == null || respuesta.getResult() == null) {
            throw new IllegalStateException(
                    "El proveedor %s no devolvió ningún resultado para el agente %s"
                            .formatted(PROVEEDOR, agenteCodigo));
        }

        String texto = respuesta.getResult().getOutput().getText();
        String motivoCierre = motivoCierre(respuesta);
        Usage uso = respuesta.getMetadata() == null ? null : respuesta.getMetadata().getUsage();

        if (texto == null || texto.isBlank()) {
            throw new IllegalStateException(explicarVacio(agenteCodigo, motivoCierre, uso));
        }

        String modeloReal = respuesta.getMetadata() == null ? null : respuesta.getMetadata().getModel();
        return new RespuestaModelo(
                texto,
                modeloReal == null || modeloReal.isBlank() ? modelo : modeloReal,
                PROVEEDOR,
                respuesta.getMetadata() == null ? null : respuesta.getMetadata().getId(),
                uso == null ? null : uso.getPromptTokens(),
                uso == null ? null : uso.getCompletionTokens());
    }

    /**
     * El modelo que se pide viaja como parámetro y no se toma del campo: es lo que
     * distingue una pasada de la otra.
     *
     * <p><b>Lo que se registra en la bitácora es el modelo que el proveedor dice haber
     * usado</b>, no el que se pidió. Los dos nombres pueden resolver al mismo modelo con
     * distinto modo de trabajo, y entonces la bitácora enseña el mismo nombre en las dos
     * pasadas aunque la respuesta y el tiempo sean muy distintos. Los tokens de salida sí
     * lo delatan: es ahí donde se ve si razonó.
     */
    private ChatResponse llamar(String agenteCodigo, String instruccion, String contenido,
                                String queModelo) {
        try {
            return chatClient.prompt()
                    .system(instruccion)
                    .user(contenido)
                    .options(DeepSeekChatOptions.builder()
                            .responseFormat(JSON)
                            .model(queModelo)
                            .maxTokens(maxTokens))
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            throw new IllegalStateException(explicarFallo(agenteCodigo, e), e);
        }
    }

    /**
     * Traduce el error del cliente HTTP a algo que se pueda leer en {@code ejecucion_ia}.
     *
     * <p>Se mira el texto del error y no una clase de excepción concreta a propósito: Spring
     * AI envuelve el fallo del proveedor en su propia excepción y la clase de dentro cambia
     * de una versión a otra, mientras que el código de estado sigue estando en el mensaje.
     */
    private String explicarFallo(String agenteCodigo, RuntimeException e) {
        String causa = cadenaDeCausas(e);
        String prefijo = "El agente %s no pudo hablar con %s: ".formatted(agenteCodigo, PROVEEDOR);

        if (contiene(causa, "401", "Authentication Fails", "invalid_api_key", "Unauthorized")) {
            return prefijo + "el proveedor rechazó la clave (401). No es un fallo pasajero: "
                    + "reintentar no lo arregla. Revisa spring.ai.deepseek.api-key en "
                    + "application-secrets.yaml.";
        }
        if (contiene(causa, "402", "Insufficient Balance")) {
            return prefijo + "la cuenta del proveedor no tiene saldo (402).";
        }
        if (contiene(causa, "429", "Too Many Requests", "rate limit")) {
            return prefijo + "el proveedor está limitando el ritmo (429). Es pasajero: "
                    + "el reintento tiene sentido.";
        }
        if (contiene(causa, "Timeout", "timed out", "timeout", "ReadTimeout")) {
            return prefijo + "se agotó el tiempo de espera. El cliente espera hasta "
                    + "spring.http.client.read-timeout; una calificación larga puede pasarse.";
        }
        if (contiene(causa, "ConnectException", "UnknownHost", "Connection refused")) {
            return prefijo + "no se pudo abrir la conexión. O no hay red, o el proveedor "
                    + "está caído.";
        }
        return prefijo + recorte(causa);
    }

    private String explicarVacio(String agenteCodigo, String motivoCierre, Usage uso) {
        if ("length".equalsIgnoreCase(motivoCierre)) {
            // No es que el modelo no supiera responder: es que se le acabó el papel.
            return ("El agente %s se quedó sin presupuesto de tokens: el modelo agotó los %d de "
                    + "max-tokens razonando y no llegó a escribir la respuesta (motivo de cierre "
                    + "«length», %s tokens de salida). Esto NO es un fallo del proveedor: se "
                    + "arregla subiendo renaser.ai.chat.max-tokens o acortando lo que se le manda.")
                    .formatted(agenteCodigo, maxTokens,
                            uso == null || uso.getCompletionTokens() == null
                                    ? "?" : uso.getCompletionTokens());
        }
        return ("El agente %s recibió una respuesta vacía de %s (motivo de cierre «%s»). "
                + "Puede ser un tropiezo puntual del proveedor: el reintento tiene sentido.")
                .formatted(agenteCodigo, PROVEEDOR, motivoCierre == null ? "sin motivo" : motivoCierre);
    }

    private String motivoCierre(ChatResponse respuesta) {
        ChatGenerationMetadata metadatos = respuesta.getResult().getMetadata();
        return metadatos == null ? null : metadatos.getFinishReason();
    }

    private String cadenaDeCausas(Throwable e) {
        StringBuilder texto = new StringBuilder();
        Throwable actual = e;
        int vueltas = 0;
        while (actual != null && vueltas++ < 8) {
            texto.append(actual.getClass().getSimpleName()).append(": ")
                    .append(actual.getMessage() == null ? "" : actual.getMessage()).append(" | ");
            actual = actual.getCause() == actual ? null : actual.getCause();
        }
        return texto.toString();
    }

    private boolean contiene(String texto, String... trozos) {
        for (String trozo : trozos) {
            if (texto.contains(trozo)) return true;
        }
        return false;
    }

    private String recorte(String texto) {
        return texto.length() <= 500 ? texto : texto.substring(0, 500) + "…";
    }
}
