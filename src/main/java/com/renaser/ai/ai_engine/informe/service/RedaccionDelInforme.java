package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Anexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Evaluado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Resumen;
import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.hay;

/**
 * Lo que recibe el redactor del informe, cómo se comprueba lo que devuelve y cómo se pone en el
 * informe. Sin base de datos ni IA: todo aquí es una cuenta que se puede probar sola.
 *
 * <p><b>Lo comprueba el código, no la IA</b>, antes de guardar nada. Una línea se descarta, y en
 * su lugar se usa la explicación de la IA, cuando:
 * <ul>
 *   <li>falta o viene en blanco;
 *   <li>trae «…» (nada se recorta);
 *   <li>trae una cifra que no está en los datos que recibió (nunca inventa cifras);
 *   <li>trae un nombre propio que no está en esos datos;
 *   <li>repite la nota («88 de 100», «nota de 88»): ya se ve junto a la barra;
 *   <li>trae la marca {@code [DATO NO UTILIZABLE]} del anonimizador: habla de algo que no vio.
 * </ul>
 * Las claves de {@code criterios} tienen que ser los nombres reales, y {@code preguntas} tiene
 * que traer tantas líneas como respuestas se le mandaron: si no, se descartan todas, porque no
 * se sabe cuál es de cuál.
 */
public final class RedaccionDelInforme {

    private RedaccionDelInforme() {
    }

    // ============================================================ lo que recibe

    public record CriterioInsumo(String nombre, String nota, String maximo, String explicacion) {
    }

    /** El texto del candidato, envuelto: es contenido de otro, nunca una instrucción. */
    public record ContenidoDelCandidato(String contenidoDelCandidato) {
    }

    public record RespuestaInsumo(int numero, String tema, ContenidoDelCandidato respuesta,
                                  String explicacion) {
    }

    public record PuntoInsumo(String bloque, String texto, String detalle) {
    }

    public record Insumo(String etapa, String vacante, String notaDeLaEtapa,
                         List<CriterioInsumo> criterios, List<RespuestaInsumo> respuestas,
                         List<PuntoInsumo> hallazgos, List<String> experiencia) {
    }

    /** Lo que devuelve el modelo, tal cual. */
    public record Respuesta(String resumen, Map<String, String> criterios, List<String> preguntas) {
    }

    /** Lo que se guarda: solo lo que pasó la comprobación. */
    public record Textos(String resumen, Map<String, String> criterios, List<String> preguntas,
                         int descartados) {
    }

    /** Los datos de la etapa que se le mandan, sacados del propio informe: un solo armado. */
    public static Insumo insumo(Informe base) {
        List<CriterioInsumo> criterios = new ArrayList<>();
        if (base.evaluado() != null) {
            for (Criterio c : base.evaluado().criterios()) {
                criterios.add(new CriterioInsumo(c.nombre(), c.notaTexto(),
                        c.maximo() == null ? null : FormatoDelInforme.nota(c.maximo()),
                        c.explicacion()));
            }
        }
        List<RespuestaInsumo> respuestas = new ArrayList<>();
        if (base.anexo() != null) {
            for (PreguntaAnexo p : base.anexo().preguntas()) {
                respuestas.add(new RespuestaInsumo(p.numero(), p.enunciado(),
                        new ContenidoDelCandidato(p.respuesta() == null ? "" : p.respuesta()),
                        p.explicacion()));
            }
        }
        List<PuntoInsumo> hallazgos = new ArrayList<>();
        for (Bloque b : base.bloques()) {
            // «Qué buscar en el CV» no viaja: son las líneas privadas de quien recluta, y de los
            // agentes solo las ve BUSQUEDA_CV (AC-05 de «Qué buscar en el CV»). El bloque sale
            // igual en el informe, armado con lo guardado; el redactor no escribe nada sobre él.
            if (ArmadoDelCv.BLOQUE_LO_BUSCADO.equals(b.codigo())) {
                continue;
            }
            for (Punto p : b.puntos()) {
                hallazgos.add(new PuntoInsumo(b.titulo(), p.texto(), p.detalle()));
            }
        }
        List<String> experiencia = new ArrayList<>();
        if (base.experiencia() != null) {
            var e = base.experiencia();
            experiencia.add("Experiencia total: " + e.total());
            experiencia.add("Último puesto: " + e.ultimoPuesto()
                    + (e.ultimoDetalle() == null ? "" : " (" + e.ultimoDetalle() + ")"));
            experiencia.add("Formación máxima: " + e.formacion());
            if (!e.habilidades().isEmpty()) {
                experiencia.add("Habilidades: " + String.join(", ", e.habilidades()));
            }
            if (e.perfil() != null) {
                experiencia.add("Perfil: " + e.perfil());
            }
        }
        return new Insumo(base.etapa(), base.vacante(),
                base.nota() == null ? null : base.nota().texto(), criterios, respuestas, hallazgos,
                experiencia);
    }

