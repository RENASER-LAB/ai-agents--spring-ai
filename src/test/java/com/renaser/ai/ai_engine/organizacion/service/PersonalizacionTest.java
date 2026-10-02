package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.impl.ServicioPersonalizacionImpl;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Encender y apagar las banderas: los dos únicos movimientos de la personalización.
 *
 * <p>Lo que se defiende: encender sin copia no existe (van en la misma transacción, y si
 * la copia revienta la bandera no queda encendida), la plataforma no personaliza —ya es
 * dueña de su método— y, desde la V67, personalizar el banco o las pruebas ya no existe:
 * se rechaza con un 400 sin copiar ni cambiar nada (decisión 13).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Encender y apagar la personalización")
class PersonalizacionTest {

    private static final Long EMPRESA = 2L;
    private static final ContextoUsuario ADMIN = new ContextoUsuario(
            10L, 20L, EMPRESA, "EQUIPO", List.of(), Map.of());

    @Mock private OrganizacionRepository organizaciones;
    @Mock private CopiadorDeInstrumentos copiador;
    @Mock private ServicioAuditoria auditoria;

    private ServicioPersonalizacionImpl servicio;
    private Organizacion empresa;

    @BeforeEach
    void armar() {
        servicio = new ServicioPersonalizacionImpl(organizaciones, copiador, auditoria);
        empresa = Organizacion.builder().id(EMPRESA).codigo("ACME").build();
        // lenient: las pruebas de la doble llave de la plataforma cortan antes de llegar
        // a buscar la empresa, y el modo estricto las tumbaría por este stub sin usar.
        org.mockito.Mockito.lenient()
                .when(organizaciones.findById(EMPRESA)).thenReturn(Optional.of(empresa));
    }

    @Test
    @DisplayName("Encender copia el instrumento y deja la bandera encendida")
    void encenderCopiaYEnciende() {
        when(copiador.copiarPesos(EMPRESA)).thenReturn(Map.of("version_pesos", 1));

        servicio.encender(ADMIN, Instrumento.PESOS);

        assertThat(empresa.isPesosPropios()).isTrue();
        verify(organizaciones).save(empresa);
    }

    @Test
    @DisplayName("Si la copia revienta, la bandera no queda encendida a medias")
    void siLaCopiaRevientaLaBanderaNoQueda() {
        when(copiador.copiarPesos(EMPRESA))
                .thenThrow(new IllegalStateException("nada publicado"));

        assertThatThrownBy(() -> servicio.encender(ADMIN, Instrumento.PESOS))
                .isInstanceOf(IllegalStateException.class);
        assertThat(empresa.isPesosPropios()).isFalse();
        verify(organizaciones, never()).save(any());
    }

