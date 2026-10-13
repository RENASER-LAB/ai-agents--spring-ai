package com.renaser.ai.ai_engine.whatsapp.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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

    /**
     * {@code POST /v1/ex/esperas} → 204: ese número va a escribir por esa postulación, con el
     * consentimiento que acaba de dar. Repetido, renueva la caducidad y el consentimiento.
     */
    void anunciar(Espera espera);

    /**
     * {@code POST /v1/ex/avisos} → 202 {@code {avisoId}}: un «te toca». El {@code avisoId} es la
     * clave de idempotencia; repetido con otro contenido, {@code 409 aviso_distinto}.
     */
    void avisar(Aviso aviso);

    /**
     * {@code POST /v1/ex/revocaciones} → 204: ese vínculo (y su anuncio, si lo había) se acabó,
     * con uno de los motivos de {@link #MOTIVOS_DE_REVOCACION}.
     */
    void revocar(Revocacion revocacion);

    /** Falso en el modo apagado: no hay a quién anunciar, así que el portal no ofrece el botón. */
    boolean estaEncendido();

    /** Los motivos de una revocación que entiende el bot; ningún otro. */
    Set<String> MOTIVOS_DE_REVOCACION =
            Set.of("DEJAR_DE_RECIBIR", "CAMBIO_CELULAR", "PROCESO_TERMINADO", "BORRADO_DATOS");

    /** Los tipos de aviso que entiende el bot: los «te toca». */
    Set<String> TIPOS_DE_AVISO = Set.of("PRUEBA_DISPONIBLE", "POSTULACION_AVANZA");

    /**
     * El consentimiento que el candidato aceptó al pulsar el botón: tipo {@code WHATSAPP}, su
     * versión, el texto exacto que vio y cuándo.
     */
    record ConsentimientoDeLaEspera(String tipo, String version, String texto, Instant aceptadoEn) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("tipo", tipo);
            cuerpo.put("version", version);
            cuerpo.put("texto", texto);
            cuerpo.put("aceptadoEn", aceptadoEn.toString());
            return cuerpo;
        }
    }

    record Espera(String telefono, String postulacionRef, Instant caducaEn,
                  ConsentimientoDeLaEspera consentimiento) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("telefono", telefono);
            cuerpo.put("postulacionRef", postulacionRef);
            cuerpo.put("caducaEn", caducaEn.toString());
            cuerpo.put("consentimiento", consentimiento.comoCuerpo());
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
     * <p>{@code reintentable}: no contestó, tardó más de la cuenta, o contestó 5xx, 429 o 401 (un
     * 401 puede ser un cambio de secreto a medias). Cualquier otro 4xx es definitivo: repetir la
     * misma petición dará lo mismo ({@code 409 aviso_distinto}, {@code 400 validacion}).
     *
     * <p>{@code codigo}: el {@code code} con que el bot explica su error, en minúsculas
     * ({@code firma_invalida}, {@code validacion}, {@code aviso_distinto}…), o nulo si no hubo
     * respuesta o no lo traía.
     */
    class FalloDelBot extends RuntimeException {
        private final boolean reintentable;
        private final String codigo;

        public FalloDelBot(String mensaje, boolean reintentable) {
            this(mensaje, reintentable, (String) null);
        }

        public FalloDelBot(String mensaje, boolean reintentable, String codigo) {
            super(mensaje);
            this.reintentable = reintentable;
            this.codigo = codigo;
        }

        public FalloDelBot(String mensaje, boolean reintentable, Throwable causa) {
            super(mensaje, causa);
            this.reintentable = reintentable;
            this.codigo = null;
        }

        public boolean esReintentable() {
            return reintentable;
        }

        public String codigo() {
            return codigo;
        }

        /** Lo que dice el contrato de cada estado HTTP de error: 5xx, 429 y 401 se reintentan. */
        public static boolean seReintenta(int estadoHttp) {
            return estadoHttp >= 500 || estadoHttp == 429 || estadoHttp == 401;
        }
    }
}
