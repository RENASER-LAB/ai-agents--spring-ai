package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfil.entity.EnlacePerfil;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PatronDelCuestionario;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.Alerta;
import com.renaser.ai.ai_engine.perfilintegral.entity.HallazgoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.repository.AfirmacionCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.entity.EnlaceCv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.EnlaceCvRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que los tres informes leen igual: el candidato (y su correo, que no sale si está
 * anonimizado), su CV y lo que la IA dejó de él, sus enlaces web y los puntos pendientes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Lo común de los tres informes")
class LoComunDelInformeTest {

    private static final Long POSTULACION = 10L;

    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private OrganizacionRepository organizaciones;
    @Mock private UsuarioRepository usuarios;
    @Mock private NombresDeUsuarios nombres;
    @Mock private CvRepository cvs;
    @Mock private DatoCvRepository datosCv;
    @Mock private EnlaceCvRepository enlacesCv;
    @Mock private PerfilCandidatoRepository perfilesCandidato;
    @Mock private EnlacePerfilRepository enlacesPerfil;
    @Mock private AfirmacionCvRepository afirmaciones;
    @Mock private PerfilTalentoRepository perfiles;
    @Mock private HallazgoPerfilRepository hallazgos;
    @Mock private AlertaRepository alertas;
    @Mock private ColaCalificacionIa cola;

    @InjectMocks private LoComunDelInforme comun;

    // ================================================================= leer

    @Nested
    @DisplayName("Leer la postulación")
    class Leer {

