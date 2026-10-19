package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.vacante.entity.RequisitoObjetivo;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Campo;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Etapa;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.VacanteDelContexto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** La ficha pública y el procedimiento de la consulta v2 (V76): AC-03 a AC-06. */
@ExtendWith(MockitoExtension.class)
@DisplayName("La ficha pública y el procedimiento de la consulta v2")
class FichaYProcedimientoPublicosTest {

    @Mock private OrganizacionRepository organizaciones;
    @Mock private RequisitoObjetivoRepository requisitos;
    @Mock private CatalogosDelPerfil catalogos;

    private FichaPublica ficha;

    @BeforeEach
    void montar() {
        PropiedadesWhatsapp propiedades = new PropiedadesWhatsapp();
        propiedades.setSecretoDesdeElBot("secreto-de-pruebas-desde-el-bot-de-whatsapp");
        ficha = new FichaPublica(organizaciones, requisitos, catalogos, propiedades);
        lenient().when(organizaciones.findById(1L)).thenReturn(Optional.of(Organizacion.builder().id(1L)
                .nombre("Renaser").build()));
        lenient().when(catalogos.ciudadesPorCodigo()).thenReturn(Map.of("0401",
                new OpcionUbigeo("0401", "Arequipa", "Arequipa"), "1501", new OpcionUbigeo("1501", "Lima", "Lima")));
    }

    private static Vacante.VacanteBuilder completa() {
        return Vacante.builder().id(5L).organizacionId(1L).titulo("Asistente Contable").estado("PUBLICADA")
                .descripcion("Lleva la contabilidad de tres sedes").proposito("Cierres al día")
                .responsabilidades("Conciliar bancos").requisitos("Bachiller en contabilidad")
                .modalidad("Presencial").horario("Lunes a viernes de 8:00 a 17:00")
                .ubicacion("Selva Alegre").ciudadUbigeo("0401")
                .notaMinima(BigDecimal.valueOf(75)).compensacionPublica("S/ 9 999 viejo")
                .remuneracionTipo("OCULTA").remuneracionMin(new BigDecimal("3500"))
                .remuneracionMax(new BigDecimal("4200")).remuneracionMoneda("PEN");
    }

    @Test
    @DisplayName("AC-03 · funciones, requisitos, horario y ubicación públicos, sin la ficha administrativa")
    void laFichaPublica() {
        when(requisitos.findByVacanteIdAndEsActivoTrue(5L)).thenReturn(List.of(
                RequisitoObjetivo.builder().id(2L).descripcion("Licencia de conducir A1").build()));

        VacanteDelContexto vacante = ficha.de(completa().build());

        assertThat(vacante.ref()).matches("vc_[0-9a-f]{32}");
        assertThat(vacante.titulo()).isEqualTo("Asistente Contable");
        assertThat(vacante.empresa()).isEqualTo("Renaser");
        assertThat(vacante.distintivo()).isEqualTo("Renaser · Arequipa");
        assertThat(vacante.convocatoria().estado()).isEqualTo("PUBLICADA");
        assertThat(vacante.convocatoria().cerradaEn()).isNull();
        assertThat(vacante.ficha().responsabilidades()).isEqualTo(new Campo("PUBLICADO", "Conciliar bancos"));
        assertThat(vacante.ficha().horario().texto()).isEqualTo("Lunes a viernes de 8:00 a 17:00");
        assertThat(vacante.ficha().ubicacion().texto()).isEqualTo("Arequipa · Selva Alegre");
        assertThat(vacante.ficha().requisitos().texto())
                .isEqualTo("Bachiller en contabilidad\n• Licencia de conducir A1");
        // Nada de la ficha administrativa: ni la nota mínima ni la compensación antigua.
        assertThat(vacante.ficha().toString()).doesNotContain("75").doesNotContain("9 999");
    }

    @Test
    @DisplayName("AC-04 · con el sueldo oculto no viaja ningún importe, aunque la vacante los tenga")
    void sueldoOculto() {
        VacanteDelContexto vacante = ficha.de(completa().build());

        assertThat(vacante.ficha().remuneracion()).isEqualTo(Campo.NO_PUBLICADO);
        assertThat(vacante.ficha().toString()).doesNotContain("3500").doesNotContain("3 500")
                .doesNotContain("4200").doesNotContain("4 200");
        assertThat(vacante.distintivo()).doesNotContain("3").doesNotContain("4");
    }

    @Test
    @DisplayName("AC-04 · fijo y rango se publican con el texto del portal")
    void sueldoFijoYRango() {
        Campo fijo = FichaPublica.remuneracionDe(completa().remuneracionTipo("FIJA").remuneracionMax(null).build());
        Campo rango = FichaPublica.remuneracionDe(completa().remuneracionTipo("RANGO").build());

        assertThat(fijo.estado()).isEqualTo("PUBLICADO");
        assertThat(fijo.texto()).contains("3").doesNotContain(" a ");
        assertThat(rango.estado()).isEqualTo("PUBLICADO");
        assertThat(rango.texto()).contains(" a ");
    }

