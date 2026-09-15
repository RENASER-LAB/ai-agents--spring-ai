package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfil.service.ServicioCicloVidaPerfil;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El paso del tiempo sobre los perfiles")
class BarridoRetencionPerfilTest {

    private static final long PERSONA = 30L;

    @Mock private PerfilCandidatoRepository perfiles;
    @Mock private PostulacionRepository postulaciones;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioCicloVidaPerfil cicloVida;

    private BarridoRetencionPerfil barrido;

    @BeforeEach
    void crearElBarrido() {
        barrido = new BarridoRetencionPerfil(perfiles, postulaciones, organizaciones,
                parametros, cicloVida);
        lenient().when(organizaciones.findAll()).thenReturn(List.of(
                Organizacion.builder().id(1L).build()));
        lenient().when(parametros.entero(eq(1L), eq("meses_conservar_perfil"), eq(24)))
                .thenReturn(24);
    }

    /**
     * El texto legal promete un plazo, y el barrido aplica otro: ahí se rompe.
     *
     * <p>El consentimiento de la V54 le dice al candidato «si paso 24 meses sin actividad,
     * mi perfil se elimina». Ese 24 no sale de ninguna parte del texto: es
     * {@link BarridoRetencionPerfil#MESES_POR_DEFECTO}. Quien lo cambie sin republicar el
     * texto deja a la plataforma borrando datos en un plazo que nadie consintió —y lo
     * contrario, conservándolos de más—, que es exactamente lo que la ley 29733 pide
     * declarar. No hay forma de que git lo avise: son dos archivos que no se citan.
     */
    @Test
    @DisplayName("el plazo que promete el texto legal es el que aplica el barrido")
    void elPlazoDelTextoLegalEsElQueAplicaElBarrido() throws Exception {
        String migracion = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/"
                        + "V54__el_consentimiento_dice_quien_trata_los_datos.sql"));

        assertThat(migracion)
                .as("el texto PLATAFORMA de la V54 nombra el plazo de conservación")
                .contains("Si paso " + BarridoRetencionPerfil.MESES_POR_DEFECTO
                        + " meses sin actividad");
    }

    private PerfilCandidato perfilTocado(Instant cuando) {
        return PerfilCandidato.builder().id(40L).personaId(PERSONA)
                .creadoEn(cuando).actualizadoEn(cuando).build();
    }

    private Instant haceMeses(int meses) {
        return Instant.now().minus(meses * 30L, ChronoUnit.DAYS);
    }

    @Test
    @DisplayName("Un perfil con años de silencio se borra: es lo que promete el consentimiento")
    void elViejoSeBorra() {
        when(perfiles.findAll()).thenReturn(List.of(perfilTocado(haceMeses(30))));
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());

        assertThat(barrido.barrer()).isEqualTo(1);
        verify(cicloVida).borrarPorPersona(PERSONA);
    }

    @Test
    @DisplayName("Postular cuenta como actividad aunque el perfil no se haya tocado")
    void postularEsActividad() {
        when(perfiles.findAll()).thenReturn(List.of(perfilTocado(haceMeses(30))));
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of(
                Postulacion.builder().id(1L).creadoEn(haceMeses(2)).build()));

        assertThat(barrido.barrer()).isZero();
        verify(cicloVida, never()).borrarPorPersona(anyLong());
    }

    @Test
    @DisplayName("Entre organizaciones con plazos distintos manda el más largo: borrar antes "
            + "de tiempo es irreversible")
    void mandaElPlazoMasLargo() {
        when(organizaciones.findAll()).thenReturn(List.of(
                Organizacion.builder().id(1L).build(),
                Organizacion.builder().id(2L).build()));
        when(parametros.entero(eq(1L), any(), eq(24))).thenReturn(12);
        when(parametros.entero(eq(2L), any(), eq(24))).thenReturn(36);
        // 30 meses sin actividad: caducado para la organizacion 1, vivo para la 2.
        when(perfiles.findAll()).thenReturn(List.of(perfilTocado(haceMeses(30))));
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());

        assertThat(barrido.barrer()).isZero();
        verify(cicloVida, never()).borrarPorPersona(anyLong());
    }

    @Test
    @DisplayName("El reciente no se toca")
    void elRecienteSeQueda() {
        when(perfiles.findAll()).thenReturn(List.of(perfilTocado(haceMeses(3))));
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());

        assertThat(barrido.barrer()).isZero();
    }
}