    /** Si hay algo que redactar: sin criterios con explicación ni respuestas, no se paga nada. */
    public static boolean hayQueRedactar(Insumo insumo) {
        return insumo.criterios().stream().anyMatch(c -> hay(c.explicacion()))
                || !insumo.respuestas().isEmpty();
    }

    /** Un resumen estable de los datos: si cambia una nota o una explicación, cambia la huella. */
    public static String huella(Insumo insumo) {
        StringBuilder s = new StringBuilder();
        s.append(insumo.etapa()).append('|').append(insumo.notaDeLaEtapa()).append('|');
        for (CriterioInsumo c : insumo.criterios()) {
            s.append(c.nombre()).append('=').append(c.nota()).append('/').append(c.maximo())
                    .append(':').append(c.explicacion()).append('|');
        }
        for (RespuestaInsumo r : insumo.respuestas()) {
            s.append(r.numero()).append('=').append(r.tema()).append(':')
                    .append(r.respuesta().contenidoDelCandidato()).append(':').append(r.explicacion())
                    .append('|');
        }
        for (PuntoInsumo p : insumo.hallazgos()) {
            s.append(p.bloque()).append('=').append(p.texto()).append(':').append(p.detalle()).append('|');
        }
        insumo.experiencia().forEach(e -> s.append(e).append('|'));
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(s.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256", e);
        }
    }

    // ============================================================ la comprobación

    public static Textos validar(Insumo insumo, Respuesta respuesta) {
        String datos = textoDe(insumo);
        String datosEnMinusculas = datos.toLowerCase(Locale.ROOT);
        Set<String> cifras = cifras(datos, true);
        int descartados = 0;
        List<String> notasDeLaEtapa = insumo.notaDeLaEtapa() == null ? List.of()
                : List.of(insumo.notaDeLaEtapa());

        String resumen = null;
        if (respuesta != null && hay(respuesta.resumen())) {
            resumen = vale(respuesta.resumen(), cifras, datosEnMinusculas, notasDeLaEtapa, null)
                    ? respuesta.resumen().strip() : null;
            descartados += resumen == null ? 1 : 0;
        }

        Map<String, String> criterios = new LinkedHashMap<>();
        Map<String, String> propuestos = respuesta == null || respuesta.criterios() == null
                ? Map.of() : respuesta.criterios();
        for (CriterioInsumo c : insumo.criterios()) {
            String linea = buscar(propuestos, c.nombre());
            if (!hay(linea)) {
                continue;
            }
            List<String> notas = new ArrayList<>(notasDeLaEtapa);
            if (c.nota() != null) {
                notas.add(c.nota());
            }
            if (vale(linea, cifras, datosEnMinusculas, notas, c.maximo())) {
                criterios.put(c.nombre(), linea.strip());
            } else {
                descartados++;
            }
        }
        // Claves que no son ningún criterio real: no se usan.
        Set<String> reales = new HashSet<>();
        insumo.criterios().forEach(c -> reales.add(c.nombre().strip().toLowerCase(Locale.ROOT)));
        for (String clave : propuestos.keySet()) {
            if (clave == null || !reales.contains(clave.strip().toLowerCase(Locale.ROOT))) {
                descartados++;
            }
        }

        List<String> preguntas = new ArrayList<>();
        List<String> propuestas = respuesta == null || respuesta.preguntas() == null
                ? List.of() : respuesta.preguntas();
        if (propuestas.size() == insumo.respuestas().size()) {
            for (String linea : propuestas) {
                boolean buena = hay(linea) && vale(linea, cifras, datosEnMinusculas, notasDeLaEtapa, null);
                preguntas.add(buena ? linea.strip() : null);
                descartados += buena ? 0 : 1;
            }
        } else {
            descartados += propuestas.size();
            insumo.respuestas().forEach(r -> preguntas.add(null));
        }
        return new Textos(resumen, criterios, preguntas, descartados);
    }

