package com.renaser.ai.ai_engine.postulacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.EnlaceArchivo;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.FilaBandeja;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que el equipo ve y se lleva del panel: la bandeja de trabajo y el currículum.
 *
 * <p><b>La bandeja.</b> Lo que se vigila aquí no es lo que devuelve, sino <b>cuántas veces
 * pregunta a la base para devolverlo</b>. Es una prueba rara —contar llamadas en vez de mirar
 * un resultado— y es a propósito: el nombre del candidato salía bien con una fila y con
 * doscientas, así que ninguna comprobación de contenido habría avisado de que la pantalla
 * tardaba minuto y medio. El coste no se ve en la respuesta, solo en el número de viajes.
 *
 * <p><b>El currículum.</b> El enlace al almacén y la descarga de siempre son dos formas de dar
 * lo mismo, y lo que hay que vigilar es que <b>las dos comprueben el permiso</b>. Si una lo
 * comprobara y la otra no, la que no lo hace se convierte en la puerta de atrás, y nadie se
 * entera hasta que alguien se baja el currículum de un candidato de una convocatoria que no le
 * toca.
 */
@ExtendWith(MockitoExtension.class)
class ServicioPostulacionesPanelImplTest {

    private static final long ORGANIZACION = 1L;
    private static final long ARCHIVO = 807L;
    private static final long VACANTE = 55L;

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
        // Ve a todos: el alcance ya tiene sus propias pruebas y aquí estorbaría. Lo que se
        // mira en la bandeja es cuántas consultas cuesta, no a quién deja ver.
        lenient().when(permisos.alcanceDe("ver_candidatos"))
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

    // ============ la bandeja ============

    @Test
    @DisplayName("Las 236 filas se resuelven con cuatro consultas, no con tres por fila")
    void laBandejaSeResuelveEnBloque() {
        // 236 es el volumen de referencia del módulo. Fila por fila eran 709 consultas
        // encadenadas contra Supabase y la bandeja del grupo grande no llegaba a contestar.
        bandejaDe(236);

        List<FilaBandeja> filas = servicio.bandeja(quien, "TALENTO");

        assertThat(filas).hasSize(236);
        verify(usuarios, times(1)).findAllById(any());
        verify(personas, times(1)).findAllById(any());
        verify(vacantes, times(1)).findAllById(any());
        // Lo que de verdad se está comprobando: que no queda ningún findById suelto dentro
        // del map. Si vuelve uno, el conteo de arriba sigue en 1 y solo esto lo delata.
        verify(usuarios, never()).findById(any());
        verify(personas, never()).findById(any());
        verify(vacantes, never()).findById(any());
    }

    @Test
    @DisplayName("Cada id se pide una sola vez aunque se repita en muchas filas")
    void losIdsRepetidosNoSePidenDosVeces() {
        // Veinte candidatos de la misma vacante son veinte veces el mismo id de vacante.
        // Mandarlos todos al findAllById no rompe nada, pero devuelve al problema de fondo:
        // pedir a la base lo que ya se tiene.
        bandejaDe(20);

        servicio.bandeja(quien, "TALENTO");

        ArgumentCaptor<Iterable<Long>> pedidos = ArgumentCaptor.captor();
        verify(vacantes).findAllById(pedidos.capture());
        assertThat(pedidos.getValue()).containsExactly(VACANTE);
    }

    @Test
    @DisplayName("Sin usuario, sin persona o con la persona borrada sale «(anonimizado)»")
    void aQuienNoSePuedeNombrarSeLeSigueLlamandoAnonimizado() {
        // El caso importa porque el borrado de datos NO borra la postulación: vacía a la
        // persona. La fila tiene que seguir saliendo —si no, el embudo deja de cuadrar— pero
        // sin nombre. Con mapas hay tres formas de no encontrarlo y las tres son este caso.
        Postulacion sinUsuario = postulacion(1L, 900L, VACANTE);      // el usuario no existe
        Postulacion sinPersona = postulacion(2L, 901L, VACANTE);      // existe, su persona no
        Postulacion borrada = postulacion(3L, 902L, VACANTE);         // existe y está vaciada
        Postulacion sinVacante = postulacion(4L, 903L, 777L);         // la vacante no existe

        when(postulaciones.bandeja(ORGANIZACION, "TALENTO", null))
                .thenReturn(List.of(sinUsuario, sinPersona, borrada, sinVacante));
        when(estados.findAll()).thenReturn(List.of(estadoTalento()));
        when(usuarios.findAllById(any())).thenReturn(List.of(
                usuario(901L, 501L), usuario(902L, 502L), usuario(903L, 503L)));
        when(personas.findAllById(any())).thenReturn(List.of(
                Persona.builder().id(502L).anonimizadoEn(Instant.now()).build(),
                Persona.builder().id(503L).nombre("Lucía").apellidos("Ortega").build()));
        when(vacantes.findAllById(any())).thenReturn(List.of(vacante()));

        List<FilaBandeja> filas = servicio.bandeja(quien, "TALENTO");

        assertThat(filas).extracting(FilaBandeja::candidato).containsExactly(
                "(anonimizado)", "(anonimizado)", "(anonimizado)", "Lucía Ortega");
        // Una vacante que ya no está deja el título vacío, no «(anonimizado)»: son dos cosas
        // distintas y el panel las pinta distinto.
        assertThat(filas).extracting(FilaBandeja::vacante)
                .containsExactly("Vacante de prueba", "Vacante de prueba", "Vacante de prueba", "");
    }

