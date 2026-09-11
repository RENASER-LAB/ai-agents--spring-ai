package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioEvaluacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioPrueba;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Crearle al candidato lo que va a rendir, por los dos caminos que entran aquí.
 *
 * <p>Lo que de verdad se sostiene en esta clase es que la organización sale de la
 * <b>postulación</b> y nunca de quien pulsa: el pase automático no tiene a nadie detrás, y
 * tomarla de un usuario metería al candidato en la etapa técnica de otra empresa.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La entrada a la etapa técnica")
class EntradaEtapaTecnicaTest {

    private static final Long POSTULACION = 70L;
    private static final Long VACANTE = 4L;
    private static final Long ORGANIZACION = 2L;
    private static final Long USUARIO = 33L;
    private static final Long VERSION_PRUEBA = 12L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private ServicioPrueba prueba;
    @Mock private ServicioEvaluacion evaluaciones;
    @Mock private VersionBancoRepository versionesBanco;

    @InjectMocks private EntradaEtapaTecnica entrada;

    private Postulacion postulacion;

    @BeforeEach
    void armar() {
        postulacion = Postulacion.builder()
                .id(POSTULACION).organizacionId(ORGANIZACION).usuarioId(USUARIO)
                .vacanteId(VACANTE)
                .build();
    }

    // ============ Si hay con qué llenar la etapa ============

    @Test
    @DisplayName("con plantilla asignada, sí hay instrumento")
    void conPlantillaHayInstrumento() {
        assertThat(entrada.hayInstrumento(conPlantilla())).isTrue();
    }

    @Test
    @DisplayName("sin plantilla asignada, no lo hay")
    void sinPlantillaNoHayInstrumento() {
        assertThat(entrada.hayInstrumento(Vacante.builder().id(VACANTE).build())).isFalse();
    }

    @Test
    @DisplayName("el cuestionario en borrador NO cuenta como instrumento")
    void elCuestionarioEnBorradorNoCuenta() {
        /*
          Sin esto, una vacante a medio montar en automático escribía un error por CADA
          candidato que terminaba su retrato: el pase se intentaba, el creador se plantaba
          dentro, y «falta publicar el cuestionario» quedaba enterrado bajo un montón de
          excepciones que parecen una avería.
        */
        when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "PUBLICADA"))
                .thenReturn(Optional.empty());

        assertThat(entrada.hayInstrumento(conCuestionario())).isFalse();
    }

    @Test
    @DisplayName("con el cuestionario publicado sí cuenta")
    void elCuestionarioPublicadoCuenta() {
        when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "PUBLICADA"))
                .thenReturn(Optional.of(VersionBanco.builder().id(8L).build()));

        assertThat(entrada.hayInstrumento(conCuestionario())).isTrue();
    }

    // ============ Crear lo que se va a rendir ============

    @Test
    @DisplayName("con plantilla crea el intento, con la organización de la POSTULACIÓN")
    void creaElIntentoConLaOrganizacionDeLaPostulacion() {
        Vacante v = conPlantilla();
        Instant cierra = Instant.parse("2026-12-01T00:00:00Z");
        v.setPruebaCierraEn(cierra);

        entrada.crearAlEntrar(postulacion, v);

        verify(prueba).crearAlEntrar(ORGANIZACION, POSTULACION, VERSION_PRUEBA, cierra);
    }

    @Test
    @DisplayName("sin plantilla se planta: a una persona hay que decírselo")
    void sinPlantillaSePlanta() {
        assertThatThrownBy(() ->
                entrada.crearAlEntrar(postulacion, Vacante.builder().id(VACANTE).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plantilla de prueba");
    }

    @Test
    @DisplayName("con cuestionario crea el examen y lo ata a la postulación")
    void creaElCuestionarioYLoAta() {
        when(evaluaciones.crearTecnicaAlEntrar(ORGANIZACION, USUARIO, VACANTE)).thenReturn(99L);

        entrada.crearAlEntrar(postulacion, conCuestionario());

        assertThat(postulacion.getEvaluacionTecnicaId()).isEqualTo(99L);
        verify(postulaciones).save(postulacion);
    }

    @Test
    @DisplayName("quien ya tiene su examen no recibe un segundo")
    void noSeCreaUnSegundoExamen() {
        // Volver a entrar en la etapa —pasa al retroceder a alguien y volver a avanzarlo—
        // crearía un segundo examen dejando el primero, con sus respuestas y sus notas, sin
        // dueño. El intento de prueba no puede duplicarse porque su tabla lo impide; aquí la
        // regla la pone este camino, y debajo el índice único de la V53.
        postulacion.setEvaluacionTecnicaId(99L);

        entrada.crearAlEntrar(postulacion, conCuestionario());

        verify(evaluaciones, never()).crearTecnicaAlEntrar(anyLong(), anyLong(), anyLong());
        verify(postulaciones, never()).save(any());
    }

    private Vacante conPlantilla() {
        return Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                .versionPlantillaPruebaId(VERSION_PRUEBA).build();
    }

    private Vacante conCuestionario() {
        return Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                .instrumentoEtapaTecnica("CUESTIONARIO_TECNICO").build();
    }
}
