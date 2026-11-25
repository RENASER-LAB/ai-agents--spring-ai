package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.consentimiento.entity.Consentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.service.TextosDeConsentimiento;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.WhatsappDeLaCuenta;
import com.renaser.ai.ai_engine.notificacion.entity.PreferenciaAviso;
import com.renaser.ai.ai_engine.notificacion.service.PreferenciasDeAviso;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.entity.BajaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.BetaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.FalloDelBot;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.Vinculacion;
import com.renaser.ai.ai_engine.whatsapp.service.EnvioAlBot;
import com.renaser.ai.ai_engine.whatsapp.service.InterruptorWhatsapp.Activacion;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.WhatsappNoActivadoException;
import com.renaser.ai.ai_engine.whatsapp.service.WhatsappNoDisponibleException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El interruptor de WhatsApp de la cuenta (V92, specs/Ready/notificaciones-del-candidato): sus
 * estados, encenderlo con un clic o con mensaje, apagarlo y la postulación nueva con él encendido.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El interruptor de WhatsApp de la cuenta")
class InterruptorWhatsappImplTest {

    private static final long RENASER = 1L;
    private static final long OTRA_EMPRESA = 2L;
    private static final String NUMERO = "+51987654321";

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private EstadoPostulacionRepository estados;
    @Mock private PersonaRepository personas;
    @Mock private VinculoWhatsappRepository vinculos;
    @Mock private AvisoWhatsappRepository avisos;
    @Mock private BajaWhatsappRepository bajas;
    @Mock private ConsentimientoRepository consentimientos;
    @Mock private TextoConsentimientoRepository textos;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private BetaWhatsapp beta;
    @Mock private ClienteConversacion cliente;
    @Mock private EnvioAlBot envio;
    @Mock private PreferenciasDeAviso preferencias;
    @Mock private PlatformTransactionManager transacciones;

    private InterruptorWhatsappImpl interruptor;
    private final Persona lucia = Persona.builder().id(33L).nombre("Lucía").apellidos("Paredes")
            .telefono(NUMERO).build();
    private final List<VinculoWhatsapp> guardados = new ArrayList<>();
    private long siguienteId = 100L;

