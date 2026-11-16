package com.renaser.ai.ai_engine.informe.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme;
import com.renaser.ai.ai_engine.informe.service.EtapaDelInforme;
import com.renaser.ai.ai_engine.informe.service.InformesDePrueba;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Respuesta;
import com.renaser.ai.ai_engine.informe.service.RedaccionDelInforme.Textos;
import com.renaser.ai.ai_engine.informe.service.TextosDelInforme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El redactor del informe por dentro (AC-01j): pide los textos con lo guardado de la etapa,
 * comprueba lo que vuelve y guarda solo lo que pasa, atado a la huella de esos datos. No lee ni
 * escribe ninguna nota.
 */
@DisplayName("El redactor del informe")
class AgenteRedactorInformeTest {

    private final ArmadoDelInforme armado = mock(ArmadoDelInforme.class);
    private final TextosDelInforme textos = mock(TextosDelInforme.class);
    private final EjecutorAgenteIa ejecutor = mock(EjecutorAgenteIa.class);
    private final AgenteRedactorInforme agente = new AgenteRedactorInforme(armado, textos, ejecutor);

    private final TrabajoIa trabajo = TrabajoIa.builder().id(9L).postulacionId(55L)
            .referenciaTabla("informe_prueba").agenteCodigo("REDACTOR_INFORME").build();

    private final Informe base = InformesDePrueba.prueba(List.of(new PreguntaAnexo(1,
            "¿Dónde está el problema?", "Abierta", "En los 3 primeros meses.", null, null)));

    @Test
    @DisplayName("guarda lo que pasa la comprobación y descarta la línea con una cifra inventada")
    void guardaLoQueCuadra() {
        when(armado.paraRedactar(55L, EtapaDelInforme.PRUEBA)).thenReturn(Optional.of(base));
        when(textos.vigentes(eq(55L), eq(EtapaDelInforme.PRUEBA), anyString())).thenReturn(Optional.empty());
        Respuesta respuesta = new Respuesta("Entregó el plan completo para las seis tiendas.",
                Map.of("Diagnóstico del problema", "Sobre el diagnóstico: ubica la rotación en los 3 primeros meses.",
                        "Presupuesto del plan", "Sobre el presupuesto: el costo real es de S/ 99 000."),
                List.of("Sobre el problema: lo ubica en los 3 primeros meses."));
        when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(), eq(Respuesta.class), eq(false)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(321L, respuesta));

        agente.ejecutar(trabajo);

        ArgumentCaptor<Textos> guardados = ArgumentCaptor.forClass(Textos.class);
        String huella = RedaccionDelInforme.huella(RedaccionDelInforme.insumo(base));
        verify(textos).guardar(eq(55L), eq(EtapaDelInforme.PRUEBA), eq(huella), guardados.capture(), eq(321L));
        assertThat(guardados.getValue().criterios()).containsOnlyKeys("Diagnóstico del problema");
        assertThat(guardados.getValue().preguntas()).containsExactly("Sobre el problema: lo ubica en los 3 primeros meses.");
        assertThat(guardados.getValue().descartados()).isEqualTo(1);
    }

    @Test
    @DisplayName("si los textos ya están al día con estos datos, no vuelve a pagar")
    void noPagaDosVeces() {
        when(armado.paraRedactar(55L, EtapaDelInforme.PRUEBA)).thenReturn(Optional.of(base));
        when(textos.vigentes(eq(55L), eq(EtapaDelInforme.PRUEBA), anyString()))
                .thenReturn(Optional.of(new Textos(null, Map.of(), List.of(), 0)));

        agente.ejecutar(trabajo);

        verify(ejecutor, never()).ejecutar(any(), anyString(), anyString(), any(), any(), eq(false));
    }

    @Test
    @DisplayName("si la etapa todavía no tiene informe, no hay nada que redactar")
    void sinInformeNoRedacta() {
        when(armado.paraRedactar(55L, EtapaDelInforme.PRUEBA)).thenReturn(Optional.empty());

        agente.ejecutar(trabajo);

        verify(ejecutor, never()).ejecutar(any(), anyString(), anyString(), any(), any(), eq(false));
        verify(textos, never()).guardar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("la etapa sale de la referencia del trabajo")
    void etapaDeLaReferencia() {
        assertThat(AgenteRedactorInforme.etapaDe("informe_cv")).isEqualTo(EtapaDelInforme.CV);
        assertThat(AgenteRedactorInforme.etapaDe("informe_evaluacion")).isEqualTo(EtapaDelInforme.EVALUACION);
        assertThat(AgenteRedactorInforme.etapaDe("informe_prueba")).isEqualTo(EtapaDelInforme.PRUEBA);
    }
}
