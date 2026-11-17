package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfil.entity.EnlacePerfil;
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
import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.hay;
import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.limpio;

/**
 * Lo que los tres informes leen igual: quién es el candidato, de qué vacante y empresa, su CV y
 * lo que la IA dejó de él (afirmaciones, retrato, hallazgos, alertas), y sus enlaces.
 *
 * <p>No recibe a nadie conectado: quien lo llama ya comprobó el permiso y el alcance, o es la
 * cola (el redactor del informe). Por eso puede buscar la vacante por su id: el id sale de una
 * postulación ya validada.
 */
@Component
@RequiredArgsConstructor
public class LoComunDelInforme {

    /** Como mucho cuatro redes y enlaces en el bloque «Enlaces». */
    static final int ENLACES_MAXIMOS = 4;

    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final OrganizacionRepository organizaciones;
    private final UsuarioRepository usuarios;
    private final NombresDeUsuarios nombres;
    private final CvRepository cvs;
    private final DatoCvRepository datosCv;
    private final EnlaceCvRepository enlacesCv;
    private final PerfilCandidatoRepository perfilesCandidato;
    private final EnlacePerfilRepository enlacesPerfil;
    private final AfirmacionCvRepository afirmaciones;
    private final PerfilTalentoRepository perfiles;
    private final HallazgoPerfilRepository hallazgos;
    private final AlertaRepository alertas;
    private final ColaCalificacionIa cola;

    /** Todo lo de arriba, leído una vez. */
    public record Base(Postulacion postulacion, Vacante vacante, Puesto puesto, String empresa,
                       String candidato, String correo, Cv cv, DatoCv dato,
                       List<AfirmacionCv> afirmaciones, PerfilTalento perfil,
                       List<HallazgoPerfil> hallazgos, List<Alerta> alertas, String comoVa) {

        public String vacanteTitulo() {
            return vacante == null ? null : vacante.getTitulo();
        }
    }

    public Base leer(Postulacion postulacion) {
        Vacante vacante = vacantes.findById(postulacion.getVacanteId()).orElse(null);
        Puesto puesto = vacante == null || vacante.getPuestoId() == null ? null
                : puestos.findById(vacante.getPuestoId()).orElse(null);
        String empresa = organizaciones.findById(postulacion.getOrganizacionId())
                .map(o -> o.getNombre()).orElse(null);
        Usuario usuario = postulacion.getUsuarioId() == null ? null
                : usuarios.findById(postulacion.getUsuarioId()).orElse(null);
        String candidato = postulacion.getUsuarioId() == null ? NombresDeUsuarios.ANONIMO
                : nombres.de(postulacion.getUsuarioId());
        // Una persona anonimizada no enseña ni su correo.
        String correo = usuario == null || NombresDeUsuarios.ANONIMO.equals(candidato)
                ? null : usuario.getCorreo();
        Cv cv = cvs.findByPostulacionId(postulacion.getId()).orElse(null);
        DatoCv dato = datosCv.findByPostulacionId(postulacion.getId()).orElse(null);
        // Lo que trae la marca del anonimizador habla de un dato que la IA no vio: no se enseña.
        // Cubre lo guardado antes de que se dejara de guardar (las afirmaciones) y lo que la IA
        // escriba en un hallazgo. Ver AnonimizadorCv#traeLaMarca.
        List<AfirmacionCv> suyas = cv == null ? List.of() : afirmaciones.findByCvId(cv.getId()).stream()
                .filter(a -> !AnonimizadorCv.traeLaMarca(a.getTexto())
                        && !AnonimizadorCv.traeLaMarca(a.getPreguntaValidacion()))
                .sorted(java.util.Comparator.comparing(AfirmacionCv::getId))
                .toList();
        PerfilTalento perfil = perfiles.findByPostulacionId(postulacion.getId()).orElse(null);
        List<HallazgoPerfil> deSuPerfil = perfil == null ? List.of()
                : hallazgos.findByPerfilTalentoId(perfil.getId()).stream()
                        .filter(h -> !AnonimizadorCv.traeLaMarca(h.getDescripcion())
                                && !AnonimizadorCv.traeLaMarca(h.getEvidencia())
                                && !AnonimizadorCv.traeLaMarca(h.getSugerencia()))
                        .sorted(java.util.Comparator.comparing(HallazgoPerfil::getId))
                        .toList();
        List<Alerta> suyasAlertas = alertas.findByPostulacionId(postulacion.getId()).stream()
                .sorted(java.util.Comparator.comparing(Alerta::getId))
                .toList();
        return new Base(postulacion, vacante, puesto, empresa, candidato, correo, cv, dato, suyas,
                perfil, deSuPerfil, suyasAlertas, cola.comoVa(postulacion.getId()));
    }

    // ============================================================== enlaces

    /**
     * Las redes y enlaces del candidato: los de su perfil y los que venían en el CV. Solo
     * {@code http} y {@code https}, sin repetir y como mucho cuatro.
     */
    public List<Enlace> redes(Base base) {
        Set<String> vistos = new LinkedHashSet<>();
        List<Enlace> salida = new ArrayList<>();
        Long personaId = base.postulacion().getUsuarioId() == null ? null
                : usuarios.findById(base.postulacion().getUsuarioId()).map(Usuario::getPersonaId)
                        .orElse(null);
        if (personaId != null) {
            perfilesCandidato.findByPersonaId(personaId).ifPresent(perfil -> {
                for (EnlacePerfil enlace : enlacesPerfil.findByPerfilCandidatoIdOrderByTipo(perfil.getId())) {
                    sumar(salida, vistos, enlace.getUrl(), enlace.getTipo());
                }
            });
        }
        if (base.cv() != null) {
            for (EnlaceCv enlace : enlacesCv.findByCvId(base.cv().getId())) {
                sumar(salida, vistos, enlace.getUrl(), enlace.getTipo());
            }
        }
        return salida;
    }