    @Test
    @DisplayName("AC-05 · lo que no está publicado se dice así, y beneficios y contrato no se inventan")
    void noPublicado() {
        VacanteDelContexto vacante = ficha.de(Vacante.builder().id(6L).organizacionId(1L).titulo("Contador")
                .estado("CERRADA").cerradaEn(Instant.now()).build());

        assertThat(vacante.ficha().horario()).isEqualTo(Campo.NO_PUBLICADO);
        assertThat(vacante.ficha().descripcion()).isEqualTo(Campo.NO_PUBLICADO);
        assertThat(vacante.ficha().ubicacion()).isEqualTo(Campo.NO_PUBLICADO);
        assertThat(vacante.ficha().beneficios()).isEqualTo(Campo.NO_PUBLICADO);
        assertThat(vacante.ficha().tipoContrato()).isEqualTo(Campo.NO_PUBLICADO);
        assertThat(vacante.ficha().beneficios().texto()).isNull();
        // Solo la empresa: sin ciudad ni zona no se inventa una.
        assertThat(vacante.distintivo()).isEqualTo("Renaser");
        assertThat(vacante.convocatoria().estado()).isEqualTo("CERRADA");
        assertThat(vacante.convocatoria().revision()).isEqualTo(1);
        assertThat(vacante.convocatoria().cerradaEn()).isNotNull();
    }

    @Test
    @DisplayName("Sin empresa ni lugar publicados, no hay distintivo")
    void sinDistintivo() {
        assertThat(FichaPublica.distintivoDe(Vacante.builder().build(), null, Map.of())).isNull();
    }

    // ============ el procedimiento ============

    @Test
    @DisplayName("AC-06 · con evaluación inicial y prueba cronometrada; la etapa actual separa lo hecho de lo previsto")
    void conEvaluacion() {
        Vacante vacante = completa().aplicaEvaluacion(true).instrumentoEtapaTecnica("PRUEBA_PROPIA").build();

        List<Etapa> etapas = ProcedimientoPublico.enMarcha(vacante, "PRUEBA_PUESTO", "CRONOMETRADA");

        assertThat(etapas).extracting(Etapa::codigo)
                .containsExactly("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "SIMULACION", "VALIDACION", "DECISION");
        assertThat(etapas).extracting(Etapa::estado)
                .containsExactly("HECHA", "ACTUAL", "PREVISTA", "PREVISTA", "PREVISTA");
        assertThat(etapas).extracting(Etapa::orden).containsExactly(1, 2, 3, 4, 5);
        assertThat(etapas.get(0).explicacion()).isEqualTo("Tu currículum y una evaluación escrita.");
        assertThat(etapas.get(1).nombre()).isEqualTo("La prueba del puesto");
        assertThat(etapas.get(1).explicacion()).contains("tiempo medido");
    }

    @Test
    @DisplayName("AC-06 · sin evaluación inicial no se promete un banco de preguntas; con cuestionario, no hay encargo")
    void sinEvaluacionYConCuestionario() {
        Vacante vacante = completa().aplicaEvaluacion(false).instrumentoEtapaTecnica("CUESTIONARIO_TECNICO").build();

        List<Etapa> etapas = ProcedimientoPublico.enMarcha(vacante, "PERFIL_INTEGRAL", null);

        assertThat(etapas.get(0).explicacion()).doesNotContain("Tu currículum y una evaluación")
                .contains("no pide una evaluación escrita");
        assertThat(etapas.get(0).estado()).isEqualTo("ACTUAL");
        assertThat(etapas.get(1).nombre()).isEqualTo("La prueba técnica");
        assertThat(etapas.get(1).explicacion()).doesNotContain("encargo");
    }

    @Test
    @DisplayName("Una prueba de plazo abierto no se presenta «con tiempo medido»")
    void plazoAbierto() {
        Vacante vacante = completa().instrumentoEtapaTecnica("PLANTILLA").build();

        assertThat(ProcedimientoPublico.enMarcha(vacante, null, "PLAZO_ABIERTO").get(1).explicacion())
                .doesNotContain("tiempo medido").contains("plazo en días");
        // Sin etapa todavía (POSTULADA), todo es lo previsto.
        assertThat(ProcedimientoPublico.enMarcha(vacante, null, null)).extracting(Etapa::estado)
                .containsOnly("PREVISTA");
    }

    @Test
    @DisplayName("Terminada, solo las etapas a las que llegó: las demás no se presentan como previstas")
    void terminada() {
        List<Etapa> etapas = ProcedimientoPublico.terminado(completa().build(),
                Set.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO"), "CRONOMETRADA");

        assertThat(etapas).extracting(Etapa::codigo).containsExactly("PERFIL_INTEGRAL", "PRUEBA_PUESTO");
        assertThat(etapas).extracting(Etapa::estado).containsOnly("HECHA");
    }
}
