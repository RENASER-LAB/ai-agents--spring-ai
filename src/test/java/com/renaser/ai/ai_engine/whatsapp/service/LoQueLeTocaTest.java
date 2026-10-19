package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Accion;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que le toca a quien postula y sus plazos reales (V76): AC-07, AC-08 y AC-22.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La acción de la consulta v2 y sus plazos")
class LoQueLeTocaTest {

    private static final String MIS_PROCESOS = "https://portal.renaser.pe/procesos";

    @Mock private IntentoPruebaRepository intentos;
    @Mock private EvaluacionRepository evaluaciones;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private VersionPlantillaPruebaRepository versionesPlantilla;

    private LoQueLeToca acciones;
    private final Instant ahora = Instant.now();
    private final Instant cierre = ahora.plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.MILLIS);

    @BeforeEach
    void montar() {
        acciones = new LoQueLeToca(intentos, evaluaciones, versionesBanco, versionesPlantilla);
        lenient().when(versionesBanco.findById(40L)).thenReturn(Optional.of(VersionBanco.builder().id(40L)
                .modalidad("CRONOMETRADA").duracionMinutos(90).build()));
    }

    @AfterEach
    void soloLee() {
        // Consultar no inicia intentos ni toca relojes: nada se guarda nunca.
        verify(intentos, never()).save(any());
        verify(evaluaciones, never()).save(any());
    }

    private static Postulacion en(String estado) {
        return Postulacion.builder().id(70L).vacanteId(5L).estadoCodigo(estado).evaluacionId(80L)
                .evaluacionTecnicaId(81L).build();
    }

    private Vacante propia() {
        return Vacante.builder().id(5L).instrumentoEtapaTecnica("PRUEBA_PROPIA").pruebaCierraEn(cierre).build();
    }

    private IntentoPrueba intento() {
        return IntentoPrueba.builder().id(9L).postulacionId(70L).versionBancoId(40L).venceEn(cierre).build();
    }

    @Test
    @DisplayName("AC-22 · prueba cronometrada sin empezar: HABILITADA, empezar antes del cierre, 90 minutos y el enlace fijo")
    void cronometradaSinEmpezar() {
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(intento()));

        Accion accion = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora, MIS_PROCESOS);

        assertThat(accion.tipo()).isEqualTo("PRUEBA_PUESTO");
        assertThat(accion.estado()).isEqualTo("HABILITADA");
        assertThat(accion.modalidad()).isEqualTo("CRONOMETRADA");
        assertThat(accion.duracionMinutos()).isEqualTo(90);
        assertThat(accion.plazoDias()).isNull();
        assertThat(accion.empezarAntesDe()).isEqualTo(cierre);
        assertThat(accion.iniciadaEn()).isNull();
        assertThat(accion.entregarAntesDe()).isNull();
        assertThat(accion.enlace()).isEqualTo(MIS_PROCESOS);
        assertThat(accion.zonaHoraria()).isEqualTo("America/Lima");
        assertThat(accion.instrucciones())
                .isEqualTo(TextosDelMomento.de("PRUEBA_TURNO_CANDIDATO", "PRUEBA_PROPIA", false).texto());
        assertThat(accion.ingreso()).contains("correo y contraseña").contains("«Mis procesos»");
    }

    @Test
    @DisplayName("AC-22 · ya empezada: EN_CURSO, con cuándo empezó y hasta cuándo entregar, sin perder el cierre")
    void cronometradaEmpezada() {
        IntentoPrueba empezado = intento();
        Instant inicio = ahora.minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MILLIS);
        empezado.setIniciadoEn(inicio);
        empezado.setVenceEn(inicio.plus(Duration.ofMinutes(90)));
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(empezado));

        Accion accion = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora, MIS_PROCESOS);

        assertThat(accion.estado()).isEqualTo("EN_CURSO");
        assertThat(accion.iniciadaEn()).isEqualTo(inicio);
        assertThat(accion.entregarAntesDe()).isEqualTo(inicio.plus(Duration.ofMinutes(90)));
        assertThat(accion.empezarAntesDe()).isEqualTo(cierre);
        assertThat(accion.duracionMinutos()).isEqualTo(90);
        assertThat(accion.enlace()).isEqualTo(MIS_PROCESOS);
        assertThat(accion.instrucciones()).isEqualTo(LoQueLeToca.YA_EMPEZADA);
    }

    @Test
    @DisplayName("AC-08 · entregada, la acción queda COMPLETADA y sin enlace: ya no se invita a hacerla")
    void entregada() {
        IntentoPrueba entregado = intento();
        entregado.setIniciadoEn(ahora.minus(Duration.ofHours(1)));
        entregado.setEntregadoEn(ahora.minus(Duration.ofMinutes(5)));
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(entregado));

        Accion accion = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora, MIS_PROCESOS);

        assertThat(accion.estado()).isEqualTo("COMPLETADA");
        assertThat(accion.enlace()).isNull();
        assertThat(accion.instrucciones()).isEqualTo(LoQueLeToca.YA_ENTREGADA);
    }

    @Test
    @DisplayName("AC-08 · vencida con huecos, VENCIDA con las palabras del portal; vencida sin empezar, también")
    void vencida() {
        IntentoPrueba sinCompletar = intento();
        sinCompletar.setIniciadoEn(ahora.minus(Duration.ofHours(2)));
        sinCompletar.setVenceEn(ahora.minus(Duration.ofMinutes(30)));
        sinCompletar.setEntregadoEn(ahora.minus(Duration.ofMinutes(30)));
        sinCompletar.setNoCompletada(true);
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(sinCompletar));

        Accion accion = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora, MIS_PROCESOS);
        assertThat(accion.estado()).isEqualTo("VENCIDA");
        assertThat(accion.enlace()).isNull();
        assertThat(accion.instrucciones()).isEqualTo(TextosDelMomento.PRUEBA_SIN_COMPLETAR.texto());
        // En milisegundos, como todas las fechas de la consulta.
        assertThat(accion.entregarAntesDe()).isEqualTo(sinCompletar.getVenceEn().truncatedTo(ChronoUnit.MILLIS));

        IntentoPrueba sinEmpezar = intento();
        sinEmpezar.setVenceEn(ahora.minus(Duration.ofMinutes(1)));
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(sinEmpezar));
        Accion caducada = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora, MIS_PROCESOS);
        assertThat(caducada.estado()).isEqualTo("VENCIDA");
        assertThat(caducada.empezarAntesDe()).isEqualTo(sinEmpezar.getVenceEn().truncatedTo(ChronoUnit.MILLIS));
        assertThat(caducada.enlace()).isNull();
        assertThat(caducada.instrucciones()).isEqualTo(LoQueLeToca.PLAZO_TERMINADO);
    }

    @Test
    @DisplayName("AC-22 · sin fecha de cierre ni intento, los plazos van nulos: no se estima nada")
    void sinDatos() {
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.empty());
        Vacante sinCierre = Vacante.builder().id(5L).instrumentoEtapaTecnica("PRUEBA_PROPIA").build();
        when(versionesBanco.pruebaPropiaDe(5L, "PUBLICADA")).thenReturn(Optional.empty());

        Accion accion = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), sinCierre, ahora, MIS_PROCESOS);

        assertThat(accion.estado()).isEqualTo("HABILITADA");
        assertThat(accion.empezarAntesDe()).isNull();
        assertThat(accion.iniciadaEn()).isNull();
        assertThat(accion.entregarAntesDe()).isNull();
        assertThat(accion.modalidad()).isNull();
        assertThat(accion.duracionMinutos()).isNull();
        assertThat(accion.plazoDias()).isNull();
        assertThat(accion.enlace()).isEqualTo(MIS_PROCESOS);
    }

    @Test
    @DisplayName("La plantilla con minutos de la vacante se mide con ellos; de plazo abierto, en días")
    void plantilla() {
        Vacante conMinutos = Vacante.builder().id(5L).instrumentoEtapaTecnica("PLANTILLA")
                .versionPlantillaPruebaId(30L).minutosEtapaTecnica(45).build();
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.empty());
        lenient().when(versionesPlantilla.findById(30L)).thenReturn(Optional.of(VersionPlantillaPrueba.builder()
                .id(30L).modalidad("PLAZO_ABIERTO").plazoDias(3).build()));

        Accion cronometrada = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), conMinutos, ahora, MIS_PROCESOS);
        assertThat(cronometrada.modalidad()).isEqualTo("CRONOMETRADA");
        assertThat(cronometrada.duracionMinutos()).isEqualTo(45);

        Vacante sinMinutos = Vacante.builder().id(5L).instrumentoEtapaTecnica("PLANTILLA")
                .versionPlantillaPruebaId(30L).build();
        Accion abierta = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), sinMinutos, ahora, MIS_PROCESOS);
        assertThat(abierta.modalidad()).isEqualTo("PLAZO_ABIERTO");
        assertThat(abierta.plazoDias()).isEqualTo(3);
        assertThat(abierta.duracionMinutos()).isNull();
        // Sin cronómetro no se habla de cronómetro.
        assertThat(abierta.instrucciones()).isEqualTo(LoQueLeToca.PLAZO_ABIERTO_HABILITADA)
                .doesNotContain("cronómetro");
    }

    @Test
    @DisplayName("AC-08 · el cuestionario técnico es su propio tipo, con sus minutos y el vencimiento de su evaluación")
    void cuestionario() {
        Vacante vacante = Vacante.builder().id(5L).instrumentoEtapaTecnica("CUESTIONARIO_TECNICO").build();
        when(evaluaciones.findById(81L)).thenReturn(Optional.of(Evaluacion.builder().id(81L)
                .versionBancoNivelId(41L).estado("PENDIENTE").venceEn(cierre).build()));
        when(versionesBanco.findById(41L)).thenReturn(Optional.of(VersionBanco.builder().id(41L)
                .minutosObjetivo(60).build()));

        Accion accion = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), vacante, ahora, MIS_PROCESOS);

        assertThat(accion.tipo()).isEqualTo("CUESTIONARIO_TECNICO");
        assertThat(accion.estado()).isEqualTo("HABILITADA");
        assertThat(accion.modalidad()).isEqualTo("CRONOMETRADA");
        assertThat(accion.duracionMinutos()).isEqualTo(60);
        assertThat(accion.empezarAntesDe()).isEqualTo(cierre);
        assertThat(accion.instrucciones()).isEqualTo(TextosDelMomento.CUESTIONARIO_LISTO.texto());
    }

    @Test
    @DisplayName("AC-08 · la evaluación inicial se entrega antes de su vencimiento; empezada, sigue EN_CURSO")
    void evaluacionInicial() {
        Instant inicio = ahora.minus(Duration.ofMinutes(3)).truncatedTo(ChronoUnit.MILLIS);
        when(evaluaciones.findById(80L)).thenReturn(Optional.of(Evaluacion.builder().id(80L)
                .estado("PENDIENTE").venceEn(cierre).build()));

        Accion sinEmpezar = acciones.de(en("PERFIL_TURNO_CANDIDATO"), propia(), ahora, MIS_PROCESOS);
        assertThat(sinEmpezar.tipo()).isEqualTo("EVALUACION_INICIAL");
        assertThat(sinEmpezar.estado()).isEqualTo("HABILITADA");
        assertThat(sinEmpezar.empezarAntesDe()).isEqualTo(cierre);
        assertThat(sinEmpezar.entregarAntesDe()).isEqualTo(cierre);
        assertThat(sinEmpezar.duracionMinutos()).isNull();

        when(evaluaciones.findById(80L)).thenReturn(Optional.of(Evaluacion.builder().id(80L)
                .estado("EN_CURSO").iniciadaEn(inicio).venceEn(cierre).build()));
        Accion empezada = acciones.de(en("PERFIL_TURNO_CANDIDATO"), propia(), ahora, MIS_PROCESOS);
        assertThat(empezada.estado()).isEqualTo("EN_CURSO");
        assertThat(empezada.iniciadaEn()).isEqualTo(inicio);
        assertThat(empezada.entregarAntesDe()).isEqualTo(cierre);
        assertThat(empezada.instrucciones()).contains("Puedes salir y volver");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POSTULADA", "PERFIL_CALIFICANDO", "PERFIL_POR_CONFIRMAR", "PRUEBA_CALIFICANDO",
            "PRUEBA_POR_CONFIRMAR", "SIMULACION_POR_HABILITAR", "SIMULACION_POR_CONFIRMAR",
            "VALIDACION_POR_HABILITAR", "DECISION_POR_CONFIRMAR", "CONTRATADO", "NO_CONTINUA", "CERRADA"})
    @DisplayName("AC-07 · en revisión, calificando, esperando una fecha o terminada no hay tarea pendiente")
    void sinTarea(String estado) {
        assertThat(acciones.de(en(estado), propia(), ahora, MIS_PROCESOS)).isNull();
    }

    @Test
    @DisplayName("AC-07 · la simulación por elegir no trae fecha: no es una cita asignada")
    void simulacionSinCita() {
        Accion accion = acciones.de(en("SIMULACION_TURNO_CANDIDATO"), propia(), ahora, MIS_PROCESOS);

        assertThat(accion.tipo()).isEqualTo("SIMULACION");
        assertThat(accion.estado()).isEqualTo("HABILITADA");
        assertThat(accion.empezarAntesDe()).isNull();
        assertThat(accion.iniciadaEn()).isNull();
        assertThat(accion.entregarAntesDe()).isNull();
        assertThat(accion.instrucciones())
                .isEqualTo(TextosDelMomento.de("SIMULACION_TURNO_CANDIDATO", null, false).texto());
    }

    @Test
    @DisplayName("Nada antes del reloj: el caso de la prueba nunca viaja en la acción")
    void nadaAntesDelReloj() {
        when(versionesBanco.findById(40L)).thenReturn(Optional.of(VersionBanco.builder().id(40L)
                .modalidad("CRONOMETRADA").duracionMinutos(90).enunciado("EL CASO SECRETO")
                .materiales("LOS MATERIALES").build()));
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(intento()));

        Accion accion = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora, MIS_PROCESOS);

        assertThat(accion.toString()).doesNotContain("EL CASO SECRETO").doesNotContain("LOS MATERIALES");
    }

    @Test
    @DisplayName("El plazo para empezar del aviso: el del intento sin empezar; nulo si ya empezó")
    void empezarAntesDelAviso() {
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(intento()));
        assertThat(acciones.empezarAntesDe(en("PRUEBA_TURNO_CANDIDATO"), propia())).isEqualTo(cierre);

        IntentoPrueba empezado = intento();
        empezado.setIniciadoEn(ahora);
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(empezado));
        assertThat(acciones.empezarAntesDe(en("PRUEBA_TURNO_CANDIDATO"), propia())).isNull();
    }

    // ============ QA-C0-F1 · cuándo cambió lo que la acción cuenta ============

    @Test
    @DisplayName("QA-C0-F1 · ampliar el plazo de un intento sin empezar cuenta como cambio, con la fecha que puso la base")
    void plazoAmpliado() {
        IntentoPrueba ampliado = intento();
        ampliado.setCreadoEn(ahora.minus(Duration.ofDays(3)));
        Instant ampliacion = ahora.minus(Duration.ofMinutes(4));
        ampliado.setContextoActualizadoEn(ampliacion);
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(ampliado));

        assertThat(acciones.ultimoCambio(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora)).isEqualTo(ampliacion);
    }

    @Test
    @DisplayName("QA-C0-F1 · el cambio de estado o de vencimiento de la evaluación también cuenta")
    void evaluacionCambiada() {
        Instant creada = ahora.minus(Duration.ofDays(2));
        Instant cambio = ahora.minus(Duration.ofMinutes(7));
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.empty());
        when(evaluaciones.findById(80L)).thenReturn(Optional.of(Evaluacion.builder().id(80L)
                .estado("PENDIENTE").venceEn(cierre).creadoEn(creada).contextoActualizadoEn(cambio).build()));

        assertThat(acciones.ultimoCambio(en("PERFIL_TURNO_CANDIDATO"), propia(), ahora)).isEqualTo(cambio);

        // Sin la fecha nueva (filas anteriores a la V77), al menos cuándo nació.
        when(evaluaciones.findById(80L)).thenReturn(Optional.of(Evaluacion.builder().id(80L)
                .estado("PENDIENTE").venceEn(cierre).creadoEn(creada).build()));
        assertThat(acciones.ultimoCambio(en("PERFIL_TURNO_CANDIDATO"), propia(), ahora)).isEqualTo(creada);

        // Marcada VENCIDA con el límite aún por venir: cuenta cuándo la marcaron, no un límite futuro.
        when(evaluaciones.findById(80L)).thenReturn(Optional.of(Evaluacion.builder().id(80L)
                .estado("VENCIDA").venceEn(cierre).creadoEn(creada).contextoActualizadoEn(cambio).build()));
        assertThat(acciones.ultimoCambio(en("PERFIL_TURNO_CANDIDATO"), propia(), ahora)).isEqualTo(cambio);
    }

    @Test
    @DisplayName("QA-C0-F1 · un plazo que venció sin que nadie tocara nada fecha el cambio a VENCIDA; uno por venir, no")
    void plazoVencidoSinTocarNada() {
        IntentoPrueba sinEmpezar = intento();
        sinEmpezar.setCreadoEn(ahora.minus(Duration.ofDays(3)));
        Instant vencio = ahora.minus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
        sinEmpezar.setVenceEn(vencio);
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(sinEmpezar));

        assertThat(acciones.ultimoCambio(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora)).isEqualTo(vencio);

        // Empezada y vencida: el límite que venció es el de entrega.
        IntentoPrueba empezado = intento();
        empezado.setIniciadoEn(ahora.minus(Duration.ofHours(2)));
        Instant entrega = ahora.minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MILLIS);
        empezado.setVenceEn(entrega);
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(empezado));
        assertThat(acciones.ultimoCambio(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora)).isEqualTo(entrega);

        // Habilitada con el cierre por venir: el cierre futuro no es un cambio.
        IntentoPrueba habilitado = intento();
        Instant nacio = ahora.minus(Duration.ofDays(1));
        habilitado.setCreadoEn(nacio);
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(habilitado));
        assertThat(acciones.ultimoCambio(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora)).isEqualTo(nacio);
    }

    @Test
    @DisplayName("QA-C0-F1 · sin intento ni evaluación, con el cierre de la prueba de la vacante ya pasado: ese es el cambio")
    void cierreDeLaVacantePasado() {
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.empty());
        Instant pasado = ahora.minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS);
        Vacante cerrada = Vacante.builder().id(5L).instrumentoEtapaTecnica("PRUEBA_PROPIA").pruebaCierraEn(pasado).build();

        assertThat(acciones.ultimoCambio(en("PRUEBA_TURNO_CANDIDATO"), cerrada, ahora)).isEqualTo(pasado);
        // Sin tarea (calificando), nada que vencer: nulo.
        assertThat(acciones.ultimoCambio(en("PRUEBA_CALIFICANDO"), cerrada, ahora)).isNull();
    }

    @Test
    @DisplayName("Un enlace con token configurado por error no sale en la acción")
    void elEnlaceConTokenNoSale() {
        when(intentos.findByPostulacionId(70L)).thenReturn(Optional.of(intento()));

        Accion accion = acciones.de(en("PRUEBA_TURNO_CANDIDATO"), propia(), ahora,
                "https://portal.renaser.pe/acceso?token=abc");

        assertThat(accion.enlace()).isNull();
    }
}
