package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.messaging.TrabajoIaPublisher;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import org.junit.jupiter.api.BeforeEach;
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
 * <p>Aquí viven dos fallos que ya se rompieron una vez:
 *
 * <ul>
 *   <li>El evaluador corría en una criba de currículums, donde nadie ha respondido nada
 *       todavía: una llamada al modelo pagada para no puntuar nada.
 *   <li>Un candidato que falló y salió bien al reintentar quedaba marcado como fallido
 *       para siempre, porque la fila fallida no se borra y se preguntaba por ella antes que
 *       por el resultado bueno.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
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

    private ColaCalificacionIaImpl cola;

    @BeforeEach
    void armarLaCola() {
        when(datosCv.codigo()).thenReturn(AgenteDatosCv.CODIGO);
        when(evidenciaCv.codigo()).thenReturn(AgenteEvidenciaCv.CODIGO);
        when(evaluador.codigo()).thenReturn(AgenteEvaluador.CODIGO);
        when(potencialRiesgo.codigo()).thenReturn(AgentePotencialRiesgo.CODIGO);
        // Solo lo miran los tests que encolan; los que preguntan «cómo va» no pasan por ahí.
        lenient().when(puente.organizacionDe(POSTULACION)).thenReturn(1L);
        cola = conLaCalificacion(true);
    }

    // ============ Por dónde empieza cada pasada ============

    @Test
    void laPasadaFinaEmpiezaLeyendoElCurriculum() {
        cola.encolarPerfilIntegral(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO, "FINA");
    }

    @Test
    void laPasadaRapidaEmpiezaSacandoLosDatosDelCandidato() {
        // Los datos cuestan casi nada y son lo que hace legible la tabla del ranking: sin
        // ellos la primera pasada deja una lista de nombres de archivo.
        cola.encolarCribaRapida(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO, "RAPIDA");
    }

    @Test
    void laCribaDeCurriculumRecorreLaMismaFilaQueLaCalificacionCompleta() {
        // No la decide quien llama: la decide el candidato. Si no entregó evaluación, la
        // fila se salta sola al evaluador más adelante.
        cola.encolarCribaCv(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO, "FINA");
    }

    @Test
    void elAvisoALaColaLlevaElIdDelTrabajoRecienCreado() {
        when(registro.crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO, "FINA"))
                .thenReturn(Optional.of(trabajo(42L, AgenteEvidenciaCv.CODIGO, "PENDIENTE", "FINA")));

        cola.encolarCribaFina(POSTULACION);

        verify(publicador).publicar(42L);
    }

    @Test
    void elAvisoSaleSoloCuandoElTrabajoSeCreoDeVerdad() {
        // Si el trabajo ya estaba hecho no hay fila nueva, y publicar un aviso pondría a un
        // consumidor a buscar algo que nadie creó.
        when(registro.crearSiHaceFalta(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        cola.encolarPerfilIntegral(POSTULACION);

        verifyNoInteractions(publicador);
    }

    @Test
    void conLaCalificacionApagadaNoSeEncolaNada() {
        // El interruptor existe para poder desplegar sin gastar en el modelo. Si se colara
        // un solo trabajo, la factura llegaría igual.
        ColaCalificacionIaImpl apagada = conLaCalificacion(false);

        apagada.encolarPerfilIntegral(POSTULACION);
        apagada.encolarCribaRapida(POSTULACION);
        apagada.encolarCribaFina(POSTULACION);
        apagada.reintentarAtascados();

        verifyNoInteractions(registro);
        verifyNoInteractions(publicador);
    }

    // ============ Quién va después de quién ============

    @Test
    void trasLeerElCurriculumTocaElEvaluadorSiElCandidatoRespondio() {
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(true);

        ejecutar(trabajo(1L, AgenteEvidenciaCv.CODIGO, "PENDIENTE", "FINA"), evidenciaCv);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvaluador.CODIGO, "FINA");
    }

    @Test
    void sinEvaluacionEntregadaElEvaluadorSeSalta() {
        // Es el fallo que se arregló: el evaluador corría igual y gastaba una petición al
        // modelo para puntuar una lista de respuestas vacía.
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(false);

        ejecutar(trabajo(1L, AgenteEvidenciaCv.CODIGO, "PENDIENTE", "FINA"), evidenciaCv);

        verify(registro, never())
                .crearSiHaceFalta(anyLong(), anyLong(), eq(AgenteEvaluador.CODIGO), anyString());
        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgentePotencialRiesgo.CODIGO, "FINA");
    }

    @Test
    void laPasadaRapidaNiSiquieraPreguntaPorElEvaluador() {
        // Su fila no lo incluye. Preguntar por la evaluación sería una consulta a la base
        // por cada candidato de la tanda para una respuesta que da igual.
        ejecutar(trabajo(1L, AgenteDatosCv.CODIGO, "PENDIENTE", "RAPIDA"), datosCv);
        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO, "RAPIDA");

        ejecutar(trabajo(2L, AgenteEvidenciaCv.CODIGO, "PENDIENTE", "RAPIDA"), evidenciaCv);
        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgentePotencialRiesgo.CODIGO, "RAPIDA");

        verify(puente, never()).tieneEvaluacionEntregada(anyLong());
    }

    @Test
    void elRetratoCierraLaFilaYNoEncolaANadieMas() {
        ejecutar(trabajo(3L, AgentePotencialRiesgo.CODIGO, "PENDIENTE", "FINA"), potencialRiesgo);

        verify(registro, never()).crearSiHaceFalta(anyLong(), anyLong(), anyString(), anyString());
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
        verify(registro, never()).crearSiHaceFalta(anyLong(), anyLong(), anyString(), anyString());
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
    void unaCribaTieneDosTrabajosYNoTresYAunAsiEstaTerminada() {
        // Por eso el final de la fila se mira por el último agente y no contando trabajos:
        // contar diría «en curso» para siempre en cuanto el evaluador se salta.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO, "TERMINADO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
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

    private ColaCalificacionIaImpl conLaCalificacion(boolean habilitada) {
        return new ColaCalificacionIaImpl(trabajos, registro, publicador, puente,
                List.of(datosCv, evidenciaCv, evaluador, potencialRiesgo),
                habilitada, 3, 15);
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
