package com.renaser.ai.ai_engine.solicitud.service.impl;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Area;
import com.renaser.ai.ai_engine.organizacion.repository.AreaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.solicitud.dto.DtosSolicitud.CrearSolicitud;
import com.renaser.ai.ai_engine.solicitud.dto.DtosSolicitud.ResultadoEsperadoDto;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.ResultadoEsperadoRepository;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("El puesto define la clasificación de la solicitud")
class ServicioSolicitudesImplTest {

    private static final long ORGANIZACION = 1L;
    private static final long PUESTO = 7L;
    private static final long AREA = 2L;
    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private SolicitudTalentoRepository solicitudes;
    @Mock private ResultadoEsperadoRepository resultados;
    @Mock private PuestoRepository puestos;
    @Mock private AreaRepository areas;
    @Mock private ServicioAuditoria auditoria;
    @Mock private Permisos permisos;

    private ServicioSolicitudesImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new ServicioSolicitudesImpl(
                solicitudes, resultados, puestos, areas, auditoria, permisos);
    }

    @Test
    @DisplayName("copia nivel y familia del puesto en vez de confiar en el formulario")
    void derivaLaClasificacionDelPuesto() {
        when(puestos.findByIdAndOrganizacionId(PUESTO, ORGANIZACION))
                .thenReturn(Optional.of(puesto(true)));
        // El área también se comprueba contra la organización: la clave ajena solo exige que
        // exista, y sin este filtro una solicitud podía nacer colgada del área de otra empresa.
        when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                .thenReturn(Optional.of(Area.builder().id(AREA).organizacionId(ORGANIZACION)
                        .nombre("Operaciones").esActiva(true).build()));
        when(solicitudes.save(any())).thenAnswer(invocacion -> {
            SolicitudTalento solicitud = invocacion.getArgument(0);
            solicitud.setId(41L);
            return solicitud;
        });

        servicio.crear(QUIEN, solicitud(null, null));

        ArgumentCaptor<SolicitudTalento> guardada = ArgumentCaptor.forClass(SolicitudTalento.class);
        verify(solicitudes).save(guardada.capture());
        assertThat(guardada.getValue().getPuestoId()).isEqualTo(PUESTO);
        assertThat(guardada.getValue().getNivelPuestoCodigo()).isEqualTo("SUPERVISION");
        assertThat(guardada.getValue().getFamiliaCodigo()).isEqualTo("OPERACIONES");
    }

    @Test
    @DisplayName("rechaza la clasificación antigua cuando contradice al puesto")
    void rechazaUnaClasificacionContradictoria() {
        when(puestos.findByIdAndOrganizacionId(PUESTO, ORGANIZACION))
                .thenReturn(Optional.of(puesto(true)));

        assertThatThrownBy(() -> servicio.crear(QUIEN, solicitud("DIRECCION", "OPERACIONES")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nivel");
        verify(solicitudes, never()).save(any());
    }

    @Test
    @DisplayName("un puesto inactivo no abre una solicitud nueva")
    void rechazaPuestoInactivo() {
        when(puestos.findByIdAndOrganizacionId(PUESTO, ORGANIZACION))
                .thenReturn(Optional.of(puesto(false)));

        assertThatThrownBy(() -> servicio.crear(QUIEN, solicitud(null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inactivo");
    }

    private Puesto puesto(boolean activo) {
        return Puesto.builder()
                .id(PUESTO)
                .organizacionId(ORGANIZACION)
                .nombre("Coordinador de sede")
                .nivelPuestoCodigo("SUPERVISION")
                .familiaCodigo("OPERACIONES")
                .esActivo(activo)
                .build();
    }

    private CrearSolicitud solicitud(String nivel, String familia) {
        return new CrearSolicitud(
                AREA, PUESTO, "NORMAL", nivel, familia,
                "Coordinar la sede", "Crecimiento", "Se frena la operación", null,
                "La carga no se puede redistribuir", null, null, null, null, null, 12L,
                List.of(
                        new ResultadoEsperadoDto("Resultado uno", null),
                        new ResultadoEsperadoDto("Resultado dos", null),
                        new ResultadoEsperadoDto("Resultado tres", null)));
    }
}
