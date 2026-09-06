package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El trabajo de leer un currículum <b>subido al perfil</b>: no cuelga de ninguna postulación.
 *
 * <p>Aquí se protege que no haga falta un agente nuevo para esto. Cuelga de
 * {@code referencia_tabla = 'lectura_cv_perfil'} igual que el REDACTOR cuelga de
 * {@code 'vacante'}, y {@code postulacion_id} se queda vacío, que es lo que la columna admite
 * desde la V11. Si alguien «arreglara» esto poniendo una postulación, la ficha de la lectura
 * acabaría escrita contra la postulación de otro.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El registro de trabajos de una lectura del perfil")
class RegistroTrabajosIaPerfilTest {

    private static final long LECTURA = 77L;

    @Mock private TrabajoIaRepository trabajos;

    @InjectMocks
    private RegistroTrabajosIa registro;

    private void conUltimo(String estado) {
        when(trabajos.findFirstByReferenciaTablaAndReferenciaIdAndAgenteCodigoOrderByIdDesc(
                "lectura_cv_perfil", LECTURA, AgenteDatosCv.CODIGO_AGENTE))
                .thenReturn(estado == null ? Optional.empty()
                        : Optional.of(TrabajoIa.builder().id(9L).estado(estado).build()));
    }

    @Test
    @DisplayName("Se crea colgando de lectura_cv_perfil y sin postulación")
    void cuelgaDeLaLecturaYNoDeUnaPostulacion() {
        conUltimo(null);
        when(trabajos.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Optional<TrabajoIa> creado = registro.crearParaLecturaDePerfil(
                1L, AgenteDatosCv.CODIGO_AGENTE, LECTURA, "FINA");

        assertThat(creado).isPresent();
        assertThat(creado.get().getReferenciaTabla()).isEqualTo(AgenteDatosCv.DEL_PERFIL);
        assertThat(creado.get().getReferenciaId()).isEqualTo(LECTURA);
        assertThat(creado.get().getPostulacionId()).isNull();
        assertThat(creado.get().getEstado()).isEqualTo("PENDIENTE");
        assertThat(creado.get().getIntentos()).isZero();
        assertThat(creado.get().getOrganizacionId()).isEqualTo(1L);
        assertThat(creado.get().getModo()).isEqualTo("FINA");
        assertThat(creado.get().getCreadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Uno vivo frena el siguiente: reemplazar el currículum dos veces seguidas no se paga dos veces")
    void unVivoFrena() {
        for (String estado : new String[]{"PENDIENTE", "EN_CURSO", "EN_ESPERA"}) {
            conUltimo(estado);
            assertThat(registro.crearParaLecturaDePerfil(
                    1L, AgenteDatosCv.CODIGO_AGENTE, LECTURA, "FINA"))
                    .as(estado).isEmpty();
        }
        verify(trabajos, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Lo TERMINADO y lo FALLIDO no eximen: volver a intentarlo es un trabajo nuevo")
    void loCerradoNoExime() {
        for (String estado : new String[]{"TERMINADO", "FALLIDO"}) {
            conUltimo(estado);
            when(trabajos.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            assertThat(registro.crearParaLecturaDePerfil(
                    1L, AgenteDatosCv.CODIGO_AGENTE, LECTURA, "FINA"))
                    .as(estado).isPresent();
        }
        ArgumentCaptor<TrabajoIa> creados = ArgumentCaptor.forClass(TrabajoIa.class);
        verify(trabajos, org.mockito.Mockito.times(2)).saveAndFlush(creados.capture());
        assertThat(creados.getAllValues()).allSatisfy(
                t -> assertThat(t.getReferenciaTabla()).isEqualTo(AgenteDatosCv.DEL_PERFIL));
    }

    @Test
    @DisplayName("La lectura de otra persona no frena la mía: se pregunta por ESTA lectura")
    void laDeOtroNoFrena() {
        // El índice de «una viva por persona» vive en la tabla de lecturas; aquí abajo lo que
        // hay son ids distintos, y preguntar por el id equivocado devolvería el trabajo ajeno.
        when(trabajos.findFirstByReferenciaTablaAndReferenciaIdAndAgenteCodigoOrderByIdDesc(
                "lectura_cv_perfil", 999L, AgenteDatosCv.CODIGO_AGENTE))
                .thenReturn(Optional.empty());
        when(trabajos.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(registro.crearParaLecturaDePerfil(
                1L, AgenteDatosCv.CODIGO_AGENTE, 999L, "FINA")).isPresent();
    }
}
