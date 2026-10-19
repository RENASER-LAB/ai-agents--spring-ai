package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Campo;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Contexto;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Convocatoria;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Ficha;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Listado;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Resumen;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.VacanteDelContexto;
import com.renaser.ai.ai_engine.whatsapp.entity.ResultadoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.ResultadoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.FichaPublica;
import com.renaser.ai.ai_engine.whatsapp.service.LoQueLeToca;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.SinAccesoWhatsappException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La consulta v2 (V76): qué se puede consultar, la paginación y el contexto. AC-01, AC-02, AC-09,
 * AC-10, AC-11 y AC-18 (lectura sin efectos) del lado de EX.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La consulta v2 del bot")
class ServicioContextoWhatsappImplTest {

    private static final String NUMERO = "+51987654321";
    private static final String OTRO_NUMERO = "+51912345678";
    private static final Long RENASER = 1L;
    private static final String SECRETO = "secreto-de-pruebas-desde-el-bot-de-whatsapp";

    @Mock private VinculoWhatsappRepository vinculos;
    @Mock private ResultadoWhatsappRepository resultados;
    @Mock private BajaWhatsappRepository bajas;
    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private EstadoPostulacionRepository estados;
    @Mock private TransicionEstadoRepository transiciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;
    @Mock private FichaPublica ficha;
    @Mock private LoQueLeToca acciones;

    private ServicioContextoWhatsappImpl servicio;

    private final List<VinculoWhatsapp> todos = new ArrayList<>();
    private final List<Postulacion> suyas = new ArrayList<>();
    private final List<Vacante> lasVacantes = new ArrayList<>();
    private final List<ResultadoWhatsapp> losResultados = new ArrayList<>();

    @BeforeEach
    void montar() {
        PropiedadesWhatsapp propiedades = new PropiedadesWhatsapp();
        propiedades.setSecretoDesdeElBot(SECRETO);
        propiedades.setEnlaceMisProcesos("https://portal.renaser.pe/procesos");
        servicio = new ServicioContextoWhatsappImpl(vinculos, resultados, bajas, postulaciones, vacantes,
                estados, transiciones, usuarios, personas, ficha, acciones, propiedades);
        lenient().when(estados.findAllByOrderByOrden()).thenReturn(List.of(
                estado("PERFIL_CALIFICANDO", "PERFIL_INTEGRAL", "CALIFICANDO", false),
                estado("PRUEBA_TURNO_CANDIDATO", "PRUEBA_PUESTO", "TURNO_CANDIDATO", false),
                estado("PRUEBA_POR_CONFIRMAR", "PRUEBA_PUESTO", "POR_CONFIRMAR", false),
                estado("CONTRATADO", null, null, true),
                estado("NO_CONTINUA", null, null, true),
                estado("CERRADA", null, null, true)));
        lenient().when(vinculos.findByTelefono(NUMERO)).thenReturn(todos);
        lenient().when(resultados.findByVinculoWhatsappIdIn(anyCollection())).thenReturn(losResultados);
        lenient().when(postulaciones.findAllById(anyCollection())).thenAnswer(i -> suyas);
        lenient().when(vacantes.findAllById(anyCollection())).thenAnswer(i -> lasVacantes);
        lenient().when(usuarios.findAllById(anyCollection())).thenReturn(List.of(
                Usuario.builder().id(60L).personaId(33L).build()));
        lenient().when(personas.findAllById(anyCollection())).thenReturn(List.of(
                Persona.builder().id(33L).telefono(NUMERO).build()));
        lenient().when(ficha.empresaDe(any())).thenReturn("Renaser");
        lenient().when(ficha.ciudadesDe(any())).thenReturn(Map.of());
        lenient().when(ficha.de(any())).thenAnswer(i -> fichaDe(i.getArgument(0)));
    }

    @AfterEach
    void soloLee() {
        // La consulta es de lectura: no cambia estados, vínculos, resultados ni nada.
        verify(postulaciones, never()).save(any());
        verify(vinculos, never()).save(any());
        verify(resultados, never()).save(any());
    }

    private static EstadoPostulacion estado(String codigo, String etapa, String momento, boolean esFinal) {
        return EstadoPostulacion.builder().codigo(codigo).etapaCodigo(etapa).momentoCodigo(momento)
                .esFinal(esFinal).build();
    }

    private static String referenciaDe(long id) {
        return "pw_" + String.format("%032x", id);
    }

