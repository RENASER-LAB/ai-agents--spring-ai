package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.AvisoPortal;
import com.renaser.ai.ai_engine.notificacion.repository.AvisoPortalRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La campana con «En el portal» apagado en «Notificaciones» (V92): los avisos de sus procesos no
 * se publican; los de su perfil (las reseñas) sí.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La campana y su interruptor")
class ServicioAvisosPortalTest {

    private static final Long USUARIO = 60L;

    @Mock private AvisoPortalRepository avisos;
    @Mock private PreferenciasDeAviso preferencias;

    private ServicioAvisosPortal campana;

    @BeforeEach
    void montar() {
        campana = new ServicioAvisosPortal(avisos, preferencias);
        lenient().when(avisos.save(any(AvisoPortal.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("AC-09 · con la campana apagada, el aviso de su etapa no se publica")
    void apagadaNoPublicaLosDeSusProcesos() {
        when(preferencias.recibeEnPortal(USUARIO)).thenReturn(false);

        AvisoPortal publicado = campana.publicar(1L, USUARIO, AvisoPortal.POSTULACION_AVANZA,
                "Te toca la prueba del puesto", "Analista de Datos", 70L, 5L);
        AvisoPortal retirada = campana.publicar(1L, USUARIO, AvisoPortal.VACANTE_ELIMINADA,
                "Se retiró la vacante", "No tienes que hacer nada", null, null);

        assertThat(publicado).isNull();
        assertThat(retirada).isNull();
        verify(avisos, never()).save(any());
    }

    @Test
    @DisplayName("Con la campana apagada, una reseña de su perfil sí llega")
    void lasResenasLleganIgual() {
        assertThat(campana.publicar(1L, USUARIO, AvisoPortal.RESENA_PUBLICADA, "Te dejaron una reseña",
                "Acme", null, null)).isNotNull();
        verify(avisos).save(any(AvisoPortal.class));
    }

    @Test
    @DisplayName("Con la campana encendida, el aviso de su etapa se publica")
    void encendidaPublica() {
        when(preferencias.recibeEnPortal(USUARIO)).thenReturn(true);

        assertThat(campana.publicar(1L, USUARIO, AvisoPortal.PRUEBA_DISPONIBLE, "Te toca la prueba",
                "Analista", 70L, 5L)).isNotNull();
    }
}
