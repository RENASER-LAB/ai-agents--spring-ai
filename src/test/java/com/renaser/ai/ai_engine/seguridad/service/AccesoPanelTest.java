package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.seguridad.config.PropiedadesSeguridad;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Login;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.exception.DemasiadosIntentosException;
import com.renaser.ai.ai_engine.seguridad.service.impl.ServicioAccesoEquipoImpl;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El login del panel: correo y contraseña, y la línea que separa los dos mundos.
 *
 * <p>Lo que más importa aquí es lo que NO entra. Un candidato también tiene correo y
 * contraseña; si este login lo dejara pasar, su cuenta del portal abriría el panel de una
 * empresa. La consulta solo mira cuentas con {@code es_equipo}, y el error es el mismo
 * exista o no la cuenta: no se regala información.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El login del panel de empresas")
class AccesoPanelTest {

    private static final Long PLATAFORMA = 1L;
    private static final Long EMPRESA = 2L;

    @Mock private ProveedorIdentidadEquipo proveedor;
    @Mock private ServicioToken tokens;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private PersonaRepository personas;
    @Mock private UsuarioRepository usuarios;
    @Mock private RolRepository roles;
    @Mock private UsuarioRolRepository usuarioRoles;
    @Mock private ServicioParametros parametros;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder codificador;

    private IntentosLogin intentos;
    private ServicioAccesoEquipoImpl servicio;

    @BeforeEach
    void armar() {
        intentos = new IntentosLogin();
        servicio = new ServicioAccesoEquipoImpl(new PropiedadesSeguridad(), proveedor, tokens,
                organizaciones, personas, usuarios, roles, usuarioRoles,
                intentos, parametros, codificador);
        lenient().when(organizaciones.findByEsPlataformaTrue())
                .thenReturn(Optional.of(Organizacion.builder().id(PLATAFORMA).esPlataforma(true).build()));
        lenient().when(parametros.entero(eq(PLATAFORMA), eq("intentos_login_max"), eq(5)))
                .thenReturn(5);
        lenient().when(parametros.entero(eq(PLATAFORMA), eq("minutos_bloqueo_login"), eq(15)))
                .thenReturn(15);
    }

    private Usuario equipo() {
        return Usuario.builder()
                .id(40L).organizacionId(EMPRESA).correo("ana@acme.pe")
                .contrasenaHash("$hash").esEquipo(true).esActivo(true)
                .build();
    }

    @Test
    @DisplayName("Una cuenta de equipo entra, y el token es de SU organización")
    void unaCuentaDeEquipoEntra() {
        when(usuarios.equipoPorCorreo("ana@acme.pe")).thenReturn(List.of(equipo()));
        when(codificador.matches("secreta-larguisima", "$hash")).thenReturn(true);
        when(tokens.emitir(40L, EMPRESA, "EQUIPO")).thenReturn("el-token");

        Sesion sesion = servicio.entrar(new Login("ana@acme.pe", "secreta-larguisima"));

        assertThat(sesion.token()).isEqualTo("el-token");
        assertThat(sesion.usuarioId()).isEqualTo(40L);
        verify(usuarios).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Un candidato con su contraseña correcta NO entra: no es del equipo")
    void unCandidatoNoEntra() {
        // La consulta del repositorio solo devuelve cuentas es_equipo: para el login, el
        // candidato no existe. Ni siquiera se le comprueba la contraseña.
        when(usuarios.equipoPorCorreo("camila@correo.pe")).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.entrar(new Login("camila@correo.pe", "su-contrasena-real")))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("Correo o contraseña incorrectos");
    }

    @Test
    @DisplayName("Una contraseña equivocada da el mismo error que un correo inexistente")
    void contrasenaEquivocadaMismoError() {
        when(usuarios.equipoPorCorreo("ana@acme.pe")).thenReturn(List.of(equipo()));
        when(codificador.matches("equivocada-pero-larga", "$hash")).thenReturn(false);

        assertThatThrownBy(() -> servicio.entrar(new Login("ana@acme.pe", "equivocada-pero-larga")))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("Correo o contraseña incorrectos");
    }

    @Test
    @DisplayName("Una cuenta desactivada no entra aunque la contraseña cuadre")
    void unaCuentaDesactivadaNoEntra() {
        Usuario dormido = equipo();
        dormido.setEsActivo(false);
        when(usuarios.equipoPorCorreo("ana@acme.pe")).thenReturn(List.of(dormido));

        assertThatThrownBy(() -> servicio.entrar(new Login("ana@acme.pe", "secreta-larguisima")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    @DisplayName("Agotar los intentos bloquea, y el bloqueo dice cuánto falta")
    void agotarLosIntentosBloquea() {
        when(usuarios.equipoPorCorreo("ana@acme.pe")).thenReturn(List.of());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> servicio.entrar(new Login("ana@acme.pe", "a-tientas-otra-vez")))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }
        assertThatThrownBy(() -> servicio.entrar(new Login("ana@acme.pe", "a-tientas-otra-vez")))
                .isInstanceOf(DemasiadosIntentosException.class);
    }
}