        @Test
        @DisplayName("trae vacante, puesto, empresa, candidato con su correo, el CV y lo de la IA en el orden en que se guardó")
        void todo() {
            when(vacantes.findById(5L)).thenReturn(Optional.of(
                    Vacante.builder().id(5L).titulo("Analista").puestoId(2L).build()));
            when(puestos.findById(2L)).thenReturn(Optional.of(Puesto.builder().nivelPuestoCodigo("ANALISTA").build()));
            when(organizaciones.findById(3L)).thenReturn(Optional.of(Organizacion.builder().nombre("Renaser").build()));
            when(usuarios.findById(1L)).thenReturn(Optional.of(Usuario.builder().id(1L).correo("ana@correo.pe").build()));
            when(nombres.de(1L)).thenReturn("Ana Torres");
            when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(Cv.builder().id(7L).build()));
            when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(DatoCv.builder().id(8L).build()));
            when(afirmaciones.findByCvId(7L)).thenReturn(List.of(
                    AfirmacionCv.builder().id(2L).build(), AfirmacionCv.builder().id(1L).build()));
            when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(PerfilTalento.builder().id(4L).build()));
            when(hallazgos.findByPerfilTalentoId(4L)).thenReturn(List.of(
                    HallazgoPerfil.builder().id(9L).build(), HallazgoPerfil.builder().id(3L).build()));
            when(alertas.findByPostulacionId(POSTULACION)).thenReturn(List.of(
                    Alerta.builder().id(6L).build(), Alerta.builder().id(5L).build()));
            when(cola.comoVa(POSTULACION)).thenReturn("TERMINADA");

            Base base = comun.leer(postulacion(1L));

            assertThat(base.vacanteTitulo()).isEqualTo("Analista");
            assertThat(base.puesto().getNivelPuestoCodigo()).isEqualTo("ANALISTA");
            assertThat(base.empresa()).isEqualTo("Renaser");
            assertThat(base.candidato()).isEqualTo("Ana Torres");
            assertThat(base.correo()).isEqualTo("ana@correo.pe");
            assertThat(base.dato().getId()).isEqualTo(8L);
            assertThat(base.afirmaciones()).extracting(AfirmacionCv::getId).containsExactly(1L, 2L);
            assertThat(base.hallazgos()).extracting(HallazgoPerfil::getId).containsExactly(3L, 9L);
            assertThat(base.alertas()).extracting(Alerta::getId).containsExactly(5L, 6L);
            assertThat(base.comoVa()).isEqualTo("TERMINADA");
        }

        @Test
        @DisplayName("una persona anonimizada no enseña su correo, y sin vacante, CV ni retrato todo queda vacío")
        void anonimizadaYSinNada() {
            when(usuarios.findById(1L)).thenReturn(Optional.of(Usuario.builder().id(1L).correo("ana@correo.pe").build()));
            when(nombres.de(1L)).thenReturn(NombresDeUsuarios.ANONIMO);

            Base base = comun.leer(postulacion(1L));

            assertThat(base.candidato()).isEqualTo(NombresDeUsuarios.ANONIMO);
            assertThat(base.correo()).isNull();
            assertThat(base.vacante()).isNull();
            assertThat(base.vacanteTitulo()).isNull();
            assertThat(base.puesto()).isNull();
            assertThat(base.empresa()).isNull();
            assertThat(base.afirmaciones()).isEmpty();
            assertThat(base.hallazgos()).isEmpty();
            verifyNoInteractions(puestos, afirmaciones, hallazgos);
        }

        @Test
        @DisplayName("sin usuario es anónima sin preguntar su nombre; una vacante sin puesto no busca el puesto")
        void sinUsuarioNiPuesto() {
            when(vacantes.findById(5L)).thenReturn(Optional.of(Vacante.builder().id(5L).titulo("Cajero").build()));

            Base base = comun.leer(postulacion(null));

            assertThat(base.candidato()).isEqualTo(NombresDeUsuarios.ANONIMO);
            assertThat(base.correo()).isNull();
            assertThat(base.puesto()).isNull();
            verifyNoInteractions(nombres, puestos);
            verify(usuarios, never()).findById(any());
        }
    }

    // ================================================================= enlaces

    @Nested
    @DisplayName("Las redes y enlaces")
    class Redes {

        @Test
        @DisplayName("los del perfil y los del CV: solo web, sin repetir y como mucho cuatro, con su rótulo")
        void delPerfilYDelCv() {
            when(usuarios.findById(1L)).thenReturn(Optional.of(Usuario.builder().id(1L).personaId(8L).build()));
            when(perfilesCandidato.findByPersonaId(8L)).thenReturn(Optional.of(PerfilCandidato.builder().id(9L).build()));
            when(enlacesPerfil.findByPerfilCandidatoIdOrderByTipo(9L)).thenReturn(List.of(
                    enlacePerfil("https://www.linkedin.com/in/ana", "LINKEDIN"),
                    enlacePerfil("  https://github.com/ana  ", null),
                    enlacePerfil("ftp://archivos.pe/cv", "WEB"),
                    enlacePerfil(" ", "WEB"),
                    enlacePerfil("HTTPS://WWW.LINKEDIN.COM/in/ana", "OTRO")));
            when(enlacesCv.findByCvId(7L)).thenReturn(List.of(
                    enlaceCv("https://www.ana.pe", "PORTAFOLIO"),
                    enlaceCv("https://misitio.pe/blog", "WEB"),
                    enlaceCv("https://otro.pe", "WEB")));

            List<Enlace> redes = comun.redes(base(postulacion(1L), Cv.builder().id(7L).build()));

            assertThat(redes).extracting(Enlace::rotulo, Enlace::url, Enlace::tipo).containsExactly(
                    tuple("LinkedIn", "https://www.linkedin.com/in/ana", "LINKEDIN"),
                    tuple("GitHub", "https://github.com/ana", "GITHUB"),
                    tuple("Portafolio", "https://www.ana.pe", "WEB"),
                    tuple("misitio.pe", "https://misitio.pe/blog", "WEB"));
        }

        @Test
        @DisplayName("el rótulo de una web es su servidor sin «www.», y un portfolio en inglés también es portafolio")
        void rotulos() {
            when(enlacesCv.findByCvId(7L)).thenReturn(List.of(
                    enlaceCv("https://www.renaser.pe/equipo", null),
                    enlaceCv("https://behance.net/ana", "PORTFOLIO"),
                    enlaceCv("https://perfil.pe/x", "GITHUB")));

            List<Enlace> redes = comun.redes(base(postulacion(null), Cv.builder().id(7L).build()));

            assertThat(redes).extracting(Enlace::rotulo).containsExactly("renaser.pe", "Portafolio", "GitHub");
            verifyNoInteractions(usuarios, perfilesCandidato);
        }

        @Test
        @DisplayName("sin persona, sin perfil o sin CV no hay de dónde sacar enlaces")
        void sinDeDonde() {
            when(usuarios.findById(1L)).thenReturn(Optional.of(Usuario.builder().id(1L).build()));
            assertThat(comun.redes(base(postulacion(1L), null))).isEmpty();
            verifyNoInteractions(perfilesCandidato, enlacesCv);

            when(usuarios.findById(1L)).thenReturn(Optional.of(Usuario.builder().id(1L).personaId(8L).build()));
            when(perfilesCandidato.findByPersonaId(8L)).thenReturn(Optional.empty());
            assertThat(comun.redes(base(postulacion(1L), null))).isEmpty();
            verifyNoInteractions(enlacesPerfil);
        }

        @Test
        @DisplayName("solo es web lo que abre un navegador sin riesgo: http o https con servidor")
        void esWeb() {
            assertThat(LoComunDelInforme.esWeb("https://renaser.pe")).isTrue();
            assertThat(LoComunDelInforme.esWeb(" http://renaser.pe/x ")).isTrue();
            assertThat(LoComunDelInforme.esWeb(null)).isFalse();
            assertThat(LoComunDelInforme.esWeb("  ")).isFalse();
            assertThat(LoComunDelInforme.esWeb("mailto:ana@correo.pe")).isFalse();
            assertThat(LoComunDelInforme.esWeb("javascript:alert(1)")).isFalse();
            assertThat(LoComunDelInforme.esWeb("http:///solo-ruta")).isFalse();
            assertThat(LoComunDelInforme.esWeb("renaser.pe/sin-esquema")).isFalse();
            assertThat(LoComunDelInforme.esWeb("http://con espacio.pe")).isFalse();
        }
    }

    // ================================================================= lo pendiente

    @Nested
    @DisplayName("Lo pendiente y los hallazgos")
    class Pendiente {

        @Test
        @DisplayName("las dudas del CV son lo declarado y lo que no tenía información, con su etiqueta o la fija")
        void dudasDelCv() {
            Base base = new Base(postulacion(1L), null, null, null, null, null, null, null, List.of(
                    afirmacion("DECLARADA", " Excel avanzado ", " ¿Qué fórmula usas? "),
                    afirmacion("FALTA_INFO", "Inglés", null),
                    afirmacion("DEMOSTRADA", "Bajó la rotación", null),
                    afirmacion("CONTRADICHA", "Jefa", null)), null, List.of(), List.of(), null);

            assertThat(LoComunDelInforme.dudasDelCv(base, null, "Del CV"))
                    .extracting(Punto::texto, Punto::detalle, Punto::etiqueta, Punto::grupo).containsExactly(
                            tuple("«Excel avanzado»", "¿Qué fórmula usas?", "Declarada", "Del CV"),
                            tuple("«Inglés»", null, "Sin información", "Del CV"));
            assertThat(LoComunDelInforme.dudasDelCv(base, "Por contrastar", null))
                    .extracting(Punto::etiqueta).containsOnly("Por contrastar");
        }

        @Test
        @DisplayName("lo que dejó la evaluación: la falta de evidencia, cada tipo de alerta y los patrones; lo vacío no sale")
        void pendientesDeLaEvaluacion() {
            Base base = new Base(postulacion(1L), null, null, null, null, null, null, null, List.of(), null,
                    List.of(hallazgo("FALTA_EVIDENCIA", " No da cifras ", " dijo «mucho» "),
                            hallazgo("FALTA_EVIDENCIA", " ", null),
                            hallazgo("FORTALEZA", "Comunica", null)),
                    List.of(alerta("CONTRADICCION", "Dice dos cosas"), alerta("DEMASIADO_IDEAL", "Perfecto"),
                            alerta("OTRA", "Rara"), alerta("OTRA", null)),
                    null);

            assertThat(LoComunDelInforme.pendientesDeLaEvaluacion(base, null, "De la evaluación"))
                    .extracting(Punto::texto, Punto::detalle, Punto::etiqueta).containsExactly(
                            tuple("No da cifras", "dijo «mucho»", "Falta evidencia"),
                            tuple("Dice dos cosas", null, "Contradicción"),
                            tuple("Perfecto", null, "Demasiado ideal"),
                            tuple("Rara", null, "Alerta"));
            assertThat(LoComunDelInforme.pendientesDeLaEvaluacion(base,
                    List.of(new PatronDelCuestionario("X", "Todo igual", " ", 3, 3)), null))
                    .last().satisfies(p -> {
                        assertThat(p.etiqueta()).isEqualTo("Patrón");
                        assertThat(p.detalle()).isNull();
                    });
        }

        @Test
        @DisplayName("los hallazgos de un tipo; solo los riesgos que se trabajan llevan su sugerencia")
        void hallazgosDe() {
            Base base = new Base(postulacion(1L), null, null, null, null, null, null, null, List.of(), null,
                    List.of(hallazgo("RIESGO_DESARROLLABLE", "Excel básico", " un curso "),
                            hallazgo("FORTALEZA", " Comunica ", "no se enseña"),
                            hallazgo("FORTALEZA", null, null)),
                    List.of(), null);

            assertThat(LoComunDelInforme.hallazgosDe(base, "RIESGO_DESARROLLABLE"))
                    .extracting(Punto::texto, Punto::detalle).containsExactly(tuple("Excel básico", "un curso"));
            assertThat(LoComunDelInforme.hallazgosDe(base, "FORTALEZA"))
                    .extracting(Punto::texto, Punto::detalle).containsExactly(tuple("Comunica", null));
            assertThat(LoComunDelInforme.bloque("X", "Título", "BIEN", List.of()).vacio()).isEqualTo("Sin datos");
        }
    }

    // ================================================================= ayudas

    private static Postulacion postulacion(Long usuarioId) {
        return Postulacion.builder().id(POSTULACION).usuarioId(usuarioId).vacanteId(5L).organizacionId(3L).build();
    }

    private static Base base(Postulacion postulacion, Cv cv) {
        return new Base(postulacion, null, null, null, null, null, cv, null, List.of(), null, List.of(), List.of(),
                null);
    }

    private static EnlacePerfil enlacePerfil(String url, String tipo) {
        return EnlacePerfil.builder().url(url).tipo(tipo).build();
    }

    private static EnlaceCv enlaceCv(String url, String tipo) {
        return EnlaceCv.builder().url(url).tipo(tipo).build();
    }

    private static AfirmacionCv afirmacion(String clase, String texto, String pregunta) {
        return AfirmacionCv.builder().clasificacion(clase).texto(texto).preguntaValidacion(pregunta).build();
    }

    private static HallazgoPerfil hallazgo(String tipo, String descripcion, String extra) {
        return HallazgoPerfil.builder().tipo(tipo).descripcion(descripcion).evidencia(extra).sugerencia(extra)
                .build();
    }

    private static Alerta alerta(String tipo, String descripcion) {
        return Alerta.builder().tipo(tipo).descripcion(descripcion).build();
    }
}
