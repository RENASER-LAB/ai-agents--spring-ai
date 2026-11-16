package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Dato;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Evaluado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Experiencia;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Nota;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Recuento;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;
import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.service.NotaDelCurriculum;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.duracion;
import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.hay;
import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.limpio;

/**
 * El informe del currículum: la nota del CV, «Experiencia profesional», los ocho criterios con lo
 * que demostró en cada uno, y lo que el CV demuestra, lo que no cuadra y lo que queda por
 * verificar en la evaluación (las afirmaciones que la IA clasificó al leerlo).
 *
 * <p>La nota es la misma que el sistema calcula con los pesos del nivel del puesto y que entra en
 * el Perfil Integral: criterios por {@link NotaDelCurriculum} y, si la vacante busca algo, lo
 * buscado con su peso. No se pondera aquí por su cuenta.
 *
 * <p><b>No lleva fortalezas ni riesgos del retrato</b>: van en el informe de la evaluación.
 */
@Component
@RequiredArgsConstructor
public class ArmadoDelCv {

    private static final String ETAPA = "PERFIL_INTEGRAL";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    static final String SIN_DATO = "sin dato";
    /**
     * El código del bloque «Qué buscar en el CV». Son las líneas privadas de quien recluta: salen
     * en el informe, pero no se le mandan al redactor ({@link RedaccionDelInforme#insumo}).
     */
    static final String BLOQUE_LO_BUSCADO = "BUSCADO";
    /** Menos que esto no es texto que la IA pueda leer: el mismo umbral del extractor. */
    private static final int TEXTO_MINIMO = 40;

    private final CriterioRepository criterios;
    private final NotaCriterioRepository notasCriterio;
    private final PesoCriterioRepository pesosCriterio;
    private final LoBuscadoEnElCv loBuscado;

    /** Las notas de los ocho criterios del CV, en su orden, con la nota del CV. */
    record NotasDelCv(List<Criterio> criterios, BigDecimal nota, BigDecimal pesoLoBuscado,
                      List<LoBuscadoEnElCv.LineaVigente> lineas, LoBuscadoEnElCv.DeUnaPersona busqueda) {
        boolean algunaNota() {
            return criterios.stream().anyMatch(c -> c.nota() != null);
        }
    }

    EstadoDeUnInforme estado(Base base) {
        NotasDelCv notas = notas(base);
        if (notas.algunaNota()) {
            return new EstadoDeUnInforme(EtapaDelInforme.CV.name(), true, null, notas.nota());
        }
        String comoVa = base.comoVa();
        if ("EN_CURSO".equals(comoVa)) {
            return new EstadoDeUnInforme(EtapaDelInforme.CV.name(), false,
                    MotivosDelInforme.CALIFICANDO, null);
        }
        if (("TERMINADA".equals(comoVa) || "FALLIDA".equals(comoVa)) && sinTextoLegible(base)) {
            return new EstadoDeUnInforme(EtapaDelInforme.CV.name(), true, null, null);
        }
        return new EstadoDeUnInforme(EtapaDelInforme.CV.name(), false,
                "FALLIDA".equals(comoVa) ? MotivosDelInforme.FALLO : MotivosDelInforme.SIN_CALIFICAR,
                null);
    }