    private static String buscar(Map<String, String> propuestos, String nombre) {
        String linea = propuestos.get(nombre);
        if (linea != null) {
            return linea;
        }
        for (Map.Entry<String, String> e : propuestos.entrySet()) {
            if (e.getKey() != null && e.getKey().strip().equalsIgnoreCase(nombre.strip())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Si una línea se puede usar tal cual. */
    static boolean vale(String linea, Set<String> cifrasDeLosDatos, String datosEnMinusculas,
                        List<String> notas, String maximo) {
        if (!hay(linea) || linea.contains("…") || linea.contains("...")) {
            return false;
        }
        // La marca del anonimizador: la línea habla de un dato que la IA no vio.
        if (AnonimizadorCv.traeLaMarca(linea)) {
            return false;
        }
        for (String cifra : cifras(linea)) {
            if (!cifrasDeLosDatos.contains(cifra)) {
                return false;
            }
        }
        if (repiteLaNota(linea, notas, maximo)) {
            return false;
        }
        return !traeUnNombreInventado(linea, datosEnMinusculas);
    }

    /**
     * Las cifras de un texto, sin separadores: «S/ 2.500», «2 500» y «2500» son la misma cifra, y
     * «3,5» se compara como «35». Se recorre a mano y no con una expresión regular: es lineal
     * siempre.
     */
    static Set<String> cifras(String texto) {
        return cifras(texto, false);
    }

    /**
     * Lo mismo, y en los datos se guardan además los trozos de una cifra con separadores: si la
     * respuesta dice «S/ 31 400», una línea que diga «400» tampoco inventa nada.
     */
    static Set<String> cifras(String texto, boolean conTrozos) {
        Set<String> salida = new HashSet<>();
        if (texto == null) {
            return salida;
        }
        StringBuilder actual = new StringBuilder();
        StringBuilder trozo = new StringBuilder();
        int n = texto.length();
        for (int i = 0; i < n; i++) {
            char c = texto.charAt(i);
            if (Character.isDigit(c)) {
                actual.append(c);
                trozo.append(c);
            } else if (!actual.isEmpty() && separaMiles(texto, i, c)) {
                // Separador dentro de la misma cifra.
                if (conTrozos) {
                    salida.add(trozo.toString());
                }
                trozo.setLength(0);
            } else if (!actual.isEmpty()) {
                salida.add(actual.toString());
                if (conTrozos) {
                    salida.add(trozo.toString());
                }
                actual.setLength(0);
                trozo.setLength(0);
            }
        }
        if (!actual.isEmpty()) {
            salida.add(actual.toString());
            if (conTrozos) {
                salida.add(trozo.toString());
            }
        }
        return salida;
    }

    /** Un punto o una coma entre dígitos, o un espacio seguido de exactamente tres dígitos. */
    private static boolean separaMiles(String texto, int i, char c) {
        int n = texto.length();
        if ((c == '.' || c == ',') && i + 1 < n && Character.isDigit(texto.charAt(i + 1))) {
            return true;
        }
        if (c == ' ' || c == '\u00A0' || c == '\u202F') {
            return tresDigitos(texto, i + 1);
        }
        return false;
    }

    private static boolean tresDigitos(String texto, int desde) {
        if (desde + 3 > texto.length()) {
            return false;
        }
        for (int k = desde; k < desde + 3; k++) {
            if (!Character.isDigit(texto.charAt(k))) {
                return false;
            }
        }
        return desde + 3 == texto.length() || !Character.isDigit(texto.charAt(desde + 3));
    }

    private static boolean repiteLaNota(String linea, List<String> notas, String maximo) {
        String l = linea.toLowerCase(Locale.ROOT);
        for (String nota : notas) {
            if (!hay(nota) || "—".equals(nota)) {
                continue;
            }
            String v = nota.toLowerCase(Locale.ROOT);
            List<String> formas = new ArrayList<>(List.of(v + "/", v + " /", v + " de 100", v + " sobre",
                    v + " puntos", "nota de " + v, "nota " + v, "puntaje de " + v, "puntaje " + v));
            if (hay(maximo)) {
                formas.add(v + " de " + maximo);
            }
            for (String forma : formas) {
                if (contieneComoCifra(l, forma)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Que la forma aparezca y que la cifra no sea el final de otra más larga («188 de 100»). */
    private static boolean contieneComoCifra(String texto, String forma) {
        int desde = 0;
        while (true) {
            int i = texto.indexOf(forma, desde);
            if (i < 0) {
                return false;
            }
            boolean empiezaLimpia = i == 0 || !Character.isDigit(texto.charAt(i - 1))
                    || !Character.isDigit(forma.charAt(0));
            if (empiezaLimpia) {
                return true;
            }
            desde = i + 1;
        }
    }

    /**
     * Una palabra con mayúscula que no abre frase y que no está en los datos: un nombre de persona,
     * de empresa o de lugar que la IA se inventó.
     */
    static boolean traeUnNombreInventado(String linea, String datosEnMinusculas) {
        boolean abreFrase = true;
        StringBuilder palabra = new StringBuilder();
        boolean palabraAbreFrase = true;
        for (int i = 0; i <= linea.length(); i++) {
            char c = i < linea.length() ? linea.charAt(i) : ' ';
            if (Character.isLetter(c)) {
                if (palabra.isEmpty()) {
                    palabraAbreFrase = abreFrase;
                }
                palabra.append(c);
                continue;
            }
            if (!palabra.isEmpty()) {
                String p = palabra.toString();
                if (!palabraAbreFrase && p.length() > 2 && Character.isUpperCase(p.charAt(0))
                        && !datosEnMinusculas.contains(p.toLowerCase(Locale.ROOT))) {
                    return true;
                }
                palabra.setLength(0);
                abreFrase = false;
            }
            if (c == '.' || c == '!' || c == '?' || c == ':' || c == ';' || c == '«' || c == '"'
                    || c == '(' || c == '¿' || c == '¡' || c == '“') {
                abreFrase = true;
            } else if (!Character.isWhitespace(c) && c != ',') {
                abreFrase = false;
            }
        }
        return false;
    }

    /** Todo lo que recibió el redactor, en un solo texto, para buscar cifras y nombres. */
    private static String textoDe(Insumo insumo) {
        StringBuilder s = new StringBuilder();
        s.append(insumo.vacante()).append('\n').append(insumo.notaDeLaEtapa()).append('\n');
        for (CriterioInsumo c : insumo.criterios()) {
            s.append(c.nombre()).append(' ').append(c.nota()).append(' ').append(c.maximo()).append(' ')
                    .append(c.explicacion()).append('\n');
        }
        for (RespuestaInsumo r : insumo.respuestas()) {
            s.append(r.numero()).append(' ').append(r.tema()).append(' ')
                    .append(r.respuesta().contenidoDelCandidato()).append(' ').append(r.explicacion())
                    .append('\n');
        }
        for (PuntoInsumo p : insumo.hallazgos()) {
            s.append(p.bloque()).append(' ').append(p.texto()).append(' ').append(p.detalle()).append('\n');
        }
        insumo.experiencia().forEach(e -> s.append(e).append('\n'));
        return s.toString();
    }

    // ============================================================ el informe con los textos

    /** El informe con los textos del redactor donde pasaron la comprobación. */
    public static Informe aplicar(Informe base, Textos textos) {
        Resumen resumen = base.resumen();
        if (textos != null && hay(textos.resumen())) {
            resumen = new Resumen(EtapaDelInforme.CV.name().equals(base.etapa())
                    ? "Lo que muestra su CV" : "En pocas líneas", textos.resumen());
        }
        Map<String, String> lineas = textos == null ? Map.of() : textos.criterios();
        Evaluado evaluado = base.evaluado();
        if (evaluado != null && !lineas.isEmpty()) {
            List<Criterio> criterios = evaluado.criterios().stream().map(c -> {
                String linea = lineas.get(c.nombre());
                return linea == null ? c : new Criterio(c.nombre(), c.nota(), c.maximo(), c.notaTexto(),
                        c.maximoTexto(), c.porcentaje(), c.quien(), c.ajustada(), c.motivoAjuste(), linea,
                        c.explicacion(), c.preguntas(), c.pendiente());
            }).toList();
            evaluado = new Evaluado(evaluado.titulo(), evaluado.escala(), criterios, evaluado.pie());
        }
        List<Bloque> bloques = base.bloques().stream().map(b -> new Bloque(b.codigo(), b.titulo(),
                b.tono(), b.vacio(), b.puntos().stream().map(p -> {
                    String linea = "CRITERIO".equals(p.origen()) && p.detalle() != null
                            ? lineas.get(p.texto()) : null;
                    return linea == null ? p : new Punto(p.texto(), linea, p.origen(), p.etiqueta(), p.grupo());
                }).toList())).toList();
        Anexo anexo = base.anexo();
        if (anexo != null && textos != null && textos.preguntas().size() == anexo.preguntas().size()) {
            List<PreguntaAnexo> preguntas = new ArrayList<>();
            for (int i = 0; i < anexo.preguntas().size(); i++) {
                PreguntaAnexo p = anexo.preguntas().get(i);
                String linea = textos.preguntas().get(i);
                preguntas.add(linea == null ? p : new PreguntaAnexo(p.numero(), p.enunciado(), p.meta(),
                        p.respuesta(), linea, p.explicacion()));
            }
            anexo = new Anexo(anexo.titulo(), anexo.subtitulo(), anexo.caso(), preguntas,
                    anexo.entregablesResumen(), anexo.entregables());
        }
        var detalle = EtapaDelInforme.PRUEBA.name().equals(base.etapa()) && evaluado != null
                ? ArmadoDeLaPrueba.detalleTrasLosTextos(evaluado.criterios()) : base.detalle();
        return new Informe(base.etapa(), base.titulo(), base.empresa(), base.vacante(),
                base.postulacionId(), base.candidato(), base.correo(), base.generadoEn(), base.nota(),
                base.grupo(), base.datosClave(), base.recuentos(), base.experiencia(), resumen, evaluado,
                bloques, base.enlaces(), base.detalleTitulo(), detalle, base.detallePie(), anexo,
                base.aviso(), base.textosEnCamino(), base.nombreArchivo());
    }
}
