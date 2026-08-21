package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los cambios de estado de la cola, que son los que impiden calificar a alguien dos veces.
 *
 * <p>Lo importante está en {@code tomar}: la condición y la escritura viajan juntas en un
 * solo UPDATE. Leer y luego escribir no vale con ocho consumidores —dos pueden leer
 * «PENDIENTE» antes de que ninguno haya escrito— y el resultado sería pagar dos llamadas al
 * modelo por el mismo candidato y guardar la nota del que termine último.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El registro y los reintentos de los trabajos de IA")
class RegistroTrabajosIaTest {

    @Mock
    private TrabajoIaRepository trabajos;

    @InjectMocks
    private RegistroTrabajosIa registro;

    @Test
    void loTomaSoloSiLaBaseDiceQueLoTomoEste() {
        TrabajoIa suyo = trabajo(9L, "EN_CURSO", 1);
        when(trabajos.tomarSiEstaPendiente(eq(9L), any(Instant.class))).thenReturn(1);
        when(trabajos.findById(9L)).thenReturn(Optional.of(suyo));

        assertThat(registro.tomar(9L)).contains(suyo);
    }

    @Test
    void siOtroSeAdelantoNiSiquieraSeLeeElTrabajo() {
        // Cero filas cambiadas significa que otro consumidor ya lo tiene, o que ya terminó.
        // Es lo que hace que un mensaje entregado dos veces no califique dos veces.
        when(trabajos.tomarSiEstaPendiente(eq(9L), any(Instant.class))).thenReturn(0);

        assertThat(registro.tomar(9L)).isEmpty();
        verify(trabajos, never()).findById(anyLong());
    }

    @Test
    void terminarDejaLaHoraParaPoderVerCuantoTardo() {
        TrabajoIa suyo = trabajo(9L, "EN_CURSO", 1);
        when(trabajos.findById(9L)).thenReturn(Optional.of(suyo));

        registro.terminar(9L);

        assertThat(suyo.getEstado()).isEqualTo("TERMINADO");
        assertThat(suyo.getTerminadoEn()).isNotNull();
        verify(trabajos).save(suyo);
    }

    @Test
    void mientrasQuedeIntentoElTrabajoVuelveAPendiente() {
        TrabajoIa suyo = trabajo(9L, "EN_CURSO", 1);
        when(trabajos.findById(9L)).thenReturn(Optional.of(suyo));

        assertThat(registro.fallar(9L, 3, "el proveedor no responde")).isTrue();

        assertThat(suyo.getEstado()).isEqualTo("PENDIENTE");
        // Sin borrar la hora en que lo tomaron, el vigilante de atascados lo vería como un
        // EN_CURSO colgado y lo devolvería otra vez: el mismo trabajo contado dos veces.
        assertThat(suyo.getTomadoEn()).isNull();
        assertThat(suyo.getTerminadoEn()).isNull();
    }

    @Test
    void agotadosLosIntentosSeMarcaFallidoYNoSeInventaNingunaNota() {
        TrabajoIa suyo = trabajo(9L, "EN_CURSO", 3);
        when(trabajos.findById(9L)).thenReturn(Optional.of(suyo));

        assertThat(registro.fallar(9L, 3, "el proveedor no responde")).isFalse();

        assertThat(suyo.getEstado()).isEqualTo("FALLIDO");
        assertThat(suyo.getTerminadoEn()).isNotNull();
    }

    @Test
    void unTrabajoQueYaNoExisteNoSeReintenta() {
        // Pasaría si alguien borró la fila a mano mientras corría. Devolver «reintenta»
        // dejaría al llamador publicando un aviso por un id que no lleva a ninguna parte.
        when(trabajos.findById(9L)).thenReturn(Optional.empty());

        assertThat(registro.fallar(9L, 3, "da igual")).isFalse();
    }

    @Test
    void unTrabajoColgadoVuelveAPendienteYUnoQueYaTerminoNoSeToca() {
        TrabajoIa colgado = trabajo(9L, "EN_CURSO", 1);
        when(trabajos.findById(9L)).thenReturn(Optional.of(colgado));
        registro.devolverAPendiente(9L);
        assertThat(colgado.getEstado()).isEqualTo("PENDIENTE");
        assertThat(colgado.getTomadoEn()).isNull();

        // El vigilante mira por hora, no por estado actual: si entre que lee y que escribe
        // el trabajo acabó, devolverlo a la cola lo haría correr —y pagarse— dos veces.
        TrabajoIa yaHecho = trabajo(10L, "TERMINADO", 1);
        when(trabajos.findById(10L)).thenReturn(Optional.of(yaHecho));
        registro.devolverAPendiente(10L);
        assertThat(yaHecho.getEstado()).isEqualTo("TERMINADO");
        verify(trabajos, never()).save(yaHecho);
    }

