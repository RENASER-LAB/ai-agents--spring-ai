package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.LecturaCvPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfil.service.ServicioCicloVidaPerfil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioCicloVidaPerfilImpl implements ServicioCicloVidaPerfil {

    private final PerfilCandidatoRepository perfiles;
    private final ExperienciaPerfilRepository experiencias;
    private final EducacionPerfilRepository educaciones;
    private final IdiomaPerfilRepository idiomas;
    private final CertificacionPerfilRepository certificaciones;
    private final EnlacePerfilRepository enlaces;
    private final LecturaCvPerfilRepository lecturas;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;

    @Override
    @Transactional
    public void borrarPorPersona(Long personaId) {
        perfiles.findByPersonaId(personaId).ifPresent(perfil -> {
            // ⚠️ Los archivos se apuntan ANTES de borrar nada. Sus ids solo viven en estas
            // filas: borradas, la foto, la portada, el currículum y los diplomas se quedan
            // en el almacén sin que quede forma de encontrarlos — y la supresión de la
            // 29733 promete justo lo contrario.
            List<Long> suyos = new ArrayList<>();
            suyos.add(perfil.getFotoArchivoId());
            suyos.add(perfil.getPortadaArchivoId());
            suyos.add(perfil.getCvArchivoId());
            certificaciones.findByPerfilCandidatoIdOrderByNombre(perfil.getId())
                    .forEach(c -> suyos.add(c.getArchivoId()));

            // Las hijas primero: las FK no dejan otro orden.
            experiencias.deleteByPerfilCandidatoId(perfil.getId());
            educaciones.deleteByPerfilCandidatoId(perfil.getId());
            idiomas.deleteByPerfilCandidatoId(perfil.getId());
            certificaciones.deleteByPerfilCandidatoId(perfil.getId());
            enlaces.deleteByPerfilCandidatoId(perfil.getId());
            perfiles.delete(perfil);

            // Las lecturas cuelgan de la persona, no del perfil, así que sobrevivirían a
            // este borrado. Se van con él: un recibo de «este archivo ya se leyó» sobre un
            // perfil que ya no existe cerraría LISTA una lectura que no propondría nada, y
            // apuntaría además a un currículum recién soltado.
            lecturas.deleteByPersonaId(personaId);

            // Ya no queda ninguna fila apuntándolos: ahora se suelta el contenido. La fila
            // de `archivo` se conserva con su `borradoEn`, como en todo el sistema — saber
            // que existió sin poder recuperarlo es lo que permite explicar un hueco.
            List<Long> soltados = suyos.stream().filter(Objects::nonNull).toList();
            soltados.forEach(this::soltar);
            log.info("Perfil de la persona {} borrado con todo lo que colgaba de el "
                    + "({} archivos soltados)", personaId, soltados.size());
        });
    }

    /**
     * Suelta uno de sus archivos: se borra el contenido y la fila se queda.
     *
     * <p>Sin cotejar organización, a diferencia del portal: aquí no hay nadie preguntando
     * —es la supresión que ejecuta la plataforma o el barrido de retención—, y el id no
     * viene de fuera sino de su propia fila de perfil.
     */
    private void soltar(Long archivoId) {
        archivos.findById(archivoId)
                .filter(a -> a.getBorradoEn() == null)
                .ifPresent(almacen::borrarContenido);
    }
}
