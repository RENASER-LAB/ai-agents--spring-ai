package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Quién sigue en carrera, que es la lista de a quién le afecta lo que pase en su vacante.
 *
 * <p>Se prueba aquí y en un solo sitio a propósito: la definición está escrita una vez para
 * que cambiar el sueldo, corregir el texto y —más adelante— archivar o eliminar la vacante
 * le hablen exactamente a la misma gente. Olvidar un estado en uno de esos sitios no rompe
 * nada: manda un aviso sobre un puesto que la persona ya perdió.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Las postulaciones en carrera")
class PostulacionesEnCarreraTest {

    @Mock private PostulacionRepository postulaciones;

    private PostulacionesEnCarrera enCarrera() {
        return new PostulacionesEnCarrera(postulaciones);
    }

    private Postulacion con(String estado) {
        return Postulacion.builder().id(1L).vacanteId(9L).estadoCodigo(estado).build();
    }

    @Test
    @DisplayName("los tres estados finales terminaron; todo lo demás sigue vivo")
    void losTresFinalesNoSiguen() {
        assertThat(PostulacionesEnCarrera.sigueEnCarrera(con("CONTRATADO"))).isFalse();
        assertThat(PostulacionesEnCarrera.sigueEnCarrera(con("NO_CONTINUA"))).isFalse();
        assertThat(PostulacionesEnCarrera.sigueEnCarrera(con("CERRADA"))).isFalse();

        assertThat(PostulacionesEnCarrera.sigueEnCarrera(con("RECIBIDA"))).isTrue();
        assertThat(PostulacionesEnCarrera.sigueEnCarrera(con("PERFIL_TURNO_CANDIDATO"))).isTrue();
        assertThat(PostulacionesEnCarrera.sigueEnCarrera(con("DECISION_FINAL"))).isTrue();
        // Sin postulación no hay nadie a quien avisar, y eso no es «sigue en carrera».
        assertThat(PostulacionesEnCarrera.sigueEnCarrera(null)).isFalse();
    }

    @Test
    @DisplayName("el filtro va en la consulta, no después: la lista no se trae entera para descartarla")
    void elFiltroVaEnLaConsulta() {
        when(postulaciones.enCarreraDeLaVacante(eq(9L), any())).thenReturn(List.of(con("RECIBIDA")));

        assertThat(enCarrera().deLaVacante(9L)).hasSize(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> estados = ArgumentCaptor.forClass(Collection.class);
        verify(postulaciones).enCarreraDeLaVacante(eq(9L), estados.capture());
        assertThat(estados.getValue())
                .containsExactlyInAnyOrder("CONTRATADO", "NO_CONTINUA", "CERRADA");
    }

    @Test
    @DisplayName("el conteo de toda la empresa sale de una sola consulta, y sin nadie es cero")
    void elConteoEsUnaSolaConsulta() {
        when(postulaciones.enCarreraPorVacante(eq(1L), any()))
                .thenReturn(List.of(new Object[]{9L, 3L}, new Object[]{10L, 1L}));

        var porVacante = enCarrera().cuantasPorVacante(1L);

        assertThat(porVacante).containsEntry(9L, 3).containsEntry(10L, 1);
        // Las vacantes sin nadie no salen en el mapa: quien lo lea trata la ausencia como
        // un cero, y así una empresa con cien vacantes vacías no paga cien filas.
        assertThat(porVacante.getOrDefault(11L, 0)).isZero();
    }

    @Test
    @DisplayName("cuántas quedan en una vacante se pregunta con los mismos tres estados")
    void elConteoDeUnaVacante() {
        when(postulaciones.countByVacanteIdAndEstadoCodigoNotIn(anyLong(), any())).thenReturn(2L);

        assertThat(enCarrera().cuantasEnLaVacante(9L)).isEqualTo(2);
        verify(postulaciones).countByVacanteIdAndEstadoCodigoNotIn(9L,
                PostulacionesEnCarrera.ESTADOS_TERMINADOS);
    }
}
