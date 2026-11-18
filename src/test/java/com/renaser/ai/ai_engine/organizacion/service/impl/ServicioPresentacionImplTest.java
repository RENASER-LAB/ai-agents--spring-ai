package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.GuardarBrochure;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.PresentacionPanel;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionBeneficioRepository;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRedSocialRepository;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.repository.SedeRepository;
import com.renaser.ai.ai_engine.organizacion.service.PresentacionInvalidaException;
import com.renaser.ai.ai_engine.organizacion.service.ReglasDeLaPresentacion;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La pestaña «Brochure» del módulo sin base de datos: guarda solo la plantilla y el color de la
 * empresa de quien ha entrado, deja lo demás como estaba y lo audita si cambió.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La pestaña «Brochure»: la plantilla y el color se guardan solos")
class ServicioPresentacionImplTest {

    private static final long ACME = 7L;

    @Mock OrganizacionRepository organizaciones;
    @Mock OrganizacionBeneficioRepository beneficios;
    @Mock OrganizacionRedSocialRepository redes;
    @Mock SedeRepository sedes;
    @Mock CatalogosDelPerfil catalogos;
    @Mock ArchivoRepository archivos;
    @Mock AlmacenArchivos almacen;
    @Mock ServicioAuditoria auditoria;
    @InjectMocks ServicioPresentacionImpl servicio;

    private Organizacion acme;
    private final ContextoUsuario talento = new ContextoUsuario(1L, 1L, ACME, "EQUIPO", List.of(),
            Map.of("editar_presentacion_empresa", "TODO"));

    @BeforeEach
    void preparar() {
        acme = Organizacion.builder().id(ACME).codigo("ACME").nombre("Acme S.A.C.").esActiva(true)
                .quienesSomos("Construimos casas que duran.").mision("Construir bien.")
                .paginaWeb("https://acme.pe").plantillaBrochure("COLUMNAS").colorBrochure("PETROLEO")
                .build();
        when(organizaciones.bloquear(ACME)).thenReturn(Optional.of(acme));
    }

    @Test
    @DisplayName("guarda la plantilla y el color en la empresa de quien ha entrado, sin tocar lo demás, y lo audita")
    void guardaSoloLaEleccion() {
        PresentacionPanel panel = servicio.guardarBrochure(talento,
                new GuardarBrochure("CLASICA", "ROJO"));

        assertThat(panel.plantillaBrochure()).isEqualTo("CLASICA");
        assertThat(panel.colorBrochure()).isEqualTo("ROJO");
        assertThat(panel.quienesSomos()).isEqualTo("Construimos casas que duran.");
        assertThat(panel.mision()).isEqualTo("Construir bien.");
        assertThat(panel.paginaWeb()).isEqualTo("https://acme.pe");
        assertThat(acme.getPlantillaBrochure()).isEqualTo("CLASICA");
        assertThat(acme.getColorBrochure()).isEqualTo("ROJO");
        verify(organizaciones).bloquear(ACME);
        verify(organizaciones).save(acme);
        // Ni los beneficios ni las redes se borran: no son de esta pestaña.
        verify(beneficios, never()).borrarLosDe(anyLong());
        verify(redes, never()).borrarLasDe(anyLong());
        verify(auditoria).registrar(eq(ACME), eq(talento), eq("elegir_brochure_empresa"),
                eq("organizacion"), eq(ACME),
                eq(Map.of("plantillaBrochure", "COLUMNAS", "colorBrochure", "PETROLEO")),
                eq(Map.of("plantillaBrochure", "CLASICA", "colorBrochure", "ROJO")), isNull());
    }

    @Test
    @DisplayName("volver a elegir lo mismo no vuelve a auditar")
    void loMismoNoAudita() {
        servicio.guardarBrochure(talento, new GuardarBrochure("COLUMNAS", "PETROLEO"));

        verify(auditoria, never()).registrar(any(), any(), anyString(), any(), any(), any(), any(),
                any());
    }

    @Test
    @DisplayName("fuera del catálogo: 400 con los que fallan y no se guarda nada")
    void fueraDelCatalogo() {
        PresentacionInvalidaException error = catchThrowableOfType(
                PresentacionInvalidaException.class,
                () -> servicio.guardarBrochure(talento, new GuardarBrochure("MOSAICO", "FUCSIA")));

        assertThat(error.getErrores())
                .containsEntry("plantillaBrochure", ReglasDeLaPresentacion.MENSAJE_PLANTILLA)
                .containsEntry("colorBrochure", ReglasDeLaPresentacion.MENSAJE_COLOR);
        assertThat(acme.getPlantillaBrochure()).isEqualTo("COLUMNAS");
        assertThat(acme.getColorBrochure()).isEqualTo("PETROLEO");
        verify(organizaciones, never()).save(any());
        verify(auditoria, never()).registrar(any(), any(), anyString(), any(), any(), any(), any(),
                any());
    }
}
