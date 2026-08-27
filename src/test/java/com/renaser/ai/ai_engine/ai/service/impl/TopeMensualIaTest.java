package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.repository.EjecucionIaRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.entity.Parametro;
import com.renaser.ai.ai_engine.parametro.repository.ParametroRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.usuario.entity.Rol;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.entity.UsuarioRol;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El tope mensual de IA (pieza E): la cuenta, el freno y la campana del 80%.
 *
 * <p>Lo que se protege: que sin tope no haya ni pregunta (la mayoría de las
 * organizaciones no tienen), que el freno actúe exactamente en el 100%, y que el aviso
 * del 80% salga UNA vez por mes — a la empresa y a la plataforma, cada correo con la
 * plantilla de su organización.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El tope mensual de IA por organización")
class TopeMensualIaTest {

    private static final Long EMPRESA = 2L;
    private static final Long PLATAFORMA = 1L;
    private static final String MES = YearMonth.now(TopeMensualIa.ZONA_LIMA).toString();

    @Mock private ServicioParametros parametros;
    @Mock private ParametroRepository filasParametro;
    @Mock private EjecucionIaRepository ejecuciones;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private RolRepository roles;
    @Mock private UsuarioRolRepository usuarioRoles;
    @Mock private UsuarioRepository usuarios;
    @Mock private ServicioCorreo correo;

    private TopeMensualIa tope;

    @BeforeEach
    void armar() {
        tope = new TopeMensualIa(parametros, filasParametro, ejecuciones, organizaciones,
                roles, usuarioRoles, usuarios, correo);
    }

    private void conTope(String valor) {
        when(parametros.texto(EMPRESA, TopeMensualIa.PARAMETRO_TOPE, null)).thenReturn(valor);
    }

    private void conConsumo(String valor) {
        when(ejecuciones.costoDelPeriodo(eq(EMPRESA), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal(valor));
    }

    // ============ El freno del 100% ============

    @Test
    void sinParametroNoHayTopeNiSumaQuePagar() {
        conTope(null);

        assertThat(tope.sinCupo(EMPRESA)).isFalse();
        // Ni siquiera se suma el mes: a la organización sin tope este camino le cuesta
        // una lectura de parámetro, no una agregación por petición.
        verifyNoInteractions(ejecuciones);
    }

    @Test
    void porDebajoDelTopeHayCupo() {
        conTope("100");
        conConsumo("60.00");

        assertThat(tope.sinCupo(EMPRESA)).isFalse();
    }

    @Test
    void alcanzarElTopeExactoYaEsNoTenerCupo() {
        // El freno es «>= tope», no «> tope»: con el cupo justo agotado, el siguiente
        // trabajo ya es factura sorpresa.
        conTope("100");
        conConsumo("100.0000");

        assertThat(tope.sinCupo(EMPRESA)).isTrue();
    }

    @Test
    void unTopeIlegibleNoCongelaANadie() {
        // Un valor roto en la base no puede parar las calificaciones de una empresa: se
        // trata como sin tope y queda el error en el registro para que alguien lo arregle.
        conTope("cincuenta");

        assertThat(tope.sinCupo(EMPRESA)).isFalse();
    }

    // ============ La campana del 80% ============

    private void armarDestinatarios() {
        lenient().when(roles.findByOrganizacionIdAndCodigo(EMPRESA, "ADMINISTRADOR"))
                .thenReturn(Optional.of(Rol.builder().id(20L).build()));
        lenient().when(usuarioRoles.findByRolId(20L))
                .thenReturn(List.of(UsuarioRol.builder().usuarioId(40L).rolId(20L).build()));
        lenient().when(usuarios.findById(40L)).thenReturn(Optional.of(Usuario.builder()
                .id(40L).organizacionId(EMPRESA).correo("ana@acme.pe").esActivo(true).build()));
        lenient().when(organizaciones.findById(EMPRESA)).thenReturn(Optional.of(
                Organizacion.builder().id(EMPRESA).nombre("Acme S.A.C.").build()));
        lenient().when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(
                Organizacion.builder().id(PLATAFORMA).nombre("Renaser").esPlataforma(true).build()));
        lenient().when(roles.findByOrganizacionIdAndCodigo(PLATAFORMA, "ADMINISTRADOR"))
                .thenReturn(Optional.of(Rol.builder().id(10L).build()));
        lenient().when(usuarioRoles.findByRolId(10L))
                .thenReturn(List.of(UsuarioRol.builder().usuarioId(30L).rolId(10L).build()));
        lenient().when(usuarios.findById(30L)).thenReturn(Optional.of(Usuario.builder()
                .id(30L).organizacionId(PLATAFORMA).correo("duena@renaser.pe").esActivo(true).build()));
    }

