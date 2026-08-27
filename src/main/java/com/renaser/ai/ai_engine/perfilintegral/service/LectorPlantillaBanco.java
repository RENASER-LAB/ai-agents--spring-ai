package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.BancoLeido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.ErrorDeImportacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaCampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaPar;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaRango;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lee la plantilla Excel del banco de preguntas y la convierte en filas planas.
 *
 * <p>Sin estado y sin base de datos: entra un archivo, sale {@link BancoLeido}. Todo lo
 * que esté mal se junta en la lista de errores con su hoja y su fila —a quien sube 190
 * preguntas le sirve la lista completa, no un rechazo por entrega— y si hay un solo
 * error, lo leído no se usa.
 *
 * <p>El mismo lector traga las dos variantes reales del archivo, que difieren en una
 * fila: la plantilla vacía (encabezados en la fila 4, ejemplos grises y una fila
 * centinela «⬇ Escribe aquí lo tuyo…») y los bancos volcados por nosotros (encabezados
 * en la 3, datos directos). Por eso el encabezado no se asume en una fila fija: se
 * busca, la fila de guía que le sigue se salta siempre, y si hay centinela los datos
 * empiezan después de ella —lo de en medio son los ejemplos y se ignoran—.
 *
 * <p>Aquí se valida lo estructural del archivo: referencias entre hojas, duplicados,
 * rangos numéricos, columnas que no tocan a ese formato. La completitud por formato
 * (un EF-4 con todas sus claves, un SEC que cubre 1..n) sigue siendo asunto de
 * {@code validarCoherencia} al publicar: el borrador puede quedar a medias a propósito.
 */
@Component
public class LectorPlantillaBanco {

    private static final Set<String> TIPOS_V3 =
            Set.of("EF-4", "SJT-R", "SEC", "INV", "DE", "CD", "V", "PC");

    // La columna A de la fila de encabezados, por hoja. Es el ancla con que se localiza
    // dónde empiezan los datos, así que debe seguir a la plantilla al pie de la letra.
    private static final String HOJA_PREGUNTAS = "Preguntas";
    private static final String HOJA_OPCIONES = "Opciones";
    private static final String HOJA_CAMPOS = "Campos de caso (CD)";
    private static final String HOJA_RANGOS = "Rangos (V)";
    private static final String HOJA_PARES = "Pares";
    private static final Map<String, String> ANCLAS = Map.of(
            HOJA_PREGUNTAS, "Código",
            HOJA_OPCIONES, "Código de la pregunta",
            HOJA_CAMPOS, "Código de la pregunta",
            HOJA_RANGOS, "Código de la pregunta",
            HOJA_PARES, "Pregunta A");

    // El lector es un @Component sin estado entre llamadas: el estado vive en Lectura.
    public BancoLeido leer(InputStream archivo, Map<String, String> indiceDimensiones) {
        return new Lectura(indiceDimensiones).leer(archivo);
    }

