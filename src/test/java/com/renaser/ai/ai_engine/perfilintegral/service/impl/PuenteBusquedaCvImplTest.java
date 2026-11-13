package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.LineaBuscadaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDeLaBusquedaIa;
import com.renaser.ai.ai_engine.perfilintegral.entity.BusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.ResultadoBusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.repository.BusquedaCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ResultadoBusquedaCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.LineaVigente;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.ServicioTextoCv;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El puente de «Qué buscar en el CV»: lo que se guarda de la IA")
class PuenteBusquedaCvImplTest {

    private static final long POSTULACION = 40L;
    private static final long VACANTE = 4L;
    private static final String CV = "2021-2024 · Cocinera principal en Verde Bowl.\n"
            + "Bajé la merma   de verduras del 9 % al 4 % en un año. Escribe BPM.";

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private CvRepository cvs;
    @Mock private ServicioTextoCv textoCv;
    @Mock private LoBuscadoEnElCv loBuscado;
    @Mock private BusquedaCvRepository busquedas;
    @Mock private ResultadoBusquedaCvRepository resultados;
    @Mock private PuenteCalificacionIa puente;

    @InjectMocks private PuenteBusquedaCvImpl puenteBusqueda;

    private final List<LineaVigente> vigentes = List.of(
            new LineaVigente(101L, 1, "Controla costos y mermas", 2),
            new LineaVigente(102L, 2, "Certificado BPM", 2),
            new LineaVigente(103L, 3, "Ha dirigido un equipo de cocina", 2));

