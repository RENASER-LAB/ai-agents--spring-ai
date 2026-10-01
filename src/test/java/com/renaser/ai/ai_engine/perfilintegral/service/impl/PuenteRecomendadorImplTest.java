package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.DatosDeLaVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos.CriterioCalculado;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos.PreguntaCalculada;
import com.renaser.ai.ai_engine.perfilintegral.service.DatosDeLaVacanteParaIa;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El puente del RECOMENDADOR (V66): arma lo que ve el agente con lo que ya hay en el
 * borrador (o, si no hay, en la publicada), guarda la propuesta ya validada y deja escrito
 * por qué falló. Nada de esto toca el borrador.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El puente del recomendador de preguntas")
class PuenteRecomendadorImplTest {

    private static final long ORGANIZACION = 1L;
    private static final long VACANTE = 40L;

    @Mock private PropuestaPreguntasRepository propuestas;
    @Mock private VacanteRepository vacantes;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private CalificacionPorPuntos porPuntos;
    @Mock private DatosDeLaVacanteParaIa datosDeLaVacante;

    private PuenteRecomendadorImpl puente;

    @BeforeEach
    void armar() {
        puente = new PuenteRecomendadorImpl(propuestas, vacantes, versionesBanco, porPuntos,
                datosDeLaVacante);
    }

    private PropuestaPreguntas propuesta(String estado) {
        PropuestaPreguntas p = PropuestaPreguntas.builder().id(3L).organizacionId(ORGANIZACION)
                .vacanteId(VACANTE).estado(estado).puntosQueFaltan(30)
                .indicacion("Más peso a la atención al cliente").build();
        when(propuestas.findFirstByVacanteIdOrderByIdDesc(VACANTE)).thenReturn(Optional.of(p));
        return p;
    }

