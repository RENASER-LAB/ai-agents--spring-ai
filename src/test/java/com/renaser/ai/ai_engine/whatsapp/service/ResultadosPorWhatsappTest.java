package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.entity.EventoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.ResultadoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.EventoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.ResultadoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El resultado consultable, su evento y su autorización (V76): AC-12, AC-13, AC-15, AC-16, AC-18
 * y AC-21 del lado de EX.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Los resultados por WhatsApp y su bandeja de salida")
class ResultadosPorWhatsappTest {

    private static final Long RENASER = 1L;
    private static final String NUMERO = "+51987654321";

    @Mock private VinculoWhatsappRepository vinculos;
    @Mock private ResultadoWhatsappRepository resultados;
    @Mock private EventoWhatsappRepository eventos;
    @Mock private BajaWhatsappRepository bajas;
    @Mock private VacanteRepository vacantes;
    @Mock private PersonaRepository personas;
    @Mock private BetaWhatsapp beta;
    @Mock private EnvioAlBot envio;

    private final PropiedadesWhatsapp propiedades = new PropiedadesWhatsapp();
    private ResultadosPorWhatsapp whatsapp;

    @BeforeEach
    void montar() {
        propiedades.setProductorV2(true);
        whatsapp = new ResultadosPorWhatsapp(vinculos, resultados, eventos, bajas, vacantes, personas, beta,
                propiedades, envio);
        lenient().when(beta.ofrecePara(RENASER)).thenReturn(true);
        lenient().when(vacantes.findById(5L)).thenReturn(Optional.of(Vacante.builder().id(5L)
                .organizacionId(RENASER).titulo("Asistente Contable y Administrativo para la Sede Central de "
                        + "Arequipa").build()));
        lenient().when(personas.findById(33L)).thenReturn(Optional.of(Persona.builder().id(33L)
                .nombre("Ana").build()));
    }

    private static Postulacion terminada(String estado) {
        return Postulacion.builder().id(70L).organizacionId(RENASER).vacanteId(5L).estadoCodigo(estado).build();
    }

    private static VinculoWhatsapp vinculo(String estado) {
        return VinculoWhatsapp.builder().id(7L).postulacionId(70L).personaId(33L).referencia("pw_ref")
                .telefono(NUMERO).estado(estado).build();
    }

    private void conVinculo(String estado) {
        when(vinculos.vivoDe(70L)).thenReturn(Optional.of(vinculo(estado)));
    }

    @Test
    @DisplayName("AC-12 · con el vínculo activo, el resultado y su evento quedan en la transacción del hecho")
    void elResultadoYSuEvento() {
        conVinculo(VinculoWhatsapp.ACTIVO);
        Instant ocurrida = Instant.now();
        Instant fecha = ocurrida.truncatedTo(ChronoUnit.MILLIS);

        whatsapp.alTerminar(terminada("NO_CONTINUA"), ocurrida, true);

        ArgumentCaptor<ResultadoWhatsapp> resultado = ArgumentCaptor.forClass(ResultadoWhatsapp.class);
        verify(resultados).save(resultado.capture());
        assertThat(resultado.getValue().getTipo()).isEqualTo("NO_CONTINUA");
        assertThat(resultado.getValue().getFecha()).isEqualTo(fecha);
        assertThat(resultado.getValue().getRevision()).isEqualTo(1);
        assertThat(resultado.getValue().getVinculoWhatsappId()).isEqualTo(7L);
        // 30 días de consulta desde el hecho (C-2).
        assertThat(resultado.getValue().getConsultableHasta()).isEqualTo(fecha.plus(Duration.ofDays(30)));

        ArgumentCaptor<EventoWhatsapp> evento = ArgumentCaptor.forClass(EventoWhatsapp.class);
        verify(eventos).save(evento.capture());
        EventoWhatsapp guardado = evento.getValue();
        assertThat(guardado.getId().version()).isEqualTo(7);
        assertThat(guardado.getTipo()).isEqualTo("RESULTADO");
        assertThat(guardado.getEstado()).isEqualTo("PENDIENTE");
        assertThat(guardado.getRevision()).isEqualTo(1);
        assertThat(guardado.getResultadoTipo()).isEqualTo("NO_CONTINUA");
        assertThat(guardado.getOcurridoEn()).isEqualTo(fecha);
        assertThat(guardado.isAvisoAutorizado()).isTrue();
        // La autorización dura como mucho 24 horas desde el hecho (C-6).
        assertThat(guardado.getAvisoCaducaEn()).isEqualTo(fecha.plus(Duration.ofHours(24)));
        assertThat(guardado.isCierraVinculo()).isTrue();
        assertThat(guardado.getTelefono()).isEqualTo(NUMERO);
        assertThat(guardado.getReferencia()).isEqualTo("pw_ref");
        assertThat(guardado.getNombre()).isEqualTo("Ana");
        assertThat(guardado.getVacante()).hasSizeLessThanOrEqualTo(60).endsWith("…");
    }

