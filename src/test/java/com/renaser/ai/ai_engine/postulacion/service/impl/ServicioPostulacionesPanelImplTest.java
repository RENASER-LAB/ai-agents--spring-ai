package com.renaser.ai.ai_engine.postulacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.EnlaceArchivo;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Entregar un currículum: el enlace al almacén y la descarga de siempre.
 *
 * <p>Son dos formas de dar lo mismo, y lo que hay que vigilar es que <b>las dos comprueben el
 * permiso</b>. Si una lo comprobara y la otra no, la que no lo hace se convierte en la puerta
 * de atrás, y nadie se entera hasta que alguien se baja el currículum de un candidato de una
 * convocatoria que no le toca.
 */
@ExtendWith(MockitoExtension.class)
class ServicioPostulacionesPanelImplTest {

    private static final long ORGANIZACION = 1L;
    private static final long ARCHIVO = 807L;

    @Mock private com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository postulaciones;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository estados;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository transiciones;
    @Mock private com.renaser.ai.ai_engine.vacante.repository.VacanteRepository vacantes;
    @Mock private com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository usuarios;
    @Mock private com.renaser.ai.ai_engine.usuario.repository.PersonaRepository personas;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.CvRepository cvs;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.EnlaceCvRepository enlaces;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados maquina;
    @Mock private Permisos permisos;
    @Mock private com.renaser.ai.ai_engine.prueba.service.ServicioPrueba prueba;
    @Mock private com.renaser.ai.ai_engine.validacion.service.ServicioValidacion validacion;
    @Mock private com.renaser.ai.ai_engine.simulacion.service.ServicioDisponibilidadSimulacion disponibilidad;

    @InjectMocks
    private ServicioPostulacionesPanelImpl servicio;

    private ContextoUsuario quien;

    @BeforeEach
    void quienPregunta() {
        quien = new ContextoUsuario(10L, 20L, ORGANIZACION, "EQUIPO", List.of(1L),
                Map.of("descargar_entregables", "TODO"));
        lenient().when(permisos.alcanceDe("descargar_entregables"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, 10L));
    }

    @Test
    void elEnlaceLlegaConSuCaducidadYElNombreDelArchivo() {
        // El nombre viaja aparte porque la ruta del almacén es un uuid: sin él, el navegador
        // guardaría el currículum con un nombre que no le dice nada a nadie.
        Instant caduca = Instant.now().plus(Duration.ofMinutes(5));
        when(archivos.findByIdAndOrganizacionId(ARCHIVO, ORGANIZACION))
                .thenReturn(Optional.of(archivo()));
        when(almacen.urlDeDescarga(any(Archivo.class)))
                .thenReturn(Optional.of(new AlmacenArchivos.EnlaceFirmado("https://firmado", caduca)));

        EnlaceArchivo enlace = servicio.enlaceDeArchivo(quien, ARCHIVO);

        assertThat(enlace.url()).isEqualTo("https://firmado");
        assertThat(enlace.expiraEn()).isEqualTo(caduca);
        assertThat(enlace.nombre()).isEqualTo("curriculum.pdf");
    }

    @Test
    void unArchivoDeOtraOrganizacionNoSeFirma() {
        // La comprobación es antes de firmar, y tiene que serlo: el enlace no vuelve a
        // preguntar quién eres, así que firmarlo primero y comprobar después no comprueba nada.
        when(archivos.findByIdAndOrganizacionId(ARCHIVO, ORGANIZACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.enlaceDeArchivo(quien, ARCHIVO))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(almacen, never()).urlDeDescarga(any(Archivo.class));
    }

    @Test
    void siElAlmacenNoSabeFirmarLoDiceEnVezDeDevolverUnEnlaceVacio() {
        when(archivos.findByIdAndOrganizacionId(ARCHIVO, ORGANIZACION))
                .thenReturn(Optional.of(archivo()));
        when(almacen.urlDeDescarga(any(Archivo.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.enlaceDeArchivo(quien, ARCHIVO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("descarga de siempre");
    }

    @Test
    void laDescargaDeSiempreComprueba() {
        // La misma comprobación que el enlace, y por eso comparten el código que la hace.
        when(archivos.findByIdAndOrganizacionId(ARCHIVO, ORGANIZACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.descargarArchivo(quien, ARCHIVO, new StringBuilder()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(almacen, never()).leer(any(Archivo.class));
    }

    @Test
    void laDescargaDevuelveLosBytesYElNombre() {
        when(archivos.findByIdAndOrganizacionId(ARCHIVO, ORGANIZACION))
                .thenReturn(Optional.of(archivo()));
        when(almacen.leer(any(Archivo.class))).thenReturn("%PDF".getBytes());

        StringBuilder nombre = new StringBuilder();
        byte[] bytes = servicio.descargarArchivo(quien, ARCHIVO, nombre);

        assertThat(bytes).asString().isEqualTo("%PDF");
        assertThat(nombre).hasToString("curriculum.pdf");
    }

    private Archivo archivo() {
        return Archivo.builder().id(ARCHIVO).organizacionId(ORGANIZACION)
                .ruta("1/abc.pdf").nombreOriginal("curriculum.pdf")
                .tipo("application/pdf").build();
    }
}
