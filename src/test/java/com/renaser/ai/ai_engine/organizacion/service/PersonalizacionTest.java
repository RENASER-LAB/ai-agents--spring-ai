package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.impl.ServicioPersonalizacionImpl;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
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
 * la copia revienta la bandera no queda encendida), apagar no borra nada (RF-138: el
 * banco propio se archiva), y la plataforma no personaliza — ya es dueña de su método.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Encender y apagar la personalización")
class PersonalizacionTest {

    private static final Long EMPRESA = 2L;
    private static final ContextoUsuario ADMIN = new ContextoUsuario(
            10L, 20L, EMPRESA, "EQUIPO", List.of(), Map.of());

    @Mock private OrganizacionRepository organizaciones;
    @Mock private CopiadorDeInstrumentos copiador;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private ServicioAuditoria auditoria;

    private ServicioPersonalizacionImpl servicio;
    private Organizacion empresa;

    @BeforeEach
    void armar() {
        servicio = new ServicioPersonalizacionImpl(organizaciones, copiador, versionesBanco, auditoria);
        empresa = Organizacion.builder().id(EMPRESA).codigo("ACME").build();
        when(organizaciones.findById(EMPRESA)).thenReturn(Optional.of(empresa));
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
        when(copiador.copiarBanco(EMPRESA))
                .thenThrow(new IllegalStateException("nada publicado"));

        assertThatThrownBy(() -> servicio.encender(ADMIN, Instrumento.BANCO))
                .isInstanceOf(IllegalStateException.class);
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

        assertThatThrownBy(() -> servicio.encender(ADMIN, Instrumento.BANCO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plataforma");
    }

    @Test
    @DisplayName("Apagar el banco archiva las versiones propias publicadas, no las borra")
    void apagarElBancoArchivaLoPropio() {
        empresa.setBancoPropio(true);
        VersionBanco propia = VersionBanco.builder()
                .id(80L).organizacionId(EMPRESA).estado("PUBLICADA").build();
        when(versionesBanco.findByOrganizacionIdAndEstado(EMPRESA, "PUBLICADA"))
                .thenReturn(List.of(propia));

        servicio.apagar(ADMIN, Instrumento.BANCO);

        assertThat(empresa.isBancoPropio()).isFalse();
        assertThat(propia.getEstado()).isEqualTo("ARCHIVADA");
        verify(versionesBanco).save(propia);
        verify(versionesBanco, never()).delete(any());
    }

    @Test
    @DisplayName("Apagar lo ya apagado es un conflicto")
    void apagarDosVecesEsConflicto() {
        assertThatThrownBy(() -> servicio.apagar(ADMIN, Instrumento.PRUEBA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya está apagada");
    }
}
