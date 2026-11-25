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
import java.util.HashSet;
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
    private final com.renaser.ai.ai_engine.whatsapp.service.ResultadosPorWhatsapp resultados;
    // El interruptor de WhatsApp de la cuenta (V92): lo encienden el mensaje y lo apaga la BAJA.
    private final com.renaser.ai.ai_engine.notificacion.service.PreferenciasDeAviso preferencias;

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
        Set<Long> escribieron = new HashSet<>();
        for (VinculoWhatsapp anunciado : vinculos.vivosDelNumeroParaEscribir(telefono)) {
            if (!anunciado.esperaSuMensaje(ahora)) {
                continue;
            }
            escribieron.add(anunciado.getPersonaId());
            // Sin postulación es el anuncio del número solo (V92): no tenía procesos vivos. Se
            // cierra como un proceso terminado y contesta «no tienes procesos activos».
            Postulacion postulacion = anunciado.getPostulacionId() == null ? null
                    : postulaciones.findById(anunciado.getPostulacionId()).orElse(null);
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
        if (!escribieron.isEmpty()) {
            alEscribirConAnuncio(telefono, escribieron);
        }

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
                lasNovedades(activos)));
    }

    @Override
    @Transactional(readOnly = true)
    public Novedades novedades(String telefono) {
        // Con la beta apagada también: los vínculos activos siguen recibiendo novedades si escriben.
        String numero = Celular.delBot(telefono).orElse(null);
        if (numero == null) {
            throw new IllegalArgumentException(NUMERO_QUE_NO_VALE);
        }
        return new Novedades(lasNovedades(contar(vinculos.activosDelNumero(numero), finales())));
    }

    @Override
    @Transactional
    public int baja(String telefono) {
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
        // También lo ya cerrado (V76): sus resultados dejan de consultarse y sus eventos
        // pendientes —el aviso final incluido— ya no salen.
        resultados.alDarseDeBaja(numero, ahora);
        if (!bajas.existsById(numero)) {
            bajas.save(BajaWhatsapp.builder().telefono(numero).dadaEn(ahora).build());
        }
        // El interruptor de WhatsApp de quien tiene ese número se apaga, y el mensaje flotante
        // vuelve a poder salir para invitarle a volver (V92).
        Set<Long> suyas = new HashSet<>();
        vivos.forEach(v -> suyas.add(v.getPersonaId()));
        personas.findAllByTelefono(numero).forEach(p -> suyas.add(p.getId()));
        suyas.forEach(preferencias::alDarseDeBaja);
        log.info("El número {} se dio de baja de WhatsApp: {} vínculo(s) cerrado(s)",
                Celular.enmascarado(numero), vivos.size());
        return vivos.size();
    }

    /**
     * Escribió desde un número que esperábamos (V92): el interruptor de esa persona queda
     * encendido para todos sus procesos y, si el número tenía la BAJA, sale de ahí —el bot ya la
     * levantó al recibir el mensaje con la espera vigente—.
     */
    private void alEscribirConAnuncio(String telefono, Set<Long> personasQueEscribieron) {
        personasQueEscribieron.forEach(personaId -> preferencias.whatsapp(personaId, true));
        if (bajas.existsById(telefono)) {
            bajas.deleteById(telefono);
        }
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
            contados.add(new Contado(vinculo, postulacion, new Novedad(
                    TextosDeWhatsapp.cortar(vacante.getTitulo(), TextosDeWhatsapp.LARGO_DE_LA_VACANTE),
                    TextosDeWhatsapp.cortar(momento.titulo(), TextosDeWhatsapp.LARGO_DEL_MOMENTO),
                    TextosDeWhatsapp.cortar(momento.texto(), TextosDeWhatsapp.LARGO_DE_LA_NOVEDAD))));
        }
        return contados;
    }

    /** Las novedades que caben en una respuesta: como mucho ocho, las de los vínculos más recientes. */
    private static List<Novedad> lasNovedades(List<Contado> activos) {
        return activos.stream().limit(TextosDeWhatsapp.MAXIMO_DE_NOVEDADES).map(Contado::novedad).toList();
    }

    /** El nombre de pila de quien vinculó el primero: con él saluda el bot. */
    private String nombreDe(List<Contado> activos) {
        if (activos.isEmpty()) {
            return null;
        }
        return personas.findById(activos.get(0).vinculo().getPersonaId())
                .map(Persona::getNombre)
                .map(n -> TextosDeWhatsapp.cortar(n, TextosDeWhatsapp.LARGO_DEL_NOMBRE))
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
