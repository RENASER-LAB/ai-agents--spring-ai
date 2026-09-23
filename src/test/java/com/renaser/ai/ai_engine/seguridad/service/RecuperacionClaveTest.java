package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreo;
import com.renaser.ai.ai_engine.notificacion.service.EnviadorCorreo;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.service.ServicioRecuperacionClave.Publico;
import com.renaser.ai.ai_engine.seguridad.service.impl.ServicioRecuperacionClaveImpl;
import com.renaser.ai.ai_engine.usuario.entity.RecuperacionClave;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.RecuperacionClaveRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * «Me olvidé mi contraseña», con dobles: las reglas que deciden quién recibe un enlace, qué
 * se guarda de él y cuándo deja de servir.
 *
 * <p>Las tres cosas que esta clase defiende por encima de todo:
 * <ul>
 *   <li><b>Nada distingue «existe» de «no existe».</b> Pedir el enlace no lanza ni cambia
 *       según la cuenta; un enlace que no sirve contesta el mismo texto sea cual sea el
 *       motivo.
 *   <li><b>El token no se guarda ni se audita.</b> Solo su hash, y la auditoría lleva la
 *       cuenta y el momento.
 *   <li><b>Cada enlace cae en su puerta.</b> El del equipo en {@code /admin/restablecer},
 *       lleve o no {@code renaser.panel.url} el {@code /admin}.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Recuperar la contraseña")
class RecuperacionClaveTest {

    private static final Long PLATAFORMA = 1L;
    private static final Long ACME = 2L;
    private static final Long BETA = 3L;
    private static final String CORREO = "ana@correo.pe";
    private static final String CLAVE_ACTUAL = "la-de-siempre-123";
    private static final String HASH_ACTUAL = "hash-de-la-de-siempre";

    @Mock private UsuarioRepository usuarios;
    @Mock private RecuperacionClaveRepository recuperaciones;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private DuenoDelInstrumento duenos;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioCorreo correo;
    @Mock private ServicioAuditoria auditoria;
    @Mock private IntentosLogin intentos;
    @Mock private PasswordEncoder codificador;
    @Mock private SolicitudesPorIp solicitudesPorIp;
    @Mock private ColaDeRecuperaciones cola;
    @Mock private PlatformTransactionManager transacciones;

    private ServicioRecuperacionClaveImpl servicio;
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void armar() {
        servicio = new ServicioRecuperacionClaveImpl(usuarios, recuperaciones, organizaciones, duenos,
                parametros, correo, auditoria, intentos, codificador, solicitudesPorIp, cola,
                transacciones);
        ReflectionTestUtils.setField(servicio, "urlDelPortal", "https://portal.ejemplo.test/");
        ReflectionTestUtils.setField(servicio, "urlDelPanel", "https://portal.ejemplo.test");

        lenient().when(duenos.plataforma()).thenReturn(Organizacion.builder()
                .id(PLATAFORMA).codigo("RENASER").nombre("Renaser").esPlataforma(true).build());
        // Los parámetros contestan su valor por defecto: el tercer argumento
        lenient().when(parametros.entero(anyLong(), anyString(), anyInt()))
                .thenAnswer(i -> i.getArgument(2));
        // La cola del test no espera: atiende en el acto, en el mismo hilo
        lenient().doAnswer(i -> {
            ((Runnable) i.getArgument(0)).run();
            return true;
        }).when(cola).encolar(any());
        lenient().when(solicitudesPorIp.admitir(any(), anyInt())).thenReturn(true);
        lenient().when(recuperaciones.saveAndFlush(any(RecuperacionClave.class))).thenAnswer(i -> {
            RecuperacionClave r = i.getArgument(0);
            r.setId(ids.incrementAndGet());
            return r;
        });
        lenient().when(correo.plantillaConRecambio(anyLong(), anyLong(), anyString()))
                .thenAnswer(i -> Optional.of(PlantillaCorreo.builder()
                        .organizacionId(i.getArgument(0)).codigo(i.getArgument(2)).version(1)
                        .asunto("a").cuerpo("c").esActiva(true).build()));
        lenient().when(correo.enviarCon(any(), anyLong(), anyString(), anyMap()))
                .thenReturn(EnviadorCorreo.Resultado.NO_ENVIADO);
    }

    private Usuario candidato(String correoDeLaCuenta) {
        return Usuario.builder().id(7L).organizacionId(PLATAFORMA).correo(correoDeLaCuenta)
                .contrasenaHash(HASH_ACTUAL).esEquipo(false).esActivo(true).build();
    }

