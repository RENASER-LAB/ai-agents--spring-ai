package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.BusquedaEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.DeUnaPersona;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.Encontrado;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.LineaVigente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Lo que una fila del ranking dice de «Qué buscar en el CV» (AC-16, AC-18). */
@DisplayName("El ranking: «Qué buscar en el CV» de una fila")
class BusquedaDeLaFilaTest {

    private final List<LineaVigente> lineas = List.of(
            new LineaVigente(1L, 1, "Experiencia en cocina saludable", 2),
            new LineaVigente(2L, 2, "Ha dirigido un equipo de cocina", 2),
            new LineaVigente(3L, 3, "Controla costos y mermas", 2),
            new LineaVigente(4L, 4, "Certificado BPM", 2));

    private final DeUnaPersona lucia = new DeUnaPersona("TERMINADA", null, Map.of(
            1L, new Encontrado("TIENE", "Cocinera principal en Verde Bowl", "Tres años.", null),
            2L, new Encontrado("TIENE", "Jefa de partida a cargo de 6", null, null),
            3L, new Encontrado("TIENE", "Bajé la merma", null, null),
            4L, new Encontrado("MENCIONA", null, "Escribe «BPM».", "¿Tienes vigente el certificado?")));

    @Test
    @DisplayName("Terminada: el recuento, el estado de cada línea y el detalle con permiso de ficha")
    void terminadaConDetalle() {
        BusquedaEnElCv suya = ServicioPerfilIntegralPanelImpl.busquedaDeLaFila(lineas, lucia,
                "TERMINADA", new BigDecimal("87.5"), new BigDecimal("88"), true);

        assertThat(suya.estado()).isEqualTo("TERMINADA");
        assertThat(suya.tiene()).isEqualTo(3);
        assertThat(suya.menciona()).isEqualTo(1);
        assertThat(suya.total()).isEqualTo(4);
        assertThat(suya.lineas()).extracting(l -> l.estado())
                .containsExactly("TIENE", "TIENE", "TIENE", "MENCIONA");
        assertThat(suya.lineas().get(3).pregunta()).isEqualTo("¿Tienes vigente el certificado?");
        assertThat(suya.lineas().get(0).frase()).isEqualTo("Cocinera principal en Verde Bowl");
    }

    @Test
    @DisplayName("Sin el permiso de abrir la ficha viajan los estados, no las frases ni las preguntas")
    void sinPermisoDeFicha() {
        BusquedaEnElCv suya = ServicioPerfilIntegralPanelImpl.busquedaDeLaFila(lineas, lucia,
                "TERMINADA", null, null, false);

        assertThat(suya.lineas()).extracting(l -> l.estado()).doesNotContainNull();
        assertThat(suya.lineas()).allSatisfy(l -> {
            assertThat(l.frase()).isNull();
            assertThat(l.nota()).isNull();
            assertThat(l.pregunta()).isNull();
        });
    }

    @Test
    @DisplayName("Sin nada guardado, lo dice la cola: buscando, fallida o pendiente (también en espera)")
    void segunLaCola() {
        assertThat(ServicioPerfilIntegralPanelImpl.busquedaDeLaFila(lineas, null, "BUSCANDO", null,
                null, true).estado()).isEqualTo("BUSCANDO");
        assertThat(ServicioPerfilIntegralPanelImpl.busquedaDeLaFila(lineas, null, "FALLIDA", null,
                null, true).estado()).isEqualTo("FALLIDA");
        assertThat(ServicioPerfilIntegralPanelImpl.busquedaDeLaFila(lineas, null, "EN_ESPERA", null,
                null, true).estado()).isEqualTo("PENDIENTE");
        BusquedaEnElCv nunca = ServicioPerfilIntegralPanelImpl.busquedaDeLaFila(lineas, null, null,
                null, null, true);
        assertThat(nunca.estado()).isEqualTo("PENDIENTE");
        assertThat(nunca.lineas()).extracting(l -> l.texto()).hasSize(4);
        assertThat(nunca.lineas()).extracting(l -> l.estado()).containsOnlyNulls();
    }

    @Test
    @DisplayName("El CV sin texto manda sobre lo que diga la cola")
    void sinTexto() {
        BusquedaEnElCv suya = ServicioPerfilIntegralPanelImpl.busquedaDeLaFila(lineas,
                new DeUnaPersona("SIN_TEXTO", "El PDF no tiene texto", Map.of()), "TERMINADA",
                null, null, true);
        assertThat(suya.estado()).isEqualTo("SIN_TEXTO");
        assertThat(suya.tiene()).isZero();
    }
}
