package com.renaser.ai.ai_engine.informe.service.impl;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Anexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.ArchivoArmado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Celular;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EntregableAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.InformesDelPostulante;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PostulanteDelVisor;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.VisorDeLaVacante;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme.Armado;
import com.renaser.ai.ai_engine.informe.service.CelularesDelVisor;
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
import static org.mockito.ArgumentMatchers.anyList;
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
    @Mock private CelularesDelVisor celulares;

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
        @DisplayName("dos postulantes con el mismo nombre y el mismo puesto del ranking no se pisan: cada carpeta lleva su lugar en la lista")
        void carpetasRepetidas() throws IOException {
            ranking(List.of(fila(1, 10L, "Ana"), fila(1, 10L, "Ana")));
            armado(EtapaDelInforme.CV, sinEnlaces(), null);

            Map<String, String> entradas = entradas(servicio.zipDeLaVacante(quien(VER_CV), VACANTE).contenido());

            assertThat(entradas).containsOnlyKeys("01 - Ana/1 - Informe del CV.pdf",
                    "02 - Ana/1 - Informe del CV.pdf");
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

    // ================================================================= el orden por nota combinada

    /**
     * El orden del visor y de su .zip (spec la-ia-sabe-que-dia-es-hoy, AC-09 a AC-11): arriba
     * quienes tienen las tres notas, por su nota combinada; debajo los demás, en el orden del
     * ranking.
     */
    @Nested
    @DisplayName("El orden por nota combinada")
    class ElOrden {

        /**
         * La vacante de la captura del 16/11/2026: en el ranking primero los que no tienen
         * prueba; los tres con prueba calificada tienen ponderado.
         */
        private void vacanteDeLaCaptura() {
            ranking(List.of(fila(1, 21L, "Ximena Aleman"), fila(2, 22L, "Ruth Suri"),
                    fila(3, 23L, "Alex Aquise"), fila(4, 24L, "Robert Villegas"),
                    fila(5, 25L, "Mayra Garcia-Godos"), fila(6, 26L, "Christian Casahuaman"),
                    fila(7, 27L, "Diego Tejada")));
            when(notasEtapa.findByPostulacionIdInAndEtapaCodigo(anyList(), eq("PRUEBA_PUESTO"))).thenReturn(List.of(
                    nota(22L, 94), nota(24L, 93), nota(25L, 88),
                    // Empezada pero sin calificar: no es tercera nota.
                    NotaEtapa.builder().postulacionId(26L).puntaje(null).build()));
            // Perfil Integral 82 · 70 · 73 y prueba 93 · 94 · 88, con pesos 40 y 30.
            when(tandas.ponderadosDeLoRendido(any(), eq(VACANTE))).thenReturn(Map.of(
                    24L, new BigDecimal("86.71"), 22L, new BigDecimal("80.29"), 25L, new BigDecimal("79.43")));
        }

        @Test
        @DisplayName("AC-09 · los tres con las tres notas van primero, de mayor a menor nota combinada y numerados 1, 2 y 3; los demás debajo en el orden del ranking")
        void arribaLosDeLasTresNotas() {
            vacanteDeLaCaptura();

            VisorDeLaVacante visor = servicio.visor(quien(VER_CV, VER_EVALUACION, VER_PRUEBA), VACANTE);

            assertThat(visor.postulantes()).extracting(PostulanteDelVisor::puesto, PostulanteDelVisor::candidato)
                    .containsExactly(
                            tuple(1, "Robert Villegas"),
                            tuple(2, "Ruth Suri"),
                            tuple(3, "Mayra Garcia-Godos"),
                            tuple(4, "Ximena Aleman"),
                            tuple(5, "Alex Aquise"),
                            tuple(6, "Christian Casahuaman"),
                            tuple(7, "Diego Tejada"));
        }

        @Test
        @DisplayName("a igual ponderado y prueba, decide la evaluación; a igualdad completa, el ranking")
        void desempates() {
            FilaRanking ana = fila(1, 31L, "Ana");
            FilaRanking luis = fila(2, 32L, "Luis");
            FilaRanking eva = fila(3, 33L, "Eva");
            when(eva.notaEtapa()).thenReturn(BigDecimal.valueOf(90));
            ranking(List.of(ana, luis, eva));
            when(notasEtapa.findByPostulacionIdInAndEtapaCodigo(anyList(), eq("PRUEBA_PUESTO")))
                    .thenReturn(List.of(nota(31L, 80), nota(32L, 80), nota(33L, 80)));
            when(tandas.ponderadosDeLoRendido(any(), eq(VACANTE))).thenReturn(Map.of(
                    31L, BigDecimal.valueOf(75), 32L, BigDecimal.valueOf(75), 33L, BigDecimal.valueOf(75)));

            assertThat(servicio.visor(quien(VER_CV), VACANTE).postulantes())
                    .extracting(PostulanteDelVisor::candidato).containsExactly("Eva", "Ana", "Luis");
        }

        @Test
        @DisplayName("el ponderado sale a quien ve las tres notas, y solo de quien tiene las tres")
        void ponderadoALaVista() {
            ranking(List.of(fila(1, 51L, "Con las tres"), fila(2, 52L, "Sin prueba")));
            when(notasEtapa.findByPostulacionIdInAndEtapaCodigo(anyList(), eq("PRUEBA_PUESTO")))
                    .thenReturn(List.of(nota(51L, 90)));
            when(tandas.ponderadosDeLoRendido(any(), eq(VACANTE)))
                    .thenReturn(Map.of(51L, BigDecimal.valueOf(83), 52L, BigDecimal.valueOf(60)));

            assertThat(servicio.visor(quien(VER_CV, VER_EVALUACION, VER_PRUEBA), VACANTE).postulantes())
                    .extracting(PostulanteDelVisor::candidato, PostulanteDelVisor::ponderado)
                    .containsExactly(tuple("Con las tres", BigDecimal.valueOf(83)), tuple("Sin prueba", null));
            assertThat(servicio.visor(quien(VER_CV, VER_EVALUACION), VACANTE).postulantes())
                    .extracting(PostulanteDelVisor::ponderado).containsOnlyNulls();
        }

        @Test
        @DisplayName("AC-10 · sin permiso para ver la nota del CV el orden es el mismo y la columna sale vacía")
        void elOrdenNoDependeDeLosPermisos() {
            vacanteDeLaCaptura();
            List<String> deDireccion = servicio.visor(quien(VER_CV, VER_EVALUACION, VER_PRUEBA), VACANTE)
                    .postulantes().stream().map(PostulanteDelVisor::candidato).toList();

            VisorDeLaVacante sinCv = servicio.visor(quien(VER_EVALUACION), VACANTE);

            assertThat(sinCv.postulantes()).extracting(PostulanteDelVisor::candidato)
                    .containsExactlyElementsOf(deDireccion);
            assertThat(sinCv.postulantes()).extracting(PostulanteDelVisor::notaCv).containsOnlyNulls();
            assertThat(sinCv.postulantes()).extracting(PostulanteDelVisor::notaPrueba).containsOnlyNulls();
        }

        @Test
        @DisplayName("una vacante sin pesos de etapa no tiene ponderado: queda en el orden del ranking")
        void sinPesosElOrdenDelRanking() {
            ranking(List.of(fila(1, 41L, "Sin prueba"), fila(2, 42L, "Con prueba")));
            when(notasEtapa.findByPostulacionIdInAndEtapaCodigo(anyList(), eq("PRUEBA_PUESTO")))
                    .thenReturn(List.of(nota(42L, 99)));
            when(tandas.ponderadosDeLoRendido(any(), eq(VACANTE))).thenReturn(Map.of());

            assertThat(servicio.visor(quien(VER_PRUEBA), VACANTE).postulantes())
                    .extracting(PostulanteDelVisor::puesto, PostulanteDelVisor::candidato)
                    .containsExactly(tuple(1, "Sin prueba"), tuple(2, "Con prueba"));
        }

        @Test
        @DisplayName("sin nota del CV no cuenta como tres notas aunque tenga ponderado")
        void sinNotaDelCvNoVaArriba() {
            FilaRanking sinCv = fila(2, 52L, "Sin CV");
            when(sinCv.notaCurriculum()).thenReturn(null);
            ranking(List.of(fila(1, 51L, "Primero"), sinCv));
            when(notasEtapa.findByPostulacionIdInAndEtapaCodigo(anyList(), eq("PRUEBA_PUESTO")))
                    .thenReturn(List.of(nota(52L, 99)));
            when(tandas.ponderadosDeLoRendido(any(), eq(VACANTE))).thenReturn(Map.of(52L, BigDecimal.valueOf(90)));

            assertThat(servicio.visor(quien(VER_CV), VACANTE).postulantes())
                    .extracting(PostulanteDelVisor::candidato).containsExactly("Primero", "Sin CV");
        }

        @Test
        @DisplayName("AC-11 · las carpetas del .zip van numeradas en el mismo orden que el visor")
        void elZipEnElMismoOrden() throws IOException {
            vacanteDeLaCaptura();
            for (long id = 21L; id <= 27L; id++) {
                Postulacion suya = Postulacion.builder().id(id).build();
                when(alcance.laPostulacionVisible(any(), eq(id), any())).thenReturn(suya);
                when(armado.armar(suya, EtapaDelInforme.CV, true))
                        .thenReturn(Optional.of(new Armado(sinEnlaces(), null)));
            }

            Map<String, String> entradas = entradas(servicio.zipDeLaVacante(quien(VER_CV), VACANTE).contenido());

            assertThat(entradas.keySet()).containsExactly(
                    "01 - Robert Villegas/1 - Informe del CV.pdf",
                    "02 - Ruth Suri/1 - Informe del CV.pdf",
                    "03 - Mayra Garcia-Godos/1 - Informe del CV.pdf",
                    "04 - Ximena Aleman/1 - Informe del CV.pdf",
                    "05 - Alex Aquise/1 - Informe del CV.pdf",
                    "06 - Christian Casahuaman/1 - Informe del CV.pdf",
                    "07 - Diego Tejada/1 - Informe del CV.pdf");
        }
    }

    // ================================================================= el celular

    @Nested
    @DisplayName("El celular en el visor")
    class ElCelular {

        @Test
        @DisplayName("AC-14 · AC-15 · cada postulante lleva el celular que le toca; quien no tiene ninguno, nada")
        void llevaElSuyo() {
            ranking(List.of(fila(1, 10L, "Ana"), fila(2, 11L, "Luis")));
            Celular deAna = new Celular("987 654 321", "tel:+51987654321", "https://wa.me/51987654321");
            when(celulares.de(List.of(10L, 11L))).thenReturn(Map.of(10L, deAna));

            VisorDeLaVacante visor = servicio.visor(quien(VER_CV), VACANTE);

            assertThat(visor.postulantes()).extracting(PostulanteDelVisor::celular).containsExactly(deAna, null);
        }

        @Test
        @DisplayName("lo ve quien puede abrir el visor, sin pedir el permiso de descargar")
        void sinPermisoDeDescargar() {
            ranking(List.of(fila(1, 10L, "Ana")));
            Celular fijo = new Celular("(01) 456-7890", "tel:014567890", null);
            when(celulares.de(List.of(10L))).thenReturn(Map.of(10L, fijo));

            assertThat(servicio.visor(quien(VER_PRUEBA), VACANTE).postulantes().getFirst().celular()).isEqualTo(fijo);
        }
    }

    // ================================================================= los entregables

    /** AC-12 y AC-13: los entregables se abren desde el informe solo con «descargar_entregables». */
    @Nested
    @DisplayName("Los entregables con enlace")
    class LosEntregables {

        private void armadoDeLaPrueba() {
            Informe base = InformesDePrueba.prueba(List.of());
            Anexo anexo = new Anexo(base.anexo().titulo(), base.anexo().subtitulo(), base.anexo().caso(),
                    List.of(), "3 de 4 entregados", List.of(
                    new EntregableAnexo("Documento de Recursos Humanos", "Subido el 14/11 a las 10:14", true, 77L, null),
                    new EntregableAnexo("Video de sustentación", "Subido el 14/11 a las 10:20", true, null,
                            "https://drive.example/video"),
                    new EntregableAnexo("Enlace raro", "Subido el 14/11 a las 10:30", true, null, "javascript:alert(1)"),
                    new EntregableAnexo("Cronograma", "No se subió ningún archivo", false, 78L, null)));
            Informe conAnexo = new Informe(base.etapa(), base.titulo(), base.empresa(), base.vacante(),
                    base.postulacionId(), base.candidato(), base.correo(), base.generadoEn(), base.nota(),
                    base.grupo(), base.datosClave(), base.recuentos(), base.experiencia(), base.resumen(),
                    base.evaluado(), base.bloques(), base.enlaces(), base.detalleTitulo(), base.detalle(),
                    base.detallePie(), anexo, base.aviso(), base.textosEnCamino(), base.nombreArchivo());
            armado(EtapaDelInforme.PRUEBA, conAnexo, null);
            when(archivos.enlaceDeVolcado(any(), eq(77L))).thenReturn(Optional.of(
                    new EnlaceArchivo("https://almacen.example/rrhh.pdf?firma=8h", Instant.now(), "rrhh.pdf")));
        }

        @Test
        @DisplayName("AC-12 · con permiso: el archivo trae su id y el enlace firmado de 8 horas; el de tipo enlace, su dirección")
        void conPermiso() {
            armadoDeLaPrueba();

            List<EntregableAnexo> entregables =
                    servicio.informe(quien(VER_PRUEBA, DESCARGAR), POSTULACION, "prueba").anexo().entregables();

            assertThat(entregables).extracting(EntregableAnexo::nombre, EntregableAnexo::archivoId, EntregableAnexo::url)
                    .containsExactly(
                            tuple("Documento de Recursos Humanos", 77L, "https://almacen.example/rrhh.pdf?firma=8h"),
                            tuple("Video de sustentación", null, "https://drive.example/video"),
                            // Lo que no es web no se abre desde aquí.
                            tuple("Enlace raro", null, null),
                            // Uno no entregado sigue sin enlace.
                            tuple("Cronograma", null, null));
            verify(archivos, never()).enlaceDeVolcado(any(), eq(78L));
        }

        @Test
        @DisplayName("AC-12 · el PDF que descarga quien tiene el permiso lleva esos enlaces")
        void elPdfLlevaLosEnlaces() {
            armadoDeLaPrueba();

            servicio.pdf(quien(VER_PRUEBA, DESCARGAR), POSTULACION, "prueba");

            org.mockito.ArgumentCaptor<Informe> captor = org.mockito.ArgumentCaptor.forClass(Informe.class);
            verify(generadorPdf).generar(captor.capture());
            assertThat(captor.getValue().anexo().entregables()).extracting(EntregableAnexo::url)
                    .contains("https://almacen.example/rrhh.pdf?firma=8h", "https://drive.example/video");
        }

        @Test
        @DisplayName("AC-13 · sin permiso, ni el visor ni el PDF traen con qué abrirlos, como hoy")
        void sinPermiso() {
            armadoDeLaPrueba();

            List<EntregableAnexo> entregables =
                    servicio.informe(quien(VER_PRUEBA), POSTULACION, "prueba").anexo().entregables();

            assertThat(entregables).extracting(EntregableAnexo::archivoId).containsOnlyNulls();
            assertThat(entregables).extracting(EntregableAnexo::url).containsOnlyNulls();
            assertThat(entregables).extracting(EntregableAnexo::entregado).containsExactly(true, true, true, false);
            verify(archivos, never()).enlaceDeVolcado(any(), anyLong());
        }

        @Test
        @DisplayName("si el almacén no firma, el archivo conserva su id para el visor y el PDF sale sin enlace")
        void siNoSePuedeFirmar() {
            armadoDeLaPrueba();
            when(archivos.enlaceDeVolcado(any(), eq(77L))).thenThrow(new IllegalStateException("almacén caído"));

            EntregableAnexo documento = servicio.informe(quien(VER_PRUEBA, DESCARGAR), POSTULACION, "prueba")
                    .anexo().entregables().getFirst();

            assertThat(documento.archivoId()).isEqualTo(77L);
            assertThat(documento.url()).isNull();
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

    private static NotaEtapa nota(Long postulacionId, int puntaje) {
        return NotaEtapa.builder().postulacionId(postulacionId).puntaje(BigDecimal.valueOf(puntaje)).build();
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
