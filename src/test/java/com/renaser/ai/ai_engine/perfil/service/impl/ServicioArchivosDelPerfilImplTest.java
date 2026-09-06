package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.perfil.entity.CertificacionPerfil;
import com.renaser.ai.ai_engine.perfil.entity.LecturaCvPerfil;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.LecturaCvPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfil.service.ServicioArchivosDelPerfil.Contenido;
import com.renaser.ai.ai_engine.perfil.service.ServicioPropuestaPerfil;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los archivos que el candidato guarda en su perfil.
 *
 * ⚠️ **Las tres reglas que se prueban aquí costaron un fallo cada una**, y las
 * tres se encontraron probando a mano contra una base de verdad, no leyendo el
 * código: no pagar dos veces la misma lectura, cerrar la lectura viva al quitar
 * el currículum, y pedir los archivos con la organización de quien pregunta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Los archivos del perfil del candidato")
class ServicioArchivosDelPerfilImplTest {

    private static final long PERSONA = 7L;
    private static final long ORG = 1L;

    @Mock private PerfilCandidatoRepository perfiles;
    @Mock private CertificacionPerfilRepository certificaciones;
    @Mock private LecturaCvPerfilRepository lecturas;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private ColaCalificacionIa cola;
    @Mock private DatoCvRepository datosCv;
    @Mock private ServicioPropuestaPerfil propuesta;

    private ServicioArchivosDelPerfilImpl servicio;
    private PerfilCandidato perfil;

    private static final ContextoUsuario QUIEN =
            new ContextoUsuario(3L, PERSONA, ORG, "CANDIDATO", List.of(), Map.of());

    private static MockMultipartFile unPdf() {
        return new MockMultipartFile("archivo", "cv.pdf", "application/pdf", "%PDF-1.4".getBytes());
    }

    private static Archivo archivo(long id, String hash) {
        return Archivo.builder().id(id).organizacionId(ORG).nombreOriginal("cv.pdf")
                .tipo("application/pdf").ruta(ORG + "/" + id + ".pdf").tamano(8L)
                .contenidoHash(hash).build();
    }

    @BeforeEach
    void perfilExistente() {
        perfil = PerfilCandidato.builder().id(11L).personaId(PERSONA)
                .creadoEn(Instant.now()).actualizadoEn(Instant.now()).build();
        lenient().when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(perfil));
        lenient().when(perfiles.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(lecturas.save(any())).thenAnswer(i -> {
            LecturaCvPerfil l = i.getArgument(0);
            if (l.getId() == null) l.setId(99L);
            return l;
        });
        servicio = new ServicioArchivosDelPerfilImpl(
                perfiles, certificaciones, lecturas, archivos, almacen, cola, datosCv,
                propuesta);
    }

    // ==================== El currículum ====================

    @Test
    @DisplayName("Subir el currículum lo guarda y manda a leerlo")
    void subirEncolaLaLectura() {
        when(almacen.guardar(eq(ORG), any())).thenReturn(archivo(50L, "hash-a"));
        when(archivos.findByContenidoHash("hash-a")).thenReturn(List.of());
        when(cola.encolarDatosCvDelPerfil(ORG, 99L)).thenReturn(true);

        servicio.guardarCurriculum(QUIEN, unPdf());

        assertThat(perfil.getCvArchivoId()).isEqualTo(50L);
        assertThat(perfil.getCvActualizadoEn()).isNotNull();
        verify(cola).encolarDatosCvDelPerfil(ORG, 99L);
    }

    @Test
    @DisplayName("Si la cola no lo acepta, la lectura NO se queda «leyendo» para siempre")
    void colaApagadaCierraLaLectura() {
        // La pantalla sondea ese estado cada cinco segundos: un «estamos leyendo
        // tu currículum» eterno es peor que decir que no se pudo.
        when(almacen.guardar(eq(ORG), any())).thenReturn(archivo(50L, "hash-a"));
        when(archivos.findByContenidoHash("hash-a")).thenReturn(List.of());
        when(cola.encolarDatosCvDelPerfil(ORG, 99L)).thenReturn(false);

        servicio.guardarCurriculum(QUIEN, unPdf());

        ArgumentCaptor<LecturaCvPerfil> guardadas = ArgumentCaptor.forClass(LecturaCvPerfil.class);
        verify(lecturas, org.mockito.Mockito.atLeastOnce()).save(guardadas.capture());
        LecturaCvPerfil ultima = guardadas.getAllValues().get(guardadas.getAllValues().size() - 1);
        assertThat(ultima.getEstado()).isEqualTo(LecturaCvPerfil.NO_LEGIBLE);
        assertThat(ultima.getMotivo()).contains("no está disponible");
        assertThat(ultima.getTerminadoEn()).isNotNull();
    }

