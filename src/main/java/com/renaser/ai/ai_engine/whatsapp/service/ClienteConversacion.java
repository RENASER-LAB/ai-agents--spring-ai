package com.renaser.ai.ai_engine.whatsapp.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que EX le pide al bot de WhatsApp: las tres rutas {@code /v1/ex/…} del servicio de
 * conversación (V73). Ver docs/CONTRATO-NOVEDADES-POR-WHATSAPP.md.
 *
 * <p>Tres modos, según {@code app.whatsapp.modo}: apagado (no llama a nadie), falso (un bot en
 * memoria con su registro, para desarrollo y E2E) y real (HTTPS con firma).
 *
 * <p>Si el bot no contesta como debe, lanza {@link FalloDelBot}, que dice si vale la pena
 * reintentar.
 */
public interface ClienteConversacion {

    /** {@code POST /v1/ex/esperas}: ese número va a escribir por esa postulación. Repetido, renueva. */
    void anunciar(Espera espera);

    /** {@code POST /v1/ex/avisos}: un «te toca». El {@code avisoId} es la clave de idempotencia. */
    void avisar(Aviso aviso);

    /** {@code POST /v1/ex/revocaciones}: ese vínculo (y su anuncio, si lo había) se acabó. */
    void revocar(Revocacion revocacion);

    /** Falso en el modo apagado: no hay a quién anunciar, así que el portal no ofrece el botón. */
    boolean estaEncendido();

    record Espera(String telefono, String postulacionRef, Instant caducaEn) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("telefono", telefono);
            cuerpo.put("postulacionRef", postulacionRef);
            cuerpo.put("caducaEn", caducaEn.toString());
            return cuerpo;
        }
    }

    record Aviso(UUID avisoId, String telefono, String postulacionRef, String tipo,
                 String nombre, String vacante, String texto) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("avisoId", avisoId.toString());
            cuerpo.put("telefono", telefono);
            cuerpo.put("postulacionRef", postulacionRef);
            cuerpo.put("tipo", tipo);
            cuerpo.put("nombre", nombre);
            cuerpo.put("vacante", vacante);
            cuerpo.put("texto", texto);
            return cuerpo;
        }
    }

    record Revocacion(String telefono, String postulacionRef, String motivo) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("telefono", telefono);
            cuerpo.put("postulacionRef", postulacionRef);
            cuerpo.put("motivo", motivo);
            return cuerpo;
        }
    }

    /**
     * El bot no hizo lo que se le pidió.
     *
     * <p>{@code reintentable}: no contestó, tardó más de la cuenta, o contestó 5xx o 429. Lo
     * contrario —un 4xx— es definitivo: repetir la misma petición dará lo mismo (un 409 de un
     * aviso con otro contenido, una firma que no cuadra).
     */
    class FalloDelBot extends RuntimeException {
        private final boolean reintentable;

        public FalloDelBot(String mensaje, boolean reintentable) {
            super(mensaje);
            this.reintentable = reintentable;
        }

        public FalloDelBot(String mensaje, boolean reintentable, Throwable causa) {
            super(mensaje, causa);
            this.reintentable = reintentable;
        }

        public boolean esReintentable() {
            return reintentable;
        }
    }
}
