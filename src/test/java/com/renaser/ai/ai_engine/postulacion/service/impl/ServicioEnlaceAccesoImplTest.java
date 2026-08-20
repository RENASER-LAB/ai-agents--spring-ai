package com.renaser.ai.ai_engine.postulacion.service.impl;

import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.EnlaceAcceso;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EnlaceAccesoRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.ServicioEnlaceAcceso.EnlaceGenerado;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El enlace que deja entrar sin contraseña.
 *
 * <p>Casi todo lo que se prueba aquí es lo que debe <b>rechazar</b>, y no por gusto: este
 * token abre la sesión de un candidato y enseña su currículum y sus notas. Si un enlace
 * vencido, revocado o inventado entrara, el agujero no daría ningún síntoma —nadie ve un
 * error, todo parece funcionar— y solo se descubriría cuando alguien viera lo que no es suyo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El enlace de acceso al portal")
class ServicioEnlaceAccesoImplTest {

    private static final Long POSTULACION = 644L;
    private static final Long USUARIO = 1203L;
    private static final Long ORGANIZACION = 1L;

    @Mock private EnlaceAccesoRepository enlaces;
    @Mock private PostulacionRepository postulaciones;
    @Mock private ServicioToken tokens;
    @Mock private ServicioParametros parametros;

    private ServicioEnlaceAccesoImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioEnlaceAccesoImpl(enlaces, postulaciones, tokens, parametros);
        ReflectionTestUtils.setField(servicio, "urlDelPortal", "https://portal.renaser.test");
    }

    private Postulacion laPostulacion() {
        return Postulacion.builder()
                .id(POSTULACION).usuarioId(USUARIO).organizacionId(ORGANIZACION)
                .build();
    }

    /** Devuelve lo que se le pasó a save, que es donde vive el hash del token. */
    private EnlaceAcceso alGuardar() {
        when(enlaces.save(any(EnlaceAcceso.class))).thenAnswer(i -> i.getArgument(0));
        return null;
    }

    // ============ Crear ============

    @Test
    @DisplayName("el token no se guarda en claro: en la base solo queda su hash")
    void guardaElHashYNoElToken() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(laPostulacion()));
        when(parametros.entero(eq(ORGANIZACION), anyString(), anyInt())).thenReturn(30);
        alGuardar();

        String token = servicio.crear(POSTULACION);

        org.mockito.ArgumentCaptor<EnlaceAcceso> guardado =
                org.mockito.ArgumentCaptor.forClass(EnlaceAcceso.class);
        verify(enlaces).save(guardado.capture());

        assertThat(guardado.getValue().getTokenHash())
                .as("un SHA-256 en hexadecimal son 64 caracteres")
                .hasSize(64)
                .as("si el token apareciera tal cual, guardarlo sería como guardar una contraseña en claro")
                .isNotEqualTo(token)
                .doesNotContain(token);
    }

    @Test
    @DisplayName("el token viaja en una URL, así que no lleva caracteres que se rompan al copiarlo")
    void elTokenEsSeguroEnUnaUrl() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(laPostulacion()));
        when(parametros.entero(eq(ORGANIZACION), anyString(), anyInt())).thenReturn(30);
        alGuardar();

        String token = servicio.crear(POSTULACION);

        assertThat(token)
                .as("un «+», un «/» o un «=» dentro de un enlace se rompen al copiarlo")
                .doesNotContain("+").doesNotContain("/").doesNotContain("=")
                .hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    @DisplayName("dos enlaces seguidos nunca salen iguales")
    void cadaEnlaceEsDistinto() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(laPostulacion()));
        when(parametros.entero(eq(ORGANIZACION), anyString(), anyInt())).thenReturn(30);
        alGuardar();

        assertThat(servicio.crear(POSTULACION)).isNotEqualTo(servicio.crear(POSTULACION));
    }

    @Test
    @DisplayName("los días de vigencia salen del parámetro, no de una constante")
    void respetaElParametroDeDias() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(laPostulacion()));
        when(parametros.entero(ORGANIZACION, ServicioEnlaceAccesoImpl.PARAMETRO_DIAS,
                ServicioEnlaceAccesoImpl.DIAS_POR_DEFECTO)).thenReturn(3);
        alGuardar();

        EnlaceGenerado generado = servicio.generarEnlace(POSTULACION);

        assertThat(generado.venceEn())
                .isAfter(Instant.now().plus(2, ChronoUnit.DAYS))
                .isBefore(Instant.now().plus(4, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("una postulación que no existe no genera enlace")
    void noCreaEnlaceDeLaNada() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(POSTULACION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("644");

        verify(enlaces, never()).save(any());
    }

    // ============ El enlace armado ============

    @Test
    @DisplayName("la dirección del portal sale de la configuración, no escrita a mano")
    void armaElEnlaceConLaUrlConfigurada() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(laPostulacion()));
        when(parametros.entero(eq(ORGANIZACION), anyString(), anyInt())).thenReturn(30);
        alGuardar();

        EnlaceGenerado generado = servicio.generarEnlace(POSTULACION);

        assertThat(generado.url()).startsWith("https://portal.renaser.test/acceso?token=");
    }

    @Test
    @DisplayName("una barra de más al final de la url no produce una dirección con dos barras")
    void noDuplicaLaBarra() {
        ReflectionTestUtils.setField(servicio, "urlDelPortal", "https://portal.renaser.test/");
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(laPostulacion()));
        when(parametros.entero(eq(ORGANIZACION), anyString(), anyInt())).thenReturn(30);
        alGuardar();

        assertThat(servicio.generarEnlace(POSTULACION).url())
                .doesNotContain("test//acceso")
                .startsWith("https://portal.renaser.test/acceso?token=");
    }

    // ============ Canjear ============

    /** Un enlace vivo cuyo hash corresponde al token que se le pasa. */
    private void hayUnEnlaceVigenteCon(String token) {
        when(enlaces.findByTokenHash(anyString())).thenAnswer(i -> Optional.of(
                EnlaceAcceso.builder()
                        .id(9L).postulacionId(POSTULACION)
                        .tokenHash(i.getArgument(0))
                        .venceEn(Instant.now().plus(10, ChronoUnit.DAYS))
                        .usos(0).creadoEn(Instant.now())
                        .build()));
    }

    @Test
    @DisplayName("un enlace vigente abre la sesión del candidato al que pertenece")
    void canjeaYAbreSesion() {
        hayUnEnlaceVigenteCon("da-igual");
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(laPostulacion()));
        when(tokens.emitir(USUARIO, ORGANIZACION, "CANDIDATO")).thenReturn("jwt-del-candidato");
        alGuardar();

        Sesion sesion = servicio.canjear("da-igual");

        assertThat(sesion.token()).isEqualTo("jwt-del-candidato");
        assertThat(sesion.usuarioId())
                .as("la sesión tiene que ser la del dueño de la postulación, no la de otro")
                .isEqualTo(USUARIO);
    }

    @Test
    @DisplayName("al usarlo se anota el primer uso, el último y cuántas veces")
    void anotaLosUsos() {
        hayUnEnlaceVigenteCon("da-igual");
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(laPostulacion()));
        when(tokens.emitir(anyLong(), anyLong(), anyString())).thenReturn("jwt");
        alGuardar();

        servicio.canjear("da-igual");

        org.mockito.ArgumentCaptor<EnlaceAcceso> guardado =
                org.mockito.ArgumentCaptor.forClass(EnlaceAcceso.class);
        verify(enlaces).save(guardado.capture());
        EnlaceAcceso despues = guardado.getValue();

        assertThat(despues.getUsos()).isEqualTo(1);
        assertThat(despues.getPrimerUsoEn()).isNotNull();
        assertThat(despues.getUltimoUsoEn()).isNotNull();
    }

    @Test
    @DisplayName("un enlace vencido no entra")
    void rechazaElVencido() {
        when(enlaces.findByTokenHash(anyString())).thenReturn(Optional.of(
                EnlaceAcceso.builder()
                        .postulacionId(POSTULACION)
                        .venceEn(Instant.now().minus(1, ChronoUnit.DAYS))
                        .build()));

        assertThatThrownBy(() -> servicio.canjear("vencido"))
                .isInstanceOf(CredencialesInvalidasException.class);
        verify(tokens, never()).emitir(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("un enlace revocado no entra, aunque todavía no haya vencido")
    void rechazaElRevocado() {
        when(enlaces.findByTokenHash(anyString())).thenReturn(Optional.of(
                EnlaceAcceso.builder()
                        .postulacionId(POSTULACION)
                        .venceEn(Instant.now().plus(10, ChronoUnit.DAYS))
                        .revocadoEn(Instant.now().minus(1, ChronoUnit.HOURS))
                        .build()));

        assertThatThrownBy(() -> servicio.canjear("revocado"))
                .isInstanceOf(CredencialesInvalidasException.class);
        verify(tokens, never()).emitir(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("un token inventado no entra")
    void rechazaElInventado() {
        when(enlaces.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.canjear("me-lo-acabo-de-inventar"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    @DisplayName("sin token no se consulta siquiera la base")
    void rechazaElVacioSinTocarLaBase() {
        assertThatThrownBy(() -> servicio.canjear(null))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> servicio.canjear("   "))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(enlaces, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("los cuatro rechazos dicen exactamente lo mismo")
    void noDistingueElMotivoDelRechazo() {
        when(enlaces.findByTokenHash(anyString())).thenReturn(Optional.empty());

        String porInventado = mensajeDe(() -> servicio.canjear("inventado"));
        String porVacio = mensajeDe(() -> servicio.canjear(""));

        assertThat(porInventado)
                .as("distinguirlos le diría a quien prueba al azar cuáles existieron alguna vez")
                .isEqualTo(porVacio);
    }

    @Test
    @DisplayName("si el enlace apunta a una postulación que ya no está, tampoco entra")
    void rechazaSiLaPostulacionDesaparecio() {
        hayUnEnlaceVigenteCon("huerfano");
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.canjear("huerfano"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    private String mensajeDe(Runnable accion) {
        try {
            accion.run();
            throw new AssertionError("se esperaba un rechazo y no hubo ninguno");
        } catch (CredencialesInvalidasException e) {
            return e.getMessage();
        }
    }

    // ============ La regla de «vigente», que vive en la entidad ============

    @Test
    @DisplayName("vigente es: no revocado y todavía dentro de plazo")
    void laReglaDeVigencia() {
        Instant ahora = Instant.now();
        EnlaceAcceso vivo = EnlaceAcceso.builder().venceEn(ahora.plus(1, ChronoUnit.DAYS)).build();
        EnlaceAcceso vencido = EnlaceAcceso.builder().venceEn(ahora.minus(1, ChronoUnit.DAYS)).build();
        EnlaceAcceso revocado = EnlaceAcceso.builder()
                .venceEn(ahora.plus(1, ChronoUnit.DAYS)).revocadoEn(ahora).build();
        EnlaceAcceso sinFecha = EnlaceAcceso.builder().build();

        assertThat(vivo.estaVigente(ahora)).isTrue();
        assertThat(vencido.estaVigente(ahora)).isFalse();
        assertThat(revocado.estaVigente(ahora)).isFalse();
        assertThat(sinFecha.estaVigente(ahora))
                .as("sin fecha de vencimiento no se asume que vale para siempre")
                .isFalse();
    }
}
