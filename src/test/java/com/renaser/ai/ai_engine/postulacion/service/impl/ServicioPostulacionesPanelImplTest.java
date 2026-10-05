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
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante alcanceVacante;
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
    @Mock private com.renaser.ai.ai_engine.postulacion.service.ServicioEnlaceAcceso enlacesDeAcceso;
    @Mock private com.renaser.ai.ai_engine.decision.service.QuienPuedeContratar quienPuedeContratar;
    @Mock private com.renaser.ai.ai_engine.colaborador.service.ContratacionEnLaFicha contratacion;

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
        // La ficha pregunta por la ficha de colaborador (V64); aquí no hay ninguna.
        lenient().when(contratacion.de(any(), any())).thenReturn(
                new com.renaser.ai.ai_engine.colaborador.service.ContratacionEnLaFicha.Vinculo(null, false, false));
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
        when(alcanceVacante.laPostulacionVisible(any(), eq(1L), eq("abrir_ficha_candidato")))
                .thenReturn(p);
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
    @DisplayName("La ficha dice si quien la abre puede mover la postulación")
    void laFichaDiceSiSePuedeMover() {
        // El panel no tiene ninguna otra forma de saberlo: el login solo devuelve el token y
        // el id, así que sin este booleano la única manera de averiguar si se puede descartar
        // a alguien sería intentarlo y leer el 403. Es una pista para pintar el botón, no la
        // defensa —esa sigue siendo el @PreAuthorize de la transición.
        prepararLaFichaDe(1L);

        assertThat(servicio.ficha(quien, 1L).puedeMoverPostulacion())
                .as("este contexto solo trae descargar_entregables")
                .isFalse();

        // El tipo es EQUIPO o CANDIDATO, no el nombre del rol: los roles van en rolIds.
        ContextoUsuario talento = new ContextoUsuario(10L, 20L, ORGANIZACION, "EQUIPO",
                List.of(1L), Map.of("mover_postulacion", "TODO"));
        assertThat(servicio.ficha(talento, 1L).puedeMoverPostulacion()).isTrue();
    }

    /** El montaje mínimo para que la ficha de esa postulación se pueda armar. */
    private void prepararLaFichaDe(long postulacionId) {
        Postulacion p = postulacion(postulacionId, 901L, VACANTE);
        when(alcanceVacante.laPostulacionVisible(any(), eq(postulacionId), eq("abrir_ficha_candidato")))
                .thenReturn(p);
        when(usuarios.findById(901L)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.usuario.entity.Usuario.builder()
                        .id(901L).organizacionId(ORGANIZACION)
                        .correo("ana@correo.pe").build()));
        when(nombres.de(901L)).thenReturn("Ana Ruiz");
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante()));
        when(estados.findById("EVALUACION_POR_REVISAR")).thenReturn(Optional.of(estadoTalento()));
        when(cvs.findByPostulacionId(postulacionId)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("La ficha de una postulación que no es suya responde 404, no 403")
    void laFichaAjenaNoSeAbre() {
        // Que una postulación de vacante ajena no se alcance lo decide y lo prueba
        // AlcanceSobreLaVacante. Aquí lo que importa es que su no llega hasta arriba.
        when(alcanceVacante.laPostulacionVisible(any(), eq(1L), eq("abrir_ficha_candidato")))
                .thenThrow(new ResourceNotFoundException("Postulación", "id", 1L));

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
            lenient().when(alcanceVacante.laPostulacionVisible(
                            any(), eq(1L), eq("corregir_contacto_candidato")))
                    .thenReturn(p);
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

    // ============ Una vacante eliminada (V60) ============

    /**
     * Corregir el contacto y generar el enlace de acceso de alguien cuya vacante se eliminó.
     *
     * <p>El enlace además no preguntaba nada: el controlador llamaba al generador, que busca
     * la postulación por id suelto. Ahora pasa por el guardián —empresa y alcance de
     * {@code mover_postulacion}— y por la vacante eliminada, como el resto de escrituras.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("Si su vacante se eliminó, ni su contacto ni su enlace de acceso se tocan")
    class DeUnaVacanteEliminada {

        private Postulacion p;

        @BeforeEach
        void laPostulacion() {
            p = new Postulacion();
            p.setId(1L);
            p.setOrganizacionId(ORGANIZACION);
            p.setVacanteId(40L);
        }

        private void suVacanteSeElimino() {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("Vacante", "id", 40L))
                    .when(alcanceVacante).exigirQueSuVacanteSigaExistiendo(p);
        }

        @Test
        @DisplayName("corregir su contacto contesta 404 y no pisa la ficha ni deja auditoría")
        void elContacto() {
            when(alcanceVacante.laPostulacionVisible(any(), eq(1L),
                    eq("corregir_contacto_candidato"))).thenReturn(p);
            suVacanteSeElimino();

            assertThatThrownBy(() -> servicio.corregirContacto(quien, 1L,
                    new CorregirContacto("otra@ejemplo.pe", null, "No debería guardarse")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Vacante");
            verifyNoInteractions(datosCv, auditoria);
        }

        @Test
        @DisplayName("generar su enlace de acceso contesta 404 y no crea ningún enlace")
        void elEnlaceDeAcceso() {
            when(alcanceVacante.laPostulacionVisible(any(), eq(1L), eq("mover_postulacion")))
                    .thenReturn(p);
            suVacanteSeElimino();

            assertThatThrownBy(() -> servicio.enlaceDeAcceso(quien, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Vacante");
            verifyNoInteractions(enlacesDeAcceso);
        }

        @Test
        @DisplayName("con la vacante viva el enlace se genera, y solo si el alcance llega")
        void elEnlaceDeUnaViva() {
            when(alcanceVacante.laPostulacionVisible(any(), eq(1L), eq("mover_postulacion")))
                    .thenReturn(p);
            var generado = new com.renaser.ai.ai_engine.postulacion.service.ServicioEnlaceAcceso
                    .EnlaceGenerado("https://portal/acceso?token=x", Instant.now());
            when(enlacesDeAcceso.generarEnlace(1L)).thenReturn(generado);

            assertThat(servicio.enlaceDeAcceso(quien, 1L)).isEqualTo(generado);
            verify(alcanceVacante).exigirQueSuVacanteSigaExistiendo(p);
        }

        @Test
        @DisplayName("fuera de su empresa o de su alcance, el enlace contesta 404 y no se crea")
        void elEnlaceFueraDeAlcance() {
            when(alcanceVacante.laPostulacionVisible(any(), eq(1L), eq("mover_postulacion")))
                    .thenThrow(new ResourceNotFoundException("Postulación", "id", 1L));

            assertThatThrownBy(() -> servicio.enlaceDeAcceso(quien, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(enlacesDeAcceso);
        }
    }

    // ============ Entrar a la prueba o a Validación ============

    /*
     * «Avanzar» y el movimiento manual comparten las reglas de entrada: crear o reutilizar lo
     * que la persona necesita en la etapa, y en Validación llevarla al paso que corresponde a
     * su periodo. Van sin @Nested a propósito: el check del harness no corre las clases
     * anidadas.
     */

    private static final long SUYA = 5L;
    private static final String YA_EN_CURSO = "su periodo de validación ya estaba en curso";

    /** La etapa de cada estado, como en la semilla de la V9. Los finales no tienen. */
    private static final Map<String, String> ETAPA_DE = Map.ofEntries(
            Map.entry("PERFIL_POR_CONFIRMAR", "PERFIL_INTEGRAL"),
            Map.entry("PRUEBA_TURNO_CANDIDATO", "PRUEBA_PUESTO"),
            Map.entry("PRUEBA_POR_CONFIRMAR", "PRUEBA_PUESTO"),
            Map.entry("SIMULACION_POR_HABILITAR", "SIMULACION"),
            Map.entry("SIMULACION_POR_CONFIRMAR", "SIMULACION"),
            Map.entry("VALIDACION_POR_HABILITAR", "VALIDACION"),
            Map.entry("VALIDACION_TURNO_CANDIDATO", "VALIDACION"),
            Map.entry("VALIDACION_POR_CONFIRMAR", "VALIDACION"));

    @Mock private com.renaser.ai.ai_engine.postulacion.service.EntradaEtapaTecnica entradaTecnica;

    private static EstadoPostulacion estado(String codigo) {
        return EstadoPostulacion.builder().codigo(codigo).etapaCodigo(ETAPA_DE.get(codigo))
                .esFinal("NO_CONTINUA".equals(codigo)).build();
    }

    /** Una postulación en {@code estadoActual}, que el guardián deja ver con {@code permiso}. */
    private Postulacion enCarrera(String estadoActual, String permiso) {
        Postulacion p = Postulacion.builder().id(SUYA).organizacionId(ORGANIZACION)
                .vacanteId(VACANTE).usuarioId(77L).estadoCodigo(estadoActual).build();
        when(alcanceVacante.laPostulacionVisible(any(), eq(SUYA), eq(permiso))).thenReturn(p);
        lenient().when(estados.findById(anyString())).thenAnswer(inv -> {
            String codigo = inv.getArgument(0);
            return ETAPA_DE.containsKey(codigo) || "NO_CONTINUA".equals(codigo)
                    ? Optional.of(estado(codigo)) : Optional.empty();
        });
        // Nadie la ha movido mientras tanto: la fila bloqueada dice lo mismo que la leída.
        lenient().when(postulaciones.estadoBloqueandoLaFila(SUYA)).thenReturn(estadoActual);
        lenient().when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION))
                .thenReturn(Optional.of(vacante()));
        return p;
    }

    /** «Avanzar» desde {@code estadoActual}, que la máquina calcula hacia {@code siguiente}. */
    private Postulacion paraAvanzar(String estadoActual, String siguiente) {
        Postulacion p = enCarrera(estadoActual, "confirmar_avance");
        when(maquina.siguiente(estadoActual)).thenReturn(Optional.of(estado(siguiente)));
        return p;
    }

    private void moverAMano(String destino, String motivo) {
        servicio.transicionar(quien, SUYA,
                new com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.Transicionar(
                        destino, motivo, null, null));
    }

    private void noSeMovio() {
        verify(maquina, never()).transicionar(any(), anyString(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(maquina, never()).transicionar(any(), anyString(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.<String>any());
    }

    @Test
    @DisplayName("«Avanzar» a Validación entra al paso que devuelve el periodo, con su coletilla")
    void avanzarAValidacionEntraAlPasoDelPeriodo() {
        Postulacion p = paraAvanzar("SIMULACION_POR_CONFIRMAR", "VALIDACION_POR_HABILITAR");
        when(validacion.crearAlEntrar(SUYA, ORGANIZACION)).thenReturn(
                new com.renaser.ai.ai_engine.validacion.service.ServicioValidacion.Entrada(
                        9L, "VALIDACION_TURNO_CANDIDATO", YA_EN_CURSO));

        servicio.confirmarAvance(quien, SUYA, "Vuelve tras revisar su simulación");

        // Una sola transición, desde donde estaba hasta donde entró de verdad.
        verify(maquina).transicionar(p, "VALIDACION_TURNO_CANDIDATO", quien,
                "Vuelve tras revisar su simulación · " + YA_EN_CURSO, false, false, null);
        verify(postulaciones).estadoBloqueandoLaFila(SUYA);
        verifyNoInteractions(entradaTecnica, disponibilidad);
    }

    @Test
    @DisplayName("«Avanzar» sin periodo lo crea «por habilitar» y el motivo va sin coletilla")
    void avanzarSinPeriodoNoLlevaColetilla() {
        Postulacion p = paraAvanzar("SIMULACION_POR_CONFIRMAR", "VALIDACION_POR_HABILITAR");
        when(validacion.crearAlEntrar(SUYA, ORGANIZACION)).thenReturn(
                new com.renaser.ai.ai_engine.validacion.service.ServicioValidacion.Entrada(
                        9L, "VALIDACION_POR_HABILITAR", null));

        servicio.confirmarAvance(quien, SUYA, "Simulación calificada");

        verify(maquina).transicionar(p, "VALIDACION_POR_HABILITAR", quien,
                "Simulación calificada", false, false, null);
    }

    @Test
    @DisplayName("«Avanzar» a la prueba crea antes lo que rinde la vacante, y después mueve")
    void avanzarALaPruebaCreaLoQueRinde() {
        Postulacion p = paraAvanzar("PERFIL_POR_CONFIRMAR", "PRUEBA_TURNO_CANDIDATO");

        servicio.confirmarAvance(quien, SUYA, "Pasa a la prueba");

        var orden = org.mockito.Mockito.inOrder(postulaciones, entradaTecnica, maquina);
        orden.verify(postulaciones).estadoBloqueandoLaFila(SUYA);
        orden.verify(entradaTecnica).crearAlEntrar(eq(p), any(Vacante.class));
        orden.verify(maquina).transicionar(p, "PRUEBA_TURNO_CANDIDATO", quien,
                "Pasa a la prueba", false, false, null);
        verifyNoInteractions(validacion);
    }

    @Test
    @DisplayName("«Avanzar» a simulación no crea nada y recalcula la disponibilidad, como siempre")
    void avanzarASimulacionNoCreaNada() {
        Postulacion p = paraAvanzar("PRUEBA_POR_CONFIRMAR", "SIMULACION_POR_HABILITAR");

        servicio.confirmarAvance(quien, SUYA, "Pasa a la simulación");

        verify(maquina).transicionar(p, "SIMULACION_POR_HABILITAR", quien,
                "Pasa a la simulación", false, false, null);
        verify(disponibilidad).recalcularVacante(ORGANIZACION, VACANTE);
        verifyNoInteractions(entradaTecnica, validacion);
        verify(postulaciones, never()).estadoBloqueandoLaFila(any());
    }

    @Test
    @DisplayName("Si otra entrada ganó la carrera, esta se planta sin crear ni mover nada")
    void siOtraEntradaGanoSePlanta() {
        paraAvanzar("SIMULACION_POR_CONFIRMAR", "VALIDACION_POR_HABILITAR");
        // La otra petición ya la llevó a Validación y soltó la fila: esta lee lo que dejó.
        when(postulaciones.estadoBloqueandoLaFila(SUYA)).thenReturn("VALIDACION_TURNO_CANDIDATO");

        assertThatThrownBy(() -> servicio.confirmarAvance(quien, SUYA, "Doble clic"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acaba de moverse");

        verifyNoInteractions(validacion, entradaTecnica);
        noSeMovio();
    }

    @Test
    @DisplayName("Si crear el periodo falla, no hay transición: todo o nada")
    void siFallaCrearElPeriodoNoHayTransicion() {
        paraAvanzar("SIMULACION_POR_CONFIRMAR", "VALIDACION_POR_HABILITAR");
        when(validacion.crearAlEntrar(SUYA, ORGANIZACION)).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("validacion_postulacion_id_key"));

        assertThatThrownBy(() -> servicio.confirmarAvance(quien, SUYA, "Avanza"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        noSeMovio();
    }

    @Test
    @DisplayName("Mover a mano a Validación desde otra etapa crea el periodo y respeta el paso elegido")
    void moverAManoAValidacionRespetaElPaso() {
        Postulacion p = enCarrera("PRUEBA_POR_CONFIRMAR", "mover_postulacion");
        // Recién creado: «por habilitar». Desde su turno se puede habilitar e iniciar igual.
        when(validacion.crearAlEntrar(SUYA, ORGANIZACION)).thenReturn(
                new com.renaser.ai.ai_engine.validacion.service.ServicioValidacion.Entrada(
                        9L, "VALIDACION_POR_HABILITAR", null));

        moverAMano("VALIDACION_TURNO_CANDIDATO", "Lo retomamos a mano");

        verify(validacion).crearAlEntrar(SUYA, ORGANIZACION);
        verify(maquina).transicionar(p, "VALIDACION_TURNO_CANDIDATO", quien,
                "Lo retomamos a mano", false, false, null, true);
    }

    @Test
    @DisplayName("Mover a mano a «por confirmar» con el periodo cerrado lo reutiliza y respeta el paso")
    void moverAManoAPorConfirmarConPeriodoCerrado() {
        Postulacion p = enCarrera("SIMULACION_POR_CONFIRMAR", "mover_postulacion");
        when(validacion.crearAlEntrar(SUYA, ORGANIZACION)).thenReturn(
                new com.renaser.ai.ai_engine.validacion.service.ServicioValidacion.Entrada(
                        9L, "VALIDACION_POR_CONFIRMAR", "su periodo de validación ya estaba cerrado"));

        moverAMano("VALIDACION_TURNO_CANDIDATO", "Repite el periodo");

        // El paso elegido manda y el motivo queda como se escribió: la coletilla solo explica
        // por qué alguien entró a otro paso que el pedido.
        verify(maquina).transicionar(p, "VALIDACION_TURNO_CANDIDATO", quien,
                "Repite el periodo", false, false, null, true);
    }

    @Test
    @DisplayName("Mover a mano a «por habilitar» con el periodo en curso entra a su turno, con coletilla")
    void moverAManoAPorHabilitarAplicaLaTabla() {
        Postulacion p = enCarrera("SIMULACION_POR_CONFIRMAR", "mover_postulacion");
        when(validacion.crearAlEntrar(SUYA, ORGANIZACION)).thenReturn(
                new com.renaser.ai.ai_engine.validacion.service.ServicioValidacion.Entrada(
                        9L, "VALIDACION_TURNO_CANDIDATO", YA_EN_CURSO));

        moverAMano("VALIDACION_POR_HABILITAR", "Vuelve a validación");

        verify(maquina).transicionar(p, "VALIDACION_TURNO_CANDIDATO", quien,
                "Vuelve a validación · " + YA_EN_CURSO, false, false, null, true);
    }

    @Test
    @DisplayName("Moverse dentro de la misma etapa no crea ni redirige nada")
    void dentroDeLaMismaEtapaNoCreaNada() {
        Postulacion p = enCarrera("VALIDACION_TURNO_CANDIDATO", "mover_postulacion");

        moverAMano("VALIDACION_POR_HABILITAR", "Hay que habilitarlo otra vez");

        verify(maquina).transicionar(p, "VALIDACION_POR_HABILITAR", quien,
                "Hay que habilitarlo otra vez", false, false, null, true);
        verifyNoInteractions(validacion, entradaTecnica);
        verify(postulaciones, never()).estadoBloqueandoLaFila(any());
    }

    @Test
    @DisplayName("Un cierre o un estado que no existe no crean nada: la máquina decide")
    void aUnCierreNoSeCreaNada() {
        Postulacion p = enCarrera("VALIDACION_TURNO_CANDIDATO", "mover_postulacion");

        moverAMano("NO_CONTINUA", "No sigue");
        moverAMano("ESTADO_INVENTADO", "Probando");

        verify(maquina).transicionar(p, "NO_CONTINUA", quien, "No sigue", false, false,
                "DECISION_PERSONA", true);
        verify(maquina).transicionar(p, "ESTADO_INVENTADO", quien, "Probando", false, false,
                null, true);
        verifyNoInteractions(validacion, entradaTecnica);
    }

    @Test
    @DisplayName("De un estado final no se sale, y tampoco se crea nada antes de intentarlo")
    void desdeUnEstadoFinalNoSeCreaNada() {
        Postulacion p = enCarrera("NO_CONTINUA", "mover_postulacion");

        moverAMano("PRUEBA_TURNO_CANDIDATO", "Lo reabrimos");

        // La negativa la da la máquina, con su mensaje de siempre.
        verify(maquina).transicionar(p, "PRUEBA_TURNO_CANDIDATO", quien, "Lo reabrimos",
                false, false, null, true);
        verifyNoInteractions(validacion, entradaTecnica);
    }

    @Test
    @DisplayName("Desde un estado que el catálogo no conoce no se crea nada")
    void desdeUnEstadoDesconocidoNoSeCreaNada() {
        Postulacion p = enCarrera("ESTADO_RETIRADO", "mover_postulacion");

        moverAMano("VALIDACION_POR_HABILITAR", "Prueba");

        verify(maquina).transicionar(p, "VALIDACION_POR_HABILITAR", quien, "Prueba",
                false, false, null, true);
        verifyNoInteractions(validacion, entradaTecnica);
    }

    @Test
    @DisplayName("Mover a mano a la prueba desde otra etapa crea lo que rinde la vacante")
    void moverAManoALaPruebaLaCrea() {
        Postulacion p = enCarrera("PERFIL_POR_CONFIRMAR", "mover_postulacion");

        moverAMano("PRUEBA_TURNO_CANDIDATO", "Pasa a la prueba");

        var orden = org.mockito.Mockito.inOrder(entradaTecnica, maquina);
        orden.verify(entradaTecnica).crearAlEntrar(eq(p), any(Vacante.class));
        orden.verify(maquina).transicionar(p, "PRUEBA_TURNO_CANDIDATO", quien,
                "Pasa a la prueba", false, false, null, true);
        verifyNoInteractions(validacion);
    }

    @Test
    @DisplayName("Sin prueba lista, mover a mano a la prueba da 409 con el mensaje de «Avanzar»")
    void moverAManoALaPruebaSinPruebaLista() {
        Postulacion p = enCarrera("PERFIL_POR_CONFIRMAR", "mover_postulacion");
        String mensaje = "Esta vacante no tiene plantilla de prueba asignada: no se puede avanzar";
        org.mockito.Mockito.doThrow(new IllegalStateException(mensaje))
                .when(entradaTecnica).crearAlEntrar(eq(p), any(Vacante.class));

        assertThatThrownBy(() -> moverAMano("PRUEBA_TURNO_CANDIDATO", "Pasa a la prueba"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(mensaje);

        // Ni transición ni correo: los dos salen de la máquina, que no se llega a llamar.
        verifyNoInteractions(maquina, transiciones);
    }

    @Test
    @DisplayName("Si su vacante ya no existe, mover a mano a la prueba se planta sin crear nada")
    void moverAManoALaPruebaSinVacante() {
        enCarrera("PERFIL_POR_CONFIRMAR", "mover_postulacion");
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moverAMano("PRUEBA_TURNO_CANDIDATO", "Pasa a la prueba"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya no existe");

        verifyNoInteractions(entradaTecnica, maquina);
    }

    @Test
    @DisplayName("Con la vacante archivada se rechaza antes de crear nada, como hoy")
    void conLaVacanteArchivadaNoSeCreaNada() {
        enCarrera("SIMULACION_POR_CONFIRMAR", "mover_postulacion");
        when(vacantes.existsByIdAndArchivadaEnIsNotNull(VACANTE)).thenReturn(true);

        assertThatThrownBy(() -> moverAMano("VALIDACION_POR_HABILITAR", "Vuelve"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(validacion, entradaTecnica, maquina);
    }
}
