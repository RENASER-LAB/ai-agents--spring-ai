package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.FilaRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.NotaCriterioResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El orden de la tanda, a quién mira la segunda pasada, y a quién no debe mirar ninguna.
 *
 * <p>Aquí viven los fallos que ya se rompieron:
 *
 * <ul>
 *   <li>Un candidato que la IA todavía no había clasificado hacía reventar el ranking
 *       entero: buscar un grupo nulo dentro de una lista inmutable lanza una excepción, y
 *       ese es el estado normal de toda la tanda antes de la primera pasada.
 *   <li>La segunda pasada se podía pedir con la tanda sin calificar. Entonces «los de
 *       arriba» no existen: la lista sale por orden alfabético y el gasto se va en la gente
 *       que tocó por la letra de su apellido.
 *   <li>Las cribas barrían la vacante entera sin mirar el estado, así que un retirado o un
 *       contratado con currículum volvía a «por confirmar» pagando el modelo por el camino.
 *   <li>Los botones contaban lo que intentaban encolar y no lo que encolaban, así que un
 *       segundo clic respondía «43 en cola» sin haber encolado a nadie.
 *   <li>La nota del currículum sumaba cualquier criterio con peso, y desde que existen la
 *       simulación y la validación eso incluye los suyos: la columna cambiaba sola en cuanto
 *       un facilitador calificaba.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El Perfil Integral que ve el panel")
class ServicioPerfilIntegralPanelImplTest {

    private static final long VACANTE = 7L;
    private static final long ORGANIZACION = 1L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante alcanceVacante;
    @Mock private PerfilTalentoRepository perfiles;
    @Mock private HallazgoPerfilRepository hallazgos;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private AlertaRepository alertas;
    @Mock private CriterioRepository criterios;
    @Mock private CvRepository cvs;
    @Mock private DatoCvRepository datosCv;
    @Mock private ServicioParametros parametros;
    @Mock private PesoCriterioRepository pesosCriterio;
    @Mock private EstadoPostulacionRepository estados;
    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;
    @Mock private PuestoRepository puestos;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private ServicioAuditoria auditoria;
    @Mock private ColaCalificacionIa cola;
    @Mock private MaquinaEstados maquina;
    @Mock private Permisos permisos;
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository evaluaciones;
    @Mock private com.renaser.ai.ai_engine.pesos.repository.EtapaRepository etapasCatalogo;
    @Mock private com.renaser.ai.ai_engine.perfil.repository.UbigeoRepository ubigeos;
    @Mock private com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository perfilesCandidato;
    @Mock private com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository intentos;
    @Mock private com.renaser.ai.ai_engine.pesos.repository.PesoEtapaRepository pesosEtapa;

    @InjectMocks
    private ServicioPerfilIntegralPanelImpl servicio;

    private ContextoUsuario quien;