    /** Una postulación con su vínculo: el id del vínculo marca lo reciente que es. */
    private VinculoWhatsapp conPostulacion(long id, String estado, String vinculo) {
        Vacante vacante = Vacante.builder().id(500 + id).organizacionId(RENASER).titulo("Contador")
                .estado("PUBLICADA").instrumentoEtapaTecnica("PRUEBA_PROPIA").build();
        lasVacantes.add(vacante);
        suyas.add(Postulacion.builder().id(1000 + id).organizacionId(RENASER).usuarioId(60L)
                .vacanteId(vacante.getId()).estadoCodigo(estado).creadoEn(Instant.now().minus(Duration.ofDays(5)))
                .movidoEn(Instant.now().minus(Duration.ofDays(1))).build());
        VinculoWhatsapp v = VinculoWhatsapp.builder().id(id).postulacionId(1000 + id).personaId(33L)
                .referencia(referenciaDe(id)).telefono(NUMERO).estado(vinculo).build();
        todos.add(v);
        return v;
    }

    private void conResultado(VinculoWhatsapp v, String tipo, Instant fecha) {
        losResultados.add(ResultadoWhatsapp.builder().id(v.getId()).postulacionId(v.getPostulacionId())
                .vinculoWhatsappId(v.getId()).tipo(tipo).fecha(fecha).revision(1)
                .consultableHasta(fecha.plus(Duration.ofDays(30))).actualizadoEn(fecha).build());
    }

    private static VacanteDelContexto fichaDe(Vacante vacante) {
        Campo no = Campo.NO_PUBLICADO;
        return new VacanteDelContexto("vc_x", vacante.getTitulo(), "Renaser", "Renaser",
                FichaPublica.convocatoriaDe(vacante), new Ficha(no, no, no, no, no, no, no, no, no, no));
    }

    // ============ AC-01 · solo lo suyo ============

