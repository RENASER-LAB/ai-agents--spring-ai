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

    /** Lo que devuelve el modelo cuando del archivo no salió nada aprovechable. */
    private ResultadoDatos vacio() {
        return new ResultadoDatos(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
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

    // ==================== Sin postulación detrás ====================

    @Test
    @DisplayName("Al perfil directo: el currículum subido al perfil no tiene postulación")
    void propuestaSinPostulacion() {
        // ⚠️ Es el camino nuevo del 05/09/2026. `trasPostular` sale de una
        // postulación; aquí solo hay una persona que subió su currículum, y lo
        // que se vuelca es exactamente lo mismo.
        // «Entró algo» se mide contra lo que aportó ESTA lectura, no contra lo
        // que hay en el perfil: aquí el currículum trajo un empleo, y por eso sí.
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL))
                // Mutable: el volcado ordena la lista que le devuelve el repositorio.
                .thenReturn(new java.util.ArrayList<>(
                        List.of(ExperienciaPerfil.builder().id(1L).orden(0).build())));

        boolean entro = servicio.proponerAlPerfil(PERSONA_ID,
                conExperiencia(new ExperienciaLeida(
                        "Analista", "Clínica San Juan", "2022-03", null, null)));

        assertThat(entro).isTrue();
        verify(experiencias).save(any());
    }

    @Test
    @DisplayName("Un currículum del que no salió nada NO es «lista», aunque el perfil esté lleno")
    void loQueNoAportoNadaNoEsLista() {
        // El fallo que arregla esta prueba: se medía el perfil entero, así que quien
        // había escrito su titular a mano y subía un PDF escaneado recibía «revisa lo
        // que encontramos» sin nada que revisar. La regla del puente —una ficha de la
        // que no salió nada se cierra NO_LEGIBLE— solo se cumplía con el perfil vacío.
        PerfilCandidato conTitularATeclado = PerfilCandidato.builder()
                .id(PERFIL).personaId(PERSONA_ID).titular("Analista de datos")
                .creadoEn(Instant.now()).actualizadoEn(Instant.now()).build();
        when(perfiles.findByPersonaId(PERSONA_ID))
                .thenReturn(Optional.of(conTitularATeclado));

        boolean entro = servicio.proponerAlPerfil(PERSONA_ID, vacio());

        assertThat(entro).isFalse();
        // Y lo que la persona escribió sigue ahí: no aportar nada no es borrar nada.
        assertThat(conTitularATeclado.getTitular()).isEqualTo("Analista de datos");
    }

    @Test
    @DisplayName("Lo que el modelo leyó y la persona ya tenía escrito SÍ cuenta como leído")
    void loQueYaEstabaEscritoCuenta() {
        // La otra mitad de la regla: el currículum describe su perfil aunque no haya
        // hueco donde poner nada. Decirle «no se pudo leer» sería igual de falso.
        PerfilCandidato conTitularATeclado = PerfilCandidato.builder()
                .id(PERFIL).personaId(PERSONA_ID).titular("Analista de datos")
                .creadoEn(Instant.now()).actualizadoEn(Instant.now()).build();
        when(perfiles.findByPersonaId(PERSONA_ID))
                .thenReturn(Optional.of(conTitularATeclado));

        boolean entro = servicio.proponerAlPerfil(PERSONA_ID, new ResultadoDatos(
                null, null, null, null, null, null, "Analista senior de datos",
                null, null, null, null, null, null, null));

        assertThat(entro).isTrue();
        assertThat(conTitularATeclado.getTitular()).isEqualTo("Analista de datos");
    }

    @Test
    @DisplayName("Lo que se descarta por venir inservible no cuenta como aporte")
    void loDescartadoNoCuenta() {
        // Una experiencia sin fecha de inicio parseable se descarta, y un currículum
        // del que solo salió eso no dio nada aprovechable.
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL))
                .thenReturn(new java.util.ArrayList<>());

        boolean entro = servicio.proponerAlPerfil(PERSONA_ID, conExperiencia(
                new ExperienciaLeida("Analista", "Clínica", "hace dos años", null, null)));

        assertThat(entro).isFalse();
        verify(experiencias, never()).save(any());
    }

    @Test
    @DisplayName("A una persona anonimizada no se le propone nada")
    void aLaAnonimizadaNoSeLePropone() {
        // El borrado del 29733 ya pasó por ahí: volver a escribirle el perfil
        // desharía lo que la ley obliga a hacer.
        when(personas.findById(PERSONA_ID)).thenReturn(Optional.of(
                Persona.builder().id(PERSONA_ID).anonimizadoEn(Instant.now()).build()));

        boolean entro = servicio.proponerAlPerfil(PERSONA_ID,
                conExperiencia(new ExperienciaLeida("Analista", "Clínica", "2022-03", null, null)));

        assertThat(entro).isFalse();
        verify(experiencias, never()).save(any());
    }

    @Test
    @DisplayName("Lo que propuso un currículum se distingue de lo que la persona escribió")
    void conservaLoPropuestoSoloCuentaLoDelCurriculum() {
        // Es lo que decide si el recibo de una postulación todavía vale: sin filas
        // CURRICULUM, aquella lectura ya no está en ningún sitio.
        when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL)).thenReturn(List.of(
                ExperienciaPerfil.builder().id(1L).origen("PERSONA").build()));
        when(educaciones.findByPerfilCandidatoIdOrderByOrden(PERFIL)).thenReturn(List.of());
        when(idiomas.findByPerfilCandidatoIdOrderByIdioma(PERFIL)).thenReturn(List.of());
        when(certificaciones.findByPerfilCandidatoIdOrderByNombre(PERFIL)).thenReturn(List.of());

        assertThat(servicio.conservaLoPropuestoDeUnCurriculum(PERSONA_ID)).isFalse();

        when(idiomas.findByPerfilCandidatoIdOrderByIdioma(PERFIL)).thenReturn(List.of(
                IdiomaPerfil.builder().id(2L).origen("CURRICULUM").build()));

        assertThat(servicio.conservaLoPropuestoDeUnCurriculum(PERSONA_ID)).isTrue();
    }

    @Test
    @DisplayName("Sin perfil (lo barrió la retención) no queda nada que aquella lectura propusiera")
    void sinPerfilNoConservaNada() {
        when(perfiles.findByPersonaId(PERSONA_ID)).thenReturn(Optional.empty());

        assertThat(servicio.conservaLoPropuestoDeUnCurriculum(PERSONA_ID)).isFalse();
    }

    @Test
    @DisplayName("Sin persona o sin resultado devuelve false en vez de reventar")
    void loVacioNoRevienta() {
        // Lo llama la cola: una excepción aquí tumba el trabajo entero por un
        // caso que no es un error, solo un vacío.
        assertThat(servicio.proponerAlPerfil(null, conExperiencia())).isFalse();
        assertThat(servicio.proponerAlPerfil(PERSONA_ID, null)).isFalse();
    }
}
