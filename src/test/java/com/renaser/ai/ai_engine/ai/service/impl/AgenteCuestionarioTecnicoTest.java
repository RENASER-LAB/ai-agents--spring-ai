package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoRespuestas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaRespuestaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.RespuestaAbierta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El agente que califica el cuestionario técnico: cuándo llama al modelo y con qué contrato.
 *
 * <p>Dos cosas que cuestan dinero o notas si se hacen mal:
 *
 * <ul>
 *   <li><b>Entregar en blanco no se le pregunta al modelo.</b> Pasa de verdad —el reloj
 *       entrega lo que haya, y a veces no hay nada—: pedirle que califique una lista vacía
 *       paga una llamada para recibir otra lista vacía. Pero la nota sí hay que ponerla, y es
 *       todo ceros; sin eso la postulación se queda esperando una calificación que no llega.
 *   <li><b>El contrato es el de criterios, siempre.</b> Un cuestionario de vacante nace
 *       CRITERIOS por la V42: aquí no hay nada que elegir, y si un día alguien le pasara el
 *       formato de puntaje, el código dejaría de contar y empezaría a creerle al modelo.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El agente que califica el cuestionario técnico")
class AgenteCuestionarioTecnicoTest {

    private static final long POSTULACION = 7L;

    @Mock private PuenteCalificacionIa puente;
    @Mock private EjecutorAgenteIa ejecutor;

    @InjectMocks
    private AgenteCuestionarioTecnico agente;

    @Test
    @DisplayName("su código es propio, y no el del evaluador del banco")
    void suCodigoEsPropio() {
        // No es cosmético: la cola ordena su trabajo por este código, y compartirlo con el
        // EVALUADOR dejaba sin calificar al perfil integral de la misma postulación.
        assertThat(agente.codigo()).isEqualTo("EVALUADOR_TECNICO");
        assertThat(agente.codigo()).isNotEqualTo(AgenteEvaluador.CODIGO_AGENTE);
    }

    @Test
    @DisplayName("con respuestas, llama al modelo con el formato de criterios y guarda lo que vuelve")
    void conRespuestasLlamaAlModeloYGuarda() {
        when(puente.insumoRespuestasTecnicas(POSTULACION)).thenReturn(insumoCon(dosRespuestas()));
        ResultadoEvaluador loQueDijo = new ResultadoEvaluador(List.of(
                new NotaRespuestaIa(300L, null, "cuenta el arqueo con cifras",
                        "40 mil soles al día", java.math.BigDecimal.valueOf(90), false, true, true, true, true)));
        when(ejecutor.ejecutar(any(), anyString(), anyString(), any(), eq(ResultadoEvaluador.class)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(99L, loQueDijo));

        agente.ejecutar(trabajo());

        // El formato es el del banco CAZATALENTOS, reutilizado tal cual: el modelo declara
        // los criterios y el número lo cuenta el código.
        ArgumentCaptor<String> formato = ArgumentCaptor.forClass(String.class);
        verify(ejecutor).ejecutar(any(), anyString(), formato.capture(), any(),
                eq(ResultadoEvaluador.class));
        assertThat(formato.getValue()).isEqualTo(AgenteEvaluador.FORMATO_CRITERIOS);
        assertThat(formato.getValue()).contains("c1Episodio", "cumpleSenalCero");

        verify(puente).guardarNotasTecnicas(POSTULACION, 99L, loQueDijo);
        verify(puente, never()).cerrarNotaTecnica(any());
    }

    @Test
    @DisplayName("entregado en blanco: no se paga una llamada, pero la nota se pone igual")
    void entregadoEnBlancoNoLlamaAlModeloPeroCierraLaNota() {
        when(puente.insumoRespuestasTecnicas(POSTULACION)).thenReturn(insumoCon(List.of()));

        agente.ejecutar(trabajo());

        verifyNoInteractions(ejecutor);
        // Y esto es lo que impide que se quede esperando para siempre: no contestar nada es
        // un cero, y alguien tiene que escribirlo.
        verify(puente).cerrarNotaTecnica(POSTULACION);
        verify(puente, never()).guardarNotasTecnicas(any(), any(), any());
    }

    // ============ Apoyo ============

    private TrabajoIa trabajo() {
        return TrabajoIa.builder()
                .id(1L).organizacionId(1L).postulacionId(POSTULACION)
                .agenteCodigo(AgenteCuestionarioTecnico.CODIGO_AGENTE)
                .estado("EN_CURSO").creadoEn(Instant.now())
                .build();
    }

    private InsumoRespuestas insumoCon(List<RespuestaAbierta> respuestas) {
        return new InsumoRespuestas("Administrador de sedes", "DIRECCION", "CRITERIOS", respuestas);
    }

    private List<RespuestaAbierta> dosRespuestas() {
        return List.of(
                new RespuestaAbierta(300L, "ABIERTA", "¿Cuántas cajas has tenido a cargo?", null,
                        List.of(), "Tres cajas de 40 mil soles al día",
                        "número de sedes y montos", "el faltante que no encontró",
                        "No da ninguna cifra"),
                new RespuestaAbierta(301L, "ABIERTA", "Si falta plata, ¿qué haces?", null,
                        List.of(), "Cuadro vouchers uno a uno y llamo al cajero",
                        "el paso a paso", "a quién se lo dijo", "Dice «revisaría bien»"));
    }
}