    @Test
    @DisplayName("AC-01 · un número con un proceso activo lo ve con su referencia, título y situación")
    void loSuyo() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "ACTIVO");

        Listado listado = servicio.listar(NUMERO, null);

        assertThat(listado.postulaciones()).hasSize(1);
        Resumen resumen = listado.postulaciones().get(0);
        assertThat(resumen.postulacionRef()).isEqualTo(referenciaDe(1));
        assertThat(resumen.vacante().titulo()).isEqualTo("Contador");
        assertThat(resumen.situacion()).isEqualTo("ACTIVA");
        assertThat(resumen.resultado()).isNull();
        assertThat(resumen.convocatoria()).isEqualTo("PUBLICADA");
        assertThat(listado.cursor()).isNull();
        assertThat(listado.consultadoEn()).isNotNull();
    }

    @Test
    @DisplayName("AC-01 · una referencia de otro número, inventada o con otra forma responde igual: SIN_ACCESO")
    void referenciaAjena() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "ACTIVO");

        assertThatThrownBy(() -> servicio.detalle(NUMERO, referenciaDe(99))).isInstanceOf(SinAccesoWhatsappException.class);
        assertThatThrownBy(() -> servicio.detalle(NUMERO, "1001")).isInstanceOf(SinAccesoWhatsappException.class);
        when(vinculos.findByTelefono(OTRO_NUMERO)).thenReturn(List.of());
        assertThatThrownBy(() -> servicio.detalle(OTRO_NUMERO, referenciaDe(1)))
                .isInstanceOf(SinAccesoWhatsappException.class);
    }

    @Test
    @DisplayName("AC-01 · una postulación de otra organización que la de su vacante no se entrega")
    void otraOrganizacion() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "ACTIVO");
        lasVacantes.get(0).setOrganizacionId(2L);

        assertThat(servicio.listar(NUMERO, null).postulaciones()).isEmpty();
        assertThatThrownBy(() -> servicio.detalle(NUMERO, referenciaDe(1)))
                .isInstanceOf(SinAccesoWhatsappException.class);
    }

    @Test
    @DisplayName("AC-01/AC-15 · con otro celular en la cuenta, el número anterior ya no consulta nada")
    void otroCelularEnLaCuenta() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "ACTIVO");
        when(personas.findAllById(anyCollection())).thenReturn(List.of(
                Persona.builder().id(33L).telefono(OTRO_NUMERO).build()));

        assertThat(servicio.listar(NUMERO, null).postulaciones()).isEmpty();
    }

    @Test
    @DisplayName("AC-15 · con BAJA no hay nada que consultar, ni lo activo ni lo terminado")
    void conBaja() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "ACTIVO");
        when(bajas.existsById(NUMERO)).thenReturn(true);

        assertThat(servicio.listar(NUMERO, null).postulaciones()).isEmpty();
    }

    @Test
    @DisplayName("Un vínculo que dejó de recibir, sin resultado, no da acceso; la lista vacía no es un descarte")
    void sinAcceso() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "REVOCADO");

        Listado listado = servicio.listar(NUMERO, null);

        assertThat(listado.postulaciones()).isEmpty();
        assertThat(listado.cursor()).isNull();
    }

    @Test
    @DisplayName("Una vacante eliminada se retira también de aquí")
    void vacanteEliminada() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "ACTIVO");
        lasVacantes.get(0).setEliminadaEn(Instant.now());

        assertThat(servicio.listar(NUMERO, null).postulaciones()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"hola", "12", ""})
    @DisplayName("Un teléfono que no es E.164 es 400")
    void telefonoQueNoVale(String telefono) {
        assertThatThrownBy(() -> servicio.listar(telefono, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.detalle(telefono, referenciaDe(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ============ AC-02 · de 9 en 9 sin perder ninguna ============

    @Test
    @DisplayName("AC-02 · con once procesos, dos páginas de 9 y 2, las más recientes primero, sin repetir ni perder")
    void paginas() {
        for (long id = 1; id <= 11; id++) {
            conPostulacion(id, "PERFIL_CALIFICANDO", "ACTIVO");
        }

        Listado primera = servicio.listar(NUMERO, null);
        assertThat(primera.postulaciones()).hasSize(9);
        assertThat(primera.cursor()).isNotNull().hasSizeLessThanOrEqualTo(200);
        assertThat(primera.postulaciones().get(0).postulacionRef()).isEqualTo(referenciaDe(11));

        Listado segunda = servicio.listar(NUMERO, primera.cursor());
        assertThat(segunda.postulaciones()).hasSize(2);
        assertThat(segunda.cursor()).isNull();

        Set<String> vistas = new HashSet<>();
        primera.postulaciones().forEach(r -> vistas.add(r.postulacionRef()));
        segunda.postulaciones().forEach(r -> vistas.add(r.postulacionRef()));
        assertThat(vistas).hasSize(11);
    }

    @Test
    @DisplayName("AC-02 · el cursor de otro número, o alterado, no sirve: SIN_ACCESO")
    void cursorAjeno() {
        for (long id = 1; id <= 11; id++) {
            conPostulacion(id, "PERFIL_CALIFICANDO", "ACTIVO");
        }
        String cursor = servicio.listar(NUMERO, null).cursor();

        assertThatThrownBy(() -> servicio.listar(OTRO_NUMERO, cursor)).isInstanceOf(SinAccesoWhatsappException.class);
        assertThatThrownBy(() -> servicio.listar(NUMERO, cursor + "0")).isInstanceOf(SinAccesoWhatsappException.class);
    }

    @Test
    @DisplayName("AC-02 · una referencia concreta devuelve solo su contexto")
    void unaReferencia() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "ACTIVO");
        conPostulacion(2, "PRUEBA_TURNO_CANDIDATO", "ACTIVO");

        Contexto contexto = servicio.detalle(NUMERO, referenciaDe(1));

        assertThat(contexto.postulacionRef()).isEqualTo(referenciaDe(1));
        assertThat(contexto.situacion().estado()).isEqualTo("ACTIVA");
        assertThat(contexto.situacion().etapaCodigo()).isEqualTo("PERFIL_INTEGRAL");
        assertThat(contexto.situacion().momento()).isEqualTo("CALIFICANDO");
        assertThat(contexto.situacion().titulo()).isEqualTo("Estamos calificando tus respuestas");
        assertThat(contexto.resultado()).isNull();
        assertThat(contexto.consultadoEn()).isNotNull();
        assertThat(contexto.actualizadoEn()).isBeforeOrEqualTo(contexto.consultadoEn());
        assertThat(contexto.revisionContexto()).isEqualTo(contexto.actualizadoEn().toEpochMilli());
        assertThat(contexto.procedimiento()).extracting(e -> e.estado())
                .containsExactly("ACTUAL", "PREVISTA", "PREVISTA", "PREVISTA", "PREVISTA");
    }

    // ============ QA-C0-F1 · la vigencia: fecha real del último cambio y revisión ============

    @Test
    @DisplayName("QA-C0-F1 · editar la ficha publicada mueve actualizadoEn y revisionContexto, en el detalle y en el listado")
    void editarLaFichaMueveLaVigencia() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "ACTIVO");
        Contexto antes = servicio.detalle(NUMERO, referenciaDe(1));
        // Sin cambios, otra consulta da la misma fecha y la misma revisión: consultar no es cambiar.
        assertThat(servicio.detalle(NUMERO, referenciaDe(1)).revisionContexto()).isEqualTo(antes.revisionContexto());

        // La base fecha la edición de la ficha (V77) en contexto_actualizado_en.
        Instant editada = Instant.now().minus(Duration.ofMinutes(2));
        lasVacantes.get(0).setContextoActualizadoEn(editada);
        Contexto despues = servicio.detalle(NUMERO, referenciaDe(1));
        Resumen resumen = servicio.listar(NUMERO, null).postulaciones().get(0);

        assertThat(despues.actualizadoEn()).isAfter(antes.actualizadoEn())
                .isEqualTo(editada.truncatedTo(ChronoUnit.MILLIS));
        assertThat(despues.revisionContexto()).isGreaterThan(antes.revisionContexto())
                .isEqualTo(despues.actualizadoEn().toEpochMilli());
        assertThat(resumen.actualizadaEn()).isEqualTo(despues.actualizadoEn());
    }

    @Test
    @DisplayName("QA-C0-F1 · en carrera, un cambio de la acción (un plazo ampliado o vencido) también la mueve")
    void cambioDeLaAccion() {
        conPostulacion(1, "PRUEBA_TURNO_CANDIDATO", "ACTIVO");
        Instant ampliado = Instant.now().minus(Duration.ofMinutes(5));
        when(acciones.ultimoCambio(any(), any(), any())).thenReturn(ampliado);

        Contexto contexto = servicio.detalle(NUMERO, referenciaDe(1));

        assertThat(contexto.actualizadoEn()).isEqualTo(ampliado.truncatedTo(ChronoUnit.MILLIS));
        assertThat(contexto.revisionContexto()).isEqualTo(ampliado.truncatedTo(ChronoUnit.MILLIS).toEpochMilli());
    }

    @Test
    @DisplayName("QA-C0-F1 · terminada ya no hay acción: lo que pase después con su prueba no mueve la vigencia")
    void terminadaNoMiraLaAccion() {
        VinculoWhatsapp v = conPostulacion(1, "NO_CONTINUA", "REVOCADO");
        conResultado(v, "NO_CONTINUA", Instant.now().minus(Duration.ofHours(3)));

        Contexto contexto = servicio.detalle(NUMERO, referenciaDe(1));

        assertThat(contexto.actualizadoEn())
                .isEqualTo(losResultados.get(0).getActualizadoEn().truncatedTo(ChronoUnit.MILLIS));
        verify(acciones, never()).ultimoCambio(any(), any(), any());
    }

    @Test
    @DisplayName("QA-C0-F1 · nunca posterior a la consulta, aunque el reloj de la base vaya por delante")
    void nuncaDespuesDeLaConsulta() {
        conPostulacion(1, "PERFIL_CALIFICANDO", "ACTIVO");
        lasVacantes.get(0).setContextoActualizadoEn(Instant.now().plus(Duration.ofSeconds(3)));

        Contexto contexto = servicio.detalle(NUMERO, referenciaDe(1));

        assertThat(contexto.actualizadoEn()).isEqualTo(contexto.consultadoEn());
    }

    // ============ AC-09 · AC-11 · los resultados ============

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"CONTRATADO", "NO_CONTINUA", "CERRADA"})
    @DisplayName("AC-09 · cada resultado se distingue, con su fecha y sin explicación pública")
    void resultados(String tipo) {
        VinculoWhatsapp v = conPostulacion(1, tipo, "REVOCADO");
        Instant fecha = Instant.now().minus(Duration.ofDays(2));
        conResultado(v, tipo, fecha);
        when(transiciones.findByPostulacionIdOrderByOcurridaEnAsc(1001L)).thenReturn(List.of(
                TransicionEstado.builder().estadoAnteriorCodigo(null).estadoNuevoCodigo("PERFIL_CALIFICANDO").build(),
                TransicionEstado.builder().estadoAnteriorCodigo("PRUEBA_POR_CONFIRMAR").estadoNuevoCodigo(tipo).build()));

        Listado listado = servicio.listar(NUMERO, null);
        assertThat(listado.postulaciones()).singleElement()
                .satisfies(r -> {
                    assertThat(r.situacion()).isEqualTo("FINAL");
                    assertThat(r.resultado()).isEqualTo(tipo);
                });

        Contexto contexto = servicio.detalle(NUMERO, referenciaDe(1));
        assertThat(contexto.situacion().estado()).isEqualTo("FINAL");
        assertThat(contexto.situacion().etapaCodigo()).isNull();
        assertThat(contexto.accion()).isNull();
        assertThat(contexto.resultado().tipo()).isEqualTo(tipo);
        assertThat(contexto.resultado().fecha()).isEqualTo(fecha.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(contexto.resultado().explicacion()).isNull();
        assertThat(contexto.resultado().esCorreccion()).isFalse();
        // Solo las etapas a las que llegó, y hechas.
        assertThat(contexto.procedimiento()).extracting(e -> e.codigo())
                .containsExactly("PERFIL_INTEGRAL", "PRUEBA_PUESTO");
        verify(acciones, never()).de(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-11 · pasados los 30 días, o con el acceso revocado, el resultado ya no se consulta")
    void treintaDias() {
        VinculoWhatsapp vencido = conPostulacion(1, "NO_CONTINUA", "REVOCADO");
        conResultado(vencido, "NO_CONTINUA", Instant.now().minus(Duration.ofDays(31)));
        VinculoWhatsapp revocado = conPostulacion(2, "CERRADA", "REVOCADO");
        conResultado(revocado, "CERRADA", Instant.now().minus(Duration.ofDays(1)));
        losResultados.get(1).setAccesoRevocadoEn(Instant.now());
        losResultados.get(1).setAccesoRevocadoMotivo("CAMBIO_CELULAR");

        assertThat(servicio.listar(NUMERO, null).postulaciones()).isEmpty();
        assertThatThrownBy(() -> servicio.detalle(NUMERO, referenciaDe(1)))
                .isInstanceOf(SinAccesoWhatsappException.class);
    }

    @Test
    @DisplayName("AC-11 · un resultado y otra postulación activa se consultan por separado")
    void separados() {
        VinculoWhatsapp terminado = conPostulacion(1, "CONTRATADO", "REVOCADO");
        conResultado(terminado, "CONTRATADO", Instant.now().minus(Duration.ofDays(3)));
        conPostulacion(2, "PRUEBA_TURNO_CANDIDATO", "ACTIVO");

        List<Resumen> resumenes = servicio.listar(NUMERO, null).postulaciones();

        Map<String, Resumen> porRef = resumenes.stream()
                .collect(Collectors.toMap(Resumen::postulacionRef, Function.identity()));
        assertThat(porRef).hasSize(2);
        assertThat(porRef.get(referenciaDe(1)).situacion()).isEqualTo("FINAL");
        assertThat(porRef.get(referenciaDe(2)).situacion()).isEqualTo("ACTIVA");
        assertThat(porRef.get(referenciaDe(2)).resultado()).isNull();
    }

    @Test
    @DisplayName("Un vínculo legado cerrado por fin del proceso, sin resultado registrado, no se convierte en uno")
    void legadoSinResultado() {
        conPostulacion(1, "NO_CONTINUA", "REVOCADO");

        assertThat(servicio.listar(NUMERO, null).postulaciones()).isEmpty();
    }

    // ============ AC-10 · la convocatoria cerrada ============

    @Test
    @DisplayName("AC-10 · convocatoria cerrada con la postulación en curso: las dos cosas por separado")
    void convocatoriaCerradaEnCurso() {
        conPostulacion(1, "PRUEBA_TURNO_CANDIDATO", "ACTIVO");
        lasVacantes.get(0).setEstado("CERRADA");
        lasVacantes.get(0).setCerradaEn(Instant.now().minus(Duration.ofHours(2)));

        Resumen resumen = servicio.listar(NUMERO, null).postulaciones().get(0);
        Contexto contexto = servicio.detalle(NUMERO, referenciaDe(1));

        assertThat(resumen.convocatoria()).isEqualTo("CERRADA");
        assertThat(resumen.situacion()).isEqualTo("ACTIVA");
        Convocatoria convocatoria = contexto.vacante().convocatoria();
        assertThat(convocatoria.estado()).isEqualTo("CERRADA");
        assertThat(convocatoria.cerradaEn()).isNotNull();
        assertThat(contexto.situacion().estado()).isEqualTo("ACTIVA");
        // La acción vigente se sigue pidiendo: cerrar la convocatoria no la cancela.
        verify(acciones).de(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lo vivo de un número sin más vínculos: no consulta postulaciones de más")
    void sinVinculos() {
        when(vinculos.findByTelefono(NUMERO)).thenReturn(List.of());

        assertThat(servicio.listar(NUMERO, null).postulaciones()).isEmpty();
        verify(postulaciones, never()).findAllById(anyCollection());
        verify(postulaciones, never()).findById(anyLong());
    }
}
