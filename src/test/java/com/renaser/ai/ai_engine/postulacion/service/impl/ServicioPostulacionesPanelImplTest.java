package com.renaser.ai.ai_engine.postulacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.CorregirContacto;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.EnlaceArchivo;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.FilaBandeja;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;

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
    @Mock private NombresDeUsuarios nombres;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.CvRepository cvs;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.EnlaceCvRepository enlaces;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados maquina;
    @Mock private Permisos permisos;
    @Mock private com.renaser.ai.ai_engine.prueba.service.ServicioPrueba prueba;
    @Mock private com.renaser.ai.ai_engine.validacion.service.ServicioValidacion validacion;
    @Mock private com.renaser.ai.ai_engine.simulacion.service.ServicioDisponibilidadSimulacion disponibilidad;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository datosCv;
    @Mock private com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria auditoria;

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
    @DisplayName("Las 236 filas se resuelven en bloque, no con tres consultas por fila")
    void laBandejaSeResuelveEnBloque() {
        // 236 es el volumen de referencia del módulo. Fila por fila eran 709 consultas
        // encadenadas contra Supabase y la bandeja del grupo grande no llegaba a contestar.
        bandejaDe(236);

        List<FilaBandeja> filas = servicio.bandeja(quien, "TALENTO");

        assertThat(filas).hasSize(236);
        // Los nombres se piden una vez para la tanda entera: cuántos viajes cuesta eso por
        // dentro es cosa de NombresDeUsuarios, y allí tiene su propia prueba.
        verify(nombres, times(1)).porUsuario(any());
        verify(vacantes, times(1)).findAllById(any());
        // Lo que de verdad se está comprobando: que no queda ningún findById suelto dentro
        // del map. Si vuelve uno, el conteo de arriba sigue en 1 y solo esto lo delata.
        verify(nombres, never()).de(any());
        verify(usuarios, never()).findById(any());
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
    @DisplayName("A quien no se puede nombrar se le sigue viendo la fila, sin nombre")
    void aQuienNoSePuedeNombrarSeLeSigueLlamandoAnonimizado() {
        // El caso importa porque el borrado de datos NO borra la postulación: vacía a la
        // persona. La fila tiene que seguir saliendo —si no, el embudo deja de cuadrar— pero
        // sin nombre. Por qué un id acaba sin nombre lo decide NombresDeUsuarios y allí se
        // prueban las tres formas; lo que se comprueba aquí es que la fila no se cae.
        Postulacion anonima = postulacion(1L, 900L, VACANTE);
        Postulacion conNombre = postulacion(2L, 903L, VACANTE);
        Postulacion sinVacante = postulacion(3L, 904L, 777L);

        when(postulaciones.bandeja(ORGANIZACION, "TALENTO", null))
                .thenReturn(List.of(anonima, conNombre, sinVacante));
        when(estados.findAll()).thenReturn(List.of(estadoTalento()));
        when(nombres.porUsuario(any())).thenReturn(Map.of(
                900L, NombresDeUsuarios.ANONIMO,
                903L, "Lucía Ortega",
                904L, "Mario Sosa"));
        when(vacantes.findAllById(any())).thenReturn(List.of(vacante()));

        List<FilaBandeja> filas = servicio.bandeja(quien, "TALENTO");

        assertThat(filas).extracting(FilaBandeja::candidato).containsExactly(
                "(anonimizado)", "Lucía Ortega", "Mario Sosa");
        // Una vacante que ya no está deja el título vacío, no «(anonimizado)»: son dos cosas
        // distintas y el panel las pinta distinto.
        assertThat(filas).extracting(FilaBandeja::vacante)
                .containsExactly("Vacante de prueba", "Vacante de prueba", "");
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
        when(nombres.porUsuario(any())).thenReturn(Map.of(901L, "Ana Ruiz"));
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
        when(nombres.porUsuario(any())).thenReturn(Map.of());

        assertThat(servicio.bandeja(quien, "TALENTO")).isEmpty();
        verify(usuarios, never()).findById(any());
    }

    @Test
    @DisplayName("Con ver_candidatos en PROPIO la bandeja sale vacía, no entera")
    void conPropioLaBandejaSaleVacia() {
        // En el panel ninguna postulación es de quien mira: son candidatos. Sin tratar PROPIO
        // aparte, la consulta recibía un filtro nulo —responsableOFiltroNulo solo distingue
        // SUS_VACANTES— y enseñaba la organización entera a quien menos alcance tiene. No era
        // alcanzable mientras el reparto se tocaba a mano en la base; con los permisos
        // editables desde el panel basta un PUT sobre ver_candidatos.
        when(permisos.alcanceDe("ver_candidatos"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.PROPIO, 10L));

        assertThat(servicio.bandeja(quien, "TALENTO")).isEmpty();

        verify(postulaciones, never()).bandeja(any(), anyString(), any());
        verifyNoInteractions(nombres);
    }

    @Test
    @DisplayName("La ficha trae el nombre resuelto y el correo, que vienen de sitios distintos")
    void laFichaTraeNombreYCorreo() {
        // La ficha es el otro sitio que enseña un nombre, y no tenía prueba. Importa que se
        // separen las dos fuentes: el correo es del usuario, y el nombre pasa por la regla de
        // anonimización. Antes se pedía la persona a mano y un personaId nulo reventaba con
        // un error de acceso a datos —un 500 por un candidato sin persona—; ahora no.
        Postulacion p = postulacion(1L, 901L, VACANTE);
        when(postulaciones.findByIdAndOrganizacionId(1L, ORGANIZACION)).thenReturn(Optional.of(p));
        when(permisos.alcanceDe("abrir_ficha_candidato"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, 10L));
        when(usuarios.findById(901L)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.usuario.entity.Usuario.builder()
                        .id(901L).organizacionId(ORGANIZACION)
                        .correo("ana@correo.pe").build()));
        when(nombres.de(901L)).thenReturn("Ana Ruiz");
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante()));
        when(estados.findById("EVALUACION_POR_REVISAR")).thenReturn(Optional.of(estadoTalento()));
        when(cvs.findByPostulacionId(1L)).thenReturn(Optional.empty());

        var ficha = servicio.ficha(quien, 1L);

        assertThat(ficha.candidato()).isEqualTo("Ana Ruiz");
        assertThat(ficha.correo()).isEqualTo("ana@correo.pe");
        assertThat(ficha.vacante()).isEqualTo("Vacante de prueba");
        // Sin currículum la ficha no se cae: sale con los enlaces vacíos y sin archivo.
        assertThat(ficha.enlaces()).isEmpty();
        assertThat(ficha.archivoCvId()).isNull();
    }

    @Test
    @DisplayName("La ficha de una postulación que no es suya responde 404, no 403")
    void laFichaAjenaNoSeAbre() {
        Postulacion ajena = postulacion(1L, 901L, VACANTE);
        when(postulaciones.findByIdAndOrganizacionId(1L, ORGANIZACION)).thenReturn(Optional.of(ajena));
        when(permisos.alcanceDe("abrir_ficha_candidato"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, 10L));
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante()));  // sin responsable

        assertThatThrownBy(() -> servicio.ficha(quien, 1L))
                .as("un 403 confirmaría que esa postulación existe")
                .isInstanceOf(ResourceNotFoundException.class);

        // Y no se llega a pedir el nombre de alguien que este usuario no puede ver.
        verify(nombres, never()).de(any());
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
        Map<Long, String> comoSeLlaman = new java.util.HashMap<>();
        for (long i = 1; i <= cuantas; i++) {
            tanda.add(postulacion(i, 900L + i, VACANTE));
            comoSeLlaman.put(900L + i, "Candidata Número " + i);
        }
        when(postulaciones.bandeja(ORGANIZACION, "TALENTO", null)).thenReturn(tanda);
        when(estados.findAll()).thenReturn(List.of(estadoTalento()));
        when(nombres.porUsuario(any())).thenReturn(comoSeLlaman);
        when(vacantes.findAllById(any())).thenReturn(List.of(vacante()));
    }

    private static Postulacion postulacion(long id, long usuarioId, long vacanteId) {
        return Postulacion.builder().id(id).uuid(UUID.randomUUID())
                .organizacionId(ORGANIZACION).usuarioId(usuarioId).vacanteId(vacanteId)
                .estadoCodigo("EVALUACION_POR_REVISAR").grupoPrioridad("MEDIA")
                .movidoEn(Instant.now()).build();
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

    @org.junit.jupiter.api.Nested
    @DisplayName("Corregir el contacto de una ficha")
    class CorregirElContacto {

        private com.renaser.ai.ai_engine.postulacion.entity.DatoCv laFichaDe(String email, String tel) {
            var ficha = com.renaser.ai.ai_engine.postulacion.entity.DatoCv.builder()
                    .id(7L).postulacionId(1L).nombre("Ariana Belen Tineo").email(email).telefono(tel)
                    .build();
            Postulacion p = new Postulacion();
            p.setId(1L);
            p.setOrganizacionId(ORGANIZACION);
            lenient().when(permisos.alcanceDe("corregir_contacto_candidato"))
                    .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, 10L));
            lenient().when(postulaciones.findByIdAndOrganizacionId(1L, ORGANIZACION))
                    .thenReturn(java.util.Optional.of(p));
            lenient().when(datosCv.findByPostulacionId(1L)).thenReturn(java.util.Optional.of(ficha));
            return ficha;
        }

        @Test
        @DisplayName("cambia solo lo que se manda, y deja el otro dato en paz")
        void soloLoQueLlega() {
            var ficha = laFichaDe("ariana_tineousmp.pe", "999888777");

            var salida = servicio.corregirContacto(quien, 1L,
                    new CorregirContacto("tineoariana00@gmail.com", null, "La IA leyo mal la arroba"));

            assertThat(salida.email()).isEqualTo("tineoariana00@gmail.com");
            // El telefono estaba bien: mandar solo el correo no puede tocarlo. Obligar a
            // reescribir el que ya servia es la forma mas facil de estropearlo.
            assertThat(salida.telefono()).isEqualTo("999888777");
            assertThat(ficha.getEmail()).isEqualTo("tineoariana00@gmail.com");
        }

        @Test
        @DisplayName("queda auditado con el valor anterior")
        void quedaEscritoQueHabiaAntes() {
            laFichaDe("ariana_tineousmp.pe", "999888777");

            servicio.corregirContacto(quien, 1L,
                    new CorregirContacto("tineoariana00@gmail.com", null, "La IA leyo mal la arroba"));

            var anterior = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien), eq("corregir_contacto_candidato"),
                    eq("dato_cv"), eq(7L), anterior.capture(), any(), eq("La IA leyo mal la arroba"));
            // Sin el valor viejo la auditoria no sirve para lo unico que hace falta: saber que
            // decia el curriculum si el candidato pregunta por que su correo cambio.
            assertThat(anterior.getValue().toString()).contains("ariana_tineousmp.pe");
        }

        @Test
        @DisplayName("sin correo ni telefono no hay nada que corregir")
        void nadaQueCorregir() {
            laFichaDe("algo@ejemplo.com", "999888777");

            assertThatThrownBy(() -> servicio.corregirContacto(quien, 1L,
                    new CorregirContacto(null, "  ", "un motivo")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nada que corregir");
            verifyNoInteractions(auditoria);
        }
    }

}