    @Test
    void laBusquedaDeDuplicadosIncluyeElModo() {
        // Sin el modo, la pasada fina encontraría el trabajo que ya hizo la rápida y no
        // correría nunca: justo lo contrario de lo que se pide al pulsar el botón.
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "EVIDENCIA_CV", "FINA")).thenReturn(Optional.empty());
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(registro.crearSiHaceFalta(1L, 55L, "EVIDENCIA_CV", "FINA", null)).isPresent();

        ArgumentCaptor<TrabajoIa> creado = ArgumentCaptor.forClass(TrabajoIa.class);
        verify(trabajos).save(creado.capture());
        assertThat(creado.getValue().getEstado()).isEqualTo("PENDIENTE");
        assertThat(creado.getValue().getModo()).isEqualTo("FINA");
        assertThat(creado.getValue().getIntentos()).isZero();
    }

    @Test
    void loQueYaEstaHechoOEnMarchaNoSeVuelveACrear() {
        // Es lo que hace que pedir la criba dos veces no duplique nada.
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "EVIDENCIA_CV", "FINA"))
                .thenReturn(Optional.of(trabajo(9L, "TERMINADO", 1)));

        assertThat(registro.crearSiHaceFalta(1L, 55L, "EVIDENCIA_CV", "FINA", null)).isEmpty();
        verify(trabajos, never()).save(any(TrabajoIa.class));
    }

    @Test
    void loQueSeHizoAntesDeLoQueLoAlimentaSiSeRehace() {
        // El retrato de un candidato cribado se armó sin sus respuestas. Cuando el evaluador
        // termina, ese retrato queda viejo: sin esto se quedaba en pie uno que no vio la
        // mitad de lo que hay, y el candidato se presentaba con una nota incompleta.
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "POTENCIAL_RIESGO", "FINA"))
                .thenReturn(Optional.of(trabajo(5L, "TERMINADO", 1)));
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(i -> i.getArgument(0));

        // Alimentado por el trabajo 6, que es posterior al 5 que ya estaba hecho.
        assertThat(registro.crearSiHaceFalta(1L, 55L, "POTENCIAL_RIESGO", "FINA", 6L)).isPresent();
    }

    @Test
    void loQueSeHizoDespuesDeLoQueLoAlimentaSeQuedaComoEsta() {
        // El otro lado de la regla, y el que evita pagar de más: si el retrato ya se armó
        // después del evaluador, ya lo tuvo en cuenta y rehacerlo no cambiaría nada.
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "POTENCIAL_RIESGO", "FINA"))
                .thenReturn(Optional.of(trabajo(7L, "TERMINADO", 1)));

        assertThat(registro.crearSiHaceFalta(1L, 55L, "POTENCIAL_RIESGO", "FINA", 6L)).isEmpty();
        verify(trabajos, never()).save(any(TrabajoIa.class));
    }

    @Test
    void loQueFalloSiSePuedeVolverAIntentar() {
        // Es lo que permite reencolar a mano una postulación que se quedó colgada porque el
        // proveedor del modelo estuvo caído.
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "EVIDENCIA_CV", "FINA"))
                .thenReturn(Optional.of(trabajo(9L, "FALLIDO", 3)));
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(registro.crearSiHaceFalta(1L, 55L, "EVIDENCIA_CV", "FINA", null)).isPresent();
    }

    // ============ La barrera: quién dispara el retrato, y una sola vez ============

    @Test
    void mientrasQuedeAlguienVivoElRetratoNoSeCrea() {
        // Los tres primeros corren a la vez, así que los tres preguntan al terminar. El que
        // pregunta cuando su compañero sigue hablando con el proveedor tiene que irse con
        // las manos vacías: un retrato armado a medias no vale nada y cuesta lo mismo.
        when(trabajos.bloquearLosQueVanALaVez(55L, "FINA", A_LA_VEZ)).thenReturn(List.of(
                trabajo(1L, "DATOS_CV", "TERMINADO"),
                trabajo(2L, "EVIDENCIA_CV", "EN_CURSO"),
                trabajo(3L, "EVALUADOR", "TERMINADO")));

        assertThat(registro.crearElRetratoSiLosDemasAcabaron(
                1L, 55L, A_LA_VEZ, "POTENCIAL_RIESGO", "FINA", 3L)).isEmpty();
        verify(trabajos, never()).save(any(TrabajoIa.class));
    }

    @Test
    void cuandoLosTresAcabanElRetratoSeCrea() {
        when(trabajos.bloquearLosQueVanALaVez(55L, "FINA", A_LA_VEZ)).thenReturn(List.of(
                trabajo(1L, "DATOS_CV", "TERMINADO"),
                trabajo(2L, "EVIDENCIA_CV", "TERMINADO"),
                trabajo(3L, "EVALUADOR", "TERMINADO")));
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "POTENCIAL_RIESGO", "FINA")).thenReturn(Optional.empty());
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(registro.crearElRetratoSiLosDemasAcabaron(
                1L, 55L, A_LA_VEZ, "POTENCIAL_RIESGO", "FINA", 3L)).isPresent();

        ArgumentCaptor<TrabajoIa> creado = ArgumentCaptor.forClass(TrabajoIa.class);
        verify(trabajos).save(creado.capture());
        assertThat(creado.getValue().getAgenteCodigo()).isEqualTo("POTENCIAL_RIESGO");
        assertThat(creado.getValue().getEstado()).isEqualTo("PENDIENTE");
    }

    @Test
    void elSegundoEnLlegarNoCreaUnRetratoQueElPrimeroYaCreo() {
        // Aquí está el doble disparo, y por qué no ocurre. Dos terminan a la vez y los dos
        // llegan hasta aquí: el bloqueo de la primera línea hace que entren de uno en uno, y
        // el segundo, al mirar, ya ve el trabajo que creó el primero. Sin ese orden los dos
        // leerían «no hay retrato» y se pagarían dos Perfiles de Talento por el mismo
        // candidato.
        when(trabajos.bloquearLosQueVanALaVez(55L, "FINA", A_LA_VEZ)).thenReturn(List.of(
                trabajo(1L, "DATOS_CV", "TERMINADO"),
                trabajo(2L, "EVIDENCIA_CV", "TERMINADO"),
                trabajo(3L, "EVALUADOR", "TERMINADO")));
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "POTENCIAL_RIESGO", "FINA"))
                .thenReturn(Optional.of(trabajo(4L, "POTENCIAL_RIESGO", "PENDIENTE")));

        assertThat(registro.crearElRetratoSiLosDemasAcabaron(
                1L, 55L, A_LA_VEZ, "POTENCIAL_RIESGO", "FINA", 3L)).isEmpty();
        verify(trabajos, never()).save(any(TrabajoIa.class));
    }

    @Test
    void unPasoAgotadoNoParaAlRetrato() {
        // El arreglo que se nota. Un currículum escaneado no da texto y su lector se agota
        // en reintentos; el examen de cincuenta preguntas del candidato ya está calificado y
        // no necesitaba el currículum para nada. El retrato sale con lo que hay.
        when(trabajos.bloquearLosQueVanALaVez(55L, "FINA", A_LA_VEZ)).thenReturn(List.of(
                trabajo(1L, "DATOS_CV", "FALLIDO"),
                trabajo(2L, "EVIDENCIA_CV", "FALLIDO"),
                trabajo(3L, "EVALUADOR", "TERMINADO")));
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "POTENCIAL_RIESGO", "FINA")).thenReturn(Optional.empty());
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(registro.crearElRetratoSiLosDemasAcabaron(
                1L, 55L, A_LA_VEZ, "POTENCIAL_RIESGO", "FINA", 3L)).isPresent();
    }

    @Test
    void elQueFalloYLuegoSalioBienNoCuentaComoFallido() {
        // De cada agente solo vale su último intento. La fila fallida no se borra nunca, así
        // que mirarlas todas contaría un fallo que ya no existe y, con los tres «fallidos»,
        // se negaría a armar un retrato que sí tiene con qué armarse.
        when(trabajos.bloquearLosQueVanALaVez(55L, "FINA", A_LA_VEZ)).thenReturn(List.of(
                trabajo(1L, "DATOS_CV", "FALLIDO"),
                trabajo(2L, "EVIDENCIA_CV", "FALLIDO"),
                trabajo(5L, "DATOS_CV", "TERMINADO"),
                trabajo(6L, "EVIDENCIA_CV", "TERMINADO")));
        when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                55L, "POTENCIAL_RIESGO", "FINA")).thenReturn(Optional.empty());
        when(trabajos.save(any(TrabajoIa.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(registro.crearElRetratoSiLosDemasAcabaron(
                1L, 55L, A_LA_VEZ, "POTENCIAL_RIESGO", "FINA", 6L)).isPresent();
    }

    @Test
    void conTodoFallidoNoSeArmaUnRetratoSobreLaNada() {
        // El límite de «un fallo no para la fila». Si no salió bien ni un solo paso no hay
        // absolutamente nada que resumir, y pedirle al modelo un retrato de la nada es
        // inventarse una nota. La tanda se queda fallida, se ve fallida, y alguien la
        // vuelve a pedir.
        when(trabajos.bloquearLosQueVanALaVez(55L, "FINA", A_LA_VEZ)).thenReturn(List.of(
                trabajo(1L, "DATOS_CV", "FALLIDO"),
                trabajo(2L, "EVIDENCIA_CV", "FALLIDO"),
                trabajo(3L, "EVALUADOR", "FALLIDO")));

        assertThat(registro.crearElRetratoSiLosDemasAcabaron(
                1L, 55L, A_LA_VEZ, "POTENCIAL_RIESGO", "FINA", 3L)).isEmpty();
        verify(trabajos, never()).save(any(TrabajoIa.class));
    }

    private static final List<String> A_LA_VEZ =
            List.of("DATOS_CV", "EVIDENCIA_CV", "EVALUADOR");

    private TrabajoIa trabajo(Long id, String agenteCodigo, String estado) {
        TrabajoIa suyo = trabajo(id, estado, 1);
        suyo.setAgenteCodigo(agenteCodigo);
        return suyo;
    }

    private TrabajoIa trabajo(Long id, String estado, int intentos) {
        return TrabajoIa.builder()
                .id(id)
                .postulacionId(55L)
                .agenteCodigo("EVIDENCIA_CV")
                .modo("FINA")
                .estado(estado)
                .intentos(intentos)
                .tomadoEn(Instant.now())
                .build();
    }
}
