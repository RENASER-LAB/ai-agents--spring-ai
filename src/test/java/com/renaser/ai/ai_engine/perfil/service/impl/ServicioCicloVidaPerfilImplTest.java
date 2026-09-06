package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.perfil.entity.CertificacionPerfil;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.LecturaCvPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El borrado del perfil")
class ServicioCicloVidaPerfilImplTest {

    private static final long PERSONA = 30L;
    private static final long PERFIL = 40L;

    @Mock private PerfilCandidatoRepository perfiles;
    @Mock private ExperienciaPerfilRepository experiencias;
    @Mock private EducacionPerfilRepository educaciones;
    @Mock private IdiomaPerfilRepository idiomas;
    @Mock private CertificacionPerfilRepository certificaciones;
    @Mock private EnlacePerfilRepository enlaces;
    @Mock private LecturaCvPerfilRepository lecturas;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;

    private ServicioCicloVidaPerfilImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioCicloVidaPerfilImpl(perfiles, experiencias, educaciones,
                idiomas, certificaciones, enlaces, lecturas, archivos, almacen);
    }

    private static Archivo archivo(long id) {
        return Archivo.builder().id(id).organizacionId(1L).ruta("1/" + id).build();
    }

    @Test
    @DisplayName("Borra las seis tablas, las hijas primero: las FK no dejan otro orden")
    void borraTodoLoQueCuelga() {
        PerfilCandidato perfil = PerfilCandidato.builder().id(PERFIL).personaId(PERSONA).build();
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(perfil));

        servicio.borrarPorPersona(PERSONA);

        verify(experiencias).deleteByPerfilCandidatoId(PERFIL);
        verify(educaciones).deleteByPerfilCandidatoId(PERFIL);
        verify(idiomas).deleteByPerfilCandidatoId(PERFIL);
        verify(certificaciones).deleteByPerfilCandidatoId(PERFIL);
        verify(enlaces).deleteByPerfilCandidatoId(PERFIL);
        verify(perfiles).delete(perfil);
    }

    @Test
    @DisplayName("Ley 29733: la foto, la portada, el currículum y los diplomas se sueltan del almacén")
    void sueltaSusCuatroArchivos() {
        // Sin esto el perfil se borraba «entero» y sus archivos se quedaban en el bucket sin
        // que quedara ninguna fila por la que encontrarlos.
        PerfilCandidato perfil = PerfilCandidato.builder().id(PERFIL).personaId(PERSONA)
                .fotoArchivoId(1L).portadaArchivoId(2L).cvArchivoId(3L)
                .cvActualizadoEn(Instant.now()).build();
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(perfil));
        when(certificaciones.findByPerfilCandidatoIdOrderByNombre(PERFIL)).thenReturn(List.of(
                CertificacionPerfil.builder().id(9L).perfilCandidatoId(PERFIL).archivoId(4L).build(),
                CertificacionPerfil.builder().id(10L).perfilCandidatoId(PERFIL).build()));
        for (long id : List.of(1L, 2L, 3L, 4L)) {
            when(archivos.findById(id)).thenReturn(Optional.of(archivo(id)));
        }

        servicio.borrarPorPersona(PERSONA);

        verify(almacen).borrarContenido(argThat(a -> a.getId() == 1L));
        verify(almacen).borrarContenido(argThat(a -> a.getId() == 2L));
        verify(almacen).borrarContenido(argThat(a -> a.getId() == 3L));
        verify(almacen).borrarContenido(argThat(a -> a.getId() == 4L));
        // La certificación sin diploma no inventa una búsqueda de archivo.
        verify(archivos, never()).findById(null);
        // Y el orden importa: primero se van las filas que apuntan, después el contenido.
        InOrder orden = inOrder(certificaciones, perfiles, almacen);
        orden.verify(certificaciones).deleteByPerfilCandidatoId(PERFIL);
        orden.verify(perfiles).delete(perfil);
        orden.verify(almacen, atLeastOnce()).borrarContenido(any());
    }

    @Test
    @DisplayName("Un archivo que ya estaba soltado no se toca otra vez")
    void elYaSoltadoNoSeToca() {
        PerfilCandidato perfil = PerfilCandidato.builder().id(PERFIL).personaId(PERSONA)
                .fotoArchivoId(1L).build();
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(perfil));
        Archivo yaBorrado = archivo(1L);
        yaBorrado.setBorradoEn(Instant.now());
        when(archivos.findById(1L)).thenReturn(Optional.of(yaBorrado));

        servicio.borrarPorPersona(PERSONA);

        verify(almacen, never()).borrarContenido(any());
    }

    @Test
    @DisplayName("Las lecturas del currículum se van con el perfil: son el recibo de un perfil que ya no existe")
    void borraLasLecturas() {
        // Conservarlas dejaba que volver a subir el mismo PDF cerrara LISTA una lectura que
        // no propondría nada, sobre un perfil vacío.
        PerfilCandidato perfil = PerfilCandidato.builder().id(PERFIL).personaId(PERSONA).build();
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.of(perfil));

        servicio.borrarPorPersona(PERSONA);

        verify(lecturas).deleteByPersonaId(PERSONA);
    }

    @Test
    @DisplayName("Sin perfil no hay nada que borrar, y no es un error")
    void sinPerfilNoHaceNada() {
        when(perfiles.findByPersonaId(PERSONA)).thenReturn(Optional.empty());

        servicio.borrarPorPersona(PERSONA);

        verifyNoInteractions(experiencias, educaciones, idiomas, certificaciones, enlaces,
                lecturas, archivos, almacen);
    }
}
