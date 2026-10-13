package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.consentimiento.entity.Consentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.TipoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.ConsentimientoRepository;
import com.renaser.ai.ai_engine.consentimiento.service.TextosDeConsentimiento;
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
import com.renaser.ai.ai_engine.vacante.service.VacanteEliminada;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.EnlaceWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.WhatsappDelProceso;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.AvisoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.BetaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.Celular;
import com.renaser.ai.ai_engine.whatsapp.service.EnvioAlBot;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.ServicioWhatsappPortal;
import com.renaser.ai.ai_engine.whatsapp.service.TextosDeWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.WhatsappNoDisponibleException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ver {@link ServicioWhatsappPortal}.
 *
 * <p>El botón trabaja en dos tiempos: primero guarda —consentimiento y vínculo, en una
 * transacción corta— y después, ya confirmado, le anuncia el número al bot. Así una llamada al
 * bot que tarda no deja una fila bloqueada, y si el bot no responde el anuncio queda para el
 * sondeo y el candidato tiene su enlace igual: si escribe antes de que llegue, volver a pulsar
 * lo arregla.
 */
@Service
public class ServicioWhatsappPortalImpl implements ServicioWhatsappPortal {

    /** Lo que el sondeo espera antes de reintentar un anuncio que el botón ya está mandando. */
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
    private final TextosDeConsentimiento textos;
    private final DuenoDelInstrumento duenos;
    private final BetaWhatsapp beta;
    private final EnvioAlBot envio;
    private final PropiedadesWhatsapp propiedades;
    private final TransactionTemplate enUnaTransaccion;

    @SuppressWarnings("java:S107") // Cada uno es una tabla o una regla que el botón toca.
    public ServicioWhatsappPortalImpl(PostulacionRepository postulaciones, VacanteRepository vacantes,
                                      EstadoPostulacionRepository estados, PersonaRepository personas,
                                      VinculoWhatsappRepository vinculos, AvisoWhatsappRepository avisos,
                                      BajaWhatsappRepository bajas, ConsentimientoRepository consentimientos,
                                      TextosDeConsentimiento textos, DuenoDelInstrumento duenos,
                                      BetaWhatsapp beta, EnvioAlBot envio, PropiedadesWhatsapp propiedades,
                                      PlatformTransactionManager transacciones) {
        this.postulaciones = postulaciones;
        this.vacantes = vacantes;
        this.estados = estados;
        this.personas = personas;
        this.vinculos = vinculos;
        this.avisos = avisos;
        this.bajas = bajas;
        this.consentimientos = consentimientos;
        this.textos = textos;
        this.duenos = duenos;
        this.beta = beta;
        this.envio = envio;
        this.propiedades = propiedades;
        this.enUnaTransaccion = new TransactionTemplate(transacciones);
    }

    // ============ La tarjeta de cada proceso ============

    @Override
    public Map<Long, WhatsappDelProceso> tarjetasDe(Long personaId, List<Postulacion> suyas) {
        if (suyas.isEmpty() || !beta.encendida()) {
            return Map.of();
        }
        Long plataforma = duenos.plataforma().getId();
        Set<String> finales = finales();
        List<Postulacion> deRenaser = suyas.stream()
                .filter(p -> plataforma.equals(p.getOrganizacionId()))
                .filter(p -> !finales.contains(p.getEstadoCodigo()))
                .toList();
        if (deRenaser.isEmpty()) {
            return Map.of();
        }
        String telefono = personas.findById(personaId).map(Persona::getTelefono).orElse(null);
        boolean suprimido = telefono != null && bajas.existsById(telefono);
        Map<Long, VinculoWhatsapp> ultimos = vinculos
                .ultimosDe(deRenaser.stream().map(Postulacion::getId).toList()).stream()
                .collect(Collectors.toMap(VinculoWhatsapp::getPostulacionId, v -> v, (a, b) -> a));
        Instant ahora = Instant.now();
        Map<Long, WhatsappDelProceso> tarjetas = new HashMap<>();
        for (Postulacion p : deRenaser) {
            tarjetas.put(p.getId(), tarjeta(telefono, suprimido, ultimos.get(p.getId()), ahora));
        }
        return tarjetas;
    }