    @BeforeEach
    void montar() {
        lenient().when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(
                Organizacion.builder().id(RENASER).esPlataforma(true).build()));
        lenient().when(estados.findAllByOrderByOrden()).thenReturn(List.of(
                EstadoPostulacion.builder().codigo("PERFIL_CALIFICANDO").esFinal(false).build(),
                EstadoPostulacion.builder().codigo("NO_CONTINUA").esFinal(true).build()));
        lenient().when(beta.encendida()).thenReturn(true);
        lenient().when(beta.esDeLaPlataforma(RENASER)).thenReturn(true);
        lenient().when(personas.findById(33L)).thenReturn(Optional.of(lucia));
        lenient().when(textos.findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                RENASER, "WHATSAPP_CUENTA")).thenReturn(Optional.of(textoDeLaCuenta()));
        lenient().when(consentimientos.save(any(Consentimiento.class))).thenAnswer(i -> {
            Consentimiento c = i.getArgument(0);
            c.setId(500L);
            return c;
        });
        lenient().when(vinculos.saveAndFlush(any(VinculoWhatsapp.class))).thenAnswer(i -> guardar(i.getArgument(0)));
        lenient().when(vinculos.save(any(VinculoWhatsapp.class))).thenAnswer(i -> guardar(i.getArgument(0)));
        lenient().when(vacantes.findAllById(any())).thenReturn(List.of(
                Vacante.builder().id(5L).titulo("Analista de Datos").build(),
                Vacante.builder().id(6L).titulo("Analista de Planeamiento").build()));
        DuenoDelInstrumento duenos = new DuenoDelInstrumento(organizaciones);
        interruptor = new InterruptorWhatsappImpl(postulaciones, vacantes, estados, personas, vinculos,
                avisos, bajas, consentimientos, textos, new TextosDeConsentimiento(textos, duenos), duenos,
                beta, cliente, envio, new PropiedadesWhatsapp(), preferencias, transacciones);
    }

    private VinculoWhatsapp guardar(VinculoWhatsapp v) {
        if (v.getId() == null) {
            v.setId(siguienteId++);
        }
        guardados.add(v);
        return v;
    }

    private static TextoConsentimiento textoDeLaCuenta() {
        return TextoConsentimiento.builder().id(410L).tipo("WHATSAPP_CUENTA").version("1.0")
                .texto("Al activarlo aceptas recibir por WhatsApp las novedades de tus procesos. "
                        + "Escribe BAJA cuando quieras dejarlo.").build();
    }

    private static ContextoUsuario lucia() {
        return new ContextoUsuario(60L, 33L, RENASER, "CANDIDATO", List.of(), Map.of());
    }

    private static Postulacion postulacion(long id, long vacante, long organizacion, String estado) {
        return Postulacion.builder().id(id).uuid(UUID.randomUUID()).usuarioId(60L).vacanteId(vacante)
                .organizacionId(organizacion).estadoCodigo(estado).creadoEn(Instant.now().minus(Duration.ofDays(2)))
                .build();
    }

    /** Dos procesos vivos de RENASER, uno terminado y uno de otra empresa. */
    private void susProcesos() {
        when(postulaciones.findByUsuarioIdOrderByCreadoEnDesc(60L)).thenReturn(List.of(
                postulacion(70L, 5L, RENASER, "PERFIL_CALIFICANDO"),
                postulacion(71L, 6L, RENASER, "PERFIL_CALIFICANDO"),
                postulacion(72L, 5L, RENASER, "NO_CONTINUA"),
                postulacion(73L, 7L, OTRA_EMPRESA, "PERFIL_CALIFICANDO")));
    }

    /** Su número escribió alguna vez desde esta cuenta (un vínculo de antes, cerrado por la BAJA). */
    private void numeroComprobado() {
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of(VinculoWhatsapp.builder().id(9L)
                .personaId(33L).postulacionId(60L).telefono(NUMERO).estado(VinculoWhatsapp.SUPRIMIDO)
                .motivoCierre(VinculoWhatsapp.POR_BAJA).vinculadoEn(Instant.now().minus(Duration.ofDays(30)))
                .anunciadoEn(Instant.now().minus(Duration.ofDays(31))).build()));
    }

    // ============ El estado ============

    @Test
    @DisplayName("Los estados de la cuenta, en su orden, cada uno con el número que le toca")
    void losEstados() {
        Instant ahora = Instant.now();
        assertThat(InterruptorWhatsappImpl.deLaCuenta(true, null, false, List.of(), null, ahora))
                .isEqualTo(new WhatsappDeLaCuenta(true, "SIN_NUMERO", null, false, false, null, false));

        VinculoWhatsapp escribio = VinculoWhatsapp.builder().id(1L).telefono(NUMERO).estado("ACTIVO")
                .vinculadoEn(ahora.minus(Duration.ofDays(3))).anunciadoEn(ahora.minus(Duration.ofDays(3))).build();
        WhatsappDeLaCuenta activo = InterruptorWhatsappImpl.deLaCuenta(true, NUMERO, true,
                List.of(escribio), null, ahora);
        assertThat(activo.estado()).isEqualTo("ACTIVO");
        assertThat(activo.numero()).isEqualTo("+51 9•• ••• 321");
        assertThat(activo.comprobado()).isTrue();

        VinculoWhatsapp esperando = VinculoWhatsapp.builder().id(2L).telefono(NUMERO).estado("PENDIENTE")
                .anunciadoEn(ahora.minus(Duration.ofHours(1))).anuncioCaducaEn(ahora.plus(Duration.ofHours(23)))
                .build();
        WhatsappDeLaCuenta espera = InterruptorWhatsappImpl.deLaCuenta(true, NUMERO, false,
                List.of(esperando), null, ahora);
        assertThat(espera.estado()).isEqualTo("ESPERANDO");
        assertThat(espera.numero()).isEqualTo("+51 987 654 321");
        assertThat(espera.comprobado()).isFalse();

        // AC-04 · pasaron las 24 horas sin mensaje.
        VinculoWhatsapp caducado = VinculoWhatsapp.builder().id(3L).telefono(NUMERO).estado("PENDIENTE")
                .anunciadoEn(ahora.minus(Duration.ofHours(25))).anuncioCaducaEn(ahora.minus(Duration.ofHours(1)))
                .build();
        assertThat(InterruptorWhatsappImpl.deLaCuenta(true, NUMERO, false, List.of(caducado), null, ahora)
                .estado()).isEqualTo("NO_LLEGO");

        // Lo apagó desde el portal: el último está cerrado, y el número sigue comprobado. Un
        // anuncio anterior que caducó ya no cuenta: manda el último.
        VinculoWhatsapp antiguo = VinculoWhatsapp.builder().id(5L).telefono(NUMERO).estado("PENDIENTE")
                .anunciadoEn(ahora.minus(Duration.ofDays(5))).anuncioCaducaEn(ahora.minus(Duration.ofDays(4)))
                .build();
        VinculoWhatsapp dejado = VinculoWhatsapp.builder().id(4L).telefono(NUMERO).estado("REVOCADO")
                .motivoCierre("DEJAR_DE_RECIBIR").vinculadoEn(ahora.minus(Duration.ofDays(2)))
                .anunciadoEn(ahora.minus(Duration.ofDays(2))).build();
        WhatsappDeLaCuenta apagado = InterruptorWhatsappImpl.deLaCuenta(true, NUMERO, false,
                List.of(antiguo, dejado), null, ahora);
        assertThat(apagado.estado()).isEqualTo("APAGADO");
        assertThat(apagado.comprobado()).isTrue();
        assertThat(apagado.porBaja()).isFalse();
    }

    @Test
    @DisplayName("Por BAJA dice cuándo (día de Lima); con un número nuevo sin comprobar, que cambió")
    void bajaYCambioDeNumero() {
        Instant ahora = Instant.now();
        Instant dada = ahora.minus(Duration.ofDays(12));
        VinculoWhatsapp suprimido = VinculoWhatsapp.builder().id(1L).telefono(NUMERO).estado("SUPRIMIDO")
                .motivoCierre("BAJA").vinculadoEn(dada.minus(Duration.ofDays(1))).anunciadoEn(dada).build();
        WhatsappDeLaCuenta porBaja = InterruptorWhatsappImpl.deLaCuenta(true, NUMERO, false, List.of(suprimido),
                BajaWhatsapp.builder().telefono(NUMERO).dadaEn(dada).build(), ahora);
        assertThat(porBaja.estado()).isEqualTo("APAGADO");
        assertThat(porBaja.porBaja()).isTrue();
        assertThat(porBaja.bajaEn()).isEqualTo(LocalDate.ofInstant(dada, java.time.ZoneId.of("America/Lima")));
        assertThat(porBaja.comprobado()).isTrue();
        assertThat(porBaja.cambioDeNumero()).isFalse();

        VinculoWhatsapp delAnterior = VinculoWhatsapp.builder().id(2L).telefono("+51911111111").estado("REVOCADO")
                .motivoCierre("CAMBIO_CELULAR").vinculadoEn(ahora.minus(Duration.ofDays(5)))
                .anunciadoEn(ahora.minus(Duration.ofDays(5))).build();
        WhatsappDeLaCuenta cambio = InterruptorWhatsappImpl.deLaCuenta(true, NUMERO, false, List.of(delAnterior),
                null, ahora);
        assertThat(cambio.estado()).isEqualTo("APAGADO");
        assertThat(cambio.cambioDeNumero()).isTrue();
        assertThat(cambio.comprobado()).isFalse();
    }

    @Test
    @DisplayName("El estado cuenta los procesos vivos de la beta y la última postulación")
    void elEstadoConSusProcesos() {
        susProcesos();
        when(preferencias.de(33L)).thenReturn(PreferenciaAviso.porDefecto(33L));

        var estado = interruptor.estado(33L, 60L);

        assertThat(estado.whatsapp().estado()).isEqualTo("APAGADO");
        assertThat(estado.hayProcesoVivo()).isTrue();
        assertThat(estado.ultimaPostulacion()).isNotNull();
    }

    // ============ Encender con un clic ============

    @Test
    @DisplayName("AC-05 · tras la BAJA, con el número comprobado: ACTIVACION, sale de la baja y sus procesos vivos quedan ACTIVO sin abrir el chat")
    void conUnClicTrasLaBaja() {
        susProcesos();
        numeroComprobado();
        when(cliente.vincular(any())).thenAnswer(i -> ((Vinculacion) i.getArgument(0)).postulaciones().stream()
                .map(ClienteConversacion.PostulacionAVincular::postulacionRef).toList());
        when(bajas.existsById(NUMERO)).thenReturn(true);

        Activacion hecho = interruptor.activar(lucia(), "10.0.0.1", "Firefox");

        assertThat(hecho.resultado()).isEqualTo(DtosNotificaciones.ACTIVADO);
        assertThat(hecho.enlace()).isNull();
        assertThat(hecho.confirmacionEnviada()).isTrue();
        ArgumentCaptor<Vinculacion> pedido = ArgumentCaptor.forClass(Vinculacion.class);
        verify(cliente).vincular(pedido.capture());
        assertThat(pedido.getValue().motivo()).isEqualTo("ACTIVACION");
        assertThat(pedido.getValue().telefono()).isEqualTo(NUMERO);
        assertThat(pedido.getValue().nombre()).isEqualTo("Lucía");
        // Solo los vivos de RENASER: ni el terminado ni el de otra empresa.
        assertThat(pedido.getValue().postulaciones()).hasSize(2)
                .extracting(ClienteConversacion.PostulacionAVincular::vacante)
                .containsExactly("Analista de Datos", "Analista de Planeamiento");
        assertThat(pedido.getValue().consentimiento().version()).isEqualTo("1.0");
        assertThat(pedido.getValue().consentimiento().texto()).startsWith("Al activarlo aceptas");
        // El cuerpo lleva los campos del contrato y ninguno más.
        assertThat(pedido.getValue().comoCuerpo()).containsOnlyKeys("telefono", "nombre", "motivo",
                "postulaciones", "consentimiento");

        verify(bajas).deleteById(NUMERO);
        assertThat(guardados).hasSize(2).allSatisfy(v -> {
            assertThat(v.getEstado()).isEqualTo(VinculoWhatsapp.ACTIVO);
            assertThat(v.getVinculadoEn()).isNotNull();
            assertThat(v.getConsentimientoId()).isEqualTo(500L);
            assertThat(v.getTelefono()).isEqualTo(NUMERO);
        });
        assertThat(guardados).extracting(VinculoWhatsapp::getPostulacionId).containsExactlyInAnyOrder(70L, 71L);
        verify(preferencias).whatsapp(33L, true);
        verify(preferencias).bloquear(33L);
        verify(envio, never()).anunciarAhora(anyLong());

        // La firma: WHATSAPP_CUENTA, sin postulación, con la IP y el navegador.
        ArgumentCaptor<Consentimiento> firma = ArgumentCaptor.forClass(Consentimiento.class);
        verify(consentimientos).save(firma.capture());
        assertThat(firma.getValue().getTextoConsentimientoId()).isEqualTo(410L);
        assertThat(firma.getValue().getPostulacionId()).isNull();
        assertThat(firma.getValue().getIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Doble clic: la firma de la cuenta se reutiliza, una por persona y versión")
    void unaFirmaPorPersona() {
        susProcesos();
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of());
        when(consentimientos.findFirstByPersonaIdAndPostulacionIdIsNullAndTextoConsentimientoId(33L, 410L))
                .thenReturn(Optional.of(Consentimiento.builder().id(499L).personaId(33L).build()));

        interruptor.activar(lucia(), "10.0.0.1", "Firefox");

        verify(consentimientos, never()).save(any(Consentimiento.class));
        assertThat(guardados).allSatisfy(v -> assertThat(v.getConsentimientoId()).isEqualTo(499L));
    }

    @Test
    @DisplayName("AC-13 · si el bot falla, no sale de la baja, no se crean vínculos ni se enciende")
    void siElBotFallaNoCambiaNada() {
        susProcesos();
        numeroComprobado();
        when(cliente.vincular(any())).thenThrow(new FalloDelBot("El bot no respondió", true));

        assertThatThrownBy(() -> interruptor.activar(lucia(), "10.0.0.1", "Firefox"))
                .isInstanceOf(WhatsappNoActivadoException.class)
                .hasMessage("No pudimos activarlo. Vuelve a intentarlo en un momento.");

        verify(bajas, never()).deleteById(anyString());
        assertThat(guardados).isEmpty();
        verify(preferencias, never()).whatsapp(anyLong(), eq(true));
        verify(envio, never()).anunciarAhora(anyLong());
    }

    @Test
    @DisplayName("Con un clic y el bot no conoce el número (404 SIN_CONVERSACION): cae al camino con mensaje")
    void sinConversacionCaeAlMensaje() {
        susProcesos();
        numeroComprobado();
        when(cliente.vincular(any())).thenThrow(new FalloDelBot("404 SIN_CONVERSACION", false, "SIN_CONVERSACION"));

        Activacion hecho = interruptor.activar(lucia(), "10.0.0.1", "Firefox");

        assertThat(hecho.resultado()).isEqualTo(DtosNotificaciones.POR_MENSAJE);
        assertThat(hecho.enlace()).startsWith("https://wa.me/51928439930?text=");
        assertThat(guardados).hasSize(2).allSatisfy(v -> assertThat(v.getEstado()).isEqualTo("PENDIENTE"));
        verify(envio, times(2)).anunciarAhora(anyLong());
        verify(bajas, never()).deleteById(anyString());
        verify(preferencias, never()).whatsapp(anyLong(), eq(true));
    }

    @Test
    @DisplayName("Con el número comprobado y sin procesos vivos: queda encendido para los que vengan, sin llamar al bot")
    void conUnClicSinProcesos() {
        when(postulaciones.findByUsuarioIdOrderByCreadoEnDesc(60L)).thenReturn(List.of(
                postulacion(72L, 5L, RENASER, "NO_CONTINUA")));
        numeroComprobado();

        Activacion hecho = interruptor.activar(lucia(), "10.0.0.1", "Firefox");

        assertThat(hecho.resultado()).isEqualTo(DtosNotificaciones.ACTIVADO);
        assertThat(hecho.confirmacionEnviada()).isFalse();
        verify(cliente, never()).vincular(any());
        verify(preferencias).whatsapp(33L, true);
    }

    // ============ Encender con mensaje ============

    @Test
    @DisplayName("AC-02 · sin comprobar: firma WHATSAPP_CUENTA y un vínculo PENDIENTE de 24 h por cada proceso vivo, y el enlace al chat")
    void sinComprobarAnunciaCadaProceso() {
        susProcesos();
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of());

        Activacion hecho = interruptor.activar(lucia(), "10.0.0.1", "Firefox");

        assertThat(hecho.resultado()).isEqualTo(DtosNotificaciones.POR_MENSAJE);
        assertThat(hecho.enlace()).startsWith("https://wa.me/");
        assertThat(guardados).hasSize(2).allSatisfy(v -> {
            assertThat(v.getEstado()).isEqualTo("PENDIENTE");
            assertThat(v.getConsentimientoId()).isEqualTo(500L);
            assertThat(v.getAnuncioCaducaEn()).isAfter(Instant.now().plus(Duration.ofHours(23)));
            assertThat(v.getReferencia()).startsWith("pw_");
        });
        assertThat(guardados).extracting(VinculoWhatsapp::getPostulacionId).containsExactly(70L, 71L);
        verify(cliente, never()).vincular(any());
        verify(envio, times(2)).anunciarAhora(anyLong());
        verify(preferencias, never()).whatsapp(anyLong(), eq(true));
    }

    @Test
    @DisplayName("Reintentar renueva el anuncio de la misma fila, no crea otra")
    void reintentarRenueva() {
        susProcesos();
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of());
        VinculoWhatsapp caducado = VinculoWhatsapp.builder().id(40L).postulacionId(70L).personaId(33L)
                .referencia("pw_viejo").telefono(NUMERO).estado("PENDIENTE")
                .anunciadoEn(Instant.now().minus(Duration.ofHours(30)))
                .anuncioCaducaEn(Instant.now().minus(Duration.ofHours(6))).build();
        when(vinculos.vivoParaEscribir(70L)).thenReturn(Optional.of(caducado));

        interruptor.activar(lucia(), "10.0.0.1", "Firefox");

        assertThat(caducado.getAnuncioCaducaEn()).isAfter(Instant.now());
        assertThat(guardados).extracting(VinculoWhatsapp::getReferencia).contains("pw_viejo");
        assertThat(guardados).hasSize(2);
    }

    @Test
    @DisplayName("Sin procesos vivos y sin comprobar: se anuncia el número solo, sin postulación")
    void sinProcesosAnunciaElNumeroSolo() {
        when(postulaciones.findByUsuarioIdOrderByCreadoEnDesc(60L)).thenReturn(List.of());
        when(vinculos.findByPersonaId(33L)).thenReturn(List.of());

        Activacion hecho = interruptor.activar(lucia(), "10.0.0.1", "Firefox");

        assertThat(hecho.resultado()).isEqualTo(DtosNotificaciones.POR_MENSAJE);
        assertThat(guardados).singleElement().satisfies(v -> {
            assertThat(v.getPostulacionId()).isNull();
            assertThat(v.getEstado()).isEqualTo("PENDIENTE");
        });
        verify(envio).anunciarAhora(anyLong());
    }

    @Test
    @DisplayName("Sin celular en la cuenta: 409 SIN_CELULAR, y no se firma nada")
    void sinCelular() {
        when(personas.findById(33L)).thenReturn(Optional.of(Persona.builder().id(33L).nombre("Lucía").build()));

        assertThatThrownBy(() -> interruptor.activar(lucia(), "10.0.0.1", "Firefox"))
                .isInstanceOf(WhatsappNoDisponibleException.class)
                .extracting("codigo").isEqualTo("SIN_CELULAR");
        verify(consentimientos, never()).save(any());
    }

    @Test
    @DisplayName("Con la beta apagada: 409 NO_DISPONIBLE")
    void betaApagada() {
        when(beta.encendida()).thenReturn(false);

        assertThatThrownBy(() -> interruptor.activar(lucia(), "10.0.0.1", "Firefox"))
                .isInstanceOf(WhatsappNoDisponibleException.class)
                .extracting("codigo").isEqualTo("NO_DISPONIBLE");
    }

    // ============ Apagar ============

    @Test
    @DisplayName("AC-07 · apagarlo cierra todos sus vínculos con DEJAR_DE_RECIBIR, omite sus avisos y se lo cuenta al bot")
    void apagar() {
        VinculoWhatsapp activo = VinculoWhatsapp.builder().id(80L).postulacionId(70L).telefono(NUMERO)
                .estado("ACTIVO").vinculadoEn(Instant.now()).build();
        VinculoWhatsapp pendiente = VinculoWhatsapp.builder().id(81L).postulacionId(71L).telefono(NUMERO)
                .estado("PENDIENTE").build();
        when(vinculos.vivosDeLaPersonaParaEscribir(33L)).thenReturn(List.of(activo, pendiente));

        interruptor.apagar(lucia());

        assertThat(List.of(activo, pendiente)).allSatisfy(v -> {
            assertThat(v.getEstado()).isEqualTo("REVOCADO");
            assertThat(v.getMotivoCierre()).isEqualTo("DEJAR_DE_RECIBIR");
        });
        verify(avisos).omitirPendientesDe(eq(List.of(80L, 81L)), any());
        verify(preferencias).whatsapp(33L, false);
        verify(envio).revocarAhora(80L);
        verify(envio).revocarAhora(81L);
    }

    // ============ Postular con el interruptor encendido ============

    private static final Postulacion LA_NUEVA = postulacion(74L, 5L, RENASER, "PERFIL_TURNO_CANDIDATO");

    private void encendidoYConFirma() {
        when(preferencias.de(33L)).thenReturn(PreferenciaAviso.builder().personaId(33L).correo(true)
                .portal(true).whatsappActivo(true).build());
        lenient().when(consentimientos.deTipoMasRecientes(33L, "WHATSAPP_CUENTA")).thenReturn(List.of(
                Consentimiento.builder().id(500L).personaId(33L).textoConsentimientoId(410L)
                        .aceptadoEn(Instant.now().minus(Duration.ofDays(1))).build()));
        lenient().when(textos.findById(410L)).thenReturn(Optional.of(textoDeLaCuenta()));
    }

    @Test
    @DisplayName("AC-06 · con WhatsApp activo, la postulación nueva queda vinculada sola, con NUEVA_POSTULACION")
    void laPostulacionNuevaSeVincula() {
        encendidoYConFirma();
        when(cliente.vincular(any())).thenAnswer(i -> ((Vinculacion) i.getArgument(0)).postulaciones().stream()
                .map(ClienteConversacion.PostulacionAVincular::postulacionRef).toList());
        when(vinculos.paraEscribir(anyLong())).thenAnswer(i -> guardados.stream()
                .filter(v -> v.getId().equals(i.getArgument(0))).findFirst());

        interruptor.alPostular(33L, LA_NUEVA);

        ArgumentCaptor<Vinculacion> pedido = ArgumentCaptor.forClass(Vinculacion.class);
        verify(cliente).vincular(pedido.capture());
        assertThat(pedido.getValue().motivo()).isEqualTo("NUEVA_POSTULACION");
        assertThat(pedido.getValue().postulaciones()).singleElement()
                .satisfies(p -> assertThat(p.vacante()).isEqualTo("Analista de Datos"));
        VinculoWhatsapp nuevo = guardados.get(0);
        assertThat(nuevo.getPostulacionId()).isEqualTo(74L);
        assertThat(nuevo.getEstado()).isEqualTo("ACTIVO");
        assertThat(nuevo.getVinculadoEn()).isNotNull();
        assertThat(nuevo.getAnuncioProximoIntentoEn()).isNull();
    }

    @Test
    @DisplayName("Si el bot falla al vincular la nueva, queda PENDIENTE para el sondeo y la postulación sigue")
    void laNuevaSinBotQuedaPendiente() {
        encendidoYConFirma();
        when(cliente.vincular(any())).thenThrow(new FalloDelBot("El bot no respondió", true));

        interruptor.alPostular(33L, LA_NUEVA);

        assertThat(guardados).singleElement().satisfies(v -> {
            assertThat(v.getEstado()).isEqualTo("PENDIENTE");
            assertThat(v.getAnuncioProximoIntentoEn()).isNotNull();
        });
    }

    @Test
    @DisplayName("Encendido sin procesos con la BAJA puesta: la próxima postulación es su ACTIVACION y la levanta")
    void laNuevaConLaBajaEsLaActivacion() {
        encendidoYConFirma();
        when(bajas.existsById(NUMERO)).thenReturn(true);
        when(cliente.vincular(any())).thenAnswer(i -> ((Vinculacion) i.getArgument(0)).postulaciones().stream()
                .map(ClienteConversacion.PostulacionAVincular::postulacionRef).toList());
        when(vinculos.paraEscribir(anyLong())).thenAnswer(i -> guardados.stream()
                .filter(v -> v.getId().equals(i.getArgument(0))).findFirst());

        interruptor.alPostular(33L, LA_NUEVA);

        ArgumentCaptor<Vinculacion> pedido = ArgumentCaptor.forClass(Vinculacion.class);
        verify(cliente).vincular(pedido.capture());
        assertThat(pedido.getValue().motivo()).isEqualTo("ACTIVACION");
        verify(bajas).deleteById(NUMERO);
    }

    @Test
    @DisplayName("Con el interruptor apagado, postular no vincula nada")
    void apagadoNoVincula() {
        when(preferencias.de(33L)).thenReturn(PreferenciaAviso.porDefecto(33L));

        interruptor.alPostular(33L, LA_NUEVA);

        verify(vinculos, never()).saveAndFlush(any());
        verify(cliente, never()).vincular(any());
    }

    @Test
    @DisplayName("Quien encendió WhatsApp proceso por proceso, antes de la V92, se vincula con su último consentimiento de proceso")
    void conElConsentimientoDeAntes() {
        encendidoYConFirma();
        when(consentimientos.deTipoMasRecientes(33L, "WHATSAPP_CUENTA")).thenReturn(List.of());
        when(consentimientos.deTipoMasRecientes(33L, "WHATSAPP")).thenReturn(List.of(
                Consentimiento.builder().id(300L).personaId(33L).textoConsentimientoId(400L)
                        .aceptadoEn(Instant.now().minus(Duration.ofDays(40))).build()));
        when(textos.findById(400L)).thenReturn(Optional.of(TextoConsentimiento.builder().id(400L)
                .tipo("WHATSAPP").version("1.0").texto("Al escribirnos aceptas…").build()));
        when(cliente.vincular(any())).thenReturn(List.of());

        interruptor.alPostular(33L, LA_NUEVA);

        ArgumentCaptor<Vinculacion> pedido = ArgumentCaptor.forClass(Vinculacion.class);
        verify(cliente).vincular(pedido.capture());
        assertThat(pedido.getValue().consentimiento().texto()).isEqualTo("Al escribirnos aceptas…");
        assertThat(guardados.get(0).getConsentimientoId()).isEqualTo(300L);
    }
}