    @Test
    @DisplayName("AC-18 · con el productor apagado no nace ningún evento: el resultado se consulta, nada se manda")
    void productorApagado() {
        propiedades.setProductorV2(false);
        conVinculo(VinculoWhatsapp.ACTIVO);

        whatsapp.alTerminar(terminada("CONTRATADO"), Instant.now(), true);

        verify(resultados).save(any());
        verify(eventos, never()).save(any());
    }

    @Test
    @DisplayName("AC-18 · con la beta apagada o sin bot tampoco nace ningún evento")
    void sinBeta() {
        when(beta.ofrecePara(RENASER)).thenReturn(false);
        conVinculo(VinculoWhatsapp.ACTIVO);

        whatsapp.alTerminar(terminada("CONTRATADO"), Instant.now(), true);

        verify(eventos, never()).save(any());
    }

    @Test
    @DisplayName("Si el equipo decidió no contárselo, el evento sale sin autorización de aviso")
    void sinAvisar() {
        conVinculo(VinculoWhatsapp.ACTIVO);

        whatsapp.alTerminar(terminada("NO_CONTINUA"), Instant.now(), false);

        ArgumentCaptor<EventoWhatsapp> evento = ArgumentCaptor.forClass(EventoWhatsapp.class);
        verify(eventos).save(evento.capture());
        assertThat(evento.getValue().isAvisoAutorizado()).isFalse();
    }

    @Test
    @DisplayName("AC-16 · quien pulsó el botón y nunca escribió no tiene resultado ni autorización")
    void vinculoPendiente() {
        conVinculo(VinculoWhatsapp.PENDIENTE);

        whatsapp.alTerminar(terminada("CERRADA"), Instant.now(), true);

        verify(resultados, never()).save(any());
        verify(eventos, never()).save(any());
    }

    @Test
    @DisplayName("AC-15 · con el número dado de baja no hay resultado ni aviso")
    void numeroDeBaja() {
        conVinculo(VinculoWhatsapp.ACTIVO);
        when(bajas.existsById(NUMERO)).thenReturn(true);

        whatsapp.alTerminar(terminada("CERRADA"), Instant.now(), true);

        verify(resultados, never()).save(any());
        verify(eventos, never()).save(any());
    }

    @Test
    @DisplayName("AC-13 · el mismo hecho dos veces no deja otro resultado ni otro pendiente")
    void sinDuplicados() {
        conVinculo(VinculoWhatsapp.ACTIVO);
        when(resultados.existsByPostulacionId(70L)).thenReturn(true);

        whatsapp.alTerminar(terminada("CERRADA"), Instant.now(), true);

        verify(resultados, never()).save(any());
        verify(eventos, never()).save(any());
    }

    @Test
    @DisplayName("Eliminar la vacante no deja resultado por aquí: su cierre lo cuenta la campana")
    void vacanteEliminada() {
        conVinculo(VinculoWhatsapp.ACTIVO);
        when(vacantes.findById(5L)).thenReturn(Optional.of(Vacante.builder().id(5L).organizacionId(RENASER)
                .eliminadaEn(Instant.now()).build()));

        whatsapp.alTerminar(terminada("CERRADA"), Instant.now(), true);

        verify(resultados, never()).save(any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"PRUEBA_TURNO_CANDIDATO", "PERFIL_POR_CONFIRMAR"})
    @DisplayName("Un estado que no es final no es un resultado")
    void noEsFinal(String estado) {
        whatsapp.alTerminar(terminada(estado), Instant.now(), true);

        verifyNoInteractions(vinculos, resultados, eventos);
    }