    private static void sumar(List<Enlace> salida, Set<String> vistos, String url, String tipo) {
        if (salida.size() >= ENLACES_MAXIMOS || !esWeb(url)) {
            return;
        }
        String limpia = url.strip();
        if (!vistos.add(limpia.toLowerCase(Locale.ROOT))) {
            return;
        }
        salida.add(new Enlace(rotulo(limpia, tipo), limpia, tipoDe(limpia, tipo)));
    }

    /** Solo lo que un navegador sabe abrir sin riesgo: http y https con un servidor. */
    public static boolean esWeb(String url) {
        if (!hay(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.strip());
            String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("http".equals(esquema) || "https".equals(esquema)) && hay(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String tipoDe(String url, String tipo) {
        String t = tipo == null ? "" : tipo.toUpperCase(Locale.ROOT);
        String u = url.toLowerCase(Locale.ROOT);
        if (t.contains("LINKEDIN") || u.contains("linkedin.com")) {
            return "LINKEDIN";
        }
        if (t.contains("GITHUB") || u.contains("github.com")) {
            return "GITHUB";
        }
        return "WEB";
    }

    private static String rotulo(String url, String tipo) {
        return switch (tipoDe(url, tipo)) {
            case "LINKEDIN" -> "LinkedIn";
            case "GITHUB" -> "GitHub";
            default -> {
                String t = tipo == null ? "" : tipo.toUpperCase(Locale.ROOT);
                if (t.contains("PORTAFOLIO") || t.contains("PORTFOLIO")) {
                    yield "Portafolio";
                }
                try {
                    String host = URI.create(url).getHost();
                    yield host.startsWith("www.") ? host.substring(4) : host;
                } catch (IllegalArgumentException e) {
                    yield "Enlace";
                }
            }
        };
    }

    // ============================================================== lo pendiente

    /** Las dudas del CV: lo declarado sin prueba y lo que no tenía información. */
    public static List<Punto> dudasDelCv(Base base, String etiquetaFija, String grupo) {
        List<Punto> puntos = new ArrayList<>();
        for (AfirmacionCv a : base.afirmaciones()) {
            if (!"DECLARADA".equals(a.getClasificacion()) && !"FALTA_INFO".equals(a.getClasificacion())) {
                continue;
            }
            String etiqueta = etiquetaFija != null ? etiquetaFija
                    : "DECLARADA".equals(a.getClasificacion()) ? "Declarada" : "Sin información";
            puntos.add(new Punto("«" + a.getTexto().strip() + "»", limpio(a.getPreguntaValidacion()),
                    "CV", etiqueta, grupo));
        }
        return puntos;
    }

    /**
     * Lo que la evaluación dejó por verificar: la falta de evidencia, las alertas y los
     * patrones del cuestionario.
     */
    public static List<Punto> pendientesDeLaEvaluacion(Base base, List<PatronDelCuestionario> patrones,
                                                       String grupo) {
        List<Punto> puntos = new ArrayList<>();
        for (HallazgoPerfil h : base.hallazgos()) {
            if ("FALTA_EVIDENCIA".equals(h.getTipo()) && hay(h.getDescripcion())) {
                puntos.add(new Punto(h.getDescripcion().strip(), limpio(h.getEvidencia()),
                        "EVALUACION", "Falta evidencia", grupo));
            }
        }
        for (Alerta a : base.alertas()) {
            if (!hay(a.getDescripcion())) {
                continue;
            }
            String etiqueta = "DEMASIADO_IDEAL".equals(a.getTipo()) ? "Demasiado ideal"
                    : "CONTRADICCION".equals(a.getTipo()) ? "Contradicción" : "Alerta";
            puntos.add(new Punto(a.getDescripcion().strip(), null, "EVALUACION", etiqueta, grupo));
        }
        for (PatronDelCuestionario p : patrones == null ? List.<PatronDelCuestionario>of() : patrones) {
            puntos.add(new Punto(p.titulo(), limpio(p.descripcion()), "EVALUACION", "Patrón", grupo));
        }
        return puntos;
    }

    /** Los hallazgos de un tipo, como puntos; los riesgos que se trabajan, con su sugerencia. */
    public static List<Punto> hallazgosDe(Base base, String tipo) {
        List<Punto> puntos = new ArrayList<>();
        for (HallazgoPerfil h : base.hallazgos()) {
            if (tipo.equals(h.getTipo()) && hay(h.getDescripcion())) {
                String sugerencia = "RIESGO_DESARROLLABLE".equals(tipo) ? limpio(h.getSugerencia()) : null;
                puntos.add(new Punto(h.getDescripcion().strip(), sugerencia, null, null, null));
            }
        }
        return puntos;
    }

    public static Bloque bloque(String codigo, String titulo, String tono, List<Punto> puntos) {
        return new Bloque(codigo, titulo, tono, "Sin datos", puntos);
    }
}
