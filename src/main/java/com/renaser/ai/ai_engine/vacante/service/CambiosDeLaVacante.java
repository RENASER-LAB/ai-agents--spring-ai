package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.GuardarVacante;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Qué cambió al guardar una vacante, y cómo se le cuenta a quien está postulando.
 *
 * <p><b>Por qué existe, y por qué no vive dentro del servicio.</b> Guardar una vacante
 * publicada no es escribir unos campos: es decidir si a cada persona en carrera le llega una
 * noticia y qué dice. Esa decisión se toma comparando lo que había con lo que llega, campo a
 * campo, y tiene tres respuestas distintas según el campo:
 *
 * <ul>
 *   <li><b>Visibles cortos</b> —título, modalidad, horario, ciudad, zona o referencia y
 *       remuneración—: caben en una línea, así que el aviso dice el antes y el ahora. Es la
 *       información completa.
 *   <li><b>Visibles largos</b> —descripción, propósito, responsabilidades y requisitos—: solo
 *       se nombran. Pegar dos párrafos enteros en una campana no informa, tapa; quien quiera
 *       leerlos entra a la vacante, que es a donde lleva el aviso.
 *   <li><b>Internos</b> —responsable, forma de cierre, plazas y fechas—: se guardan y se
 *       auditan, y no se avisan. Al candidato no le dicen nada: son cómo se organiza la
 *       empresa por dentro, no lo que él aceptó al postular.
 * </ul>
 *
 * <p><b>Lo que el formulario no ofrece, no se toca.</b> El cuerpo del PUT es el formulario
 * de edición, y ese formulario no enseña la fecha de apertura ni —cuando la forma de cierre
 * no las usa— las plazas o la fecha de cierre. Un campo ausente ahí significa «no te lo he
 * enseñado», no «bórralo»: leerlo como un nulo hacía que abrir el lápiz y pulsar guardar sin
 * tocar nada vaciara en silencio datos que nadie vio. Por eso esta clase no compara la
 * apertura y calcula las otras dos con la regla de abajo — y el servicio escribe justo lo
 * que ella dice, para que lo comparado y lo guardado no puedan separarse.
 *
 * <p><b>Cambiar la forma de cierre SÍ limpia lo que sobra.</b> Pasar de «por plazas» a «por
 * fecha» deja unas plazas que ya no rigen nada, y eso es una decisión que alguien tomó en la
 * pantalla: se limpia y se audita como cambio interno. Lo que no se admite es limpiarlas sin
 * que nadie lo haya pedido.
 *
 * <p><b>Los espacios del principio y del final no cuentan como un cambio.</b> El panel manda
 * el formulario entero cada vez y un salto de línea de más al copiar y pegar no puede
 * escribirle a cuarenta personas. Es también lo que hace que reenviar la misma edición —por
 * un doble clic, por un reintento— no vuelva a auditar ni a avisar: la segunda vez no hay
 * nada que comparar.
 *
 * <p>Es una clase sin estado compartido y sin dependencias: no conoce repositorios ni
 * usuarios, solo dos versiones de la misma vacante. Por eso se puede probar sin levantar
 * nada.
 */
public final class CambiosDeLaVacante {

    /** Qué hace el sistema con este campo cuando cambia. */
    public enum Clase {
        /** Se avisa con el antes y el ahora. */
        VISIBLE_CORTO,
        /** Se avisa nombrándolo, sin pegar el texto. */
        VISIBLE_LARGO,
        /** Se guarda y se audita, sin avisar a nadie. */
        INTERNO
    }

    /**
     * Un campo que cambió.
     *
     * @param campo    su nombre en la auditoría
     * @param etiqueta cómo se llama en el aviso («Horario»), o cómo se nombra en la frase de
     *                 los largos («la descripción»)
     */
    public record Cambio(String campo, String etiqueta, String antes, String ahora,
                         Clase clase) {}

    /** Lo que cierra todo aviso de edición: que no hay nada que hacer. */
    public static final String CIERRE =
            "Tu postulación sigue su curso y no tienes que hacer nada.";

    /** El campo de la remuneración, que llega ya escrito en una frase por {@link Remuneracion}. */
    public static final String REMUNERACION = "remuneracion";

