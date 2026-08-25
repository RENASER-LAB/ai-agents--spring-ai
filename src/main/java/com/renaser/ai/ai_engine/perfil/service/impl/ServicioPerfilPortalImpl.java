package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarCabecera;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarCertificacion;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarEducacion;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarEnlace;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarExperiencia;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarIdioma;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.Pretension;
import com.renaser.ai.ai_engine.perfil.entity.CertificacionPerfil;
import com.renaser.ai.ai_engine.perfil.entity.EducacionPerfil;
import com.renaser.ai.ai_engine.perfil.entity.EnlacePerfil;
import com.renaser.ai.ai_engine.perfil.entity.ExperienciaPerfil;
import com.renaser.ai.ai_engine.perfil.entity.IdiomaPerfil;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelEducativoRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelIdiomaRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfil.service.ClaveNatural;
import com.renaser.ai.ai_engine.perfil.service.ServicioPerfilPortal;
import com.renaser.ai.ai_engine.perfil.service.ValidacionEnlaces;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Ver {@link ServicioPerfilPortal}. Las reglas que aplican aquí:
 *
 * <ul>
 *   <li>El perfil se crea perezosamente: existe desde el primer escrito, no antes.</li>
 *   <li>La pretensión es todo o nada (los tres campos o ninguno) → 400 si va a medias.</li>
 *   <li>Editar ⇒ {@code origen=PERSONA} y confirmado; confirmar ⇒ conserva CURRICULUM.</li>
 *   <li>RF-166: los enlaces se validan por forma y por dominio → 400 si no cumplen,
 *       409 si ya existe el mismo enlace del mismo tipo.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ServicioPerfilPortalImpl implements ServicioPerfilPortal {

    private static final String PERSONA = "PERSONA";

    private final PerfilCandidatoRepository perfiles;
    private final ExperienciaPerfilRepository experiencias;
    private final EducacionPerfilRepository educaciones;
    private final IdiomaPerfilRepository idiomas;
    private final CertificacionPerfilRepository certificaciones;
    private final EnlacePerfilRepository enlaces;
    private final NivelEducativoRepository nivelesEducativos;
    private final NivelIdiomaRepository nivelesIdioma;
    private final PintorDePerfil pintor;

    // ==================== Ver ====================

    @Override
    public PerfilCompleto ver(ContextoUsuario quien) {
        return pintor.pintar(quien.personaId());
    }

    @Override
    public PerfilCompleto descargar(ContextoUsuario quien) {
        // El mismo contenido que ver: el derecho de acceso es sobre lo que hay, ni mas ni menos.
        return pintor.pintar(quien.personaId());
    }

    // ==================== La cabecera ====================

    @Override
    @Transactional
    public void editarCabecera(ContextoUsuario quien, EditarCabecera datos) {
        Pretension pretension = datos.pretension();
        if (pretension != null && !completaONula(pretension)) {
            throw new IllegalArgumentException("La pretensión es un rango completo o nada: "
                    + "mínimo, máximo y moneda, los tres");
        }
        if (pretension != null && pretension.min() != null
                && pretension.max().compareTo(pretension.min()) < 0) {
            throw new IllegalArgumentException("El máximo de la pretensión no puede ser "
                    + "menor que el mínimo");
        }
        PerfilCandidato perfil = elDe(quien);
        perfil.setTitular(limpio(datos.titular()));
        perfil.setResumen(limpio(datos.resumen()));
        perfil.setHabilidades(datos.habilidades() == null || datos.habilidades().isEmpty()
                ? null : String.join(" | ", datos.habilidades()));
        perfil.setExperienciaMeses(datos.experienciaMeses());
        perfil.setUbicacion(limpio(datos.ubicacion()));
        perfil.setDisponibilidad(limpio(datos.disponibilidad()));
        perfil.setPretensionMin(pretension == null ? null : pretension.min());
        perfil.setPretensionMax(pretension == null ? null : pretension.max());
        perfil.setPretensionMoneda(pretension == null ? null : pretension.moneda());
        tocar(perfil);
    }

    // ==================== Experiencia ====================

    @Override
    @Transactional
    public Long crearExperiencia(ContextoUsuario quien, EditarExperiencia datos) {
        exigirFechas(datos.desde(), datos.hasta());
        PerfilCandidato perfil = elDe(quien);
        int orden = experiencias.findByPerfilCandidatoIdOrderByOrden(perfil.getId()).stream()
                .map(ExperienciaPerfil::getOrden).filter(Objects::nonNull)
                .max(Integer::compare).orElse(0) + 1;
        ExperienciaPerfil fila = experiencias.save(ExperienciaPerfil.builder()
                .perfilCandidatoId(perfil.getId())
                .puesto(datos.puesto().trim()).empresa(datos.empresa().trim())
                .desde(datos.desde()).hasta(datos.hasta())
                .descripcion(limpio(datos.descripcion()))
                .origen(PERSONA).confirmadoEn(Instant.now())
                .orden(orden).creadoEn(Instant.now())
                .build());
        tocar(perfil);
        return fila.getId();
    }

    @Override
    @Transactional
    public void editarExperiencia(ContextoUsuario quien, Long id, EditarExperiencia datos) {
        exigirFechas(datos.desde(), datos.hasta());
        ExperienciaPerfil fila = miExperiencia(quien, id);
        fila.setPuesto(datos.puesto().trim());
        fila.setEmpresa(datos.empresa().trim());
        fila.setDesde(datos.desde());
        fila.setHasta(datos.hasta());
        fila.setDescripcion(limpio(datos.descripcion()));
        hacerloSuyo(fila::setOrigen, fila::setConfirmadoEn);
        experiencias.save(fila);
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void borrarExperiencia(ContextoUsuario quien, Long id) {
        experiencias.delete(miExperiencia(quien, id));
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void confirmarExperiencia(ContextoUsuario quien, Long id) {
        ExperienciaPerfil fila = miExperiencia(quien, id);
        fila.setConfirmadoEn(Instant.now());   // el origen CURRICULUM se conserva a proposito
        experiencias.save(fila);
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void reordenarExperiencia(ContextoUsuario quien, List<Long> ids) {
        PerfilCandidato perfil = elDe(quien);
        List<ExperienciaPerfil> filas =
                experiencias.findByPerfilCandidatoIdOrderByOrden(perfil.getId());
        aplicarOrden(ids, filas.stream().map(ExperienciaPerfil::getId).toList());
        for (ExperienciaPerfil fila : filas) {
            fila.setOrden(ids.indexOf(fila.getId()) + 1);
        }
        experiencias.saveAll(filas);
        tocar(perfil);
    }

    // ==================== Educacion ====================

    @Override
    @Transactional
    public Long crearEducacion(ContextoUsuario quien, EditarEducacion datos) {
        exigirNivelEducativo(datos.nivelCodigo());
        PerfilCandidato perfil = elDe(quien);
        int orden = educaciones.findByPerfilCandidatoIdOrderByOrden(perfil.getId()).stream()
                .map(EducacionPerfil::getOrden).filter(Objects::nonNull)
                .max(Integer::compare).orElse(0) + 1;
        EducacionPerfil fila = educaciones.save(EducacionPerfil.builder()
                .perfilCandidatoId(perfil.getId())
                .titulo(datos.titulo().trim()).institucion(datos.institucion().trim())
                .nivelCodigo(datos.nivelCodigo())
                .desde(datos.desde()).hasta(datos.hasta()).enCurso(datos.enCurso())
                .origen(PERSONA).confirmadoEn(Instant.now())
                .orden(orden).creadoEn(Instant.now())
                .build());
        tocar(perfil);
        return fila.getId();
    }

    @Override
    @Transactional
    public void editarEducacion(ContextoUsuario quien, Long id, EditarEducacion datos) {
        exigirNivelEducativo(datos.nivelCodigo());
        EducacionPerfil fila = miEducacion(quien, id);
        fila.setTitulo(datos.titulo().trim());
        fila.setInstitucion(datos.institucion().trim());
        fila.setNivelCodigo(datos.nivelCodigo());
        fila.setDesde(datos.desde());
        fila.setHasta(datos.hasta());
        fila.setEnCurso(datos.enCurso());
        hacerloSuyo(fila::setOrigen, fila::setConfirmadoEn);
        educaciones.save(fila);
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void borrarEducacion(ContextoUsuario quien, Long id) {
        educaciones.delete(miEducacion(quien, id));
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void confirmarEducacion(ContextoUsuario quien, Long id) {
        EducacionPerfil fila = miEducacion(quien, id);
        fila.setConfirmadoEn(Instant.now());
        educaciones.save(fila);
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void reordenarEducacion(ContextoUsuario quien, List<Long> ids) {
        PerfilCandidato perfil = elDe(quien);
        List<EducacionPerfil> filas =
                educaciones.findByPerfilCandidatoIdOrderByOrden(perfil.getId());
        aplicarOrden(ids, filas.stream().map(EducacionPerfil::getId).toList());
        for (EducacionPerfil fila : filas) {
            fila.setOrden(ids.indexOf(fila.getId()) + 1);
        }
        educaciones.saveAll(filas);
        tocar(perfil);
    }

    // ==================== Idiomas ====================

    @Override
    @Transactional
    public Long crearIdioma(ContextoUsuario quien, EditarIdioma datos) {
        exigirNivelIdioma(datos.nivelCodigo());
        PerfilCandidato perfil = elDe(quien);
        // Misma normalizacion que el merge del curriculum: sin ella «ingles» entraba como
        // un idioma distinto de «Ingles» y la lista acababa con el mismo dos veces.
        String buscada = ClaveNatural.de(datos.idioma());
        boolean repetido = idiomas.findByPerfilCandidatoIdOrderByIdioma(perfil.getId()).stream()
                .anyMatch(i -> ClaveNatural.de(i.getIdioma()).equals(buscada));
        if (repetido) {
            throw new IllegalStateException("Ese idioma ya está en el perfil: edítalo");
        }
        IdiomaPerfil fila = idiomas.save(IdiomaPerfil.builder()
                .perfilCandidatoId(perfil.getId())
                .idioma(datos.idioma().trim()).nivelCodigo(datos.nivelCodigo())
                .origen(PERSONA).confirmadoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());
        tocar(perfil);
        return fila.getId();
    }

    @Override
    @Transactional
    public void editarIdioma(ContextoUsuario quien, Long id, EditarIdioma datos) {
        exigirNivelIdioma(datos.nivelCodigo());
        IdiomaPerfil fila = miIdioma(quien, id);
        fila.setIdioma(datos.idioma().trim());
        fila.setNivelCodigo(datos.nivelCodigo());
        hacerloSuyo(fila::setOrigen, fila::setConfirmadoEn);
        idiomas.save(fila);
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void borrarIdioma(ContextoUsuario quien, Long id) {
        idiomas.delete(miIdioma(quien, id));
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void confirmarIdioma(ContextoUsuario quien, Long id) {
        IdiomaPerfil fila = miIdioma(quien, id);
        fila.setConfirmadoEn(Instant.now());
        idiomas.save(fila);
        tocar(elDe(quien));
    }

    // ==================== Certificaciones ====================

    @Override
    @Transactional
    public Long crearCertificacion(ContextoUsuario quien, EditarCertificacion datos) {
        exigirVencimiento(datos.emitidaEn(), datos.venceEn());
        PerfilCandidato perfil = elDe(quien);
        CertificacionPerfil fila = certificaciones.save(CertificacionPerfil.builder()
                .perfilCandidatoId(perfil.getId())
                .nombre(datos.nombre().trim()).entidad(limpio(datos.entidad()))
                .emitidaEn(datos.emitidaEn()).venceEn(datos.venceEn())
                .origen(PERSONA).confirmadoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());
        tocar(perfil);
        return fila.getId();
    }

    @Override
    @Transactional
    public void editarCertificacion(ContextoUsuario quien, Long id, EditarCertificacion datos) {
        exigirVencimiento(datos.emitidaEn(), datos.venceEn());
        CertificacionPerfil fila = miCertificacion(quien, id);
        fila.setNombre(datos.nombre().trim());
        fila.setEntidad(limpio(datos.entidad()));
        fila.setEmitidaEn(datos.emitidaEn());
        fila.setVenceEn(datos.venceEn());
        hacerloSuyo(fila::setOrigen, fila::setConfirmadoEn);
        certificaciones.save(fila);
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void borrarCertificacion(ContextoUsuario quien, Long id) {
        certificaciones.delete(miCertificacion(quien, id));
        tocar(elDe(quien));
    }

    @Override
    @Transactional
    public void confirmarCertificacion(ContextoUsuario quien, Long id) {
        CertificacionPerfil fila = miCertificacion(quien, id);
        fila.setConfirmadoEn(Instant.now());
        certificaciones.save(fila);
        tocar(elDe(quien));
    }

    // ==================== Enlaces ====================

    @Override
    @Transactional
    public Long crearEnlace(ContextoUsuario quien, EditarEnlace datos) {
        List<String> tipos = List.of("LINKEDIN", "GITHUB", "PORTAFOLIO", "PUBLICACION",
                "PRODUCTO", "OTRO");
        if (!tipos.contains(datos.tipo())) {
            throw new IllegalArgumentException("Tipo de enlace desconocido: " + datos.tipo());
        }
        if (!ValidacionEnlaces.esValida(datos.tipo(), datos.url())) {
            throw new IllegalArgumentException("Ese enlace no es una dirección válida"
                    + (datos.tipo().equals("LINKEDIN") ? " de LinkedIn"
                       : datos.tipo().equals("GITHUB") ? " de GitHub" : ""));
        }
        PerfilCandidato perfil = elDe(quien);
        String url = datos.url().trim();
        if (enlaces.existsByPerfilCandidatoIdAndTipoAndUrl(perfil.getId(), datos.tipo(), url)) {
            throw new IllegalStateException("Ese enlace ya está en el perfil");
        }
        EnlacePerfil fila = enlaces.save(EnlacePerfil.builder()
                .perfilCandidatoId(perfil.getId())
                .tipo(datos.tipo()).url(url)
                .creadoEn(Instant.now())
                .build());
        tocar(perfil);
        return fila.getId();
    }

    @Override
    @Transactional
    public void borrarEnlace(ContextoUsuario quien, Long id) {
        EnlacePerfil fila = enlaces.findById(id)
                .filter(e -> esMio(quien, e.getPerfilCandidatoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Enlace", "id", id));
        enlaces.delete(fila);
        tocar(elDe(quien));
    }

    // ==================== Lo comun ====================

    /** El perfil del que llama; se crea perezosamente al primer escrito. */
    private PerfilCandidato elDe(ContextoUsuario quien) {
        return perfiles.findByPersonaId(quien.personaId())
                .orElseGet(() -> perfiles.save(PerfilCandidato.builder()
                        .personaId(quien.personaId())
                        .creadoEn(Instant.now()).actualizadoEn(Instant.now())
                        .build()));
    }

    /** Lo ajeno responde 404, no 403: decir «prohibido» ya confirmaria que existe. */
    private boolean esMio(ContextoUsuario quien, Long perfilId) {
        return perfiles.findByPersonaId(quien.personaId())
                .map(p -> p.getId().equals(perfilId))
                .orElse(false);
    }

    private ExperienciaPerfil miExperiencia(ContextoUsuario quien, Long id) {
        return experiencias.findById(id)
                .filter(e -> esMio(quien, e.getPerfilCandidatoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Experiencia", "id", id));
    }

    private EducacionPerfil miEducacion(ContextoUsuario quien, Long id) {
        return educaciones.findById(id)
                .filter(e -> esMio(quien, e.getPerfilCandidatoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Educación", "id", id));
    }

    private IdiomaPerfil miIdioma(ContextoUsuario quien, Long id) {
        return idiomas.findById(id)
                .filter(i -> esMio(quien, i.getPerfilCandidatoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Idioma", "id", id));
    }

    private CertificacionPerfil miCertificacion(ContextoUsuario quien, Long id) {
        return certificaciones.findById(id)
                .filter(c -> esMio(quien, c.getPerfilCandidatoId()))
                .orElseThrow(() -> new ResourceNotFoundException("Certificación", "id", id));
    }

    /** Editar convierte el dato en «escrito por mi», sea cual fuera su origen. */
    private void hacerloSuyo(java.util.function.Consumer<String> origen,
                             java.util.function.Consumer<Instant> confirmado) {
        origen.accept(PERSONA);
        confirmado.accept(Instant.now());
    }

    /**
     * Toda escritura cuenta como actividad, no solo las altas: el barrido de retencion mira
     * `actualizado_en`, y sin esto el perfil de quien lleva meses confirmando, ordenando y
     * borrando se veria abandonado y se borraria solo.
     */
    private void tocar(PerfilCandidato perfil) {
        perfil.setActualizadoEn(Instant.now());
        perfiles.save(perfil);
    }

    private static boolean completaONula(Pretension p) {
        return p.min() != null && p.max() != null && p.moneda() != null;
    }

    private static void exigirFechas(java.time.LocalDate desde, java.time.LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la "
                    + "de inicio");
        }
    }

    private static void exigirVencimiento(java.time.LocalDate emitida,
                                          java.time.LocalDate vence) {
        if (emitida != null && vence != null && vence.isBefore(emitida)) {
            throw new IllegalArgumentException("Una certificación no puede vencer antes de "
                    + "haberse emitido");
        }
    }

    private void exigirNivelEducativo(String codigo) {
        if (codigo != null && !nivelesEducativos.existsById(codigo)) {
            throw new IllegalArgumentException("Nivel educativo desconocido: " + codigo);
        }
    }

    private void exigirNivelIdioma(String codigo) {
        if (codigo == null || !nivelesIdioma.existsById(codigo)) {
            throw new IllegalArgumentException("Nivel de idioma desconocido: " + codigo);
        }
    }

    private static void aplicarOrden(List<Long> pedidos, List<Long> reales) {
        if (pedidos == null || pedidos.size() != reales.size()
                || !pedidos.stream().sorted().toList()
                        .equals(reales.stream().sorted().toList())) {
            throw new IllegalArgumentException("El orden tiene que traer exactamente los "
                    + "elementos del perfil, una vez cada uno");
        }
    }



    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