    @Test
    @DisplayName("Encender o apagar el banco o las pruebas ya no existe: 400 y no se toca nada (AC-26)")
    void elBancoYLasPruebasYaNoSePersonalizan() {
        empresa.setPruebasPuestoPropias(true);
        for (Instrumento cual : List.of(Instrumento.BANCO, Instrumento.PRUEBA)) {
            assertThatThrownBy(() -> servicio.encender(ADMIN, cual))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ya no existe");
            assertThatThrownBy(() -> servicio.apagar(ADMIN, cual))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ya no existe");
        }
        // Quien ya las tenía personalizadas conserva lo suyo: su bandera no cambia.
        assertThat(empresa.isPruebasPuestoPropias()).isTrue();
        assertThat(empresa.isBancoPropio()).isFalse();
        verify(organizaciones, never()).save(any());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Tampoco cuando lo pide la plataforma por otra empresa (AC-26)")
    void niSiquieraDesdeLaPlataforma() {
        Organizacion plataforma = Organizacion.builder().id(1L).codigo("RENASER")
                .esPlataforma(true).build();
        when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(plataforma));
        ContextoUsuario renaser = new ContextoUsuario(
                11L, 21L, 1L, "EQUIPO", List.of(), Map.of());

        for (Instrumento cual : List.of(Instrumento.BANCO, Instrumento.PRUEBA)) {
            assertThatThrownBy(() -> servicio.encenderPara(renaser, EMPRESA, cual, "la pidió"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ya no existe");
            assertThatThrownBy(() -> servicio.apagarPara(renaser, EMPRESA, cual, "la pidió"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ya no existe");
        }
        assertThat(empresa.isBancoPropio()).isFalse();
        verify(organizaciones, never()).save(any());
    }

    @Test
    @DisplayName("Encender lo ya encendido es un conflicto, no una segunda copia")
    void encenderDosVecesEsConflicto() {
        empresa.setPesosPropios(true);

        assertThatThrownBy(() -> servicio.encender(ADMIN, Instrumento.PESOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya está encendida");
        verify(copiador, never()).copiarPesos(any());
    }

    @Test
    @DisplayName("La plataforma no personaliza: ya es dueña de su método")
    void laPlataformaNoPersonaliza() {
        empresa.setEsPlataforma(true);

        assertThatThrownBy(() -> servicio.encender(ADMIN, Instrumento.PESOS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plataforma");
    }

    @Test
    @DisplayName("Apagar los pesos propios vuelve a los de la plataforma, sin borrar nada")
    void apagarLosPesosVuelveALaPlataforma() {
        empresa.setPesosPropios(true);

        servicio.apagar(ADMIN, Instrumento.PESOS);

        assertThat(empresa.isPesosPropios()).isFalse();
        verify(organizaciones).save(empresa);
    }

    @Test
    @DisplayName("Apagar lo ya apagado es un conflicto")
    void apagarDosVecesEsConflicto() {
        assertThatThrownBy(() -> servicio.apagar(ADMIN, Instrumento.PLANTILLA_EVALUACION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya está apagada");
    }

    // ============ Sobre otra empresa, desde la plataforma (pieza F) ============

    private static final Long PLATAFORMA = 1L;
    private static final ContextoUsuario DUENA = new ContextoUsuario(
            30L, 40L, PLATAFORMA, "EQUIPO", List.of(), Map.of());

    private void hayPlataforma() {
        when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(
                Organizacion.builder().id(PLATAFORMA).esPlataforma(true).build()));
    }

    @Test
    @DisplayName("La plataforma enciende la personalización de otra empresa, con el motivo auditado")
    void laPlataformaEnciendeParaOtraConMotivo() {
        // Cuando la empresa lo pide fuera del sistema: misma copia y misma auditoría que
        // si lo hiciera ella — y el POR QUÉ queda escrito, que es lo que protege a Renaser.
        hayPlataforma();
        when(copiador.copiarPesos(EMPRESA)).thenReturn(Map.of("version_pesos", 1));

        servicio.encenderPara(DUENA, EMPRESA, Instrumento.PESOS, "Lo pidió ACME por correo");

        assertThat(empresa.isPesosPropios()).isTrue();
        verify(auditoria).registrar(org.mockito.ArgumentMatchers.eq(PLATAFORMA),
                org.mockito.ArgumentMatchers.eq(DUENA),
                org.mockito.ArgumentMatchers.eq("encender_personalizacion"),
                org.mockito.ArgumentMatchers.eq("organizacion"),
                org.mockito.ArgumentMatchers.eq(EMPRESA), any(), any(),
                org.mockito.ArgumentMatchers.eq("Lo pidió ACME por correo"));
    }

    @Test
    @DisplayName("Desde una empresa no se toca la personalización de otra, ni con el permiso")
    void unaEmpresaNoTocaLaPersonalizacionDeOtra() {
        // La segunda llave del panel de plataforma: el permiso lo mira el controlador, y
        // aquí se exige además SER la plataforma — una empresa con el permiso copiado no
        // le enciende (ni apaga) nada a la competencia.
        hayPlataforma();

        assertThatThrownBy(() -> servicio.encenderPara(ADMIN, 3L, Instrumento.PESOS, "colada"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.apagarPara(ADMIN, 3L, Instrumento.PESOS, "colada"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(organizaciones, never()).save(any());
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("La empresa objetivo que no existe es un 404, no un fallo raro")
    void laEmpresaQueNoExisteEsUn404() {
        hayPlataforma();
        when(organizaciones.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.encenderPara(DUENA, 99L, Instrumento.PESOS, "typo"))
                .isInstanceOf(com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException.class);
    }
}
