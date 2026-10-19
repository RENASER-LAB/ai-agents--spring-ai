package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.whatsapp.entity.AvisoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.AvisosPorWhatsapp.DeLaTransicion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Qué transiciones dejan un aviso para el bot (V73): AC-08, AC-09, AC-10 y el cierre del vínculo
 * al terminar.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Los avisos «te toca» por WhatsApp")
class AvisosPorWhatsappTest {

    private static final Long RENASER = 1L;

    @Mock private VinculoWhatsappRepository vinculos;
    @Mock private AvisoWhatsappRepository avisos;
    @Mock private BajaWhatsappRepository bajas;
    @Mock private PersonaRepository personas;
    @Mock private BetaWhatsapp beta;
    @Mock private EnvioAlBot envio;
    @Mock private PlatformTransactionManager transacciones;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository postulaciones;
    @Mock private com.renaser.ai.ai_engine.vacante.repository.VacanteRepository vacantes;
    @Mock private LoQueLeToca acciones;
    @Mock private ResultadosPorWhatsapp resultados;

    private static final String MIS_PROCESOS = "https://portal.renaser.pe/procesos";

    private AvisosPorWhatsapp whatsapp;

    @BeforeEach
    void montar() {
        lenient().when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        PropiedadesWhatsapp propiedades = new PropiedadesWhatsapp();
        propiedades.setEnlaceMisProcesos(MIS_PROCESOS);
        whatsapp = new AvisosPorWhatsapp(vinculos, avisos, bajas, personas, beta, envio,
                new RedaccionDelAviso(postulaciones, vacantes, acciones, propiedades), resultados,
                transacciones);
    }

    private static Postulacion postulacion() {
        return Postulacion.builder().id(70L).organizacionId(RENASER).build();
    }

    private static EstadoPostulacion estado(String codigo, boolean esFinal) {
        return EstadoPostulacion.builder().codigo(codigo).esFinal(esFinal).build();
    }

    private static VinculoWhatsapp vinculo(String estado) {
        return VinculoWhatsapp.builder().id(7L).postulacionId(70L).referencia("pw_ref")
                .telefono("+51987654321").estado(estado)
                .vinculadoEn(VinculoWhatsapp.ACTIVO.equals(estado) ? Instant.now() : null)
                .anuncioCaducaEn(Instant.now().plusSeconds(3600)).build();
    }

    @Test
    @DisplayName("AC-08 · pasar a la prueba con vínculo activo deja UN aviso ligado a su transición")
    void laPruebaDejaUnAviso() {
        when(beta.ofrecePara(RENASER)).thenReturn(true);
        when(vinculos.vivoDe(70L)).thenReturn(Optional.of(vinculo(VinculoWhatsapp.ACTIVO)));

        whatsapp.alAvisar(postulacion(), "PRUEBA_DISPONIBLE", estado("PRUEBA_TURNO_CANDIDATO", false),
                new DeLaTransicion(900L, "Ana", "Asistente Contable", "PRUEBA_PROPIA"));

        ArgumentCaptor<AvisoWhatsapp> guardado = ArgumentCaptor.forClass(AvisoWhatsapp.class);
        verify(avisos).save(guardado.capture());
        AvisoWhatsapp aviso = guardado.getValue();
        assertThat(aviso.getTransicionEstadoId()).isEqualTo(900L);
        assertThat(aviso.getTipo()).isEqualTo("PRUEBA_DISPONIBLE");
        assertThat(aviso.getEstado()).isEqualTo(AvisoWhatsapp.PENDIENTE);
        assertThat(aviso.getId().version()).isEqualTo(7);
        assertThat(aviso.getTelefono()).isEqualTo("+51987654321");
        assertThat(aviso.getReferencia()).isEqualTo("pw_ref");
        // El formato de la regla 19 (V76): bloques, negritas y el enlace fijo a «Mis procesos».
        assertThat(aviso.getTexto()).startsWith("¡Felicidades, Ana! 🎉\n\n📌 *Asistente Contable*\n"
                + "Tu *prueba del puesto* ya está disponible.")
                .contains("👉 " + MIS_PROCESOS)
                .doesNotContain("token").doesNotContain("?");
    }