    @Test
    @DisplayName("RF-161: el mismo archivo no se paga dos veces")
    void elMismoArchivoNoSeVuelveAPagar() {
        // Se reconoce por la huella del contenido, no por el nombre: la misma
        // persona vuelve a subir el mismo PDF con otro nombre a menudo.
        when(almacen.guardar(eq(ORG), any())).thenReturn(archivo(60L, "hash-repetido"));
        when(archivos.findByContenidoHash("hash-repetido"))
                .thenReturn(List.of(archivo(41L, "hash-repetido"), archivo(60L, "hash-repetido")));
        when(lecturas.findByPersonaIdAndArchivoIdInAndEstado(
                eq(PERSONA), any(), eq(LecturaCvPerfil.LISTA)))
                .thenReturn(List.of(LecturaCvPerfil.builder().id(4L).estado(LecturaCvPerfil.LISTA).build()));

        servicio.guardarCurriculum(QUIEN, unPdf());

        verify(cola, never()).encolarDatosCvDelPerfil(anyLong(), anyLong());
        ArgumentCaptor<LecturaCvPerfil> nueva = ArgumentCaptor.forClass(LecturaCvPerfil.class);
        verify(lecturas).save(nueva.capture());
        assertThat(nueva.getValue().getEstado()).isEqualTo(LecturaCvPerfil.LISTA);
        assertThat(nueva.getValue().getMotivo()).contains("no se vuelve a pagar");
    }

    @Test
    @DisplayName("RF-161: tampoco se paga si ese archivo ya se leyó AL POSTULAR")
    void elLeidoAlPostularTampocoSeVuelveAPagar() {
        // Los dos recibos son tablas distintas —`lectura_cv_perfil` y `dato_cv`— y no se
        // miraban entre ellos: quien postulaba con su PDF y luego lo guardaba en su perfil
        // pagaba la misma lectura dos veces. Lo que se leyó al postular ya se propuso a
        // este mismo perfil en aquella transacción.
        when(almacen.guardar(eq(ORG), any())).thenReturn(archivo(60L, "hash-de-postular"));
        when(archivos.findByContenidoHash("hash-de-postular"))
                .thenReturn(List.of(archivo(60L, "hash-de-postular")));
        when(lecturas.findByPersonaIdAndArchivoIdInAndEstado(
                eq(PERSONA), any(), eq(LecturaCvPerfil.LISTA)))
                .thenReturn(List.of());
        when(datosCv.fichasDeLaPersonaConHash(PERSONA, "hash-de-postular"))
                .thenReturn(List.of(DatoCv.builder().id(3L).postulacionId(8L).build()));
        when(propuesta.conservaLoPropuestoDeUnCurriculum(PERSONA)).thenReturn(true);

        servicio.guardarCurriculum(QUIEN, unPdf());

        verify(cola, never()).encolarDatosCvDelPerfil(anyLong(), anyLong());
        ArgumentCaptor<LecturaCvPerfil> nueva = ArgumentCaptor.forClass(LecturaCvPerfil.class);
        verify(lecturas).save(nueva.capture());
        assertThat(nueva.getValue().getEstado()).isEqualTo(LecturaCvPerfil.LISTA);
        assertThat(nueva.getValue().getMotivo()).contains("no se vuelve a pagar");
    }

