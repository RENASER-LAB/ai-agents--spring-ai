package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.CambiarNotificaciones;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.MensajeFlotante;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.Notificaciones;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.WhatsappDeLaCuenta;
import com.renaser.ai.ai_engine.notificacion.entity.PreferenciaAviso;
import com.renaser.ai.ai_engine.notificacion.service.PreferenciasDeAviso;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.whatsapp.service.InterruptorWhatsapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El botón «Notificaciones» (V92): lo que devuelve y, sobre todo, cuándo sale el mensaje flotante
 * que invita a activar WhatsApp y con qué texto (puntos 18 a 20 de la spec, AC-01 y AC-10).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Los ajustes de avisos del portal")
class ServicioNotificacionesPortalImplTest {

    @Mock private PreferenciasDeAviso preferencias;
    @Mock private InterruptorWhatsapp whatsapp;
    @Mock private UsuarioRepository usuarios;

    private ServicioNotificacionesPortalImpl servicio;

    private static final Instant HACE_UN_DIA = Instant.now().minus(Duration.ofDays(1));
    private static final Instant HACE_UNA_HORA = Instant.now().minus(Duration.ofHours(1));

    @BeforeEach
    void montar() {
        servicio = new ServicioNotificacionesPortalImpl(preferencias, whatsapp, usuarios);
        lenient().when(usuarios.findById(60L)).thenReturn(Optional.of(
                Usuario.builder().id(60L).correo("lucia.paredes@correo.com").build()));
    }

    private static ContextoUsuario lucia() {
        return new ContextoUsuario(60L, 33L, 1L, "CANDIDATO", List.of(), Map.of());
    }

    private static WhatsappDeLaCuenta whatsapp(String estado, boolean porBaja, boolean cambio) {
        return new WhatsappDeLaCuenta(true, estado, "+51 987 654 321", porBaja, porBaja, null, cambio);
    }

    private static PreferenciaAviso vistas(int veces, Instant ultima) {
        PreferenciaAviso p = PreferenciaAviso.porDefecto(33L);
        p.setMensajeVistas((short) veces);
        p.setMensajeVistoEn(ultima);
        return p;
    }

    @Test
    @DisplayName("AC-01 · nunca activó, con número y un proceso vivo: sale «Recibe tus avisos por WhatsApp»")
    void laPrimeraVez() {
        MensajeFlotante mensaje = ServicioNotificacionesPortalImpl.mensaje(whatsapp("APAGADO", false, false),
                true, HACE_UNA_HORA, vistas(0, null));

        assertThat(mensaje).isEqualTo(new MensajeFlotante(true, DtosNotificaciones.PRIMERA_VEZ));
    }

    @Test
    @DisplayName("AC-10 · mostrado dos veces ya no sale, aunque vuelva a postular")
    void dosVecesYaNoSale() {
        assertThat(ServicioNotificacionesPortalImpl.mensaje(whatsapp("APAGADO", false, false), true,
                HACE_UNA_HORA, vistas(2, HACE_UN_DIA)).mostrar()).isFalse();
    }

    @Test
    @DisplayName("Mostrado una vez, vuelve a salir solo tras otra postulación")
    void unaVezSaleTrasOtraPostulacion() {
        assertThat(ServicioNotificacionesPortalImpl.mensaje(whatsapp("APAGADO", false, false), true,
                HACE_UN_DIA, vistas(1, HACE_UNA_HORA)).mostrar()).isFalse();
        assertThat(ServicioNotificacionesPortalImpl.mensaje(whatsapp("APAGADO", false, false), true,
                HACE_UNA_HORA, vistas(1, HACE_UN_DIA)).mostrar()).isTrue();
    }

    @Test
    @DisplayName("AC-10 · tras la BAJA (contador a cero) y otra postulación sale «Vuelve a recibir…»")
    void traBaja() {
        MensajeFlotante mensaje = ServicioNotificacionesPortalImpl.mensaje(whatsapp("APAGADO", true, false),
                true, HACE_UNA_HORA, vistas(0, HACE_UN_DIA));

        assertThat(mensaje).isEqualTo(new MensajeFlotante(true, DtosNotificaciones.TRAS_BAJA));
    }

    @Test
    @DisplayName("Con un número nuevo sin comprobar: «Activa WhatsApp en tu número nuevo»")
    void cambioDeNumero() {
        assertThat(ServicioNotificacionesPortalImpl.mensaje(whatsapp("APAGADO", false, true), true,
                HACE_UNA_HORA, vistas(0, null)).situacion()).isEqualTo(DtosNotificaciones.CAMBIO_DE_NUMERO);
    }

