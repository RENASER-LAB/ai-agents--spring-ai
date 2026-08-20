package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contra qué se califica a un candidato.
 *
 * <p>Esto es lo primero que lee cada uno de los agentes que puntúan, y no puntúa nada por sí
 * mismo: por eso, cuando se equivoca, <b>no se rompe nada a la vista</b>. El modelo recibe
 * igual su texto, devuelve igual sus notas, y la ficha del candidato queda igual de completa.
 * Solo que juzgada contra otra cosa.
 *
 * <p>De ahí que casi todo lo que se prueba aquí sean los silencios:
 *
 * <ul>
 *   <li><b>Un dato que falta se dice, no se rellena.</b> Si la vacante o el puesto ya no
 *       están, calificar contra un hueco daría una nota que parece buena y no significa nada.
 *   <li><b>Un campo vacío de la convocatoria no escribe «null».</b> Ese texto se le manda
 *       entero al modelo: la palabra «null» dentro es una instrucción basura por la que se
 *       paga token a token.
 *   <li><b>El texto va completo y sin resumir.</b> Decidir qué parte de la vacante importa es
 *       justo lo que se le está pidiendo al modelo, así que no se decide aquí.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El contexto de la vacante que se le da a los agentes")
class ContextoDeLaVacanteTest {

    private static final long POSTULACION = 55L;
    private static final long VACANTE = 7L;
    private static final long PUESTO = 3L;

    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;

    @InjectMocks
    private ContextoDeLaVacante contexto;

    // ============ La vacante ============

    @Test
    @DisplayName("la vacante que sale es la de esa postulación")
    void devuelveLaVacanteDeLaPostulacion() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante()));

        assertThat(contexto.vacanteDe(postulacion()).getId())
                .as("calificar contra la convocatoria de otro puesto daría una nota que nadie "
                        + "puede defender delante del candidato")
                .isEqualTo(VACANTE);
    }

    @Test
    @DisplayName("una postulación cuya vacante ya no existe no se califica contra un hueco")
    void sinVacanteNoHayContraQueCalificar() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contexto.vacanteDe(postulacion()))
                .isInstanceOf(IllegalStateException.class)
                .as("el mensaje tiene que decir qué postulación quedó huérfana, o no hay por "
                        + "dónde empezar a buscarla")
                .hasMessageContaining("55");
    }

    // ============ El puesto ============

    @Test
    @DisplayName("el puesto sale de la vacante, no de la postulación")
    void devuelveElPuestoDeLaVacante() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante()));
        when(puestos.findById(PUESTO)).thenReturn(Optional.of(puesto()));

        Puesto encontrado = contexto.puestoDe(postulacion());

        assertThat(encontrado.getNombre()).isEqualTo("Analista de procesos");
        assertThat(encontrado.getNivelPuestoCodigo())
                .as("el nivel es lo que separa un entregable excelente para un junior de uno "
                        + "flojo para una dirección")
                .isEqualTo("OPERATIVO");
    }

    @Test
    @DisplayName("una vacante que apunta a un puesto borrado se dice, nombrando la vacante")
    void sinPuestoSeAvisaDeQueLaVacanteApuntaAlVacio() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante()));
        when(puestos.findById(PUESTO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contexto.puestoDe(postulacion()))
                .isInstanceOf(IllegalStateException.class)
                .as("lo que hay que arreglar es la vacante, así que es la vacante la que se nombra")
                .hasMessageContaining("7");
    }

    @Test
    @DisplayName("sin vacante no se llega siquiera a preguntar por el puesto")
    void seDetieneEnLaVacanteYNoSigue() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contexto.puestoDe(postulacion()))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(puestos);
    }

    // ============ El texto de la convocatoria ============

    @Test
    @DisplayName("la convocatoria sale entera y en su orden: título, propósito, responsabilidades y requisitos")
    void pegaLaConvocatoriaEnteraYEnOrden() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacanteCon(
                "Analista de procesos", "Ordenar el flujo de compras",
                "Levantar procesos y proponer mejoras", "Tres años de experiencia")));

        assertThat(contexto.queBuscaLaVacanteDe(postulacion()))
                .as("se manda sin resumir: elegir qué parte de la vacante importa es justo lo "
                        + "que se le está pidiendo al modelo")
                .isEqualTo("""
                        Analista de procesos
                        Ordenar el flujo de compras
                        Levantar procesos y proponer mejoras
                        Tres años de experiencia""");
    }

    @Test
    @DisplayName("un campo vacío de la convocatoria no mete la palabra «null» en lo que lee el modelo")
    void unCampoVacioNoEscribeNull() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacanteCon(
                "Analista de procesos", null, null, "Tres años de experiencia")));

        String texto = contexto.queBuscaLaVacanteDe(postulacion());

        assertThat(texto)
                .as("«null» dentro del texto es una instrucción basura por la que se paga token a token")
                .doesNotContain("null")
                .contains("Analista de procesos")
                .contains("Tres años de experiencia");
    }

    @Test
    @DisplayName("una vacante sin nada escrito devuelve texto vacío, no saltos de línea sueltos")
    void unaVacanteVaciaNoDevuelveSaltosDeLinea() {
        when(vacantes.findById(VACANTE))
                .thenReturn(Optional.of(vacanteCon(null, null, null, null)));

        assertThat(contexto.queBuscaLaVacanteDe(postulacion()))
                .as("mandar tres saltos de línea es peor que mandar nada: parece que hay contenido")
                .isEmpty();
    }

    // ============ Apoyo ============

    private Postulacion postulacion() {
        return Postulacion.builder()
                .id(POSTULACION).organizacionId(1L).vacanteId(VACANTE)
                .build();
    }

    private Vacante vacante() {
        return vacanteCon("Analista de procesos", "Ordenar el flujo", "Levantar procesos",
                "Tres años");
    }

    private Vacante vacanteCon(String titulo, String proposito, String responsabilidades,
                               String requisitos) {
        return Vacante.builder()
                .id(VACANTE).organizacionId(1L).puestoId(PUESTO)
                .titulo(titulo).proposito(proposito)
                .responsabilidades(responsabilidades).requisitos(requisitos)
                .build();
    }

    private Puesto puesto() {
        return Puesto.builder()
                .id(PUESTO).organizacionId(1L).nombre("Analista de procesos")
                .nivelPuestoCodigo("OPERATIVO")
                .build();
    }
}