    @Test
    @DisplayName("Barrido por inactividad y vuelve con el mismo CV: no se queda LISTA sobre un perfil vacío")
    void trasElBarridoElReciboDeLaPostulacionNoVale() {
        // `dato_cv` dice que ese archivo se leyó, pero no guarda lo que se propuso, y el
        // barrido de retención se llevó el perfil entero: sin propuestas vivas, darla por
        // buena sería «revisa lo que encontramos» sobre un perfil vacío. Se vuelve a leer.
        when(almacen.guardar(eq(ORG), any())).thenReturn(archivo(61L, "hash-barrido"));
        when(archivos.findByContenidoHash("hash-barrido"))
                .thenReturn(List.of(archivo(61L, "hash-barrido")));
        when(lecturas.findByPersonaIdAndArchivoIdInAndEstado(
                eq(PERSONA), any(), eq(LecturaCvPerfil.LISTA)))
                .thenReturn(List.of());
        when(datosCv.fichasDeLaPersonaConHash(PERSONA, "hash-barrido"))
                .thenReturn(List.of(DatoCv.builder().id(3L).postulacionId(8L).build()));
        when(propuesta.conservaLoPropuestoDeUnCurriculum(PERSONA)).thenReturn(false);
        when(cola.encolarDatosCvDelPerfil(ORG, 99L)).thenReturn(true);

        servicio.guardarCurriculum(QUIEN, unPdf());

        verify(cola).encolarDatosCvDelPerfil(ORG, 99L);
        ArgumentCaptor<LecturaCvPerfil> nueva = ArgumentCaptor.forClass(LecturaCvPerfil.class);
        verify(lecturas).save(nueva.capture());
        assertThat(nueva.getValue().getEstado()).isEqualTo(LecturaCvPerfil.EN_CURSO);
    }

    @Test
    @DisplayName("Subir otro cierra la lectura del anterior antes de arrancar la suya")
    void subirOtroCierraLaVieja() {
        when(almacen.guardar(eq(ORG), any())).thenReturn(archivo(70L, "hash-b"));
        when(archivos.findByContenidoHash("hash-b")).thenReturn(List.of());
        when(lecturas.findByPersonaIdAndEstado(PERSONA, LecturaCvPerfil.EN_CURSO))
                .thenReturn(Optional.of(LecturaCvPerfil.builder()
                        .id(4L).estado(LecturaCvPerfil.EN_CURSO).build()));
        when(cola.encolarDatosCvDelPerfil(ORG, 99L)).thenReturn(true);

        servicio.guardarCurriculum(QUIEN, unPdf());

        ArgumentCaptor<LecturaCvPerfil> cerrada = ArgumentCaptor.forClass(LecturaCvPerfil.class);
        verify(lecturas).saveAndFlush(cerrada.capture());
        assertThat(cerrada.getValue().getEstado()).isEqualTo(LecturaCvPerfil.NO_LEGIBLE);
        assertThat(cerrada.getValue().getMotivo()).contains("antes de terminar");
    }

    @Test
    @DisplayName("Quitarlo NO borra las lecturas: son el recibo de lo ya pagado")
    void quitarloConservaElRecibo() {
        // Borrarlas hacía que volver a subir el mismo PDF llamara otra vez al
        // modelo para proponer exactamente los mismos datos.
        perfil.setCvArchivoId(80L);
        when(archivos.findByIdAndOrganizacionId(80L, ORG)).thenReturn(Optional.of(archivo(80L, "h")));
        when(lecturas.findByPersonaIdAndEstado(PERSONA, LecturaCvPerfil.EN_CURSO))
                .thenReturn(Optional.empty());

        servicio.quitarCurriculum(QUIEN);

        assertThat(perfil.getCvArchivoId()).isNull();
        assertThat(perfil.getCvActualizadoEn()).isNull();
        verify(lecturas, never()).deleteAll();
    }

    @Test
    @DisplayName("Quitarlo mientras se lee cierra esa lectura, que ya no puede terminar")
    void quitarloCancelaLaLecturaViva() {
        perfil.setCvArchivoId(80L);
        when(archivos.findByIdAndOrganizacionId(80L, ORG)).thenReturn(Optional.of(archivo(80L, "h")));
        LecturaCvPerfil viva = LecturaCvPerfil.builder().id(5L).estado(LecturaCvPerfil.EN_CURSO).build();
        when(lecturas.findByPersonaIdAndEstado(PERSONA, LecturaCvPerfil.EN_CURSO))
                .thenReturn(Optional.of(viva));

        servicio.quitarCurriculum(QUIEN);

        assertThat(viva.getEstado()).isEqualTo(LecturaCvPerfil.NO_LEGIBLE);
        assertThat(viva.getMotivo()).contains("antes de terminar de leerlo");
    }

    // ==================== La foto y la portada ====================

