package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Celular;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El celular de cada postulante del visor, de una vez para toda la lista (spec
 * la-ia-sabe-que-dia-es-hoy, AC-14 y AC-15): el de su cuenta y, si no tiene, el del CV.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Los celulares del visor")
class CelularesDelVisorTest {

    @Mock private PostulacionRepository postulaciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;
    @Mock private DatoCvRepository datosCv;

    @InjectMocks private CelularesDelVisor celulares;

    @Test
    @DisplayName("la cuenta manda sobre el CV; sin cuenta, el CV; sin ninguno, no sale")
    void cuentaCvONada() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L);
        when(postulaciones.findAllById(ids)).thenReturn(List.of(
                Postulacion.builder().id(1L).usuarioId(11L).build(),
                Postulacion.builder().id(2L).usuarioId(12L).build(),
                Postulacion.builder().id(3L).usuarioId(13L).build(),
                Postulacion.builder().id(4L).build()));
        when(usuarios.findAllById(anyIterable())).thenReturn(List.of(
                Usuario.builder().id(11L).personaId(21L).build(),
                Usuario.builder().id(12L).personaId(22L).build(),
                Usuario.builder().id(13L).build()));
        when(personas.findAllById(anyIterable())).thenReturn(List.of(
                Persona.builder().id(21L).telefono("+51987654321").build(),
                Persona.builder().id(22L).build()));
        when(datosCv.findByPostulacionIdIn(ids)).thenReturn(List.of(
                DatoCv.builder().postulacionId(1L).telefono("912 345 678").build(),
                DatoCv.builder().postulacionId(2L).telefono("(01) 456-7890").build()));

        Map<Long, Celular> suyos = celulares.de(ids);

        assertThat(suyos.get(1L).texto()).isEqualTo("987 654 321");
        assertThat(suyos.get(1L).whatsapp()).isEqualTo("https://wa.me/51987654321");
        assertThat(suyos.get(2L)).isEqualTo(new Celular("(01) 456-7890", "tel:014567890", null));
        assertThat(suyos).doesNotContainKeys(3L, 4L);
    }

    /**
     * D-1: de quien pidió borrar sus datos no sale ningún celular. El borrado vacía el de la
     * cuenta, pero el que la IA leyó del CV sigue en {@code dato_cv.telefono}.
     */
    @Test
    @DisplayName("D-1 · de una persona anonimizada no sale ni el celular de la cuenta ni el del CV")
    void anonimizadaSinCelular() {
        List<Long> ids = List.of(1L, 2L, 3L);
        when(postulaciones.findAllById(ids)).thenReturn(List.of(
                Postulacion.builder().id(1L).usuarioId(11L).build(),
                Postulacion.builder().id(2L).usuarioId(12L).build(),
                Postulacion.builder().id(3L).usuarioId(13L).build()));
        when(usuarios.findAllById(anyIterable())).thenReturn(List.of(
                Usuario.builder().id(11L).personaId(21L).build(),
                Usuario.builder().id(12L).personaId(22L).build(),
                Usuario.builder().id(13L).personaId(23L).build()));
        when(personas.findAllById(anyIterable())).thenReturn(List.of(
                // Borrada como lo hace el borrado de datos: sin teléfono y con la fecha puesta
                Persona.builder().id(21L).anonimizadoEn(Instant.parse("2026-11-01T10:00:00Z")).build(),
                // Aunque quedara un número en la cuenta, tampoco sale
                Persona.builder().id(22L).telefono("987 654 321")
                        .anonimizadoEn(Instant.parse("2026-11-01T10:00:00Z")).build(),
                // La de al lado, sin anonimizar, sigue enseñando el del CV
                Persona.builder().id(23L).build()));
        when(datosCv.findByPostulacionIdIn(ids)).thenReturn(List.of(
                DatoCv.builder().postulacionId(1L).telefono("912 345 678").build(),
                DatoCv.builder().postulacionId(2L).telefono("923 456 789").build(),
                DatoCv.builder().postulacionId(3L).telefono("934 567 890").build()));

        Map<Long, Celular> suyos = celulares.de(ids);

        assertThat(suyos).doesNotContainKeys(1L, 2L);
        assertThat(suyos.get(3L).texto()).isEqualTo("934 567 890");
    }

    @Test
    @DisplayName("sin postulantes no consulta nada")
    void sinNadie() {
        assertThat(celulares.de(List.of())).isEmpty();
        assertThat(celulares.de(null)).isEmpty();
        verifyNoInteractions(postulaciones, usuarios, personas, datosCv);
    }
}