    /**
     * En qué está la tarjeta, por orden:
     * <ol>
     *   <li>sin celular en la cuenta, se le pide;</li>
     *   <li>ya escribió: conectado, con el número enmascarado;</li>
     *   <li>su número se dio de baja: no se le vuelve a ofrecer;</li>
     *   <li>pulsó y el anuncio sigue vigente: «aún no recibimos tu mensaje»;</li>
     *   <li>dejó de recibir: se le vuelve a ofrecer, como al principio;</li>
     *   <li>todo lo demás —nunca pulsó, el anuncio caducó, cambió de celular—: sin vincular.</li>
     * </ol>
     */
    static WhatsappDelProceso tarjeta(String telefono, boolean suprimido, VinculoWhatsapp ultimo,
                                      Instant ahora) {
        if (telefono == null) {
            return new WhatsappDelProceso(true, "SIN_CELULAR", null);
        }
        if (ultimo != null && ultimo.estaActivo()) {
            return new WhatsappDelProceso(true, VinculoWhatsapp.ACTIVO,
                    Celular.enmascarado(ultimo.getTelefono()));
        }
        if (suprimido) {
            return new WhatsappDelProceso(true, VinculoWhatsapp.SUPRIMIDO, null);
        }
        String suNumero = Celular.paraMostrar(telefono);
        if (ultimo != null && ultimo.esperaSuMensaje(ahora) && telefono.equals(ultimo.getTelefono())) {
            return new WhatsappDelProceso(true, VinculoWhatsapp.PENDIENTE, suNumero);
        }
        if (ultimo != null && VinculoWhatsapp.REVOCADO.equals(ultimo.getEstado())
                && VinculoWhatsapp.POR_DEJAR_DE_RECIBIR.equals(ultimo.getMotivoCierre())) {
            return new WhatsappDelProceso(true, VinculoWhatsapp.REVOCADO, suNumero);
        }
        return new WhatsappDelProceso(true, "SIN_VINCULAR", suNumero);
    }

    // ============ El botón ============

    /** Lo que deja listo la primera transacción para la segunda parte del botón. */
    private record Preparado(String enlace, WhatsappDelProceso tarjeta, Long porAnunciar) {}

    @Override
    public EnlaceWhatsapp escribir(ContextoUsuario quien, UUID uuid, String ip, String userAgent) {
        Preparado listo = enUnaTransaccion.execute(t -> preparar(quien, uuid, ip, userAgent));
        Objects.requireNonNull(listo, "El botón de WhatsApp no preparó nada");
        if (listo.porAnunciar() != null) {
            // Ya confirmado: el bot recibe un número que de verdad quedó guardado.
            envio.anunciarAhora(listo.porAnunciar());
        }
        return new EnlaceWhatsapp(listo.enlace(), listo.tarjeta());
    }