    private Usuario deEquipo(Long id, Long organizacionId) {
        return Usuario.builder().id(id).organizacionId(organizacionId).correo(CORREO)
                .contrasenaHash(HASH_ACTUAL).esEquipo(true).esActivo(true).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> variablesDelCorreo() {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(correo).enviarCon(any(), anyLong(), anyString(), captor.capture());
        return captor.getValue();
    }

    private static String tokenDe(String enlace) {
        return URLDecoder.decode(enlace.substring(enlace.indexOf("token=") + 6), StandardCharsets.UTF_8);
    }

    private static String sha256(String texto) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    // ========================================================================
    // Pedir el enlace
    // ========================================================================

    @Test
    @DisplayName("AC-01 · un candidato activo con correo real recibe un enlace a /restablecer, "
            + "y se guarda el hash, nunca el token")
    void unCandidatoRecibeSuEnlace() throws Exception {
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO)).thenReturn(Optional.of(candidato(CORREO)));

        servicio.solicitar(Publico.CANDIDATO, "  Ana@Correo.PE ", "10.0.0.1");

        ArgumentCaptor<RecuperacionClave> guardada = ArgumentCaptor.forClass(RecuperacionClave.class);
        verify(recuperaciones).saveAndFlush(guardada.capture());
        Map<String, String> variables = variablesDelCorreo();
        String enlace = variables.get("enlace");
        assertThat(enlace).startsWith("https://portal.ejemplo.test/restablecer?token=");
        String token = tokenDe(enlace);
        // 32 bytes en base64 de URL sin relleno: 43 caracteres
        assertThat(token).hasSize(43);
        assertThat(guardada.getValue().getTokenHash()).isEqualTo(sha256(token)).isNotEqualTo(token);
        assertThat(guardada.getValue().getUsuarioId()).isEqualTo(7L);
        assertThat(ChronoUnit.MINUTES.between(guardada.getValue().getCreadoEn(),
                guardada.getValue().getVenceEn())).isEqualTo(60);
        assertThat(variables.get("nombre_empresa")).isEqualTo("Renaser");
        assertThat(variables.get("vence")).contains("hora de Lima");
        verify(correo).plantillaConRecambio(PLATAFORMA, PLATAFORMA, "RECUPERAR_CLAVE_CANDIDATO");
        verify(correo).enviarCon(any(), eq(7L), eq(CORREO), anyMap());
    }

    @Test
    @DisplayName("antes de crear el nuevo se apagan los anteriores, y en ese orden")
    void primeroSeInvalidanLosAnteriores() {
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO)).thenReturn(Optional.of(candidato(CORREO)));

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.1");

        var orden = inOrder(usuarios, recuperaciones);
        orden.verify(usuarios).bloquear(7L);
        orden.verify(recuperaciones).countByUsuarioIdAndCreadoEnAfter(eq(7L), any());
        orden.verify(recuperaciones).invalidarLosVivos(eq(7L), any());
        orden.verify(recuperaciones).saveAndFlush(any());
    }

    @Test
    @DisplayName("AC-18 · la auditoría lleva la cuenta y el momento, y ni el token ni su hash")
    void laAuditoriaNoLlevaElToken() {
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO)).thenReturn(Optional.of(candidato(CORREO)));

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.1");

        ArgumentCaptor<Object> valor = ArgumentCaptor.forClass(Object.class);
        verify(auditoria).registrarDelSistema(eq(PLATAFORMA), eq("solicitar_recuperacion_clave"),
                eq("usuario"), eq(7L), valor.capture());
        String token = tokenDe(variablesDelCorreo().get("enlace"));
        assertThat(valor.getValue().toString()).doesNotContain(token).doesNotContain("token");
    }

    @Test
    @DisplayName("AC-02 · un correo sin cuenta: ni enlace, ni correo, ni auditoría, y ningún error")
    void unCorreoSinCuentaNoRecibeNada() {
        when(usuarios.buscarPorCorreo(PLATAFORMA, "nadie@correo.pe")).thenReturn(Optional.empty());

        servicio.solicitar(Publico.CANDIDATO, "nadie@correo.pe", "10.0.0.1");

        verify(recuperaciones, never()).saveAndFlush(any());
        verify(correo, never()).enviarCon(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("AC-03 · una cuenta desactivada no recibe enlace")
    void unaCuentaDesactivadaNoRecibeNada() {
        Usuario desactivada = candidato(CORREO);
        desactivada.setEsActivo(false);
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO)).thenReturn(Optional.of(desactivada));

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.1");

        verify(recuperaciones, never()).saveAndFlush(any());
        verify(correo, never()).enviarCon(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-03 · una cuenta de carga masiva, con correo inventado, no recibe enlace")
    void unaCuentaDeCargaMasivaNoRecibeNada() {
        String inventado = "juan.perez@cv-convocatoria.local";
        when(usuarios.buscarPorCorreo(PLATAFORMA, inventado)).thenReturn(Optional.of(candidato(inventado)));

        servicio.solicitar(Publico.CANDIDATO, inventado, "10.0.0.1");

        verify(recuperaciones, never()).saveAndFlush(any());
        verify(correo, never()).enviarCon(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-05 · un correo de equipo pedido en el portal no recibe nada")
    void unCorreoDeEquipoEnElPortalNoRecibeNada() {
        // Un correo del equipo de la plataforma vive en la misma organización que los candidatos
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO))
                .thenReturn(Optional.of(deEquipo(9L, PLATAFORMA)));

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.1");

        verify(recuperaciones, never()).saveAndFlush(any());
        verify(correo, never()).enviarCon(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-05 · un correo de candidato pedido en el panel no recibe nada")
    void unCorreoDeCandidatoEnElPanelNoRecibeNada() {
        // La consulta del panel solo devuelve cuentas de equipo: un candidato no sale
        when(usuarios.equipoPorCorreo(CORREO)).thenReturn(List.of());

        servicio.solicitar(Publico.EQUIPO, CORREO, "10.0.0.1");

        verify(usuarios, never()).buscarPorCorreo(any(), any());
        verify(recuperaciones, never()).saveAndFlush(any());
        verify(correo, never()).enviarCon(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-04 · AC-16 · el enlace del equipo cae en /admin/restablecer aunque "
            + "renaser.panel.url no lleve el /admin")
    void elEnlaceDelEquipoCaeEnElPanel() {
        when(usuarios.equipoPorCorreo(CORREO)).thenReturn(List.of(deEquipo(9L, ACME)));
        when(organizaciones.findById(ACME)).thenReturn(Optional.of(
                Organizacion.builder().id(ACME).nombre("Acme S.A.").build()));

        servicio.solicitar(Publico.EQUIPO, CORREO, "10.0.0.1");

        Map<String, String> variables = variablesDelCorreo();
        assertThat(variables.get("enlace"))
                .startsWith("https://portal.ejemplo.test/admin/restablecer?token=");
        assertThat(variables.get("nombre_empresa")).isEqualTo("Acme S.A.");
        verify(correo).plantillaConRecambio(ACME, PLATAFORMA, "RECUPERAR_CLAVE_EQUIPO");
    }

    @Test
    @DisplayName("AC-16 · con el /admin ya puesto en renaser.panel.url no se duplica")
    void conElAdminPuestoNoSeDuplica() {
        ReflectionTestUtils.setField(servicio, "urlDelPanel", "https://portal.ejemplo.test/admin/");
        when(usuarios.equipoPorCorreo(CORREO)).thenReturn(List.of(deEquipo(9L, ACME)));
        when(organizaciones.findById(ACME)).thenReturn(Optional.of(
                Organizacion.builder().id(ACME).nombre("Acme S.A.").build()));

        servicio.solicitar(Publico.EQUIPO, CORREO, "10.0.0.1");

        assertThat(variablesDelCorreo().get("enlace"))
                .startsWith("https://portal.ejemplo.test/admin/restablecer?token=")
                .doesNotContain("/admin/admin");
    }

    @Test
    @DisplayName("AC-17 · un correo con cuenta de equipo en dos empresas recibe dos enlaces, "
            + "cada uno con su empresa y su propio token")
    void dosEmpresasDosEnlaces() {
        when(usuarios.equipoPorCorreo(CORREO)).thenReturn(List.of(deEquipo(9L, ACME), deEquipo(10L, BETA)));
        when(organizaciones.findById(ACME)).thenReturn(Optional.of(
                Organizacion.builder().id(ACME).nombre("Acme S.A.").build()));
        when(organizaciones.findById(BETA)).thenReturn(Optional.of(
                Organizacion.builder().id(BETA).nombre("Beta S.A.C.").build()));

        servicio.solicitar(Publico.EQUIPO, CORREO, "10.0.0.1");

        ArgumentCaptor<RecuperacionClave> guardadas = ArgumentCaptor.forClass(RecuperacionClave.class);
        verify(recuperaciones, times(2)).saveAndFlush(guardadas.capture());
        assertThat(guardadas.getAllValues()).extracting(RecuperacionClave::getUsuarioId)
                .containsExactly(9L, 10L);
        assertThat(guardadas.getAllValues().get(0).getTokenHash())
                .isNotEqualTo(guardadas.getAllValues().get(1).getTokenHash());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(correo).enviarCon(any(), eq(9L), eq(CORREO), variables.capture());
        verify(correo).enviarCon(any(), eq(10L), eq(CORREO), variables.capture());
        assertThat(variables.getAllValues()).extracting(v -> v.get("nombre_empresa"))
                .containsExactly("Acme S.A.", "Beta S.A.C.");
        verify(recuperaciones).invalidarLosVivos(eq(9L), any());
        verify(recuperaciones).invalidarLosVivos(eq(10L), any());
    }

    @Test
    @DisplayName("AC-15 · con tres enlaces en la última hora, la cuarta solicitud no crea ni manda nada")
    void laCuartaDeLaHoraNoHaceNada() {
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO)).thenReturn(Optional.of(candidato(CORREO)));
        when(recuperaciones.countByUsuarioIdAndCreadoEnAfter(eq(7L), any())).thenReturn(3L);

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.1");

        verify(recuperaciones, never()).invalidarLosVivos(any(), any());
        verify(recuperaciones, never()).saveAndFlush(any());
        verify(correo, never()).enviarCon(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("la hora se cuenta hacia atrás desde ahora, no desde medianoche")
    void laVentanaEsLaUltimaHora() {
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO)).thenReturn(Optional.of(candidato(CORREO)));
        Instant antes = Instant.now();

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.1");

        ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
        verify(recuperaciones).countByUsuarioIdAndCreadoEnAfter(eq(7L), desde.capture());
        assertThat(desde.getValue()).isBetween(antes.minus(61, ChronoUnit.MINUTES),
                Instant.now().minus(59, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("pasado el tope de su IP, la solicitud ni se encola")
    void pasadoElTopeDeLaIpNoSeEncola() {
        when(solicitudesPorIp.admitir("10.0.0.9", 30)).thenReturn(false);

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.9");

        verifyNoInteractions(cola);
        verify(usuarios, never()).buscarPorCorreo(any(), any());
    }

    @Test
    @DisplayName("un correo vacío o que no es un correo no se encola, pero sí cuenta para la IP")
    void unCorreoQueNoEsCorreoNoSeEncola() {
        servicio.solicitar(Publico.CANDIDATO, "   ", "10.0.0.1");
        servicio.solicitar(Publico.CANDIDATO, null, "10.0.0.1");
        servicio.solicitar(Publico.CANDIDATO, "sin-arroba", "10.0.0.1");
        servicio.solicitar(Publico.CANDIDATO, "a".repeat(320) + "@x.pe", "10.0.0.1");

        verifyNoInteractions(cola);
        verify(solicitudesPorIp, times(4)).admitir("10.0.0.1", 30);
    }

    @Test
    @DisplayName("sin plantilla en ninguna parte no se crea un enlace que nadie va a recibir")
    void sinPlantillaNoSeCreaElEnlace() {
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO)).thenReturn(Optional.of(candidato(CORREO)));
        when(correo.plantillaConRecambio(anyLong(), anyLong(), anyString())).thenReturn(Optional.empty());

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.1");

        verify(recuperaciones, never()).invalidarLosVivos(any(), any());
        verify(recuperaciones, never()).saveAndFlush(any());
        verify(correo, never()).enviarCon(any(), any(), any(), any());
    }

    @Test
    @DisplayName("un servidor de correo caído no cambia nada hacia fuera: el enlace queda y "
            + "la fila dice FALLIDO")
    void elCorreoCaidoNoSeNota() {
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO)).thenReturn(Optional.of(candidato(CORREO)));
        when(correo.enviarCon(any(), anyLong(), anyString(), anyMap()))
                .thenReturn(EnviadorCorreo.Resultado.FALLIDO);

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.1");

        verify(recuperaciones).saveAndFlush(any());
        verify(correo).enviarCon(any(), eq(7L), eq(CORREO), anyMap());
    }

    @Test
    @DisplayName("una vida configurada en cero o negativa no crea enlaces ya vencidos")
    void unaVidaSinSentidoUsaLaDePorDefecto() {
        when(usuarios.buscarPorCorreo(PLATAFORMA, CORREO)).thenReturn(Optional.of(candidato(CORREO)));
        when(parametros.entero(PLATAFORMA, "minutos_vida_recuperacion", 60)).thenReturn(0);

        servicio.solicitar(Publico.CANDIDATO, CORREO, "10.0.0.1");

        ArgumentCaptor<RecuperacionClave> guardada = ArgumentCaptor.forClass(RecuperacionClave.class);
        verify(recuperaciones).saveAndFlush(guardada.capture());
        assertThat(ChronoUnit.MINUTES.between(guardada.getValue().getCreadoEn(),
                guardada.getValue().getVenceEn())).isEqualTo(60);
    }

    // ========================================================================
    // Elegir la contraseña nueva
    // ========================================================================

    private static final String TOKEN = "un-token-de-prueba-que-llego-por-correo";

    private RecuperacionClave vigente(Long usuarioId) throws Exception {
        Instant ahora = Instant.now();
        return RecuperacionClave.builder().id(50L).usuarioId(usuarioId).tokenHash(sha256(TOKEN))
                .creadoEn(ahora.minus(5, ChronoUnit.MINUTES))
                .venceEn(ahora.plus(55, ChronoUnit.MINUTES)).build();
    }

    private void hayEnlace(RecuperacionClave recuperacion, Usuario cuenta) throws Exception {
        when(recuperaciones.findByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(recuperacion));
        lenient().when(usuarios.findById(cuenta.getId())).thenReturn(Optional.of(cuenta));
    }

    @Test
    @DisplayName("AC-06 · AC-14 · con un enlace válido la contraseña cambia, el enlace se gasta "
            + "y el bloqueo por intentos se levanta")
    void conUnEnlaceValidoCambia() throws Exception {
        Usuario cuenta = candidato(CORREO);
        hayEnlace(vigente(7L), cuenta);
        when(codificador.matches("una-clave-nueva", HASH_ACTUAL)).thenReturn(false);
        when(recuperaciones.gastar(eq(50L), any())).thenReturn(1);
        when(codificador.encode("una-clave-nueva")).thenReturn("hash-nuevo");

        servicio.restablecer(Publico.CANDIDATO, TOKEN, "una-clave-nueva");

        assertThat(cuenta.getContrasenaHash()).isEqualTo("hash-nuevo");
        verify(usuarios).save(cuenta);
        verify(intentos).registrarExito(CORREO);
        ArgumentCaptor<Object> valor = ArgumentCaptor.forClass(Object.class);
        verify(auditoria).registrarDelSistema(eq(PLATAFORMA), eq("restablecer_clave"), eq("usuario"),
                eq(7L), valor.capture());
        assertThat(valor.getValue().toString()).doesNotContain(TOKEN).doesNotContain("token");
    }

    @Test
    @DisplayName("AC-08 · un enlace ya usado dice que no sirve y la contraseña no cambia")
    void unEnlaceUsadoNoSirve() throws Exception {
        RecuperacionClave usada = vigente(7L);
        usada.setUsadoEn(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(recuperaciones.findByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(usada));

        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, TOKEN, "una-clave-nueva"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");
        verify(usuarios, never()).save(any());
        verify(recuperaciones, never()).gastar(any(), any());
    }

    @Test
    @DisplayName("AC-09 · un enlace vencido: el mismo texto")
    void unEnlaceVencidoNoSirve() throws Exception {
        RecuperacionClave vencida = vigente(7L);
        vencida.setCreadoEn(Instant.now().minus(2, ChronoUnit.HOURS));
        vencida.setVenceEn(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(recuperaciones.findByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(vencida));

        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, TOKEN, "una-clave-nueva"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("AC-10 · un enlace reemplazado por otro más nuevo: el mismo texto")
    void unEnlaceReemplazadoNoSirve() throws Exception {
        RecuperacionClave invalidada = vigente(7L);
        invalidada.setInvalidadoEn(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(recuperaciones.findByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(invalidada));

        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, TOKEN, "una-clave-nueva"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("un token que no existe, o que viene vacío: el mismo texto")
    void unTokenInexistenteNoSirve() {
        when(recuperaciones.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, "inventado", "una-clave-nueva"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");
        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, "  ", "una-clave-nueva"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");
        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, null, "una-clave-nueva"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("AC-16 · el enlace de una cuenta de equipo no sirve en la puerta del portal, "
            + "ni el de un candidato en la del panel")
    void cadaEnlaceSoloEnSuPuerta() throws Exception {
        hayEnlace(vigente(9L), deEquipo(9L, ACME));
        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, TOKEN, "una-clave-nueva"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");

        hayEnlace(vigente(7L), candidato(CORREO));
        assertThatThrownBy(() -> servicio.restablecer(Publico.EQUIPO, TOKEN, "una-clave-nueva-de-panel"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");
        verify(recuperaciones, never()).gastar(any(), any());
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("una cuenta desactivada después de pedir el enlace no cambia su contraseña")
    void unaCuentaDesactivadaNoRestablece() throws Exception {
        Usuario desactivada = candidato(CORREO);
        desactivada.setEsActivo(false);
        hayEnlace(vigente(7L), desactivada);

        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, TOKEN, "una-clave-nueva"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");
        verify(recuperaciones, never()).gastar(any(), any());
    }

    @Test
    @DisplayName("AC-13 · la misma contraseña de antes se rechaza sin gastar el enlace")
    void laMismaContrasenaNoGastaElEnlace() throws Exception {
        hayEnlace(vigente(7L), candidato(CORREO));
        when(codificador.matches(CLAVE_ACTUAL, HASH_ACTUAL)).thenReturn(true);

        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, TOKEN, CLAVE_ACTUAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Elige una contraseña distinta a la anterior");
        verify(recuperaciones, never()).gastar(any(), any());
        verify(usuarios, never()).save(any());
        verify(intentos, never()).registrarExito(any());
    }

    @Test
    @DisplayName("F-01 · más de 72 bytes se rechaza en español, sin llegar a BCrypt ni gastar el enlace")
    void masDeSetentaYDosBytesNoGastaElEnlace() throws Exception {
        lenient().when(recuperaciones.findByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(vigente(7L)));
        lenient().when(usuarios.findById(7L)).thenReturn(Optional.of(candidato(CORREO)));

        for (String larga : List.of("x".repeat(73), "ñ".repeat(40))) {
            assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, TOKEN, larga))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La contraseña es demasiado larga. Usa como máximo 72 caracteres; "
                            + "las letras con tilde, la ñ y los emojis cuentan por más de uno.");
        }
        verify(codificador, never()).matches(any(), any());
        verify(codificador, never()).encode(any());
        verify(recuperaciones, never()).gastar(any(), any());
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("F-01 · 72 bytes justos sí se guardan")
    void setentaYDosBytesJustosSirven() throws Exception {
        Usuario cuenta = candidato(CORREO);
        hayEnlace(vigente(7L), cuenta);
        String justa = "ñ".repeat(36);
        when(codificador.matches(justa, HASH_ACTUAL)).thenReturn(false);
        when(recuperaciones.gastar(eq(50L), any())).thenReturn(1);
        when(codificador.encode(justa)).thenReturn("hash-nuevo");

        servicio.restablecer(Publico.CANDIDATO, TOKEN, justa);

        assertThat(cuenta.getContrasenaHash()).isEqualTo("hash-nuevo");
    }

    @Test
    @DisplayName("dos pestañas con el mismo enlace: la segunda llega tarde y no cambia nada")
    void laSegundaPestanaLlegaTarde() throws Exception {
        hayEnlace(vigente(7L), candidato(CORREO));
        when(codificador.matches("una-clave-nueva", HASH_ACTUAL)).thenReturn(false);
        when(recuperaciones.gastar(eq(50L), any())).thenReturn(0);

        assertThatThrownBy(() -> servicio.restablecer(Publico.CANDIDATO, TOKEN, "una-clave-nueva"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Este enlace ya no sirve. Pide uno nuevo.");
        verify(codificador, never()).encode(any());
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("en el panel, la cuenta de equipo cambia la suya y solo la suya")
    void enElPanelCambiaLaDeEquipo() throws Exception {
        Usuario cuenta = deEquipo(9L, ACME);
        hayEnlace(vigente(9L), cuenta);
        when(codificador.matches("una-clave-nueva-de-panel", HASH_ACTUAL)).thenReturn(false);
        when(recuperaciones.gastar(eq(50L), any())).thenReturn(1);
        when(codificador.encode("una-clave-nueva-de-panel")).thenReturn("hash-nuevo");

        servicio.restablecer(Publico.EQUIPO, TOKEN, "una-clave-nueva-de-panel");

        assertThat(cuenta.getContrasenaHash()).isEqualTo("hash-nuevo");
        verify(usuarios).save(cuenta);
        verify(auditoria).registrarDelSistema(eq(ACME), eq("restablecer_clave"), eq("usuario"),
                eq(9L), any());
    }
}
