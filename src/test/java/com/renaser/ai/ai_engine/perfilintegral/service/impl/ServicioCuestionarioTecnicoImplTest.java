package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.CorregirPreguntaTecnica;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.CuestionarioResponse;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El cuestionario técnico desde el panel: generar exige ficha COMPLETA, el borrador manda
 * sobre la publicada, la publicación re-pasa la aduana, y nada de esto toca bancos ajenos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El cuestionario técnico en el panel")
class ServicioCuestionarioTecnicoImplTest {

    private static final Long ORG = 1L;
    private static final Long VACANTE = 50L;

    @Mock private VacanteRepository vacantes;
    @Mock private FichaVacanteRepository fichas;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private PreguntaRepository preguntas;
    @Mock private ColaCalificacionIa cola;
    @Mock private ServicioAuditoria auditoria;

    @InjectMocks
    private ServicioCuestionarioTecnicoImpl servicio;

    private final ContextoUsuario quien = new ContextoUsuario(
            5L, 6L, ORG, "EQUIPO", List.of(1L), Map.of("editar_vacante", "TODO"));

    private void conVacante() {
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORG)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).organizacionId(ORG).estado("BORRADOR").build()));
    }

    @Nested
    @DisplayName("Generar")
    class Generar {

        @Test
        @DisplayName("con la ficha COMPLETA encola al REDACTOR")
        void conFichaCompleta() {
            conVacante();
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.of(
                    FichaVacante.builder().estado("COMPLETA").build()));
            when(cola.encolarRedactor(ORG, VACANTE)).thenReturn(true);

            assertThat(servicio.generar(quien, VACANTE)).isTrue();
        }

        @Test
        @DisplayName("con la ficha a medias no se genera nada")
        void conFichaAMedias() {
            conVacante();
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.of(
                    FichaVacante.builder().estado("BORRADOR").build()));

            assertThatThrownBy(() -> servicio.generar(quien, VACANTE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("a medias");
            verify(cola, never()).encolarRedactor(any(), any());
        }

        @Test
        @DisplayName("si la cola no encoló (vivo o IA apagada), se dice tal cual: false")
        void siLaColaNoEncolo() {
            conVacante();
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.of(
                    FichaVacante.builder().estado("COMPLETA").build()));
            when(cola.encolarRedactor(ORG, VACANTE)).thenReturn(false);

            assertThat(servicio.generar(quien, VACANTE)).isFalse();
            verify(auditoria, never()).registrar(any(), any(), any(), any(), any(),
                    any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Ver")
    class Ver {

        @Test
        @DisplayName("el borrador manda sobre la publicada: es la copia de trabajo")
        void elBorradorManda() {
            conVacante();
            when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "BORRADOR"))
                    .thenReturn(Optional.of(VersionBanco.builder()
                            .id(31L).estado("BORRADOR").creadoEn(Instant.now()).build()));
            when(preguntas.findByVersionBancoIdOrderByOrden(31L)).thenReturn(List.of(
                    Pregunta.builder().id(400L).codigo("T01").enunciado("¿?").orden(1).build()));
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.of(
                    FichaVacante.builder().actualizadoEn(Instant.now().minusSeconds(60)).build()));
            when(cola.comoVaElRedactor(VACANTE)).thenReturn("LISTA");

            CuestionarioResponse r = servicio.ver(quien, VACANTE);

            assertThat(r.versionBancoId()).isEqualTo(31L);
            assertThat(r.estado()).isEqualTo("BORRADOR");
            assertThat(r.desactualizado()).isFalse();
            assertThat(r.generacion()).isEqualTo("LISTA");
            assertThat(r.preguntas()).hasSize(1);
        }

        @Test
        @DisplayName("la ficha editada después de generar marca el cuestionario desactualizado")
        void laFichaNuevaDesactualiza() {
            conVacante();
            Instant generado = Instant.now().minusSeconds(3600);
            when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "BORRADOR"))
                    .thenReturn(Optional.of(VersionBanco.builder()
                            .id(31L).estado("BORRADOR").creadoEn(generado).build()));
            when(preguntas.findByVersionBancoIdOrderByOrden(31L)).thenReturn(List.of());
            when(fichas.findByVacanteId(VACANTE)).thenReturn(Optional.of(
                    FichaVacante.builder().actualizadoEn(Instant.now()).build()));
            when(cola.comoVaElRedactor(VACANTE)).thenReturn("SIN_PEDIR");

            assertThat(servicio.ver(quien, VACANTE).desactualizado()).isTrue();
        }

        @Test
        @DisplayName("sin cuestionario ni trabajo: vacío y SIN_PEDIR, no un error")
        void sinNada() {
            conVacante();
            when(versionesBanco.findFirstByVacanteIdAndEstado(any(), any()))
                    .thenReturn(Optional.empty());
            when(cola.comoVaElRedactor(VACANTE)).thenReturn("SIN_PEDIR");

            CuestionarioResponse r = servicio.ver(quien, VACANTE);

            assertThat(r.versionBancoId()).isNull();
            assertThat(r.generacion()).isEqualTo("SIN_PEDIR");
            assertThat(r.preguntas()).isEmpty();
        }

        @Test
        @DisplayName("un trabajo EN_ESPERA por tope se cuenta como EN_CURSO: va a salir")
        void enEsperaEsEnCurso() {
            conVacante();
            when(versionesBanco.findFirstByVacanteIdAndEstado(any(), any()))
                    .thenReturn(Optional.empty());
            when(cola.comoVaElRedactor(VACANTE)).thenReturn("EN_CURSO");

            assertThat(servicio.ver(quien, VACANTE).generacion()).isEqualTo("EN_CURSO");
        }
    }

    @Nested
    @DisplayName("Una vacante de otra organización ni aparece")
    class VacanteAjena {

        @Test
        @DisplayName("ver, generar y publicar responden «no existe», nunca «prohibido»")
        void nadaAjenoSeVe() {
            when(vacantes.findByIdAndOrganizacionId(VACANTE, ORG)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.ver(quien, VACANTE))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> servicio.generar(quien, VACANTE))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> servicio.publicar(quien, VACANTE))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(cola, never()).encolarRedactor(any(), any());
        }
    }

    @Nested
    @DisplayName("Corregir")
    class Corregir {

        @Test
        @DisplayName("una pregunta de otro banco no se toca: 404, no un cruce silencioso")
        void preguntaAjena() {
            conVacante();
            when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "BORRADOR"))
                    .thenReturn(Optional.of(VersionBanco.builder().id(31L).build()));
            when(preguntas.findById(400L)).thenReturn(Optional.of(
                    Pregunta.builder().id(400L).versionBancoId(999L).build()));

            assertThatThrownBy(() -> servicio.corregirPregunta(quien, VACANTE, 400L,
                    new CorregirPreguntaTecnica("Nuevo", "c3", "c4", "señal")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("sin borrador no hay nada que corregir")
        void sinBorrador() {
            conVacante();
            when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "BORRADOR"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.corregirPregunta(quien, VACANTE, 400L,
                    new CorregirPreguntaTecnica("Nuevo", "c3", "c4", "señal")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Publicar")
    class Publicar {

        private Pregunta abierta(long id, String codigo) {
            return Pregunta.builder().id(id).versionBancoId(31L).codigo(codigo)
                    .enunciado("¿Qué controlabas y de qué monto?").tipo("ABIERTA")
                    .c3Esperado("montos").c4Esperado("faltantes").senalDeCero("genérica")
                    .esPuntuable(true).build();
        }

        @Test
        @DisplayName("publica, y archiva a la publicada anterior DE ESTA vacante")
        void publicaYArchivaLaSuya() {
            conVacante();
            VersionBanco borrador = VersionBanco.builder()
                    .id(31L).vacanteId(VACANTE).estado("BORRADOR")
                    .nivelPuestoCodigo("EJECUCION").build();
            VersionBanco publicadaVieja = VersionBanco.builder()
                    .id(20L).vacanteId(VACANTE).estado("PUBLICADA").build();
            when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "BORRADOR"))
                    .thenReturn(Optional.of(borrador));
            when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "PUBLICADA"))
                    .thenReturn(Optional.of(publicadaVieja));
            // Las 8 de EJECUCION, completas.
            when(preguntas.findByVersionBancoIdOrderByOrden(31L)).thenReturn(List.of(
                    abierta(1, "T01"), abierta(2, "T02"), abierta(3, "T03"), abierta(4, "T04"),
                    abierta(5, "T05"), abierta(6, "T06"), abierta(7, "T07"), abierta(8, "T08")));

            servicio.publicar(quien, VACANTE);

            assertThat(borrador.getEstado()).isEqualTo("PUBLICADA");
            assertThat(borrador.getPublicadaPorUsuarioId()).isEqualTo(5L);
            assertThat(publicadaVieja.getEstado()).isEqualTo("ARCHIVADA");
        }

        @Test
        @DisplayName("si el dueño dejó el borrador sin guía, la aduana lo frena")
        void laAduanaFrena() {
            conVacante();
            when(versionesBanco.findFirstByVacanteIdAndEstado(VACANTE, "BORRADOR"))
                    .thenReturn(Optional.of(VersionBanco.builder()
                            .id(31L).vacanteId(VACANTE).estado("BORRADOR")
                            .nivelPuestoCodigo("EJECUCION").build()));
            Pregunta sinGuia = abierta(1, "T01");
            sinGuia.setSenalDeCero(null);
            when(preguntas.findByVersionBancoIdOrderByOrden(31L))
                    .thenReturn(List.of(sinGuia));

            assertThatThrownBy(() -> servicio.publicar(quien, VACANTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("aduana");
        }
    }
}
