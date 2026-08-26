package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.model.Agente;
import com.renaser.ai.ai_engine.ai.model.EjecucionIa;
import com.renaser.ai.ai_engine.ai.model.InstruccionIa;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.AgenteRepository;
import com.renaser.ai.ai_engine.ai.repository.EjecucionIaRepository;
import com.renaser.ai.ai_engine.ai.repository.InstruccionIaRepository;
import com.renaser.ai.ai_engine.ai.service.ClienteModelo;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/** Ver {@link EjecutorAgenteIa}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EjecutorAgenteIaImpl implements EjecutorAgenteIa {

    private final AgenteRepository agentes;
    private final InstruccionIaRepository instrucciones;
    private final EjecucionIaRepository ejecuciones;
    private final ClienteModelo modelo;
    private final CalculadoraCostoIa costos;
    private final JsonMapper json;

    @Override
    public <T> Ejecutado<T> ejecutar(TrabajoIa trabajo, String objetivo, String formato,
                                     Object insumo, Class<T> tipo) {
        return ejecutar(trabajo, objetivo, formato, insumo, tipo, true);
    }

    @Override
    public <T> Ejecutado<T> ejecutar(TrabajoIa trabajo, String objetivo, String formato,
                                     Object insumo, Class<T> tipo, boolean razona) {
        Agente agente = agentes.findById(trabajo.getAgenteCodigo())
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el agente " + trabajo.getAgenteCodigo()));
        // La instrucción la administra Dirección desde el panel. Si nadie publicó ninguna no
        // se improvisa un prompt: sin instrucción no hay calificación.
        InstruccionIa instruccion = instrucciones
                .findFirstByAgenteCodigoAndEsActivaTrue(trabajo.getAgenteCodigo())
                .orElseThrow(() -> new IllegalStateException(
                        "El agente %s no tiene ninguna instrucción activa: nadie ha publicado una"
                                .formatted(trabajo.getAgenteCodigo())));

        String sistema = instruccion.getTexto() + "\n\n" + formato;
        String contenido = json.writeValueAsString(insumo);
        String envio = "=== INSTRUCCIÓN ===\n" + sistema + "\n\n=== DATOS ===\n" + contenido;

        long empezo = System.nanoTime();
        RespuestaModelo respuesta = null;
        try {
            respuesta = modelo.preguntar(trabajo.getAgenteCodigo(), sistema, contenido, razona);
            T leido = leer(respuesta.texto(), tipo);
            EjecucionIa fila = guardar(trabajo, agente, instruccion, objetivo, envio, respuesta,
                    empezo, true, null);
            return new Ejecutado<>(fila.getId(), leido);
        } catch (RuntimeException e) {
            // La bitácora se escribe igual. Un fallo sin rastro es lo que impide después
            // saber si la IA está rota o si es que el candidato no dio para más.
            guardar(trabajo, agente, instruccion, objetivo, envio, respuesta, empezo, false,
                    mensaje(e));
            log.error("El agente {} falló en el trabajo {}: {}", trabajo.getAgenteCodigo(),
                    trabajo.getId(), mensaje(e));
            throw e;
        }
    }

    /**
     * Lee el JSON de la respuesta.
     *
     * <p>Se le quita antes el vallado de markdown: aunque se pide modo JSON, un modelo puede
     * devolver el objeto envuelto en ```json y eso rompe el lector por un motivo que no tiene
     * nada que ver con el contenido.
     */
    private <T> T leer(String texto, Class<T> tipo) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalStateException("El modelo devolvió una respuesta vacía");
        }
        String limpio = texto.trim();
        if (limpio.startsWith("```")) {
            int primera = limpio.indexOf('\n');
            int ultima = limpio.lastIndexOf("```");
            if (primera > 0 && ultima > primera) {
                limpio = limpio.substring(primera + 1, ultima).trim();
            }
        }
        try {
            return json.readValue(limpio, tipo);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "El modelo no devolvió el formato pedido: " + mensaje(e), e);
        }
    }

    private EjecucionIa guardar(TrabajoIa trabajo, Agente agente, InstruccionIa instruccion,
                                String objetivo, String envio, RespuestaModelo respuesta,
                                long empezo, boolean exitosa, String error) {
        return ejecuciones.save(EjecucionIa.builder()
                .trabajoIaId(trabajo.getId())
                .organizacionId(trabajo.getOrganizacionId())
                .agenteCodigo(trabajo.getAgenteCodigo())
                .versionAgente(agente.getVersion())
                .objetivo(objetivo)
                .modelo(respuesta == null ? "desconocido" : respuesta.modelo())
                .proveedor(respuesta == null ? "desconocido" : respuesta.proveedor())
                .versionModelo(respuesta == null ? null : respuesta.versionModelo())
                .instruccionIaId(instruccion.getId())
                .envio(envio)
                .respuesta(respuesta == null ? null : respuesta.texto())
                .tokensEntrada(respuesta == null ? null : respuesta.tokensEntrada())
                .tokensSalida(respuesta == null ? null : respuesta.tokensSalida())
                // El costo se pone AL CERRAR, con la tarifa vigente en ese momento: es lo
                // que hace que un cambio de precios no reescriba el pasado (pieza E). Si
                // el proveedor ni contestó no hay tokens que cobrar, y sin tarifa queda
                // vacío con su aviso — la contabilidad jamás rompe una calificación.
                .costo(respuesta == null ? null : costos.costoDe(respuesta.proveedor(),
                        respuesta.modelo(), respuesta.tokensEntrada(), respuesta.tokensSalida(),
                        Instant.now()))
                .duracionMs((int) ((System.nanoTime() - empezo) / 1_000_000L))
                .esExitosa(exitosa)
                .error(error)
                .creadoEn(Instant.now())
                .build());
    }

    private String mensaje(Throwable e) {
        String texto = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return texto.length() <= 2000 ? texto : texto.substring(0, 2000);
    }
}
