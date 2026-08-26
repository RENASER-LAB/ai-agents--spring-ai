package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.SolicitudBorradoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioEvaluacion;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EnlaceCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.IntentosLogin;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Postular a una vacante que prescinde del banco de preguntas (V30).
 *
 * <p>Lo que se protege: que el salto de etapa sea el correcto y que no quede basura a
 * medias. Si a un candidato de una vacante sin banco se le creara su evaluación igual,
 * quedaría una fila esperando respuestas que nadie va a dar — y el cierre por plazo
 * vencido la barrería como un abandono que nunca ocurrió.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Postular a una vacante sin banco de preguntas")
class ServicioPortalImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long USUARIO = 21L;
    private static final Long PERSONA = 33L;
    private static final Long VACANTE = 40L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            USUARIO, PERSONA, ORGANIZACION, "CANDIDATO", List.of(), Map.of());

    @Mock private OrganizacionRepository organizaciones;
    @Mock private PersonaRepository personas;
    @Mock private UsuarioRepository usuarios;
    @Mock private RolRepository roles;
    @Mock private UsuarioRolRepository usuarioRoles;
    @Mock private TextoConsentimientoRepository textosConsentimiento;
    @Mock private ConsentimientoRepository consentimientos;
    @Mock private SolicitudBorradoRepository solicitudesBorrado;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private RequisitoObjetivoRepository requisitos;
    @Mock private ServicioEvaluacion evaluaciones;
    @Mock private PostulacionRepository postulaciones;
    @Mock private TransicionEstadoRepository transiciones;
    @Mock private EstadoPostulacionRepository estados;
    @Mock private CvRepository cvs;
    @Mock private EnlaceCvRepository enlaces;
    @Mock private MaquinaEstados maquina;
    @Mock private com.renaser.ai.ai_engine.perfil.service.ServicioPropuestaPerfil propuestaPerfil;
    @Mock private com.renaser.ai.ai_engine.perfil.service.ServicioLecturaCv lecturaCv;
    @Mock private AlmacenArchivos almacen;
    @Mock private ServicioCorreo correo;
    @Mock private ServicioAuditoria auditoria;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioToken tokens;
    @Mock private IntentosLogin intentos;
    @Mock private PasswordEncoder codificador;
    @Mock private MultipartFile cv;

    private ServicioPortalImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPortalImpl(organizaciones, personas, usuarios, roles, usuarioRoles,
                textosConsentimiento, consentimientos, solicitudesBorrado, vacantes, puestos,
                requisitos, evaluaciones, postulaciones, transiciones, estados, cvs, enlaces,
                maquina, propuestaPerfil, lecturaCv,
                almacen, correo, auditoria, parametros, tokens, intentos, codificador);
    }

    /**
     * La vacante publicada de la organización pedida, con todo lo que postular necesita.
     *
     * <p>Todo con {@code lenient()} a propósito: las pruebas que cortan a mitad de camino
     * —sin texto publicado, por ejemplo— no llegan a usar los últimos pasos, y el modo
     * estricto de Mockito las tumbaría por stubs sin usar.
     */
    private void armarVacantePublicada(Long organizacionDeLaVacante) {
        // La vacante se busca en el tablón entero (findById): el candidato es de la
        // plataforma y postula a la vacante de cualquier empresa.
        org.mockito.Mockito.lenient().when(vacantes.findById(VACANTE))
                .thenReturn(Optional.of(Vacante.builder()
                        .id(VACANTE).organizacionId(organizacionDeLaVacante).estado("PUBLICADA")
                        .titulo("Administrador").puestoId(5L)
                        .aplicaEvaluacion(false)
                        .build()));
        org.mockito.Mockito.lenient()
                .when(postulaciones.existsByUsuarioIdAndVacanteId(USUARIO, VACANTE)).thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(postulaciones.save(any(Postulacion.class))).thenAnswer(inv -> {
                    Postulacion p = inv.getArgument(0);
                    p.setId(77L);
                    return p;
                });
        org.mockito.Mockito.lenient()
                .when(transiciones.save(any(TransicionEstado.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(almacen.guardar(eq(organizacionDeLaVacante), eq(cv)))
                .thenReturn(Archivo.builder().id(88L).build());
        org.mockito.Mockito.lenient().when(cvs.save(any(Cv.class))).thenAnswer(inv -> {
            Cv guardado = inv.getArgument(0);
            guardado.setId(99L);
            return guardado;
        });
        org.mockito.Mockito.lenient()
                .when(usuarios.findById(USUARIO)).thenReturn(Optional.of(Usuario.builder()
                        .id(USUARIO).personaId(PERSONA).correo("ana@ejemplo.pe").build()));
        org.mockito.Mockito.lenient()
                .when(personas.findById(PERSONA)).thenReturn(Optional.of(Persona.builder()
                        .id(PERSONA).nombre("Ana").apellidos("Rojas").build()));
        org.mockito.Mockito.lenient()
                .when(requisitos.findByVacanteIdAndEsActivoTrue(VACANTE)).thenReturn(List.of());
        // El texto PROCESO publicado de la empresa de la vacante: postular lo firma
        org.mockito.Mockito.lenient().when(textosConsentimiento
                .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                        organizacionDeLaVacante, "PROCESO"))
                .thenReturn(Optional.of(com.renaser.ai.ai_engine.consentimiento.entity
                        .TextoConsentimiento.builder()
                        .id(500L).organizacionId(organizacionDeLaVacante).tipo("PROCESO")
                        .build()));
    }

    @Test
    @DisplayName("sin banco no se crea evaluación y la postulación va directa a la bandeja del equipo")
    void sinBancoVaDirectaALaBandeja() {
        armarVacantePublicada(ORGANIZACION);

        servicio.postular(QUIEN, VACANTE, cv, "Ordené la caja de tres sedes",
                null, null, null, null, true, "10.0.0.1", "Navegador");

        // El salto: directo a la bandeja del equipo, sin turno de candidato en el perfil
        verify(maquina).transicionar(any(Postulacion.class), eq("PERFIL_POR_CONFIRMAR"),
                isNull(), isNull(), eq(true), eq(false), isNull());
        // Y sin evaluación creada: no queda ninguna fila esperando respuestas
        verifyNoInteractions(evaluaciones);
    }

    @Test
    @DisplayName("postular firma el texto de LA EMPRESA de la vacante, amarrado a la postulación")
    void postularFirmaConLaEmpresaDeLaVacante() {
        // La organización del candidato es la plataforma (1L); la de la vacante, otra.
        // El registro firmado tiene que apuntar al texto de la EMPRESA — es lo que la ley
        // 29733 espera: cada quien que trata datos, nombrado y consentido.
        Long empresa = 2L;
        armarVacantePublicada(empresa);

        servicio.postular(QUIEN, VACANTE, cv, "Ordené la caja de tres sedes",
                null, null, null, null, true, "10.0.0.1", "Navegador");

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.renaser.ai.ai_engine.consentimiento.entity.Consentimiento.class);
        verify(consentimientos).save(captor.capture());
        var firmado = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(firmado.getTextoConsentimientoId()).isEqualTo(500L);
        org.assertj.core.api.Assertions.assertThat(firmado.getPostulacionId()).isEqualTo(77L);
        org.assertj.core.api.Assertions.assertThat(firmado.getPersonaId()).isEqualTo(PERSONA);
        org.assertj.core.api.Assertions.assertThat(firmado.getIp()).isEqualTo("10.0.0.1");
        org.assertj.core.api.Assertions.assertThat(firmado.getNombreRegistrado()).isEqualTo("Ana Rojas");
    }

    @Test
    @DisplayName("sin aceptar el tratamiento de datos no hay postulación, y no queda nada a medias")
    void sinAceptarElTratamientoNoHayPostulacion() {
        assertThatThrownBy(() -> servicio.postular(QUIEN, VACANTE, cv, "Un resultado",
                null, null, null, null, null, "10.0.0.1", "Navegador"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aceptar el tratamiento");
        // Se corta ANTES de tocar nada: ni postulación, ni CV, ni consentimiento
        verifyNoInteractions(postulaciones, almacen, consentimientos);
    }

    @Test
    @DisplayName("si la empresa no tiene texto publicado, postular se frena con un error claro")
    void sinTextoPublicadoPostularSeFrena() {
        // Defensa en profundidad: publicar la vacante ya exige el texto, pero si esta
        // situación llegara a darse el candidato no puede firmar un texto que no existe.
        armarVacantePublicada(2L);
        when(textosConsentimiento
                .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                        2L, "PROCESO"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.postular(QUIEN, VACANTE, cv, "Un resultado",
                null, null, null, null, true, "10.0.0.1", "Navegador"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("texto de consentimiento");
        verifyNoInteractions(consentimientos);
    }

    @Test
    @DisplayName("una cuenta de equipo no entra al portal aunque su contraseña cuadre")
    void unaCuentaDeEquipoNoEntraAlPortal() {
        // El espejo del login del panel: desde la V37 el equipo también tiene contraseña,
        // y sin el filtro es_equipo la gente del panel de la plataforma abría el portal
        // como candidata. Ni siquiera se le llega a comprobar la contraseña.
        when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.organizacion.entity.Organizacion.builder()
                        .id(ORGANIZACION).esPlataforma(true).build()));
        when(usuarios.buscarPorCorreo(ORGANIZACION, "recluta@renaser.pe"))
                .thenReturn(Optional.of(Usuario.builder()
                        .id(60L).organizacionId(ORGANIZACION).correo("recluta@renaser.pe")
                        .contrasenaHash("$hash").esEquipo(true).esActivo(true)
                        .build()));

        assertThatThrownBy(() -> servicio.entrar(new com.renaser.ai.ai_engine.portal.dto
                        .DtosPortal.Login("recluta@renaser.pe", "su-contrasena-real")))
                .isInstanceOf(com.renaser.ai.ai_engine.seguridad.exception
                        .CredencialesInvalidasException.class)
                .hasMessageContaining("Correo o contraseña incorrectos");
        verifyNoInteractions(codificador);
    }
}