    @BeforeEach
    void armar() {
        lenient().when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(
                Postulacion.builder().id(POSTULACION).vacanteId(VACANTE).build()));
        lenient().when(loBuscado.vigentes(VACANTE)).thenReturn(vigentes);
        lenient().when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                Cv.builder().textoAnonimizado(CV).build()));
        lenient().when(busquedas.saveAndFlush(any(BusquedaCv.class))).thenAnswer(i -> {
            BusquedaCv b = i.getArgument(0);
            b.setId(900L);
            return b;
        });
    }

    @SuppressWarnings("unchecked")
    private List<ResultadoBusquedaCv> guardados() {
        ArgumentCaptor<Iterable<ResultadoBusquedaCv>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(resultados).saveAll(captor.capture());
        List<ResultadoBusquedaCv> lista = new ArrayList<>();
        captor.getValue().forEach(lista::add);
        return lista;
    }

    @Test
    @DisplayName("AC-06: «lo tiene» con una frase del CV se guarda con la frase, sin mirar tildes ni espacios")
    void loTieneConFraseDelCv() {
        boolean guardado = puenteBusqueda.guardar(POSTULACION, 7L, new ResultadoDeLaBusquedaIa(List.of(
                new LineaBuscadaIa(1, "TIENE", "«Baje la merma de verduras del 9 % al 4 % en un año»",
                        "Bajó la merma a menos de la mitad.", null))), 2, false);

        assertThat(guardado).isTrue();
        ResultadoBusquedaCv primera = guardados().get(0);
        assertThat(primera.getEstado()).isEqualTo("TIENE");
        assertThat(primera.getFraseCv()).isEqualTo("Baje la merma de verduras del 9 % al 4 % en un año");
        assertThat(primera.getLineaBusquedaCvId()).isEqualTo(101L);
        assertThat(primera.getBusquedaCvId()).isEqualTo(900L);
        assertThat(primera.getEjecucionIaId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("AC-07: «lo tiene» con una frase que no está en el CV baja a «lo menciona», sin frase")
    void fraseInventadaBajaAMenciona() {
        puenteBusqueda.guardar(POSTULACION, 7L, new ResultadoDeLaBusquedaIa(List.of(
                new LineaBuscadaIa(1, "TIENE", "Redujo los costos un 30 %", "nota", null))), 2, false);

        ResultadoBusquedaCv primera = guardados().get(0);
        assertThat(primera.getEstado()).isEqualTo("MENCIONA");
        assertThat(primera.getFraseCv()).isNull();
    }

    @Test
    @DisplayName("La que falta se guarda «no aparece», la que sobra se ignora y un estado raro no cuela")
    void faltanSobranYRaras() {
        puenteBusqueda.guardar(POSTULACION, 7L, new ResultadoDeLaBusquedaIa(List.of(
                new LineaBuscadaIa(2, "MENCIONA", "Escribe BPM", "Solo la sigla.", "¿De qué año es?"),
                new LineaBuscadaIa(3, "INVENTADO", null, null, null),
                new LineaBuscadaIa(9, "TIENE", "Cocinera principal", "sobra", null))), 2, false);

        List<ResultadoBusquedaCv> filas = guardados();
        assertThat(filas).hasSize(3);
        assertThat(filas).extracting(ResultadoBusquedaCv::getEstado)
                .containsExactly("NO_APARECE", "MENCIONA", "NO_APARECE");
        // La frase solo se guarda en «lo tiene»; la pregunta, en lo demás.
        assertThat(filas.get(1).getFraseCv()).isNull();
        assertThat(filas.get(1).getPregunta()).isEqualTo("¿De qué año es?");
    }

    @Test
    @DisplayName("AC-14: un resultado de unas líneas que ya cambiaron se descarta sin guardar nada")
    void deUnaVersionViejaSeDescarta() {
        boolean guardado = puenteBusqueda.guardar(POSTULACION, 7L, new ResultadoDeLaBusquedaIa(List.of(
                new LineaBuscadaIa(1, "NO_APARECE", null, "nada", "¿?"))), 1, true);

        assertThat(guardado).isFalse();
        verify(resultados, never()).saveAll(any());
        verify(busquedas, never()).saveAndFlush(any());
        verify(puente, never()).recalcularSiYaTieneNota(anyLong());
    }

    @Test
    @DisplayName("La rebúsqueda recalcula la nota al llegar; la de la criba la deja al retrato")
    void laRebusquedaRecalcula() {
        ResultadoDeLaBusquedaIa resultado = new ResultadoDeLaBusquedaIa(List.of());

        puenteBusqueda.guardar(POSTULACION, 7L, resultado, 2, false);
        verify(puente, never()).recalcularSiYaTieneNota(POSTULACION);

        puenteBusqueda.guardar(POSTULACION, 8L, resultado, 2, true);
        verify(puente).recalcularSiYaTieneNota(POSTULACION);
    }

    @Test
    @DisplayName("Antes de guardar se espera al cerrojo de la vacante y se archiva lo anterior")
    void esperaAlCerrojoYArchiva() {
        puenteBusqueda.guardar(POSTULACION, 7L, new ResultadoDeLaBusquedaIa(List.of()), 2, false);

        verify(vacantes).bloquearParaCalificarYReleer(any(Postulacion.class));
        verify(resultados).archivarLosVivos(any(), any());
        verify(busquedas).archivarLasVivas(any(), any());
    }

    @Test
    @DisplayName("Una respuesta sin líneas no se puede leer: el intento falla y se reintenta")
    void sinLineasFalla() {
        assertThatThrownBy(() -> puenteBusqueda.guardar(POSTULACION, 7L, null, 2, false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> puenteBusqueda.guardar(POSTULACION, 7L,
                new ResultadoDeLaBusquedaIa(null), 2, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AC-11: el CV sin texto se guarda como tal, con su motivo y la versión vigente")
    void cvSinTexto() {
        puenteBusqueda.marcarSinTexto(POSTULACION, "El PDF no tiene texto");

        ArgumentCaptor<BusquedaCv> captor = ArgumentCaptor.forClass(BusquedaCv.class);
        verify(busquedas).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("SIN_TEXTO");
        assertThat(captor.getValue().getMotivo()).isEqualTo("El PDF no tiene texto");
        assertThat(captor.getValue().getVersionLineas()).isEqualTo(2);
    }

    @Test
    @DisplayName("Sin líneas en la vacante no hay insumo: no se lee el CV ni se llama a la IA")
    void sinLineasNoHayInsumo() {
        when(loBuscado.vigentes(VACANTE)).thenReturn(List.of());

        assertThat(puenteBusqueda.insumo(POSTULACION)).isNull();
        verify(textoCv, never()).prepararParaIa(anyLong());
    }

    @Test
    @DisplayName("La frase se compara sin mayúsculas, tildes ni espacios repetidos")
    void comparacion() {
        String cv = PuenteBusquedaCvImpl.comparable(CV);
        assertThat(PuenteBusquedaCvImpl.estaEnElCv("COCINERA  PRINCIPAL en verde bowl", cv)).isTrue();
        assertThat(PuenteBusquedaCvImpl.estaEnElCv("Cocinera jefa", cv)).isFalse();
        assertThat(PuenteBusquedaCvImpl.estaEnElCv("  ", cv)).isFalse();
        assertThat(PuenteBusquedaCvImpl.estaEnElCv("Cocinera", null)).isFalse();
    }
}
