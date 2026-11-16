package com.renaser.ai.ai_engine.informe.service.impl;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.ArchivoArmado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.InformesDelPostulante;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PostulanteDelVisor;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.VisorDeLaVacante;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme.Armado;
import com.renaser.ai.ai_engine.informe.service.EtapaDelInforme;
import com.renaser.ai.ai_engine.informe.service.InformesDePrueba;
import com.renaser.ai.ai_engine.informe.service.PdfDelInforme;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.FilaRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPerfilIntegralPanel;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.EnlaceArchivo;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.ServicioPostulacionesPanel;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que el servicio decide antes y después del armado: qué etapas ve cada permiso, el enlace
 * firmado al currículum, el .zip de un postulante y el de una vacante con su aviso de lo que
 * falta, y la lista del visor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("El servicio de los informes por etapa")
class ServicioInformesImplTest {

    private static final Long POSTULACION = 10L;
    private static final Long VACANTE = 5L;
    private static final String VER_CV = EtapaDelInforme.CV.permiso();
    private static final String VER_EVALUACION = EtapaDelInforme.EVALUACION.permiso();
    private static final String VER_PRUEBA = EtapaDelInforme.PRUEBA.permiso();
    private static final String DESCARGAR = "descargar_entregables";
    private static final byte[] PDF = "%PDF-falso".getBytes(StandardCharsets.UTF_8);

    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private ArmadoDelInforme armado;
    @Mock private PdfDelInforme generadorPdf;
    @Mock private ServicioPostulacionesPanel archivos;
    @Mock private ServicioPerfilIntegralPanel tandas;
    @Mock private NotaEtapaRepository notasEtapa;

    @InjectMocks private ServicioInformesImpl servicio;

    private final Postulacion postulacion = Postulacion.builder().id(POSTULACION).vacanteId(VACANTE).build();

    @BeforeEach
    void visibleYConPdf() {
        when(alcance.laPostulacionVisible(any(), anyLong(), any())).thenReturn(postulacion);
        when(generadorPdf.generar(any())).thenReturn(PDF);
    }

    // ================================================================= permisos y estados

    @Nested
    @DisplayName("Los permisos")
    class Permisos {

