package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.perfil.entity.LecturaCvPerfil;
import com.renaser.ai.ai_engine.perfil.repository.LecturaCvPerfilRepository;
import com.renaser.ai.ai_engine.perfil.service.PuenteLecturaCvPerfil;
import com.renaser.ai.ai_engine.perfil.service.ServicioPropuestaPerfil;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;
import com.renaser.ai.ai_engine.postulacion.service.impl.ExtractorTextoCv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Ver {@link PuenteLecturaCvPerfil}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PuenteLecturaCvPerfilImpl implements PuenteLecturaCvPerfil {

    /**
     * Lo que va donde los demás agentes ponen el nombre del puesto.
     *
     * <p>Aquí no hay puesto: nadie postuló a nada. Se dice así en vez de dejarlo vacío
     * porque el insumo se serializa entero al modelo, y una cadena vacía en ese hueco se
     * lee como un puesto que no se supo averiguar.
     */
    private static final String SIN_PUESTO =
            "Sin vacante: el candidato subió su currículum a su propio perfil";

    private final LecturaCvPerfilRepository lecturas;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final ExtractorTextoCv extractor;
    private final AnonimizadorCv anonimizador;
    private final ServicioPropuestaPerfil propuesta;

    @Override
    public InsumoDatos insumo(Long lecturaId) {
        LecturaCvPerfil lectura = lecturas.findById(lecturaId)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe la lectura de perfil " + lecturaId));
        Archivo archivo = archivos.findById(lectura.getArchivoId())
                .orElseThrow(() -> new IllegalStateException(
                        "La lectura de perfil " + lecturaId + " apunta a un archivo que no existe"));
        if (archivo.getBorradoEn() != null || archivo.getRuta() == null) {
            throw new IllegalStateException(
                    "El currículum de la lectura de perfil " + lecturaId + " ya no está guardado");
        }

        String texto = extractor.extraer(almacen.leer(archivo), archivo.getTipo(),
                archivo.getNombreOriginal());
        // ⚠️ Lo mismo que ve cualquier otro agente: sin foto, edad, sexo ni estado civil
        // (RF-41). No hay una versión «del perfil» más completa, y no debe haberla.
        return new InsumoDatos(SIN_PUESTO, anonimizador.anonimizar(texto));
    }

    @Override
    @Transactional
    public void guardar(Long lecturaId, Long ejecucionIaId, ResultadoDatos resultado) {
        LecturaCvPerfil lectura = lecturas.findById(lecturaId)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe la lectura de perfil " + lecturaId));
        if (yaEstaCerrada(lectura, "guardar lo leído")) {
            return;
        }

        boolean entroAlgo = propuesta.proponerAlPerfil(lectura.getPersonaId(), resultado);
        if (!entroAlgo) {
            // Del archivo salió texto pero no salió NADA aprovechable. Para el candidato es
            // el mismo caso que un PDF escaneado —«no pudimos sacar nada, llénalo a mano»—
            // y decirle LISTA le mandaría a buscar unos datos que no están.
            cerrar(lectura, LecturaCvPerfil.NO_LEGIBLE,
                    "El currículum se leyó pero no dio ningún dato aprovechable");
            log.info("Lectura de perfil {}: el modelo no devolvió nada que llevar al perfil",
                    lecturaId);
            return;
        }
        cerrar(lectura, LecturaCvPerfil.LISTA, null);
        log.info("Lectura de perfil {}: el perfil de la persona {} quedó propuesto "
                + "(ejecución {})", lecturaId, lectura.getPersonaId(), ejecucionIaId);
    }

    @Override
    @Transactional
    public void marcarNoLegible(Long lecturaId, String motivo) {
        lecturas.findById(lecturaId)
                .filter(l -> !yaEstaCerrada(l, "marcarla ilegible"))
                .ifPresent(l -> cerrar(l, LecturaCvPerfil.NO_LEGIBLE, motivo));
    }

    /**
     * Una lectura cerrada no se vuelve a abrir, y esto no es defensa por si acaso: pasa.
     *
     * <p>El trabajo sigue en la cola después de que la lectura se cierre —quitar el
     * currículum la cancela, y subir otro la deja atrás—, así que cuando el agente termina
     * quiere escribir sobre una fila que ya tiene su respuesta. Sin este corte se veía una
     * lectura pasar de «no se pudo leer» a «lista» sola, y, peor, el perfil recibía las
     * propuestas de un currículum que la persona acababa de borrar.
     */
    private boolean yaEstaCerrada(LecturaCvPerfil lectura, String queIba) {
        if (LecturaCvPerfil.EN_CURSO.equals(lectura.getEstado())) {
            return false;
        }
        log.info("La lectura de perfil {} ya está cerrada como {}: no se va a {}",
                lectura.getId(), lectura.getEstado(), queIba);
        return true;
    }

    private void cerrar(LecturaCvPerfil lectura, String estado, String motivo) {
        lectura.setEstado(estado);
        lectura.setMotivo(motivo);
        lectura.setTerminadoEn(Instant.now());
        lecturas.save(lectura);
    }
}