    @Test
    @DisplayName("V76 · con fecha para empezar, el aviso de la prueba la dice en hora de Lima")
    void laPruebaConSuPlazo() {
        when(beta.ofrecePara(RENASER)).thenReturn(true);
        when(vinculos.vivoDe(70L)).thenReturn(Optional.of(vinculo(VinculoWhatsapp.ACTIVO)));
        Postulacion suya = Postulacion.builder().id(70L).organizacionId(RENASER).vacanteId(5L).build();
        when(postulaciones.findById(70L)).thenReturn(Optional.of(suya));
        com.renaser.ai.ai_engine.vacante.entity.Vacante vacante =
                com.renaser.ai.ai_engine.vacante.entity.Vacante.builder().id(5L).build();
        when(vacantes.findById(5L)).thenReturn(Optional.of(vacante));
        Instant cierre = Instant.now().plus(java.time.Duration.ofDays(3));
        when(acciones.empezarAntesDe(suya, vacante)).thenReturn(cierre);

        whatsapp.alAvisar(postulacion(), "PRUEBA_DISPONIBLE", estado("PRUEBA_TURNO_CANDIDATO", false),
                new DeLaTransicion(900L, "Ana", "Asistente Contable", "PRUEBA_PROPIA"));

        ArgumentCaptor<AvisoWhatsapp> guardado = ArgumentCaptor.forClass(AvisoWhatsapp.class);
        verify(avisos).save(guardado.capture());
        assertThat(guardado.getValue().getTexto()).contains("⏰ Puedes empezarla hasta el *"
                + TextosDeWhatsapp.fechaYHora(cierre, Instant.now()) + "* (hora de Lima).");
    }

    @Test
    @DisplayName("El aviso cumple los límites del contrato: nombre y vacante hasta 60, texto hasta 1000")
    void elAvisoCumpleLosLimites() {
        when(beta.ofrecePara(RENASER)).thenReturn(true);
        when(vinculos.vivoDe(70L)).thenReturn(Optional.of(vinculo(VinculoWhatsapp.ACTIVO)));
        String largo = "Asistente Contable y Administrativo para la Sede Central de Arequipa, turno noche";

        whatsapp.alAvisar(postulacion(), "PRUEBA_DISPONIBLE", estado("PRUEBA_TURNO_CANDIDATO", false),
                new DeLaTransicion(900L, "María de los Ángeles Guadalupe del Carmen Fernández de Córdoba",
                        largo, "PRUEBA_PROPIA"));

        ArgumentCaptor<AvisoWhatsapp> guardado = ArgumentCaptor.forClass(AvisoWhatsapp.class);
        verify(avisos).save(guardado.capture());
        AvisoWhatsapp aviso = guardado.getValue();
        assertThat(aviso.getVacante()).hasSizeLessThanOrEqualTo(60).endsWith("…");
        assertThat(aviso.getNombre()).hasSizeLessThanOrEqualTo(60);
        assertThat(aviso.getTexto()).hasSizeLessThanOrEqualTo(1000).contains(largo);
    }

    @Test
    @DisplayName("AC-08 · la misma transición no deja dos avisos aunque se repita")
    void laMismaTransicionNoRepite() {
        when(beta.ofrecePara(RENASER)).thenReturn(true);
        when(vinculos.vivoDe(70L)).thenReturn(Optional.of(vinculo(VinculoWhatsapp.ACTIVO)));
        when(avisos.existsByTransicionEstadoIdAndTipo(900L, "PRUEBA_DISPONIBLE")).thenReturn(true);

        whatsapp.alAvisar(postulacion(), "PRUEBA_DISPONIBLE", estado("PRUEBA_TURNO_CANDIDATO", false),
                new DeLaTransicion(900L, "Ana", "Contador", null));

        verify(avisos, never()).save(any());
    }

    @Test
    @DisplayName("AC-09 · quien pulsó el botón pero nunca escribió no recibe nada")
    void conElAnuncioPendienteNoSeAvisa() {
        when(beta.ofrecePara(RENASER)).thenReturn(true);
        when(vinculos.vivoDe(70L)).thenReturn(Optional.of(vinculo(VinculoWhatsapp.PENDIENTE)));

        whatsapp.alAvisar(postulacion(), "PRUEBA_DISPONIBLE", estado("PRUEBA_TURNO_CANDIDATO", false),
                new DeLaTransicion(900L, "Ana", "Contador", null));

        verify(avisos, never()).save(any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POSTULACION_NO_CONTINUA", "POSTULACION_CERRADA", "RETIRO_CONFIRMADO",
            "VACANTE_ELIMINADA", "RECORDATORIO_PRUEBA"})
    @DisplayName("AC-10 · ni cierres, ni rechazos, ni recordatorios van por WhatsApp")
    void soloLosTeToca(String aviso) {
        whatsapp.alAvisar(postulacion(), aviso, estado("NO_CONTINUA", true),
                new DeLaTransicion(900L, "Ana", "Contador", null));

        verifyNoInteractions(vinculos, avisos, beta);
    }

