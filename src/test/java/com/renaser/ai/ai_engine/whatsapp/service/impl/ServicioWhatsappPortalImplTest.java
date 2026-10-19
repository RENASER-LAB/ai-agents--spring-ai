package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.consentimiento.entity.Consentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.service.TextosDeConsentimiento;
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
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.WhatsappDelProceso;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.BetaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.EnvioAlBot;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El botón de WhatsApp del portal y su tarjeta (V73): AC-03 a AC-06, AC-12 y AC-13. */
@ExtendWith(MockitoExtension.class)
@DisplayName("La tarjeta y el botón de WhatsApp en el portal")
class ServicioWhatsappPortalImplTest {

    private static final Long RENASER = 1L;
    private static final UUID UUID_DE_ANA = UUID.fromString("00000000-0000-4000-8000-000000000070");

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
    @Mock private EnvioAlBot envio;
    @Mock private com.renaser.ai.ai_engine.whatsapp.service.ResultadosPorWhatsapp resultados;
    @Mock private PlatformTransactionManager transacciones;

    private ServicioWhatsappPortalImpl servicio;
    private final Persona ana = Persona.builder().id(33L).nombre("Ana").apellidos("Pérez")
            .telefono("+51987654321").build();

    @BeforeEach
    void montar() {
        lenient().when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(
                Organizacion.builder().id(RENASER).esPlataforma(true).build()));
        lenient().when(estados.findAllByOrderByOrden()).thenReturn(List.of(
                EstadoPostulacion.builder().codigo("PERFIL_CALIFICANDO").esFinal(false).build(),
                EstadoPostulacion.builder().codigo("NO_CONTINUA").esFinal(true).build()));
        DuenoDelInstrumento duenos = new DuenoDelInstrumento(organizaciones);
        servicio = new ServicioWhatsappPortalImpl(postulaciones, vacantes, estados, personas, vinculos,
                avisos, bajas, consentimientos, new TextosDeConsentimiento(textos, duenos), duenos,
                beta, envio, new PropiedadesWhatsapp(), resultados, transacciones);
    }

    private static ContextoUsuario ana() {
        return new ContextoUsuario(60L, 33L, RENASER, "CANDIDATO", List.of(), Map.of());
    }

    private static Postulacion suPostulacion(Long organizacion) {
        return Postulacion.builder().id(70L).uuid(UUID_DE_ANA).usuarioId(60L).vacanteId(5L)
                .organizacionId(organizacion).estadoCodigo("PERFIL_CALIFICANDO").build();
    }

    private void suyaYDeRenaser() {
        when(postulaciones.findByUuid(UUID_DE_ANA)).thenReturn(Optional.of(suPostulacion(RENASER)));
        when(beta.ofrecePara(RENASER)).thenReturn(true);
        lenient().when(postulaciones.estadoBloqueandoLaFila(70L)).thenReturn("PERFIL_CALIFICANDO");
        lenient().when(personas.findById(33L)).thenReturn(Optional.of(ana));
        lenient().when(vacantes.findById(5L)).thenReturn(Optional.of(
                Vacante.builder().id(5L).titulo("Asistente Contable").build()));
        lenient().when(textos.findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                RENASER, "WHATSAPP")).thenReturn(Optional.of(TextoConsentimiento.builder()
                .id(400L).tipo("WHATSAPP").version("1.0").build()));
        lenient().when(consentimientos.save(any(Consentimiento.class))).thenAnswer(i -> {
            Consentimiento c = i.getArgument(0);
            c.setId(500L);
            return c;
        });
        lenient().when(vinculos.saveAndFlush(any(VinculoWhatsapp.class))).thenAnswer(i -> {
            VinculoWhatsapp v = i.getArgument(0);
            v.setId(7L);
            return v;
        });
    }

    // ============ La tarjeta ============

    @Test
    @DisplayName("Los estados de la tarjeta, cada uno con el número que le toca")
    void losEstadosDeLaTarjeta() {
        Instant ahora = Instant.now();
        assertThat(ServicioWhatsappPortalImpl.tarjeta(null, false, null, ahora))
                .isEqualTo(new WhatsappDelProceso(true, "SIN_CELULAR", null));
        assertThat(ServicioWhatsappPortalImpl.tarjeta("+51987654321", false, null, ahora))
                .isEqualTo(new WhatsappDelProceso(true, "SIN_VINCULAR", "+51 987 654 321"));

        VinculoWhatsapp pendiente = VinculoWhatsapp.builder().telefono("+51987654321")
                .estado(VinculoWhatsapp.PENDIENTE).anuncioCaducaEn(ahora.plusSeconds(60)).build();
        assertThat(ServicioWhatsappPortalImpl.tarjeta("+51987654321", false, pendiente, ahora))
                .isEqualTo(new WhatsappDelProceso(true, "PENDIENTE", "+51 987 654 321"));

        // El anuncio caducó: escribir ya no vincula, así que vuelve a ofrecerse desde el principio.
        VinculoWhatsapp caducado = VinculoWhatsapp.builder().telefono("+51987654321")
                .estado(VinculoWhatsapp.PENDIENTE).anuncioCaducaEn(ahora.minusSeconds(1)).build();
        assertThat(ServicioWhatsappPortalImpl.tarjeta("+51987654321", false, caducado, ahora).estado())
                .isEqualTo("SIN_VINCULAR");

        VinculoWhatsapp activo = VinculoWhatsapp.builder().telefono("+51987654321")
                .estado(VinculoWhatsapp.ACTIVO).vinculadoEn(ahora).build();
        assertThat(ServicioWhatsappPortalImpl.tarjeta("+51987654321", false, activo, ahora))
                .isEqualTo(new WhatsappDelProceso(true, "ACTIVO", "+51 9•• ••• 321"));

        assertThat(ServicioWhatsappPortalImpl.tarjeta("+51987654321", true, null, ahora))
                .isEqualTo(new WhatsappDelProceso(true, "SUPRIMIDO", null));

        VinculoWhatsapp dejado = VinculoWhatsapp.builder().telefono("+51987654321")
                .estado(VinculoWhatsapp.REVOCADO).motivoCierre("DEJAR_DE_RECIBIR").build();
        assertThat(ServicioWhatsappPortalImpl.tarjeta("+51987654321", false, dejado, ahora).estado())
                .isEqualTo("REVOCADO");

        // AC-02 · cambiar el celular cierra el pendiente del anterior: el nuevo empieza sin vincular.
        VinculoWhatsapp delAnterior = VinculoWhatsapp.builder().telefono("+51911111111")
                .estado(VinculoWhatsapp.REVOCADO).motivoCierre("CAMBIO_CELULAR").build();
        assertThat(ServicioWhatsappPortalImpl.tarjeta("+51987654321", false, delAnterior, ahora).estado())
                .isEqualTo("SIN_VINCULAR");
    }

    @Test
    @DisplayName("AC-04 · con la beta apagada no hay tarjeta en ningún proceso")
    void sinBetaNoHayTarjeta() {
        when(beta.encendida()).thenReturn(false);

        assertThat(servicio.tarjetasDe(33L, List.of(suPostulacion(RENASER)))).isEmpty();
    }

    @Test
    @DisplayName("AC-03/AC-04 · sale en las de RENASER en curso, y no en las de otra empresa ni en las terminadas")
    void soloEnLasDeRenaserEnCurso() {
        when(beta.encendida()).thenReturn(true);
        when(personas.findById(33L)).thenReturn(Optional.of(ana));
        Postulacion deOtra = suPostulacion(2L);
        deOtra.setId(71L);
        Postulacion terminada = suPostulacion(RENASER);
        terminada.setId(72L);
        terminada.setEstadoCodigo("NO_CONTINUA");

        var tarjetas = servicio.tarjetasDe(33L, List.of(suPostulacion(RENASER), deOtra, terminada));

        assertThat(tarjetas).containsOnlyKeys(70L);
        assertThat(tarjetas.get(70L)).isEqualTo(new WhatsappDelProceso(true, "SIN_VINCULAR", "+51 987 654 321"));
    }

    // ============ El botón ============

    @Test
    @DisplayName("AC-06 · guarda el consentimiento, deja el vínculo pendiente, lo anuncia y devuelve el enlace")
    void elBoton() {
        suyaYDeRenaser();

        var resultado = servicio.escribir(ana(), UUID_DE_ANA, "10.0.0.1", "Firefox");

        assertThat(resultado.enlace()).startsWith("https://wa.me/51928439930?text=Hola%2C%20soy%20Ana%20P");
        assertThat(resultado.whatsapp()).isEqualTo(new WhatsappDelProceso(true, "PENDIENTE", "+51 987 654 321"));

        ArgumentCaptor<Consentimiento> firma = ArgumentCaptor.forClass(Consentimiento.class);
        verify(consentimientos).save(firma.capture());
        assertThat(firma.getValue().getTextoConsentimientoId()).isEqualTo(400L);
        assertThat(firma.getValue().getPostulacionId()).isEqualTo(70L);
        assertThat(firma.getValue().getIp()).isEqualTo("10.0.0.1");

        ArgumentCaptor<VinculoWhatsapp> vinculo = ArgumentCaptor.forClass(VinculoWhatsapp.class);
        verify(vinculos).saveAndFlush(vinculo.capture());
        VinculoWhatsapp guardado = vinculo.getValue();
        assertThat(guardado.getEstado()).isEqualTo("PENDIENTE");
        assertThat(guardado.getTelefono()).isEqualTo("+51987654321");
        assertThat(guardado.getConsentimientoId()).isEqualTo(500L);
        assertThat(guardado.getReferencia()).startsWith("pw_").doesNotContain(UUID_DE_ANA.toString());
        assertThat(guardado.getAnuncioCaducaEn()).isAfter(Instant.now().plusSeconds(23 * 3600));
        // Ya confirmado, se anuncia al bot.
        verify(envio).anunciarAhora(7L);
    }

    @Test
    @DisplayName("Doble clic: renueva el mismo anuncio y no firma dos veces")
    void dobleClic() {
        suyaYDeRenaser();
        VinculoWhatsapp vivo = VinculoWhatsapp.builder().id(7L).postulacionId(70L).referencia("pw_ya")
                .telefono("+51987654321").estado("PENDIENTE").consentimientoId(500L)
                .anunciadoEn(Instant.now().minusSeconds(3600))
                .anuncioCaducaEn(Instant.now().plusSeconds(3600)).build();
        when(vinculos.vivoParaEscribir(70L)).thenReturn(Optional.of(vivo));
        when(consentimientos.findFirstByPersonaIdAndPostulacionIdAndTextoConsentimientoId(33L, 70L, 400L))
                .thenReturn(Optional.of(Consentimiento.builder().id(500L).build()));

        servicio.escribir(ana(), UUID_DE_ANA, "10.0.0.1", "Firefox");

        verify(consentimientos, never()).save(any());
        assertThat(vivo.getReferencia()).isEqualTo("pw_ya");
        assertThat(vivo.getAnuncioCaducaEn()).isAfter(Instant.now().plusSeconds(23 * 3600));
        verify(envio).anunciarAhora(7L);
    }

    @Test
    @DisplayName("AC-05 · sin celular en la cuenta: 409 SIN_CELULAR, y el portal lo pide")
    void sinCelular() {
        suyaYDeRenaser();
        ana.setTelefono(null);

        assertThatThrownBy(() -> servicio.escribir(ana(), UUID_DE_ANA, "10.0.0.1", "Firefox"))
                .isInstanceOf(WhatsappNoDisponibleException.class)
                .extracting(e -> ((WhatsappNoDisponibleException) e).getCodigo())
                .isEqualTo("SIN_CELULAR");
        verify(vinculos, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("AC-04 · en una vacante de otra empresa o con la beta apagada: 409 y nada se anuncia")
    void otraEmpresa() {
        when(postulaciones.findByUuid(UUID_DE_ANA)).thenReturn(Optional.of(suPostulacion(2L)));
        when(beta.ofrecePara(2L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.escribir(ana(), UUID_DE_ANA, "10.0.0.1", "Firefox"))
                .isInstanceOf(WhatsappNoDisponibleException.class)
                .extracting(e -> ((WhatsappNoDisponibleException) e).getCodigo())
                .isEqualTo("NO_DISPONIBLE");
        verify(envio, never()).anunciarAhora(any());
    }

    @Test
    @DisplayName("AC-13 · un número dado de baja no se vuelve a ofrecer")
    void numeroDadoDeBaja() {
        suyaYDeRenaser();
        when(bajas.existsById("+51987654321")).thenReturn(true);

        assertThatThrownBy(() -> servicio.escribir(ana(), UUID_DE_ANA, "10.0.0.1", "Firefox"))
                .isInstanceOf(WhatsappNoDisponibleException.class)
                .extracting(e -> ((WhatsappNoDisponibleException) e).getCodigo())
                .isEqualTo("SUPRIMIDO");
    }

    @Test
    @DisplayName("El proceso terminado no anuncia nada")
    void procesoTerminado() {
        suyaYDeRenaser();
        when(postulaciones.estadoBloqueandoLaFila(70L)).thenReturn("NO_CONTINUA");

        assertThatThrownBy(() -> servicio.escribir(ana(), UUID_DE_ANA, "10.0.0.1", "Firefox"))
                .isInstanceOf(WhatsappNoDisponibleException.class)
                .extracting(e -> ((WhatsappNoDisponibleException) e).getCodigo())
                .isEqualTo("PROCESO_TERMINADO");
    }

    @Test
    @DisplayName("Una postulación de otra persona responde 404")
    void deOtraPersona() {
        Postulacion ajena = suPostulacion(RENASER);
        ajena.setUsuarioId(61L);
        when(postulaciones.findByUuid(UUID_DE_ANA)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> servicio.escribir(ana(), UUID_DE_ANA, "10.0.0.1", "Firefox"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============ Dejar de recibir y otro celular ============

    @Test
    @DisplayName("AC-12 · «Dejar de recibir» cierra el vínculo y se lo cuenta al bot")
    void dejarDeRecibir() {
        when(postulaciones.findByUuid(UUID_DE_ANA)).thenReturn(Optional.of(suPostulacion(RENASER)));
        VinculoWhatsapp activo = VinculoWhatsapp.builder().id(7L).postulacionId(70L)
                .telefono("+51987654321").estado("ACTIVO").vinculadoEn(Instant.now()).build();
        when(vinculos.vivoParaEscribir(70L)).thenReturn(Optional.of(activo));

        servicio.dejarDeRecibir(ana(), UUID_DE_ANA);

        assertThat(activo.getEstado()).isEqualTo("REVOCADO");
        assertThat(activo.getMotivoCierre()).isEqualTo("DEJAR_DE_RECIBIR");
        verify(avisos).omitirPendientesDe(List.of(7L), activo.getCerradoEn());
        verify(envio).revocarAhora(7L);
    }

    @Test
    @DisplayName("AC-02 · cambiar el celular cierra lo vivo del anterior y lo revoca")
    void otroCelular() {
        VinculoWhatsapp activo = VinculoWhatsapp.builder().id(7L).postulacionId(70L)
                .telefono("+51987654321").estado("ACTIVO").vinculadoEn(Instant.now()).build();
        VinculoWhatsapp pendiente = VinculoWhatsapp.builder().id(8L).postulacionId(71L)
                .telefono("+51987654321").estado("PENDIENTE").build();
        when(vinculos.vivosDeLaPersonaParaEscribir(33L)).thenReturn(List.of(activo, pendiente));

        servicio.alCambiarElCelular(33L, "+51987654321", "+51912345678");

        assertThat(activo.getMotivoCierre()).isEqualTo("CAMBIO_CELULAR");
        assertThat(pendiente.getMotivoCierre()).isEqualTo("CAMBIO_CELULAR");
        verify(envio).revocarAhora(7L);
        verify(envio).revocarAhora(8L);
        // V76 · lo ya terminado por el número anterior deja de consultarse.
        verify(resultados).alCambiarElCelular(org.mockito.ArgumentMatchers.eq(33L),
                org.mockito.ArgumentMatchers.eq("+51912345678"), any());
    }

    @Test
    @DisplayName("V76 · cambiar el celular sin nada vivo también retira lo ya terminado del anterior")
    void otroCelularSinNadaVivo() {
        when(vinculos.vivosDeLaPersonaParaEscribir(33L)).thenReturn(List.of());

        servicio.alCambiarElCelular(33L, "+51987654321", "+51912345678");

        verify(resultados).alCambiarElCelular(org.mockito.ArgumentMatchers.eq(33L),
                org.mockito.ArgumentMatchers.eq("+51912345678"), any());
        verify(envio, never()).revocarAhora(any());
    }
}
