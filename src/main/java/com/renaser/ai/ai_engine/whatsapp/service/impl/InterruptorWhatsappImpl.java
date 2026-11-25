package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.consentimiento.entity.Consentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.TipoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.service.TextosDeConsentimiento;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones;
import com.renaser.ai.ai_engine.notificacion.dto.DtosNotificaciones.WhatsappDeLaCuenta;
import com.renaser.ai.ai_engine.notificacion.entity.PreferenciaAviso;
import com.renaser.ai.ai_engine.notificacion.service.PreferenciasDeAviso;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.AvisoDeEtapaEnLaCampana;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.entity.BajaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.BetaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.Celular;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.ConsentimientoDeLaVinculacion;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.FalloDelBot;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.PostulacionAVincular;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion.Vinculacion;
import com.renaser.ai.ai_engine.whatsapp.service.EnvioAlBot;
import com.renaser.ai.ai_engine.whatsapp.service.InterruptorWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.TextosDeWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.WhatsappNoActivadoException;
import com.renaser.ai.ai_engine.whatsapp.service.WhatsappNoDisponibleException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ver {@link InterruptorWhatsapp}.
 *
 * <p>Encenderlo va entero en una transacción con la fila de sus preferencias bloqueada: el doble
 * clic pasa de uno en uno y el segundo no encuentra nada que hacer. En el camino de un clic la
 * llamada al bot va dentro, para que su fallo no deje nada a medias (AC-13); espera como mucho
 * {@code app.whatsapp.segundos-de-espera}. En el camino con mensaje, los anuncios salen después
 * del commit, como los del botón de siempre.
 */
@Service
@Slf4j
public class InterruptorWhatsappImpl implements InterruptorWhatsapp {

    /** Lo que el sondeo espera antes de reintentar un anuncio que se está mandando ahora. */
    private static final Duration RESPALDO_DEL_ANUNCIO = Duration.ofSeconds(60);

    private static final SecureRandom AZAR = new SecureRandom();

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final EstadoPostulacionRepository estados;
    private final PersonaRepository personas;
    private final VinculoWhatsappRepository vinculos;
    private final AvisoWhatsappRepository avisos;
    private final BajaWhatsappRepository bajas;
    private final ConsentimientoRepository consentimientos;
    private final TextoConsentimientoRepository textosFirmados;
    private final TextosDeConsentimiento textos;
    private final DuenoDelInstrumento duenos;
    private final BetaWhatsapp beta;
    private final ClienteConversacion cliente;
    private final EnvioAlBot envio;
    private final PropiedadesWhatsapp propiedades;
    private final PreferenciasDeAviso preferencias;
    private final TransactionTemplate enUnaTransaccion;
    /** Después del commit de otra transacción (postular): la de entonces ya no sirve. */
    private final TransactionTemplate enOtraTransaccion;

