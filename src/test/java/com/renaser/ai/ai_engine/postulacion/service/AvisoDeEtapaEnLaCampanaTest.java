package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.notificacion.service.ServicioAvisosPortal;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La campana en cada etapa (V70, AC-10 a AC-12): qué dice cada aviso y cuándo se escribe.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El aviso de etapa en la campana")
class AvisoDeEtapaEnLaCampanaTest {

    @Mock private ServicioAvisosPortal avisos;
    @InjectMocks private AvisoDeEtapaEnLaCampana campana;

    private final Postulacion postulacion = Postulacion.builder()
            .id(5L).organizacionId(1L).usuarioId(7L).vacanteId(9L).build();

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static EstadoPostulacion estado(String codigo, String etapa, String nombre) {
        return EstadoPostulacion.builder().codigo(codigo).etapaCodigo(etapa).nombre(nombre).build();
    }

    @Test
    @DisplayName("la prueba: «Tu prueba del puesto está disponible · vacante», con su plazo")
    void laPrueba() {
        var texto = AvisoDeEtapaEnLaCampana.para("PRUEBA_DISPONIBLE",
                estado("PRUEBA_TURNO_CANDIDATO", "PRUEBA_PUESTO", "Prueba"),
                "Administrador de tienda", "90 minutos desde que la empieces").orElseThrow();

        assertThat(texto.titulo()).isEqualTo("Tu prueba del puesto está disponible · Administrador de tienda");
        assertThat(texto.cuerpo()).contains("Plazo: 90 minutos desde que la empieces.");
    }

    @Test
    @DisplayName("te toca: dice qué toca según la etapa")
    void teToca() {
        assertThat(AvisoDeEtapaEnLaCampana.para("POSTULACION_AVANZA",
                estado("SIMULACION_TURNO_CANDIDATO", "SIMULACION", "Simulación · turno del candidato"),
                "Cajero", null).orElseThrow().titulo())
                .isEqualTo("Te toca la simulación · Cajero");
        assertThat(AvisoDeEtapaEnLaCampana.para("POSTULACION_AVANZA",
                estado("PERFIL_TURNO_CANDIDATO", "PERFIL_INTEGRAL", "Perfil Integral"),
                "Cajero", null).orElseThrow().titulo())
                .isEqualTo("Te toca tu evaluación · Cajero");
        assertThat(AvisoDeEtapaEnLaCampana.para("POSTULACION_AVANZA",
                estado("VALIDACION_TURNO_CANDIDATO", "VALIDACION", "Validación"), "Cajero", null)
                .orElseThrow().titulo()).startsWith("Te toca la validación");
        assertThat(AvisoDeEtapaEnLaCampana.para("POSTULACION_AVANZA",
                estado("DECISION_TURNO_CANDIDATO", "DECISION", "Decisión"), "Cajero", null)
                .orElseThrow().titulo()).startsWith("Te toca enviar una evidencia");
        assertThat(AvisoDeEtapaEnLaCampana.para("POSTULACION_AVANZA", null, "Cajero", null)
                .orElseThrow().titulo()).startsWith("Te toca un paso nuevo");
    }

    @Test
    @DisplayName("los cierres: no continúa, cerrada y retiro, cada uno con el suyo")
    void losCierres() {
        assertThat(AvisoDeEtapaEnLaCampana.para("POSTULACION_NO_CONTINUA", null, "Cajero", null)
                .orElseThrow().titulo()).isEqualTo("Tu proceso para Cajero no continúa");
        assertThat(AvisoDeEtapaEnLaCampana.para("POSTULACION_CERRADA", null, "Cajero", null)
                .orElseThrow().titulo()).isEqualTo("Tu postulación para Cajero se cerró");
        assertThat(AvisoDeEtapaEnLaCampana.para("RETIRO_CONFIRMADO", null, "Cajero", null)
                .orElseThrow().titulo()).isEqualTo("Confirmamos tu retiro de Cajero");
        assertThat(AvisoDeEtapaEnLaCampana.para("RETIRO_CONFIRMADO", null, "", null)
                .orElseThrow().titulo()).isEqualTo("Confirmamos tu retiro");
    }

    @Test
    @DisplayName("un correo sin pareja no deja aviso")
    void sinPareja() {
        assertThat(AvisoDeEtapaEnLaCampana.para("POSTULACION_RECIBIDA", null, "Cajero", null)).isEmpty();

        campana.publicar(postulacion, "POSTULACION_RECIBIDA", null, "Cajero", null);

        verifyNoInteractions(avisos);
    }

    @Test
    @DisplayName("sin transacción se escribe ya, ligado al proceso")
    void sinTransaccionYa() {
        campana.publicar(postulacion, "POSTULACION_NO_CONTINUA", null, "Cajero", null);

        verify(avisos).publicar(eq(1L), eq(7L), eq("POSTULACION_NO_CONTINUA"),
                eq("Tu proceso para Cajero no continúa"), anyString(), eq(5L), eq(9L));
    }

    @Test
    @DisplayName("dentro de una transacción espera a que se confirme, y si se deshace no queda nada")
    void esperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();

        campana.publicar(postulacion, "POSTULACION_CERRADA", null, "Cajero", null);
        verify(avisos, never()).publicar(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyLong(), anyLong());

        // Se deshace: no hay aviso de algo que no pasó
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(avisos, never()).publicar(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyLong(), anyLong());

        // Se confirma: ahora sí
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
        verify(avisos).publicar(eq(1L), eq(7L), eq("POSTULACION_CERRADA"), anyString(),
                anyString(), eq(5L), eq(9L));
    }

    @Test
    @DisplayName("si la campana falla, no tumba nada (AC-12)")
    void siFallaNoTumbaNada() {
        when(avisos.publicar(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("la base no responde"));

        assertThatCode(() -> campana.publicar(postulacion, "POSTULACION_NO_CONTINUA", null,
                "Cajero", null)).doesNotThrowAnyException();
    }
}
