package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import com.renaser.ai.ai_engine.pesos.repository.EtapaRepository;
import com.renaser.ai.ai_engine.vacante.entity.VacanteEtapa;
import com.renaser.ai.ai_engine.vacante.repository.VacanteEtapaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El recorrido de una vacante (V95)")
class RecorridoDeLaVacanteTest {

    /** El catálogo que deja la V95. */
    static final List<Etapa> CATALOGO = List.of(
            etapa("PERFIL_INTEGRAL", "Perfil Integral de Preselección", 1, true, true),
            etapa("PRUEBA_PUESTO", "Prueba del puesto", 2, false, true),
            etapa("SIMULACION", "Simulación de trabajo", 3, false, false),
            etapa("VALIDACION", "Validación práctica", 4, false, false),
            etapa("DECISION", "Decisión", 5, false, true));

    @Mock private VacanteEtapaRepository filas;
    @Mock private EtapaRepository etapas;

    private RecorridoDeLaVacante recorridos;

    @BeforeEach
    void armar() {
        lenient().when(etapas.findAllByOrderByOrdenAsc()).thenReturn(CATALOGO);
        recorridos = new RecorridoDeLaVacante(filas, etapas);
    }

    static Etapa etapa(String codigo, String nombre, int orden, boolean fija, boolean predeterminada) {
        return Etapa.builder().codigo(codigo).nombre(nombre).orden(orden).descripcion("Explicación de " + nombre)
                .fijaPrimera(fija).enPredeterminado(predeterminada).build();
    }

    private static VacanteEtapa fila(long vacante, String etapa, int orden) {
        return VacanteEtapa.builder().vacanteId(vacante).etapaCodigo(etapa).orden(orden).build();
    }

    @Test
    @DisplayName("el predeterminado es Perfil Integral → Prueba del puesto → Decisión (AC-01)")
    void elPredeterminado() {
        assertThat(recorridos.predeterminado())
                .containsExactly("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "DECISION");
    }

    @Test
    @DisplayName("una vacante nueva recibe el predeterminado, con su lugar desde 1, sin borrar nada")
    @SuppressWarnings("unchecked")
    void ponerElPredeterminado() {
        recorridos.ponerPredeterminado(7L);

        ArgumentCaptor<List<VacanteEtapa>> escritas = ArgumentCaptor.forClass(List.class);
        verify(filas).saveAll(escritas.capture());
        assertThat(escritas.getValue()).extracting(VacanteEtapa::getEtapaCodigo, VacanteEtapa::getOrden)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PERFIL_INTEGRAL", 1),
                        org.assertj.core.groups.Tuple.tuple("PRUEBA_PUESTO", 2),
                        org.assertj.core.groups.Tuple.tuple("DECISION", 3));
        verify(filas, never()).borrarElRecorrido(any());
    }

    @Test
    @DisplayName("escribir otro recorrido borra antes el que tenía")
    void escribirBorraAntes() {
        recorridos.escribir(7L, List.of("PERFIL_INTEGRAL", "SIMULACION"));

        var orden = inOrder(filas);
        orden.verify(filas).borrarElRecorrido(7L);
        orden.verify(filas).saveAll(any());
    }

    @Test
    @DisplayName("el recorrido de una vacante sale de sus filas, en orden")
    void deSusFilas() {
        when(filas.findByVacanteIdOrderByOrdenAsc(7L)).thenReturn(List.of(
                fila(7L, "PERFIL_INTEGRAL", 1), fila(7L, "SIMULACION", 2), fila(7L, "PRUEBA_PUESTO", 3)));

        assertThat(recorridos.de(7L)).containsExactly("PERFIL_INTEGRAL", "SIMULACION", "PRUEBA_PUESTO");
        assertThat(recorridos.tiene(7L, "DECISION")).isFalse();
    }

    @Test
    @DisplayName("una vacante sin filas recorre el catálogo entero, como antes de la V95")
    void sinFilasLasCinco() {
        when(filas.findByVacanteIdOrderByOrdenAsc(8L)).thenReturn(List.of());

        assertThat(recorridos.de(8L))
                .containsExactly("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "SIMULACION", "VALIDACION", "DECISION");
    }

    @Test
    @DisplayName("los de una lista salen con una consulta, y la que no tiene filas lleva las cinco")
    void deUnaLista() {
        when(filas.findByVacanteIdInOrderByVacanteIdAscOrdenAsc(List.of(7L, 8L))).thenReturn(List.of(
                fila(7L, "PERFIL_INTEGRAL", 1), fila(7L, "DECISION", 2)));

        Map<Long, List<String>> porVacante = recorridos.de(List.of(7L, 8L));

        assertThat(porVacante.get(7L)).containsExactly("PERFIL_INTEGRAL", "DECISION");
        assertThat(porVacante.get(8L)).hasSize(5);
    }

    // ============ Qué recorrido vale ============

    @Test
    @DisplayName("vale cualquiera que empiece por el Perfil, sin repetidas y con etapas del catálogo; el Perfil solo también")
    void losQueValen() {
        assertThat(RecorridoDeLaVacante.queFalla(List.of("PERFIL_INTEGRAL"), CATALOGO)).isNull();
        assertThat(RecorridoDeLaVacante.queFalla(
                List.of("PERFIL_INTEGRAL", "SIMULACION", "PRUEBA_PUESTO", "DECISION"), CATALOGO)).isNull();
        assertThat(RecorridoDeLaVacante.queFalla(
                List.of("PERFIL_INTEGRAL", "DECISION", "VALIDACION"), CATALOGO)).isNull();
    }

    @Test
    @DisplayName("vacío, sin el Perfil primero, con repetidas o con códigos que no existen: dice qué falla (AC-04)")
    void losQueNoValen() {
        assertThat(RecorridoDeLaVacante.queFalla(List.of(), CATALOGO)).contains("vacío");
        assertThat(RecorridoDeLaVacante.queFalla(null, CATALOGO)).contains("vacío");
        assertThat(RecorridoDeLaVacante.queFalla(List.of("PRUEBA_PUESTO", "PERFIL_INTEGRAL"), CATALOGO))
                .contains("empezar por Perfil Integral");
        assertThat(RecorridoDeLaVacante.queFalla(List.of("DECISION"), CATALOGO))
                .contains("empezar por Perfil Integral");
        assertThat(RecorridoDeLaVacante.queFalla(
                List.of("PERFIL_INTEGRAL", "DECISION", "DECISION"), CATALOGO)).contains("se repite: DECISION");
        assertThat(RecorridoDeLaVacante.queFalla(List.of("PERFIL_INTEGRAL", "ENTREVISTA"), CATALOGO))
                .contains("no existen: ENTREVISTA");
    }
}
