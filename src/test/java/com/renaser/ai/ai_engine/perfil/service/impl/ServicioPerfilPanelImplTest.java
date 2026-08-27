package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.LecturaCv;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.Pretension;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("El perfil que ve el panel")
class ServicioPerfilPanelImplTest {

    private static final long POSTULACION = 10L;
    private static final long ORGANIZACION = 1L;
    private static final long PERSONA = 30L;

    @Mock private com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante alcance;
    @Mock private UsuarioRepository usuarios;
    @Mock private PintorDePerfil pintor;

    private ServicioPerfilPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPerfilPanelImpl(alcance, usuarios, pintor);
        // Quién alcanza qué ya no se decide aquí: lo decide AlcanceSobreLaVacante, y allí
        // tiene sus propias pruebas. Lo que se comprueba en esta clase es que se le pregunta
        // por el permiso correcto y que lo que conteste llega intacto al llamador.
        lenient().when(alcance.laPostulacionVisible(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(POSTULACION),
                        org.mockito.ArgumentMatchers.eq("ver_perfil_candidato")))
                .thenReturn(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).usuarioId(20L)
                        .vacanteId(7L).build());
        lenient().when(usuarios.findById(20L)).thenReturn(Optional.of(
                Usuario.builder().id(20L).personaId(PERSONA).build()));
    }

    private ContextoUsuario equipoCon(String... permisos) {
        Map<String, String> mapa = new java.util.HashMap<>();
        for (String p : permisos) {
            mapa.put(p, "TODO");
        }
        return new ContextoUsuario(99L, 98L, ORGANIZACION, "EQUIPO", List.of(1L), mapa);
    }

    private PerfilCompleto conPretension() {
        return new PerfilCompleto("Analista", null, List.of(), null, null, null,
                new Pretension(new BigDecimal("3500"), new BigDecimal("4200"), "PEN"),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new LecturaCv("LISTA", null));
    }

    @Test
    @DisplayName("Sin ver_pretension, la pretensión no viaja — el resto del perfil sí")
    void sinPermisoNoViajaLaPretension() {
        when(pintor.pintar(PERSONA)).thenReturn(conPretension());
        when(pintor.sinPretension(conPretension()))
                .thenAnswer(i -> new PintorDePerfilPuro().sinPretension(conPretension()));

        PerfilCompleto visto = servicio.verDePostulacion(
                equipoCon("ver_perfil_candidato"), POSTULACION);

        assertThat(visto.pretension()).isNull();
        assertThat(visto.titular()).isEqualTo("Analista");
    }

    @Test
    @DisplayName("Con ver_pretension sí viaja: la ve quien negocia, cuando toca")
    void conPermisoViaja() {
        when(pintor.pintar(PERSONA)).thenReturn(conPretension());

        PerfilCompleto visto = servicio.verDePostulacion(
                equipoCon("ver_perfil_candidato", "ver_pretension"), POSTULACION);

        assertThat(visto.pretension()).isNotNull();
        assertThat(visto.pretension().moneda()).isEqualTo("PEN");
    }

    @Test
    @DisplayName("Lo que el guardián no deja ver sale como 404, sin envolver ni suavizar")
    void loQueElGuardianNiegaSaleTalCual() {
        // Sin este guardián, un rol con ver_perfil_candidato acotado a sus vacantes leia la
        // trayectoria —y con DIRECCION, la pretension salarial— de candidatos de
        // convocatorias ajenas. Los roles se configuran desde el panel: que hoy nadie
        // tenga esa forma no basta.
        when(alcance.laPostulacionVisible(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(POSTULACION),
                org.mockito.ArgumentMatchers.eq("ver_perfil_candidato")))
                .thenThrow(new ResourceNotFoundException("Postulación", "id", POSTULACION));

        assertThatThrownBy(() -> servicio.verDePostulacion(
                equipoCon("ver_perfil_candidato"), POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        // Y no se llega a pintar nada de quien no le toca.
        verifyNoInteractions(pintor, usuarios);
    }

    @Test
    @DisplayName("Se pregunta por ver_perfil_candidato, no por otro permiso del panel")
    void sePreguntaPorSuPermiso() {
        // El error caro al migrar es pedir el alcance del permiso de al lado: nada falla, y
        // un rol con este acotado y otro libre acabaría leyendo perfiles ajenos.
        when(pintor.pintar(PERSONA)).thenReturn(conPretension());
        when(pintor.sinPretension(conPretension()))
                .thenAnswer(i -> new PintorDePerfilPuro().sinPretension(conPretension()));

        assertThat(servicio.verDePostulacion(equipoCon("ver_perfil_candidato"), POSTULACION)
                .titular()).isEqualTo("Analista");

        verify(alcance).laPostulacionVisible(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(POSTULACION),
                org.mockito.ArgumentMatchers.eq("ver_perfil_candidato"));
    }

    // El 404 de una postulación de otra empresa se prueba ahora donde se decide, en
    // AlcanceSobreLaVacanteTest#laDeOtraEmpresaNiSeMira. Repetirlo aquí sería comprobar el
    // mock, no el código.

    /** El quitado de la pretension de verdad, sin repositorios: para no mockear al mockeado. */
    private static class PintorDePerfilPuro {
        PerfilCompleto sinPretension(PerfilCompleto c) {
            return new PerfilCompleto(c.titular(), c.resumen(), c.habilidades(),
                    c.experienciaMeses(), c.ubicacion(), c.disponibilidad(), null,
                    c.experiencia(), c.educacion(), c.idiomas(), c.certificaciones(),
                    c.enlaces(), c.lecturaCv());
        }
    }
}