        @Test
        @DisplayName("sin el permiso de ninguna etapa no se ve nada: ni estados, ni .zip, ni visor")
        void sinNingunPermiso() {
            ContextoUsuario nadie = quien();
            assertThatThrownBy(() -> servicio.estados(nadie, POSTULACION)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.zipDelPostulante(nadie, POSTULACION))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.visor(nadie, VACANTE)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.zipDeLaVacante(nadie, VACANTE))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("un informe sin el permiso de su etapa es un 403, aunque tenga otros")
        void sinElDeLaEtapa() {
            ContextoUsuario soloCv = quien(VER_CV);
            assertThatThrownBy(() -> servicio.informe(soloCv, POSTULACION, "prueba"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(VER_PRUEBA);
            verify(armado, never()).armar(any(), any(), eq(true));
        }

        @Test
        @DisplayName("los estados traen solo las etapas que se ven: la que queda fuera de su alcance no sale")
        void estadosDeLasQueSeVen() {
            ContextoUsuario quien = quien(VER_CV, VER_EVALUACION);
            when(alcance.laPostulacionVisible(any(), anyLong(), eq(VER_EVALUACION)))
                    .thenThrow(new IllegalStateException("fuera de alcance"));
            EstadoDeUnInforme delCv = new EstadoDeUnInforme("CV", true, null, BigDecimal.valueOf(74));
            when(armado.estado(postulacion, EtapaDelInforme.CV)).thenReturn(delCv);

            InformesDelPostulante estados = servicio.estados(quien, POSTULACION);

            assertThat(estados.postulacionId()).isEqualTo(POSTULACION);
            assertThat(estados.informes()).containsExactly(delCv);
        }
    }

    // ================================================================= un informe

    @Nested
    @DisplayName("Un informe y su enlace al currículum")
    class UnInforme {

        @Test
        @DisplayName("con permiso de descargar, el enlace firmado al CV va primero y las redes después")
        void conEnlaceAlCv() {
            armado(EtapaDelInforme.CV, sinEnlaces(), 33L);
            when(archivos.enlaceDeVolcado(any(), eq(33L))).thenReturn(Optional.of(
                    new EnlaceArchivo("https://almacen.example/cv.pdf?firma=2", Instant.now(), "cv.pdf")));

            Informe informe = servicio.informe(quien(VER_CV, DESCARGAR), POSTULACION, "cv");

            assertThat(informe.enlaces()).extracting(Enlace::tipo).containsExactly("CV", "LINKEDIN");
            assertThat(informe.enlaces().get(0).url()).isEqualTo("https://almacen.example/cv.pdf?firma=2");
            assertThat(informe.nombreArchivo()).isEqualTo(sinEnlaces().nombreArchivo());
        }

        @Test
        @DisplayName("sin permiso de descargar, sin archivo, sin enlace firmado o con uno que no es web, solo las redes")
        void soloLasRedes() {
            armado(EtapaDelInforme.CV, sinEnlaces(), 33L);
            assertThat(servicio.informe(quien(VER_CV), POSTULACION, "cv").enlaces())
                    .extracting(Enlace::tipo).containsExactly("LINKEDIN");

            when(archivos.enlaceDeVolcado(any(), eq(33L))).thenReturn(Optional.empty());
            assertThat(servicio.informe(quien(VER_CV, DESCARGAR), POSTULACION, "cv").enlaces()).hasSize(1);

            when(archivos.enlaceDeVolcado(any(), eq(33L))).thenReturn(Optional.of(
                    new EnlaceArchivo("javascript:alert(1)", Instant.now(), "cv.pdf")));
            assertThat(servicio.informe(quien(VER_CV, DESCARGAR), POSTULACION, "cv").enlaces()).hasSize(1);

            when(archivos.enlaceDeVolcado(any(), eq(33L))).thenThrow(new IllegalStateException("almacén caído"));
            assertThat(servicio.informe(quien(VER_CV, DESCARGAR), POSTULACION, "cv").enlaces()).hasSize(1);

            armado(EtapaDelInforme.CV, sinEnlaces(), null);
            assertThat(servicio.informe(quien(VER_CV, DESCARGAR), POSTULACION, "cv").enlaces()).hasSize(1);
        }

        @Test
        @DisplayName("si todavía no hay informe lo dice con el motivo del estado")
        void todaviaNoHay() {
            when(armado.armar(postulacion, EtapaDelInforme.EVALUACION, true)).thenReturn(Optional.empty());
            when(armado.estado(postulacion, EtapaDelInforme.EVALUACION)).thenReturn(
                    new EstadoDeUnInforme("EVALUACION", false, "La IA está calificando", null));

            ContextoUsuario quien = quien(VER_EVALUACION);
            assertThatThrownBy(() -> servicio.informe(quien, POSTULACION, "evaluacion"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Todavía no hay informe de la evaluación: La IA está calificando.");
        }

        @Test
        @DisplayName("el PDF lleva el nombre del informe y su tipo")
        void pdf() {
            armado(EtapaDelInforme.CV, sinEnlaces(), null);

            ArchivoArmado archivo = servicio.pdf(quien(VER_CV), POSTULACION, "CV");

            assertThat(archivo.nombre()).isEqualTo(sinEnlaces().nombreArchivo());
            assertThat(archivo.tipo()).isEqualTo("application/pdf");
            assertThat(archivo.contenido()).isEqualTo(PDF);
        }
    }

    // ================================================================= el .zip de un postulante

    @Nested
    @DisplayName("El .zip de un postulante")
    class ZipDelPostulante {

        @Test
        @DisplayName("trae los informes que hay y un aviso con los que faltan: sin permiso o todavía sin calificar")
        void conAviso() throws IOException {
            armado(EtapaDelInforme.CV, sinEnlaces(), null);
            when(armado.armar(postulacion, EtapaDelInforme.PRUEBA, true)).thenReturn(Optional.empty());
            when(armado.estado(postulacion, EtapaDelInforme.PRUEBA)).thenReturn(
                    new EstadoDeUnInforme("PRUEBA", false, "Todavía no tiene calificación", null));

            ArchivoArmado zip = servicio.zipDelPostulante(quien(VER_CV, VER_PRUEBA), POSTULACION);

            assertThat(zip.tipo()).isEqualTo("application/zip");
            assertThat(zip.nombre()).isEqualTo("Informes - Ana Torres Quispe - Analista de Recursos Humanos.zip");
            Map<String, String> entradas = entradas(zip.contenido());
            assertThat(entradas).containsOnlyKeys("1 - Informe del CV.pdf", "LEEME - informes que faltan.txt");
            assertThat(entradas.get("LEEME - informes que faltan.txt")).contains(
                    "Faltan estos informes:",
                    "- Informe de la evaluación: no tienes permiso para verlo.",
                    "- Informe de la prueba del puesto: Todavía no tiene calificación.");
        }

        @Test
        @DisplayName("con todo disponible no lleva aviso, y sin vacante el nombre es solo el del candidato")
        void sinAviso() throws IOException {
            Informe sinVacante = conVacante(sinEnlaces(), null);
            for (EtapaDelInforme etapa : EtapaDelInforme.values()) {
                armado(etapa, sinVacante, null);
            }

            ArchivoArmado zip = servicio.zipDelPostulante(quien(VER_CV, VER_EVALUACION, VER_PRUEBA), POSTULACION);

            assertThat(zip.nombre()).isEqualTo("Informes - Ana Torres Quispe.zip");
            assertThat(entradas(zip.contenido())).containsOnlyKeys("1 - Informe del CV.pdf",
                    "2 - Informe de la evaluación.pdf", "3 - Informe de la prueba del puesto.pdf");
        }

        @Test
        @DisplayName("si no hay ninguno, no arma un .zip vacío: dice por qué")
        void ninguno() {
            when(armado.armar(any(), any(), eq(true))).thenReturn(Optional.empty());
            when(armado.estado(any(Postulacion.class), any())).thenReturn(
                    new EstadoDeUnInforme("CV", false, "La calificación falló", null));

            ContextoUsuario quien = quien(VER_CV);
            assertThatThrownBy(() -> servicio.zipDelPostulante(quien, POSTULACION))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Informe del currículum: La calificación falló.");
        }
    }

    // ================================================================= la vacante

    @Nested
    @DisplayName("La vacante: el visor y su .zip")
    class LaVacante {

        @Test
        @DisplayName("el visor enseña de cada postulante solo las notas de las etapas que se pueden ver")
        void visor() {
            FilaRanking ana = fila(1, 10L, "Ana");
            FilaRanking luis = fila(2, 11L, "Luis");
            ranking(List.of(ana, luis));
            when(notasEtapa.findByPostulacionIdInAndEtapaCodigo(List.of(10L, 11L), "PRUEBA_PUESTO")).thenReturn(
                    List.of(NotaEtapa.builder().postulacionId(10L).puntaje(BigDecimal.valueOf(88)).build()));

            VisorDeLaVacante visor = servicio.visor(quien(VER_CV, VER_PRUEBA), VACANTE);

            assertThat(visor.vacante()).isEqualTo("Analista");
            assertThat(visor.etapas()).containsExactly("CV", "PRUEBA");
            assertThat(visor.postulantes()).extracting(PostulanteDelVisor::notaCv,
                            PostulanteDelVisor::notaEvaluacion, PostulanteDelVisor::notaPrueba)
                    .containsExactly(
                            tuple(BigDecimal.valueOf(70), null, BigDecimal.valueOf(88)),
                            tuple(BigDecimal.valueOf(70), null, null));

            VisorDeLaVacante soloEvaluacion = servicio.visor(quien(VER_EVALUACION), VACANTE);
            assertThat(soloEvaluacion.postulantes()).extracting(PostulanteDelVisor::notaCv,
                    PostulanteDelVisor::notaEvaluacion, PostulanteDelVisor::notaPrueba).containsOnly(
                    tuple(null, BigDecimal.valueOf(65), null));
        }

        @Test
        @DisplayName("una vacante sin postulantes da un visor vacío sin preguntar por las notas")
        void visorVacio() {
            ranking(List.of());
            assertThat(servicio.visor(quien(VER_PRUEBA), VACANTE).postulantes()).isEmpty();
            verify(notasEtapa, never()).findByPostulacionIdInAndEtapaCodigo(any(), any());
        }

        @Test
        @DisplayName("el .zip lleva una carpeta por postulante visible, sin pisar nombres repetidos, y el aviso de lo que falta")
        void zipDeLaVacante() throws IOException {
            Postulacion otra = Postulacion.builder().id(11L).build();
            ranking(List.of(fila(1, 10L, "Ana"), fila(2, 11L, "Ana"), fila(3, 12L, "Oculta")));
            when(alcance.laPostulacionVisible(any(), eq(11L), any())).thenReturn(otra);
            when(alcance.laPostulacionVisible(any(), eq(12L), any())).thenThrow(new IllegalStateException("404"));
            armado(EtapaDelInforme.CV, sinEnlaces(), null);
            when(armado.armar(otra, EtapaDelInforme.CV, true)).thenReturn(Optional.empty());
            when(armado.estado(otra, EtapaDelInforme.CV)).thenReturn(
                    new EstadoDeUnInforme("CV", false, "La IA está calificando", null));

            ArchivoArmado zip = servicio.zipDeLaVacante(quien(VER_CV), VACANTE);

            assertThat(zip.tipo()).isEqualTo("application/zip");
            assertThat(zip.nombre()).startsWith("Informes - Analista - ").endsWith(".zip");
            Map<String, String> entradas = entradas(zip.contenido());
            assertThat(entradas).containsOnlyKeys("01 - Ana/1 - Informe del CV.pdf", "02 - Ana/",
                    "LEEME - informes que faltan.txt");
            assertThat(entradas.get("LEEME - informes que faltan.txt"))
                    .contains("- Ana · Informe del currículum: La IA está calificando.");
        }

        @Test
        @DisplayName("las carpetas con el mismo nombre se numeran")
        void carpetasRepetidas() throws IOException {
            ranking(List.of(fila(1, 10L, "Ana"), fila(1, 10L, "Ana")));
            armado(EtapaDelInforme.CV, sinEnlaces(), null);

            Map<String, String> entradas = entradas(servicio.zipDeLaVacante(quien(VER_CV), VACANTE).contenido());

            assertThat(entradas).containsOnlyKeys("01 - Ana/1 - Informe del CV.pdf",
                    "01 - Ana (2)/1 - Informe del CV.pdf");
        }

        @Test
        @DisplayName("sin ningún postulante visible no hay .zip que dar")
        void sinNadie() {
            ranking(List.of(fila(1, 12L, "Oculta")));
            when(alcance.laPostulacionVisible(any(), eq(12L), any())).thenThrow(new IllegalStateException("404"));

            ContextoUsuario quien = quien(VER_CV);
            assertThatThrownBy(() -> servicio.zipDeLaVacante(quien, VACANTE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Todavía no hay ningún informe en esta vacante.");
        }
    }

    // ================================================================= ayudas

    private static ContextoUsuario quien(String... permisos) {
        Map<String, String> mapa = new LinkedHashMap<>();
        Arrays.stream(permisos).forEach(p -> mapa.put(p, "TODO"));
        return new ContextoUsuario(1L, 2L, 3L, "EQUIPO", List.of(), mapa);
    }

    private void armado(EtapaDelInforme etapa, Informe informe, Long archivoCvId) {
        when(armado.armar(postulacion, etapa, true)).thenReturn(Optional.of(new Armado(informe, archivoCvId)));
    }

    /** El informe del CV de prueba con solo su LinkedIn: el enlace al CV lo pone el servicio. */
    private static Informe sinEnlaces() {
        Informe cv = InformesDePrueba.cv(1);
        return conEnlaces(cv, cv.enlaces().stream().filter(e -> !"CV".equals(e.tipo())).toList(), cv.vacante());
    }

    private static Informe conVacante(Informe i, String vacante) {
        return conEnlaces(i, i.enlaces(), vacante);
    }

    private static Informe conEnlaces(Informe i, List<Enlace> enlaces, String vacante) {
        return new Informe(i.etapa(), i.titulo(), i.empresa(), vacante, i.postulacionId(), i.candidato(),
                i.correo(), i.generadoEn(), i.nota(), i.grupo(), i.datosClave(), i.recuentos(), i.experiencia(),
                i.resumen(), i.evaluado(), i.bloques(), enlaces, i.detalleTitulo(), i.detalle(), i.detallePie(),
                i.anexo(), i.aviso(), i.textosEnCamino(), i.nombreArchivo());
    }

    private void ranking(List<FilaRanking> filas) {
        RankingVacante ranking = mock(RankingVacante.class);
        when(ranking.filas()).thenReturn(filas);
        when(ranking.vacante()).thenReturn("Analista");
        when(ranking.puesto()).thenReturn("Analista de RR. HH.");
        when(tandas.ranking(any(), eq(VACANTE))).thenReturn(ranking);
    }

    private static FilaRanking fila(int puesto, Long postulacionId, String candidato) {
        FilaRanking fila = mock(FilaRanking.class);
        when(fila.puesto()).thenReturn(puesto);
        when(fila.postulacionId()).thenReturn(postulacionId);
        when(fila.candidato()).thenReturn(candidato);
        when(fila.notaCurriculum()).thenReturn(BigDecimal.valueOf(70));
        when(fila.notaEtapa()).thenReturn(BigDecimal.valueOf(65));
        return fila;
    }

    private static Map<String, String> entradas(byte[] zip) throws IOException {
        Map<String, String> salida = new LinkedHashMap<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = entrada.getNextEntry()) != null) {
                salida.put(e.getName(), new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return salida;
    }
}
