package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.messaging.TrabajoIaPublisher;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La fila de agentes: quién va después de quién, y qué se le contesta a la pantalla que
 * pregunta cómo va.
 *
 * <p>Aquí viven los fallos que ya se rompieron una vez:
 *
 * <ul>
 *   <li>El evaluador corría en una criba de currículums, donde nadie ha respondido nada
 *       todavía: una llamada al modelo pagada para no puntuar nada.
 *   <li>Un candidato que falló y salió bien al reintentar quedaba marcado como fallido
 *       para siempre, porque la fila fallida no se borra y se preguntaba por ella antes que
 *       por el resultado bueno.
 *   <li>Un candidato ya cribado que después entregaba su evaluación se quedaba en
 *       «calificando» para siempre: el primer paso ya estaba hecho, no se encolaba nada, y
 *       ningún botón lo rescataba.
 *   <li>Una pasada fina fallida sobre una rápida terminada se contaba como terminada, y unas
 *       notas provisionales se presentaban como definitivas.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La cola de calificación")
class ColaCalificacionIaImplTest {

    private static final long POSTULACION = 55L;

    @Mock private TrabajoIaRepository trabajos;
    @Mock private RegistroTrabajosIa registro;
    @Mock private TrabajoIaPublisher publicador;
    @Mock private PuenteCalificacionIa puente;
    @Mock private AgenteSeleccion datosCv;
    @Mock private AgenteSeleccion evidenciaCv;
    @Mock private AgenteSeleccion evaluador;
    @Mock private AgenteSeleccion potencialRiesgo;
    @Mock private AgenteSeleccion pruebaPuesto;
    @Mock private AgenteSeleccion simulacion;

    private ColaCalificacionIaImpl cola;

    @BeforeEach
    void armarLaCola() {
        when(datosCv.codigo()).thenReturn(AgenteDatosCv.CODIGO);
        when(evidenciaCv.codigo()).thenReturn(AgenteEvidenciaCv.CODIGO);
        when(evaluador.codigo()).thenReturn(AgenteEvaluador.CODIGO);
        when(potencialRiesgo.codigo()).thenReturn(AgentePotencialRiesgo.CODIGO);
        when(pruebaPuesto.codigo()).thenReturn(AgentePruebaPuesto.CODIGO);
        when(simulacion.codigo()).thenReturn(AgenteSimulacion.CODIGO);
        // Solo lo miran los tests que encolan; los que preguntan «cómo va» no pasan por ahí.
        lenient().when(puente.organizacionDe(POSTULACION)).thenReturn(1L);
        cola = conLaCalificacion(true);
    }

    // ============ Por dónde empieza cada pasada ============

    @Test
    void laPasadaFinaEmpiezaSacandoLosDatosDelCandidato() {
        // Los datos entran también en la fila normal, y no solo en la rápida. Antes quien se
        // calificaba por aquí salía sin teléfono, sin experiencia y sin último puesto, y no
        // había manera de rellenarlo después.
        cola.encolarPerfilIntegral(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO, "FINA", null);
    }

    @Test
    void siLaFichaDeDatosYaEstaSacadaNoSeVuelveAPagar() {
        // Son datos copiados del currículum, no notas: no cambian salvo que cambie el
        // archivo, y quien lo reemplaza borra la ficha para que se vuelva a sacar.
        when(puente.tieneFichaCv(POSTULACION)).thenReturn(true);

        cola.encolarPerfilIntegral(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO, "FINA", null);
    }

    @Test
    void laPasadaRapidaEmpiezaSacandoLosDatosDelCandidato() {
        // Los datos cuestan casi nada y son lo que hace legible la tabla del ranking: sin
        // ellos la primera pasada deja una lista de nombres de archivo.
        cola.encolarCribaRapida(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO, "RAPIDA", null);
    }

    @Test
    void laCribaDeCurriculumRecorreLaMismaFilaQueLaCalificacionCompleta() {
        // No la decide quien llama: la decide el candidato. Si no entregó evaluación, la
        // fila se salta sola al evaluador más adelante.
        cola.encolarCribaCv(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO, "FINA", null);
    }

    @Test
    void elAvisoALaColaLlevaElIdDelTrabajoRecienCreado() {
        when(registro.crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO, "FINA", null))
                .thenReturn(Optional.of(trabajo(42L, AgenteDatosCv.CODIGO, "PENDIENTE", "FINA")));