    private Preparado preparar(ContextoUsuario quien, UUID uuid, String ip, String userAgent) {
        Postulacion postulacion = laMia(quien, uuid);
        if (!beta.ofrecePara(postulacion.getOrganizacionId())) {
            throw new WhatsappNoDisponibleException(WhatsappNoDisponibleException.NO_DISPONIBLE,
                    "Las novedades por WhatsApp no están disponibles para este proceso");
        }
        // La fila de la postulación, bloqueada: dos clics a la vez pasan de uno en uno y el
        // segundo renueva el anuncio del primero en vez de chocar contra «uno vivo».
        String estado = postulaciones.estadoBloqueandoLaFila(postulacion.getId());
        if (finales().contains(estado)) {
            throw new WhatsappNoDisponibleException(WhatsappNoDisponibleException.PROCESO_TERMINADO,
                    "Este proceso ya terminó: no hay novedades que contarte por WhatsApp");
        }
        Persona persona = personas.findById(quien.personaId())
                .orElseThrow(() -> new ResourceNotFoundException("Persona", "id", quien.personaId()));
        String telefono = persona.getTelefono();
        if (telefono == null) {
            throw new WhatsappNoDisponibleException(WhatsappNoDisponibleException.SIN_CELULAR,
                    "Tu cuenta no tiene celular: escríbelo para recibir tus novedades por WhatsApp");
        }
        if (bajas.existsById(telefono)) {
            throw new WhatsappNoDisponibleException(WhatsappNoDisponibleException.SUPRIMIDO,
                    "Este número se dio de baja de nuestros mensajes");
        }
        Vacante vacante = laVacanteDe(postulacion);
        String enlace = TextosDeWhatsapp.enlace(propiedades.getNumeroBot(), persona.getNombre(),
                persona.getApellidos(), vacante == null ? null : vacante.getTitulo());
        Instant ahora = Instant.now();

        VinculoWhatsapp vivo = vinculos.vivoParaEscribir(postulacion.getId()).orElse(null);
        if (vivo != null && vivo.estaActivo() && telefono.equals(vivo.getTelefono())) {
            // Ya escribió: no hay nada que anunciar, y el enlace sigue sirviendo para escribir.
            return new Preparado(enlace, new WhatsappDelProceso(true, VinculoWhatsapp.ACTIVO,
                    Celular.enmascarado(telefono)), null);
        }
        if (vivo != null && !telefono.equals(vivo.getTelefono())) {
            // Era de otro número (no debería quedar ninguno: cambiar el celular los cierra).
            vivo.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_CAMBIO_DE_CELULAR, ahora);
            vinculos.saveAndFlush(vivo);
            avisos.omitirPendientesDe(List.of(vivo.getId()), ahora);
            vivo = null;
        }

