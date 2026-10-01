package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.DatosDeLaVacante;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.vacante.entity.Familia;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.FamiliaRepository;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Los datos de la vacante que recibe la IA: lo que describe el trabajo, nada administrativo")
class DatosDeLaVacanteParaIaTest {

    @Mock private PuestoRepository puestos;
    @Mock private SolicitudTalentoRepository solicitudes;
    @Mock private FamiliaRepository familias;
    @InjectMocks private DatosDeLaVacanteParaIa datos;

    @Test
    @DisplayName("Con todo lleno, viaja lo del trabajo y ninguno de los excluidos (AC-12b)")
    void viajaLoDelTrabajoYNadaAdministrativo() {
        Vacante vacante = Vacante.builder().id(5L).organizacionId(1L).puestoId(2L).solicitudTalentoId(3L)
                .titulo("Asistente contable").descripcion("Registro y cierre")
                .proposito("Cierres a tiempo").responsabilidades("Conciliar bancos")
                .requisitos("Excel avanzado")
                // Lo administrativo, lleno a propósito para ver que no viaja
                .modalidad("PRESENCIAL-X").horario("HORARIO-X").ubicacion("UBICACION-X")
                .ciudadUbigeo("150101").remuneracionTipo("FIJA").remuneracionMin(new BigDecimal("4321"))
                .remuneracionMoneda("PEN").responsableUsuarioId(77L).tipoCierre("PERMANENTE")
                .plazas(3).abreEn(Instant.parse("2026-09-01T00:00:00Z"))
                .cierraEn(Instant.parse("2026-12-01T00:00:00Z"))
                .build();
        when(puestos.findByIdAndOrganizacionId(2L, 1L)).thenReturn(Optional.of(Puesto.builder()
                .id(2L).nombre("Asistente contable").nivelPuestoCodigo("EJECUCION")
                .familiaCodigo("FINANZAS").build()));
        when(solicitudes.findByIdAndOrganizacionId(3L, 1L)).thenReturn(Optional.of(SolicitudTalento.builder()
                .id(3L).resultadoPrincipal("Cierres al día").capacidadesIndispensables("Contabilidad")
                .capacidadesAprendibles("SAP").motivo("MOTIVO-X")
                .requeridaPara(LocalDate.parse("2026-10-01")).build()));
        when(familias.findById("FINANZAS")).thenReturn(Optional.of(Familia.builder()
                .codigo("FINANZAS").nombre("Finanzas").build()));

        DatosDeLaVacante enviados = datos.de(vacante);
        String json = new ObjectMapper().writeValueAsString(enviados);

        assertThat(json).contains("Asistente contable", "Registro y cierre", "Cierres a tiempo",
                "Conciliar bancos", "Excel avanzado", "EJECUCION", "Finanzas", "Cierres al día",
                "Contabilidad", "SAP");
        assertThat(json).doesNotContain("PRESENCIAL-X", "HORARIO-X", "UBICACION-X", "150101",
                "4321", "PEN", "PERMANENTE", "MOTIVO-X", "2026-10-01", "2026-12-01");
        // El contrato mismo no tiene sitio para lo administrativo
        assertThat(Arrays.stream(DatosDeLaVacante.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("modalidad", "horario", "ubicacion", "ciudad", "remuneracion",
                        "responsable", "tipoCierre", "plazas", "abreEn", "cierraEn", "motivo");
    }
}