    Informe armar(Base base, Instant ahora) {
        NotasDelCv notas = notas(base);
        boolean ilegible = !notas.algunaNota() && sinTextoLegible(base);

        List<AfirmacionCv> afirmaciones = base.afirmaciones();
        List<Punto> demuestra = deClase(afirmaciones, "DEMOSTRADA");
        List<Punto> noCuadra = deClase(afirmaciones, "CONTRADICHA");
        List<Punto> porVerificar = LoComunDelInforme.dudasDelCv(base, null, null);

        List<Bloque> bloques = new ArrayList<>();
        bloques.add(LoComunDelInforme.bloque("DEMUESTRA", "Lo que el CV demuestra", "BIEN", demuestra));
        bloques.add(LoComunDelInforme.bloque("NO_CUADRA", "No cuadra", "MAL", noCuadra));
        bloques.add(LoComunDelInforme.bloque("PENDIENTE", EtapaDelInforme.CV.pendiente(), "PENDIENTE",
                porVerificar));
        if (!notas.lineas().isEmpty()) {
            bloques.add(LoComunDelInforme.bloque(BLOQUE_LO_BUSCADO, "Qué buscar en el CV", "NEUTRO",
                    loBuscado(notas)));
        }

        String base100 = "Los criterios del currículum con los pesos de su nivel";
        if (notas.pesoLoBuscado() != null && notas.pesoLoBuscado().signum() > 0) {
            base100 += " y «Qué buscar en el CV», que pesa el "
                    + FormatoDelInforme.nota(notas.pesoLoBuscado()) + " %";
        }
        Nota nota = new Nota(notas.nota(), FormatoDelInforme.notaRedonda(notas.nota()), "de 100",
                base100 + ".");

        return new Informe(EtapaDelInforme.CV.name(), EtapaDelInforme.CV.titulo(), base.empresa(),
                base.vacanteTitulo(), base.postulacion().getId(), base.candidato(), base.correo(),
                ahora, nota, base.postulacion().getGrupoPrioridad(),
                List.of(new Dato("Generado", FormatoDelInforme.fechaHora(ahora))),
                List.of(new Recuento("Demostradas", demuestra.size(), "BIEN"),
                        new Recuento("No cuadran", noCuadra.size(), "MAL"),
                        new Recuento("Por verificar", porVerificar.size(), "PENDIENTE")),
                experiencia(base.dato()),
                null,
                new Evaluado("Qué se evaluó · lo que demostró en cada criterio", "sobre 100",
                        notas.criterios(), null),
                bloques, List.of(), null, List.of(), null, null,
                ilegible ? "El currículum no se pudo leer: es una imagen o un archivo sin texto, "
                        + "y la IA no tuvo de dónde sacar nada. Este informe no inventa lo que no "
                        + "se pudo leer." : null,
                false, MotivosDelInforme.nombreDeArchivo(EtapaDelInforme.CV, base));
    }

    NotasDelCv notas(Base base) {
        List<com.renaser.ai.ai_engine.perfilintegral.entity.Criterio> delCv =
                criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden(ETAPA);
        Set<Long> ids = delCv.stream().map(com.renaser.ai.ai_engine.perfilintegral.entity.Criterio::getId)
                .collect(Collectors.toSet());
        // ⚠️ nota_criterio es de las tres etapas: solo cuentan las de estos ocho criterios.
        Map<Long, NotaCriterio> porCriterio = notasCriterio.findByPostulacionId(base.postulacion().getId())
                .stream().filter(n -> ids.contains(n.getCriterioId()))
                .collect(Collectors.toMap(NotaCriterio::getCriterioId, Function.identity(), (a, b) -> a));

        Map<Long, BigDecimal> pesos = base.vacante() == null || base.puesto() == null ? Map.of()
                : pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(
                                base.vacante().getVersionPesosId(), base.puesto().getNivelPuestoCodigo())
                        .stream().collect(Collectors.toMap(PesoCriterio::getCriterioId,
                                PesoCriterio::getPeso, (a, b) -> a));
        BigDecimal generales = NotaDelCurriculum.deLosCriterios(porCriterio.values(), pesos, ids);

        List<LoBuscadoEnElCv.LineaVigente> lineas = base.vacante() == null ? List.of()
                : loBuscado.vigentes(base.vacante().getId());
        LoBuscadoEnElCv.DeUnaPersona suya = lineas.isEmpty() ? null
                : loBuscado.deLaTanda(lineas, List.of(base.postulacion().getId()))
                        .get(base.postulacion().getId());
        BigDecimal peso = lineas.isEmpty() ? null : loBuscado.pesoDe(base.vacante().getVersionPesosId());
        BigDecimal nota = NotaDelCurriculum.combinar(generales,
                LoBuscadoEnElCv.notaDeLoBuscado(lineas, suya), peso);

        List<Criterio> filas = delCv.stream().map(c -> {
            NotaCriterio n = porCriterio.get(c.getId());
            BigDecimal maximo = c.getPuntos() == null ? CIEN : c.getPuntos();
            BigDecimal puntaje = n == null ? null : n.getPuntaje();
            boolean ajustada = n != null && n.getAjustadaPorUsuarioId() != null;
            String explicacion = n == null ? null : limpio(n.getExplicacion());
            return new Criterio(c.getNombre(), puntaje, maximo, FormatoDelInforme.nota(puntaje),
                    maximo.compareTo(CIEN) == 0 ? null : "/ " + FormatoDelInforme.nota(maximo),
                    FormatoDelInforme.porcentaje(puntaje, maximo),
                    n == null ? null : MotivosDelInforme.quien(n.getOrigen(), ajustada),
                    ajustada, ajustada ? limpio(n.getMotivoAjuste()) : null, explicacion,
                    explicacion, null, puntaje == null ? "Sin nota todavía" : null);
        }).toList();
        return new NotasDelCv(filas, nota, peso, lineas, suya);
    }