    @Test
    @DisplayName("La fila sigue trayendo los mismos nueve campos que antes")
    void elContratoDeLaFilaNoCambia() {
        // El arreglo cambia de dónde salen los datos, no cuáles son: el frontend no se toca.
        Instant movida = Instant.now().minus(Duration.ofDays(3));
        Postulacion p = postulacion(1L, 901L, VACANTE);
        p.setMovidoEn(movida);
        p.setGrupoPrioridad("ALTA");

        when(postulaciones.bandeja(ORGANIZACION, "TALENTO", null)).thenReturn(List.of(p));
        when(estados.findAll()).thenReturn(List.of(estadoTalento()));
        when(usuarios.findAllById(any())).thenReturn(List.of(usuario(901L, 501L)));
        when(personas.findAllById(any())).thenReturn(List.of(
                Persona.builder().id(501L).nombre("Ana").apellidos("Ruiz").build()));
        when(vacantes.findAllById(any())).thenReturn(List.of(vacante()));

        FilaBandeja fila = servicio.bandeja(quien, "TALENTO").get(0);

        assertThat(fila.postulacionId()).isEqualTo(1L);
        assertThat(fila.uuid()).isEqualTo(p.getUuid().toString());
        assertThat(fila.candidato()).isEqualTo("Ana Ruiz");
        assertThat(fila.vacante()).isEqualTo("Vacante de prueba");
        assertThat(fila.estado()).isEqualTo("EVALUACION_POR_REVISAR");
        assertThat(fila.estadoNombre()).isEqualTo("Evaluación por revisar");
        assertThat(fila.esperaA()).isEqualTo("TALENTO");
        assertThat(fila.grupoPrioridad()).isEqualTo("ALTA");
        assertThat(fila.diasSinCambio()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Una bandeja vacía no pregunta por nadie")
    void unaBandejaVaciaNoPreguntaPorNadie() {
        when(postulaciones.bandeja(ORGANIZACION, "TALENTO", null)).thenReturn(List.of());
        when(estados.findAll()).thenReturn(List.of(estadoTalento()));

        assertThat(servicio.bandeja(quien, "TALENTO")).isEmpty();
        verify(usuarios, never()).findById(any());
    }

    @Test
    void unEsperaAQueNoExisteNiSiquieraLlegaALaBase() {
        assertThatThrownBy(() -> servicio.bandeja(quien, "CONTABILIDAD"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(postulaciones, never()).bandeja(any(), any(), any());
    }

    // ============ ayudas ============

    /** Una tanda de {@code cuantas} postulaciones, todas resolubles, sobre la misma vacante. */
    private void bandejaDe(int cuantas) {
        List<Postulacion> tanda = new ArrayList<>();
        List<Usuario> gente = new ArrayList<>();
        List<Persona> personasDe = new ArrayList<>();
        for (long i = 1; i <= cuantas; i++) {
            tanda.add(postulacion(i, 900L + i, VACANTE));
            gente.add(usuario(900L + i, 500L + i));
            personasDe.add(Persona.builder().id(500L + i)
                    .nombre("Candidata").apellidos("Número " + i).build());
        }
        when(postulaciones.bandeja(ORGANIZACION, "TALENTO", null)).thenReturn(tanda);
        when(estados.findAll()).thenReturn(List.of(estadoTalento()));
        when(usuarios.findAllById(any())).thenReturn(gente);
        when(personas.findAllById(any())).thenReturn(personasDe);
        when(vacantes.findAllById(any())).thenReturn(List.of(vacante()));
    }

    private static Postulacion postulacion(long id, long usuarioId, long vacanteId) {
        return Postulacion.builder().id(id).uuid(UUID.randomUUID())
                .organizacionId(ORGANIZACION).usuarioId(usuarioId).vacanteId(vacanteId)
                .estadoCodigo("EVALUACION_POR_REVISAR").grupoPrioridad("MEDIA")
                .movidoEn(Instant.now()).build();
    }

    private static Usuario usuario(long id, long personaId) {
        return Usuario.builder().id(id).organizacionId(ORGANIZACION).personaId(personaId)
                .correo("candidata" + id + "@ejemplo.com").build();
    }

    private static Vacante vacante() {
        return Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                .titulo("Vacante de prueba").build();
    }

    private static EstadoPostulacion estadoTalento() {
        return EstadoPostulacion.builder().codigo("EVALUACION_POR_REVISAR")
                .nombre("Evaluación por revisar").esperaA("TALENTO").build();
    }

    private Archivo archivo() {
        return Archivo.builder().id(ARCHIVO).organizacionId(ORGANIZACION)
                .ruta("1/abc.pdf").nombreOriginal("curriculum.pdf")
                .tipo("application/pdf").build();
    }
}