    @Test
    @DisplayName("No sale con WhatsApp activo o esperando, sin número, sin procesos vivos o sin beta")
    void cuandoNoSale() {
        PreferenciaAviso nuevo = vistas(0, null);
        assertThat(ServicioNotificacionesPortalImpl.mensaje(whatsapp("ACTIVO", false, false), true,
                HACE_UNA_HORA, nuevo).mostrar()).isFalse();
        assertThat(ServicioNotificacionesPortalImpl.mensaje(whatsapp("ESPERANDO", false, false), true,
                HACE_UNA_HORA, nuevo).mostrar()).isFalse();
        assertThat(ServicioNotificacionesPortalImpl.mensaje(new WhatsappDeLaCuenta(true, "SIN_NUMERO", null,
                false, false, null, false), true, HACE_UNA_HORA, nuevo).mostrar()).isFalse();
        assertThat(ServicioNotificacionesPortalImpl.mensaje(whatsapp("APAGADO", false, false), false,
                HACE_UNA_HORA, nuevo).mostrar()).isFalse();
        assertThat(ServicioNotificacionesPortalImpl.mensaje(new WhatsappDeLaCuenta(false, "APAGADO",
                "+51 987 654 321", false, false, null, false), true, HACE_UNA_HORA, nuevo).mostrar()).isFalse();
        // No llegó su mensaje: sí se le vuelve a invitar.
        assertThat(ServicioNotificacionesPortalImpl.mensaje(whatsapp("NO_LLEGO", false, false), true,
                HACE_UNA_HORA, nuevo).mostrar()).isTrue();
    }

    @Test
    @DisplayName("Lo que devuelve: los tres canales con el correo de la cuenta como destino")
    void losTresCanales() {
        PreferenciaAviso sinCorreo = PreferenciaAviso.porDefecto(33L);
        sinCorreo.setCorreo(false);
        when(preferencias.de(33L)).thenReturn(sinCorreo);
        when(whatsapp.estado(33L, 60L)).thenReturn(new InterruptorWhatsapp.Estado(
                whatsapp("APAGADO", false, false), true, HACE_UNA_HORA));

        Notificaciones todo = servicio.notificaciones(lucia());

        assertThat(todo.correo().activo()).isFalse();
        assertThat(todo.correo().destino()).isEqualTo("lucia.paredes@correo.com");
        assertThat(todo.portal().activo()).isTrue();
        assertThat(todo.whatsapp().estado()).isEqualTo("APAGADO");
        assertThat(todo.mensaje().mostrar()).isTrue();
        assertThat(todo.guiaVista()).isFalse();
    }

    @Test
    @DisplayName("V93 · guiaVista dice si ya dio por vista la guía de los avisos")
    void guiaVista() {
        PreferenciaAviso yaLaVio = PreferenciaAviso.porDefecto(33L);
        yaLaVio.setGuiaAvisosVistaEn(HACE_UN_DIA);
        when(preferencias.de(33L)).thenReturn(yaLaVio);
        when(whatsapp.estado(33L, 60L)).thenReturn(new InterruptorWhatsapp.Estado(
                whatsapp("ACTIVO", false, false), true, HACE_UNA_HORA));

        assertThat(servicio.notificaciones(lucia()).guiaVista()).isTrue();
    }

    @Test
    @DisplayName("V93 · marcar la guía vista es de quien pregunta")
    void marcarGuiaVista() {
        servicio.guiaVista(lucia());

        verify(preferencias).guiaVista(33L);
    }

    @Test
    @DisplayName("Cambiar sin nada que cambiar no escribe; con algo, lo guarda")
    void cambiar() {
        when(preferencias.de(33L)).thenReturn(PreferenciaAviso.porDefecto(33L));
        when(whatsapp.estado(33L, 60L)).thenReturn(new InterruptorWhatsapp.Estado(
                whatsapp("APAGADO", false, false), false, null));

        servicio.cambiar(lucia(), new CambiarNotificaciones(null, null));
        verify(preferencias, never()).cambiar(anyLong(), any(), any());

        servicio.cambiar(lucia(), new CambiarNotificaciones(false, null));
        verify(preferencias).cambiar(33L, false, null);
    }
}