    @Test
    void alCruzarEl80SaleElAvisoALaEmpresaYALaPlataforma() {
        conTope("100");
        conConsumo("85.00");
        when(parametros.texto(EMPRESA, TopeMensualIa.PARAMETRO_AVISO, null)).thenReturn(null);
        armarDestinatarios();

        tope.avisarSiCruzaElUmbral(EMPRESA);

        // Cada correo con la plantilla de SU organización: el de Ana lo firma Acme, el de
        // la dueña lo firma Renaser.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(correo).enviar(eq(EMPRESA), eq(40L), eq("ana@acme.pe"),
                eq(TopeMensualIa.PLANTILLA_AVISO), variables.capture());
        verify(correo).enviar(eq(PLATAFORMA), eq(30L), eq("duena@renaser.pe"),
                eq(TopeMensualIa.PLANTILLA_AVISO), any());
        assertThat(variables.getValue())
                .containsEntry("empresa", "Acme S.A.C.")
                .containsEntry("porcentaje", "85")
                .containsEntry("mes", MES);

        // Y queda la marca del mes: correo_enviado no sabe de organizaciones, así que el
        // «ya avisé» vive en un parámetro con el YYYY-MM.
        ArgumentCaptor<Parametro> marca = ArgumentCaptor.forClass(Parametro.class);
        verify(filasParametro).save(marca.capture());
        assertThat(marca.getValue().getCodigo()).isEqualTo(TopeMensualIa.PARAMETRO_AVISO);
        assertThat(marca.getValue().getValor()).isEqualTo(MES);
    }

    @Test
    void elAvisoDelMesYaMandadoNoSeRepite() {
        conTope("100");
        conConsumo("90.00");
        when(parametros.texto(EMPRESA, TopeMensualIa.PARAMETRO_AVISO, null)).thenReturn(MES);

        tope.avisarSiCruzaElUmbral(EMPRESA);

        verifyNoInteractions(correo);
        verify(filasParametro, never()).save(any());
    }

    @Test
    void enUnMesNuevoLaCampanaVuelveASonar() {
        // La marca guarda el YYYY-MM: al cambiar el mes ya no coincide y el aviso del mes
        // nuevo sale. (El consumo también arranca de cero solo, porque la suma se acota
        // al mes corriente — eso lo hace la consulta, no esta clase.)
        conTope("100");
        conConsumo("85.00");
        when(parametros.texto(EMPRESA, TopeMensualIa.PARAMETRO_AVISO, null)).thenReturn("2026-07");
        when(filasParametro.findByOrganizacionIdAndCodigo(EMPRESA, TopeMensualIa.PARAMETRO_AVISO))
                .thenReturn(Optional.of(Parametro.builder()
                        .organizacionId(EMPRESA).codigo(TopeMensualIa.PARAMETRO_AVISO)
                        .valor("2026-07").build()));
        armarDestinatarios();

        tope.avisarSiCruzaElUmbral(EMPRESA);

        verify(correo).enviar(eq(EMPRESA), eq(40L), anyString(),
                eq(TopeMensualIa.PLANTILLA_AVISO), any());
        ArgumentCaptor<Parametro> marca = ArgumentCaptor.forClass(Parametro.class);
        verify(filasParametro).save(marca.capture());
        assertThat(marca.getValue().getValor()).isEqualTo(MES);
    }

    @Test
    void porDebajoDel80NoSuenaNada() {
        conTope("100");
        conConsumo("79.99");

        tope.avisarSiCruzaElUmbral(EMPRESA);

        verifyNoInteractions(correo, filasParametro);
    }

    @Test
    void sinTopeElAvisoNoExiste() {
        conTope(null);

        tope.avisarSiCruzaElUmbral(EMPRESA);

        verifyNoInteractions(correo, ejecuciones, filasParametro);
    }
}
