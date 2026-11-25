package com.renaser.ai.ai_engine.whatsapp.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que EX le pide al bot de WhatsApp: las tres rutas {@code /v1/ex/…} del servicio de
 * conversación (V73), y desde la V76 los eventos v2 de {@code /v1/ex/resultados}. Ver
 * docs/CONTRATO-NOVEDADES-POR-WHATSAPP.md y docs/CONTRATO-CONTEXTO-Y-RESULTADOS-POR-WHATSAPP-V2.md.
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

    /**
     * {@code POST /v1/ex/resultados} → 202 {@code {eventoId}} (contrato v2, C-4): un RESULTADO o
     * una CONVOCATORIA_CERRADA. El {@code eventoId} es la clave de idempotencia; repetido con otro
     * contenido, {@code 409 evento_distinto}. Un bot en v1 contesta {@code 404
     * ruta_no_disponible}, que es definitivo: no se reintenta en bucle.
     */
    void publicarEvento(Evento evento);

    /**
     * {@code POST /v1/ex/vinculaciones} → 200 {@code {postulaciones: [{postulacionRef}]}} (V92,
     * spec 034 del CRM): vincular sin mensaje las postulaciones de un número que ya habló con el
     * bot. Con {@link #MOTIVO_ACTIVACION} el bot levanta su BAJA si la tenía y le manda la
     * plantilla {@code avisos_activados}; con {@link #MOTIVO_NUEVA_POSTULACION} solo vincula.
     * Idempotente por teléfono y postulación.
     *
     * <p>{@code 404 SIN_CONVERSACION} (el número nunca habló con el bot) y {@code 409 SUPRIMIDO} llegan
     * en el {@code code} del error, como un {@link FalloDelBot} definitivo con ese código; ver
     * {@link FalloDelBot#esSinConversacion()} y {@link FalloDelBot#esSuprimido()}.
     *
     * @return las referencias que quedaron vinculadas; una que el bot tenía cerrada no vuelve
     */
    List<String> vincular(Vinculacion vinculacion);

    /** Falso en el modo apagado: no hay a quién anunciar, así que el portal no ofrece el botón. */
    boolean estaEncendido();

    /** Encender WhatsApp desde el portal: levanta la BAJA y confirma con la plantilla. */
    String MOTIVO_ACTIVACION = "ACTIVACION";

    /** Postular con WhatsApp ya activo: solo vincula, sin mensaje. */
    String MOTIVO_NUEVA_POSTULACION = "NUEVA_POSTULACION";

    /** Los motivos de una vinculación que entiende el bot. */
    Set<String> MOTIVOS_DE_VINCULACION = Set.of(MOTIVO_ACTIVACION, MOTIVO_NUEVA_POSTULACION);

    /** Los motivos de una revocación que entiende el bot; ningún otro. */
    Set<String> MOTIVOS_DE_REVOCACION =
            Set.of("DEJAR_DE_RECIBIR", "CAMBIO_CELULAR", "PROCESO_TERMINADO", "BORRADO_DATOS");

    /** Los tipos de aviso que entiende el bot: los «te toca». */
    Set<String> TIPOS_DE_AVISO = Set.of("PRUEBA_DISPONIBLE", "POSTULACION_AVANZA", "PRUEBA_MAS_TIEMPO");

    /** Los tipos de evento v2 (C-4). Nunca van por {@code /v1/ex/avisos}. */
    Set<String> TIPOS_DE_EVENTO = Set.of("RESULTADO", "CONVOCATORIA_CERRADA");

    /** Los resultados de una postulación terminada, como los nombra EX: no se traducen. */
    Set<String> TIPOS_DE_RESULTADO = Set.of("CONTRATADO", "NO_CONTINUA", "CERRADA");

    /** El resultado que viaja en un evento: el mismo bloque que en la consulta (C-3). */
    record ResultadoDelEvento(String tipo, Instant fecha, int revision, String explicacion,
                              boolean esCorreccion, Instant consultableHasta) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("tipo", tipo);
            cuerpo.put("fecha", fecha.toString());
            cuerpo.put("revision", revision);
            cuerpo.put("explicacion", explicacion);
            cuerpo.put("esCorreccion", esCorreccion);
            cuerpo.put("consultableHasta", consultableHasta.toString());
            return cuerpo;
        }
    }

    /** La convocatoria que cerró, con su revisión: el bot la compara aparte de la del resultado. */
    record ConvocatoriaDelEvento(Instant cerradaEn, int revision) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("cerradaEn", cerradaEn.toString());
            cuerpo.put("revision", revision);
            return cuerpo;
        }
    }

    /**
     * Un evento v2 (C-4). {@code resultado} va con {@code RESULTADO} y {@code convocatoria} con
     * {@code CONVOCATORIA_CERRADA}; el que no toca no viaja.
     */
    record Evento(UUID eventoId, String tipo, String telefono, String postulacionRef, String nombre,
                  String vacante, Instant ocurridoEn, ResultadoDelEvento resultado,
                  ConvocatoriaDelEvento convocatoria, boolean avisoAutorizado, Instant avisoCaducaEn,
                  boolean cierraVinculo) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("eventoId", eventoId.toString());
            cuerpo.put("tipo", tipo);
            cuerpo.put("telefono", telefono);
            cuerpo.put("postulacionRef", postulacionRef);
            cuerpo.put("nombre", nombre);
            cuerpo.put("vacante", vacante);
            cuerpo.put("ocurridoEn", ocurridoEn.toString());
            if (resultado != null) {
                cuerpo.put("resultado", resultado.comoCuerpo());
            }
            if (convocatoria != null) {
                cuerpo.put("convocatoria", convocatoria.comoCuerpo());
            }
            Map<String, Object> aviso = new LinkedHashMap<>();
            aviso.put("autorizado", avisoAutorizado);
            aviso.put("caducaEn", avisoCaducaEn.toString());
            cuerpo.put("aviso", aviso);
            cuerpo.put("cierraVinculo", cierraVinculo);
            return cuerpo;
        }
    }

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

    /** Una postulación que se vincula: su referencia opaca y el título de la vacante (hasta 60). */
    record PostulacionAVincular(String postulacionRef, String vacante) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("postulacionRef", postulacionRef);
            cuerpo.put("vacante", vacante);
            return cuerpo;
        }
    }

    /**
     * El consentimiento que firmó en el portal, tal como lo guarda el bot junto a la autorización:
     * el texto exacto, su versión y cuándo. Sin tipo: el contrato de la spec 034 no lo lleva.
     */
    record ConsentimientoDeLaVinculacion(String texto, String version, Instant aceptadoEn) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("texto", texto);
            cuerpo.put("version", version);
            cuerpo.put("aceptadoEn", aceptadoEn.toString());
            return cuerpo;
        }
    }

    /** El cuerpo de {@code /v1/ex/vinculaciones}, con los campos del contrato y ninguno más. */
    record Vinculacion(String telefono, String nombre, String motivo,
                       List<PostulacionAVincular> postulaciones,
                       ConsentimientoDeLaVinculacion consentimiento) {
        public Map<String, Object> comoCuerpo() {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("telefono", telefono);
            cuerpo.put("nombre", nombre);
            cuerpo.put("motivo", motivo);
            cuerpo.put("postulaciones", postulaciones.stream().map(PostulacionAVincular::comoCuerpo).toList());
            cuerpo.put("consentimiento", consentimiento.comoCuerpo());
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

        /**
         * {@code 404 SIN_CONVERSACION} de {@code /v1/ex/vinculaciones}: el número nunca habló con
         * el bot (V92). El portal cae entonces al camino con mensaje.
         */
        public boolean esSinConversacion() {
            return SIN_CONVERSACION.equalsIgnoreCase(codigo);
        }

        /**
         * {@code 409 SUPRIMIDO} de {@code /v1/ex/vinculaciones} con {@code NUEVA_POSTULACION}: el
         * número está dado de baja en el bot y no se vincula (V92).
         */
        public boolean esSuprimido() {
            return SUPRIMIDO.equalsIgnoreCase(codigo);
        }

        /** El {@code code} con que el bot dice que ese número nunca le escribió. */
        public static final String SIN_CONVERSACION = "SIN_CONVERSACION";

        /** El {@code code} con que el bot dice que ese número está dado de baja. */
        public static final String SUPRIMIDO = "SUPRIMIDO";

        /** Lo que dice el contrato de cada estado HTTP de error: 5xx, 429 y 401 se reintentan. */
        public static boolean seReintenta(int estadoHttp) {
            return estadoHttp >= 500 || estadoHttp == 429 || estadoHttp == 401;
        }
    }
}
