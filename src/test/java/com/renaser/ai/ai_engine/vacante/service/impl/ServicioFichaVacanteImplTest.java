package com.renaser.ai.ai_engine.vacante.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.dto.DtosFichaVacante.FichaResponse;
import com.renaser.ai.ai_engine.vacante.dto.DtosFichaVacante.GuardarFicha;
import com.renaser.ai.ai_engine.vacante.entity.FichaVacante;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.FichaVacanteRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * La ficha de vacante: el tamaño se deriva y no se declara, COMPLETA se calcula, el
 * orden de los riesgos no admite huecos, y la sugerencia de pesos sale del tamaño.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La ficha de vacante")
class ServicioFichaVacanteImplTest {

    private static final Long ORG = 1L;
    private static final Long VACANTE = 10L;

    @Mock private FichaVacanteRepository fichas;
    @Mock private VacanteRepository vacantes;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private DuenoDelInstrumento dueno;
    @Mock private ServicioAuditoria auditoria;

    @InjectMocks
    private ServicioFichaVacanteImpl servicio;

    private final ContextoUsuario quien = new ContextoUsuario(
            5L, 6L, ORG, "EQUIPO", List.of(1L), Map.of("editar_vacante", "TODO"));

    private void conVacante() {
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORG)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).organizacionId(ORG).estado("BORRADOR").build()));
    }

    // Una ficha con todo lo obligatorio; los tests le quitan piezas.
    private static GuardarFicha completa() {
        return new GuardarFicha(
                "Ventas al doble", "La caja no cuadra", "Abre, cuadra, cierra",
                "Rosa lo hacía bien", "Somos 12", "Compras chicas", "A mí",
                "Fines de semana", "Excel y caja", null,
                12, 3,
                "Caja", "Margen", "Inventario", "Personal",
                "Manejo de caja", null,
                "Excel", null, null,
                "F4");
    }

    @Nested
    @DisplayName("El tamaño se deriva de la gente en la empresa")
    class Tamano {

        @Test
        @DisplayName("30 es MICRO, 31 es MEDIA, 200 es MEDIA, 201 es GRANDE")
        void losBordesExactos() {
            assertThat(ServicioFichaVacanteImpl.tamanoDe(30)).isEqualTo("MICRO");
            assertThat(ServicioFichaVacanteImpl.tamanoDe(31)).isEqualTo("MEDIA");
            assertThat(ServicioFichaVacanteImpl.tamanoDe(200)).isEqualTo("MEDIA");
            assertThat(ServicioFichaVacanteImpl.tamanoDe(201)).isEqualTo("GRANDE");
            assertThat(ServicioFichaVacanteImpl.tamanoDe(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Guardar")
    class Guardar {

        @Test
        @DisplayName("con todo lo obligatorio queda COMPLETA y con su tamaño")
        void quedaCompleta() {
            conVacante();
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.empty());
            when(fichas.save(any())).thenAnswer(i -> i.getArgument(0));
            sinPesosPublicados();

            FichaResponse r = servicio.guardar(quien, VACANTE, completa());

            assertThat(r.estado()).isEqualTo("COMPLETA");
            assertThat(r.tamano()).isEqualTo("MICRO");
        }

        @Test
        @DisplayName("a medias queda BORRADOR: sin familias no hay ficha completa")
        void aMediasEsBorrador() {
            conVacante();
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.empty());
            when(fichas.save(any())).thenAnswer(i -> i.getArgument(0));

            GuardarFicha sinFamilias = new GuardarFicha(
                    "V", "C", "D", "E", "S", "A", "J", "I", "R", null,
                    12, 3, "Caja", "Margen", "Inventario", "Personal",
                    "Caja", null, null, null, null, null);
            FichaResponse r = servicio.guardar(quien, VACANTE, sinFamilias);

            assertThat(r.estado()).isEqualTo("BORRADOR");
        }

        @Test
        @DisplayName("el espejo (Q10) es opcional: sin él también puede estar COMPLETA")
        void elEspejoNoBloquea() {
            conVacante();
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.empty());
            when(fichas.save(any())).thenAnswer(i -> i.getArgument(0));
            sinPesosPublicados();

            assertThat(servicio.guardar(quien, VACANTE, completa()).estado())
                    .isEqualTo("COMPLETA");
        }

        @Test
        @DisplayName("un hueco en los riesgos se rechaza: el orden es la velocidad de daño")
        void riesgoConHueco() {
            conVacante();
            GuardarFicha conHueco = new GuardarFicha(
                    null, null, null, null, null, null, null, null, null, null,
                    null, null,
                    "Caja", null, "Inventario", null,
                    null, null, null, null, null, null);

            assertThatThrownBy(() -> servicio.guardar(quien, VACANTE, conHueco))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("riesgo 3 sin riesgo 2");
        }

        @Test
        @DisplayName("eliminatoria 2 sin la 1 se rechaza")
        void eliminatoriaConHueco() {
            conVacante();
            GuardarFicha conHueco = new GuardarFicha(
                    null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null,
                    null, "Sin la primera",
                    null, null, null, null);

            assertThatThrownBy(() -> servicio.guardar(quien, VACANTE, conHueco))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("una vacante cerrada no cambia su ficha")
        void cerradaNoSeToca() {
            when(vacantes.findByIdAndOrganizacionId(VACANTE, ORG)).thenReturn(Optional.of(
                    Vacante.builder().id(VACANTE).organizacionId(ORG).estado("CERRADA").build()));

            assertThatThrownBy(() -> servicio.guardar(quien, VACANTE, completa()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("una vacante de otra organización ni aparece")
        void ajenaNoAparece() {
            when(vacantes.findByIdAndOrganizacionId(VACANTE, ORG)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.guardar(quien, VACANTE, completa()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("La sugerencia de pesos")
    class Sugerencia {

        @Test
        @DisplayName("MICRO sugiere la versión CAZATALENTOS · MICRO publicada")
        void microSugiereMicro() {
            conVacante();
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.empty());
            when(fichas.save(any())).thenAnswer(i -> i.getArgument(0));
            when(dueno.duenoDe(ORG, Instrumento.PESOS)).thenReturn(99L);
            when(versionesPesos.findByOrganizacionIdOrderByCreadoEnDesc(99L)).thenReturn(List.of(
                    pesos(11L, "CAZATALENTOS · MICRO", "PUBLICADA"),
                    pesos(12L, "CAZATALENTOS · MEDIA/GRANDE", "PUBLICADA"),
                    pesos(6L, "v4 embudo completo", "PUBLICADA")));

            FichaResponse r = servicio.guardar(quien, VACANTE, completa());

            assertThat(r.pesosSugeridos()).isNotNull();
            assertThat(r.pesosSugeridos().id()).isEqualTo(11L);
            assertThat(r.pesosSugeridos().yaAsignada()).isFalse();
        }

        @Test
        @DisplayName("una versión en borrador no se sugiere: la vacante no la aceptaría")
        void borradorNoSeSugiere() {
            conVacante();
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.empty());
            when(fichas.save(any())).thenAnswer(i -> i.getArgument(0));
            when(dueno.duenoDe(ORG, Instrumento.PESOS)).thenReturn(99L);
            when(versionesPesos.findByOrganizacionIdOrderByCreadoEnDesc(99L)).thenReturn(List.of(
                    pesos(11L, "CAZATALENTOS · MICRO", "BORRADOR")));

            assertThat(servicio.guardar(quien, VACANTE, completa()).pesosSugeridos()).isNull();
        }
    }

    @Test
    @DisplayName("Ver una ficha que no existe es 404, no una ficha vacía inventada")
    void verSinFicha() {
        conVacante();
        when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.ver(quien, VACANTE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Guardar de nuevo conserva la ficha (upsert), no crea otra")
    void guardarDosVecesActualiza() {
        conVacante();
        FichaVacante existente = FichaVacante.builder()
                .id(77L).vacanteId(VACANTE).organizacionId(ORG)
                .estado("BORRADOR").creadoEn(Instant.now()).build();
        when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.of(existente));
        when(fichas.save(any())).thenAnswer(i -> i.getArgument(0));
        sinPesosPublicados();

        FichaResponse r = servicio.guardar(quien, VACANTE, completa());

        assertThat(r.id()).isEqualTo(77L);
        assertThat(r.estado()).isEqualTo("COMPLETA");
    }

    private void sinPesosPublicados() {
        lenient().when(dueno.duenoDe(ORG, Instrumento.PESOS)).thenReturn(99L);
        lenient().when(versionesPesos.findByOrganizacionIdOrderByCreadoEnDesc(99L))
                .thenReturn(List.of());
    }

    private static VersionPesos pesos(Long id, String etiqueta, String estado) {
        return VersionPesos.builder().id(id).etiqueta(etiqueta).estado(estado)
                .publicadaEn(Instant.now()).build();
    }
}