    @Test
    @DisplayName("AC-04 · con la beta apagada o en otra empresa no se encola nada")
    void sinBetaNoSeEncola() {
        when(beta.ofrecePara(RENASER)).thenReturn(false);

        whatsapp.alAvisar(postulacion(), "POSTULACION_AVANZA", estado("SIMULACION_TURNO_CANDIDATO", false),
                new DeLaTransicion(901L, "Ana", "Contador", null));

        verify(avisos, never()).save(any());
        verify(vinculos, never()).vivoDe(anyLong());
    }

    @Test
    @DisplayName("Un avance cuenta lo que dice el portal de ese momento")
    void unAvanceLlevaElTextoDelMomento() {
        when(beta.ofrecePara(RENASER)).thenReturn(true);
        when(vinculos.vivoDe(70L)).thenReturn(Optional.of(vinculo(VinculoWhatsapp.ACTIVO)));

        whatsapp.alAvisar(postulacion(), "POSTULACION_AVANZA", estado("SIMULACION_TURNO_CANDIDATO", false),
                new DeLaTransicion(901L, "Ana", "Contador", null));

        ArgumentCaptor<AvisoWhatsapp> guardado = ArgumentCaptor.forClass(AvisoWhatsapp.class);
        verify(avisos).save(guardado.capture());
        assertThat(guardado.getValue().getTexto()).contains("Te toca: *Confirma tu simulación*")
                .contains("👉 " + MIS_PROCESOS);
    }

    @Test
    @DisplayName("AC-12 · al terminar el proceso el vínculo se cierra y queda la revocación por mandar")
    void alTerminarSeCierra() {
        VinculoWhatsapp activo = vinculo(VinculoWhatsapp.ACTIVO);
        when(vinculos.vivoParaEscribir(70L)).thenReturn(Optional.of(activo));

        Instant ocurrida = Instant.now();
        whatsapp.alTerminar(postulacion(), ocurrida, true);

        assertThat(activo.getEstado()).isEqualTo(VinculoWhatsapp.REVOCADO);
        assertThat(activo.getMotivoCierre()).isEqualTo("PROCESO_TERMINADO");
        assertThat(activo.getRevocacionProximoIntentoEn()).isNotNull();
        verify(avisos).omitirPendientesDe(any(), any());
        // V76 · el resultado y su evento se guardan con el hecho; el cierre pregunta por el evento.
        verify(resultados).alTerminar(any(Postulacion.class), org.mockito.ArgumentMatchers.eq(ocurrida),
                org.mockito.ArgumentMatchers.eq(true));
        verify(resultados).alCerrarElVinculo(org.mockito.ArgumentMatchers.eq(activo), any());
    }

    @Test
    @DisplayName("AC-15 · el borrado se lleva los vínculos, sus avisos y la baja de un número que nadie más usa")
    void elBorradoSeLlevaTodo() {
        VinculoWhatsapp activo = vinculo(VinculoWhatsapp.ACTIVO);
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of(activo));
        when(bajas.existsById("+51987654321")).thenReturn(true);
        when(personas.existsByTelefonoAndIdNot("+51987654321", 33L)).thenReturn(false);

        whatsapp.alBorrarLosDatos(33L, "+51987654321");

        verify(avisos).borrarDe(List.of(7L));
        // V76 · sus resultados y sus eventos se van con ellos.
        verify(resultados).alBorrarLosDatos(List.of(7L));
        verify(vinculos).deleteAll(List.of(activo));
        verify(bajas).deleteById("+51987654321");
        // Sin transacción en curso, la revocación sale ya, con su motivo.
        verify(envio).revocarSinFila("+51987654321", "pw_ref", "BORRADO_DATOS");
    }

    @Test
    @DisplayName("V76 · el borrado cancela también un aviso final que el bot ya aceptó, aunque el vínculo esté cerrado")
    void elBorradoCancelaElAvisoFinalAceptado() {
        VinculoWhatsapp cerrado = vinculo(VinculoWhatsapp.REVOCADO);
        cerrado.setCerradoEn(Instant.now());
        cerrado.setMotivoCierre(VinculoWhatsapp.POR_PROCESO_TERMINADO);
        cerrado.setRevocacionEnviadaEn(Instant.now());
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of(cerrado));
        when(resultados.alBorrarLosDatos(List.of(7L))).thenReturn(java.util.Set.of(7L));

        whatsapp.alBorrarLosDatos(33L, null);

        verify(envio).revocarSinFila("+51987654321", "pw_ref", "BORRADO_DATOS");
    }

    @Test
    @DisplayName("La baja de un número que otra cuenta sigue usando no se borra")
    void laBajaCompartidaSeQueda() {
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of());
        when(bajas.existsById("+51987654321")).thenReturn(true);
        when(personas.existsByTelefonoAndIdNot("+51987654321", 33L)).thenReturn(true);

        whatsapp.alBorrarLosDatos(33L, "+51987654321");

        verify(bajas, never()).deleteById(any());
    }
}
