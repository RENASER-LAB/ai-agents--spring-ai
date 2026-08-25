package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.perfil.entity.ExperienciaPerfil;
import com.renaser.ai.ai_engine.perfil.entity.IdiomaPerfil;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelEducativoRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelIdiomaRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ExperienciaLeida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.IdiomaLeido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El merge que decide si la IA ayuda o estorba. Casi todo lo que se prueba aquí es lo que
 * NO debe pasar: pisar lo que la persona escribió es el fallo que convierte la herramienta
 * en un problema.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La propuesta del currículum al perfil")
class ServicioPropuestaPerfilImplTest {

    private static final long POSTULACION = 10L;
    private static final long USUARIO = 20L;
    private static final long PERSONA_ID = 30L;
    private static final long PERFIL = 40L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;
    @Mock private PerfilCandidatoRepository perfiles;
    @Mock private ExperienciaPerfilRepository experiencias;
    @Mock private EducacionPerfilRepository educaciones;
    @Mock private IdiomaPerfilRepository idiomas;
    @Mock private CertificacionPerfilRepository certificaciones;
    @Mock private EnlacePerfilRepository enlaces;
    @Mock private NivelEducativoRepository nivelesEducativos;
    @Mock private NivelIdiomaRepository nivelesIdioma;

    private ServicioPropuestaPerfilImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPropuestaPerfilImpl(postulaciones, usuarios, personas, perfiles,
                experiencias, educaciones, idiomas, certificaciones, enlaces,
                nivelesEducativos, nivelesIdioma);
        lenient().when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(
                Postulacion.builder().id(POSTULACION).usuarioId(USUARIO).build()));
        lenient().when(usuarios.findById(USUARIO)).thenReturn(Optional.of(
                Usuario.builder().id(USUARIO).personaId(PERSONA_ID).build()));
        lenient().when(personas.findById(PERSONA_ID)).thenReturn(Optional.of(
                Persona.builder().id(PERSONA_ID).build()));
        lenient().when(perfiles.findByPersonaId(PERSONA_ID))
                .thenReturn(Optional.of(perfil()));
        lenient().when(perfiles.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(experiencias.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(idiomas.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private PerfilCandidato perfil() {
        return PerfilCandidato.builder().id(PERFIL).personaId(PERSONA_ID)
                .creadoEn(Instant.now()).actualizadoEn(Instant.now()).build();
    }

    private ResultadoDatos conExperiencia(ExperienciaLeida... leidas) {
        return new ResultadoDatos(null, null, null, null, null, null, null, null, null, null,
                List.of(leidas), null, null, null);
    }

    @Test
    @DisplayName("Lo leído entra como CURRICULUM y sin confirmar: nadie lo ha revisado")
    void loLeidoEntraSinConfirmar() {
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL)).thenReturn(
                new java.util.ArrayList<>());

        servicio.proponer(POSTULACION, conExperiencia(
                new ExperienciaLeida("Analista", "Clínica San Juan", "2022-03", null, null)));

        ArgumentCaptor<ExperienciaPerfil> guardada =
                ArgumentCaptor.forClass(ExperienciaPerfil.class);
        verify(experiencias).save(guardada.capture());
        assertThat(guardada.getValue().getOrigen()).isEqualTo("CURRICULUM");
        assertThat(guardada.getValue().getConfirmadoEn()).isNull();
        assertThat(guardada.getValue().getDesde()).isEqualTo(LocalDate.of(2022, 3, 1));
    }

    @Test
    @DisplayName("Lo que la persona escribió NO se pisa, diga lo que diga el currículum")
    void noPisaLoEscritoPorLaPersona() {
        ExperienciaPerfil escrita = ExperienciaPerfil.builder()
                .id(1L).perfilCandidatoId(PERFIL)
                .puesto("Analista").empresa("Clínica San Juan")
                .desde(LocalDate.of(2021, 1, 1)).origen("PERSONA")
                .confirmadoEn(Instant.now()).orden(1).build();
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL))
                .thenReturn(new java.util.ArrayList<>(List.of(escrita)));

        servicio.proponer(POSTULACION, conExperiencia(
                new ExperienciaLeida("analista", "clínica san juan", "2022-03", null,
                        "otra cosa")));

        // La clave coincide (sin mayusculas ni tildes), asi que no es un alta; y es de la
        // persona, asi que tampoco se actualiza. No se guarda nada.
        verify(experiencias, never()).save(any());
        assertThat(escrita.getDesde()).isEqualTo(LocalDate.of(2021, 1, 1));
    }

    @Test
    @DisplayName("Lo CURRICULUM confirmado tampoco se pisa: confirmarlo lo hizo suyo")
    void noPisaLoConfirmado() {
        ExperienciaPerfil confirmada = ExperienciaPerfil.builder()
                .id(1L).perfilCandidatoId(PERFIL)
                .puesto("Analista").empresa("ACME")
                .desde(LocalDate.of(2020, 1, 1)).origen("CURRICULUM")
                .confirmadoEn(Instant.now()).orden(1).build();
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL))
                .thenReturn(new java.util.ArrayList<>(List.of(confirmada)));

        servicio.proponer(POSTULACION, conExperiencia(
                new ExperienciaLeida("Analista", "ACME", "2023-05", null, null)));

        verify(experiencias, never()).save(any());
    }

    @Test
    @DisplayName("Lo CURRICULUM sin confirmar sí se actualiza: solo el último CV manda")
    void actualizaLoNoConfirmado() {
        ExperienciaPerfil propuesta = ExperienciaPerfil.builder()
                .id(1L).perfilCandidatoId(PERFIL)
                .puesto("Analista").empresa("ACME")
                .desde(LocalDate.of(2020, 1, 1)).origen("CURRICULUM")
                .confirmadoEn(null).orden(1).build();
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL))
                .thenReturn(new java.util.ArrayList<>(List.of(propuesta)));

        servicio.proponer(POSTULACION, conExperiencia(
                new ExperienciaLeida("Analista", "ACME", "2021-06", "2023-01", null)));

        verify(experiencias).save(propuesta);
        assertThat(propuesta.getDesde()).isEqualTo(LocalDate.of(2021, 6, 1));
        assertThat(propuesta.getHasta()).isEqualTo(LocalDate.of(2023, 1, 1));
    }

    @Test
    @DisplayName("Una experiencia sin fecha de inicio parseable se descarta, no se inventa")
    void sinFechaSeDescarta() {
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL)).thenReturn(
                new java.util.ArrayList<>());

        servicio.proponer(POSTULACION, conExperiencia(
                new ExperienciaLeida("Analista", "ACME", "hace tiempo", null, null),
                new ExperienciaLeida("Jefe", "ACME", "2023-13", null, null)));

        verify(experiencias, never()).save(any());
    }

    @Test
    @DisplayName("Un idioma con nivel fuera del catálogo se descarta entero")
    void idiomaConNivelInventadoSeDescarta() {
        when(idiomas.findByPerfilCandidatoIdOrderByIdioma(PERFIL)).thenReturn(
                new java.util.ArrayList<>());
        when(nivelesIdioma.existsById("B2")).thenReturn(true);
        when(nivelesIdioma.existsById("FLUIDO")).thenReturn(false);

        servicio.proponer(POSTULACION, new ResultadoDatos(null, null, null, null, null, null,
                null, null, null, null, null, null,
                List.of(new IdiomaLeido("Inglés", "B2"), new IdiomaLeido("Francés", "FLUIDO")),
                null));

        ArgumentCaptor<IdiomaPerfil> guardado = ArgumentCaptor.forClass(IdiomaPerfil.class);
        verify(idiomas).save(guardado.capture());
        assertThat(guardado.getValue().getIdioma()).isEqualTo("Inglés");
    }

    @Test
    @DisplayName("La cabecera solo rellena huecos: lo que ya tiene valor no se toca")
    void cabeceraSoloHuecos() {
        PerfilCandidato conResumen = perfil();
        conResumen.setResumen("Lo escribí yo");
        when(perfiles.findByPersonaId(PERSONA_ID)).thenReturn(Optional.of(conResumen));

        servicio.proponer(POSTULACION, new ResultadoDatos(null, null, null,
                "Resumen del modelo", List.of("Excel"), 60, "Analista senior", null, null,
                null, null, null, null, null));

        assertThat(conResumen.getResumen()).isEqualTo("Lo escribí yo");
        assertThat(conResumen.getTitular()).isEqualTo("Analista senior");   // este sí era hueco
        assertThat(conResumen.getHabilidades()).isEqualTo("Excel");
        assertThat(conResumen.getExperienciaMeses()).isEqualTo(60);
    }

    @Test
    @DisplayName("Las listas en null (un trabajo con el prompt viejo) no hacen nada")
    void listasNullNoHacenNada() {
        servicio.proponer(POSTULACION, new ResultadoDatos("Ana", null, null, null, null, null,
                null, null, null, null, null, null, null, null));

        verify(experiencias, never()).save(any());
        verify(idiomas, never()).save(any());
    }

    @Test
    @DisplayName("Si el perfil no existía, se crea; la persona no tiene que hacer nada antes")
    void creaElPerfilSiNoExiste() {
        when(perfiles.findByPersonaId(PERSONA_ID)).thenReturn(Optional.empty());
        when(perfiles.save(any())).thenAnswer(i -> {
            PerfilCandidato p = i.getArgument(0);
            p.setId(PERFIL);
            return p;
        });

        servicio.proponer(POSTULACION, new ResultadoDatos(null, null, null, "Resumen", null,
                null, null, null, null, null, null, null, null, null));

        verify(perfiles, org.mockito.Mockito.atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("Una persona anonimizada por el borrado no recibe perfil: sería resucitarla")
    void personaAnonimizadaNoRecibeNada() {
        when(personas.findById(PERSONA_ID)).thenReturn(Optional.of(
                Persona.builder().id(PERSONA_ID).anonimizadoEn(Instant.now()).build()));

        servicio.proponer(POSTULACION, new ResultadoDatos(null, null, null, "Resumen", null,
                null, null, null, null, null, null, null, null, null));

        verify(perfiles, never()).save(any());
    }

    @Test
    @DisplayName("La clave natural ignora mayúsculas, tildes y espacios de más")
    void claveNormalizada() {
        assertThat(ServicioPropuestaPerfilImpl.clave("  Analista   Sénior "))
                .isEqualTo(ServicioPropuestaPerfilImpl.clave("analista senior"));
    }

    @Test
    @DisplayName("Las fechas: AAAA-MM al primer día del mes, AAAA a enero, y lo raro a null")
    void fechas() {
        assertThat(ServicioPropuestaPerfilImpl.mes("2023-05"))
                .isEqualTo(LocalDate.of(2023, 5, 1));
        assertThat(ServicioPropuestaPerfilImpl.mes("2023"))
                .isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(ServicioPropuestaPerfilImpl.mes("2023-13")).isNull();
        assertThat(ServicioPropuestaPerfilImpl.mes("hace dos años")).isNull();
        assertThat(ServicioPropuestaPerfilImpl.mes(null)).isNull();
    }
}
