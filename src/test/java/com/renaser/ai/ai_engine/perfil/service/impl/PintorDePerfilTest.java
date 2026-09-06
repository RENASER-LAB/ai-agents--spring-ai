package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil;
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
    @Mock private com.renaser.ai.ai_engine.perfil.repository.LecturaCvPerfilRepository lecturas;
    @Mock private com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository archivos;

    private PintorDePerfil pintor;

    @BeforeEach
    void crearElPintor() {
        pintor = new PintorDePerfil(perfiles, experiencias, educaciones, idiomas,
                certificaciones, enlaces, postulaciones, cvs, datosCv, cola, lecturas, archivos);
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

    /** Sustituye el perfil vacío del arranque por uno con foto, portada o currículum. */
    private void conElPerfil(java.util.function.UnaryOperator<PerfilCandidato.PerfilCandidatoBuilder> como) {
        PerfilCandidato.PerfilCandidatoBuilder base = PerfilCandidato.builder()
                .id(PERFIL).personaId(PERSONA)
                .creadoEn(Instant.now()).actualizadoEn(Instant.now());
        lenient().when(perfiles.findByPersonaId(PERSONA))
                .thenReturn(Optional.of(como.apply(base).build()));
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
                new com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.LecturaCv("LISTA", null),
                false, com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.Portada.NINGUNA, null);

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

    @Test
    @DisplayName("Al panel no le llega la foto, ni la portada, ni el CV, ni los diplomas")
    void sinLoDelCandidato() {
        // ⚠️ Es la decisión de la clienta del 05/09/2026 y el flanco del RF-41: si la foto
        // se cuela al panel, la persona que decide vuelve a ver la cara que el anonimizador
        // le esconde a la IA. Sin este test, añadirla de vuelta no rompería nada.
        PerfilCompleto suyo = new PerfilCompleto("Analista", "Mi resumen", List.of("Excel"),
                48, "Lima", "INMEDIATA", null, List.of(), List.of(), List.of(),
                List.of(new com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.CertificacionItem(7L, "SST", "Sencico", null, null,
                        "PERSONA", true, true)),
                List.of(), new com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.LecturaCv("LISTA", null),
                true, new com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.Portada("GALERIA", "CANTO_AQUA"),
                new com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.CurriculumDelPerfil("cv.pdf", 1024L, null));

        PerfilCompleto visto = pintor.sinLoDelCandidato(suyo);

        assertThat(visto.tieneFoto()).isFalse();
        assertThat(visto.portada()).isEqualTo(com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.Portada.NINGUNA);
        assertThat(visto.cv()).isNull();
        assertThat(visto.certificaciones()).singleElement()
                .extracting(com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.CertificacionItem::tieneArchivo).isEqualTo(false);
        // Y lo demás sigue entero: quitar de más también es un fallo.
        assertThat(visto.titular()).isEqualTo("Analista");
        assertThat(visto.resumen()).isEqualTo("Mi resumen");
        assertThat(visto.habilidades()).containsExactly("Excel");
        assertThat(visto.certificaciones()).singleElement()
                .extracting(com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.CertificacionItem::nombre).isEqualTo("SST");
        assertThat(visto.lecturaCv().estado()).isEqualTo("LISTA");
    }

    // ============ La foto, la portada y el currículum del perfil ============

    @Test
    @DisplayName("Un perfil recién nacido no tiene foto, ni portada, ni currículum")
    void perfilPelado() {
        var perfil = pintor.pintar(PERSONA);

        assertThat(perfil.tieneFoto()).isFalse();
        assertThat(perfil.portada()).isEqualTo(DtosPerfil.Portada.NINGUNA);
        assertThat(perfil.cv()).isNull();
    }

    @Test
    @DisplayName("Quien nunca abrió su perfil también responde 200 con todo vacío, no 404")
    void sinFilaDePerfil() {
        // La pantalla siempre tiene algo que pintar: la fila de perfil_candidato se crea
        // perezosamente y quien acaba de registrarse todavía no la tiene.
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.empty());
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());

        var perfil = pintor.pintar(PERSONA);

        assertThat(perfil.tieneFoto()).isFalse();
        assertThat(perfil.portada().tipo()).isEqualTo("NINGUNA");
        assertThat(perfil.cv()).isNull();
        assertThat(perfil.lecturaCv().estado()).isEqualTo("SIN_CV");
    }

    @Test
    @DisplayName("Con foto se dice que la hay, nunca cuál es: el id del archivo no viaja")
    void conFoto() {
        conElPerfil(b -> b.fotoArchivoId(300L));
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());

        assertThat(pintor.pintar(PERSONA).tieneFoto()).isTrue();
    }

    @Test
    @DisplayName("La portada de la galería viaja con su código; la propia, sin él")
    void lasDosPortadas() {
        conElPerfil(b -> b.portadaGaleria("CANTO_MENTA"));
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());
        assertThat(pintor.pintar(PERSONA).portada())
                .isEqualTo(new DtosPerfil.Portada("GALERIA", "CANTO_MENTA"));
    }

    @Test
    @DisplayName("La portada propia manda sobre la de galería si por lo que sea quedaran las dos")
    void laPropiaManda() {
        // La base lo impide con un CHECK, pero si una fila vieja trajera las dos, se pinta la
        // que el candidato subió: es la suya.
        conElPerfil(b -> b.portadaArchivoId(400L).portadaGaleria("CANTO_MENTA"));
        when(postulaciones.deLaPersona(PERSONA)).thenReturn(List.of());

        assertThat(pintor.pintar(PERSONA).portada())
                .isEqualTo(new DtosPerfil.Portada("PROPIA", null));
    }

    @Test
    @DisplayName("El currículum se pinta con su nombre y su peso, leídos del archivo")
    void conCurriculum() {
        Instant subido = Instant.parse("2026-09-01T10:00:00Z");
        conElPerfil(b -> b.cvArchivoId(500L).cvActualizadoEn(subido));
        when(archivos.findById(500L)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.archivo.entity.Archivo.builder()
                        .id(500L).nombreOriginal("camila-torres.pdf").tamano(240_000L).build()));
        when(lecturas.findFirstByPersonaIdAndArchivoIdOrderByIdDesc(PERSONA, 500L))
                .thenReturn(Optional.empty());

        var cv = pintor.pintar(PERSONA).cv();

        assertThat(cv).isNotNull();
        assertThat(cv.nombre()).isEqualTo("camila-torres.pdf");
        assertThat(cv.tamano()).isEqualTo(240_000L);
        assertThat(cv.subidoEn()).isEqualTo(subido);
    }

    @Test
    @DisplayName("Un currículum apuntado a un archivo que ya no está no revienta: se pinta sin él")
    void curriculumHuerfano() {
        conElPerfil(b -> b.cvArchivoId(500L));
        when(archivos.findById(500L)).thenReturn(Optional.empty());
        when(lecturas.findFirstByPersonaIdAndArchivoIdOrderByIdDesc(PERSONA, 500L))
                .thenReturn(Optional.empty());

        assertThat(pintor.pintar(PERSONA).cv()).isNull();
    }

    // ============ Lo que NO llega al panel ============

    @Test
    @DisplayName("El panel recibe el perfil sin foto, sin portada, sin currículum y sin diplomas")
    void loDelCandidatoSeQuedaEnElPortal() {
        // ⚠️ No es cosmética: el RF-41 esconde la cara a la IA para no sesgar por aspecto, y
        // enseñársela a quien decide desharía la regla por la puerta de al lado.
        conElPerfil(b -> b.titular("Analista de datos").fotoArchivoId(300L)
                .portadaGaleria("CANTO_MENTA").cvArchivoId(500L));
        when(archivos.findById(500L)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.archivo.entity.Archivo.builder()
                        .id(500L).nombreOriginal("cv.pdf").tamano(1L).build()));
        when(lecturas.findFirstByPersonaIdAndArchivoIdOrderByIdDesc(PERSONA, 500L))
                .thenReturn(Optional.empty());
        when(certificaciones.findByPerfilCandidatoIdOrderByNombre(PERFIL)).thenReturn(List.of(
                com.renaser.ai.ai_engine.perfil.entity.CertificacionPerfil.builder()
                        .id(9L).perfilCandidatoId(PERFIL).nombre("Scrum Master")
                        .origen("PERSONA").archivoId(600L).build()));

        var paraElPanel = pintor.sinLoDelCandidato(pintor.pintar(PERSONA));

        assertThat(paraElPanel.tieneFoto()).isFalse();
        assertThat(paraElPanel.portada()).isEqualTo(DtosPerfil.Portada.NINGUNA);
        assertThat(paraElPanel.cv()).isNull();
        assertThat(paraElPanel.certificaciones()).singleElement()
                .satisfies(c -> {
                    assertThat(c.nombre()).isEqualTo("Scrum Master");
                    assertThat(c.tieneArchivo()).isFalse();
                });
        // Lo demás sí pasa: quitar el diploma no puede llevarse el certificado por delante.
        assertThat(paraElPanel.titular()).isEqualTo("Analista de datos");
    }
}