    /**
     * Sin mayúsculas ni acentos: «Integridad», «INTEGRIDAD» e «integridad» son lo mismo.
     * La usa también quien arma el índice de dimensiones, para que las claves y las
     * búsquedas pasen por la misma vara.
     */
    public static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .strip();
    }

    /** Una pasada sobre un archivo. Existe para que el bean de arriba no acumule estado. */
    private static final class Lectura {

        private final Map<String, String> indiceDimensiones;
        private final List<ErrorDeImportacion> errores = new ArrayList<>();

        private Lectura(Map<String, String> indiceDimensiones) {
            this.indiceDimensiones = indiceDimensiones;
        }

        private BancoLeido leer(InputStream archivo) {
            XSSFWorkbook libro;
            try {
                // Solo la apertura va dentro del try: POI lanza de todo ante un archivo
                // corrupto o que no es un xlsx (un .docx renombrado), y eso es un error
                // del archivo, no del servidor. Envolver también la lectura escondería
                // un fallo nuestro detrás de «revisa tu archivo», que está bien.
                libro = new XSSFWorkbook(archivo);
            } catch (Exception e) {
                error("(archivo)", 0, "no se pudo leer como .xlsx: ¿es la plantilla "
                        + "del banco guardada desde Excel o LibreOffice?");
                return new BancoLeido(List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.copyOf(errores));
            }
            try (libro) {
                return leerLibro(libro);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("No se pudo cerrar el libro leído", e);
            }
        }

        private BancoLeido leerLibro(XSSFWorkbook libro) {
            List<FilaPregunta> preguntas = new ArrayList<>();
            List<FilaOpcion> opciones = new ArrayList<>();
            List<FilaCampoCaso> campos = new ArrayList<>();
            List<FilaRango> rangos = new ArrayList<>();
            List<FilaPar> pares = new ArrayList<>();

            // El mapa código → tipo alimenta las comprobaciones cruzadas de las otras
            // hojas. LinkedHashMap para que los mensajes salgan en el orden del archivo.
            Map<String, String> tipoPorCodigo = new LinkedHashMap<>();
            Map<String, Integer> filaPorCodigo = new HashMap<>();

            Sheet hojaPreguntas = libro.getSheet(HOJA_PREGUNTAS);
            if (hojaPreguntas == null) {
                error(HOJA_PREGUNTAS, 0, "el archivo no tiene la hoja «Preguntas», "
                        + "que es la única obligatoria: ¿es la plantilla del banco?");
                return new BancoLeido(List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.copyOf(errores));
            }

            for (Row fila : filasDeDatos(hojaPreguntas, HOJA_PREGUNTAS)) {
                FilaPregunta p = leerPregunta(fila, tipoPorCodigo, filaPorCodigo);
                if (p != null) {
                    preguntas.add(p);
                }
            }

            // Las demás hojas son opcionales: faltar es lo mismo que venir vacías.
            Sheet hoja = libro.getSheet(HOJA_OPCIONES);
            if (hoja != null) {
                for (Row fila : filasDeDatos(hoja, HOJA_OPCIONES)) {
                    FilaOpcion o = leerOpcion(fila, tipoPorCodigo);
                    if (o != null) {
                        opciones.add(o);
                    }
                }
            }
            hoja = libro.getSheet(HOJA_CAMPOS);
            if (hoja != null) {
                for (Row fila : filasDeDatos(hoja, HOJA_CAMPOS)) {
                    FilaCampoCaso c = leerCampo(fila, tipoPorCodigo);
                    if (c != null) {
                        campos.add(c);
                    }
                }
            }
            hoja = libro.getSheet(HOJA_RANGOS);
            if (hoja != null) {
                for (Row fila : filasDeDatos(hoja, HOJA_RANGOS)) {
                    FilaRango r = leerRango(fila, tipoPorCodigo);
                    if (r != null) {
                        rangos.add(r);
                    }
                }
            }
            hoja = libro.getSheet(HOJA_PARES);
            if (hoja != null) {
                for (Row fila : filasDeDatos(hoja, HOJA_PARES)) {
                    FilaPar par = leerPar(fila, tipoPorCodigo, pares);
                    if (par != null) {
                        pares.add(par);
                    }
                }
            }

            // Un archivo del que no sale ni una pregunta es la plantilla sin llenar, o
            // una hoja con los ejemplos borrados y nada escrito. Crear un borrador vacío
            // y responder 201 sería decirle a quien sube que fue bien.
            if (preguntas.isEmpty() && errores.isEmpty()) {
                error(HOJA_PREGUNTAS, 0, "la hoja «Preguntas» no tiene ninguna pregunta "
                        + "debajo de la fila de ejemplos: ¿guardaste el archivo con lo "
                        + "tuyo escrito?");
            }

            comprobarCruces(preguntas, opciones, filaPorCodigo, campos);

            return new BancoLeido(List.copyOf(preguntas), List.copyOf(opciones),
                    List.copyOf(campos), List.copyOf(rangos), List.copyOf(pares),
                    List.copyOf(errores));
        }

        // ============ Dónde empiezan los datos ============

        /**
         * Las filas de datos de una hoja, salvando el decorado de la plantilla.
         *
         * <p>Busca la fila de encabezados por su columna A (las diez primeras filas),
         * salta la fila de guía que siempre la sigue, y si hay una fila centinela «⬇»
         * empieza después de ella: lo de en medio son los ejemplos grises. Las filas
         * totalmente vacías (la zona amarilla tiene estilo pero no valores) se saltan.
         *
         * <p>Vale la <b>primera</b> centinela y solo esa. Buscar la última sería peor que
         * inútil: quien copie a su hoja un bloque de la plantilla y arrastre el «⬇» al
         * medio perdería en silencio todo lo escrito encima, con un 201 que dice que fue
         * bien. Una segunda centinela es señal de eso mismo y se rechaza con su fila.
         */
        private List<Row> filasDeDatos(Sheet hoja, String nombre) {
            int filaEncabezado = -1;
            for (int i = 0; i <= Math.min(9, hoja.getLastRowNum()); i++) {
                if (ANCLAS.get(nombre).equalsIgnoreCase(textoDe(celda(hoja.getRow(i), 0)))) {
                    filaEncabezado = i;
                    break;
                }
            }
            if (filaEncabezado < 0) {
                error(nombre, 0, "no se encontró la fila de encabezados (la que empieza "
                        + "con «" + ANCLAS.get(nombre) + "»): no cambies los encabezados "
                        + "de la plantilla");
                return List.of();
            }

            int desde = filaEncabezado + 2;   // encabezado + fila de guía
            for (int i = desde; i <= hoja.getLastRowNum(); i++) {
                if (esCentinela(hoja.getRow(i))) {
                    desde = i + 1;
                    break;
                }
            }

            List<Row> filas = new ArrayList<>();
            for (int i = desde; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null || filaVacia(fila)) {
                    continue;
                }
                if (esCentinela(fila)) {
                    error(nombre, i + 1, "aquí hay otra fila «⬇ Escribe aquí lo tuyo…» "
                            + "en medio de los datos: bórrala, o lo que está encima de "
                            + "ella se quedaría fuera sin que nadie lo note");
                    continue;
                }
                if (textoDe(celda(fila, 0)) == null) {
                    error(nombre, i + 1, "falta el código en la primera columna");
                    continue;
                }
                filas.add(fila);
            }
            return filas;
        }

        private boolean esCentinela(Row fila) {
            String a = textoDe(celda(fila, 0));
            return a != null && a.startsWith("⬇");
        }

        // ============ Una fila de cada hoja ============

        private FilaPregunta leerPregunta(Row fila, Map<String, String> tipoPorCodigo,
                                          Map<String, Integer> filaPorCodigo) {
            int n = fila.getRowNum() + 1;
            String codigo = textoDe(celda(fila, 0));
            String tipo = textoDe(celda(fila, 1));
            String enunciado = textoDe(celda(fila, 2));
            String situacion = textoDe(celda(fila, 3));
            Integer peso = enteroDe(HOJA_PREGUNTAS, n, "Peso", celda(fila, 4));
            Boolean eliminatoria = siNoDe(HOJA_PREGUNTAS, n, "¿Eliminatoria?", celda(fila, 5));
            String queMide = textoDe(celda(fila, 6));
            Integer nCampos = enteroDe(HOJA_PREGUNTAS, n, "N° de campos", celda(fila, 7));
            String notaInterna = textoDe(celda(fila, 8));
            // Las dos últimas son para los ítems V que no se puntúan por tramos: o traen
            // la fórmula escrita, o remiten a la tabla de otro ítem (C36 usa la de D57).
            // Sin ellas, un banco con ítems así se importa pero no se puede publicar.
            String formula = textoDe(celda(fila, 9));
            String rangosDe = textoDe(celda(fila, 10));

            if (tipoPorCodigo.containsKey(codigo)) {
                error(HOJA_PREGUNTAS, n, "el código " + codigo + " ya apareció en la fila "
                        + filaPorCodigo.get(codigo) + ": cada pregunta lleva uno único");
                return null;
            }
            boolean bien = true;
            if (tipo == null || !TIPOS_V3.contains(tipo)) {
                error(HOJA_PREGUNTAS, n, "el tipo «" + (tipo == null ? "" : tipo)
                        + "» no es ninguno de los 8: EF-4, SJT-R, SEC, INV, DE, CD, V, PC");
                bien = false;
            }
            if (enunciado == null) {
                error(HOJA_PREGUNTAS, n, "falta la pregunta (columna C)");
                bien = false;
            }
            if (peso == null || peso < 0 || peso > 2) {
                error(HOJA_PREGUNTAS, n, "el peso debe ser 0, 1 o 2");
                bien = false;
            }
            if (nCampos != null && !"CD".equals(tipo)) {
                error(HOJA_PREGUNTAS, n, "«N° de campos» es solo para el tipo CD");
                bien = false;
            }
            if ("CD".equals(tipo) && (nCampos == null || nCampos < 1)) {
                error(HOJA_PREGUNTAS, n, "un CD declara cuántos campos tiene (columna H)");
                bien = false;
            }
            if ((formula != null || rangosDe != null) && !"V".equals(tipo)) {
                error(HOJA_PREGUNTAS, n, "la fórmula y la tabla prestada son solo del "
                        + "tipo V (dato verificable), y " + codigo + " es " + tipo);
                bien = false;
            }
            if (formula != null && rangosDe != null) {
                error(HOJA_PREGUNTAS, n, "elige una: o la fórmula escrita, o la tabla de "
                        + "otra pregunta, no las dos");
                bien = false;
            }

            List<String> dimensiones = dimensionesDe(n, queMide);

            // El código se registra aunque la fila tenga errores: así las otras hojas
            // no acumulan «pregunta inexistente» encima de un problema ya contado.
            if (codigo != null && tipo != null) {
                tipoPorCodigo.put(codigo, tipo);
                filaPorCodigo.put(codigo, n);
            }
            if (!bien || eliminatoria == null) {
                return null;
            }
            return new FilaPregunta(n, codigo, tipo, enunciado, situacion,
                    peso.shortValue(), eliminatoria, dimensiones,
                    nCampos == null ? null : nCampos.shortValue(),
                    formula, rangosDe, notaInterna);
        }

        private FilaOpcion leerOpcion(Row fila, Map<String, String> tipoPorCodigo) {
            int n = fila.getRowNum() + 1;
            String codigo = textoDe(celda(fila, 0));
            String texto = textoDe(celda(fila, 1));
            Integer esperada = enteroDe(HOJA_OPCIONES, n, "Respuesta esperada", celda(fila, 2));
            BigDecimal valor = decimalDe(HOJA_OPCIONES, n, "Valor oculto", celda(fila, 3));
            String trampaCruda = textoDe(celda(fila, 4));
            Boolean trampa = siNoDe(HOJA_OPCIONES, n, "¿Es trampa?", celda(fila, 4));
            Integer posicion = enteroDe(HOJA_OPCIONES, n, "Posición correcta", celda(fila, 5));

            String tipo = tipoPorCodigo.get(codigo);
            if (tipo == null) {
                error(HOJA_OPCIONES, n, "la pregunta " + codigo + " no está en la hoja Preguntas");
                return null;
            }
            boolean bien = true;
            if (texto == null) {
                error(HOJA_OPCIONES, n, "falta el texto de la opción");
                bien = false;
            }
            if (Set.of("V", "CD").contains(tipo)) {
                error(HOJA_OPCIONES, n, "la pregunta " + codigo + " es de tipo " + tipo
                        + " y no lleva opciones: sus respuestas van en su propia hoja");
                bien = false;
            }
            if (esperada != null && !"SJT-R".equals(tipo)) {
                error(HOJA_OPCIONES, n, "«Respuesta esperada» es solo para SJT-R y "
                        + codigo + " es " + tipo);
                bien = false;
            }
            if (esperada != null && (esperada < 1 || esperada > 5)) {
                error(HOJA_OPCIONES, n, "la respuesta esperada va de 1 a 5");
                bien = false;
            }
            if (valor != null && !"EF-4".equals(tipo)) {
                error(HOJA_OPCIONES, n, "«Valor oculto» es solo para EF-4 y "
                        + codigo + " es " + tipo);
                bien = false;
            }
            if (valor != null && (valor.compareTo(BigDecimal.valueOf(-2)) < 0
                    || valor.compareTo(BigDecimal.valueOf(2)) > 0)) {
                error(HOJA_OPCIONES, n, "el valor oculto va de −2 a +2");
                bien = false;
            }
            if (trampaCruda != null && !Set.of("INV", "DE").contains(tipo)) {
                error(HOJA_OPCIONES, n, "«¿Es trampa?» es solo para INV y DE y "
                        + codigo + " es " + tipo);
                bien = false;
            }
            if (posicion != null && !"SEC".equals(tipo)) {
                error(HOJA_OPCIONES, n, "«Posición correcta» es solo para SEC y "
                        + codigo + " es " + tipo);
                bien = false;
            }
            if (posicion != null && posicion < 1) {
                error(HOJA_OPCIONES, n, "la posición correcta empieza en 1");
                bien = false;
            }
            if (!bien || trampa == null) {
                return null;
            }
            return new FilaOpcion(n, codigo, texto,
                    esperada == null ? null : esperada.doubleValue(), valor, trampa,
                    posicion == null ? null : posicion.shortValue());
        }

        private FilaCampoCaso leerCampo(Row fila, Map<String, String> tipoPorCodigo) {
            int n = fila.getRowNum() + 1;
            String codigo = textoDe(celda(fila, 0));
            String etiqueta = textoDe(celda(fila, 1));
            String validacion = textoDe(celda(fila, 2));

            String tipo = tipoPorCodigo.get(codigo);
            if (tipo == null) {
                error(HOJA_CAMPOS, n, "la pregunta " + codigo + " no está en la hoja Preguntas");
                return null;
            }
            if (!"CD".equals(tipo)) {
                error(HOJA_CAMPOS, n, "la pregunta " + codigo + " es de tipo " + tipo
                        + ": los campos de caso son solo para CD");
                return null;
            }
            if (etiqueta == null) {
                error(HOJA_CAMPOS, n, "falta el texto del campo");
                return null;
            }
            return new FilaCampoCaso(n, codigo, etiqueta, validacion);
        }

        private FilaRango leerRango(Row fila, Map<String, String> tipoPorCodigo) {
            int n = fila.getRowNum() + 1;
            String codigo = textoDe(celda(fila, 0));
            String condicion = textoDe(celda(fila, 1));
            BigDecimal puntos = decimalDe(HOJA_RANGOS, n, "Puntos", celda(fila, 2));
            Boolean alerta = siNoDe(HOJA_RANGOS, n, "¿Levanta alerta?", celda(fila, 3));

            String tipo = tipoPorCodigo.get(codigo);
            if (tipo == null) {
                error(HOJA_RANGOS, n, "la pregunta " + codigo + " no está en la hoja Preguntas");
                return null;
            }
            boolean bien = true;
            if (!"V".equals(tipo)) {
                error(HOJA_RANGOS, n, "la pregunta " + codigo + " es de tipo " + tipo
                        + ": los rangos son solo para V");
                bien = false;
            }
            if (condicion == null) {
                error(HOJA_RANGOS, n, "falta la condición del rango");
                bien = false;
            }
            if (puntos == null || puntos.compareTo(BigDecimal.ZERO) < 0
                    || puntos.compareTo(BigDecimal.valueOf(3)) > 0) {
                error(HOJA_RANGOS, n, "los puntos van de 0 a 3, como toda fórmula del v3");
                bien = false;
            }
            if (!bien || alerta == null) {
                return null;
            }
            return new FilaRango(n, codigo, condicion, puntos, alerta);
        }

        private FilaPar leerPar(Row fila, Map<String, String> tipoPorCodigo,
                                List<FilaPar> yaLeidos) {
            int n = fila.getRowNum() + 1;
            String a = textoDe(celda(fila, 0));
            String b = textoDe(celda(fila, 1));
            BigDecimal penalizacion = decimalDe(HOJA_PARES, n, "Penalización", celda(fila, 2));
            Integer distancia = enteroDe(HOJA_PARES, n, "Distancia mínima", celda(fila, 3));
            String condicion = textoDe(celda(fila, 4));

            boolean bien = true;
            if (!tipoPorCodigo.containsKey(a)) {
                error(HOJA_PARES, n, "la pregunta " + a + " no está en la hoja Preguntas");
                bien = false;
            }
            if (b == null || !tipoPorCodigo.containsKey(b)) {
                error(HOJA_PARES, n, "la pregunta " + (b == null ? "(vacía)" : b)
                        + " no está en la hoja Preguntas");
                bien = false;
            }
            if (a != null && a.equals(b)) {
                error(HOJA_PARES, n, "un par une dos preguntas distintas, y aquí " + a
                        + " aparece en ambas columnas");
                bien = false;
            }
            if (yaLeidos.stream().anyMatch(p ->
                    (p.codigoA().equals(a) && p.codigoB().equals(b))
                            || (p.codigoA().equals(b) && p.codigoB().equals(a)))) {
                error(HOJA_PARES, n, "el par " + a + "–" + b + " ya está declarado");
                bien = false;
            }
            if (penalizacion != null && (penalizacion.compareTo(BigDecimal.ZERO) < 0
                    || penalizacion.compareTo(BigDecimal.valueOf(100)) > 0)) {
                error(HOJA_PARES, n, "la penalización es un porcentaje de 0 a 100");
                bien = false;
            }
            if (distancia != null && distancia < 1) {
                error(HOJA_PARES, n, "la distancia mínima es al menos 1 pregunta");
                bien = false;
            }
            if (!bien) {
                return null;
            }
            return new FilaPar(n, a, b, penalizacion,
                    distancia == null ? null : distancia.shortValue(), condicion);
        }

        // ============ Comprobaciones que necesitan el archivo entero ============

        private void comprobarCruces(List<FilaPregunta> preguntas, List<FilaOpcion> opciones,
                                     Map<String, Integer> filaPorCodigo,
                                     List<FilaCampoCaso> campos) {
            Map<String, Long> opcionesPorPregunta = new HashMap<>();
            for (FilaOpcion o : opciones) {
                opcionesPorPregunta.merge(o.codigoPregunta(), 1L, Long::sum);
            }
            opcionesPorPregunta.forEach((codigo, cuantas) -> {
                if (cuantas > 26) {
                    error(HOJA_PREGUNTAS, filaPorCodigo.getOrDefault(codigo, 0),
                            "la pregunta " + codigo + " tiene " + cuantas
                                    + " opciones y el máximo son 26 (letras a–z)");
                }
            });

            Map<String, Long> camposPorPregunta = new HashMap<>();
            for (FilaCampoCaso c : campos) {
                camposPorPregunta.merge(c.codigoPregunta(), 1L, Long::sum);
            }
            for (FilaPregunta p : preguntas) {
                if (!"CD".equals(p.tipo()) || p.casosPedidos() == null) {
                    continue;
                }
                long filas = camposPorPregunta.getOrDefault(p.codigo(), 0L);
                if (filas != p.casosPedidos()) {
                    error(HOJA_PREGUNTAS, p.fila(), "la pregunta " + p.codigo()
                            + " declara " + p.casosPedidos() + " campos pero la hoja "
                            + "«Campos de caso (CD)» trae " + filas);
                }
            }
        }

        // ============ Dimensiones («Qué mide») ============

        private List<String> dimensionesDe(int fila, String queMide) {
            if (queMide == null) {
                return List.of();
            }
            List<String> codigos = new ArrayList<>();
            for (String trozo : queMide.split(",")) {
                String pedido = trozo.trim();
                if (pedido.isEmpty()) {
                    continue;
                }
                String codigo = indiceDimensiones.get(normalizar(pedido));
                if (codigo == null) {
                    error(HOJA_PREGUNTAS, fila, "«" + pedido + "» no está en el catálogo "
                            + "de dimensiones; valen sus códigos o nombres: "
                            + String.join(", ", new TreeSet<>(indiceDimensiones.values())));
                } else if (!codigos.contains(codigo)) {
                    codigos.add(codigo);
                }
            }
            return List.copyOf(codigos);
        }

        // ============ Celdas ============

        private Cell celda(Row fila, int columna) {
            return fila == null ? null : fila.getCell(columna);
        }

        private boolean filaVacia(Row fila) {
            for (int i = 0; i < 9; i++) {
                if (textoDe(celda(fila, i)) != null) {
                    return false;
                }
            }
            return true;
        }

        /** El texto de la celda, venga como venga: cadena, número o fórmula ya calculada. */
        private String textoDe(Cell celda) {
            if (celda == null) {
                return null;
            }
            CellType tipo = celda.getCellType() == CellType.FORMULA
                    ? celda.getCachedFormulaResultType() : celda.getCellType();
            String texto = switch (tipo) {
                case STRING -> celda.getStringCellValue();
                // Un código como «30» o un peso llegan como número: sin los decimales
                // fantasma de Excel (30.0 → «30»).
                case NUMERIC -> BigDecimal.valueOf(celda.getNumericCellValue())
                        .stripTrailingZeros().toPlainString();
                case BOOLEAN -> celda.getBooleanCellValue() ? "sí" : "no";
                default -> null;
            };
            if (texto == null) {
                return null;
            }
            texto = texto.strip();
            return texto.isEmpty() ? null : texto;
        }

        private Integer enteroDe(String hoja, int fila, String columna, Cell celda) {
            String texto = textoDe(celda);
            if (texto == null) {
                return null;
            }
            try {
                return new BigDecimal(texto.replace(',', '.'))
                        .setScale(0, RoundingMode.UNNECESSARY).intValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                error(hoja, fila, "«" + columna + "» debe ser un número entero y dice «"
                        + texto + "»");
                return null;
            }
        }

        private BigDecimal decimalDe(String hoja, int fila, String columna, Cell celda) {
            String texto = textoDe(celda);
            if (texto == null) {
                return null;
            }
            try {
                return new BigDecimal(texto.replace(',', '.'));
            } catch (NumberFormatException e) {
                error(hoja, fila, "«" + columna + "» debe ser un número y dice «"
                        + texto + "»");
                return null;
            }
        }

        /** «sí»/«no» en cualquier caja y sin exigir la tilde. Vacío es que no. */
        private Boolean siNoDe(String hoja, int fila, String columna, Cell celda) {
            String texto = textoDe(celda);
            if (texto == null) {
                return false;
            }
            return switch (normalizar(texto)) {
                case "si" -> true;
                case "no" -> false;
                default -> {
                    error(hoja, fila, "«" + columna + "» admite sí o no, y dice «"
                            + texto + "»");
                    yield null;
                }
            };
        }

        private void error(String hoja, int fila, String mensaje) {
            errores.add(new ErrorDeImportacion(hoja, fila, mensaje));
        }
    }
}