        cola.encolarCribaFina(POSTULACION);

        verify(publicador).publicar(42L);
    }

    @Test
    void elAvisoSaleSoloCuandoElTrabajoSeCreoDeVerdad() {
        // Si el trabajo ya estaba hecho no hay fila nueva, y publicar un aviso pondría a un
        // consumidor a buscar algo que nadie creó.
        when(registro.crearSiHaceFalta(anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        assertThat(cola.encolarPerfilIntegral(POSTULACION)).isFalse();

        verifyNoInteractions(publicador);
    }

    @Test
    void conLaCalificacionApagadaNoSeEncolaNada() {
        // El interruptor existe para poder desplegar sin gastar en el modelo. Si se colara
        // un solo trabajo, la factura llegaría igual.
        ColaCalificacionIaImpl apagada = conLaCalificacion(false);

        assertThat(apagada.encolarPerfilIntegral(POSTULACION)).isFalse();
        assertThat(apagada.encolarCribaRapida(POSTULACION)).isFalse();
        assertThat(apagada.encolarCribaFina(POSTULACION)).isFalse();
        apagada.reintentarAtascados();

        verifyNoInteractions(registro);
        verifyNoInteractions(publicador);
    }

    // ============ El paso que toca, y no siempre es el primero ============

    @Test
    void alCribadoQueDespuesEntregaSuEvaluacionSeLeEncolaElEvaluador() {
        // El fallo más caro que tenía esto. Al candidato se le leyó el currículum en una
        // criba; cuando después entrega su evaluación se pide calificarlo, el primer paso ya
        // está TERMINADO, no se encolaba nada, y la postulación se quedaba en «calificando»
        // para siempre sin que ningún botón la rescatara.
        when(puente.tieneFichaCv(POSTULACION)).thenReturn(true);
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(true);
        yaHecho(AgenteEvidenciaCv.CODIGO, "FINA", 4L);
        when(registro.crearSiHaceFalta(1L, POSTULACION, AgenteEvaluador.CODIGO, "FINA", null))
                .thenReturn(Optional.of(trabajo(6L, AgenteEvaluador.CODIGO, "PENDIENTE", "FINA")));

        assertThat(cola.encolarPerfilIntegral(POSTULACION)).isTrue();

        verify(publicador).publicar(6L);
    }

    @Test
    void loQueSeCalculoAntesDeLoQueLoAlimentaSeRehace() {
        // El retrato de ese mismo candidato se armó en la criba, sin sus respuestas. Cuando
        // el evaluador termina, ese retrato queda viejo: se rehace en vez de dejar en pie uno
        // que no vio la mitad de lo que hay.
        yaHecho(AgentePotencialRiesgo.CODIGO, "FINA", 5L);

        ejecutar(trabajo(6L, AgenteEvaluador.CODIGO, "PENDIENTE", "FINA"), evaluador);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgentePotencialRiesgo.CODIGO, "FINA", 6L);
    }

    @Test
    void conUnPasoVivoNoSeEncolaNadaMas() {
        // La fila sigue sola. Adelantarse pagaría dos veces al proveedor por el mismo
        // candidato, que es exactamente lo que la fila existe para evitar.
        when(puente.tieneFichaCv(POSTULACION)).thenReturn(true);
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                POSTULACION, AgenteEvidenciaCv.CODIGO, "FINA"))
                .thenReturn(Optional.of(trabajo(7L, AgenteEvidenciaCv.CODIGO, "EN_CURSO", "FINA")));

        assertThat(cola.encolarPerfilIntegral(POSTULACION)).isFalse();

        verifyNoInteractions(registro);
        verifyNoInteractions(publicador);
    }

    @Test
    void unPasoFallidoSeVuelveAIntentar() {
        // Es lo que permite rescatar a mano una postulación que se colgó por un problema del
        // proveedor: se pide otra vez y arranca justo donde se rompió.
        when(puente.tieneFichaCv(POSTULACION)).thenReturn(true);
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                POSTULACION, AgenteEvidenciaCv.CODIGO, "FINA"))
                .thenReturn(Optional.of(trabajo(7L, AgenteEvidenciaCv.CODIGO, "FALLIDO", "FINA")));

        cola.encolarPerfilIntegral(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO, "FINA", null);
    }

    // ============ Quién va después de quién ============

    @Test
    void trasLeerElCurriculumTocaElEvaluadorSiElCandidatoRespondio() {
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(true);

        ejecutar(trabajo(1L, AgenteEvidenciaCv.CODIGO, "PENDIENTE", "FINA"), evidenciaCv);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvaluador.CODIGO, "FINA", 1L);
    }

    @Test
    void sinEvaluacionEntregadaElEvaluadorSeSalta() {
        // Es el fallo que se arregló: el evaluador corría igual y gastaba una petición al
        // modelo para puntuar una lista de respuestas vacía.
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(false);

        ejecutar(trabajo(1L, AgenteEvidenciaCv.CODIGO, "PENDIENTE", "FINA"), evidenciaCv);

        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), eq(AgenteEvaluador.CODIGO), anyString(), any());
        verify(registro).crearSiHaceFalta(
                1L, POSTULACION, AgentePotencialRiesgo.CODIGO, "FINA", 1L);
    }

    @Test
    void laPasadaRapidaNiSiquieraPreguntaPorElEvaluador() {
        // Su fila no lo incluye. Preguntar por la evaluación sería una consulta a la base
        // por cada candidato de la tanda para una respuesta que da igual.
        ejecutar(trabajo(1L, AgenteDatosCv.CODIGO, "PENDIENTE", "RAPIDA"), datosCv);
        verify(registro).crearSiHaceFalta(
                1L, POSTULACION, AgenteEvidenciaCv.CODIGO, "RAPIDA", 1L);

        ejecutar(trabajo(2L, AgenteEvidenciaCv.CODIGO, "PENDIENTE", "RAPIDA"), evidenciaCv);
        verify(registro).crearSiHaceFalta(
                1L, POSTULACION, AgentePotencialRiesgo.CODIGO, "RAPIDA", 2L);

        verify(puente, never()).tieneEvaluacionEntregada(anyLong());
    }

    @Test
    void elRetratoCierraLaFilaYNoEncolaANadieMas() {
        ejecutar(trabajo(3L, AgentePotencialRiesgo.CODIGO, "PENDIENTE", "FINA"), potencialRiesgo);

        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), anyString(), anyString(), any());
    }

    // ============ Ejecutar un trabajo ============

    @Test
    void siOtroConsumidorSeAdelantoElAgenteNiSeLlama() {
        // El mismo mensaje puede entregarse dos veces: sin esto se pagarían dos llamadas al
        // modelo por el mismo candidato.
        when(registro.tomar(1L)).thenReturn(Optional.empty());

        cola.ejecutar(1L);

        verify(evidenciaCv, never()).ejecutar(any(TrabajoIa.class));
    }

    @Test
    void unAgenteQueNadieSabeAtenderSeMarcaFallidoSinGastarReintentos() {
        // Pasaría con una fila vieja de un agente que ya no existe. Reintentarla tres veces
        // no la arregla: el código no va a aparecer solo.
        when(registro.tomar(1L))
                .thenReturn(Optional.of(trabajo(1L, "AGENTE_QUE_NO_EXISTE", "EN_CURSO", "FINA")));

        cola.ejecutar(1L);

        verify(registro).fallar(eq(1L), eq(0), anyString());
    }

    @Test
    void siElAgenteFallaYQuedaIntentoElTrabajoVuelveALaCola() {
        TrabajoIa suyo = trabajo(1L, AgenteEvidenciaCv.CODIGO, "EN_CURSO", "FINA");
        when(registro.tomar(1L)).thenReturn(Optional.of(suyo));
        doThrowEn(evidenciaCv);
        when(registro.fallar(eq(1L), anyInt(), anyString())).thenReturn(true);

        cola.ejecutar(1L);

        verify(publicador).publicar(1L);
        // Y no se avanza al siguiente: el retrato se armaría con un currículum sin leer.
        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void agotadosLosIntentosNoSeVuelveAPublicar() {
        // Publicar de nuevo un trabajo ya marcado FALLIDO sería un mensaje que da vueltas
        // sin que nadie pueda tomarlo.
        when(registro.tomar(1L))
                .thenReturn(Optional.of(trabajo(1L, AgenteEvidenciaCv.CODIGO, "EN_CURSO", "FINA")));
        doThrowEn(evidenciaCv);
        when(registro.fallar(eq(1L), anyInt(), anyString())).thenReturn(false);

        cola.ejecutar(1L);

        verifyNoInteractions(publicador);
    }

    // ============ Cómo va ============

    @Test
    void sinTrabajosLaCalificacionNiSiquieraEmpezo() {
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of());

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("SIN_EMPEZAR");
    }

    @Test
    void unTrabajoVivoMandaSobreTodoLoDemas() {
        // Aunque los anteriores hayan salido bien, mientras quede uno la calificación no ha
        // terminado y la pantalla no puede dar por bueno lo que hay.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO, "PENDIENTE", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("EN_CURSO");
    }

    @Test
    void quienFalloYLuegoSalioBienAlReintentarNoQuedaMarcadoComoFallido() {
        // El fallo que se arregló. La fila fallida no se borra nunca, así que preguntar
        // primero por el fallo dejaba marcado como fallido a un candidato que sí tiene su
        // retrato hecho: aparecía en rojo en el ranking con todas sus notas puestas.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO, "FALLIDO", "FINA"),
                trabajo(2L, AgenteEvidenciaCv.CODIGO, "TERMINADO", "FINA"),
                trabajo(3L, AgentePotencialRiesgo.CODIGO, "TERMINADO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    @Test
    void siElRetratoNoLlegoAHacerseLaCalificacionSiEsFallida() {
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO, "FALLIDO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("FALLIDA");
    }

    @Test
    void unaCribaTieneMenosTrabajosYAunAsiEstaTerminada() {
        // Por eso el final de la fila se mira por el agente que cierra la etapa y no
        // contando trabajos: contar diría «en curso» para siempre en cuanto el evaluador se
        // salta.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO, "TERMINADO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    @Test
    void unaFinaFallidaSobreUnaRapidaTerminadaEsFallida() {
        // El fallo que se arregló. Mirando las dos pasadas juntas se encontraba el agente
        // que cierra la etapa TERMINADO en la rápida y se contestaba «terminada»: el
        // contador de fallidos marcaba cero y unas notas provisionales del modelo que no
        // razona se presentaban como definitivas.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO, "TERMINADO", "RAPIDA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO, "TERMINADO", "RAPIDA"),
                trabajo(3L, AgenteEvidenciaCv.CODIGO, "FALLIDO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("FALLIDA");
        // Y la nota que hay en pantalla sigue siendo la de la rápida: provisional, pero real.
        assertThat(cola.pasadaDe(POSTULACION)).isEqualTo("RAPIDA");
    }

    // ============ Con qué pasada está calificado ============

    @Test
    void sinRetratoTerminadoTodaviaNoHayPasada() {
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO, "TERMINADO", "RAPIDA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO, "EN_CURSO", "RAPIDA")));

        assertThat(cola.pasadaDe(POSTULACION)).isNull();
    }

    @Test
    void conSoloLaPrimeraPasadaLaNotaEsProvisional() {
        // La pantalla lo necesita para no enseñar como definitivo un número que salió del
        // modelo que no razona: se ven igual y no valen lo mismo.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgentePotencialRiesgo.CODIGO, "TERMINADO", "RAPIDA")));

        assertThat(cola.pasadaDe(POSTULACION)).isEqualTo("RAPIDA");
    }

    @Test
    void laFinaMandaAunqueLaRapidaSeaPosterior() {
        // La fina es la que pisa las notas: da igual el orden en que quedaron las filas.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgentePotencialRiesgo.CODIGO, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO, "TERMINADO", "RAPIDA")));

        assertThat(cola.pasadaDe(POSTULACION)).isEqualTo("FINA");
    }

    // ============ Lo que se queda atascado ============

    @Test
    void losPendientesViejosSeVuelvenAPublicar() {
        // Se pierde el mensaje, o RabbitMQ estaba caído al publicarlo. El trabajo es
        // idempotente, así que volver a empujarlo es barato.
        when(trabajos.findByEstadoAndCreadoEnBefore(eq("PENDIENTE"), any(Instant.class)))
                .thenReturn(List.of(trabajo(8L, AgenteEvidenciaCv.CODIGO, "PENDIENTE", "FINA")));

        cola.reintentarAtascados();

        verify(publicador).publicar(8L);
    }

    @Test
    void losColgadosSeDevuelvenAPendienteAntesDeVolverAPublicarlos() {
        // Alguien lo tomó y el proceso murió a mitad. Publicarlo sin devolverlo a pendiente
        // no serviría: quien lo recogiera se lo encontraría en EN_CURSO y lo soltaría.
        when(trabajos.findByEstadoAndTomadoEnBefore(eq("EN_CURSO"), any(Instant.class)))
                .thenReturn(List.of(trabajo(9L, AgenteEvidenciaCv.CODIGO, "EN_CURSO", "FINA")));

        cola.reintentarAtascados();

        verify(registro).devolverAPendiente(9L);
        verify(publicador).publicar(9L);
    }

    // ============ Apoyo ============

    // ============ Los dos que corren solos ============

    @Test
    void laPruebaDelPuestoSeEncolaSolaYNoArrastraAlRestoDeLaFila() {
        // Es otra etapa: otra rúbrica, otra escala y semanas después. Meterla en la fila del
        // retrato haría que calificar una prueba volviera a leer el currículum.
        cola.encolarPruebaPuesto(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgentePruebaPuesto.CODIGO, "FINA", null);
        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), eq(AgenteEvidenciaCv.CODIGO), anyString(), any());
    }

    @Test
    void lasPreguntasDeLaConversacionSeEncolanSolas() {
        cola.encolarPreguntasSimulacion(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteSimulacion.CODIGO, "FINA", null);
    }

    @Test
    void alTerminarLaPruebaNoSeEncolaANadieDetras() {
        // Su fila tiene un solo paso. Si esto encolara al siguiente de la fila del retrato,
        // calificar una prueba pagaría de propina un Perfil de Talento que nadie pidió.
        when(registro.tomar(9L))
                .thenReturn(Optional.of(trabajo(9L, AgentePruebaPuesto.CODIGO, "EN_CURSO", "FINA")));

        cola.ejecutar(9L);

        verify(pruebaPuesto).ejecutar(any(TrabajoIa.class));
        verify(registro).terminar(9L);
        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void unTrabajoDeOtraEtapaNoCambiaComoVaElRetrato() {
        // La trampa que esto evita: «cómo va» habla del retrato del candidato, y con estos
        // dos agentes una misma postulación tiene trabajos de tres etapas distintas. Sin
        // filtrar, pedir las preguntas de la conversación pintaba el ranking entero «en
        // curso» por algo que no toca ninguna de las notas que el ranking enseña.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO, "TERMINADO", "FINA"),
                trabajo(3L, AgenteSimulacion.CODIGO, "PENDIENTE", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    @Test
    void unaPruebaFallidaNoDejaAlCandidatoComoSiNoTuvieraRetrato() {
        // Al revés que el anterior, y por el mismo motivo: que la prueba del puesto falle no
        // borra el Perfil de Talento que ya estaba hecho.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgentePotencialRiesgo.CODIGO, "TERMINADO", "FINA"),
                trabajo(2L, AgentePruebaPuesto.CODIGO, "FALLIDO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    private ColaCalificacionIaImpl conLaCalificacion(boolean habilitada) {
        return new ColaCalificacionIaImpl(trabajos, registro, publicador, puente,
                List.of(datosCv, evidenciaCv, evaluador, potencialRiesgo, pruebaPuesto, simulacion),
                habilitada, 3, 15);
    }

    /** Deja puesto un paso ya terminado, como el que dejaría una criba anterior. */
    private void yaHecho(String agenteCodigo, String modo, Long id) {
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                POSTULACION, agenteCodigo, modo))
                .thenReturn(Optional.of(trabajo(id, agenteCodigo, "TERMINADO", modo)));
    }

    /** Deja que el agente termine bien y comprueba a quién encola la cola después. */
    private void ejecutar(TrabajoIa suyo, AgenteSeleccion agente) {
        when(registro.tomar(suyo.getId())).thenReturn(Optional.of(suyo));
        cola.ejecutar(suyo.getId());
        verify(agente).ejecutar(suyo);
        verify(registro).terminar(suyo.getId());
    }

    private void doThrowEn(AgenteSeleccion agente) {
        doThrow(new IllegalStateException("el proveedor no responde"))
                .when(agente).ejecutar(any(TrabajoIa.class));
    }

    private TrabajoIa trabajo(Long id, String agenteCodigo, String estado, String modo) {
        return TrabajoIa.builder()
                .id(id)
                .postulacionId(POSTULACION)
                .organizacionId(1L)
                .agenteCodigo(agenteCodigo)
                .estado(estado)
                .modo(modo)
                .intentos(1)
                .creadoEn(Instant.now())
                .build();
    }
}