    private static List<Punto> deClase(List<AfirmacionCv> afirmaciones, String clase) {
        return afirmaciones.stream()
                .filter(a -> clase.equals(a.getClasificacion()) && hay(a.getTexto()))
                .map(a -> new Punto(a.getTexto().strip(), null, "CV", null, null))
                .toList();
    }

    private static List<Punto> loBuscado(NotasDelCv notas) {
        List<Punto> puntos = new ArrayList<>();
        boolean terminada = notas.busqueda() != null && notas.busqueda().terminada();
        for (LoBuscadoEnElCv.LineaVigente linea : notas.lineas()) {
            LoBuscadoEnElCv.Encontrado encontrado = terminada
                    ? notas.busqueda().porLinea().get(linea.id()) : null;
            String estado = !terminada ? null : encontrado == null ? "NO_APARECE" : encontrado.estado();
            String etiqueta = estado == null ? "Pendiente"
                    : switch (estado) {
                        case "TIENE" -> "Lo tiene";
                        case "MENCIONA" -> "Lo menciona";
                        default -> "No aparece";
                    };
            String detalle = null;
            if (encontrado != null && hay(encontrado.frase())) {
                detalle = "«" + encontrado.frase().strip() + "»";
            } else if (encontrado != null && hay(encontrado.pregunta())) {
                detalle = encontrado.pregunta().strip();
            }
            puntos.add(new Punto(linea.texto(), detalle, "CV", etiqueta, null));
        }
        return puntos;
    }

    static Experiencia experiencia(DatoCv dato) {
        if (dato == null) {
            return new Experiencia(SIN_DATO, null, SIN_DATO, null, SIN_DATO, List.of(), null);
        }
        String total = duracion(dato.getExperienciaMesesTotal());
        List<String> detalle = new ArrayList<>();
        if (hay(dato.getUltimaEmpresa())) {
            detalle.add(dato.getUltimaEmpresa().strip());
        }
        String cuanto = duracion(dato.getUltimaMesesDuracion());
        if (cuanto != null) {
            detalle.add(cuanto);
        }
        List<String> habilidades = !hay(dato.getHabilidades()) ? List.of()
                : Arrays.stream(dato.getHabilidades().split("\\|"))
                        .map(String::strip).filter(h -> !h.isEmpty()).toList();
        return new Experiencia(total == null ? SIN_DATO : total, null,
                hay(dato.getUltimoPuesto()) ? dato.getUltimoPuesto().strip() : SIN_DATO,
                detalle.isEmpty() ? null : String.join(" · ", detalle),
                hay(dato.getEducacionMaxima()) ? dato.getEducacionMaxima().strip() : SIN_DATO,
                habilidades, limpio(dato.getPerfilResumen()));
    }

    private static boolean sinTextoLegible(Base base) {
        if (base.cv() == null) {
            return false;
        }
        String texto = base.cv().getTextoExtraido();
        return texto == null || texto.strip().length() < TEXTO_MINIMO;
    }
}