        Consentimiento firma = consentimientoDe(persona, postulacion, ip, userAgent, ahora);
        if (vivo == null) {
            vivo = VinculoWhatsapp.builder()
                    .postulacionId(postulacion.getId())
                    .personaId(persona.getId())
                    .referencia(nuevaReferencia())
                    .telefono(telefono)
                    .estado(VinculoWhatsapp.PENDIENTE)
                    .creadoEn(ahora)
                    .build();
        }
        // Nuevo o renovado: el anuncio vuelve a durar 24 horas desde ahora.
        vivo.setConsentimientoId(firma.getId());
        vivo.setAnunciadoEn(ahora);
        vivo.setAnuncioCaducaEn(ahora.plus(Duration.ofHours(Math.max(1, propiedades.getHorasDelAnuncio()))));
        vivo.setAnuncioConfirmadoEn(null);
        vivo.setAnuncioIntentos(0);
        vivo.setAnuncioProximoIntentoEn(ahora.plus(RESPALDO_DEL_ANUNCIO));
        vivo = vinculos.saveAndFlush(vivo);
        return new Preparado(enlace, new WhatsappDelProceso(true, VinculoWhatsapp.PENDIENTE,
                Celular.paraMostrar(telefono)), vivo.getId());
    }

    /**
     * Su firma del texto WHATSAPP vigente para esta postulación: la que ya tenía o una nueva. Una
     * por postulación y versión: el doble clic no firma dos veces.
     */
    private Consentimiento consentimientoDe(Persona persona, Postulacion postulacion, String ip,
                                            String userAgent, Instant ahora) {
        TextoConsentimiento texto = textos.exigirVigente(duenos.plataforma().getId(),
                TipoConsentimiento.WHATSAPP);
        return consentimientos.findFirstByPersonaIdAndPostulacionIdAndTextoConsentimientoId(
                        persona.getId(), postulacion.getId(), texto.getId())
                .orElseGet(() -> consentimientos.save(Consentimiento.builder()
                        .personaId(persona.getId())
                        .textoConsentimientoId(texto.getId())
                        .postulacionId(postulacion.getId())
                        .nombreRegistrado(nombreCompleto(persona))
                        .aceptadoEn(ahora)
                        .ip(ip)
                        .userAgent(userAgent)
                        .creadoEn(ahora)
                        .build()));
    }

    // ============ Dejar de recibir ============

    /** Lo que cerró «Dejar de recibir»: la postulación y el vínculo que hay que revocar, si había. */
    private record Cerrado(Postulacion postulacion, Long vinculo) {}

    @Override
    public WhatsappDelProceso dejarDeRecibir(ContextoUsuario quien, UUID uuid) {
        Cerrado hecho = enUnaTransaccion.execute(t -> {
            Postulacion suya = laMia(quien, uuid);
            VinculoWhatsapp vivo = vinculos.vivoParaEscribir(suya.getId()).orElse(null);
            if (vivo == null) {
                return new Cerrado(suya, null);
            }
            Instant ahora = Instant.now();
            vivo.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_DEJAR_DE_RECIBIR, ahora);
            vinculos.save(vivo);
            avisos.omitirPendientesDe(List.of(vivo.getId()), ahora);
            return new Cerrado(suya, vivo.getId());
        });
        Objects.requireNonNull(hecho, "Dejar de recibir no encontró la postulación");
        if (hecho.vinculo() != null) {
            envio.revocarAhora(hecho.vinculo());
        }
        return tarjetasDe(quien.personaId(), List.of(hecho.postulacion()))
                .getOrDefault(hecho.postulacion().getId(), WhatsappDelProceso.NO_DISPONIBLE);
    }

    // ============ Otro celular ============

    @Override
    public void alCambiarElCelular(Long personaId, String anterior, String nuevo) {
        if (Objects.equals(anterior, nuevo)) {
            return;
        }
        List<VinculoWhatsapp> vivos = vinculos.vivosDeLaPersonaParaEscribir(personaId);
        if (vivos.isEmpty()) {
            return;
        }
        Instant ahora = Instant.now();
        for (VinculoWhatsapp vivo : vivos) {
            vivo.cerrar(VinculoWhatsapp.REVOCADO, VinculoWhatsapp.POR_CAMBIO_DE_CELULAR, ahora);
        }
        vinculos.saveAll(vivos);
        List<Long> ids = vivos.stream().map(VinculoWhatsapp::getId).toList();
        avisos.omitirPendientesDe(ids, ahora);
        // Al bot, en cuanto el cambio quede guardado; si no responde, el sondeo reintenta.
        AvisoDeEtapaEnLaCampana.alConfirmar(() -> ids.forEach(envio::revocarAhora),
                "WhatsApp: revocar el celular anterior", null);
    }

    // ============ ayudas ============

    /** La postulación de quien pregunta, con su vacante en pie; si no, el mismo 404. */
    private Postulacion laMia(ContextoUsuario quien, UUID uuid) {
        Postulacion mia = postulaciones.findByUuid(uuid)
                .filter(p -> p.getUsuarioId().equals(quien.usuarioId()))
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "código", uuid));
        VacanteEliminada.exigirQueSuProcesoSigaExistiendo(mia, vacantes);
        return mia;
    }

    /** Su vacante, para el título del mensaje. La postulación ya se comprobó suya en {@code laMia}. */
    private Vacante laVacanteDe(Postulacion suya) {
        return suya.getVacanteId() == null ? null : vacantes.findById(suya.getVacanteId()).orElse(null);
    }

    private Set<String> finales() {
        return estados.findAllByOrderByOrden().stream()
                .filter(EstadoPostulacion::isEsFinal)
                .map(EstadoPostulacion::getCodigo)
                .collect(Collectors.toSet());
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