    /**
     * El campo de la ciudad (V62). Se compara por su código —dos ciudades con el mismo nombre
     * son dos ciudades— y se cuenta por su nombre, que es lo que el candidato entiende.
     */
    public static final String CIUDAD = "ciudadUbigeo";

    /** Las dos formas de cierre que traen un dato detrás. La tercera, PERMANENTE, no. */
    private static final String CIERRE_POR_PLAZAS = "PLAZAS";
    private static final String CIERRE_POR_FECHA = "FECHA";

    private static final String SIN_INDICAR = "sin indicar";

    /**
     * Cómo se escribe la ciudad cuando no había: «Ciudad: — → Arequipa».
     *
     * <p>Un guion y no «sin indicar», al revés que los campos de texto, porque así lo fija la
     * spec de la ciudad (V62) y es lo que sus pruebas buscan; los de texto conservan lo que
     * ya decían, que es lo que buscan las suyas. Ninguno de los dos deja un hueco.
     */
    private static final String SIN_CIUDAD = "—";

    private final List<Cambio> cambios;
    private final Integer plazas;
    private final Instant cierraEn;

    private CambiosDeLaVacante(List<Cambio> cambios, Integer plazas, Instant cierraEn) {
        this.cambios = List.copyOf(cambios);
        this.plazas = plazas;
        this.cierraEn = cierraEn;
    }

    /**
     * Compara lo guardado con lo que llega del formulario.
     *
     * <p>La remuneración entra ya escrita («S/ 3 000 a 4 000», «sin publicar») y no como
     * cuatro campos sueltos: quien decide cómo se escribe el dinero es {@link Remuneracion},
     * y compararla aquí por sus partes obligaría a repetir ese formato —y a equivocarse en
     * él— en el único sitio donde el candidato lo va a leer.
     *
     * <p>La ciudad entra por las dos puntas (V62): el código va en la vacante y en el
     * formulario, y es lo que se compara; los nombres los pone el servicio, que es quien
     * tiene el catálogo, y son lo que el aviso escribe. Sin ciudad, el nombre llega vacío.
     */
    public static CambiosDeLaVacante entre(Vacante vacante, GuardarVacante datos,
                                           String sueldoAntes, String sueldoAhora,
                                           String ciudadAntes, String ciudadAhora) {
        List<Cambio> encontrados = new ArrayList<>();

        texto(encontrados, "titulo", "Título", vacante.getTitulo(), datos.titulo(),
                Clase.VISIBLE_CORTO);
        texto(encontrados, "modalidad", "Modalidad", vacante.getModalidad(), datos.modalidad(),
                Clase.VISIBLE_CORTO);
        texto(encontrados, "horario", "Horario", vacante.getHorario(), datos.horario(),
                Clase.VISIBLE_CORTO);
        if (!limpio(vacante.getCiudadUbigeo()).equals(limpio(datos.ciudadUbigeo()))) {
            encontrados.add(new Cambio(CIUDAD, "Ciudad", limpio(ciudadAntes),
                    limpio(ciudadAhora), Clase.VISIBLE_CORTO));
        }
        // «Zona o referencia» y no «Ubicación» desde la V62: el nombre de la ciudad ya no vive
        // aquí. El campo de la auditoría sigue siendo `ubicacion`, que es la columna.
        texto(encontrados, "ubicacion", "Zona o referencia", vacante.getUbicacion(),
                datos.ubicacion(), Clase.VISIBLE_CORTO);
        texto(encontrados, REMUNERACION, "Remuneración", sueldoAntes, sueldoAhora,
                Clase.VISIBLE_CORTO);

        texto(encontrados, "descripcion", "la descripción", vacante.getDescripcion(),
                datos.descripcion(), Clase.VISIBLE_LARGO);
        texto(encontrados, "proposito", "el propósito", vacante.getProposito(),
                datos.proposito(), Clase.VISIBLE_LARGO);
        texto(encontrados, "responsabilidades", "las responsabilidades",
                vacante.getResponsabilidades(), datos.responsabilidades(), Clase.VISIBLE_LARGO);
        texto(encontrados, "requisitos", "los requisitos", vacante.getRequisitos(),
                datos.requisitos(), Clase.VISIBLE_LARGO);

        texto(encontrados, "tipoCierre", "Forma de cierre", vacante.getTipoCierre(),
                datos.tipoCierre(), Clase.INTERNO);
        valor(encontrados, "responsableUsuarioId", "Responsable",
                vacante.getResponsableUsuarioId(), datos.responsableUsuarioId());

        // Las dos que dependen de la forma de cierre: se comparan contra lo que de verdad se
        // va a guardar, no contra lo que vino en el cuerpo.
        Integer plazas = plazasQueQuedarian(vacante, datos);
        Instant cierre = cierreQueQuedaria(vacante, datos);
        valor(encontrados, "plazas", "Plazas", vacante.getPlazas(), plazas);
        valor(encontrados, "cierraEn", "Fecha de cierre", vacante.getCierraEn(), cierre);

        // ⚠️ La fecha de APERTURA no se compara, y no es un olvido: el formulario de edición
        // no la enseña, así que el cuerpo nunca la trae y compararla haría que cada guardado
        // «cambiara» a nulo algo que nadie tocó. Se define al crear la vacante.

        return new CambiosDeLaVacante(encontrados, plazas, cierre);
    }

