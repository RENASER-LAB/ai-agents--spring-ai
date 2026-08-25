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

@ExtendWith(MockitoExtension.class)
@DisplayName("El perfil que ve el panel")
class ServicioPerfilPanelImplTest {

    private static final long POSTULACION = 10L;
    private static final long ORGANIZACION = 1L;
    private static final long PERSONA = 30L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private com.renaser.ai.ai_engine.vacante.repository.VacanteRepository vacantes;
    @Mock private UsuarioRepository usuarios;
    @Mock private PintorDePerfil pintor;
    @Mock private com.renaser.ai.ai_engine.seguridad.service.Permisos permisos;

    private ServicioPerfilPanelImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPerfilPanelImpl(postulaciones, vacantes, usuarios, pintor, permisos);
        lenient().when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).usuarioId(20L)
                        .vacanteId(7L).build()));
        lenient().when(permisos.alcanceDe("ver_perfil_candidato")).thenReturn(
                new com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance(
                        com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance.Tipo.TODO, 99L));
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
    @DisplayName("Con el permiso limitado a SUS_VACANTES, el candidato de otro es un 404")
    void alcanceLimitadoNoVeLoAjeno() {
        // Sin esto, un rol con ver_perfil_candidato acotado a sus vacantes leia la
        // trayectoria —y con DIRECCION, la pretension salarial— de candidatos de
        // convocatorias ajenas. Los roles se configuran desde el panel: que hoy nadie
        // tenga esa forma no basta.
        when(permisos.alcanceDe("ver_perfil_candidato")).thenReturn(
                new com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance(
                        com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance.Tipo.SUS_VACANTES,
                        99L));
        when(vacantes.findById(7L)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.vacante.entity.Vacante.builder()
                        .id(7L).responsableUsuarioId(1234L).build()));

        assertThatThrownBy(() -> servicio.verDePostulacion(
                equipoCon("ver_perfil_candidato"), POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Con SUS_VACANTES y siendo el responsable, sí lo ve")
    void alcanceLimitadoVeLoSuyo() {
        when(permisos.alcanceDe("ver_perfil_candidato")).thenReturn(
                new com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance(
                        com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance.Tipo.SUS_VACANTES,
                        99L));
        when(vacantes.findById(7L)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.vacante.entity.Vacante.builder()
                        .id(7L).responsableUsuarioId(99L).build()));
        when(pintor.pintar(PERSONA)).thenReturn(conPretension());
        when(pintor.sinPretension(conPretension()))
                .thenAnswer(i -> new PintorDePerfilPuro().sinPretension(conPretension()));

        assertThat(servicio.verDePostulacion(equipoCon("ver_perfil_candidato"), POSTULACION)
                .titular()).isEqualTo("Analista");
    }

    @Test
    @DisplayName("Una postulación de otra organización es un 404, como en todo el panel")
    void otraOrganizacionEs404() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.verDePostulacion(
                equipoCon("ver_perfil_candidato"), POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);
    }

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