    private Vacante vacanteExiste() {
        Vacante v = Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION).titulo("Cajero").build();
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(v));
        when(datosDeLaVacante.de(v)).thenReturn(new DatosDeLaVacante("Cajero", null, null, null,
                null, null, null, null, null, null, null));
        return v;
    }

    private static PreguntaCalculada calculada(String tipo, String enunciado, int maximo) {
        Pregunta p = Pregunta.builder().tipo(tipo).enunciado(enunciado).build();
        return new PreguntaCalculada(p, List.of(), null, null, maximo, null, false);
    }

    // ---------------------------------------------------------------- insumo

    @Test
    @DisplayName("Sin ninguna propuesta pedida no hay insumo")
    void insumoSinPropuesta() {
        when(propuestas.findFirstByVacanteIdOrderByIdDesc(VACANTE)).thenReturn(Optional.empty());

        assertThat(puente.insumo(VACANTE)).isNull();
        verifyNoInteractions(vacantes, versionesBanco, porPuntos);
    }

    @Test
    @DisplayName("Si la última propuesta ya está lista, no hay nada pedido esperando")
    void insumoConLaUltimaYaLista() {
        propuesta(PropuestaPreguntas.LISTA);

        assertThat(puente.insumo(VACANTE)).isNull();
        verifyNoInteractions(vacantes);
    }

    @Test
    @DisplayName("Si la vacante ya no existe, el trabajo falla con un motivo claro")
    void insumoSinVacante() {
        propuesta(PropuestaPreguntas.PEDIDA);
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.insumo(VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La vacante 40 ya no existe");
    }

    @Test
    @DisplayName("Con borrador, el agente ve sus criterios y las preguntas sin criterio con sus puntos")
    void insumoConBorrador() {
        propuesta(PropuestaPreguntas.PEDIDA);
        vacanteExiste();
        VersionBanco borrador = VersionBanco.builder().id(70L).estado("BORRADOR").build();
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.of(borrador));
        CriterioBanco criterio = CriterioBanco.builder().id(8L).nombre("Servicio")
                .queEvalua("Trato al cliente").build();
        CriterioCalculado calculado = new CriterioCalculado(criterio, 40, BigDecimal.ZERO, 20,
                BigDecimal.ZERO, 20, false, List.of(calculada(ReglasDePuntos.ABIERTA, "¿Un caso?", 20)));
        when(porPuntos.calcular(70L, null)).thenReturn(new CalificacionPorPuntos.Resultado(borrador,
                List.of(calculado), List.of(calculada(ReglasDePuntos.ESCALA, "¿Cuánto?", 10)),
                BigDecimal.ZERO, false));

        InsumoRecomendador insumo = puente.insumo(VACANTE);

        assertThat(insumo.vacante().titulo()).isEqualTo("Cajero");
        assertThat(insumo.puntosQueFaltan()).isEqualTo(30);
        assertThat(insumo.indicacion()).isEqualTo("Más peso a la atención al cliente");
        assertThat(insumo.tiposPermitidos()).containsExactly(ReglasDePuntos.ABIERTA,
                ReglasDePuntos.OPCION_UNICA, ReglasDePuntos.OPCION_MULTIPLE, ReglasDePuntos.ESCALA);
        assertThat(insumo.criteriosDelBorrador()).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(8L);
            assertThat(c.nombre()).isEqualTo("Servicio");
            assertThat(c.queEvalua()).isEqualTo("Trato al cliente");
            assertThat(c.puntos()).isEqualTo(40);
            assertThat(c.preguntas()).singleElement().satisfies(p -> {
                assertThat(p.tipo()).isEqualTo(ReglasDePuntos.ABIERTA);
                assertThat(p.enunciado()).isEqualTo("¿Un caso?");
                assertThat(p.puntos()).isEqualTo(20);
            });
        });
        assertThat(insumo.preguntasSinCriterio()).singleElement().satisfies(p -> {
            assertThat(p.tipo()).isEqualTo(ReglasDePuntos.ESCALA);
            assertThat(p.puntos()).isEqualTo(10);
        });
        verify(versionesBanco, never()).preguntasPropiasDe(VACANTE, "PUBLICADA");
    }

    @Test
    @DisplayName("Sin borrador toma la publicada")
    void insumoConLaPublicada() {
        propuesta(PropuestaPreguntas.PEDIDA);
        vacanteExiste();
        VersionBanco publicada = VersionBanco.builder().id(71L).estado("PUBLICADA").build();
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.of(publicada));
        when(porPuntos.calcular(71L, null)).thenReturn(new CalificacionPorPuntos.Resultado(publicada,
                List.of(), List.of(calculada(ReglasDePuntos.OPCION_UNICA, "¿Cuál?", 5)),
                BigDecimal.ZERO, false));

        InsumoRecomendador insumo = puente.insumo(VACANTE);

        assertThat(insumo.criteriosDelBorrador()).isEmpty();
        assertThat(insumo.preguntasSinCriterio()).extracting("enunciado").containsExactly("¿Cuál?");
    }

    @Test
    @DisplayName("Sin borrador ni publicada, el agente empieza de cero")
    void insumoSinNada() {
        propuesta(PropuestaPreguntas.PEDIDA);
        vacanteExiste();
        when(versionesBanco.preguntasPropiasDe(VACANTE, "BORRADOR")).thenReturn(Optional.empty());
        when(versionesBanco.preguntasPropiasDe(VACANTE, "PUBLICADA")).thenReturn(Optional.empty());

        InsumoRecomendador insumo = puente.insumo(VACANTE);

        assertThat(insumo.criteriosDelBorrador()).isEmpty();
        assertThat(insumo.preguntasSinCriterio()).isEmpty();
        verifyNoInteractions(porPuntos);
    }

    // ---------------------------------------------------------------- guardarPropuesta

    @Test
    @DisplayName("Si ya no hay ninguna pedida, la propuesta que llega se descarta")
    void guardarSinPedida() {
        propuesta(PropuestaPreguntas.FALLIDA);

        puente.guardarPropuesta(VACANTE, new ResultadoRecomendador(List.of()));

        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("La propuesta queda LISTA, con los puntos como enteros y las abiertas sin opciones")
    void guardarLaPropuesta() {
        PropuestaPreguntas pedida = propuesta(PropuestaPreguntas.PEDIDA);
        ResultadoRecomendador resultado = new ResultadoRecomendador(List.of(
                new CriterioPropuesto(8L, "ignorado", null, List.of(
                        new PreguntaPropuesta(ReglasDePuntos.ABIERTA, "¿Un caso?", new BigDecimal("20.0"),
                                "Un caso con resultado", null))),
                new CriterioPropuesto(null, "Orden", "Cuadre de caja", List.of(
                        new PreguntaPropuesta(ReglasDePuntos.OPCION_UNICA, "¿Qué haces?", new BigDecimal("10"),
                                null, List.of(new OpcionPropuesta("Cuadro", new BigDecimal("10.00")),
                                        new OpcionPropuesta("Nada", null)))))));

        puente.guardarPropuesta(VACANTE, resultado);

        verify(propuestas).save(pedida);
        assertThat(pedida.getEstado()).isEqualTo(PropuestaPreguntas.LISTA);
        assertThat(pedida.getTerminadaEn()).isNotNull();
        JsonNode json = new ObjectMapper().readTree(pedida.getContenido());
        assertThat(json.size()).isEqualTo(2);
        JsonNode abierta = json.get(0).get("preguntas").get(0);
        assertThat(json.get(0).get("criterioExistenteId").asLong()).isEqualTo(8L);
        assertThat(abierta.get("puntos").decimalValue()).isEqualByComparingTo("20");
        assertThat(abierta.get("puntos").asString()).isEqualTo("20");
        assertThat(abierta.get("opciones").isEmpty()).isTrue();
        JsonNode cerrada = json.get(1).get("preguntas").get(0);
        assertThat(cerrada.get("opciones").get(0).get("puntos").asString()).isEqualTo("10");
        assertThat(cerrada.get("opciones").get(1).get("puntos").isNull()).isTrue();
    }

    // ---------------------------------------------------------------- marcarFallida

    @Test
    @DisplayName("Sin pedida no hay nada que marcar como fallido")
    void fallidaSinPedida() {
        when(propuestas.findFirstByVacanteIdOrderByIdDesc(VACANTE)).thenReturn(Optional.empty());

        puente.marcarFallida(VACANTE, List.of("suma 90"));

        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("La fallida dice qué errores no pudo corregir")
    void fallidaConSusErrores() {
        PropuestaPreguntas pedida = propuesta(PropuestaPreguntas.PEDIDA);

        puente.marcarFallida(VACANTE, List.of("suma 90", "tipo raro"));

        verify(propuestas).save(pedida);
        assertThat(pedida.getEstado()).isEqualTo(PropuestaPreguntas.FALLIDA);
        assertThat(pedida.getTerminadaEn()).isNotNull();
        assertThat(pedida.getMotivoFallo()).isEqualTo(
                "La IA no devolvió una propuesta válida, ni al corregirla: suma 90 · tipo raro");
    }

    @Test
    @DisplayName("Un motivo larguísimo se corta a 2000 caracteres para caber en la columna")
    void fallidaConMotivoLargo() {
        PropuestaPreguntas pedida = propuesta(PropuestaPreguntas.PEDIDA);

        puente.marcarFallida(VACANTE, List.of("x".repeat(3000)));

        assertThat(pedida.getMotivoFallo()).hasSize(2000).endsWith("…")
                .startsWith("La IA no devolvió una propuesta válida");
    }
}
