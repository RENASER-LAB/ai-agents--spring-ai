package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Un bot de mentira, en memoria (modo {@code falso}): para desarrollo, las pruebas de
 * integración y los E2E.
 *
 * <p>Contesta como el de verdad según el contrato: valida lo que recibe ({@code 400 validacion},
 * definitivo, si a una espera le falta el consentimiento o una revocación trae un motivo que no
 * existe) y reconoce los avisos por su {@code avisoId} (repetido con otro contenido,
 * {@code 409 aviso_distinto}). Así una prueba que pasa contra él pasa contra el contrato.
 *
 * <p>Apunta cada llamada que acepta —esperas con su consentimiento, avisos y revocaciones— con
 * el mismo cuerpo que llevaría al bot de verdad, y las enseña por
 * {@code GET /api/v1/integraciones/whatsapp/falso/registro} (firmada como las demás). No manda
 * nada a nadie: es lo que permite comprobar en un E2E que llegó un solo aviso, o que cambiar el
 * celular revocó el número anterior.
 *
 * <p>{@link #fallar(boolean)} lo pone a contestar como un bot caído, para probar los reintentos.
 */
@Slf4j
public class ClienteConversacionFalso implements ClienteConversacion {

    public static final String ESPERA = "ESPERA";
    public static final String AVISO = "AVISO";
    public static final String REVOCACION = "REVOCACION";

    /** Lo que se le pidió al bot, en el orden en que llegó. */
    public record Llamada(String tipo, Instant recibidaEn, Map<String, Object> cuerpo) {}

    /** Tope de memoria: un servidor de desarrollo encendido días no crece sin fin. */
    private static final int MAXIMO = 2000;

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final int LARGO_DE_LA_REFERENCIA = 64;
    private static final int LARGO_DEL_NOMBRE = 60;
    private static final int LARGO_DE_LA_VACANTE = 60;
    private static final int LARGO_DEL_TEXTO = 1000;

    private final List<Llamada> registro = new CopyOnWriteArrayList<>();
    /** El contenido de cada aviso aceptado, por su avisoId: la idempotencia del bot. */
    private final Map<String, Map<String, Object>> avisosAceptados = new ConcurrentHashMap<>();
    private volatile boolean caido;

    @Override
    public void anunciar(Espera espera) {
        caidoSiToca();
        comun(espera.telefono(), espera.postulacionRef());
        exigir(espera.caducaEn() != null, "caducaEn");
        ConsentimientoDeLaEspera consentimiento = espera.consentimiento();
        exigir(consentimiento != null, "consentimiento");
        exigir("WHATSAPP".equals(consentimiento.tipo()), "consentimiento.tipo");
        exigir(lleno(consentimiento.version()), "consentimiento.version");
        exigir(lleno(consentimiento.texto()), "consentimiento.texto");
        exigir(consentimiento.aceptadoEn() != null, "consentimiento.aceptadoEn");
        apuntar(ESPERA, espera.comoCuerpo());
    }

    @Override
    public void avisar(Aviso aviso) {
        caidoSiToca();
        comun(aviso.telefono(), aviso.postulacionRef());
        exigir(aviso.avisoId() != null, "avisoId");
        exigir(TIPOS_DE_AVISO.contains(aviso.tipo()), "tipo");
        exigir(cabe(aviso.nombre(), LARGO_DEL_NOMBRE), "nombre");
        exigir(cabe(aviso.vacante(), LARGO_DE_LA_VACANTE), "vacante");
        exigir(lleno(aviso.texto()) && aviso.texto().length() <= LARGO_DEL_TEXTO, "texto");
        Map<String, Object> cuerpo = aviso.comoCuerpo();
        if (avisosAceptados.size() >= MAXIMO) {
            avisosAceptados.clear();
        }
        Map<String, Object> anterior = avisosAceptados.putIfAbsent(aviso.avisoId().toString(), cuerpo);
        if (anterior != null && !anterior.equals(cuerpo)) {
            throw new FalloDelBot("El bot falso respondió 409 aviso_distinto: el aviso "
                    + aviso.avisoId() + " ya llegó con otro contenido", false, "aviso_distinto");
        }
        apuntar(AVISO, cuerpo);
    }

    @Override
    public void revocar(Revocacion revocacion) {
        caidoSiToca();
        comun(revocacion.telefono(), revocacion.postulacionRef());
        exigir(MOTIVOS_DE_REVOCACION.contains(revocacion.motivo()), "motivo");
        apuntar(REVOCACION, revocacion.comoCuerpo());
    }

    @Override
    public boolean estaEncendido() {
        return true;
    }

    /** Las llamadas recibidas, las más viejas primero. */
    public List<Llamada> registro() {
        return new ArrayList<>(registro);
    }

    /** Las de un número: en un E2E cada prueba mira solo lo suyo. */
    public List<Llamada> registroDe(String telefono) {
        return registro.stream()
                .filter(l -> telefono.equals(l.cuerpo().get("telefono")))
                .toList();
    }

    /** Para las pruebas: que conteste como si estuviera caído (reintentable) o que vuelva. */
    public void fallar(boolean estaCaido) {
        this.caido = estaCaido;
    }

    public void olvidar() {
        registro.clear();
        avisosAceptados.clear();
    }

    // ============ como el bot de verdad ============

    private void caidoSiToca() {
        if (caido) {
            throw new FalloDelBot("El bot falso está caído", true);
        }
    }

    private static void comun(String telefono, String postulacionRef) {
        exigir(telefono != null && E164.matcher(telefono).matches(), "telefono");
        exigir(lleno(postulacionRef) && postulacionRef.length() <= LARGO_DE_LA_REFERENCIA,
                "postulacionRef");
    }

    /** Lo que el bot contesta a un cuerpo que no cumple el contrato: 400 validacion, definitivo. */
    private static void exigir(boolean cumple, String campo) {
        if (!cumple) {
            throw new FalloDelBot("El bot falso respondió 400 validacion: " + campo
                    + " no cumple el contrato", false, "validacion");
        }
    }

    private static boolean lleno(String texto) {
        return texto != null && !texto.isBlank();
    }

    private static boolean cabe(String texto, int largo) {
        return texto != null && texto.length() <= largo;
    }

    private void apuntar(String tipo, Map<String, Object> cuerpo) {
        if (registro.size() >= MAXIMO) {
            registro.remove(0);
        }
        registro.add(new Llamada(tipo, Instant.now(), Collections.unmodifiableMap(new LinkedHashMap<>(cuerpo))));
        log.info("Bot falso de WhatsApp: {} {}", tipo, cuerpo.get("postulacionRef"));
    }
}