    @Test
    @DisplayName("La foto se guarda como imagen, y suelta la anterior")
    void laFotoReemplazaALaAnterior() {
        perfil.setFotoArchivoId(90L);
        Archivo vieja = archivo(90L, "h");
        when(almacen.guardarImagen(eq(ORG), any())).thenReturn(archivo(91L, "h2"));
        when(archivos.findByIdAndOrganizacionId(90L, ORG)).thenReturn(Optional.of(vieja));

        servicio.guardarFoto(QUIEN, new MockMultipartFile("a", "yo.png", "image/png", new byte[] {1}));

        assertThat(perfil.getFotoArchivoId()).isEqualTo(91L);
        verify(almacen).borrarContenido(vieja);
    }

    @Test
    @DisplayName("Elegir una portada de galería borra la propia: son excluyentes")
    void laGaleriaYLaPropiaSonExcluyentes() {
        perfil.setPortadaArchivoId(92L);
        when(archivos.findByIdAndOrganizacionId(92L, ORG)).thenReturn(Optional.of(archivo(92L, "h")));

        servicio.elegirPortadaDeGaleria(QUIEN, "CANTO_AQUA");

        assertThat(perfil.getPortadaArchivoId()).isNull();
        assertThat(perfil.getPortadaGaleria()).isEqualTo("CANTO_AQUA");
    }