    /**
     * Cuántas plazas quedan después de guardar.
     *
     * <ul>
     *   <li>La forma de cierre elegida es «por plazas»: manda lo que diga el formulario, que
     *       es quien las enseña — vaciarlas ahí es una decisión de quien edita.
     *   <li>La forma de cierre cambió: las plazas de antes ya no rigen nada y se limpian.
     *   <li>Ni una cosa ni la otra: el formulario no las enseñaba, así que se quedan como
     *       estaban. Es lo que impide que guardar sin tocar nada las borre.
     * </ul>
     */
    private static Integer plazasQueQuedarian(Vacante vacante, GuardarVacante datos) {
        if (CIERRE_POR_PLAZAS.equals(limpio(datos.tipoCierre()))) {
            return datos.plazas();
        }
        return cambioLaFormaDeCierre(vacante, datos) ? null : vacante.getPlazas();
    }

    /**
     * Lo mismo para la fecha de cierre, que solo enseña la forma «por fecha».
     *
     * <p><b>Y con la precisión que el formulario enseña, que es el DÍA.</b> La pantalla
     * pinta «2026-12-01» y devuelve la medianoche UTC de ese día, así que una vacante que
     * cerraba a las 18:30 volvía guardada a las 00:00 sin que nadie tocara el campo —con su
     * fila de auditoría y su «Cambios guardados»—, y el cierre por plazo vencido, que corre
     * solo, se le adelantaba esas horas.
     *
     * <p>Mientras el formulario ofrezca solo el día, <b>si el día no cambia, se conserva el
     * instante guardado</b>; cambiar el día sí escribe lo que llega. Comparar y escribir con
     * la misma regla es lo que impide que vuelva a haber un campo que se mueve solo.
     */
    private static Instant cierreQueQuedaria(Vacante vacante, GuardarVacante datos) {
        if (CIERRE_POR_FECHA.equals(limpio(datos.tipoCierre()))) {
            return elMismoDia(vacante.getCierraEn(), datos.cierraEn())
                    ? vacante.getCierraEn() : datos.cierraEn();
        }
        return cambioLaFormaDeCierre(vacante, datos) ? null : vacante.getCierraEn();
    }

    /**
     * Si dos instantes caen en el mismo día UTC.
     *
     * <p>UTC y no la hora de Lima porque es el día que el propio formulario enseña: recorta
     * los diez primeros caracteres del instante en UTC. Leerlo en otro huso haría que la
     * pantalla dijera un día y esta comparación pensara en otro.
     */
    private static boolean elMismoDia(Instant guardado, Instant queLlega) {
        return guardado != null && queLlega != null
                && guardado.truncatedTo(ChronoUnit.DAYS)
                        .equals(queLlega.truncatedTo(ChronoUnit.DAYS));
    }

    private static boolean cambioLaFormaDeCierre(Vacante vacante, GuardarVacante datos) {
        return !limpio(vacante.getTipoCierre()).equals(limpio(datos.tipoCierre()));
    }

    /** Las plazas que el servicio tiene que escribir. Ver {@link #plazasQueQuedarian}. */
    public Integer plazasQueQuedan() {
        return plazas;
    }

    /** La fecha de cierre que el servicio tiene que escribir. */
    public Instant cierreQueQueda() {
        return cierraEn;
    }

