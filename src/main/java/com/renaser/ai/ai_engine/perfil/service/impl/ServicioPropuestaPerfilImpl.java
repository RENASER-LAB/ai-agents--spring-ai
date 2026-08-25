package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.perfil.entity.CertificacionPerfil;
import com.renaser.ai.ai_engine.perfil.entity.EducacionPerfil;
import com.renaser.ai.ai_engine.perfil.entity.ExperienciaPerfil;
import com.renaser.ai.ai_engine.perfil.entity.IdiomaPerfil;
import com.renaser.ai.ai_engine.perfil.entity.EnlacePerfil;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.EducacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.ExperienciaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.IdiomaPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelEducativoRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelIdiomaRepository;
import com.renaser.ai.ai_engine.perfil.repository.EnlacePerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfil.service.ServicioPropuestaPerfil;
import com.renaser.ai.ai_engine.perfil.service.ValidacionEnlaces;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.CertificacionLeida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.EducacionLeida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ExperienciaLeida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.IdiomaLeido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * El merge que hace posible que la IA ayude sin estorbar.
 *
 * <p>Por cada elemento leído se busca una fila con la misma <b>clave natural</b> (puesto y
 * empresa, título e institución, el idioma, el nombre de la certificación), comparada sin
 * mayúsculas, sin tildes y sin espacios de más — el modelo escribe «Analista Senior» y la
 * persona «analista senior», y eso es lo mismo.
 *
 * <ul>
 *   <li>No hay fila → entra como CURRICULUM, sin confirmar.</li>
 *   <li>La fila es PERSONA o está confirmada → <b>no se toca</b> (RF-159).</li>
 *   <li>La fila es CURRICULUM sin confirmar → se actualiza: solo el último CV manda (RF-162).</li>
 *   <li>Lo que ya no aparece en el CV → se conserva: borrar sin que el candidato lo pida
 *       contradice que el perfil es suyo (RF-157).</li>
 * </ul>
 *
 * <p>La cabecera (titular, resumen, habilidades, meses) no lleva origen por campo, así que
 * su regla es más simple: la IA <b>solo rellena huecos</b>, nunca reemplaza.
 *
 * <p>Saneado por elemento, no por tanda: una experiencia sin fecha de inicio parseable se
 * descarta (la columna es NOT NULL y es mejor un hueco que un dato falso), y un nivel de
 * idioma fuera del catálogo descarta ese idioma. El resto de la tanda entra igual.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioPropuestaPerfilImpl implements ServicioPropuestaPerfil {

    private static final String CURRICULUM = "CURRICULUM";

    private final PostulacionRepository postulaciones;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final PerfilCandidatoRepository perfiles;
    private final ExperienciaPerfilRepository experiencias;
    private final EducacionPerfilRepository educaciones;
    private final IdiomaPerfilRepository idiomas;
    private final CertificacionPerfilRepository certificaciones;
    private final EnlacePerfilRepository enlaces;
    private final NivelEducativoRepository nivelesEducativos;
    private final NivelIdiomaRepository nivelesIdioma;

    @Override
    @Transactional
    public void proponer(Long postulacionId, ResultadoDatos resultado) {
        if (resultado == null) {
            return;
        }
        Long personaId = personaDe(postulacionId);
        if (personaId == null) {
            return;
        }

        PerfilCandidato perfil = perfiles.findByPersonaId(personaId)
                .orElseGet(() -> perfiles.save(PerfilCandidato.builder()
                        .personaId(personaId)
                        .creadoEn(Instant.now())
                        .actualizadoEn(Instant.now())
                        .build()));

        rellenarHuecosDeCabecera(perfil, resultado);
        proponerExperiencia(perfil.getId(), resultado.experiencia());
        proponerEducacion(perfil.getId(), resultado.educacion());
        proponerIdiomas(perfil.getId(), resultado.idiomas());
        proponerCertificaciones(perfil.getId(), resultado.certificaciones());

        perfil.setActualizadoEn(Instant.now());
        perfiles.save(perfil);
    }

    /** La persona dueña, o null si no se puede (o no se debe: anonimizada por el borrado). */
    private Long personaDe(Long postulacionId) {
        return postulaciones.findById(postulacionId)
                .flatMap(p -> usuarios.findById(p.getUsuarioId()))
                .flatMap(u -> personas.findById(u.getPersonaId()))
                .filter(persona -> persona.getAnonimizadoEn() == null)
                .map(Persona::getId)
                .orElse(null);
    }

    @Override
    @Transactional
    public void proponerEnlaces(Long personaId, String linkedin, String github,
                                String portafolio) {
        boolean hayAlguno = !enBlanco(linkedin) || !enBlanco(github) || !enBlanco(portafolio);
        if (!hayAlguno) {
            return;
        }
        boolean anonimizada = personas.findById(personaId)
                .map(p -> p.getAnonimizadoEn() != null).orElse(true);
        if (anonimizada) {
            return;
        }
        PerfilCandidato perfil = perfiles.findByPersonaId(personaId)
                .orElseGet(() -> perfiles.save(PerfilCandidato.builder()
                        .personaId(personaId)
                        .creadoEn(Instant.now())
                        .actualizadoEn(Instant.now())
                        .build()));
        proponerEnlace(perfil.getId(), "LINKEDIN", linkedin);
        proponerEnlace(perfil.getId(), "GITHUB", github);
        proponerEnlace(perfil.getId(), "PORTAFOLIO", portafolio);
    }

    private void proponerEnlace(Long perfilId, String tipo, String url) {
        if (enBlanco(url) || !ValidacionEnlaces.esValida(tipo, url)) {
            return;   // el formulario de postular no es el sitio para pelear por una URL
        }
        String limpia = url.trim();
        if (!enlaces.existsByPerfilCandidatoIdAndTipoAndUrl(perfilId, tipo, limpia)) {
            enlaces.save(EnlacePerfil.builder()
                    .perfilCandidatoId(perfilId)
                    .tipo(tipo)
                    .url(limpia)
                    .creadoEn(Instant.now())
                    .build());
        }
    }

    // ==================== La cabecera: solo huecos ====================

    private void rellenarHuecosDeCabecera(PerfilCandidato perfil, ResultadoDatos r) {
        if (enBlanco(perfil.getTitular()) && !enBlanco(r.ultimoPuesto())) {
            perfil.setTitular(r.ultimoPuesto().trim());
        }
        if (enBlanco(perfil.getResumen()) && !enBlanco(r.perfilResumen())) {
            perfil.setResumen(r.perfilResumen().trim());
        }
        if (enBlanco(perfil.getHabilidades()) && r.habilidades() != null
                && !r.habilidades().isEmpty()) {
            perfil.setHabilidades(String.join(" | ", r.habilidades()));
        }
        if (perfil.getExperienciaMeses() == null && mesesValidos(r.experienciaMesesTotal())) {
            perfil.setExperienciaMeses(r.experienciaMesesTotal());
        }
    }

    // ==================== Las listas ====================

    private void proponerExperiencia(Long perfilId, List<ExperienciaLeida> leidas) {
        if (leidas == null) {
            return;
        }
        List<ExperienciaPerfil> actuales = experiencias.findByPerfilCandidatoIdOrderByOrden(perfilId);
        int siguienteOrden = actuales.stream().map(ExperienciaPerfil::getOrden)
                .filter(Objects::nonNull).max(Integer::compare).orElse(0) + 1;

        for (ExperienciaLeida leida : leidas) {
            if (enBlanco(leida.puesto()) || enBlanco(leida.empresa())) {
                continue;
            }
            LocalDate desde = mes(leida.desde());
            if (desde == null) {
                // La columna es NOT NULL: mejor un hueco que una fecha inventada.
                log.info("Experiencia sin fecha de inicio parseable, descartada: {} en {}",
                        leida.puesto(), leida.empresa());
                continue;
            }
            LocalDate hasta = mes(leida.hasta());
            if (hasta != null && hasta.isBefore(desde)) {
                hasta = null;   // el CHECK la rechazaria; sin fin es «sigue aqui», que es neutro
            }

            Optional<ExperienciaPerfil> existente = buscar(actuales,
                    e -> clave(e.getPuesto()) + "|" + clave(e.getEmpresa()),
                    clave(leida.puesto()) + "|" + clave(leida.empresa()));

            if (existente.isEmpty()) {
                actuales.add(experiencias.save(ExperienciaPerfil.builder()
                        .perfilCandidatoId(perfilId)
                        .puesto(leida.puesto().trim())
                        .empresa(leida.empresa().trim())
                        .desde(desde).hasta(hasta)
                        .descripcion(recortar(leida.descripcion()))
                        .origen(CURRICULUM)
                        .orden(siguienteOrden++)
                        .creadoEn(Instant.now())
                        .build()));
            } else if (sePuedeActualizar(existente.get().getOrigen(),
                    existente.get().getConfirmadoEn())) {
                ExperienciaPerfil e = existente.get();
                e.setDesde(desde);
                e.setHasta(hasta);
                e.setDescripcion(recortar(leida.descripcion()));
                experiencias.save(e);
            }
        }
    }

    private void proponerEducacion(Long perfilId, List<EducacionLeida> leidas) {
        if (leidas == null) {
            return;
        }
        List<EducacionPerfil> actuales = educaciones.findByPerfilCandidatoIdOrderByOrden(perfilId);
        int siguienteOrden = actuales.stream().map(EducacionPerfil::getOrden)
                .filter(Objects::nonNull).max(Integer::compare).orElse(0) + 1;

        for (EducacionLeida leida : leidas) {
            if (enBlanco(leida.titulo()) || enBlanco(leida.institucion())) {
                continue;
            }
            // Un nivel fuera del catalogo no tumba el elemento: el titulo y la institucion
            // valen solos, y el nivel es opcional en la tabla.
            String nivel = leida.nivel() != null
                    && nivelesEducativos.existsById(leida.nivel()) ? leida.nivel() : null;

            Optional<EducacionPerfil> existente = buscar(actuales,
                    e -> clave(e.getTitulo()) + "|" + clave(e.getInstitucion()),
                    clave(leida.titulo()) + "|" + clave(leida.institucion()));

            if (existente.isEmpty()) {
                actuales.add(educaciones.save(EducacionPerfil.builder()
                        .perfilCandidatoId(perfilId)
                        .titulo(leida.titulo().trim())
                        .institucion(leida.institucion().trim())
                        .nivelCodigo(nivel)
                        .desde(mes(leida.desde())).hasta(mes(leida.hasta()))
                        .enCurso(false)
                        .origen(CURRICULUM)
                        .orden(siguienteOrden++)
                        .creadoEn(Instant.now())
                        .build()));
            } else if (sePuedeActualizar(existente.get().getOrigen(),
                    existente.get().getConfirmadoEn())) {
                EducacionPerfil e = existente.get();
                e.setNivelCodigo(nivel);
                e.setDesde(mes(leida.desde()));
                e.setHasta(mes(leida.hasta()));
                educaciones.save(e);
            }
        }
    }

    private void proponerIdiomas(Long perfilId, List<IdiomaLeido> leidos) {
        if (leidos == null) {
            return;
        }
        List<IdiomaPerfil> actuales = idiomas.findByPerfilCandidatoIdOrderByIdioma(perfilId);

        for (IdiomaLeido leido : leidos) {
            if (enBlanco(leido.idioma())) {
                continue;
            }
            // Aqui el nivel si tumba el elemento: un idioma sin nivel valido no dice nada.
            if (leido.nivel() == null || !nivelesIdioma.existsById(leido.nivel())) {
                log.info("Idioma con nivel fuera del catalogo, descartado: {} ({})",
                        leido.idioma(), leido.nivel());
                continue;
            }

            Optional<IdiomaPerfil> existente = buscar(actuales,
                    i -> clave(i.getIdioma()), clave(leido.idioma()));

            if (existente.isEmpty()) {
                actuales.add(idiomas.save(IdiomaPerfil.builder()
                        .perfilCandidatoId(perfilId)
                        .idioma(leido.idioma().trim())
                        .nivelCodigo(leido.nivel())
                        .origen(CURRICULUM)
                        .creadoEn(Instant.now())
                        .build()));
            } else if (sePuedeActualizar(existente.get().getOrigen(),
                    existente.get().getConfirmadoEn())) {
                IdiomaPerfil i = existente.get();
                i.setNivelCodigo(leido.nivel());
                idiomas.save(i);
            }
        }
    }

    private void proponerCertificaciones(Long perfilId, List<CertificacionLeida> leidas) {
        if (leidas == null) {
            return;
        }
        List<CertificacionPerfil> actuales =
                certificaciones.findByPerfilCandidatoIdOrderByNombre(perfilId);

        for (CertificacionLeida leida : leidas) {
            if (enBlanco(leida.nombre())) {
                continue;
            }
            LocalDate emitida = mes(leida.emitidaEn());
            LocalDate vence = mes(leida.venceEn());
            if (vence != null && emitida != null && vence.isBefore(emitida)) {
                vence = null;   // el CHECK la rechazaria; sin vencimiento es «no caduca»
            }

            Optional<CertificacionPerfil> existente = buscar(actuales,
                    c -> clave(c.getNombre()), clave(leida.nombre()));

            if (existente.isEmpty()) {
                actuales.add(certificaciones.save(CertificacionPerfil.builder()
                        .perfilCandidatoId(perfilId)
                        .nombre(leida.nombre().trim())
                        .entidad(recortar(leida.entidad()))
                        .emitidaEn(emitida).venceEn(vence)
                        .origen(CURRICULUM)
                        .creadoEn(Instant.now())
                        .build()));
            } else if (sePuedeActualizar(existente.get().getOrigen(),
                    existente.get().getConfirmadoEn())) {
                CertificacionPerfil c = existente.get();
                c.setEntidad(recortar(leida.entidad()));
                c.setEmitidaEn(emitida);
                c.setVenceEn(vence);
                certificaciones.save(c);
            }
        }
    }

    // ==================== Las reglas, en un solo sitio ====================

    /** Solo lo CURRICULUM y sin confirmar se actualiza. Lo demas es de la persona. */
    private static boolean sePuedeActualizar(String origen, Instant confirmadoEn) {
        return CURRICULUM.equals(origen) && confirmadoEn == null;
    }

    private static <T> Optional<T> buscar(List<T> actuales, Function<T, String> claveDe,
                                          String buscada) {
        return actuales.stream().filter(e -> claveDe.apply(e).equals(buscada)).findFirst();
    }

    /** Sin mayusculas, sin tildes y sin espacios de mas: «Analista Senior» = «analista senior». */
    static String clave(String texto) {
        if (texto == null) {
            return "";
        }
        String plano = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return plano.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** «AAAA-MM» o «AAAA» al primer dia del mes; null si no cuadra. No se adivina. */
    static LocalDate mes(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.trim();
        try {
            if (t.matches("\\d{4}-\\d{2}")) {
                return LocalDate.parse(t + "-01");
            }
            if (t.matches("\\d{4}")) {
                return LocalDate.parse(t + "-01-01");
            }
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(t);
            }
        } catch (RuntimeException e) {
            // un «2023-13» cae aqui: se descarta, no se corrige
        }
        return null;
    }

    private static boolean enBlanco(String texto) {
        return texto == null || texto.isBlank();
    }

    private static boolean mesesValidos(Integer meses) {
        return meses != null && meses >= 0 && meses <= 720;
    }

    private static String recortar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.length() <= 500 ? limpio : limpio.substring(0, 500);
    }
}