    // ============ la convocatoria ============

    @Test
    @DisplayName("AC-10 · al cerrar la convocatoria, quien sigue en carrera recibe su evento sin cerrar el vínculo")
    void convocatoriaCerrada() {
        Instant cerrada = Instant.now();
        Vacante vacante = Vacante.builder().id(5L).organizacionId(RENASER).titulo("Contador").estado("CERRADA")
                .cerradaEn(cerrada).build();
        VinculoWhatsapp deBaja = vinculo(VinculoWhatsapp.ACTIVO);
        deBaja.setId(8L);
        deBaja.setTelefono("+51911111111");
        when(vinculos.activosEnCarreraDeLaVacante(5L)).thenReturn(List.of(vinculo(VinculoWhatsapp.ACTIVO), deBaja));
        when(bajas.existsById(NUMERO)).thenReturn(false);
        when(bajas.existsById("+51911111111")).thenReturn(true);

        whatsapp.alCerrarLaConvocatoria(vacante);

        ArgumentCaptor<EventoWhatsapp> evento = ArgumentCaptor.forClass(EventoWhatsapp.class);
        verify(eventos).save(evento.capture());
        EventoWhatsapp guardado = evento.getValue();
        assertThat(guardado.getTipo()).isEqualTo("CONVOCATORIA_CERRADA");
        assertThat(guardado.getConvocatoriaCerradaEn()).isEqualTo(cerrada.truncatedTo(ChronoUnit.MILLIS));
        assertThat(guardado.getRevision()).isEqualTo(1);
        assertThat(guardado.isCierraVinculo()).isFalse();
        assertThat(guardado.getResultadoTipo()).isNull();
        // Ni cierra el vínculo ni toca la postulación.
        verify(vinculos, never()).save(any());
    }

    @Test
    @DisplayName("AC-18 · con el productor apagado cerrar la convocatoria no escribe nada")
    void convocatoriaSinProductor() {
        propiedades.setProductorV2(false);

        whatsapp.alCerrarLaConvocatoria(Vacante.builder().id(5L).organizacionId(RENASER).build());

        verifyNoInteractions(vinculos, eventos);
    }

    // ============ la revocación del vínculo y el evento ============

    private static EventoWhatsapp evento(String estado) {
        return EventoWhatsapp.builder().id(UUID.randomUUID()).postulacionId(70L).vinculoWhatsappId(7L)
                .tipo("RESULTADO").estado(estado).telefono(NUMERO)
                .avisoCaducaEn(Instant.now().plus(Duration.ofHours(20))).build();
    }