    @SuppressWarnings("java:S107") // Cada uno es una tabla o una regla que el interruptor toca.
    public InterruptorWhatsappImpl(PostulacionRepository postulaciones, VacanteRepository vacantes,
                                   EstadoPostulacionRepository estados, PersonaRepository personas,
                                   VinculoWhatsappRepository vinculos, AvisoWhatsappRepository avisos,
                                   BajaWhatsappRepository bajas, ConsentimientoRepository consentimientos,
                                   TextoConsentimientoRepository textosFirmados,
                                   TextosDeConsentimiento textos, DuenoDelInstrumento duenos,
                                   BetaWhatsapp beta, ClienteConversacion cliente, EnvioAlBot envio,
                                   PropiedadesWhatsapp propiedades, PreferenciasDeAviso preferencias,
                                   PlatformTransactionManager transacciones) {
        this.postulaciones = postulaciones;
        this.vacantes = vacantes;
        this.estados = estados;
        this.personas = personas;
        this.vinculos = vinculos;
        this.avisos = avisos;
        this.bajas = bajas;
        this.consentimientos = consentimientos;
        this.textosFirmados = textosFirmados;
        this.textos = textos;
        this.duenos = duenos;
        this.beta = beta;
        this.cliente = cliente;
        this.envio = envio;
        this.propiedades = propiedades;
        this.preferencias = preferencias;
        this.enUnaTransaccion = new TransactionTemplate(transacciones);
        this.enOtraTransaccion = new TransactionTemplate(transacciones);
        this.enOtraTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ============ El estado ============

    @Override
    public Estado estado(Long personaId, Long usuarioId) {
        Persona persona = personas.findById(personaId).orElse(null);
        String telefono = persona == null ? null : persona.getTelefono();
        PreferenciaAviso preferencia = preferencias.de(personaId);
        List<VinculoWhatsapp> suyos = vinculos.findByPersonaId(personaId);
        BajaWhatsapp baja = telefono == null ? null : bajas.findById(telefono).orElse(null);
        List<Postulacion> todas = postulaciones.findByUsuarioIdOrderByCreadoEnDesc(usuarioId);
        Instant ultimaPostulacion = todas.stream().map(Postulacion::getCreadoEn).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        WhatsappDeLaCuenta whatsapp = deLaCuenta(beta.encendida(), telefono, preferencia.isWhatsappActivo(),
                suyos, baja, Instant.now());
        return new Estado(whatsapp, !vivasEntre(todas).isEmpty(), ultimaPostulacion);
    }

    /**
     * En qué está WhatsApp para la cuenta, por orden: sin número; activo (el interruptor manda,
     * aunque algún proceso espere todavía); esperando su mensaje (algún anuncio vigente); no llegó
     * (el último anuncio caducó sin mensaje); y apagado, con o sin BAJA.
     */
    static WhatsappDeLaCuenta deLaCuenta(boolean disponible, String telefono, boolean activo,
                                         List<VinculoWhatsapp> suyos, BajaWhatsapp baja, Instant ahora) {
        if (telefono == null) {
            return new WhatsappDeLaCuenta(disponible, DtosNotificaciones.SIN_NUMERO, null, false, false,
                    null, false);
        }
        List<VinculoWhatsapp> deEsteNumero = suyos.stream().filter(v -> telefono.equals(v.getTelefono())).toList();
        boolean comprobado = deEsteNumero.stream().anyMatch(v -> v.getVinculadoEn() != null);
        boolean cambioDeNumero = !comprobado && suyos.stream()
                .anyMatch(v -> !telefono.equals(v.getTelefono()) && v.getVinculadoEn() != null);
        boolean porBaja = baja != null;
        java.time.LocalDate bajaEn = baja == null || baja.getDadaEn() == null ? null
                : baja.getDadaEn().atZone(TextosDeWhatsapp.ZONA).toLocalDate();

        String estado;
        if (activo) {
            estado = DtosNotificaciones.ACTIVO;
        } else if (deEsteNumero.stream().anyMatch(v -> v.esperaSuMensaje(ahora))) {
            estado = DtosNotificaciones.ESPERANDO;
        } else if (ultimoAnuncio(deEsteNumero).filter(v -> VinculoWhatsapp.PENDIENTE.equals(v.getEstado()))
                .isPresent()) {
            // Sigue PENDIENTE y ya no espera: caducó sin que escribiera.
            estado = DtosNotificaciones.NO_LLEGO;
        } else {
            estado = DtosNotificaciones.APAGADO;
        }
        String numero = activo ? Celular.enmascarado(telefono) : Celular.paraMostrar(telefono);
        return new WhatsappDeLaCuenta(disponible, estado, numero, comprobado, porBaja, bajaEn, cambioDeNumero);
    }

    /** El último anunciado de ese número: el que dice si el último intento llegó o no. */
    private static Optional<VinculoWhatsapp> ultimoAnuncio(List<VinculoWhatsapp> deEsteNumero) {
        return deEsteNumero.stream()
                .filter(v -> v.getAnunciadoEn() != null)
                .max(Comparator.comparing(VinculoWhatsapp::getAnunciadoEn)
                        .thenComparing(VinculoWhatsapp::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    // ============ Encenderlo ============

    /** Lo que deja hecho la transacción: lo que se contesta y los anuncios que salen después. */
    private record Hecho(String resultado, String enlace, boolean confirmacion, List<Long> porAnunciar) {}

    /** Una postulación por vincular, con el vínculo vivo que ya tenía (esperando su mensaje) o sin él. */
    private record PorVincular(Postulacion postulacion, VinculoWhatsapp vivo, String referencia) {}

    @Override
    public Activacion activar(ContextoUsuario quien, String ip, String userAgent) {
        Hecho hecho = enUnaTransaccion.execute(t -> encender(quien, ip, userAgent));
        Objects.requireNonNull(hecho, "Encender WhatsApp no dejó nada hecho");
        // Ya confirmado: el bot recibe números que de verdad quedaron guardados.
        hecho.porAnunciar().forEach(envio::anunciarAhora);
        return new Activacion(hecho.resultado(), hecho.enlace(), hecho.confirmacion());
    }

    private Hecho encender(ContextoUsuario quien, String ip, String userAgent) {
        Persona persona = personas.findById(quien.personaId())
                .orElseThrow(() -> new ResourceNotFoundException("Persona", "id", quien.personaId()));
        String telefono = persona.getTelefono();
        if (telefono == null) {
            throw new WhatsappNoDisponibleException(WhatsappNoDisponibleException.SIN_CELULAR,
                    "Tu cuenta no tiene celular: agrégalo para recibir tus avisos por WhatsApp");
        }
        if (!beta.encendida()) {
            throw new WhatsappNoDisponibleException(WhatsappNoDisponibleException.NO_DISPONIBLE,
                    "Los avisos por WhatsApp no están disponibles por ahora");
        }
        // La fila, bloqueada hasta el final: dos clics a la vez pasan de uno en uno.
        preferencias.bloquear(persona.getId());
        Instant ahora = Instant.now();
        TextoConsentimiento texto = textos.exigirVigente(duenos.plataforma().getId(),
                TipoConsentimiento.WHATSAPP_CUENTA);
        Consentimiento firma = firmaDeLaCuenta(persona, texto, ip, userAgent, ahora);
        List<Postulacion> vivas = vivasEntre(postulaciones.findByUsuarioIdOrderByCreadoEnDesc(quien.usuarioId()));
        boolean comprobado = vinculos.findByPersonaId(persona.getId()).stream()
                .anyMatch(v -> telefono.equals(v.getTelefono()) && v.getVinculadoEn() != null);
        if (comprobado) {
            Optional<Hecho> conUnClic = conUnClic(persona, telefono, vivas, firma, texto, ahora);
            if (conUnClic.isPresent()) {
                return conUnClic.get();
            }
            // 404 SIN_CONVERSACION: el bot no conoce el número; se comprueba con un mensaje.
        }
        return porMensaje(persona, telefono, vivas, firma, ahora);
    }

    /**
     * El número ya está comprobado: el bot vincula sus procesos vivos sin mensaje. Vacío si el bot
     * contesta {@code 404 SIN_CONVERSACION}; cualquier otro fallo es un 503 y se deshace todo.
     */
    private Optional<Hecho> conUnClic(Persona persona, String telefono, List<Postulacion> vivas,
                                      Consentimiento firma, TextoConsentimiento texto, Instant ahora) {
        List<PorVincular> porVincular = new ArrayList<>();
        for (Postulacion postulacion : vivas) {
            VinculoWhatsapp vivo = vivoConEsteNumero(postulacion.getId(), telefono, ahora);
            if (vivo != null && vivo.estaActivo()) {
                continue;
            }
            porVincular.add(new PorVincular(postulacion, vivo,
                    vivo == null ? nuevaReferencia() : vivo.getReferencia()));
        }
        if (porVincular.isEmpty()) {
            // Sin procesos que vincular: queda encendido para los que vengan. Si el número tiene
            // la BAJA, la levanta la vinculación de su próxima postulación.
            preferencias.whatsapp(persona.getId(), true);
            return Optional.of(new Hecho(DtosNotificaciones.ACTIVADO, null, false, List.of()));
        }

        Map<Long, Vacante> vacantePorId = vacantesDe(porVincular.stream().map(PorVincular::postulacion).toList());
        List<PostulacionAVincular> lista = porVincular.stream()
                .map(p -> new PostulacionAVincular(p.referencia(), tituloCorto(vacantePorId.get(p.postulacion().getVacanteId()))))
                .toList();
        List<String> vinculadas;
        try {
            vinculadas = cliente.vincular(new Vinculacion(telefono, nombreDePila(persona),
                    ClienteConversacion.MOTIVO_ACTIVACION, lista, comoLoFirmo(firma, texto)));
        } catch (FalloDelBot fallo) {
            if (fallo.esSinConversacion()) {
                log.info("El bot no conoce el número {}: se activa con un mensaje", Celular.enmascarado(telefono));
                return Optional.empty();
            }
            log.warn("No se pudo activar WhatsApp para la persona {}: {}", persona.getId(), fallo.getMessage());
            throw new WhatsappNoActivadoException("No pudimos activarlo. Vuelve a intentarlo en un momento.", fallo);
        }

        // Con el 200, y no antes: el número sale de la BAJA y sus procesos quedan vinculados.
        if (bajas.existsById(telefono)) {
            bajas.deleteById(telefono);
        }
        Set<String> hechas = new HashSet<>(vinculadas);
        for (PorVincular uno : porVincular) {
            if (!hechas.contains(uno.referencia())) {
                // El bot lo tenía cerrado (proceso terminado o datos borrados): no se reabre.
                continue;
            }
            VinculoWhatsapp vinculo = uno.vivo() != null ? uno.vivo() : VinculoWhatsapp.builder()
                    .postulacionId(uno.postulacion().getId())
                    .personaId(persona.getId())
                    .referencia(uno.referencia())
                    .telefono(telefono)
                    .anunciadoEn(ahora)
                    .anuncioCaducaEn(ahora)
                    .creadoEn(ahora)
                    .build();
            vinculo.setEstado(VinculoWhatsapp.ACTIVO);
            vinculo.setConsentimientoId(firma.getId());
            vinculo.setVinculadoEn(ahora);
            vinculo.setAnuncioConfirmadoEn(ahora);
            vinculo.setAnuncioProximoIntentoEn(null);
            vinculos.save(vinculo);
        }
        vinculos.flush();
        preferencias.whatsapp(persona.getId(), true);
        return Optional.of(new Hecho(DtosNotificaciones.ACTIVADO, null, true, List.of()));
    }

    /**
     * El número está sin comprobar: se anuncia para cada proceso vivo —lo que el botón de cada
     * proceso hacía para uno— y el portal abre el chat. Sin procesos vivos se anuncia el número
     * solo, para que pueda comprobarlo igual.
     */
    private Hecho porMensaje(Persona persona, String telefono, List<Postulacion> vivas, Consentimiento firma,
                             Instant ahora) {
        List<Long> porAnunciar = new ArrayList<>();
        for (Postulacion postulacion : vivas) {
            VinculoWhatsapp vivo = vivoConEsteNumero(postulacion.getId(), telefono, ahora);
            if (vivo != null && vivo.estaActivo()) {
                continue;
            }
            porAnunciar.add(anunciar(vivo, postulacion.getId(), persona, telefono, firma, ahora).getId());
        }
        if (porAnunciar.isEmpty()) {
            VinculoWhatsapp sinPostulacion = vinculos.vivosDeLaPersonaParaEscribir(persona.getId()).stream()
                    .filter(v -> v.getPostulacionId() == null && telefono.equals(v.getTelefono()))
                    .findFirst().orElse(null);
            porAnunciar.add(anunciar(sinPostulacion, null, persona, telefono, firma, ahora).getId());
        }
        String titulo = vivas.size() == 1
                ? Optional.ofNullable(vacantesDe(vivas).get(vivas.get(0).getVacanteId())).map(Vacante::getTitulo).orElse(null)
                : null;
        String enlace = TextosDeWhatsapp.enlace(propiedades.getNumeroBot(), persona.getNombre(),
                persona.getApellidos(), titulo);
        return new Hecho(DtosNotificaciones.POR_MENSAJE, enlace, false, porAnunciar);
    }

    /** Nuevo o renovado: el anuncio vuelve a durar sus horas desde ahora. */
    private VinculoWhatsapp anunciar(VinculoWhatsapp vivo, Long postulacionId, Persona persona, String telefono,
                                     Consentimiento firma, Instant ahora) {
        VinculoWhatsapp vinculo = vivo != null ? vivo : VinculoWhatsapp.builder()
                .postulacionId(postulacionId)
                .personaId(persona.getId())
                .referencia(nuevaReferencia())
                .telefono(telefono)
                .estado(VinculoWhatsapp.PENDIENTE)
                .creadoEn(ahora)
                .build();
        vinculo.setConsentimientoId(firma.getId());
        vinculo.setAnunciadoEn(ahora);
        vinculo.setAnuncioCaducaEn(ahora.plus(Duration.ofHours(Math.max(1, propiedades.getHorasDelAnuncio()))));
        vinculo.setAnuncioConfirmadoEn(null);
        vinculo.setAnuncioIntentos(0);
        vinculo.setAnuncioProximoIntentoEn(ahora.plus(RESPALDO_DEL_ANUNCIO));
        return vinculos.saveAndFlush(vinculo);
    }

    /**
     * El vínculo vivo de esta postulación con el número de ahora, bloqueado. Si era de otro número
     * (no debería quedar ninguno: cambiar el celular los cierra), se cierra y se revoca.
     */
    private VinculoWhatsapp vivoConEsteNumero(Long postulacionId, String telefono, Instant ahora) {
        VinculoWhatsapp vivo = vinculos.vivoParaEscribir(postulacionId).orElse(null);
        if (vivo != null && !telefono.equals(vivo.getTelefono())) {
            vivo.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_CAMBIO_DE_CELULAR, ahora);
            vinculos.saveAndFlush(vivo);
            avisos.omitirPendientesDe(List.of(vivo.getId()), ahora);
            return null;
        }
        return vivo;
    }

    /** Su firma del WHATSAPP_CUENTA vigente: la que ya tenía o una nueva. Una por persona y versión. */
    private Consentimiento firmaDeLaCuenta(Persona persona, TextoConsentimiento texto, String ip, String userAgent,
                                           Instant ahora) {
        return consentimientos.findFirstByPersonaIdAndPostulacionIdIsNullAndTextoConsentimientoId(
                        persona.getId(), texto.getId())
                .orElseGet(() -> consentimientos.save(Consentimiento.builder()
                        .personaId(persona.getId())
                        .textoConsentimientoId(texto.getId())
                        .nombreRegistrado(nombreCompleto(persona))
                        .aceptadoEn(ahora)
                        .ip(ip)
                        .userAgent(userAgent)
                        .creadoEn(ahora)
                        .build()));
    }

    // ============ Apagarlo ============

    @Override
    public void apagar(ContextoUsuario quien) {
        List<Long> cerrados = enUnaTransaccion.execute(t -> {
            preferencias.bloquear(quien.personaId());
            Instant ahora = Instant.now();
            List<VinculoWhatsapp> vivos = vinculos.vivosDeLaPersonaParaEscribir(quien.personaId());
            for (VinculoWhatsapp vivo : vivos) {
                vivo.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_DEJAR_DE_RECIBIR, ahora);
            }
            vinculos.saveAll(vivos);
            List<Long> ids = vivos.stream().map(VinculoWhatsapp::getId).toList();
            if (!ids.isEmpty()) {
                avisos.omitirPendientesDe(ids, ahora);
            }
            preferencias.whatsapp(quien.personaId(), false);
            return ids;
        });
        // Al bot, ya confirmado; si no responde, el sondeo reintenta.
        Objects.requireNonNull(cerrados, "Apagar WhatsApp no cerró nada").forEach(envio::revocarAhora);
    }

    // ============ Postular con el interruptor encendido ============

    /** Lo que deja preparado la primera transacción para pedirle al bot la vinculación. */
    private record Preparada(Long vinculoId, String telefono, String referencia, String motivo,
                             Vinculacion vinculacion) {}

    @Override
    public void alPostular(Long personaId, Postulacion postulacion) {
        AvisoDeEtapaEnLaCampana.alConfirmar(() -> vincularLaNueva(personaId, postulacion),
                "WhatsApp: vincular la postulación nueva", postulacion.getId());
    }

    /**
     * Después del commit de la postulación: su vínculo, ya guardado y anunciado para el sondeo por
     * si acaso, y la llamada al bot fuera de toda transacción. Si el bot contesta, queda ACTIVO; si
     * no, se queda PENDIENTE y el sondeo de siempre anuncia el número.
     */
    void vincularLaNueva(Long personaId, Postulacion postulacion) {
        Long postulacionId = postulacion.getId();
        Preparada lista = enOtraTransaccion.execute(t -> prepararLaNueva(personaId, postulacion));
        if (lista == null) {
            return;
        }
        List<String> vinculadas;
        try {
            vinculadas = cliente.vincular(lista.vinculacion());
        } catch (FalloDelBot fallo) {
            log.warn("El bot no vinculó la postulación {} ({}): queda anunciada para el sondeo: {}",
                    postulacionId, lista.motivo(), fallo.getMessage());
            return;
        }
        if (vinculadas.contains(lista.referencia())) {
            enOtraTransaccion.executeWithoutResult(t -> confirmarLaNueva(lista));
        }
    }

    private Preparada prepararLaNueva(Long personaId, Postulacion postulacion) {
        if (!preferencias.de(personaId).isWhatsappActivo() || !beta.encendida()) {
            return null;
        }
        Long postulacionId = postulacion.getId();
        if (vivasEntre(List.of(postulacion)).isEmpty()) {
            return null;
        }
        Persona persona = personas.findById(personaId).orElse(null);
        String telefono = persona == null ? null : persona.getTelefono();
        if (telefono == null || vinculos.vivoParaEscribir(postulacionId).isPresent()) {
            return null;
        }
        Firmada firmada = ultimaFirmaDeWhatsapp(personaId).orElse(null);
        if (firmada == null) {
            log.warn("La persona {} tiene WhatsApp encendido y ningún consentimiento de WhatsApp: la "
                    + "postulación {} no se vincula", personaId, postulacionId);
            return null;
        }
        Instant ahora = Instant.now();
        VinculoWhatsapp nuevo = anunciar(null, postulacionId, persona, telefono, firmada.firma(), ahora);
        Vacante vacante = vacantesDe(List.of(postulacion)).get(postulacion.getVacanteId());
        // Con la BAJA todavía puesta —encendió sin procesos vivos—, esta es su activación.
        String motivo = bajas.existsById(telefono)
                ? ClienteConversacion.MOTIVO_ACTIVACION : ClienteConversacion.MOTIVO_NUEVA_POSTULACION;
        return new Preparada(nuevo.getId(), telefono, nuevo.getReferencia(), motivo,
                new Vinculacion(telefono, nombreDePila(persona), motivo,
                        List.of(new PostulacionAVincular(nuevo.getReferencia(), tituloCorto(vacante))),
                        comoLoFirmo(firmada.firma(), firmada.texto())));
    }

    private void confirmarLaNueva(Preparada lista) {
        VinculoWhatsapp vinculo = vinculos.paraEscribir(lista.vinculoId()).orElse(null);
        if (vinculo == null || !VinculoWhatsapp.PENDIENTE.equals(vinculo.getEstado())) {
            return;
        }
        Instant ahora = Instant.now();
        vinculo.setEstado(VinculoWhatsapp.ACTIVO);
        vinculo.setVinculadoEn(ahora);
        vinculo.setAnuncioConfirmadoEn(ahora);
        vinculo.setAnuncioProximoIntentoEn(null);
        vinculos.save(vinculo);
        if (ClienteConversacion.MOTIVO_ACTIVACION.equals(lista.motivo()) && bajas.existsById(lista.telefono())) {
            bajas.deleteById(lista.telefono());
        }
    }

    /** Un consentimiento firmado con su texto. */
    private record Firmada(Consentimiento firma, TextoConsentimiento texto) {}

    /**
     * Con qué consentimiento se vincula una postulación nueva: el de la cuenta más reciente, o —en
     * quien encendió WhatsApp antes de la V92, proceso por proceso— el último de un proceso.
     */
    private Optional<Firmada> ultimaFirmaDeWhatsapp(Long personaId) {
        for (TipoConsentimiento tipo : List.of(TipoConsentimiento.WHATSAPP_CUENTA, TipoConsentimiento.WHATSAPP)) {
            for (Consentimiento firma : consentimientos.deTipoMasRecientes(personaId, tipo.codigo())) {
                Optional<TextoConsentimiento> texto = textosFirmados.findById(firma.getTextoConsentimientoId());
                if (texto.isPresent()) {
                    return Optional.of(new Firmada(firma, texto.get()));
                }
            }
        }
        return Optional.empty();
    }

    // ============ ayudas ============

    /**
     * Las postulaciones vivas que pueden usar WhatsApp: sin terminar, con la vacante en pie y de
     * la beta (de RENASER). Se respeta el orden en que llegan.
     */
    private List<Postulacion> vivasEntre(List<Postulacion> suyas) {
        if (suyas.isEmpty()) {
            return List.of();
        }
        Set<String> finales = finales();
        List<Postulacion> candidatas = suyas.stream()
                .filter(p -> p.getEstadoCodigo() != null && !finales.contains(p.getEstadoCodigo()))
                .filter(p -> beta.esDeLaPlataforma(p.getOrganizacionId()))
                .toList();
        if (candidatas.isEmpty()) {
            return List.of();
        }
        Map<Long, Vacante> enPie = vacantesDe(candidatas);
        return candidatas.stream().filter(p -> enPie.containsKey(p.getVacanteId())).toList();
    }

    /** Sus vacantes que siguen en pie (las eliminadas no entran), por id. */
    private Map<Long, Vacante> vacantesDe(List<Postulacion> suyas) {
        List<Long> ids = suyas.stream().map(Postulacion::getVacanteId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return vacantes.findAllById(ids).stream()
                .filter(v -> v.getEliminadaEn() == null)
                .collect(Collectors.toMap(Vacante::getId, Function.identity(), (a, b) -> a));
    }

    private Set<String> finales() {
        return estados.findAllByOrderByOrden().stream()
                .filter(EstadoPostulacion::isEsFinal)
                .map(EstadoPostulacion::getCodigo)
                .collect(Collectors.toSet());
    }

    /** El texto exacto que firmó (el pintado, si se guardó; si no, el de su versión). */
    private static ConsentimientoDeLaVinculacion comoLoFirmo(Consentimiento firma, TextoConsentimiento texto) {
        String leido = firma.getTextoFirmado() == null || firma.getTextoFirmado().isBlank()
                ? texto.getTexto() : firma.getTextoFirmado();
        return new ConsentimientoDeLaVinculacion(leido, texto.getVersion(), firma.getAceptadoEn());
    }

    /** El título de la vacante con el largo del contrato; vacío si no hay. */
    private static String tituloCorto(Vacante vacante) {
        String titulo = vacante == null ? null
                : TextosDeWhatsapp.cortar(vacante.getTitulo(), TextosDeWhatsapp.LARGO_DE_LA_VACANTE);
        return titulo == null ? "" : titulo;
    }

    /** El nombre de pila con el largo del contrato: con él saluda la plantilla de confirmación. */
    private static String nombreDePila(Persona persona) {
        String nombre = TextosDeWhatsapp.cortar(persona.getNombre(), TextosDeWhatsapp.LARGO_DEL_NOMBRE);
        return nombre == null ? "" : nombre;
    }

    private static String nombreCompleto(Persona persona) {
        String nombre = persona.getNombre() == null ? "" : persona.getNombre().trim();
        String apellidos = persona.getApellidos() == null ? "" : persona.getApellidos().trim();
        String junto = (nombre + " " + apellidos).trim();
        return junto.isEmpty() ? null : junto;
    }

    /** 128 bits de azar en hexadecimal: no dice nada de la postulación ni de la persona. */
    private static String nuevaReferencia() {
        byte[] bytes = new byte[16];
        AZAR.nextBytes(bytes);
        return "pw_" + HexFormat.of().formatHex(bytes);
    }
}