    @Test
    @DisplayName("Una portada inventada se para aquí, no en el CHECK de la base")
    void laPortadaInventadaSePara() {
        assertThatThrownBy(() -> servicio.elegirPortadaDeGaleria(QUIEN, "CANTO_DORADO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Servir los bytes ====================

    @Test
    @DisplayName("Se piden CON la organización de quien pregunta")
    void seLeenConLaOrganizacion() {
        // Es el cerrojo que exige la regla de arquitectura, y encaja: estos
        // archivos se sellaron con esta misma organización al subirlos.
        perfil.setFotoArchivoId(93L);
        when(archivos.findByIdAndOrganizacionId(93L, ORG)).thenReturn(Optional.of(archivo(93L, "h")));
        when(almacen.leer(any())).thenReturn(new byte[] {1, 2, 3});

        Contenido c = servicio.foto(QUIEN);

        assertThat(c.bytes()).hasSize(3);
        assertThat(c.nombre()).isEqualTo("cv.pdf");
        verify(archivos).findByIdAndOrganizacionId(93L, ORG);
    }

    @Test
    @DisplayName("Sin foto responde 404, no un contenido vacío")
    void sinFotoEs404() {
        assertThatThrownBy(() -> servicio.foto(QUIEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Un archivo ya soltado responde 404 aunque su fila siga ahí")
    void elSoltadoEs404() {
        // La fila se conserva a propósito para poder explicar el hueco; lo que
        // no se puede es servir un contenido que ya no está.
        perfil.setFotoArchivoId(94L);
        Archivo borrado = archivo(94L, "h");
        borrado.setBorradoEn(Instant.now());
        when(archivos.findByIdAndOrganizacionId(94L, ORG)).thenReturn(Optional.of(borrado));

        assertThatThrownBy(() -> servicio.foto(QUIEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("El toString del contenido dice cuántos bytes, nunca cuáles")
    void elContenidoNoSeVuelcaAlRegistro() {
        // Lleva dentro el currículum de una persona: volcarlo sería un dato
        // personal en el registro.
        Contenido c = new Contenido(new byte[] {1, 2, 3}, "cv.pdf", "application/pdf");
        assertThat(c.toString()).contains("bytes=3").doesNotContain("[1, 2, 3]");
        assertThat(c).isEqualTo(new Contenido(new byte[] {1, 2, 3}, "cv.pdf", "application/pdf"));
    }

    // ==================== Los diplomas ====================

    @Test
    @DisplayName("Un diploma en PDF va por la ruta de documento")
    void elDiplomaEnPdf() {
        cuandoLaCertificacionEsMia(20L, null);
        when(almacen.guardar(eq(ORG), any())).thenReturn(archivo(95L, "h"));

        servicio.guardarDiploma(QUIEN, 20L, unPdf());

        verify(almacen).guardar(eq(ORG), any());
        verify(almacen, never()).guardarImagen(anyLong(), any());
    }

    @Test
    @DisplayName("Y una foto del papel también vale: va por la de imagen")
    void elDiplomaEnFoto() {
        // Mucha gente tiene el diploma en papel y le hace una foto con el móvil.
        cuandoLaCertificacionEsMia(20L, null);
        when(almacen.guardarImagen(eq(ORG), any())).thenReturn(archivo(96L, "h"));

        servicio.guardarDiploma(QUIEN, 20L,
                new MockMultipartFile("a", "diploma.jpg", "image/jpeg", new byte[] {1}));

        verify(almacen).guardarImagen(eq(ORG), any());
        verify(almacen, never()).guardar(anyLong(), any());
    }

    @Test
    @DisplayName("La certificación de otra persona responde 404, no 403")
    void laCertificacionAjenaEs404() {
        // Decir «prohibido» ya confirmaría que existe.
        CertificacionPerfil deOtro = CertificacionPerfil.builder()
                .id(21L).perfilCandidatoId(999L).nombre("BLS").build();
        when(certificaciones.findById(21L)).thenReturn(Optional.of(deOtro));

        assertThatThrownBy(() -> servicio.diploma(QUIEN, 21L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Quitar el diploma suelta su archivo y deja la certificación")
    void quitarElDiplomaDejaLaCertificacion() {
        CertificacionPerfil fila = cuandoLaCertificacionEsMia(22L, 97L);
        when(archivos.findByIdAndOrganizacionId(97L, ORG)).thenReturn(Optional.of(archivo(97L, "h")));

        servicio.quitarDiploma(QUIEN, 22L);

        assertThat(fila.getArchivoId()).isNull();
        verify(certificaciones).save(fila);
    }

    // ==================== Quitar lo demás ====================

    @Test
    @DisplayName("Quitar la foto la suelta y deja el perfil sin ella")
    void quitarLaFoto() {
        perfil.setFotoArchivoId(98L);
        when(archivos.findByIdAndOrganizacionId(98L, ORG)).thenReturn(Optional.of(archivo(98L, "h")));

        servicio.quitarFoto(QUIEN);

        assertThat(perfil.getFotoArchivoId()).isNull();
    }

    @Test
    @DisplayName("Quitar la portada borra las dos formas: la propia y el código")
    void quitarLaPortadaBorraLasDos() {
        perfil.setPortadaArchivoId(99L);
        perfil.setPortadaGaleria("CANTO_AQUA");
        when(archivos.findByIdAndOrganizacionId(99L, ORG)).thenReturn(Optional.of(archivo(99L, "h")));

        servicio.quitarPortada(QUIEN);

        assertThat(perfil.getPortadaArchivoId()).isNull();
        assertThat(perfil.getPortadaGaleria()).isNull();
    }

    @Test
    @DisplayName("Subir una portada propia borra el código de galería: son excluyentes")
    void laPropiaBorraElCodigo() {
        perfil.setPortadaGaleria("CANTO_ROSA");
        when(almacen.guardarImagen(eq(ORG), any())).thenReturn(archivo(100L, "h"));

        servicio.guardarPortada(QUIEN,
                new MockMultipartFile("a", "fondo.png", "image/png", new byte[] {1}));

        assertThat(perfil.getPortadaArchivoId()).isEqualTo(100L);
        assertThat(perfil.getPortadaGaleria()).isNull();
    }

    @Test
    @DisplayName("Sin perfil todavía, pedir la portada es 404 y no revienta")
    void sinPerfilTodaviaEs404() {
        // `perfil_candidato` se crea perezosamente: quien acaba de registrarse
        // no tiene fila, y pedir su portada no puede ser un 500.
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.empty());
        when(perfiles.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> servicio.portada(QUIEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("El currículum se sirve con nombre y tipo, para que la descarga tenga sentido")
    void elCurriculumSeSirveConSuNombre() {
        perfil.setCvArchivoId(101L);
        when(archivos.findByIdAndOrganizacionId(101L, ORG)).thenReturn(Optional.of(archivo(101L, "h")));
        when(almacen.leer(any())).thenReturn(new byte[] {9});

        Contenido c = servicio.curriculum(QUIEN);

        assertThat(c.nombre()).isEqualTo("cv.pdf");
        assertThat(c.tipo()).isEqualTo("application/pdf");
    }

    private CertificacionPerfil cuandoLaCertificacionEsMia(long id, Long archivoId) {
        CertificacionPerfil fila = CertificacionPerfil.builder()
                .id(id).perfilCandidatoId(perfil.getId()).nombre("BLS").archivoId(archivoId).build();
        when(certificaciones.findById(id)).thenReturn(Optional.of(fila));
        return fila;
    }
}