    private static VinculoWhatsapp cerradoPorFin() {
        VinculoWhatsapp vinculo = vinculo(VinculoWhatsapp.ACTIVO);
        vinculo.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_PROCESO_TERMINADO, Instant.now());
        return vinculo;
    }

    @Test
    @DisplayName("C-7 · con el RESULTADO pendiente, el cierre no manda PROCESO_TERMINADO: lo hace el evento")
    void elCierreEsperaAlEvento() {
        VinculoWhatsapp vinculo = cerradoPorFin();
        when(eventos.findByVinculoWhatsappIdAndTipo(7L, "RESULTADO")).thenReturn(List.of(evento("PENDIENTE")));

        whatsapp.alCerrarElVinculo(vinculo, Instant.now());

        assertThat(vinculo.getRevocacionProximoIntentoEn()).isNull();
        assertThat(vinculo.getRevocacionEnviadaEn()).isNull();
    }

    @Test
    @DisplayName("C-7 · con el RESULTADO ya aceptado, el vínculo queda contado; sin evento, la revocación de v1")
    void elCierreSinEventoEsV1() {
        VinculoWhatsapp aceptado = cerradoPorFin();
        when(eventos.findByVinculoWhatsappIdAndTipo(7L, "RESULTADO")).thenReturn(List.of(evento("ACEPTADO")));
        whatsapp.alCerrarElVinculo(aceptado, Instant.now());
        assertThat(aceptado.getRevocacionEnviadaEn()).isNotNull();
        assertThat(aceptado.getRevocacionProximoIntentoEn()).isNull();

        VinculoWhatsapp sinEvento = cerradoPorFin();
        when(eventos.findByVinculoWhatsappIdAndTipo(7L, "RESULTADO")).thenReturn(List.of());
        whatsapp.alCerrarElVinculo(sinEvento, Instant.now());
        assertThat(sinEvento.getRevocacionProximoIntentoEn()).isNotNull();
    }

    @Test
    @DisplayName("C-10 · un RESULTADO fallido (bot en v1) devuelve al vínculo su revocación de siempre")
    void elRespaldoDeV1() {
        VinculoWhatsapp vinculo = cerradoPorFin();
        vinculo.setRevocacionProximoIntentoEn(null);
        when(vinculos.findById(7L)).thenReturn(Optional.of(vinculo));
        when(vinculos.paraEscribir(7L)).thenReturn(Optional.of(vinculo));

        whatsapp.trasElEvento(evento("FALLIDO"), Instant.now());

        assertThat(vinculo.getRevocacionProximoIntentoEn()).isNotNull();
        verify(vinculos).save(vinculo);
    }

    @Test
    @DisplayName("C-7 · un RESULTADO aceptado deja el vínculo contado: no sale PROCESO_TERMINADO")
    void aceptadoCierraElVinculo() {
        VinculoWhatsapp vinculo = cerradoPorFin();
        vinculo.setRevocacionProximoIntentoEn(null);
        when(vinculos.findById(7L)).thenReturn(Optional.of(vinculo));
        when(vinculos.paraEscribir(7L)).thenReturn(Optional.of(vinculo));

        whatsapp.trasElEvento(evento("ACEPTADO"), Instant.now());

        assertThat(vinculo.getRevocacionEnviadaEn()).isNotNull();
        assertThat(vinculo.getRevocacionProximoIntentoEn()).isNull();
    }

    @Test
    @DisplayName("Con el vínculo aún vivo, el evento no lo toca: lo resuelve su cierre")
    void vinculoVivo() {
        VinculoWhatsapp vivo = vinculo(VinculoWhatsapp.ACTIVO);
        when(vinculos.findById(7L)).thenReturn(Optional.of(vivo));

        whatsapp.trasElEvento(evento("ACEPTADO"), Instant.now());

        verify(vinculos, never()).save(any());
        // Ni siquiera lo bloquea: una BAJA en curso puede tenerlo y esperar a este evento.
        verify(vinculos, never()).paraEscribir(any());
    }

    // ============ revalidar antes de mandar ============

    @Test
    @DisplayName("AC-16 · pasadas las 24 horas no se manda: autorizacion_caducada")
    void caducado() {
        EventoWhatsapp viejo = evento("PENDIENTE");
        viejo.setAvisoCaducaEn(Instant.now().minusSeconds(1));

        assertThat(whatsapp.motivoParaNoEnviar(viejo, Instant.now())).isEqualTo("autorizacion_caducada");
    }

    @Test
    @DisplayName("AC-15 · BAJA, acceso revocado o borrado antes del despacho: no se manda")
    void revalidar() {
        EventoWhatsapp pendiente = evento("PENDIENTE");
        when(bajas.existsById(NUMERO)).thenReturn(true);
        assertThat(whatsapp.motivoParaNoEnviar(pendiente, Instant.now())).isEqualTo("suprimido");

        when(bajas.existsById(NUMERO)).thenReturn(false);
        when(resultados.findByPostulacionId(70L)).thenReturn(Optional.of(ResultadoWhatsapp.builder()
                .postulacionId(70L).vinculoWhatsappId(7L).accesoRevocadoEn(Instant.now())
                .accesoRevocadoMotivo("CAMBIO_CELULAR").build()));
        assertThat(whatsapp.motivoParaNoEnviar(pendiente, Instant.now())).isEqualTo("revocado");

        when(resultados.findByPostulacionId(70L)).thenReturn(Optional.empty());
        assertThat(whatsapp.motivoParaNoEnviar(pendiente, Instant.now())).isEqualTo("revocado");

        when(resultados.findByPostulacionId(70L)).thenReturn(Optional.of(ResultadoWhatsapp.builder()
                .postulacionId(70L).vinculoWhatsappId(7L).build()));
        assertThat(whatsapp.motivoParaNoEnviar(pendiente, Instant.now())).isNull();
    }

    @Test
    @DisplayName("La convocatoria cerrada solo sale con el vínculo aún activo")
    void convocatoriaRevalidada() {
        EventoWhatsapp cierre = evento("PENDIENTE");
        cierre.setTipo("CONVOCATORIA_CERRADA");
        when(vinculos.findById(7L)).thenReturn(Optional.of(cerradoPorFin()));
        assertThat(whatsapp.motivoParaNoEnviar(cierre, Instant.now())).isEqualTo("revocado");

        when(vinculos.findById(7L)).thenReturn(Optional.of(vinculo(VinculoWhatsapp.ACTIVO)));
        assertThat(whatsapp.motivoParaNoEnviar(cierre, Instant.now())).isNull();
    }

    // ============ lo que retira el acceso ============

    @Test
    @DisplayName("AC-15 · la BAJA revoca los resultados del número y omite sus pendientes")
    void laBaja() {
        when(vinculos.findByTelefono(NUMERO)).thenReturn(List.of(vinculo(VinculoWhatsapp.REVOCADO)));
        Instant ahora = Instant.now();

        whatsapp.alDarseDeBaja(NUMERO, ahora);

        verify(resultados).revocarDe(List.of(7L), "BAJA", ahora);
        verify(eventos).omitirPendientesDe(List.of(7L), "suprimido", ahora);
    }

    @Test
    @DisplayName("AC-15 · otro celular: lo del anterior deja de consultarse; un aviso final ya aceptado se cancela")
    void otroCelular() {
        VinculoWhatsapp delAnterior = cerradoPorFin();
        delAnterior.setRevocacionProximoIntentoEn(null);
        VinculoWhatsapp delNuevo = vinculo(VinculoWhatsapp.ACTIVO);
        delNuevo.setId(9L);
        delNuevo.setTelefono("+51912345678");
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of(delAnterior, delNuevo));
        when(eventos.avisosFinalesVigentesDe(eq(List.of(7L)), any())).thenReturn(List.of(evento("ACEPTADO")));
        Instant ahora = Instant.now();

        whatsapp.alCambiarElCelular(33L, "+51912345678", ahora);

        verify(resultados).revocarDe(List.of(7L), "CAMBIO_CELULAR", ahora);
        verify(eventos).omitirPendientesDe(List.of(7L), "revocado", ahora);
        // Sin transacción en curso, se le cuenta al bot ya.
        verify(envio).revocarSinFila(NUMERO, "pw_ref", "CAMBIO_CELULAR");
    }

    @Test
    @DisplayName("AC-15 · otro celular con el aviso final aún sin mandar: no sale y el vínculo recupera su revocación")
    void otroCelularConPendiente() {
        VinculoWhatsapp delAnterior = cerradoPorFin();
        delAnterior.setRevocacionProximoIntentoEn(null);
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of(delAnterior));
        when(eventos.avisosFinalesVigentesDe(eq(List.of(7L)), any())).thenReturn(List.of(evento("PENDIENTE")));
        when(vinculos.paraEscribir(7L)).thenReturn(Optional.of(delAnterior));

        whatsapp.alCambiarElCelular(33L, "+51912345678", Instant.now());

        assertThat(delAnterior.getRevocacionProximoIntentoEn()).isNotNull();
        verify(envio, never()).revocarSinFila(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("AC-21 · el borrado se lleva resultados y eventos, y dice qué avisos aceptados hay que cancelar")
    void elBorrado() {
        when(eventos.avisosFinalesVigentesDe(eq(List.of(7L)), any()))
                .thenReturn(List.of(evento("ACEPTADO")));

        Set<Long> porCancelar = whatsapp.alBorrarLosDatos(List.of(7L));

        assertThat(porCancelar).containsExactly(7L);
        verify(eventos).borrarDe(List.of(7L));
        verify(resultados).borrarDe(List.of(7L));
    }
}
