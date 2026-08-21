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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La tanda de agentes: quién corre a la vez, quién espera a quién, y qué se le contesta a la
 * pantalla que pregunta cómo va.
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
 *   <li>Los cuatro agentes iban en fila aunque tres de ellos no dependen unos de otros:
 *       ocho minutos y medio por candidato para esperar tres veces seguidas al mismo
 *       proveedor. Y si el primero fallaba —un PDF escaneado sin texto— la fila se cortaba
 *       ahí y el candidato no volvía a moverse nunca.
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
        when(datosCv.codigo()).thenReturn(AgenteDatosCv.CODIGO_AGENTE);
        when(evidenciaCv.codigo()).thenReturn(AgenteEvidenciaCv.CODIGO_AGENTE);
        when(evaluador.codigo()).thenReturn(AgenteEvaluador.CODIGO_AGENTE);
        when(potencialRiesgo.codigo()).thenReturn(AgentePotencialRiesgo.CODIGO_AGENTE);
        when(pruebaPuesto.codigo()).thenReturn(AgentePruebaPuesto.CODIGO_AGENTE);
        when(simulacion.codigo()).thenReturn(AgenteSimulacion.CODIGO_AGENTE);
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

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO_AGENTE, "FINA", null);
    }

    @Test
    void siLaFichaDeDatosYaEstaSacadaNoSeVuelveAPagar() {
        // Son datos copiados del currículum, no notas: no cambian salvo que cambie el
        // archivo, y quien lo reemplaza borra la ficha para que se vuelva a sacar.
        when(puente.tieneFichaCv(POSTULACION)).thenReturn(true);

        cola.encolarPerfilIntegral(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO_AGENTE, "FINA", null);
    }

    @Test
    void laPasadaRapidaEmpiezaSacandoLosDatosDelCandidato() {
        // Los datos cuestan casi nada y son lo que hace legible la tabla del ranking: sin
        // ellos la primera pasada deja una lista de nombres de archivo.
        cola.encolarCribaRapida(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO_AGENTE, "RAPIDA", null);
    }

    @Test
    void laCribaDeCurriculumRecorreLaMismaFilaQueLaCalificacionCompleta() {
        // No la decide quien llama: la decide el candidato. Si no entregó evaluación, la
        // fila se salta sola al evaluador más adelante.
        cola.encolarCribaCv(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO_AGENTE, "FINA", null);
    }

    @Test
    void elAvisoALaColaLlevaElIdDelTrabajoRecienCreado() {
        when(registro.crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO_AGENTE, "FINA", null))
                .thenReturn(Optional.of(trabajo(42L, AgenteDatosCv.CODIGO_AGENTE, "PENDIENTE", "FINA")));

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
        yaHecho(AgenteEvidenciaCv.CODIGO_AGENTE, "FINA", 4L);
        when(registro.crearSiHaceFalta(1L, POSTULACION, AgenteEvaluador.CODIGO_AGENTE, "FINA", null))
                .thenReturn(Optional.of(trabajo(6L, AgenteEvaluador.CODIGO_AGENTE, "PENDIENTE", "FINA")));

        assertThat(cola.encolarPerfilIntegral(POSTULACION)).isTrue();

        verify(publicador).publicar(6L);
    }

    @Test
    void elIdDelQueTerminaViajaHastaLaBarrera() {
        // El retrato de ese mismo candidato pudo armarse en la criba, sin sus respuestas.
        // Cuando el evaluador termina, ese retrato queda viejo, y quien lo detecta es la
        // barrera comparando ids: por eso el id del que acaba de terminar tiene que llegar
        // hasta ella. Que un retrato viejo se rehaga se comprueba en RegistroTrabajosIaTest,
        // que es donde vive esa regla.
        ejecutar(trabajo(6L, AgenteEvaluador.CODIGO_AGENTE, "PENDIENTE", "FINA"), evaluador);

        verify(registro).crearElRetratoSiLosDemasAcabaron(1L, POSTULACION,
                List.of(AgenteDatosCv.CODIGO_AGENTE, AgenteEvidenciaCv.CODIGO_AGENTE,
                        AgenteEvaluador.CODIGO_AGENTE),
                AgentePotencialRiesgo.CODIGO_AGENTE, "FINA", 6L);
    }

    @Test
    void conUnPasoVivoNoSeEncolaNadaMas() {
        // La fila sigue sola. Adelantarse pagaría dos veces al proveedor por el mismo
        // candidato, que es exactamente lo que la fila existe para evitar.
        when(puente.tieneFichaCv(POSTULACION)).thenReturn(true);
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                POSTULACION, AgenteEvidenciaCv.CODIGO_AGENTE, "FINA"))
                .thenReturn(Optional.of(trabajo(7L, AgenteEvidenciaCv.CODIGO_AGENTE, "EN_CURSO", "FINA")));

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
                POSTULACION, AgenteEvidenciaCv.CODIGO_AGENTE, "FINA"))
                .thenReturn(Optional.of(trabajo(7L, AgenteEvidenciaCv.CODIGO_AGENTE, "FALLIDO", "FINA")));

        cola.encolarPerfilIntegral(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO_AGENTE, "FINA", null);
    }

    // ============ Quién corre a la vez, y quién espera a los demás ============

    @Test
    void losTresQueNoDependenDeNadieSeEncolanALaVez() {
        // La razón del cambio entero. Los tres leen fuentes distintas —el currículum los dos
        // primeros, las respuestas de la evaluación el tercero— y escriben tablas distintas,
        // así que ninguno tiene por qué esperar a otro. En fila eran ocho minutos y medio de
        // esperar tres veces seguidas al mismo proveedor.
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(true);

        cola.encolarPerfilIntegral(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO_AGENTE, "FINA", null);
        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO_AGENTE, "FINA", null);
        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvaluador.CODIGO_AGENTE, "FINA", null);
        // Y el retrato no: mientras haya alguno vivo, armarlo lo haría con la mitad de lo
        // que va a haber.
        verify(registro, never()).crearElRetratoSiLosDemasAcabaron(
                anyLong(), anyLong(), anyList(), anyString(), anyString(), any());
    }

    @Test
    void elRetratoNoSaleHastaQueElUltimoDeLosTresTermina() {
        // La barrera. Los tres preguntan al terminar —desde el código no hay forma de saber
        // quién es el último, y menos con ocho consumidores en instancias distintas— y quien
        // contesta es la base. Aquí se comprueba que preguntan, y con qué.
        ejecutar(trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "PENDIENTE", "FINA"), evidenciaCv);

        verify(registro).crearElRetratoSiLosDemasAcabaron(1L, POSTULACION,
                List.of(AgenteDatosCv.CODIGO_AGENTE, AgenteEvidenciaCv.CODIGO_AGENTE,
                        AgenteEvaluador.CODIGO_AGENTE),
                AgentePotencialRiesgo.CODIGO_AGENTE, "FINA", 1L);
        // Y nadie encola a nadie por su cuenta: eso duplicaría trabajos.
        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void dosQueAcabanALaVezNoDisparanDosRetratos() {
        // El caso delicado. Los dos últimos en terminar pueden preguntar en el mismo
        // milisegundo, y los dos preguntan de verdad: esto no se arregla callando a uno,
        // porque no hay forma de saber cuál. Se arregla en la base, y por eso a uno se le
        // contesta con un trabajo y al otro con nada. Solo sale un aviso a la cola.
        when(registro.crearElRetratoSiLosDemasAcabaron(eq(1L), eq(POSTULACION), anyList(),
                eq(AgentePotencialRiesgo.CODIGO_AGENTE), eq("FINA"), anyLong()))
                .thenReturn(Optional.of(
                        trabajo(9L, AgentePotencialRiesgo.CODIGO_AGENTE, "PENDIENTE", "FINA")))
                .thenReturn(Optional.empty());

        ejecutar(trabajo(2L, AgenteEvidenciaCv.CODIGO_AGENTE, "PENDIENTE", "FINA"), evidenciaCv);
        ejecutar(trabajo(3L, AgenteEvaluador.CODIGO_AGENTE, "PENDIENTE", "FINA"), evaluador);

        verify(registro, times(2)).crearElRetratoSiLosDemasAcabaron(
                anyLong(), anyLong(), anyList(), anyString(), anyString(), anyLong());
        verify(publicador, times(1)).publicar(9L);
    }

    @Test
    void sinEvaluacionEntregadaElEvaluadorSeSalta() {
        // Es el fallo que se arregló: el evaluador corría igual y gastaba una petición al
        // modelo para puntuar una lista de respuestas vacía.
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(false);

        cola.encolarPerfilIntegral(POSTULACION);

        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), eq(AgenteEvaluador.CODIGO_AGENTE), anyString(), any());
        // Y los otros dos sí, que son los que sí tienen algo que leer.
        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteDatosCv.CODIGO_AGENTE, "FINA", null);
        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteEvidenciaCv.CODIGO_AGENTE, "FINA", null);
    }

    @Test
    void laPasadaRapidaNiSiquieraPreguntaPorElEvaluador() {
        // Su tanda no lo incluye. Preguntar por la evaluación sería una consulta a la base
        // por cada candidato de la tanda para una respuesta que da igual.
        cola.encolarCribaRapida(POSTULACION);
        verify(registro).crearSiHaceFalta(
                1L, POSTULACION, AgenteDatosCv.CODIGO_AGENTE, "RAPIDA", null);
        verify(registro).crearSiHaceFalta(
                1L, POSTULACION, AgenteEvidenciaCv.CODIGO_AGENTE, "RAPIDA", null);

        // Y su barrera espera a dos, no a tres: si esperara al evaluador, una criba de
        // currículums no terminaría nunca.
        ejecutar(trabajo(2L, AgenteEvidenciaCv.CODIGO_AGENTE, "PENDIENTE", "RAPIDA"), evidenciaCv);
        verify(registro).crearElRetratoSiLosDemasAcabaron(1L, POSTULACION,
                List.of(AgenteDatosCv.CODIGO_AGENTE, AgenteEvidenciaCv.CODIGO_AGENTE),
                AgentePotencialRiesgo.CODIGO_AGENTE, "RAPIDA", 2L);

        verify(puente, never()).tieneEvaluacionEntregada(anyLong());
    }

    @Test
    void elRetratoCierraLaFilaYNoEncolaANadieMas() {
        ejecutar(trabajo(3L, AgentePotencialRiesgo.CODIGO_AGENTE, "PENDIENTE", "FINA"), potencialRiesgo);

        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), anyString(), anyString(), any());
        verify(registro, never()).crearElRetratoSiLosDemasAcabaron(
                anyLong(), anyLong(), anyList(), anyString(), anyString(), any());
    }

    @Test
    void siLosTresYaEstanYSoloFaltaElRetratoSePideSoloEl() {
        // El rescate. Una tanda de cuando esto era una fila y se cortaba en el primer fallo
        // se quedó con los tres pasos hechos y sin retrato. Pedir la calificación otra vez
        // no debe volver a pagar nada de lo que ya está: solo lo que falta.
        when(puente.tieneFichaCv(POSTULACION)).thenReturn(true);
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(true);
        yaHecho(AgenteEvidenciaCv.CODIGO_AGENTE, "FINA", 2L);
        yaHecho(AgenteEvaluador.CODIGO_AGENTE, "FINA", 3L);
        when(registro.crearElRetratoSiLosDemasAcabaron(eq(1L), eq(POSTULACION), anyList(),
                eq(AgentePotencialRiesgo.CODIGO_AGENTE), eq("FINA"), isNull()))
                .thenReturn(Optional.of(
                        trabajo(9L, AgentePotencialRiesgo.CODIGO_AGENTE, "PENDIENTE", "FINA")));

        assertThat(cola.encolarPerfilIntegral(POSTULACION)).isTrue();

        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), anyString(), anyString(), any());
        verify(publicador).publicar(9L);
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
        TrabajoIa suyo = trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "EN_CURSO", "FINA");
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
                .thenReturn(Optional.of(trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "EN_CURSO", "FINA")));
        doThrowEn(evidenciaCv);
        when(registro.fallar(eq(1L), anyInt(), anyString())).thenReturn(false);

        cola.ejecutar(1L);

        verifyNoInteractions(publicador);
    }

    @Test
    void siElLectorDelCurriculumSeAgotaElRetratoSaleIgual() {
        // El fallo que motivó todo esto. Cuatro de cada ciento dieciséis currículums son un
        // PDF escaneado y no dan texto: el lector se agota en reintentos y antes la fila se
        // cortaba ahí. El candidato se quedaba en PERFIL_CALIFICANDO para siempre, con su
        // examen de cincuenta preguntas ya calificado, y el evaluador no necesitaba el
        // currículum para nada. Ahora el retrato sale igual, con lo que sí se pudo leer.
        TrabajoIa suyo = trabajo(1L, AgenteDatosCv.CODIGO_AGENTE, "EN_CURSO", "FINA");
        when(registro.tomar(1L)).thenReturn(Optional.of(suyo));
        doThrowEn(datosCv);
        when(registro.fallar(eq(1L), anyInt(), anyString())).thenReturn(false);
        when(registro.crearElRetratoSiLosDemasAcabaron(eq(1L), eq(POSTULACION), anyList(),
                eq(AgentePotencialRiesgo.CODIGO_AGENTE), eq("FINA"), eq(1L)))
                .thenReturn(Optional.of(
                        trabajo(9L, AgentePotencialRiesgo.CODIGO_AGENTE, "PENDIENTE", "FINA")));

        cola.ejecutar(1L);

        verify(publicador).publicar(9L);
        // Y el que se agotó no vuelve a la cola: ya no queda intento que gastar.
        verify(publicador, never()).publicar(1L);
    }

    @Test
    void mientrasQuedeUnIntentoElFalloNoPideElRetrato() {
        // El otro lado de la regla. Todavía puede dar algo en el siguiente intento, y armar
        // el retrato ahora lo haría sin un currículum que dentro de un minuto sí se va a
        // poder leer.
        when(registro.tomar(1L))
                .thenReturn(Optional.of(trabajo(1L, AgenteDatosCv.CODIGO_AGENTE, "EN_CURSO", "FINA")));
        doThrowEn(datosCv);
        when(registro.fallar(eq(1L), anyInt(), anyString())).thenReturn(true);

        cola.ejecutar(1L);

        verify(publicador).publicar(1L);
        verify(registro, never()).crearElRetratoSiLosDemasAcabaron(
                anyLong(), anyLong(), anyList(), anyString(), anyString(), any());
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
                trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO_AGENTE, "PENDIENTE", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("EN_CURSO");
    }

    @Test
    void quienFalloYLuegoSalioBienAlReintentarNoQuedaMarcadoComoFallido() {
        // El fallo que se arregló. La fila fallida no se borra nunca, así que preguntar
        // primero por el fallo dejaba marcado como fallido a un candidato que sí tiene su
        // retrato hecho: aparecía en rojo en el ranking con todas sus notas puestas.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "FALLIDO", "FINA"),
                trabajo(2L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(3L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    @Test
    void siElRetratoNoLlegoAHacerseLaCalificacionSiEsFallida() {
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO_AGENTE, "FALLIDO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("FALLIDA");
    }

    @Test
    void unaCribaTieneMenosTrabajosYAunAsiEstaTerminada() {
        // Por eso el final de la fila se mira por el agente que cierra la etapa y no
        // contando trabajos: contar diría «en curso» para siempre en cuanto el evaluador se
        // salta.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    @Test
    void conUnPasoFallidoPeroConRetratoHechoLaCalificacionEstaTerminada() {
        // El estado nuevo que trae correr a la vez: el lector del currículum se agotó, los
        // otros dos salieron bien y el retrato se armó con menos evidencia. Eso es una
        // calificación terminada —el candidato tiene su nota y su grupo— y no una fallida,
        // que lo dejaría en rojo en el ranking teniéndolo todo.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteDatosCv.CODIGO_AGENTE, "FALLIDO", "FINA"),
                trabajo(2L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(3L, AgenteEvaluador.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(4L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    @Test
    void unaFinaFallidaSobreUnaRapidaTerminadaEsFallida() {
        // El fallo que se arregló. Mirando las dos pasadas juntas se encontraba el agente
        // que cierra la etapa TERMINADO en la rápida y se contestaba «terminada»: el
        // contador de fallidos marcaba cero y unas notas provisionales del modelo que no
        // razona se presentaban como definitivas.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "RAPIDA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "RAPIDA"),
                trabajo(3L, AgenteEvidenciaCv.CODIGO_AGENTE, "FALLIDO", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("FALLIDA");
        // Y la nota que hay en pantalla sigue siendo la de la rápida: provisional, pero real.
        assertThat(cola.pasadaDe(POSTULACION)).isEqualTo("RAPIDA");
    }

    // ============ Con qué pasada está calificado ============

    @Test
    void sinRetratoTerminadoTodaviaNoHayPasada() {
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "RAPIDA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO_AGENTE, "EN_CURSO", "RAPIDA")));

        assertThat(cola.pasadaDe(POSTULACION)).isNull();
    }

    @Test
    void conSoloLaPrimeraPasadaLaNotaEsProvisional() {
        // La pantalla lo necesita para no enseñar como definitivo un número que salió del
        // modelo que no razona: se ven igual y no valen lo mismo.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "RAPIDA")));

        assertThat(cola.pasadaDe(POSTULACION)).isEqualTo("RAPIDA");
    }

    @Test
    void laFinaMandaAunqueLaRapidaSeaPosterior() {
        // La fina es la que pisa las notas: da igual el orden en que quedaron las filas.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "RAPIDA")));

        assertThat(cola.pasadaDe(POSTULACION)).isEqualTo("FINA");
    }

    // ============ Lo que se queda atascado ============

    @Test
    void losPendientesViejosSeVuelvenAPublicar() {
        // Se pierde el mensaje, o RabbitMQ estaba caído al publicarlo. El trabajo es
        // idempotente, así que volver a empujarlo es barato.
        when(trabajos.findByEstadoAndCreadoEnBefore(eq("PENDIENTE"), any(Instant.class)))
                .thenReturn(List.of(trabajo(8L, AgenteEvidenciaCv.CODIGO_AGENTE, "PENDIENTE", "FINA")));

        cola.reintentarAtascados();

        verify(publicador).publicar(8L);
    }

    @Test
    void losColgadosSeDevuelvenAPendienteAntesDeVolverAPublicarlos() {
        // Alguien lo tomó y el proceso murió a mitad. Publicarlo sin devolverlo a pendiente
        // no serviría: quien lo recogiera se lo encontraría en EN_CURSO y lo soltaría.
        when(trabajos.findByEstadoAndTomadoEnBefore(eq("EN_CURSO"), any(Instant.class)))
                .thenReturn(List.of(trabajo(9L, AgenteEvidenciaCv.CODIGO_AGENTE, "EN_CURSO", "FINA")));

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

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgentePruebaPuesto.CODIGO_AGENTE, "FINA", null);
        verify(registro, never()).crearSiHaceFalta(
                anyLong(), anyLong(), eq(AgenteEvidenciaCv.CODIGO_AGENTE), anyString(), any());
    }

    @Test
    void lasPreguntasDeLaConversacionSeEncolanSolas() {
        cola.encolarPreguntasSimulacion(POSTULACION);

        verify(registro).crearSiHaceFalta(1L, POSTULACION, AgenteSimulacion.CODIGO_AGENTE, "FINA", null);
    }

    @Test
    void alTerminarLaPruebaNoSeEncolaANadieDetras() {
        // Su fila tiene un solo paso. Si esto encolara al siguiente de la fila del retrato,
        // calificar una prueba pagaría de propina un Perfil de Talento que nadie pidió.
        when(registro.tomar(9L))
                .thenReturn(Optional.of(trabajo(9L, AgentePruebaPuesto.CODIGO_AGENTE, "EN_CURSO", "FINA")));

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
                trabajo(1L, AgenteEvidenciaCv.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(2L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(3L, AgenteSimulacion.CODIGO_AGENTE, "PENDIENTE", "FINA")));

        assertThat(cola.comoVa(POSTULACION)).isEqualTo("TERMINADA");
    }

    @Test
    void unaPruebaFallidaNoDejaAlCandidatoComoSiNoTuvieraRetrato() {
        // Al revés que el anterior, y por el mismo motivo: que la prueba del puesto falle no
        // borra el Perfil de Talento que ya estaba hecho.
        when(trabajos.findByPostulacionIdOrderByIdAsc(POSTULACION)).thenReturn(List.of(
                trabajo(1L, AgentePotencialRiesgo.CODIGO_AGENTE, "TERMINADO", "FINA"),
                trabajo(2L, AgentePruebaPuesto.CODIGO_AGENTE, "FALLIDO", "FINA")));

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
