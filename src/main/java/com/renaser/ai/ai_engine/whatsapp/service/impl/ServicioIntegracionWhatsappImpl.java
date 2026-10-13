package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Baja;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Novedad;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Novedades;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.PeticionVinculo;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.PostulacionVinculada;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Vinculo;
import com.renaser.ai.ai_engine.whatsapp.entity.BajaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.BetaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.Celular;
import com.renaser.ai.ai_engine.whatsapp.service.ServicioIntegracionWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.TextosDeWhatsapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ver {@link ServicioIntegracionWhatsapp}.
 *
 * <p>EX es quien sabe: el bot solo guarda qué números le anunciamos. Si dice que un número
 * escribió, aquí se comprueba que hay un anuncio vigente para él, que el proceso sigue vivo y que
 * la beta está encendida; lo que se le devuelve son las mismas palabras que el portal
 * ({@link TextosDelMomento}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioIntegracionWhatsappImpl implements ServicioIntegracionWhatsapp {

    /** Un número que no es E.164 no es de nadie: 400. */
    static final String NUMERO_QUE_NO_VALE =
            "El teléfono tiene que venir en formato internacional (E.164), como +51987654321";

    private final VinculoWhatsappRepository vinculos;
    private final AvisoWhatsappRepository avisos;
    private final BajaWhatsappRepository bajas;
    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final EstadoPostulacionRepository estados;
    private final IntentoPruebaRepository intentos;
    private final PersonaRepository personas;
    private final BetaWhatsapp beta;

    @Override
    @Transactional
    public ResultadoVinculo vincular(PeticionVinculo peticion) {
        String telefono = Celular.delBot(peticion.telefono()).orElse(null);
        if (telefono == null) {
            throw new IllegalArgumentException(NUMERO_QUE_NO_VALE);
        }
        if (!beta.encendida()) {
            return ResultadoVinculo.rechazado(409, "BETA_APAGADA", TextosDeWhatsapp.BETA_APAGADA);
        }
        Instant ahora = Instant.now();
        Instant cuando = peticion.recibidoEn() == null || peticion.recibidoEn().isAfter(ahora)
                ? ahora : peticion.recibidoEn();
        Set<String> finales = finales();

        // Todos los anunciados de ese número, de una vez: lo único que cuenta es el número.
        int terminados = 0;
        for (VinculoWhatsapp anunciado : vinculos.vivosDelNumeroParaEscribir(telefono)) {
            if (!anunciado.esperaSuMensaje(ahora)) {
                continue;
            }
            Postulacion postulacion = postulaciones.findById(anunciado.getPostulacionId()).orElse(null);
            if (postulacion == null || !sigueVivo(postulacion, finales)
                    || !beta.esDeLaPlataforma(postulacion.getOrganizacionId())) {
                anunciado.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_PROCESO_TERMINADO, ahora);
                vinculos.save(anunciado);
                terminados++;
                continue;
            }
            anunciado.setEstado(VinculoWhatsapp.ACTIVO);
            anunciado.setVinculadoEn(cuando);
            anunciado.setPrimerWamid(peticion.wamid());
            anunciado.setAnuncioProximoIntentoEn(null);
            vinculos.save(anunciado);
        }
        vinculos.flush();

        List<Contado> activos = contar(vinculos.activosDelNumero(telefono), finales);
        if (activos.isEmpty()) {
            if (terminados > 0 || vinculos.terminoConElAnuncioVigente(telefono, ahora)) {
                return ResultadoVinculo.rechazado(409, "PROCESO_TERMINADO",
                        TextosDeWhatsapp.SIN_PROCESOS_ACTIVOS);
            }
            return ResultadoVinculo.rechazado(404, "SIN_ANUNCIO", TextosDeWhatsapp.SIN_ANUNCIO);
        }
        return ResultadoVinculo.vinculado(new Vinculo(
                activos.stream().map(c -> new PostulacionVinculada(c.vinculo().getReferencia())).toList(),
                nombreDe(activos),
                activos.stream().map(Contado::novedad).toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public Novedades novedades(String telefono) {
        // Con la beta apagada también: los vínculos activos siguen recibiendo novedades si escriben.
        String numero = Celular.delBot(telefono).orElse(null);
        if (numero == null) {
            throw new IllegalArgumentException(NUMERO_QUE_NO_VALE);
        }
        List<Contado> activos = contar(vinculos.activosDelNumero(numero), finales());
        return new Novedades(nombreDe(activos), activos.stream().map(Contado::novedad).toList());
    }

    @Override
    @Transactional
    public Baja baja(String telefono) {
        String numero = Celular.delBot(telefono).orElse(null);
        if (numero == null) {
            throw new IllegalArgumentException(NUMERO_QUE_NO_VALE);
        }
        Instant ahora = Instant.now();
        List<VinculoWhatsapp> vivos = vinculos.vivosDelNumeroParaEscribir(numero);
        for (VinculoWhatsapp vivo : vivos) {
            vivo.cerrar(VinculoWhatsapp.SUPRIMIDO, VinculoWhatsapp.POR_BAJA, ahora);
        }
        vinculos.saveAll(vivos);
        if (!vivos.isEmpty()) {
            avisos.omitirPendientesDe(vivos.stream().map(VinculoWhatsapp::getId).toList(), ahora);
        }
        if (!bajas.existsById(numero)) {
            bajas.save(BajaWhatsapp.builder().telefono(numero).dadaEn(ahora).build());
        }
        log.info("El número {} se dio de baja de WhatsApp: {} vínculo(s) cerrado(s)",
                Celular.enmascarado(numero), vivos.size());
        return new Baja(vivos.size());
    }

    // ============ las novedades ============

    /** Un vínculo activo con lo que se le cuenta de su proceso. */
    private record Contado(VinculoWhatsapp vinculo, Postulacion postulacion, Novedad novedad) {}

    /**
     * La novedad de cada vínculo activo cuyo proceso sigue vivo. Los que ya terminaron no se
     * cuentan: su vínculo se cierra al terminar, y si ese cierre se perdió, aquí no asoman.
     */
    private List<Contado> contar(List<VinculoWhatsapp> activos, Set<String> finales) {
        if (activos.isEmpty()) {
            return List.of();
        }
        Map<Long, Postulacion> porId = postulaciones.findAllById(
                        activos.stream().map(VinculoWhatsapp::getPostulacionId).toList()).stream()
                .collect(Collectors.toMap(Postulacion::getId, Function.identity()));
        Map<Long, Vacante> vacantePorId = vacantes.findAllById(porId.values().stream()
                        .map(Postulacion::getVacanteId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Vacante::getId, Function.identity()));
        Set<Long> sinCompletar = intentos.findByPostulacionIdIn(porId.keySet()).stream()
                .filter(IntentoPrueba::isNoCompletada)
                .map(IntentoPrueba::getPostulacionId)
                .collect(Collectors.toSet());

        List<Contado> contados = new ArrayList<>();
        for (VinculoWhatsapp vinculo : activos) {
            Postulacion postulacion = porId.get(vinculo.getPostulacionId());
            if (postulacion == null || !sigueVivo(postulacion, finales)) {
                continue;
            }
            Vacante vacante = vacantePorId.get(postulacion.getVacanteId());
            if (vacante == null || vacante.getEliminadaEn() != null) {
                continue;
            }
            TextosDelMomento.Texto momento = TextosDelMomento.de(postulacion.getEstadoCodigo(),
                    vacante.getInstrumentoEtapaTecnica(), sinCompletar.contains(postulacion.getId()));
            contados.add(new Contado(vinculo, postulacion, new Novedad(vinculo.getReferencia(),
                    vacante.getTitulo(), momento.titulo(), momento.texto())));
        }
        return contados;
    }

    /** El nombre de pila de quien vinculó el primero: con él saluda el bot. */
    private String nombreDe(List<Contado> activos) {
        if (activos.isEmpty()) {
            return null;
        }
        return personas.findById(activos.get(0).vinculo().getPersonaId())
                .map(Persona::getNombre)
                .map(String::trim)
                .filter(n -> !n.isEmpty())
                .orElse(null);
    }

    private static boolean sigueVivo(Postulacion postulacion, Set<String> finales) {
        return postulacion.getEstadoCodigo() != null && !finales.contains(postulacion.getEstadoCodigo());
    }

    private Set<String> finales() {
        return estados.findAllByOrderByOrden().stream()
                .filter(EstadoPostulacion::isEsFinal)
                .map(EstadoPostulacion::getCodigo)
                .collect(Collectors.toSet());
    }
}
