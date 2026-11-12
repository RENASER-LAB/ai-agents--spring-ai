package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoBusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.LineaBuscadaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDeLaBusquedaIa;
import com.renaser.ai.ai_engine.perfilintegral.entity.BusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.entity.ResultadoBusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.repository.BusquedaCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ResultadoBusquedaCvRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv;
import com.renaser.ai.ai_engine.perfilintegral.service.LoBuscadoEnElCv.LineaVigente;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteBusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.ServicioTextoCv;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Ver {@link PuenteBusquedaCv}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuenteBusquedaCvImpl implements PuenteBusquedaCv {

    private static final Set<String> ESTADOS = Set.of(
            ResultadoBusquedaCv.TIENE, ResultadoBusquedaCv.MENCIONA, ResultadoBusquedaCv.NO_APARECE);

    /** La frase cabe en su columna (CHECK de la V80); más larga no es una frase, es medio CV. */
    private static final int MAXIMO_FRASE = 1000;
    private static final int MAXIMO_TEXTO = 1000;

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final CvRepository cvs;
    private final ServicioTextoCv textoCv;
    private final LoBuscadoEnElCv loBuscado;
    private final BusquedaCvRepository busquedas;
    private final ResultadoBusquedaCvRepository resultados;
    private final PuenteCalificacionIa puente;

    @Override
    @Transactional
    public InsumoBusquedaCv insumo(Long postulacionId) {
        Postulacion postulacion = postulacion(postulacionId);
        // Las líneas antes que el CV: sin líneas no se lee ni el archivo.
        List<LineaVigente> vigentes = loBuscado.vigentes(postulacion.getVacanteId());
        if (vigentes.isEmpty()) {
            return null;
        }
        Vacante vacante = vacantes.findById(postulacion.getVacanteId())
                .orElseThrow(() -> new IllegalStateException("La vacante de esta postulación ya no existe"));
        String puesto = puestos.findById(vacante.getPuestoId()).map(Puesto::getNombre)
                .orElse(vacante.getTitulo());
        // El mismo texto anonimizado que lee EVIDENCIA_CV. Si no tiene texto, lanza CvSinTexto.
        String curriculum = textoCv.prepararParaIa(postulacionId);
        return new InsumoBusquedaCv(puesto, curriculum,
                vigentes.stream().map(LineaVigente::texto).toList(), vigentes.get(0).version());
    }

    @Override
    @Transactional
    public void marcarSinTexto(Long postulacionId, String motivo) {
        Postulacion postulacion = postulacion(postulacionId);
        // La vacante antes que nada: editar las líneas la bloquea, y esto espera a que acabe.
        vacantes.bloquearParaCalificarYReleer(postulacion);
        List<LineaVigente> vigentes = loBuscado.vigentes(postulacion.getVacanteId());
        if (vigentes.isEmpty()) {
            return;
        }
        archivarLoDeLaPersona(postulacionId);
        busquedas.saveAndFlush(BusquedaCv.builder()
                .postulacionId(postulacionId)
                .versionLineas(vigentes.get(0).version())
                .estado(BusquedaCv.SIN_TEXTO)
                .motivo(recortar(motivo, MAXIMO_TEXTO))
                .creadoEn(Instant.now())
                .build());
        log.info("BUSQUEDA_CV: el CV de la postulación {} no tiene texto; queda «CV sin texto» sin "
                + "llamar a la IA ({})", postulacionId, motivo);
    }

    @Override
    @Transactional
    public boolean guardar(Long postulacionId, Long ejecucionIaId, ResultadoDeLaBusquedaIa resultado,
                           int versionLineas, boolean rebusqueda) {
        if (resultado == null || resultado.lineas() == null) {
            throw new IllegalStateException(
                    "El agente BUSQUEDA_CV no devolvió las líneas: no se guarda nada y se reintenta");
        }
        Postulacion postulacion = postulacion(postulacionId);
        // El cerrojo de la vacante, como las demás notas: un cambio de las líneas o de los pesos
        // en curso termina antes, y lo de abajo ya lee lo que dejó.
        vacantes.bloquearParaCalificarYReleer(postulacion);
        List<LineaVigente> vigentes = loBuscado.vigentes(postulacion.getVacanteId());
        if (vigentes.isEmpty() || vigentes.get(0).version() != versionLineas) {
            log.info("BUSQUEDA_CV: lo encontrado en la postulación {} es de la versión {} de las "
                    + "líneas y ya no rige; se descarta", postulacionId, versionLineas);
            return false;
        }

        String curriculum = cvs.findByPostulacionId(postulacionId).map(Cv::getTextoAnonimizado)
                .orElse(null);
        String curriculumComparable = curriculum == null ? null : comparable(curriculum);

        Map<Integer, LineaBuscadaIa> porNumero = new HashMap<>();
        for (LineaBuscadaIa linea : resultado.lineas()) {
            // La primera de cada número; las que sobran o no traen número se ignoran.
            if (linea != null && linea.numero() != null) {
                porNumero.putIfAbsent(linea.numero(), linea);
            }
        }

        archivarLoDeLaPersona(postulacionId);
        BusquedaCv busqueda = busquedas.saveAndFlush(BusquedaCv.builder()
                .postulacionId(postulacionId)
                .versionLineas(versionLineas)
                .estado(BusquedaCv.TERMINADA)
                .ejecucionIaId(ejecucionIaId)
                .creadoEn(Instant.now())
                .build());

        List<ResultadoBusquedaCv> filas = new ArrayList<>();
        int bajadas = 0;
        for (int i = 0; i < vigentes.size(); i++) {
            LineaVigente vigente = vigentes.get(i);
            LineaBuscadaIa suya = porNumero.get(i + 1);
            ResultadoBusquedaCv fila = comoFila(suya, curriculumComparable);
            if (suya != null && ResultadoBusquedaCv.TIENE.equals(suya.estado())
                    && !ResultadoBusquedaCv.TIENE.equals(fila.getEstado())) {
                bajadas++;
            }
            fila.setBusquedaCvId(busqueda.getId());
            fila.setPostulacionId(postulacionId);
            fila.setLineaBusquedaCvId(vigente.id());
            fila.setVersionLineas(versionLineas);
            fila.setEjecucionIaId(ejecucionIaId);
            fila.setCreadoEn(Instant.now());
            filas.add(fila);
        }
        resultados.saveAll(filas);
        log.info("BUSQUEDA_CV: {} líneas guardadas en la postulación {} (versión {}); {} «lo tiene» "
                + "sin frase comprobable bajaron a «lo menciona»", filas.size(), postulacionId,
                versionLineas, bajadas);

        if (rebusqueda) {
            // La rebúsqueda no pasa por la barrera del Perfil Integral: su nota se rehace aquí,
            // sin mover a nadie de etapa y sin avisar al pase automático.
            puente.recalcularSiYaTieneNota(postulacionId);
        }
        return true;
    }

    /**
     * Lo encontrado de una línea, ya con la red de seguridad: estado conocido, «lo tiene» solo
     * con una frase que está en el CV, y la pregunta solo donde se pide.
     */
    static ResultadoBusquedaCv comoFila(LineaBuscadaIa suya, String curriculumComparable) {
        if (suya == null) {
            return ResultadoBusquedaCv.builder().estado(ResultadoBusquedaCv.NO_APARECE).build();
        }
        String estado = suya.estado() == null ? "" : suya.estado().trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(estado)) {
            estado = ResultadoBusquedaCv.NO_APARECE;
        }
        String frase = sinComillas(suya.frase());
        if (ResultadoBusquedaCv.TIENE.equals(estado)
                && !estaEnElCv(frase, curriculumComparable)) {
            estado = ResultadoBusquedaCv.MENCIONA;
            frase = null;
        }
        if (!ResultadoBusquedaCv.TIENE.equals(estado)) {
            // Solo «lo tiene» enseña una frase: es la que el servidor comprobó.
            frase = null;
        }
        return ResultadoBusquedaCv.builder()
                .estado(estado)
                .fraseCv(recortar(frase, MAXIMO_FRASE))
                .nota(recortar(suya.nota(), MAXIMO_TEXTO))
                .pregunta(ResultadoBusquedaCv.TIENE.equals(estado) ? null
                        : recortar(suya.pregunta(), MAXIMO_TEXTO))
                .build();
    }

    /** Si la frase está en el CV, sin mirar mayúsculas, tildes ni espacios repetidos. */
    static boolean estaEnElCv(String frase, String curriculumComparable) {
        if (frase == null || curriculumComparable == null) {
            return false;
        }
        String buscada = comparable(frase);
        return !buscada.isEmpty() && buscada.length() <= MAXIMO_FRASE
                && curriculumComparable.contains(buscada);
    }

    /** El texto como se compara: sin tildes, en minúsculas y con un solo espacio entre palabras. */
    static String comparable(String texto) {
        String sinMarcas = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return String.join(" ", sinMarcas.toLowerCase(Locale.ROOT).trim().split("\\s++"));
    }

    /** Las comillas con que el modelo a veces envuelve la frase no son parte del CV. */
    private static String sinComillas(String frase) {
        if (frase == null) {
            return null;
        }
        String limpia = frase.strip();
        int desde = 0;
        int hasta = limpia.length();
        while (desde < hasta && "\"'«»“”".indexOf(limpia.charAt(desde)) >= 0) {
            desde++;
        }
        while (hasta > desde && "\"'«»“”".indexOf(limpia.charAt(hasta - 1)) >= 0) {
            hasta--;
        }
        String resultado = limpia.substring(desde, hasta).strip();
        return resultado.isEmpty() ? null : resultado;
    }

    private void archivarLoDeLaPersona(Long postulacionId) {
        Instant ahora = Instant.now();
        resultados.archivarLosVivos(List.of(postulacionId), ahora);
        busquedas.archivarLasVivas(List.of(postulacionId), ahora);
    }

    private static String recortar(String texto, int tope) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= tope ? limpio : limpio.substring(0, tope);
    }

    private Postulacion postulacion(Long id) {
        return postulaciones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", id));
    }
}
