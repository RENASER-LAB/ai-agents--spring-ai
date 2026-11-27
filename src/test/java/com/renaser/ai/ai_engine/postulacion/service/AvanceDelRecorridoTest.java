package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.decision.service.EtapaDeLaDecision;
import com.renaser.ai.ai_engine.perfilintegral.service.EtapaDelPerfilIntegral;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.RecorridoDeLaVacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * El avance por el recorrido con una etapa que solo existe en esta prueba (AC-19).
 *
 * <p>«Entrevista con gerencia» tiene sus estados en la rejilla, su pieza y su lugar en el recorrido
 * de una vacante, y nada más: ni la máquina de estados ni el avance la conocen por su nombre. Se
 * entra a ella, se avanza por sus momentos y se sale hacia la Decisión.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El avance por el recorrido, con una etapa nueva")
class AvanceDelRecorridoTest {

    private static final Long VACANTE = 9L;

    @Mock private MaquinaEstados maquina;
    @Mock private EstadoPostulacionRepository estados;
    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private RecorridoDeLaVacante recorridos;

    /** La etapa nueva: solo esta pieza sabe qué pasa al entrar. */
    static final class Entrevista implements DefinicionDeEtapa {
        final List<String> entradas = new ArrayList<>();
        final List<String> despues = new ArrayList<>();

        @Override
        public String codigo() {
            return "ENTREVISTA";
        }

        @Override
        public Destino alEntrar(Entrada entrada) {
            entradas.add(entrada.destino());
            return new Destino(entrada.destino(), entrada.motivo() + " · con la gerencia");
        }

        @Override
        public void trasEntrar(Postulacion postulacion, String estado) {
            despues.add(estado);
        }
    }

    private final Entrevista entrevista = new Entrevista();
    private final List<EstadoPostulacion> catalogo = new ArrayList<>(RecorridosDePrueba.CATALOGO);
    private AvanceDelRecorrido avance;
    private Postulacion postulacion;

    @BeforeEach
    void armar() {
        catalogo.add(EstadoPostulacion.builder().codigo("ENTREVISTA_TURNO_CANDIDATO").etapaCodigo("ENTREVISTA")
                .momentoCodigo("TURNO_CANDIDATO").esperaA("CANDIDATO").orden(30).build());
        catalogo.add(EstadoPostulacion.builder().codigo("ENTREVISTA_POR_CONFIRMAR").etapaCodigo("ENTREVISTA")
                .momentoCodigo("POR_CONFIRMAR").esperaA("TALENTO").orden(31).build());
        List<String> recorrido = List.of("PERFIL_INTEGRAL", "ENTREVISTA", "DECISION");

        lenient().when(recorridos.de(VACANTE)).thenReturn(recorrido);
        lenient().when(recorridos.tiene(any(), anyString()))
                .thenAnswer(inv -> recorrido.contains(inv.<String>getArgument(1)));
        lenient().when(estados.findById(anyString())).thenAnswer(inv -> catalogo.stream()
                .filter(e -> e.getCodigo().equals(inv.getArgument(0))).findFirst());
        // La máquina calcula de verdad, con el catálogo que incluye la etapa nueva.
        lenient().when(maquina.siguiente(anyString(), any(Recorrido.class))).thenAnswer(inv -> {
            EstadoPostulacion actual = catalogo.stream()
                    .filter(e -> e.getCodigo().equals(inv.getArgument(0))).findFirst().orElseThrow();
            return MaquinaEstados.calcularSiguiente(inv.getArgument(1), catalogo, actual);
        });
        lenient().when(vacantes.findByIdAndOrganizacionId(VACANTE, 1L))
                .thenReturn(Optional.of(Vacante.builder().id(VACANTE).organizacionId(1L).build()));

        avance = new AvanceDelRecorrido(maquina, estados, postulaciones, vacantes, recorridos,
                RegistroDeEtapas.con(List.of(new EtapaDelPerfilIntegral(), entrevista, new EtapaDeLaDecision())));
        postulacion = Postulacion.builder().id(55L).organizacionId(1L).vacanteId(VACANTE)
                .estadoCodigo("PERFIL_POR_CONFIRMAR").build();
        // La máquina simulada no mueve: la prueba mueve a mano lo que movería.
        lenient().doAnswer(inv -> {
            postulacion.setEstadoCodigo(inv.getArgument(1));
            return null;
        }).when(maquina).transicionar(any(), anyString(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.<String>any());
    }

    @Test
    @DisplayName("entra a la etapa nueva con su pieza, avanza por sus momentos y sale a la Decisión (AC-19)")
    void entraAvanzaYSale() {
        assertThat(avance.avanzar(postulacion, null, "Pasa", true)).contains("ENTREVISTA_TURNO_CANDIDATO");
        assertThat(entrevista.entradas).containsExactly("ENTREVISTA_TURNO_CANDIDATO");
        assertThat(entrevista.despues).containsExactly("ENTREVISTA_TURNO_CANDIDATO");
        verify(maquina).transicionar(postulacion, "ENTREVISTA_TURNO_CANDIDATO", null,
                "Pasa · con la gerencia", true, false, null);

        // Dentro de la etapa, moverse no vuelve a llamar a su pieza.
        assertThat(avance.avanzar(postulacion, null, "Entrevistada", true)).contains("ENTREVISTA_POR_CONFIRMAR");
        assertThat(entrevista.entradas).hasSize(1);

        // Y al terminarla, la que sigue en el recorrido: la Decisión, por «por confirmar».
        assertThat(avance.avanzar(postulacion, null, "Confirmada", true)).contains("DECISION_POR_CONFIRMAR");
        assertThat(avance.avanzar(postulacion, null, "Nada más", true)).isEmpty();
    }

    @Test
    @DisplayName("la etapa nueva no tiene que decir nada para estar lista: por defecto, lo está")
    void listaPorDefecto() {
        EstadoPostulacion entrada = catalogo.stream()
                .filter(e -> e.getCodigo().equals("ENTREVISTA_TURNO_CANDIDATO")).findFirst().orElseThrow();

        assertThat(avance.listaParaEntrar(entrada, Vacante.builder().build())).isTrue();
    }
}
