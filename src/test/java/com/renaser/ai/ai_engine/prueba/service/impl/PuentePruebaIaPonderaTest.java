package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cuándo la prueba queda con nota de etapa al terminar el agente, y cuándo no.
 *
 * <p>Antes no quedaba nunca, y el motivo era bueno: casi ninguna rúbrica está completa cuando
 * el modelo acaba, porque los criterios de método persona siguen vacíos por diseño. Sumar
 * media rúbrica daría un número bajo que parece un juicio y es un hueco.
 *
 * <p>Pero «casi ninguna» no es «ninguna». <b>Una rúbrica cuyos criterios son todos de agente
 * queda entera en cuanto el modelo termina</b>, y ahí la nota solo dependía de que alguien se
 * acordara de pedirla desde el panel. Nadie se acordaba: en producción había diecinueve
 * pruebas corregidas y sin una sola nota de etapa, y en el ranking se veían como si no se
 * hubieran corregido.
 *
 * <p>Estas dos pruebas fijan las dos ramas. La segunda es la que protege la decisión vieja:
 * si alguien quita la condición y suma siempre, se pone roja.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La prueba queda con nota solo si su rúbrica quedó entera")
class PuentePruebaIaPonderaTest {

    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private CalificacionPorCriterio calificacion;
    @InjectMocks private PuentePruebaIaImpl puente;

    private static Criterio criterio(long id, String nombre) {
        Criterio c = new Criterio();
        c.setId(id);
        c.setNombre(nombre);
        return c;
    }

    private static NotaCriterio nota(long criterioId, BigDecimal puntaje) {
        NotaCriterio n = new NotaCriterio();
        n.setCriterioId(criterioId);
        n.setPuntaje(puntaje);
        return n;
    }

    private static Postulacion postulacion() {
        Postulacion p = new Postulacion();
        p.setId(16L);
        return p;
    }

    /** Llama al método privado: lo que se prueba es su regla, no cómo se llega a ella. */
    private void ponderarSiProcede(Postulacion p, List<Criterio> rubrica) {
        ReflectionTestUtils.invokeMethod(puente, "ponderarSiLaRubricaEstaEntera", p, rubrica);
    }

    @Test
    @DisplayName("con la rúbrica entera calificada, suma y deja la nota")
    void sumaCuandoEstaEntera() {
        List<Criterio> rubrica = List.of(criterio(1, "Caja"), criterio(2, "Personal"));
        when(notasCriterio.findByPostulacionId(16L)).thenReturn(List.of(
                nota(1, new BigDecimal("8")), nota(2, new BigDecimal("5"))));

        ponderarSiProcede(postulacion(), rubrica);

        verify(calificacion).calcularNotaEtapa(any(), eq("PRUEBA_PUESTO"), eq(rubrica));
    }

    @Test
    @DisplayName("un cero es una nota puesta: no cuenta como criterio que falta")
    void unCeroEsUnaNota() {
        List<Criterio> rubrica = List.of(criterio(1, "Caja"));
        when(notasCriterio.findByPostulacionId(16L))
                .thenReturn(List.of(nota(1, BigDecimal.ZERO)));

        ponderarSiProcede(postulacion(), rubrica);

        verify(calificacion).calcularNotaEtapa(any(), any(), any());
    }

    @Test
    @DisplayName("si falta un criterio NO suma: media rúbrica parece un juicio y es un hueco")
    void noSumaSiFaltaAlguno() {
        List<Criterio> rubrica = List.of(criterio(1, "Caja"), criterio(2, "Video"));
        // El de método persona sigue vacío, que es lo normal en una rúbrica mixta.
        when(notasCriterio.findByPostulacionId(16L))
                .thenReturn(List.of(nota(1, new BigDecimal("8"))));

        ponderarSiProcede(postulacion(), rubrica);

        verify(calificacion, never()).calcularNotaEtapa(any(), any(), any());
    }

    @Test
    @DisplayName("una fila de nota sin puntaje cuenta como que falta, no como un cero")
    void unaFilaSinPuntajeNoCuenta() {
        List<Criterio> rubrica = List.of(criterio(1, "Caja"));
        // La fila existe —se crea al ajustar— pero no tiene puntaje.
        when(notasCriterio.findByPostulacionId(16L)).thenReturn(List.of(nota(1, null)));

        ponderarSiProcede(postulacion(), rubrica);

        verify(calificacion, never()).calcularNotaEtapa(any(), any(), any());
    }

    @Test
    @DisplayName("sin rúbrica no hay nada que sumar, y no se consulta la base")
    void sinRubricaNoHaceNada() {
        ponderarSiProcede(postulacion(), List.of());

        verify(notasCriterio, never()).findByPostulacionId(anyLong());
        verify(calificacion, never()).calcularNotaEtapa(any(), any(), any());
    }
}