    @BeforeEach
    void laVacanteYQuienLaMira() {
        quien = new ContextoUsuario(10L, 20L, ORGANIZACION, "EQUIPO", List.of(1L),
                Map.of("ver_embudo", "TODO", "ajustar_nota", "TODO"));
        lenient().when(vacantes.findById(VACANTE)).thenReturn(Optional.of(Vacante.builder()
                .id(VACANTE).organizacionId(ORGANIZACION).puestoId(3L).versionPesosId(2L)
                .titulo("Analista de procesos").responsableUsuarioId(10L).build()));
        lenient().when(puestos.findById(3L)).thenReturn(Optional.of(Puesto.builder()
                .id(3L).nombre("Analista").nivelPuestoCodigo("OPERATIVO").build()));
        lenient().when(permisos.alcanceDe(anyString()))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.TODO, 10L));
        // El guardián deja pasar por defecto: qué alcanza quien mira se decide y se prueba en
        // AlcanceSobreLaVacante. Los tests que quieren un no lo dicen ellos.
        lenient().when(alcanceVacante.laVacanteVisible(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(VACANTE), anyString()))
                .thenReturn(Vacante.builder()
                        .id(VACANTE).organizacionId(ORGANIZACION).puestoId(3L).versionPesosId(2L)
                        .titulo("Analista de procesos").responsableUsuarioId(10L).build());
        lenient().when(alcanceVacante.laPostulacionVisible(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(), anyString()))
                .thenAnswer(i -> postulaciones
                        .findByIdAndOrganizacionId(i.getArgument(1), ORGANIZACION)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Postulación", "id", i.getArgument(1))));
    }

    // ============ El ranking de otra etapa ============

    @Test
    void laNotaDeLaFilaEsLaDeLaEtapaPedida() {
        candidatos(candidato(1L, "ALTA", "90"), candidato(2L, "ALTA", "40"));
        when(etapasCatalogo.existsById("PRUEBA_PUESTO")).thenReturn(true);
        // En la prueba el orden se invierte: quien iba segundo la bordo.
        when(notasEtapa.findByPostulacionIdInAndEtapaCodigo(List.of(1L, 2L), "PRUEBA_PUESTO"))
                .thenReturn(List.of(
                        NotaEtapa.builder().postulacionId(2L).etapaCodigo("PRUEBA_PUESTO")
                                .puntaje(new BigDecimal("95")).build()));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas();

        assertThat(filas).extracting(FilaRanking::postulacionId).containsExactly(2L, 1L);
        assertThat(filas.get(0).notaEtapa()).isEqualByComparingTo("95");
        // Quien no rindio la prueba no hereda su nota de la preseleccion: va sin nota.
        assertThat(filas.get(1).notaEtapa()).isNull();
    }

    // ============ El ponderado de lo ya rendido ============

    /**
     * El ponderado de lo ya rendido: las dos etapas que existen, reescaladas sobre 100.
     *
     * <p>Estas pruebas existen porque la cifra es facil de calcular mal de tres formas
     * distintas, y las tres dan un numero con pinta de correcto: dividir entre 100 —que
     * devuelve 70 puntos disfrazados—, dividir entre un 70 escrito a mano —que falla en las
     * vacantes de la v3— y sumar los pesos de las cuatro etapas en vez de buscar los dos que
     * hacen falta.
     */
    @Test
    void elPonderadoMezclaLasDosEtapasSobreLaSumaDeSusPesos() {
        candidatos(candidato(1L, "ALTA", "82"));
        pesosDeEtapa("PERFIL_INTEGRAL", "40", "PRUEBA_PUESTO", "30");
        notaDeLaPrueba(1L, "73");

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas();

        // (82 x 40 + 73 x 30) / 70 = 78.14, no (82 x 40 + 73 x 30) / 100 = 54.70.
        assertThat(filas.get(0).ponderado().sobre100()).isEqualByComparingTo("78.14");
    }

    /**
     * El divisor sale de los pesos de la vacante, no de un 70 escrito a mano.
     *
     * <p>Con las mismas dos notas y otro reparto tiene que dar otro numero. Si diera el mismo
     * seria que el 70 esta en el codigo, y las vacantes que siguen en versiones viejas
     * saldrian con una cifra que no es la suya.
     */
    @Test
    void elPonderadoCambiaSiLaVacanteReparteLosPesosDeOtraForma() {
        candidatos(candidato(1L, "ALTA", "82"));
        pesosDeEtapa("PERFIL_INTEGRAL", "40", "PRUEBA_PUESTO", "20");
        notaDeLaPrueba(1L, "73");

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas();

        // (82 x 40 + 73 x 20) / 60 = 79.00, distinto del 78.14 del reparto 40/30.
        assertThat(filas.get(0).ponderado().sobre100()).isEqualByComparingTo("79.00");
    }

    /** El desglose trae las tres cifras con que se rehace la cuenta a mano. */
    @Test
    void elDesgloseTraeElCurriculumYLasDosEtapas() {
        candidatos(candidato(1L, "ALTA", "82"));
        pesosDeEtapa("PERFIL_INTEGRAL", "40", "PRUEBA_PUESTO", "30");
        notaDeLaPrueba(1L, "73");
        pesos(peso(11L, "100"));
        notasDeLaTanda.add(nota(1L, 11L, "76.50"));

        FilaRanking fila = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas().get(0);

        assertThat(fila.ponderado().cv()).isEqualByComparingTo("76.50");
        assertThat(fila.ponderado().perfil()).isEqualByComparingTo("82");
        assertThat(fila.ponderado().prueba()).isEqualByComparingTo("73");
    }

    /**
     * Con una sola de las dos notas no hay ponderado, y eso es la respuesta correcta.
     *
     * <p>Reescalar sobre una etapa devuelve esa misma nota, y en la tabla se leeria como «ya
     * tiene su ponderado» junto al de quien si rindio las dos. Media cuenta no es una cuenta.
     */
    @Test
    void sinLaNotaDeLaPruebaNoHayPonderado() {
        candidatos(candidato(1L, "ALTA", "82"));
        pesosDeEtapa("PERFIL_INTEGRAL", "40", "PRUEBA_PUESTO", "30");

        FilaRanking fila = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas().get(0);

        assertThat(fila.ponderado().sobre100()).isNull();
        // El desglose si trae lo que hay: lo que falta es la mezcla, no la mitad que existe.
        assertThat(fila.ponderado().perfil()).isEqualByComparingTo("82");
        assertThat(fila.ponderado().prueba()).isNull();
    }

    /**
     * Una version de pesos sin la prueba no revienta el ranking entero.
     *
     * <p>Las hubo antes de que la prueba existiera, y siguen atadas a las notas de entonces.
     * Sin su peso no hay con que mezclar: la cifra falta, pero la pantalla se pinta.
     */
    @Test
    void sinPesoDeLaPruebaNoHayPonderadoYTampocoExcepcion() {
        candidatos(candidato(1L, "ALTA", "82"));
        pesosDeEtapa("PERFIL_INTEGRAL", "100");
        notaDeLaPrueba(1L, "73");

        FilaRanking fila = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas().get(0);

        assertThat(fila.ponderado().sobre100()).isNull();
    }

    /**
     * El ponderado sobrevive al numerado de las filas.
     *
     * <p>Es la unica prueba que caza el fallo mas facil de este cambio: el ranking construye
     * cada fila, la ordena y despues la RECONSTRUYE campo a campo para ponerle su puesto.
     * Olvidar el campo en esa segunda copia compila, no rompe ninguna otra prueba, y deja el
     * cien por cien de las filas sin cifra mientras la primera construccion se ve perfecta.
     */
    @Test
    void elPonderadoSobreviveAlNumeradoDeLasFilas() {
        candidatos(candidato(1L, "ALTA", "82"), candidato(2L, "ALTA", "60"));
        pesosDeEtapa("PERFIL_INTEGRAL", "40", "PRUEBA_PUESTO", "30");
        notaDeLaPrueba(1L, "73");
        notaDeLaPrueba(2L, "95");

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas();

        // Quien bordo la prueba va primero, y su cifra viaja con el.
        assertThat(filas.get(0).puesto()).isEqualTo(1);
        assertThat(filas.get(0).ponderado().sobre100()).isEqualByComparingTo("75.00");
        assertThat(filas.get(1).ponderado().sobre100()).isEqualByComparingTo("78.14");
    }

    /**
     * Los pesos se piden UNA vez para la tanda, no una vez por candidato.
     *
     * <p>Con cien postulantes, una consulta por fila son cien consultas para pintar la
     * pantalla que existe justamente para mirar la tanda de una vez. Todo este bloque del
     * servicio se escribio para eso y esta prueba es la que impide deshacerlo sin querer.
     */
    @Test
    void elPonderadoNoPideNadaPorFila() {
        candidatos(candidato(1L, "ALTA", "82"), candidato(2L, "ALTA", "60"),
                candidato(3L, "MEDIA", "44"));
        pesosDeEtapa("PERFIL_INTEGRAL", "40", "PRUEBA_PUESTO", "30");

        servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO");

        verify(pesosEtapa, times(1)).findByVersionPesosId(2L);
        // Dos: la de la etapa que se mira y la de la otra. Nunca dos por candidato.
        verify(notasEtapa, times(2))
                .findByPostulacionIdInAndEtapaCodigo(anyList(), org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * El codigo del criterio viaja con la nota.
     *
     * <p>Existe por una razon de pantalla, no de modelo: una tabla con una columna por
     * criterio pone «Resultados demostrables» encima de una celda que dice «40», y esa
     * cabecera decide el ancho de la columna. Sin el nombre corto, ocho criterios sacan la
     * tabla de la ventana.
     *
     * <p>Se comprueba que van los DOS y que no se confunden: el corto rotula y el largo
     * explica.
     */
    @Test
    void elCodigoDelCriterioViajaJuntoAlNombre() {
        candidatos(candidato(1L, "ALTA", "90"));
        pesos(peso(7L, "25"));
        notasDeLaTanda.add(nota(1L, 7L, "80"));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas.get(0).notasCriterio()).singleElement()
                .satisfies(n -> {
                    assertThat(n.codigo()).isEqualTo("CV_C7");
                    assertThat(n.criterio()).isEqualTo("Criterio 7");
                });
    }

    @Test
    void unaEtapaInventadaSeRechazaConLasQueExisten() {
        // La vacante ya la sirve el @BeforeEach: el 400 llega antes de listar la tanda.
        when(etapasCatalogo.existsById("ENTREVISTA")).thenReturn(false);
        when(etapasCatalogo.findAllByOrderByOrdenAsc()).thenReturn(List.of(
                Etapa.builder().codigo("PERFIL_INTEGRAL").build(),
                Etapa.builder().codigo("PRUEBA_PUESTO").build()));

        assertThatThrownBy(() -> servicio.ranking(quien, VACANTE, "ENTREVISTA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENTREVISTA")
                .hasMessageContaining("PRUEBA_PUESTO");
    }

    @Test
    void laFirmaViejaSigueSiendoLaPreseleccionSinTocarElCatalogo() {
        candidatos(candidato(1L, "ALTA", "90"));

        servicio.ranking(quien, VACANTE);

        verifyNoInteractions(etapasCatalogo);
    }

    // ============ Las columnas de la prueba del puesto ============

    /**
     * En la etapa técnica las columnas son las de SU rúbrica.
     *
     * <p>Hasta aquí las cinco pestañas devolvían los mismos ocho criterios del currículum, así
     * que en «Prueba del puesto» se veían ocho columnas del CV bajo una cabecera que decía
     * «prueba». Los de una prueba son de su plantilla: dos vacantes traen columnas distintas.
     *
     * <p>Y el <b>peso</b> sale de los {@code puntos} de la rúbrica —que suman 100— y no de
     * {@code peso_criterio}, que solo tiene filas para los ocho globales: pedirle el peso
     * aquí devolvería nulo en todas las columnas.
     */
    @Test
    void enLaPruebaLasColumnasSonLasDeSuRubrica() {
        candidatos(candidato(1L, "ALTA", "90"));
        pesos(peso(7L, "25"));
        laRubricaDeLaPrueba(1L, criterioDePrueba(50L, "CAJA", "Manejo y control de caja", "20"),
                criterioDePrueba(51L, "DIVISAS", "Conocimiento de divisas", "15"));
        notasDeLaTanda.add(nota(1L, 50L, "18"));
        when(etapasCatalogo.existsById("PRUEBA_PUESTO")).thenReturn(true);

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas();

        assertThat(filas.get(0).notasCriterio())
                .extracting(NotaCriterioResponse::codigo)
                .containsExactly("CAJA", "DIVISAS");
        assertThat(filas.get(0).notasCriterio().get(0).peso()).isEqualByComparingTo("20");
        assertThat(filas.get(0).notasCriterio().get(0).maximo()).isEqualByComparingTo("20");
        assertThat(filas.get(0).notasCriterio().get(0).puntaje()).isEqualByComparingTo("18");
        // Sin calificar todavía: la columna existe y sale hueca, que no es lo mismo que
        // desaparecer de la lista.
        assertThat(filas.get(0).notasCriterio().get(1).puntaje()).isNull();
    }

    /**
     * Quien aún no ha rendido sale sin criterios, no con los del currículum.
     *
     * <p>Es el estado normal de una tanda a medio recorrer: casi todos siguen en perfil
     * integral. Antes esto reventaba la pestaña entera —{@code laRubricaDe} lanza un 404
     * cuando no hay intento— y caer a los ocho del CV sería peor todavía: ocho columnas del
     * currículum debajo de una cabecera que dice «prueba».
     */
    @Test
    void quienNoHaRendidoLaPruebaSaleSinCriterios() {
        candidatos(candidato(1L, "ALTA", "90"), candidato(2L, "ALTA", "80"));
        pesos(peso(7L, "25"));
        laRubricaDeLaPrueba(1L, criterioDePrueba(50L, "CAJA", "Manejo y control de caja", "100"));
        when(etapasCatalogo.existsById("PRUEBA_PUESTO")).thenReturn(true);

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas();

        assertThat(filas).hasSize(2);
        assertThat(porId(filas, 1L).notasCriterio()).hasSize(1);
        assertThat(porId(filas, 2L).notasCriterio()).isEmpty();
    }

    /**
     * El cuestionario técnico no tiene rúbrica, y eso son cero columnas.
     *
     * <p>Se califica pregunta a pregunta contando criterios, no repartiendo cien puntos entre
     * unos apartados. Es lo mismo que ya hace {@code ServicioCalificacionPruebaImpl}: devolver
     * vacío en vez de reventar es lo que deja que la pestaña se abra igual.
     */
    @Test
    void elCuestionarioTecnicoNoTraeColumnasDeRubrica() {
        candidatos(candidato(1L, "ALTA", "90"));
        pesos(peso(7L, "25"));
        laVacanteRindeElCuestionarioTecnico();
        when(etapasCatalogo.existsById("PRUEBA_PUESTO")).thenReturn(true);

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas();

        assertThat(filas.get(0).notasCriterio()).isEmpty();
        verifyNoInteractions(intentos);
    }

    /**
     * La nota del currículum sigue siendo la del currículum.
     *
     * <p>Es la cicatriz de un 675 sobre 100: {@code nota_criterio} es de las tres etapas y
     * sumar sin filtrar por rúbrica mezcla las notas de la prueba con las del CV. Aquí la
     * misma postulación tiene notas de las dos, y la cifra del retrato solo puede contar las
     * ocho globales.
     */
    @Test
    void laNotaDelCurriculumNoCuentaLosCriteriosDeLaPrueba() {
        candidatos(candidato(1L, "ALTA", "90"));
        pesos(peso(7L, "100"));
        laRubricaDeLaPrueba(1L, criterioDePrueba(50L, "CAJA", "Manejo y control de caja", "100"));
        notasDeLaTanda.add(nota(1L, 7L, "60"));    // del currículum
        notasDeLaTanda.add(nota(1L, 50L, "90"));   // de la prueba
        when(etapasCatalogo.existsById("PRUEBA_PUESTO")).thenReturn(true);

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE, "PRUEBA_PUESTO").filas();

        assertThat(filas.get(0).notaCurriculum()).isEqualByComparingTo("60");
    }

    // ============ El orden de la tanda ============

    @Test
    void unCandidatoSinGrupoNoRompeElRankingYSeVaAlFinal() {
        // Antes de la primera pasada NADIE tiene grupo, así que este es el estado normal de
        // una vacante recién abierta: si revienta aquí, la pantalla no se puede ni abrir.
        candidatos(candidato(1L, null, "90"),
                   candidato(2L, "ALTA", "40"),
                   candidato(3L, "INCOMPATIBLE", "95"));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas).extracting(FilaRanking::postulacionId)
                .containsExactly(2L, 3L, 1L);
    }

    @Test
    void mandaElGrupoAntesQueLaNota() {
        // Quien llega a la nota arrastrando un riesgo crítico no va por delante de quien
        // llega sin ninguno. Ordenar por número escondería justo eso.
        candidatos(candidato(1L, "POTENCIAL_CON_RIESGO", "95"),
                   candidato(2L, "ALTA", "40"));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas).extracting(FilaRanking::postulacionId).containsExactly(2L, 1L);
    }

    @Test
    void dentroDelMismoGrupoMandaLaNotaMasAlta() {
        candidatos(candidato(1L, "ALTA", "70"),
                   candidato(2L, "ALTA", "95"),
                   candidato(3L, "ALTA", "40"));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas).extracting(FilaRanking::postulacionId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void quienTodaviaNoTieneNotaVaDespuesDeQuienSiLaTiene() {
        // Un hueco no es un cero, pero tampoco puede colarse arriba: mientras la IA no lo
        // haya leído, ese candidato no compite con los que ya tienen número.
        candidatos(candidato(1L, "ALTA", null),
                   candidato(2L, "ALTA", "40"));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas).extracting(FilaRanking::postulacionId).containsExactly(2L, 1L);
    }

    @Test
    void aIgualGrupoYNotaDesempataElNombre() {
        // Para que la lista no baile entre dos recargas de la misma pantalla.
        candidatos(conNombre(candidato(1L, "ALTA", "80"), "Zulema", "Vargas"),
                   conNombre(candidato(2L, "ALTA", "80"), "Ana", "Beltrán"));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas).extracting(FilaRanking::candidato)
                .containsExactly("Ana Beltrán", "Zulema Vargas");
    }

    @Test
    void elNumeroDeFilaSePoneCuandoYaEstanOrdenadas() {
        // Es la posición en la tanda, no un dato del candidato: numerar antes daría el
        // número de llegada, que no le importa a nadie.
        candidatos(candidato(1L, "INCOMPATIBLE", "95"),
                   candidato(2L, "ALTA", "40"));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas).extracting(FilaRanking::puesto).containsExactly(1, 2);
        assertThat(filas.get(0).postulacionId()).isEqualTo(2L);
    }

    // ============ La ciudad ============

    @Test
    @DisplayName("La ciudad se pinta con su departamento delante, sobre la fila ya numerada")
    void laCiudadLlevaElDepartamentoDelante() {
        // Hay provincias homónimas —Lima y Lima, San Martín y San Martín—, así que una
        // columna que solo dijera «Lima» no distinguiría la capital de Cañete.
        //
        // Se mira sobre la lista que DEVUELVE el ranking, no sobre la de antes de ordenar:
        // numerar copia el record campo a campo, y ciudad y ciudadCodigo son dos String
        // vecinos que se pueden intercambiar sin que el compilador diga nada.
        candidatos(enCiudad(candidato(1L, "ALTA", "90"), "0402"));
        elCatalogoDice(lugar("0402", 2, "04", "Camaná"), lugar("04", 1, null, "Arequipa"));

        FilaRanking fila = servicio.ranking(quien, VACANTE).filas().get(0);

        assertThat(fila.ciudad()).isEqualTo("Arequipa — Camaná");
        assertThat(fila.ciudadCodigo()).isEqualTo("0402");
    }

    @Test
    @DisplayName("Quien vive fuera del Perú sale sin departamento inventado")
    void elExtranjeroNoLlevaDepartamento() {
        // EXT no cuelga de ningún departamento. Ponerle un guion delante obligaría a
        // inventarse la mitad de la izquierda.
        candidatos(enCiudad(candidato(1L, "ALTA", "90"), "EXT"));
        elCatalogoDice(lugar("EXT", 1, null, "Fuera del Perú"));

        FilaRanking fila = servicio.ranking(quien, VACANTE).filas().get(0);

        assertThat(fila.ciudad()).isEqualTo("Fuera del Perú");
        assertThat(fila.ciudadCodigo()).isEqualTo("EXT");
    }

    @Test
    @DisplayName("Sin ciudad, las dos columnas van vacías — no se rellenan con el texto libre")
    void sinCiudadNoSeInventaNada() {
        // A quien creó su cuenta antes de que la ciudad fuera obligatoria no se le vuelve a
        // preguntar. Su fila va vacía, y vacía se ordena al final: eso es información, no
        // un hueco que haya que tapar con perfil_candidato.ubicacion.
        candidatos(conNombre(candidato(1L, "ALTA", "90"), "Camila", "Reyes"));

        FilaRanking fila = servicio.ranking(quien, VACANTE).filas().get(0);

        assertThat(fila.ciudad()).isNull();
        assertThat(fila.ciudadCodigo()).isNull();
        verifyNoInteractions(ubigeos);
    }

    // ============ La pretensión ============

    @Test
    @DisplayName("Sin ver_pretension no viaja — y la consulta ni se lanza")
    void sinElPermisoLaPretensionNoViaja() {
        // La V36 lo dejó decidido: si la pretensión apareciera junto a la nota, pesaría en
        // la decisión, que es justo lo que este sistema evita. La ve quien negocia.
        candidatos(conPretension(candidato(1L, "ALTA", "90"), "2500", "3500", "PEN"));

        FilaRanking fila = servicio.ranking(quien, VACANTE).filas().get(0);

        assertThat(fila.pretensionMin()).isNull();
        assertThat(fila.pretensionMax()).isNull();
        assertThat(fila.pretensionMoneda()).isNull();
        // No es solo no pintarla: sin permiso el dato no llega ni a la memoria de esta
        // petición.
        verifyNoInteractions(perfilesCandidato);
    }

    @Test
    @DisplayName("Con ver_pretension sí viaja: la ve quien negocia, cuando toca")
    void conElPermisoLaPretensionViaja() {
        candidatos(conPretension(candidato(1L, "ALTA", "90"), "2500", "3500", "PEN"));

        FilaRanking fila = servicio.ranking(quienTambienVeLaPretension(), VACANTE).filas().get(0);

        assertThat(fila.pretensionMin()).isEqualByComparingTo("2500");
        assertThat(fila.pretensionMax()).isEqualByComparingTo("3500");
        assertThat(fila.pretensionMoneda()).isEqualTo("PEN");
    }

    @Test
    @DisplayName("Quien no declaró pretensión va con los tres campos en null, no en cero")
    void quienNoDeclaroPretensionVaVacio() {
        // Un cero diría «pide cero», que es una frase distinta de «no lo ha dicho».
        candidatos(conNombre(candidato(1L, "ALTA", "90"), "Camila", "Reyes"));
        lenient().when(perfilesCandidato.findByPersonaIdIn(anyList())).thenReturn(List.of());

        FilaRanking fila = servicio.ranking(quienTambienVeLaPretension(), VACANTE).filas().get(0);

        assertThat(fila.pretensionMin()).isNull();
        assertThat(fila.pretensionMoneda()).isNull();
    }

    // ============ La nota del currículum ============

    @Test
    void laNotaDelCurriculumSeRepartEntreLosCriteriosQueSiTienenNota() {
        // Si la IA no pudo puntuar uno, lo justo es repartir su peso entre los demás. Sobre
        // 100 fijos, un criterio sin calificar restaría como si fuera un cero, y el
        // candidato pagaría por un fallo del modelo.
        candidatos(candidato(1L, "ALTA", "80"));
        pesos(peso(10L, "30"), peso(11L, "70"));
        notasDeLaTanda.add(nota(1L, 10L, "80"));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas.get(0).notaCurriculum()).isEqualByComparingTo("80.00");
    }

    @Test
    void conTodosLosCriteriosCalificadosCadaUnoPesaLoSuyo() {
        candidatos(candidato(1L, "ALTA", "80"));
        pesos(peso(10L, "30"), peso(11L, "70"));
        notasDeLaTanda.addAll(List.of(nota(1L, 10L, "80"), nota(1L, 11L, "60")));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        // 80 x 30 + 60 x 70 = 6600, repartido entre 100 de peso
        assertThat(filas.get(0).notaCurriculum()).isEqualByComparingTo("66.00");
    }

    @Test
    void laNotaDelCurriculumNoSeMezclaConLaDeOtrasEtapas() {
        // El fallo que se arregló. La versión de pesos trae también los diez criterios de la
        // simulación y los nueve de la validación, así que sumar «todo lo que tenga peso»
        // hacía que esta columna cambiara en cuanto un facilitador calificaba una simulación.
        // Dos currículums idénticos mostraban notas distintas sin que nadie tocara un
        // currículum.
        candidatos(candidato(1L, "ALTA", "80"));
        pesos(peso(10L, "100"));
        // El 99 es del criterio de una simulación: tiene peso en la misma versión, pero no
        // es del currículum y no puede entrar en esta cuenta.
        pesoSuelto(peso(99L, "100"));
        notasDeLaTanda.addAll(List.of(nota(1L, 10L, "80"), nota(1L, 99L, "20")));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas.get(0).notaCurriculum()).isEqualByComparingTo("80.00");
    }

    @Test
    void sinNingunaNotaDeCriterioNoSeInventaUnCero() {
        candidatos(candidato(1L, "ALTA", "80"));
        pesos(peso(10L, "30"));

        List<FilaRanking> filas = servicio.ranking(quien, VACANTE).filas();

        assertThat(filas.get(0).notaCurriculum()).isNull();
    }

    // ============ La segunda pasada ============

    @Test
    void laSegundaPasadaSeNiegaSiTodaviaNadieTieneNota() {
        // El fallo que se arregló. Es un error fácil de cometer —basta pulsar el botón
        // mientras la tanda se está cargando— y caro de descubrir, porque encolar no falla:
        // se gasta el modelo que razona en la mitad de la lista elegida por el apellido.
        candidatos(candidato(1L, null, null), candidato(2L, null, null));

        assertThatThrownBy(() -> servicio.cribaFina(quien, VACANTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orden alfabético");

        verify(cola, never()).encolarCribaFina(anyLong());
    }

    @Test
    void elCorteSeCalculaSobreLosQueTienenNotaNoSobreLaListaEntera() {
        // Seis candidatos y solo tres calificados: la mitad son dos, no tres. Contar los
        // seis metería en el corte a alguien de quien no se sabe nada.
        candidatos(candidato(1L, "ALTA", "90"), candidato(2L, "ALTA", "80"),
                   candidato(3L, "ALTA", "70"), candidato(4L, null, null),
                   candidato(5L, null, null), candidato(6L, null, null));
        when(parametros.entero(ORGANIZACION, "porcentaje_criba_fina", 50)).thenReturn(50);
        lenient().when(cola.encolarCribaFina(anyLong())).thenReturn(true);

        servicio.cribaFina(quien, VACANTE);

        verify(cola).encolarCribaFina(1L);
        verify(cola).encolarCribaFina(2L);
        verify(cola, never()).encolarCribaFina(3L);
    }

    @Test
    void elCorteEsAlMenosUno() {
        // Con tres candidatos y un corte del 20 % la cuenta da cero, y una segunda pasada
        // que no mira a nadie no es una segunda pasada: es un botón que no hace nada.
        candidatos(candidato(1L, "ALTA", "90"), candidato(2L, "ALTA", "80"),
                   candidato(3L, "ALTA", "70"));
        when(parametros.entero(ORGANIZACION, "porcentaje_criba_fina", 50)).thenReturn(20);

        servicio.cribaFina(quien, VACANTE);

        verify(cola).encolarCribaFina(1L);
        verify(cola, never()).encolarCribaFina(2L);
    }

    @Test
    void quienYaPasoPorLaFinaNoRepite() {
        // La fina es la definitiva: volver a pedirla cuesta lo mismo y no cambia nada.
        yaPasoPorLaFina(1L);
        candidatos(candidato(1L, "ALTA", "90"), candidato(2L, "ALTA", "80"));
        when(parametros.entero(ORGANIZACION, "porcentaje_criba_fina", 50)).thenReturn(100);

        servicio.cribaFina(quien, VACANTE);

        verify(cola, never()).encolarCribaFina(1L);
        verify(cola).encolarCribaFina(2L);
    }

    // ============ A quién no toca ninguna criba ============

    @Test
    void laCribaRapidaNoResucitaAQuienYaTermino() {
        // El fallo que se arregló. Un retirado, un contratado y un descartado siguen en la
        // vacante con su currículum puesto: barrerla sin mirar el estado los devolvía a «por
        // confirmar», a la bandeja de alguien, y pagaba el modelo por cada uno.
        catalogoDeEstados();
        Postulacion viva = candidato(1L, null, null);
        Postulacion contratado = enEstado(candidato(2L, null, null), "CONTRATADO");
        Postulacion retirado = enEstado(candidato(3L, null, null), "CERRADA");
        conCurriculum(viva, contratado, retirado);
        sinRetrato(1L, 2L, 3L);
        candidatos(viva, contratado, retirado);
        when(cola.encolarCribaRapida(1L)).thenReturn(true);

        servicio.cribaRapida(quien, VACANTE);

        verify(cola).encolarCribaRapida(1L);
        verify(cola, never()).encolarCribaRapida(2L);
        verify(cola, never()).encolarCribaRapida(3L);
    }

    @Test
    void laCribaDeUnCurriculumSeNiegaSiLaPostulacionYaTermino() {
        catalogoDeEstados();
        Postulacion contratado = enEstado(candidato(2L, null, null), "CONTRATADO");
        when(postulaciones.findByIdAndOrganizacionId(2L, ORGANIZACION))
                .thenReturn(Optional.of(contratado));
        conCurriculum(contratado);

        assertThatThrownBy(() -> servicio.cribarCv(quien, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cerrada");

        verify(cola, never()).encolarCribaCv(anyLong());
    }

    // ============ Los botones cuentan lo que de verdad encolaron ============

    @Test
    @DisplayName("La criba rápida mira la tanda en bloque, no candidato a candidato")
    void laCribaRapidaNoPreguntaUnaVezPorCandidato() {
        // La tanda de una criba es la misma que pinta el ranking: crece con los candidatos
        // que se apunten. Preguntar por fila con qué pasada está cada uno y si tiene
        // currículum eran dos consultas por persona antes siquiera de decidir si se encola.
        Postulacion[] tanda = new Postulacion[60];
        Long[] ids = new Long[60];
        for (int i = 0; i < 60; i++) {
            tanda[i] = candidato(i + 1L, null, null);
            ids[i] = i + 1L;
        }
        conCurriculum(tanda);
        sinRetrato(ids);
        candidatos(tanda);
        lenient().when(cola.encolarCribaRapida(anyLong())).thenReturn(true);

        assertThat(servicio.cribaRapida(quien, VACANTE).candidatos()).isEqualTo(60);

        verify(cvs, times(1)).findByPostulacionIdIn(anyList());
        verify(cola, times(1)).estadoDe(anyList());
        // Las dos consultas por fila que había antes. Si vuelve cualquiera de las dos, los
        // conteos de arriba siguen en 1 y solo esto lo delata.
        verify(cvs, never()).findByPostulacionId(anyLong());
        verify(cola, never()).pasadaDe(anyLong());
    }

    @Test
    void laCribaRapidaSoloCuentaAQuienDeVerdadQuedoEnLaCola() {
        // Antes se sumaba siempre. Un segundo clic respondía «2 en cola» sin haber encolado
        // a nadie, y ese número falso quedaba escrito también en la auditoría, que es donde
        // alguien va a mirar dentro de tres meses.
        Postulacion uno = candidato(1L, null, null);
        Postulacion dos = candidato(2L, null, null);
        conCurriculum(uno, dos);
        sinRetrato(1L, 2L);
        candidatos(uno, dos);
        when(cola.encolarCribaRapida(1L)).thenReturn(true);
        when(cola.encolarCribaRapida(2L)).thenReturn(false);

        assertThat(servicio.cribaRapida(quien, VACANTE).candidatos()).isEqualTo(1);
        // Y a quien no se encoló tampoco se le mueve el estado: seguiría diciendo que se le
        // está calificando cuando no hay nada calificándose.
        verify(maquina, never()).transicionar(eq(dos), anyString(), any(), any(),
                anyBoolean(), anyBoolean(), any());
    }

    @Test
    void laCribaDeUnCurriculumAvisaCuandoNoHabiaNadaQueHacer() {
        Postulacion p = candidato(1L, null, null);
        when(postulaciones.findByIdAndOrganizacionId(1L, ORGANIZACION)).thenReturn(Optional.of(p));
        conCurriculum(p);
        when(cola.encolarCribaCv(1L)).thenReturn(false);

        assertThat(servicio.cribarCv(quien, 1L).estado()).isEqualTo("SIN_CAMBIOS");
        verify(auditoria, never()).registrar(anyLong(), any(), anyString(), anyString(),
                anyLong(), any(), any(), any());
    }

    // ============ El alcance del permiso que se está usando ============

    @Test
    void laCribaFiltraConElAlcanceDeSuPropioPermisoYNoConElDeOtro() {
        // El endpoint exige ajustar_nota, pero el filtro miraba siempre el alcance de
        // ver_embudo. Un rol con ajustar_nota limitado a sus vacantes y ver_embudo sin
        // límite podía cribar una convocatoria ajena. Hoy ningún rol sembrado tiene esa
        // forma, pero los roles se configuran desde el panel.
        // La vacante es de otra persona del equipo, así que el guardián dice que no —pero
        // solo si se le pregunta por ajustar_nota, que es el permiso del endpoint.
        when(alcanceVacante.laVacanteVisible(any(), eq(VACANTE), eq("ajustar_nota")))
                .thenThrow(new ResourceNotFoundException("Vacante", "id", VACANTE));

        assertThatThrownBy(() -> servicio.cribaRapida(quien, VACANTE))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cola, never()).encolarCribaRapida(anyLong());
        // Y lo que fija esta prueba: se pregunta por el permiso del endpoint, no por el otro.
        verify(alcanceVacante, never()).laVacanteVisible(any(), eq(VACANTE), eq("ver_embudo"));
    }

    // ============ Apoyo ============

    /** El catálogo de estados, con los tres de los que ya no se sale. */
    private void catalogoDeEstados() {
        when(estados.findAllByOrderByOrden()).thenReturn(List.of(
                estado("PERFIL_POR_CONFIRMAR", false),
                estado("CONTRATADO", true),
                estado("NO_CONTINUA", true),
                estado("CERRADA", true)));
    }

    private EstadoPostulacion estado(String codigo, boolean esFinal) {
        return EstadoPostulacion.builder().codigo(codigo).nombre(codigo).esFinal(esFinal).build();
    }

    private Postulacion enEstado(Postulacion p, String codigo) {
        p.setEstadoCodigo(codigo);
        return p;
    }

    /** Les pone currículum: sin archivo la criba ni los mira. */
    /**
     * Estos candidatos tienen currículum.
     *
     * <p>Se apunta por las dos vías porque hay dos formas de preguntarlo y las dos existen:
     * la ficha de uno solo lo pide suelto, y las cribas lo piden para la tanda entera. Si el
     * doble solo supiera contestar a una, la criba se saltaría a todo el mundo y el test
     * pasaría a verde sin haber probado nada.
     */
    private void conCurriculum(Postulacion... losSuyos) {
        for (Postulacion p : losSuyos) {
            Cv suyo = Cv.builder().id(500L + p.getId()).postulacionId(p.getId()).build();
            lenient().when(cvs.findByPostulacionId(p.getId())).thenReturn(Optional.of(suyo));
            cvsDeLaTanda.add(suyo);
        }
    }

    /** Nadie le ha hecho todavía el retrato: es a quien la criba rápida tiene que mirar. */
    private void sinRetrato(Long... postulacionIds) {
        for (Long id : postulacionIds) {
            estadosDeLaTanda.put(id, new ColaCalificacionIa.Estado("SIN_EMPEZAR", null));
        }
    }

    /** Un peso que existe en la versión pero cuyo criterio no es del currículum. */
    private void pesoSuelto(PesoCriterio suelto) {
        List<PesoCriterio> todos = new ArrayList<>(
                pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(2L, "OPERATIVO"));
        todos.add(suelto);
        when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(2L, "OPERATIVO"))
                .thenReturn(todos);
    }

    /** Deja a ese candidato como ya mirado a fondo, antes de armar la tanda. */
    private void yaPasoPorLaFina(Long postulacionId) {
        estadosDeLaTanda.put(postulacionId,
                new ColaCalificacionIa.Estado("TERMINADA", "FINA"));
    }

    /** Deja la vacante con estos candidatos y sus notas ya preparadas. */
    /**
     * Los pesos de etapa de la version de la vacante, en pares codigo/peso.
     *
     * <p>Abre ademas la pestaña de la prueba en el catalogo: el ranking valida que la etapa
     * pedida exista antes de mirar nada, y sin eso los siete tests del ponderado mueren en
     * la primera linea con un mensaje que no habla de pesos.
     */
    private void pesosDeEtapa(String... codigoYPeso) {
        lenient().when(etapasCatalogo.existsById("PRUEBA_PUESTO")).thenReturn(true);
        List<com.renaser.ai.ai_engine.pesos.entity.PesoEtapa> lista = new ArrayList<>();
        for (int i = 0; i < codigoYPeso.length; i += 2) {
            lista.add(com.renaser.ai.ai_engine.pesos.entity.PesoEtapa.builder()
                    .versionPesosId(2L).etapaCodigo(codigoYPeso[i])
                    .peso(new BigDecimal(codigoYPeso[i + 1])).build());
        }
        when(pesosEtapa.findByVersionPesosId(2L)).thenReturn(lista);
    }

    /** Le pone nota de la prueba del puesto, que es la otra mitad del ponderado. */
    private void notaDeLaPrueba(Long postulacionId, String puntaje) {
        notasDeLaPrueba.add(NotaEtapa.builder()
                .postulacionId(postulacionId).etapaCodigo("PRUEBA_PUESTO")
                .puntaje(new BigDecimal(puntaje)).build());
        lenient().when(notasEtapa.findByPostulacionIdInAndEtapaCodigo(anyList(), eq("PRUEBA_PUESTO")))
                .thenReturn(notasDeLaPrueba);
    }

    private final List<NotaEtapa> notasDeLaPrueba = new ArrayList<>();

    private void candidatos(Postulacion... losSuyos) {
        List<Postulacion> lista = new ArrayList<>(List.of(losSuyos));
        when(postulaciones.findByVacanteIdOrderByCreadoEnDesc(VACANTE)).thenReturn(lista);
        List<Long> ids = lista.stream().map(Postulacion::getId).toList();
        for (Postulacion p : lista) {
            lenient().when(postulaciones.findById(p.getId())).thenReturn(Optional.of(p));
        }

        // El ranking pide todo de la tanda en bloque y no candidato a candidato: once
        // consultas en total en vez de once por fila. Los dobles tienen que hablar ese
        // idioma o el ranking sale vacío sin que ninguna aserción explique por qué.
        lenient().when(datosCv.findByPostulacionIdIn(ids)).thenReturn(List.of());
        lenient().when(perfiles.findByPostulacionIdIn(ids)).thenReturn(List.of());
        lenient().when(hallazgos.findByPerfilTalentoIdIn(anyList())).thenReturn(List.of());
        lenient().when(cvs.findByPostulacionIdIn(ids)).thenReturn(cvsDeLaTanda);
        lenient().when(alertas.findByPostulacionIdIn(ids)).thenReturn(List.of());
        lenient().when(notasCriterio.findByPostulacionIdIn(ids)).thenReturn(notasDeLaTanda);
        lenient().when(notasEtapa.findByPostulacionIdInAndEtapaCodigo(ids, "PERFIL_INTEGRAL"))
                .thenReturn(notasDeEtapa);
        lenient().when(usuarios.findAllById(anyIterable())).thenReturn(usuariosDeLaTanda);
        lenient().when(personas.findAllById(anyIterable())).thenReturn(personasDeLaTanda);
        lenient().when(archivos.findAllById(anyIterable())).thenReturn(List.of());

        Map<Long, ColaCalificacionIa.Estado> estado = new HashMap<>();
        for (Long id : ids) {
            estado.put(id, estadosDeLaTanda.getOrDefault(id,
                    new ColaCalificacionIa.Estado("TERMINADA", "RAPIDA")));
        }
        lenient().when(cola.estadoDe(ids)).thenReturn(estado);
    }

    // Lo que la tanda irá acumulando según se van creando los candidatos. Se llena en los
    // ayudantes de abajo y se entrega entero cuando el ranking lo pide en bloque.
    private final List<NotaEtapa> notasDeEtapa = new ArrayList<>();
    private final List<NotaCriterio> notasDeLaTanda = new ArrayList<>();
    private final List<Usuario> usuariosDeLaTanda = new ArrayList<>();
    private final List<Persona> personasDeLaTanda = new ArrayList<>();
    private final List<Cv> cvsDeLaTanda = new ArrayList<>();
    private final Map<Long, ColaCalificacionIa.Estado> estadosDeLaTanda = new HashMap<>();

    /**
     * Un candidato de la tanda. La nota se registra aquí mismo porque en el ranking va
     * pegada a él: separarlas obligaría a repetir el mismo par en cada test.
     */
    private Postulacion candidato(Long id, String grupo, String notaEtapaValor) {
        Postulacion p = Postulacion.builder()
                .id(id).organizacionId(ORGANIZACION).vacanteId(VACANTE).usuarioId(100L + id)
                .uuid(UUID.nameUUIDFromBytes(String.valueOf(id).getBytes()))
                .estadoCodigo("PERFIL_POR_CONFIRMAR").grupoPrioridad(grupo)
                .build();
        if (notaEtapaValor != null) {
            notasDeEtapa.add(NotaEtapa.builder()
                    .postulacionId(id).etapaCodigo("PERFIL_INTEGRAL")
                    .puntaje(new BigDecimal(notaEtapaValor)).build());
        }
        return p;
    }

    /** Le pone nombre, que es lo último que desempata cuando grupo y nota coinciden. */
    private Postulacion conNombre(Postulacion p, String nombre, String apellidos) {
        Persona persona = laPersonaDe(p);
        persona.setNombre(nombre);
        persona.setApellidos(apellidos);
        return p;
    }

    /** Le pone dónde vive: el código del ubigeo, que es lo que guarda la persona. */
    private Postulacion enCiudad(Postulacion p, String ciudadUbigeo) {
        laPersonaDe(p).setCiudadUbigeo(ciudadUbigeo);
        return p;
    }

    /** Le pone pretensión declarada, que cuelga de su perfil y no de la postulación. */
    private Postulacion conPretension(Postulacion p, String min, String max, String moneda) {
        Persona persona = laPersonaDe(p);
        lenient().when(perfilesCandidato.findByPersonaIdIn(anyList())).thenReturn(List.of(
                com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato.builder()
                        .id(p.getId()).personaId(persona.getId())
                        .pretensionMin(new BigDecimal(min)).pretensionMax(new BigDecimal(max))
                        .pretensionMoneda(moneda).build()));
        return p;
    }

    /** La persona de esa postulación, creándola con su usuario la primera vez. */
    private Persona laPersonaDe(Postulacion p) {
        Long personaId = 200L + p.getId();
        return personasDeLaTanda.stream()
                .filter(persona -> personaId.equals(persona.getId()))
                .findFirst()
                .orElseGet(() -> {
                    usuariosDeLaTanda.add(Usuario.builder()
                            .id(p.getUsuarioId()).personaId(personaId).build());
                    Persona nueva = Persona.builder()
                            .id(personaId).nombre("Camila").apellidos("Reyes").build();
                    personasDeLaTanda.add(nueva);
                    return nueva;
                });
    }

    /**
     * El catálogo de ubigeo, contestando solo lo que le preguntan.
     *
     * <p>El ranking lo consulta dos veces —primero las provincias que salen en la tanda,
     * después los departamentos de esas provincias— y el doble tiene que distinguirlas: uno
     * que devolviera siempre todo dejaría pasar un servicio que pidiera mal.
     */
    private void elCatalogoDice(com.renaser.ai.ai_engine.perfil.entity.Ubigeo... filas) {
        lenient().when(ubigeos.findAllById(anyIterable())).thenAnswer(invocacion -> {
            List<String> pedidos = new ArrayList<>();
            invocacion.<Iterable<String>>getArgument(0).forEach(pedidos::add);
            return List.of(filas).stream()
                    .filter(u -> pedidos.contains(u.getCodigo()))
                    .toList();
        });
    }

    private com.renaser.ai.ai_engine.perfil.entity.Ubigeo lugar(
            String codigo, int nivel, String padre, String nombre) {
        return com.renaser.ai.ai_engine.perfil.entity.Ubigeo.builder()
                .codigo(codigo).nivel((short) nivel).padre(padre).nombre(nombre).activo(true)
                .build();
    }

    /** El mismo de siempre, pero además con el permiso que abre la pretensión. */
    private ContextoUsuario quienTambienVeLaPretension() {
        return new ContextoUsuario(10L, 20L, ORGANIZACION, "EQUIPO", List.of(1L),
                Map.of("ver_embudo", "TODO", "ajustar_nota", "TODO", "ver_pretension", "TODO"));
    }

    private void pesos(PesoCriterio... losSuyos) {
        when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(2L, "OPERATIVO"))
                .thenReturn(List.of(losSuyos));
        when(criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(
                "PERFIL_INTEGRAL"))
                .thenReturn(List.of(losSuyos).stream()
                        .map(p -> Criterio.builder().id(p.getCriterioId())
                                .nombre("Criterio " + p.getCriterioId())
                                // El codigo se distingue del nombre a proposito: si los dos
                                // dijeran lo mismo, un DTO que devolviera el largo en el
                                // campo del corto pasaria el test sin que nadie lo viera.
                                .codigo("CV_C" + p.getCriterioId())
                                .puntos(new BigDecimal("100")).build())
                        .toList());
    }

    /** La fila de ese candidato, buscada por su id: el orden lo decide el ranking. */
    private FilaRanking porId(List<FilaRanking> filas, Long postulacionId) {
        return filas.stream().filter(f -> f.postulacionId().equals(postulacionId))
                .findFirst().orElseThrow();
    }

    /**
     * Esa postulación rindió la prueba del puesto, con esta rúbrica.
     *
     * <p>La rúbrica cuelga de la versión de la plantilla que abrió el candidato, no de la
     * vacante: por eso hacen falta las dos consultas, el intento y sus criterios.
     */
    private void laRubricaDeLaPrueba(Long postulacionId, Criterio... rubrica) {
        lenient().when(intentos.findByPostulacionIdIn(anyList())).thenReturn(List.of(
                com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba.builder()
                        .postulacionId(postulacionId).versionPlantillaPruebaId(VERSION_PRUEBA)
                        .build()));
        lenient().when(criterios.findByVersionPlantillaPruebaIdInOrderByOrden(anyCollection()))
                .thenReturn(List.of(rubrica));
    }

    private static final Long VERSION_PRUEBA = 9L;

    private Criterio criterioDePrueba(Long id, String codigo, String nombre, String puntos) {
        return Criterio.builder().id(id).codigo(codigo).nombre(nombre)
                .etapaCodigo("PRUEBA_PUESTO").versionPlantillaPruebaId(VERSION_PRUEBA)
                .puntos(new BigDecimal(puntos)).build();
    }

    /** Esta vacante rinde el cuestionario técnico y no la prueba del puesto (V43). */
    private void laVacanteRindeElCuestionarioTecnico() {
        when(alcanceVacante.laVacanteVisible(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(VACANTE), anyString()))
                .thenReturn(Vacante.builder()
                        .id(VACANTE).organizacionId(ORGANIZACION).puestoId(3L).versionPesosId(2L)
                        .titulo("Analista de procesos").responsableUsuarioId(10L)
                        .instrumentoEtapaTecnica("CUESTIONARIO_TECNICO").build());
    }

    private PesoCriterio peso(Long criterioId, String valor) {
        return PesoCriterio.builder().versionPesosId(2L).nivelPuestoCodigo("OPERATIVO")
                .criterioId(criterioId).peso(new BigDecimal(valor)).build();
    }

    /**
     * Una nota de criterio. Lleva la postulación a la que pertenece porque el ranking trae
     * las de toda la tanda de una vez y luego las agrupa: sin ese dato acabarían todas
     * juntas bajo la misma clave.
     */
    private NotaCriterio nota(Long postulacionId, Long criterioId, String puntaje) {
        return NotaCriterio.builder().postulacionId(postulacionId).criterioId(criterioId)
                .puntaje(new BigDecimal(puntaje)).origen("IA").build();
    }

    // ================== Reabrir la evaluación ==================
    //
    // Se le devuelve el turno a un candidato que no llegó a tiempo. Casi todo lo que hace
    // este método es decir que NO, y por buenas razones: cada negativa protege una decisión
    // que ya pudo tomarse con la nota vieja.

    @org.junit.jupiter.api.Nested
    @DisplayName("Al reabrir la evaluación de un candidato")
    class ReabrirEvaluacion {

        private static final long POSTULACION = 77L;

        private Postulacion postulacionConEvaluacion(String estado, Long evaluacionId) {
            Postulacion p = Postulacion.builder()
                    .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE)
                    .usuarioId(500L).estadoCodigo(estado).build();
            p.setEvaluacionId(evaluacionId);
            return p;
        }

        private com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion evaluacion(
                String estado, java.time.Instant iniciada) {
            return com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion.builder()
                    .id(9L).organizacionId(ORGANIZACION).estado(estado)
                    .venceEn(java.time.Instant.parse("2020-01-01T00:00:00Z"))
                    .iniciadaEn(iniciada)
                    .build();
        }

        private void hayPostulacion(Postulacion p) {
            when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                    .thenReturn(Optional.of(p));
        }

        @Test
        @DisplayName("Sin motivo escrito no se reabre: es lo único que queda de por qué se hizo")
        void exigeMotivo() {
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(() -> servicio.reabrirEvaluacion(quien, POSTULACION, 7, "  "))
                    .withMessageContaining("motivo");
            verifyNoInteractions(maquina);
        }

        @Test
        @DisplayName("Una postulación que ya terminó no se reabre")
        void noSiYaTermino() {
            Postulacion p = postulacionConEvaluacion("DESCARTADA", 9L);
            hayPostulacion(p);
            when(maquina.yaTermino(p)).thenReturn(true);

            org.assertj.core.api.Assertions.assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.reabrirEvaluacion(quien, POSTULACION, 7, "se equivocó"))
                    .withMessageContaining("ya terminó");
        }

        @Test
        @DisplayName("Si nunca tuvo evaluación, no hay nada que reabrir")
        void noSiNoTieneEvaluacion() {
            hayPostulacion(postulacionConEvaluacion(LE_TOCA, null));

            org.assertj.core.api.Assertions.assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.reabrirEvaluacion(quien, POSTULACION, 7, "se equivocó"))
                    .withMessageContaining("no tiene evaluación");
        }

        @Test
        @DisplayName("Una entregada no se reabre: su nota ya pudo usarse para decidir")
        void noSiYaFueEntregada() {
            hayPostulacion(postulacionConEvaluacion(LE_TOCA, 9L));
            when(evaluaciones.findById(9L)).thenReturn(Optional.of(evaluacion("TERMINADA", null)));

            org.assertj.core.api.Assertions.assertThatIllegalStateException()
                    .isThrownBy(() -> servicio.reabrirEvaluacion(quien, POSTULACION, 7, "se equivocó"))
                    .withMessageContaining("invalidaría su nota");
        }

        @Test
        @DisplayName("La que nunca empezó vuelve a PENDIENTE, con el plazo que se le diga")
        void laQueNoEmpezoVuelveAPendiente() {
            Postulacion p = postulacionConEvaluacion("PERFIL_VENCIDO", 9L);
            hayPostulacion(p);
            var eva = evaluacion("VENCIDA", null);
            when(evaluaciones.findById(9L)).thenReturn(Optional.of(eva));

            var salida = servicio.reabrirEvaluacion(quien, POSTULACION, 5, "el correo no le llegó");

            assertThat(eva.getEstado()).isEqualTo("PENDIENTE");
            assertThat(salida.diasDePlazo()).isEqualTo(5);
            assertThat(salida.venceEn()).isAfter(java.time.Instant.now());
            // La vigencia se estira con el plazo: de nada sirve poder responder si el
            // resultado ya no vale cuando responda.
            assertThat(eva.getVigenteHasta()).isEqualTo(eva.getVenceEn());
            verify(maquina).transicionar(eq(p), eq(LE_TOCA), eq(quien), eq("el correo no le llegó"),
                    eq(false), eq(false), isNull());
            verify(auditoria).registrar(eq(ORGANIZACION), eq(quien), eq("reabrir_evaluacion"),
                    eq("postulacion"), eq(POSTULACION), anyMap(), anyMap(),
                    eq("el correo no le llegó"));
        }

        @Test
        @DisplayName("La que ya había empezado vuelve a EN_CURSO y conserva lo respondido")
        void laEmpezadaConservaLoRespondido() {
            hayPostulacion(postulacionConEvaluacion(LE_TOCA, 9L));
            var eva = evaluacion("EN_CURSO", java.time.Instant.parse("2020-01-02T00:00:00Z"));
            when(evaluaciones.findById(9L)).thenReturn(Optional.of(eva));

            servicio.reabrirEvaluacion(quien, POSTULACION, 3, "se le cayó internet");

            assertThat(eva.getEstado()).isEqualTo("EN_CURSO");
            // Ya estaba en el estado que le toca: no se le mueve por moverlo, porque cada
            // transición deja rastro y un rastro falso confunde a quien lo lea después.
            verify(maquina, never()).transicionar(any(), anyString(), any(), anyString(),
                    anyBoolean(), anyBoolean(), any());
        }

        @Test
        @DisplayName("Sin días, el plazo sale del parámetro de la organización")
        void sinDiasUsaElParametro() {
            hayPostulacion(postulacionConEvaluacion(LE_TOCA, 9L));
            when(evaluaciones.findById(9L)).thenReturn(Optional.of(evaluacion("PENDIENTE", null)));
            when(parametros.entero(ORGANIZACION, "dias_plazo_evaluacion", 14)).thenReturn(21);

            var salida = servicio.reabrirEvaluacion(quien, POSTULACION, null, "ampliación acordada");

            assertThat(salida.diasDePlazo()).isEqualTo(21);
        }

        @Test
        @DisplayName("Un número de días absurdo se ignora y manda el parámetro")
        void diasNoPositivosCaenAlParametro() {
            hayPostulacion(postulacionConEvaluacion(LE_TOCA, 9L));
            when(evaluaciones.findById(9L)).thenReturn(Optional.of(evaluacion("PENDIENTE", null)));
            when(parametros.entero(ORGANIZACION, "dias_plazo_evaluacion", 14)).thenReturn(14);

            assertThat(servicio.reabrirEvaluacion(quien, POSTULACION, 0, "cero días").diasDePlazo())
                    .isEqualTo(14);
        }
    }

    private static final String LE_TOCA = "PERFIL_TURNO_CANDIDATO";

    @org.junit.jupiter.api.Nested
    @DisplayName("Recalificar es la palanca de la calibración")
    class Recalificar {

        private Postulacion conEvaluacion() {
            Postulacion p = Postulacion.builder().id(2L).organizacionId(ORGANIZACION)
                    .vacanteId(VACANTE).evaluacionId(60L).build();
            when(postulaciones.findByIdAndOrganizacionId(2L, ORGANIZACION))
                    .thenReturn(Optional.of(p));
            return p;
        }

        @Test
        @DisplayName("si no falta nada, se rehace el evaluador igual: quien pide RE-calificar quiere notas nuevas")
        void rehaceAunqueEsteTodoHecho() {
            conEvaluacion();
            when(cola.encolarPerfilIntegral(2L)).thenReturn(false);   // todo TERMINADO
            when(cola.reencolarEvaluador(2L)).thenReturn(true);

            assertThat(servicio.recalificar(quien, 2L).estado()).isEqualTo("ENCOLADA");
        }

        @Test
        @DisplayName("con un trabajo vivo no se paga dos veces: SIN_CAMBIOS")
        void conUnoVivoNoDuplica() {
            conEvaluacion();
            when(cola.encolarPerfilIntegral(2L)).thenReturn(false);
            when(cola.reencolarEvaluador(2L)).thenReturn(false);

            assertThat(servicio.recalificar(quien, 2L).estado()).isEqualTo("SIN_CAMBIOS");
        }

        @Test
        @DisplayName("sin evaluación entregada no hay nada que recalificar")
        void sinEvaluacionNoHayNada() {
            when(postulaciones.findByIdAndOrganizacionId(2L, ORGANIZACION))
                    .thenReturn(Optional.of(Postulacion.builder().id(2L)
                            .organizacionId(ORGANIZACION).vacanteId(VACANTE).build()));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> servicio.recalificar(quien, 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no tiene evaluación");
        }
    }
}
