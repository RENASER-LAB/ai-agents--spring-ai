package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarCabecera;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarEnlace;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarExperiencia;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.Pretension;
import com.renaser.ai.ai_engine.perfil.entity.ExperienciaPerfil;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelEducativoRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelIdiomaRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El perfil editado por su dueño")
class ServicioPerfilPortalImplTest {

    private static final long PERSONA = 30L;
    private static final long PERFIL = 40L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            20L, PERSONA, 1L, "CANDIDATO", List.of(), Map.of());

    @Mock private PerfilCandidatoRepository perfiles;
    @Mock private ExperienciaPerfilRepository experiencias;
    @Mock private EducacionPerfilRepository educaciones;
    @Mock private IdiomaPerfilRepository idiomas;
    @Mock private CertificacionPerfilRepository certificaciones;
    @Mock private EnlacePerfilRepository enlaces;
    @Mock private NivelEducativoRepository nivelesEducativos;
    @Mock private NivelIdiomaRepository nivelesIdioma;
    @Mock private PintorDePerfil pintor;

    private ServicioPerfilPortalImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPerfilPortalImpl(perfiles, experiencias, educaciones, idiomas,
                certificaciones, enlaces, nivelesEducativos, nivelesIdioma, pintor);
        lenient().when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(perfil()));
        lenient().when(perfiles.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private PerfilCandidato perfil() {
        return PerfilCandidato.builder().id(PERFIL).personaId(PERSONA)
                .creadoEn(Instant.now()).actualizadoEn(Instant.now()).build();
    }

    @Test
    @DisplayName("La pretensión a medias no se guarda: un mínimo sin moneda no dice nada")
    void pretensionAMediasEs400() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                servicio.editarCabecera(QUIEN, new EditarCabecera(null, null, null, null,
                        null, null, new Pretension(new BigDecimal("3500"), null, null))));
    }

    @Test
    @DisplayName("El máximo por debajo del mínimo tampoco")
    void maximoMenorQueMinimoEs400() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                servicio.editarCabecera(QUIEN, new EditarCabecera(null, null, null, null,
                        null, null, new Pretension(new BigDecimal("4000"),
                        new BigDecimal("3000"), "PEN"))));
    }

    @Test
    @DisplayName("La pretensión completa entra, y las habilidades se unen como siempre")
    void cabeceraCompleta() {
        PerfilCandidato p = perfil();
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(p));

        servicio.editarCabecera(QUIEN, new EditarCabecera("Analista", "Mi resumen",
                List.of("Excel", "SQL"), 60, "Arequipa", "Inmediata",
                new Pretension(new BigDecimal("3500"), new BigDecimal("4200"), "PEN")));

        assertThat(p.getPretensionMin()).isEqualByComparingTo("3500");
        assertThat(p.getPretensionMoneda()).isEqualTo("PEN");
        assertThat(p.getHabilidades()).isEqualTo("Excel | SQL");
    }

    @Test
    @DisplayName("Editar un elemento lo convierte en «escrito por mí»")
    void editarLoHaceSuyo() {
        ExperienciaPerfil propuesta = ExperienciaPerfil.builder()
                .id(1L).perfilCandidatoId(PERFIL).puesto("Analista").empresa("ACME")
                .desde(LocalDate.of(2020, 1, 1)).origen("CURRICULUM").confirmadoEn(null)
                .orden(1).build();
        when(experiencias.findById(1L)).thenReturn(Optional.of(propuesta));

        servicio.editarExperiencia(QUIEN, 1L, new EditarExperiencia("Analista senior",
                "ACME", LocalDate.of(2020, 1, 1), null, null));

        assertThat(propuesta.getOrigen()).isEqualTo("PERSONA");
        assertThat(propuesta.getConfirmadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Confirmar valida el dato conservando que salió del currículum")
    void confirmarConservaElOrigen() {
        ExperienciaPerfil propuesta = ExperienciaPerfil.builder()
                .id(1L).perfilCandidatoId(PERFIL).puesto("Analista").empresa("ACME")
                .desde(LocalDate.of(2020, 1, 1)).origen("CURRICULUM").confirmadoEn(null)
                .orden(1).build();
        when(experiencias.findById(1L)).thenReturn(Optional.of(propuesta));

        servicio.confirmarExperiencia(QUIEN, 1L);

        assertThat(propuesta.getOrigen()).isEqualTo("CURRICULUM");
        assertThat(propuesta.getConfirmadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Un elemento de otra persona responde 404, no 403: decir «prohibido» ya "
            + "confirmaría que existe")
    void loAjenoEs404() {
        ExperienciaPerfil ajena = ExperienciaPerfil.builder()
                .id(9L).perfilCandidatoId(777L).puesto("X").empresa("Y")
                .desde(LocalDate.of(2020, 1, 1)).origen("PERSONA").orden(1).build();
        when(experiencias.findById(9L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> servicio.borrarExperiencia(QUIEN, 9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Una experiencia que termina antes de empezar no entra")
    void fechasCruzadasEs400() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                servicio.crearExperiencia(QUIEN, new EditarExperiencia("Analista", "ACME",
                        LocalDate.of(2022, 5, 1), LocalDate.of(2021, 1, 1), null)));
    }

    @Test
    @DisplayName("Un LinkedIn que no es de linkedin.com responde 400")
    void linkedinFalsoEs400() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                servicio.crearEnlace(QUIEN, new EditarEnlace("LINKEDIN",
                        "https://misitio.com/perfil")));
    }

    @Test
    @DisplayName("El mismo enlace dos veces responde 409")
    void enlaceRepetidoEs409() {
        when(enlaces.existsByPerfilCandidatoIdAndTipoAndUrl(PERFIL, "GITHUB",
                "https://github.com/camila")).thenReturn(true);

        assertThatIllegalStateException().isThrownBy(() ->
                servicio.crearEnlace(QUIEN, new EditarEnlace("GITHUB",
                        "https://github.com/camila")));
    }

    @Test
    @DisplayName("Reordenar exige exactamente los elementos que hay, una vez cada uno")
    void reordenarConIdsAjenosEs400() {
        ExperienciaPerfil una = ExperienciaPerfil.builder().id(1L).perfilCandidatoId(PERFIL)
                .puesto("A").empresa("B").desde(LocalDate.of(2020, 1, 1))
                .origen("PERSONA").orden(1).build();
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL))
                .thenReturn(List.of(una));

        assertThatIllegalArgumentException().isThrownBy(() ->
                servicio.reordenarExperiencia(QUIEN, List.of(1L, 99L)));
    }

    @Test
    @DisplayName("Un idioma repetido no entra dos veces: se edita el que hay")
    void idiomaRepetidoEs409() {
        when(idiomas.findByPerfilCandidatoIdOrderByIdioma(PERFIL)).thenReturn(List.of(
                com.renaser.ai.ai_engine.perfil.entity.IdiomaPerfil.builder()
                        .id(1L).perfilCandidatoId(PERFIL).idioma("Inglés").nivelCodigo("B2")
                        .origen("PERSONA").build()));
        when(nivelesIdioma.existsById("C1")).thenReturn(true);

        assertThatIllegalStateException().isThrownBy(() ->
                servicio.crearIdioma(QUIEN,
                        new com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarIdioma(
                                "inglés", "C1")));
    }

    @Test
    @DisplayName("Crear un elemento lo deja como «escrito por mí» desde el primer momento")
    void loCreadoNaceSuyo() {
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL)).thenReturn(List.of());
        when(experiencias.save(any())).thenAnswer(i -> {
            ExperienciaPerfil e = i.getArgument(0);
            e.setId(5L);
            return e;
        });

        servicio.crearExperiencia(QUIEN, new EditarExperiencia("Analista", "ACME",
                LocalDate.of(2022, 1, 1), null, null));

        verify(experiencias).save(org.mockito.ArgumentMatchers.argThat(e ->
                "PERSONA".equals(e.getOrigen()) && e.getConfirmadoEn() != null));
    }
}
