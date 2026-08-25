package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Los cuatro estados de la lectura del currículum, que es lo que el candidato mira mientras
 * espera.
 *
 * <p>Se prueban los cuatro porque el primer intento derivaba este estado de {@code comoVa},
 * que contesta cómo va el RETRATO: un evaluador caído decía «tu currículum no se pudo leer»
 * de un archivo perfectamente leído, y un retrato terminado sin ficha se quedaba en
 * «leyendo» para siempre. Ninguna de las dos cosas daba error.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El estado de la lectura del currículum")
class PintorDePerfilTest {

    private static final long PERSONA = 30L;
    private static final long PERFIL = 40L;
    private static final long POSTULACION = 10L;

    @Mock private PerfilCandidatoRepository perfiles;
    @Mock private ExperienciaPerfilRepository experiencias;
    @Mock private EducacionPerfilRepository educaciones;
    @Mock private IdiomaPerfilRepository idiomas;
    @Mock private CertificacionPerfilRepository certificaciones;
    @Mock private EnlacePerfilRepository enlaces;
    @Mock private PostulacionRepository postulaciones;
    @Mock private CvRepository cvs;
    @Mock private DatoCvRepository datosCv;
    @Mock private ColaCalificacionIa cola;

    private PintorDePerfil pintor;

    @BeforeEach
    void crearElPintor() {
        pintor = new PintorDePerfil(perfiles, experiencias, educaciones, idiomas,
                certificaciones, enlaces, postulaciones, cvs, datosCv, cola);
        lenient().when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(
                PerfilCandidato.builder().id(PERFIL).personaId(PERSONA)
                        .creadoEn(Instant.now()).actualizadoEn(Instant.now()).build()));
        lenient().when(experiencias.findByPerfilCandidatoIdOrderByOrden(PERFIL))
                .thenReturn(List.of());
        lenient().when(educaciones.findByPerfilCandidatoIdOrderByOrden(PERFIL))
                .thenReturn(List.of());
        lenient().when(idiomas.findByPerfilCandidatoIdOrderByIdioma(PERFIL))
                .thenReturn(List.of());
        lenient().when(certificaciones.findByPerfilCandidatoIdOrderByNombre(PERFIL))
                .thenReturn(List.of());
        lenient().when(enlaces.findByPerfilCandidatoIdOrderByTipo(PERFIL)).thenReturn(List.of());
    }

    private void hayUnaPostulacionConCv() {
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of(
                Postulacion.builder().id(POSTULACION).creadoEn(Instant.now()).build()));
        when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                Cv.builder().id(1L).postulacionId(POSTULACION).archivoOriginalId(100L).build()));
    }

    @Test
    @DisplayName("SIN_CV · todavía no ha postulado con ningún archivo")
    void sinCv() {
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());

        assertThat(pintor.pintar(PERSONA).lecturaCv().estado()).isEqualTo("SIN_CV");
    }

    @Test
    @DisplayName("LISTA · hay ficha: el archivo se leyó y hay algo que revisar")
    void lista() {
        hayUnaPostulacionConCv();
        when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                DatoCv.builder().id(7L).postulacionId(POSTULACION).nombre("Camila").build()));

        assertThat(pintor.pintar(PERSONA).lecturaCv().estado()).isEqualTo("LISTA");
    }

    @Test
    @DisplayName("EN_CURSO · la lectura está corriendo ahora mismo")
    void enCurso() {
        hayUnaPostulacionConCv();
        when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(cola.comoVaLaLectura(POSTULACION)).thenReturn("EN_CURSO");

        assertThat(pintor.pintar(PERSONA).lecturaCv().estado()).isEqualTo("EN_CURSO");
    }

    @Test
    @DisplayName("NO_LEGIBLE · la lectura se agotó en reintentos (un PDF escaneado)")
    void noLegiblePorFallo() {
        hayUnaPostulacionConCv();
        when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(cola.comoVaLaLectura(POSTULACION)).thenReturn("FALLIDA");

        assertThat(pintor.pintar(PERSONA).lecturaCv().estado()).isEqualTo("NO_LEGIBLE");
    }

    @Test
    @DisplayName("NO_LEGIBLE · terminó sin dejar ficha: del archivo no salió nada")
    void noLegibleTerminadaSinFicha() {
        // Este es el caso que el primer intento pintaba como «leyendo» para siempre. Pasa de
        // verdad: hay un test llamado unaFichaVaciaNoSeGuarda, o sea que el agente puede
        // terminar bien sin nada que guardar.
        hayUnaPostulacionConCv();
        when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(cola.comoVaLaLectura(POSTULACION)).thenReturn("TERMINADA");

        assertThat(pintor.pintar(PERSONA).lecturaCv().estado()).isEqualTo("NO_LEGIBLE");
    }

    @Test
    @DisplayName("NO_LEGIBLE · nadie llegó a pedir la lectura; no se queda «leyendo» eterno")
    void noLegibleSinEmpezar() {
        hayUnaPostulacionConCv();
        when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(cola.comoVaLaLectura(POSTULACION)).thenReturn("SIN_EMPEZAR");

        assertThat(pintor.pintar(PERSONA).lecturaCv().estado()).isEqualTo("NO_LEGIBLE");
    }

    @Test
    @DisplayName("Un perfil que nunca se llenó se pinta vacío, no revienta ni da 404")
    void perfilVacio() {
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.empty());
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());

        PerfilCompleto vacio = pintor.pintar(PERSONA);

        assertThat(vacio.titular()).isNull();
        assertThat(vacio.habilidades()).isEmpty();
        assertThat(vacio.experiencia()).isEmpty();
        assertThat(vacio.pretension()).isNull();
    }

    @Test
    @DisplayName("Quitar la pretensión no toca nada más del perfil")
    void sinPretensionConservaElResto() {
        PerfilCompleto con = new PerfilCompleto("Analista", "Mi resumen", List.of("Excel"), 96,
                "Arequipa", "Inmediata",
                new com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.Pretension(
                        new BigDecimal("3500"), new BigDecimal("4200"), "PEN"),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.LecturaCv("LISTA", null));

        PerfilCompleto sin = pintor.sinPretension(con);

        assertThat(sin.pretension()).isNull();
        assertThat(sin.titular()).isEqualTo("Analista");
        assertThat(sin.resumen()).isEqualTo("Mi resumen");
        assertThat(sin.habilidades()).containsExactly("Excel");
        assertThat(sin.lecturaCv().estado()).isEqualTo("LISTA");
    }

    @Test
    @DisplayName("Las habilidades se parten por «|» y los huecos no cuentan")
    void habilidades() {
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(
                PerfilCandidato.builder().id(PERFIL).personaId(PERSONA)
                        .habilidades("Excel |  | SQL |Power BI ")
                        .creadoEn(Instant.now()).actualizadoEn(Instant.now()).build()));

        assertThat(pintor.pintar(PERSONA).habilidades())
                .containsExactly("Excel", "SQL", "Power BI");
    }
}