    private static void texto(List<Cambio> encontrados, String campo, String etiqueta,
                              String antes, String ahora, Clase clase) {
        String a = limpio(antes);
        String b = limpio(ahora);
        if (!a.equals(b)) {
            encontrados.add(new Cambio(campo, etiqueta, a, b, clase));
        }
    }

    private static void valor(List<Cambio> encontrados, String campo, String etiqueta,
                              Object antes, Object ahora) {
        if (!Objects.equals(antes, ahora)) {
            encontrados.add(new Cambio(campo, etiqueta, escribir(antes), escribir(ahora),
                    Clase.INTERNO));
        }
    }

    /** Sin espacios alrededor, y un campo en blanco es lo mismo que uno vacío. */
    public static String limpio(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String escribir(Object valor) {
        return switch (valor) {
            case null -> "";
            case Instant instante -> instante.toString();
            default -> String.valueOf(valor);
        };
    }

    // ---------- lo que el servicio pregunta ----------

    public boolean hayCambios() {
        return !cambios.isEmpty();
    }

    /** Si cambió algo que el candidato ve en la convocatoria. */
    public boolean hayVisibles() {
        return cambios.stream().anyMatch(c -> c.clase() != Clase.INTERNO);
    }

    public boolean cambioElSueldo() {
        return cambios.stream().anyMatch(c -> REMUNERACION.equals(c.campo()));
    }

    public List<Cambio> lista() {
        return cambios;
    }

    /** Lo que había, campo a campo, para la auditoría. */
    public Map<String, Object> anterior() {
        return mapa(true);
    }

    /** Lo que queda, campo a campo, para la auditoría. */
    public Map<String, Object> nuevo() {
        return mapa(false);
    }

    private Map<String, Object> mapa(boolean antes) {
        Map<String, Object> valores = new LinkedHashMap<>();
        for (Cambio cambio : cambios) {
            valores.put(cambio.campo(), antes ? cambio.antes() : cambio.ahora());
        }
        return valores;
    }

    // ---------- el aviso ----------

    /** El título del aviso, con el nombre que la vacante tiene AHORA. */
    public static String tituloDelAviso(String tituloActual) {
        return "Se actualizó la vacante «" + tituloActual + "»";
    }

    /**
     * Lo que dice el aviso: los cambios cortos con su antes y su ahora, los largos
     * nombrados, y el cierre de que no hay nada que hacer.
     */
    public String cuerpoDelAviso() {
        List<String> partes = new ArrayList<>();
        for (Cambio cambio : cambios) {
            if (cambio.clase() == Clase.VISIBLE_CORTO) {
                partes.add(cambio.etiqueta() + ": " + conNombre(cambio) + " → "
                        + conNombre(cambio.ahora(), cambio.campo()));
            }
        }
        List<String> largos = cambios.stream()
                .filter(c -> c.clase() == Clase.VISIBLE_LARGO)
                .map(Cambio::etiqueta)
                .toList();
        if (!largos.isEmpty()) {
            partes.add((largos.size() == 1 ? "Se actualizó " : "Se actualizaron ")
                    + enumerar(largos));
        }
        return partes.isEmpty() ? CIERRE : String.join(" · ", partes) + ". " + CIERRE;
    }

    /**
     * Un campo vacío se dice, no se deja en blanco.
     *
     * <p>«Ciudad:  → Arequipa» parece un fallo de la pantalla. «Ciudad: — → Arequipa» cuenta
     * lo que de verdad pasó: que antes no lo decía.
     */
    private static String conNombre(Cambio cambio) {
        return conNombre(cambio.antes(), cambio.campo());
    }

    private static String conNombre(String valor, String campo) {
        if (!valor.isBlank()) {
            return valor;
        }
        return CIUDAD.equals(campo) ? SIN_CIUDAD : SIN_INDICAR;
    }

    /** «la descripción y los requisitos», «el propósito, la descripción y los requisitos». */
    private static String enumerar(List<String> nombres) {
        if (nombres.size() == 1) {
            return nombres.get(0);
        }
        String todosMenosElUltimo = String.join(", ", nombres.subList(0, nombres.size() - 1));
        return todosMenosElUltimo + " y " + nombres.get(nombres.size() - 1);
    }
}
