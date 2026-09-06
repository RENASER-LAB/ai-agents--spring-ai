package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfil.service.PuenteLecturaCvPerfil;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El mismo agente lee dos currículums que llegan por puertas distintas: el de una postulación
 * y el que el candidato subió a su perfil.
 *
 * <p><b>Por qué un solo agente y no dos.</b> La tarea es idéntica —el mismo objetivo, el mismo
 * formato, la misma instrucción activa—, así que un agente nuevo obligaría a sembrar su fila
 * en {@code agente} y su {@code instruccion_ia} para acabar mandando exactamente el mismo
 * prompt. Lo que cambia es de dónde sale el texto y dónde se guarda la ficha, y eso son dos
 * puentes.
 *
 * <p>Lo que se protege aquí es que las dos puertas <b>no se crucen</b>: la ficha de un
 * currículum del perfil escrita contra una postulación acabaría en el expediente de otra
 * persona.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El agente que lee el currículum")
class AgenteDatosCvTest {

    private static final long POSTULACION = 12L;
    private static final long LECTURA = 77L;
    private static final long EJECUCION = 500L;

    @Mock private PuenteCalificacionIa puente;
    @Mock private PuenteLecturaCvPerfil puenteDelPerfil;
    @Mock private EjecutorAgenteIa ejecutor;

    @InjectMocks
    private AgenteDatosCv agente;

    private final ResultadoDatos ficha = new ResultadoDatos(
            "Camila Torres", null, null, null, null, null, null, null, null, null,
            null, null, null, null);

    private void elModeloContesta() {
        when(ejecutor.<ResultadoDatos>ejecutar(any(), anyString(), anyString(), any(),
                eq(ResultadoDatos.class), anyBoolean()))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(EJECUCION, ficha));
    }

    @Test
    @DisplayName("El de una postulación se lee y se guarda contra esa postulación")
    void elDeUnaPostulacion() {
        TrabajoIa trabajo = TrabajoIa.builder().id(1L).postulacionId(POSTULACION)
                .agenteCodigo(AgenteDatosCv.CODIGO_AGENTE).modo("FINA").build();
        when(puente.insumoDatos(POSTULACION)).thenReturn(new InsumoDatos("Analista", "texto"));
        elModeloContesta();

        agente.ejecutar(trabajo);

        verify(puente).guardarDatos(POSTULACION, EJECUCION, ficha);
        verifyNoInteractions(puenteDelPerfil);
    }

    @Test
    @DisplayName("El del perfil se lee por su lectura, y NO toca la puerta de las postulaciones")
    void elDelPerfil() {
        // La comprobación que importa es la segunda: sin el `return` que corta arriba, este
        // trabajo seguiría hasta `insumoDatos(null)` y escribiría la ficha contra la
        // postulación vacía.
        TrabajoIa trabajo = TrabajoIa.builder().id(2L)
                .referenciaTabla(AgenteDatosCv.DEL_PERFIL).referenciaId(LECTURA)
                .agenteCodigo(AgenteDatosCv.CODIGO_AGENTE).modo("FINA").build();
        when(puenteDelPerfil.insumo(LECTURA)).thenReturn(new InsumoDatos(null, "texto del perfil"));
        elModeloContesta();

        agente.ejecutar(trabajo);

        verify(puenteDelPerfil).guardar(LECTURA, EJECUCION, ficha);
        verifyNoInteractions(puente);
    }

    @Test
    @DisplayName("Nunca razona: ni por una puerta ni por la otra")
    void nuncaRazona() {
        // No hay nada que deliberar al copiar datos de un papel, y razonar cuesta tokens en
        // cada currículum leído.
        TrabajoIa trabajo = TrabajoIa.builder().id(3L)
                .referenciaTabla(AgenteDatosCv.DEL_PERFIL).referenciaId(LECTURA).build();
        when(puenteDelPerfil.insumo(LECTURA)).thenReturn(new InsumoDatos(null, "texto"));
        elModeloContesta();

        agente.ejecutar(trabajo);

        verify(ejecutor).ejecutar(eq(trabajo), anyString(), anyString(), any(),
                eq(ResultadoDatos.class), eq(false));
    }

    @Test
    @DisplayName("Se anuncia con el código que la cola conoce")
    void suCodigo() {
        assertThat(agente.codigo()).isEqualTo(AgenteDatosCv.CODIGO_AGENTE);
    }
}
