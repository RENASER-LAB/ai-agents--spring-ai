package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.PreferenciaAviso;
import com.renaser.ai.ai_engine.notificacion.repository.PreferenciaAvisoRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las preferencias de aviso (V92): por defecto sin fila, y lo que cambia cada interruptor. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Las preferencias de aviso")
class PreferenciasDeAvisoTest {

    @Mock private PreferenciaAvisoRepository repositorio;
    @Mock private UsuarioRepository usuarios;
    @InjectMocks private PreferenciasDeAviso preferencias;

    private PreferenciaAviso fila(boolean whatsapp, int vistas) {
        PreferenciaAviso p = PreferenciaAviso.porDefecto(33L);
        p.setWhatsappActivo(whatsapp);
        p.setMensajeVistas((short) vistas);
        return p;
    }

    @Test
    @DisplayName("Sin fila: correo y campana encendidos, WhatsApp apagado; sin usuario, todo sale")
    void porDefecto() {
        when(usuarios.findById(60L)).thenReturn(Optional.of(Usuario.builder().id(60L).personaId(33L).build()));
        when(repositorio.findById(33L)).thenReturn(Optional.empty());

        assertThat(preferencias.recibeCorreo(60L)).isTrue();
        assertThat(preferencias.recibeEnPortal(60L)).isTrue();
        assertThat(preferencias.de(33L).isWhatsappActivo()).isFalse();
        assertThat(preferencias.recibeCorreo(null)).isTrue();
    }

    @Test
    @DisplayName("Con el correo apagado, recibeCorreo dice que no")
    void correoApagado() {
        PreferenciaAviso sinCorreo = fila(false, 0);
        sinCorreo.setCorreo(false);
        when(usuarios.findById(60L)).thenReturn(Optional.of(Usuario.builder().id(60L).personaId(33L).build()));
        when(repositorio.findById(33L)).thenReturn(Optional.of(sinCorreo));

        assertThat(preferencias.recibeCorreo(60L)).isFalse();
        assertThat(preferencias.recibeEnPortal(60L)).isTrue();
    }

    @Test
    @DisplayName("La BAJA apaga WhatsApp y devuelve el contador del mensaje a cero")
    void laBaja() {
        PreferenciaAviso activa = fila(true, 2);
        when(repositorio.paraEscribir(33L)).thenReturn(Optional.of(activa));
        when(repositorio.save(any(PreferenciaAviso.class))).thenAnswer(i -> i.getArgument(0));

        preferencias.alDarseDeBaja(33L);

        verify(repositorio).asegurar(33L);
        assertThat(activa.isWhatsappActivo()).isFalse();
        assertThat(activa.getMensajeVistas()).isZero();
        assertThat(activa.getActualizadoEn()).isNotNull();
    }

    @Test
    @DisplayName("Cada vez que se pinta el mensaje cuenta una más, con la hora")
    void mensajeVisto() {
        PreferenciaAviso nueva = fila(false, 1);
        when(repositorio.paraEscribir(33L)).thenReturn(Optional.of(nueva));
        when(repositorio.save(any(PreferenciaAviso.class))).thenAnswer(i -> i.getArgument(0));

        preferencias.mensajeVisto(33L);

        assertThat(nueva.getMensajeVistas()).isEqualTo((short) 2);
        assertThat(nueva.getMensajeVistoEn()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Cambiar solo lo que viene: el correo apagado, la campana como estaba")
    void cambiarSoloLoQueViene() {
        PreferenciaAviso nueva = fila(false, 0);
        when(repositorio.paraEscribir(33L)).thenReturn(Optional.of(nueva));
        when(repositorio.save(any(PreferenciaAviso.class))).thenAnswer(i -> i.getArgument(0));

        preferencias.cambiar(33L, false, null);

        assertThat(nueva.isCorreo()).isFalse();
        assertThat(nueva.isPortal()).isTrue();
    }
}
