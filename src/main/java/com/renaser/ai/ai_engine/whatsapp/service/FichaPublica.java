package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.vacante.entity.RequisitoObjetivo;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.vacante.service.Remuneracion;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Campo;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Convocatoria;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Ficha;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.VacanteDelContexto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Lo que la consulta v2 cuenta de una vacante (V76, reglas 4 y 5 de la spec): una lista
 * <b>explícita</b> de campos públicos, sacados de las mismas fuentes que el tablón del portal.
 * Nunca la ficha administrativa: ni notas mínimas, ni pesos, ni la compensación antigua que ya
 * no se usa.
 *
 * <ul>
 *   <li>Un texto vacío es {@code NO_PUBLICADO}: un dato ausente no se convierte en una condición.</li>
 *   <li>El sueldo respeta {@code OCULTA}, {@code FIJA} y {@code RANGO}: oculto, {@code NO_PUBLICADO}
 *       y sin ningún importe, aunque la base los tenga.</li>
 *   <li>Tipo de contrato y beneficios no tienen hoy una fuente pública: van {@code NO_PUBLICADO}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class FichaPublica {

    public static final String PUBLICADO = "PUBLICADO";
    public static final String PUBLICADA = "PUBLICADA";
    public static final String CERRADA = "CERRADA";

    static final int LARGO_DEL_CAMPO = 600;
    static final int LARGO_DE_LA_EMPRESA = 60;
    static final int LARGO_DEL_DISTINTIVO = 72;

    private final OrganizacionRepository organizaciones;
    private final RequisitoObjetivoRepository requisitos;
    private final CatalogosDelPerfil catalogos;
    private final PropiedadesWhatsapp propiedades;

    /** Las ciudades del catálogo, solo si alguna de estas vacantes tiene una. */
    public Map<String, OpcionUbigeo> ciudadesDe(Collection<Vacante> vacantes) {
        boolean hayAlguna = vacantes.stream().anyMatch(v -> v.getCiudadUbigeo() != null);
        return hayAlguna ? catalogos.ciudadesPorCodigo() : Map.of();
    }

    /** El nombre de la empresa de la vacante, como sale en el tablón; nulo si no se conoce. */
    public String empresaDe(Vacante vacante) {
        return organizaciones.findById(vacante.getOrganizacionId())
                .map(Organizacion::getNombre)
                .filter(n -> !n.isBlank())
                .map(n -> TextosDeWhatsapp.cortar(n, LARGO_DE_LA_EMPRESA))
                .orElse(null);
    }

    /**
     * Lo que separa dos vacantes con el mismo título: la empresa y la ciudad (o la zona), solo con
     * datos publicados. Sin ninguno, nulo: no se inventa.
     */
    public static String distintivoDe(Vacante vacante, String empresa, Map<String, OpcionUbigeo> ciudades) {
        List<String> partes = new ArrayList<>();
        if (empresa != null && !empresa.isBlank()) {
            partes.add(empresa.trim());
        }
        String lugar = ciudadDe(vacante, ciudades);
        if (lugar == null && !vacio(vacante.getUbicacion())) {
            lugar = vacante.getUbicacion().trim();
        }
        if (lugar != null) {
            partes.add(lugar);
        }
        return partes.isEmpty() ? null
                : TextosDeWhatsapp.cortar(String.join(" · ", partes), LARGO_DEL_DISTINTIVO);
    }

    /** {@code PUBLICADA} o {@code CERRADA}, con su fecha y su revisión: 1 al cerrar, 0 antes. */
    public static Convocatoria convocatoriaDe(Vacante vacante) {
        boolean cerrada = CERRADA.equals(vacante.getEstado());
        return new Convocatoria(cerrada ? CERRADA : PUBLICADA,
                cerrada ? LoQueLeToca.alMilisegundo(vacante.getCerradaEn()) : null, cerrada ? 1 : 0);
    }

    /** La vacante entera, como la ve la consulta v2. */
    public VacanteDelContexto de(Vacante vacante) {
        Map<String, OpcionUbigeo> ciudades = ciudadesDe(List.of(vacante));
        String empresa = empresaDe(vacante);
        return new VacanteDelContexto(
                ReferenciasOpacas.deLaVacante(propiedades.getSecretoDesdeElBot(), vacante.getId()),
                TextosDeWhatsapp.cortar(vacio(vacante.getTitulo()) ? "Vacante" : vacante.getTitulo(),
                        TextosDeWhatsapp.LARGO_DE_LA_VACANTE),
                empresa, distintivoDe(vacante, empresa, ciudades), convocatoriaDe(vacante),
                ficha(vacante, ciudades));
    }

    private Ficha ficha(Vacante vacante, Map<String, OpcionUbigeo> ciudades) {
        return new Ficha(
                campo(vacante.getDescripcion()),
                campo(vacante.getProposito()),
                campo(vacante.getResponsabilidades()),
                campo(requisitosDe(vacante)),
                campo(ubicacionDe(vacante, ciudades)),
                campo(vacante.getModalidad()),
                campo(vacante.getHorario()),
                remuneracionDe(vacante),
                // Sin una fuente pública en EX: no se rellenan desde ningún otro sitio.
                Campo.NO_PUBLICADO,
                Campo.NO_PUBLICADO);
    }

    /** Con el sueldo oculto no viaja ningún importe, aunque la vacante los tenga guardados. */
    static Campo remuneracionDe(Vacante vacante) {
        if (!Remuneracion.laEnsena(vacante)) {
            return Campo.NO_PUBLICADO;
        }
        return campo(Remuneracion.escribir(vacante));
    }

    /** El texto de requisitos y, debajo, los indispensables que el portal enseña al postular. */
    private String requisitosDe(Vacante vacante) {
        List<String> lineas = new ArrayList<>();
        if (!vacio(vacante.getRequisitos())) {
            lineas.add(vacante.getRequisitos().trim());
        }
        requisitos.findByVacanteIdAndEsActivoTrue(vacante.getId()).stream()
                .sorted(Comparator.comparing(RequisitoObjetivo::getId))
                .map(RequisitoObjetivo::getDescripcion)
                .filter(d -> !vacio(d))
                .forEach(d -> lineas.add("• " + d.trim()));
        return String.join("\n", lineas);
    }

    /** «Arequipa, Arequipa · Selva Alegre»: la ciudad del catálogo y la zona, lo que haya. */
    private static String ubicacionDe(Vacante vacante, Map<String, OpcionUbigeo> ciudades) {
        String ciudad = ciudadDe(vacante, ciudades);
        String zona = vacio(vacante.getUbicacion()) ? null : vacante.getUbicacion().trim();
        if (ciudad == null) {
            return zona;
        }
        return zona == null ? ciudad : ciudad + " · " + zona;
    }

    private static String ciudadDe(Vacante vacante, Map<String, OpcionUbigeo> ciudades) {
        if (vacante.getCiudadUbigeo() == null) {
            return null;
        }
        OpcionUbigeo opcion = ciudades.get(vacante.getCiudadUbigeo());
        if (opcion == null || vacio(opcion.nombre())) {
            return null;
        }
        boolean conDepartamento = !vacio(opcion.departamento())
                && !opcion.departamento().equalsIgnoreCase(opcion.nombre());
        return conDepartamento ? opcion.nombre() + ", " + opcion.departamento() : opcion.nombre();
    }

    static Campo campo(String texto) {
        return vacio(texto) ? Campo.NO_PUBLICADO
                : new Campo(PUBLICADO, TextosDeWhatsapp.cortar(texto, LARGO_DEL_CAMPO));
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
