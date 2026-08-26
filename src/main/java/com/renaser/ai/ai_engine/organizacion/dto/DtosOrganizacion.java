package com.renaser.ai.ai_engine.organizacion.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Los contratos de la organización: el alta de empresas y la personalización.
public final class DtosOrganizacion {

    private DtosOrganizacion() {}

    // topeMensualIa es opcional y en texto (USD): vacío = la empresa nace sin tope y
    // Renaser se lo pone después por PUT /panel/plataforma/empresas/{id}/tope-ia.
    public record CrearEmpresa(@NotBlank String nombre, @NotBlank String codigo,
                               @NotBlank String correoAdministrador,
                               String topeMensualIa) {}

    // La respuesta lleva el enlace de la invitación del primer administrador, por lo
    // mismo que las invitaciones normales: quien da el alta puede vérselo llegar.
    public record EmpresaCreada(Long id, Long invitacionId, String urlInvitacion,
                                Instant invitacionVenceEn) {}

    public record EmpresaPanel(Long id, String codigo, String nombre, boolean esActiva,
                               Instant creadoEn) {}

    // Qué tiene personalizado la organización de quien pregunta: una casilla por bandera.
    public record Personalizacion(boolean bancoPropio, boolean pesosPropios,
                                  boolean plantillasEvaluacionPropias,
                                  boolean pruebasPuestoPropias) {}

    // El consumo de IA de un mes, por organización y desglosado por agente (pieza E):
    // contesta la pregunta útil — ¿el dinero se va en leer currículums o en calificar
    // exámenes? Las llamadas sin costo (sin tarifa, o sin conteo de tokens) suman cero
    // en dinero pero se cuentan: el hueco tiene que verse.
    public record ConsumoAgente(String agenteCodigo, BigDecimal costo, long tokensEntrada,
                                long tokensSalida, long llamadas) {}

    public record ConsumoEmpresa(Long organizacionId, String nombre, BigDecimal costoTotal,
                                 List<ConsumoAgente> porAgente) {}
}
