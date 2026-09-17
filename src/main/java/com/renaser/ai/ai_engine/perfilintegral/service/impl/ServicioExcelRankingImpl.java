package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.ExcelDeRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.PedidoExcelRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DatosCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.FilaRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.Ponderado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.NotaCriterioResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioExcelRanking;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPerfilIntegralPanel;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.EnlaceArchivo;
import com.renaser.ai.ai_engine.postulacion.service.ServicioPostulacionesPanel;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El volcado del ranking a un .xlsx de <b>una sola hoja</b>, llamada {@code Datos}.
 *
 * <p><b>De dónde salen los datos.</b> De la misma tanda que pinta la pantalla
 * ({@link ServicioPerfilIntegralPanel#ranking}). No se toca ningún repositorio: así el filtro
 * por organización y el alcance del permiso los sigue aplicando el mismo guardián de siempre,
 * y este volcado no puede enseñar una fila que la pantalla no enseñaría.
 *
 * <p><b>Por qué una hoja y no dos.</b> Hasta la plantilla nueva eran {@code Resumen} y
 * {@code Detalle}: la segunda ponía una línea por criterio y por candidato, así que una tanda
 * de ochenta con ocho criterios eran seiscientas cuarenta filas que nadie leía en vertical.
 * Ahora cada criterio es <b>una columna</b> —igual que en la tabla del panel— y el detalle que
 * de verdad se lee, la explicación, viaja junto al candidato en «Justificación detallada». Con
 * eso la hoja de detalle se queda sin nada propio que contar.
 *
 * <p><b>El orden.</b> Llega en {@code postulacionIds} y se copia. La tanda del ranking viene
 * ordenada por grupo de prioridad y nota —otro orden, perfectamente válido, y no el que se
 * pidió—, así que las filas se recorren por la lista del pedido y la tanda solo sirve de
 * índice. Escribir recorriendo la tanda «porque ya viene ordenada» es el error que deja una
 * hoja que no se parece a la pantalla desde la que se exportó.
 *
 * <p><b>XSSF y no SXSSF.</b> Una tanda real son 40-80 filas y no hay paginación: el libro
 * entero en memoria son unos pocos cientos de kilobytes. SXSSF paga cuando son decenas de
 * miles de filas, y a cambio deja un libro que ya no se puede releer.
 */
@Service
public class ServicioExcelRankingImpl implements ServicioExcelRanking {

    private static final Logger log = LoggerFactory.getLogger(ServicioExcelRankingImpl.class);

    private static final String PERFIL_INTEGRAL = "PERFIL_INTEGRAL";
    private static final String PRUEBA_PUESTO = "PRUEBA_PUESTO";

    /** La única hoja. El nombre lo pidió el cliente y es el mismo en las dos etapas. */
    private static final String HOJA = "Datos";

    /**
     * Lo que dice una celda de nota sin nota. Nunca en blanco y nunca un cero: un cero es un
     * juicio que nadie ha hecho, y un blanco en una columna de números se lee como un cero.
     */
    private static final String SIN_NOTA = "rúbrica incompleta";

    /** Lo que falta aquí es una nota de etapa, no una rúbrica. */
    private static final String SIN_ETAPA = "falta una nota de etapa";

    /**
     * El permiso que pide firmar el enlace al currículum, columna «CV».
     *
     * <p>⚠️ <b>Tiene que ser el MISMO que pide {@code ServicioPostulacionesPanel}</b> al
     * entregar el archivo. Se pregunta aquí una vez, antes de firmar ochenta veces, para
     * poder decirlo en una línea del pie en vez de tragarse ochenta excepciones iguales.
     */
    private static final String PERMISO_CV = "descargar_entregables";

    /** El índigo de la cabecera, el mismo que el resto de los volcados: 4338CA. */
    private static final byte[] INDIGO = {0x43, 0x38, (byte) 0xCA};

    private final ServicioPerfilIntegralPanel tandas;
    private final ServicioPostulacionesPanel archivos;
    private final Clock reloj;

    @Autowired
    public ServicioExcelRankingImpl(ServicioPerfilIntegralPanel tandas,
                                    ServicioPostulacionesPanel archivos) {
        this(tandas, archivos, Clock.systemDefaultZone());
    }

    // El reloj entra por el constructor para que la fecha del nombre del archivo se pueda
    // comprobar sin quemar el día de hoy en una prueba: las fechas quemadas caducan.
    ServicioExcelRankingImpl(ServicioPerfilIntegralPanel tandas,
                             ServicioPostulacionesPanel archivos, Clock reloj) {
        this.tandas = tandas;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public ExcelDeRanking generar(ContextoUsuario quien, Long vacanteId, PedidoExcelRanking pedido) {
        String etapa = pedido.etapa() == null ? "" : pedido.etapa().trim();
        if (!PERFIL_INTEGRAL.equals(etapa) && !PRUEBA_PUESTO.equals(etapa)) {
            throw new IllegalArgumentException(
                    "El ranking solo se vuelca a Excel para PERFIL_INTEGRAL y PRUEBA_PUESTO; "
                            + "«" + etapa + "» no tiene columnas que volcar.");
        }

        // La anotación del dto ya lo pide, y aun así se comprueba aquí: la validación de
        // Spring no siempre acaba en un 400 en este proyecto, y una lista nula reventaría
        // más abajo con un 500 que no le dice nada a quien llama.
        if (pedido.postulacionIds() == null || pedido.postulacionIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "No llegó ninguna postulación que volcar: el Excel se arma con la lista "
                            + "de candidatos ya ordenada.");
        }

        RankingVacante tanda = tandas.ranking(quien, vacanteId, etapa);
        Map<Long, FilaRanking> deLaVacante = tanda.filas().stream()
                .collect(Collectors.toMap(FilaRanking::postulacionId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));

        // Aquí y solo aquí se decide el orden de las filas: la lista del pedido. El mapa de
        // arriba es un índice, no un recorrido.
        List<FilaRanking> enElOrdenPedido = new ArrayList<>();
        List<Long> ajenas = new ArrayList<>();
        Set<Long> yaPuestas = new LinkedHashSet<>();
        for (Long id : pedido.postulacionIds()) {
            if (id == null || !yaPuestas.add(id)) {
                continue;
            }
            FilaRanking fila = deLaVacante.get(id);
            if (fila == null) {
                // No es de esta vacante (o ya no lo es). Se anota para decirlo al pie.
                ajenas.add(id);
            } else {
                enElOrdenPedido.add(fila);
            }
        }
        if (enElOrdenPedido.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ninguna de las " + ajenas.size() + " postulaciones pedidas es de la vacante "
                            + vacanteId + ": no hay nada que volcar.");
        }

        /*
          ⚠️ **Las columnas de criterio salen de la TANDA ENTERA y las filas del recorte
          pedido.** Son dos listas distintas a propósito, y confundirlas rompe dos cosas:

          <ul>
            <li>Con el recorte, el orden de las columnas lo decidiría el orden de las FILAS,
                así que la misma tanda descargada dos veces con distinta ordenación saldría
                con las columnas cambiadas de sitio.
            <li>Y la tabla del panel arma sus columnas de criterio con las filas SIN filtrar
                —a propósito, y está escrito allí—. Si un filtro deja fuera a la única persona
                que tiene puntuado un criterio, la pantalla sigue enseñando esa columna; con
                el recorte, el archivo se la comería y dejaría de parecerse a la pantalla.
          </ul>
        */
        byte[] contenido = escribir(quien, etapa, enElOrdenPedido, tanda.filas(), ajenas,
                pedido.filtroDescrito());
        return new ExcelDeRanking(nombreDelArchivo(etapa, vacanteId), contenido);
    }

    /** {@code ranking-perfil-integral-vacante-13-2026-08-31.xlsx}: la fecha va dentro. */
    private String nombreDelArchivo(String etapa, Long vacanteId) {
        return "ranking-" + etapa.toLowerCase().replace('_', '-')
                + "-vacante-" + vacanteId + "-" + LocalDate.now(reloj) + ".xlsx";
    }

    // ========================================================================
    // El libro
    // ========================================================================

    private byte[] escribir(ContextoUsuario quien, String etapa, List<FilaRanking> filas,
                            List<FilaRanking> laTandaEntera, List<Long> ajenas,
                            String filtroDescrito) {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Pinceles pinceles = new Pinceles(libro);
            Sheet hoja = libro.createSheet(HOJA);

            boolean deLaPrueba = PRUEBA_PUESTO.equals(etapa);
            List<Criterio> criterios = criteriosDeLaTanda(laTandaEntera);
            boolean veElCv = quien.tiene(PERMISO_CV);
            Map<Long, EnlaceArchivo> enlaces = veElCv ? enlacesDelCv(quien, filas) : Map.of();

            encabezar(hoja, pinceles, cabeceras(deLaPrueba, criterios),
                    anchos(deLaPrueba, criterios));
            int linea = 1;
            for (FilaRanking fila : filas) {
                escribirFila(hoja.createRow(linea++), pinceles, fila, deLaPrueba, criterios,
                        enlaces.get(fila.postulacionId()));
            }
            pie(hoja, pinceles, filas, ajenas, filtroDescrito, veElCv, enlaces);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            // Escribir en memoria no falla por E/S; si falla, no hay nada que reintentar y
            // callarlo dejaría un archivo de cero bytes que el navegador baja igual.
            throw new UncheckedIOException("No se pudo armar el Excel del ranking", e);
        }
    }

    // ========================================================================
    // Las columnas
    // ========================================================================

    /**
     * Las cabeceras, en el orden de la plantilla que pidió el cliente.
     *
     * <p>⚠️ <b>«Nota Perfil Integral» y no «Nota CV», aunque la plantilla diga lo segundo.</b>
     * En las vacantes de Administrador y Asistente Administrativo esa cifra <b>es</b> la nota
     * del currículum —su perfil integral lo llena el CV desde la V52, porque tienen el banco
     * de preguntas apagado—, y por eso la plantilla la llamó así. Pero en una vacante con
     * banco esa misma columna trae la nota de la prueba RENASER y no la del currículum:
     * rotularla «CV» diría, en la mitad de las vacantes, de dónde sale un número que sale de
     * otro sitio. El pie lo explica en una línea.
     *
     * <p>⚠️ <b>«/100» y no «/40».</b> La plantilla traía «Nota Combinada /40» en la cabecera
     * y {@code =0.55*F+0.45*G} en las celdas, que da una cifra sobre 100; y su hoja de
     * Metodología hablaba de un 30 % y un 12 % que tampoco son esos pesos. De las tres, la
     * que coincide con el sistema es la fórmula: 45 el perfil integral y 55 la prueba, que es
     * lo que dice {@code peso_etapa} para esas vacantes.
     */
    private List<String> cabeceras(boolean deLaPrueba, List<Criterio> criterios) {
        List<String> titulos = new ArrayList<>(
                List.of("#", "Candidato", "Correo", "CV", "Teléfono"));
        if (deLaPrueba) {
            titulos.add("Nota Examen Técnico /100");
            titulos.add("Nota Perfil Integral /100");
        } else {
            titulos.add("Nota Perfil Integral /100");
        }
        criterios.forEach(c -> titulos.add(c.cabecera()));
        if (deLaPrueba) {
            titulos.add("Nota Combinada /100");
        }
        titulos.add("Justificación resumida");
        titulos.add("Justificación detallada");
        return titulos;
    }

    private int[] anchos(boolean deLaPrueba, List<Criterio> criterios) {
        List<Integer> anchos = new ArrayList<>(List.of(5, 34, 32, 30, 18));
        anchos.add(15);
        if (deLaPrueba) {
            anchos.add(16);
        }
        criterios.forEach(c -> anchos.add(14));
        if (deLaPrueba) {
            anchos.add(17);
        }
        anchos.add(48);
        anchos.add(90);
        return anchos.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Los criterios que se pintan, uno por columna, en el orden en que aparecen.
     *
     * <p>Salen de las propias filas y no de un catálogo: en la prueba del puesto la rúbrica
     * es la de ESA vacante, así que dos vacantes traen columnas distintas y no hay lista fija
     * que consultar. Es la misma regla que sigue la tabla del panel, y por eso el archivo
     * sale con las mismas columnas que la pantalla desde la que se pidió.
     *
     * <p>Se indexan por {@link Clave} —el código de la rúbrica cuando lo hay— y gana el
     * primero que aparece. Después se desambiguan los rótulos que hayan quedado repetidos.
     */
    private List<Criterio> criteriosDeLaTanda(List<FilaRanking> filas) {
        Map<Clave, Criterio> porClave = new LinkedHashMap<>();
        for (FilaRanking fila : filas) {
            List<NotaCriterioResponse> suyos = fila.notasCriterio();
            if (suyos == null) {
                continue;
            }
            for (NotaCriterioResponse nota : suyos) {
                Criterio criterio = Criterio.de(nota);
                porClave.putIfAbsent(criterio.clave(), criterio);
            }
        }
        return desambiguados(List.copyOf(porClave.values()));
    }

    /**
     * Dos columnas distintas nunca llevan el mismo rótulo encima. Aquí se garantiza.
     *
     * <p>⚠️ <b>Y se garantiza de verdad, no «casi».</b> El intento anterior contaba los
     * rótulos repetidos una vez y les pegaba el código, y eso fallaba por los dos lados: no
     * hacía nada cuando los colisionados no tenían código —que es justo el caso de un
     * criterio llamado «Divisas» junto a otro llamado «Divisas (pts /15)»—, y podía crear una
     * colisión nueva si un tercer criterio ya se llamaba como quedaba el retocado. Esto
     * recorre la lista una sola vez llevando cuenta de lo ya usado, así que el rótulo que
     * sale es único por construcción: primero se intenta con el código, y si tampoco basta,
     * con un ordinal.
     */
    private List<Criterio> desambiguados(List<Criterio> criterios) {
        /*
          Dos pasadas, y las dos hacen falta:

          La primera cuenta los rótulos repetidos y le enseña el código a TODAS las columnas
          que compartan uno, no solo a la segunda. Retocar únicamente a la que llega después
          deja una pareja asimétrica —«Comunicación» y «Comunicación [COM_ESCRITA]»— en la que
          la primera parece la buena y la otra una variante suya, cuando son dos criterios del
          mismo rango.

          La segunda garantiza la unicidad de verdad, contando lo ya usado sobre la marcha:
          cubre lo que el código no arregla —dos criterios sin código, o un tercero que ya se
          llamaba como queda el retocado— y ahí entra el ordinal.
        */
        Map<String, Long> cuantosPorRotulo = criterios.stream()
                .collect(Collectors.groupingBy(this::clavePorRotulo, Collectors.counting()));

        Set<String> yaUsados = new LinkedHashSet<>();
        List<Criterio> salida = new ArrayList<>(criterios.size());
        for (Criterio criterio : criterios) {
            Criterio suyo = cuantosPorRotulo.get(clavePorRotulo(criterio)) > 1
                    ? criterio.conElCodigoALaVista()
                    : criterio;
            int intento = 2;
            while (!yaUsados.add(clavePorRotulo(suyo))) {
                suyo = criterio.conElCodigoALaVista().numerado(intento++);
            }
            salida.add(suyo);
        }
        return List.copyOf(salida);
    }

    private String clavePorRotulo(Criterio criterio) {
        return criterio.cabecera().toLowerCase(Locale.ROOT);
    }

    /**
     * Lo que hace que dos notas caigan en la MISMA columna: <b>el nombre, el código y el
     * techo</b>. Las tres, y ninguna sobra.
     *
     * <p>Esta clave ha sido cinco cosas distintas y cada versión se rompió por su lado, así
     * que conviene dejar escrito qué le pasaba a cada una:
     *
     * <ul>
     *   <li><b>Solo el nombre</b> mezclaba una tanda con dos versiones de la misma plantilla:
     *       «Divisas» sobre 15 en unas filas y sobre 20 en otras acababa con una cabecera que
     *       decía «/20» y celdas que eran sobre 15 — un 14 leído como 70 % donde hubo un 93 %.
     *   <li><b>(nombre, techo) con el techo como {@code BigDecimal}</b> dejaba pasar dos
     *       columnas con el mismo texto encima: {@code 15} y {@code 15.00} son objetos
     *       distintos y se escriben igual.
     *   <li><b>El rótulo entero</b> se tragaba las notas de dos criterios de verdad distintos
     *       que se llamaran igual —una rúbrica con «Comunicación» oral y «Comunicación»
     *       escrita es una rúbrica normal—, y la segunda desaparecía de la hoja.
     *   <li><b>(código, techo)</b> parecía la buena y tiene el fallo más feo de todos:
     *       ⚠️ <b>el código solo es único dentro de UNA versión de plantilla</b> —así lo
     *       declara el índice de la V10 y así lo comprueba el servicio al publicarla—. En una
     *       tanda que mezcle dos versiones, el mismo {@code C2} puede ser «Comunicación» en
     *       una y «Control de caja» en la otra: las dos notas caían en la misma columna y una
     *       se leía bajo el nombre del otro criterio. No es una nota perdida, es un número
     *       contado como si midiera otra cosa.
     * </ul>
     *
     * <p>Con las tres, cada una tapa el agujero de las otras: el nombre separa los códigos
     * reutilizados entre versiones, el código separa los homónimos de una misma rúbrica, y el
     * techo separa las versiones de un mismo criterio. Lo que queda fuera —dos criterios sin
     * nombre y sin código— no tiene nada que los distinga, y para eso está la garantía de que
     * ninguna nota se cae de la hoja: {@link #justificacionDetallada} escribe todas.
     *
     * <p>El nombre se compara sin distinguir mayúsculas ni espacios de sobra, porque «Divisas»
     * y «&nbsp;divisas&nbsp;» son el mismo criterio escrito dos veces. El código <b>sí</b>
     * distingue mayúsculas, porque la unicidad que lo respalda también lo hace: {@code COM} y
     * {@code com} pueden convivir en una rúbrica y son dos criterios.
     */
    private record Clave(String nombre, String codigo, String techo) {}

    /** Un criterio de la rúbrica y su techo, que es lo que va en la cabecera. */
    private record Criterio(String nombre, String codigo, BigDecimal maximo,
                            boolean codigoALaVista, int ordinal) {

        /**
         * Lo que se escribe cuando el criterio llegó sin nombre.
         *
         * <p>Antes se descartaba, y descartarlo le quitaba la columna a un puntaje que sí
         * existe: la nota desaparecía de la rejilla y solo asomaba en la justificación, en
         * una línea que empezaba por dos puntos. Un criterio mal nombrado es un problema de
         * la plantilla; perder su nota es un problema de esta hoja.
         */
        private static final String SIN_NOMBRE = "(criterio sin nombre)";

        static Criterio de(NotaCriterioResponse nota) {
            String suyo = nota.criterio() == null ? "" : nota.criterio().trim();
            String suCodigo = nota.codigo() == null ? "" : nota.codigo().trim();
            return new Criterio(suyo.isEmpty() ? SIN_NOMBRE : suyo, suCodigo, nota.maximo(),
                    false, 0);
        }

        Clave clave() {
            return new Clave(nombre.toLowerCase(Locale.ROOT), codigo, techoDicho());
        }

        /** El mismo criterio, rotulado con su código para no confundirse con su homónimo. */
        Criterio conElCodigoALaVista() {
            return codigo.isEmpty() ? this : new Criterio(nombre, codigo, maximo, true, 0);
        }

        /**
         * El último recurso: un ordinal pegado al rótulo.
         *
         * <p>Hace falta cuando el código no desambigua —porque no lo hay, o porque el rótulo
         * con el código ya lo usa otro—. Un «(2)» no le dice nada a nadie, y es exactamente
         * por eso: avisa de que la rúbrica tiene dos criterios que no se pueden distinguir
         * mirándolos, que es un problema de la plantilla y no de esta hoja. Lo que no hace es
         * dejar dos columnas iguales, que es lo que obliga a adivinar cuál es cuál.
         */
        Criterio numerado(int cual) {
            return new Criterio(nombre, codigo, maximo, codigoALaVista, cual);
        }

        private String techoDicho() {
            return maximo == null ? "" : maximo.stripTrailingZeros().toPlainString();
        }

        /** «Conocimiento del negocio de divisas (pts /15)», como en la plantilla. */
        String cabecera() {
            String suyo = codigoALaVista ? nombre + " [" + codigo + "]" : nombre;
            if (ordinal > 0) {
                suyo = suyo + " (" + ordinal + ")";
            }
            return maximo == null ? suyo : suyo + " (pts /" + techoDicho() + ")";
        }
    }

    // ========================================================================
    // La fila
    // ========================================================================

    private void escribirFila(Row f, Pinceles pinceles, FilaRanking fila, boolean deLaPrueba,
                              List<Criterio> criterios, EnlaceArchivo enlaceDelCv) {
        int c = 0;
        // El «#» es el puesto EN EL RANKING, el mismo que se ve en la mesa, y no la posición
        // en esta hoja. Dos razones: la hoja ya numera sus propias filas por el margen, así
        // que una columna que repita eso no añade nada; y con la posición de la hoja, la
        // misma descarga decía «#2 Camila» en pantalla y «#1 Camila» en el archivo, con la
        // misma cabecera. El orden de las filas sí es el pedido.
        numero(f, c++, fila.puesto(), pinceles);
        texto(f, c++, fila.candidato(), pinceles);
        texto(f, c++, correoDelCv(fila), pinceles);
        cv(f, c++, fila, enlaceDelCv, pinceles);
        texto(f, c++, telefonoDelCv(fila), pinceles);

        Ponderado suyo = fila.ponderado();
        if (deLaPrueba) {
            // En esta pestaña «la nota de la etapa» ES la de la prueba: la misma cifra que la
            // columna «Nota» de la tabla. No se saca del ponderado para que una fila sin
            // ponderado —a la que le falta la otra etapa— siga enseñando la que sí tiene.
            nota(f, c++, fila.notaEtapa(), pinceles);
            cifraOMotivo(f, c++, suyo == null ? null : suyo.perfil(), pinceles);
        } else {
            nota(f, c++, fila.notaEtapa(), pinceles);
        }

        Map<Clave, NotaCriterioResponse> suyas = notasPorCriterio(fila);
        for (Criterio criterio : criterios) {
            NotaCriterioResponse nota = suyas.get(criterio.clave());
            // Sin nota de ESE criterio la celda se queda en blanco y no dice «rúbrica
            // incompleta»: en una columna por criterio, el hueco ya es el mensaje, y
            // ochenta celdas repitiendo la misma frase taparían las cifras que hay al lado.
            cifra(f, c++, nota == null ? null : nota.puntaje(), pinceles);
        }

        if (deLaPrueba) {
            cifraOMotivo(f, c++, suyo == null ? null : suyo.sobre100(), pinceles);
        }

        largo(f, c++, fila.resumen(), pinceles);
        largo(f, c, justificacionDetallada(fila), pinceles);
    }

    /**
     * Las notas de esta fila, indexadas por la MISMA clave con la que se armaron las
     * columnas.
     *
     * <p>⚠️ Tiene que ser la misma —{@link Criterio#clave()} y no el nombre en crudo—, o la
     * cabecera y la celda dejan de hablar del mismo criterio. Cuando una se quedaba con el
     * techo más alto y la otra con la primera nota, la hoja escribía un puntaje bajo una
     * cabecera que prometía otro máximo.
     */
    private Map<Clave, NotaCriterioResponse> notasPorCriterio(FilaRanking fila) {
        List<NotaCriterioResponse> suyas = fila.notasCriterio();
        if (suyas == null || suyas.isEmpty()) {
            return Map.of();
        }
        Map<Clave, NotaCriterioResponse> mapa = new LinkedHashMap<>();
        for (NotaCriterioResponse nota : suyas) {
            mapa.putIfAbsent(Criterio.de(nota).clave(), nota);
        }
        return mapa;
    }

    /**
     * Lo que sostiene la nota, criterio a criterio y en una sola celda.
     *
     * <p>Es lo que hasta ahora vivía en la hoja «Detalle» con una fila por criterio. Junto
     * así se lee al lado del candidato en vez de en otra pestaña, que es donde se lee de
     * verdad: quien abre esta hoja está comparando personas, no criterios.
     *
     * <p>El motivo del ajuste va pegado a su criterio y no aparte, porque solo significa algo
     * junto a la nota que corrigió. Cuando no hay ninguno, no se escribe nada — que una
     * persona no haya tocado una nota es lo normal, no una ausencia que explicar.
     */
    private String justificacionDetallada(FilaRanking fila) {
        List<NotaCriterioResponse> suyas = fila.notasCriterio();
        if (suyas == null || suyas.isEmpty()) {
            return "";
        }
        return suyas.stream()
                .map(this::unCriterioExplicado)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    private String unCriterioExplicado(NotaCriterioResponse nota) {
        String explicacion = nota.explicacion() == null ? "" : nota.explicacion().trim();
        String motivo = nota.motivoAjuste() == null ? "" : nota.motivoAjuste().trim();
        /*
          ⚠️ **Una nota con puntaje se escribe aunque no venga explicada**, y no es relleno:
          es la garantía de que ningún número se cae de la hoja. Dos criterios que la rejilla
          no puede separar —sin nombre y sin código— comparten columna y solo uno se ve ahí;
          si además llegaran mudos, el otro puntaje no estaría en ninguna parte del archivo.
          Callar una nota es peor que escribir «sin explicación».
        */
        if (explicacion.isEmpty() && motivo.isEmpty() && nota.puntaje() == null) {
            return "";
        }
        StringBuilder linea = new StringBuilder();
        // El nombre TAL COMO LO TRAJO LA FILA, recortado, más el de reserva cuando llegó en
        // blanco. ⚠️ No es exactamente el rótulo de su columna: ese sale del primer criterio
        // que apareció en la tanda y puede llevar el código detrás para desambiguar. Se deja
        // así a propósito — aquí se explica lo que dice ESTA fila, no lo que rotula la
        // columna, y forzarlo a coincidir escondería que la rúbrica los escribe distinto.
        linea.append(Criterio.de(nota).nombre());
        if (nota.puntaje() != null) {
            linea.append(" (").append(nota.puntaje().stripTrailingZeros().toPlainString());
            if (nota.maximo() != null) {
                linea.append('/').append(nota.maximo().stripTrailingZeros().toPlainString());
            }
            linea.append(')');
        }
        linea.append(": ");
        linea.append(explicacion.isEmpty() ? "sin explicación" : explicacion);
        if (!motivo.isEmpty()) {
            linea.append("\nCorregida a mano: ").append(motivo);
        }
        return linea.toString();
    }

    // ========================================================================
    // La columna «CV»
    // ========================================================================

    /**
     * Un enlace firmado por candidato, pedido de una vez antes de escribir nada.
     *
     * <p>⚠️ <b>Es una llamada al almacén por fila</b>, y no hay forma de firmar en bloque:
     * cada enlace es de un objeto. Con una tanda de ochenta son ochenta firmas, que es caro
     * pero acotado —no crece con el tamaño de los currículums— y ocurre una vez por descarga.
     * Si algún día la tanda deja de tener ese tamaño, lo que hay que cambiar es esto.
     *
     * <p>Una fila que falle no tumba el volcado: se queda sin enlace y el pie dice cuántas.
     * Un archivo que ya no está en el almacén y una firma que el almacén rechaza son dos
     * motivos distintos para lo mismo, y ninguno justifica dejar sin hoja a las otras setenta
     * y nueve.
     */
    private Map<Long, EnlaceArchivo> enlacesDelCv(ContextoUsuario quien, List<FilaRanking> filas) {
        Map<Long, EnlaceArchivo> enlaces = new LinkedHashMap<>();
        for (FilaRanking fila : filas) {
            if (fila.archivoId() == null) {
                continue;
            }
            try {
                Optional<EnlaceArchivo> enlace = archivos.enlaceDeVolcado(quien, fila.archivoId());
                enlace.filter(this::sePuedeAbrirDesdeUnExcel)
                        .ifPresent(e -> enlaces.put(fila.postulacionId(), e));
            } catch (RuntimeException noSePudo) {
                log.warn("No se pudo firmar el enlace del CV de la postulación {}: {}",
                        fila.postulacionId(), noSePudo.toString());
            }
        }
        return enlaces;
    }

    /**
     * Si esa url la puede abrir quien haga clic en una celda.
     *
     * <p>⚠️ <b>No todo almacén reparte enlaces que un navegador entienda.</b> El de memoria
     * —el que usa el perfil local y el que ven los tests— devuelve {@code memoria://…}, que es
     * una url perfectamente formada y que no abre nada. Sin esta comprobación la hoja salía en
     * local con cuatro enlaces azules muertos y un pie prometiendo que «abren el currículum
     * sin pedir sesión»: las dos cosas falsas a la vez, y ninguna se nota hasta que alguien
     * pulsa.
     *
     * <p>Se mira el <b>esquema</b> y no el nombre del almacén, por lo mismo que lo hace el
     * panel al bajar un archivo: lo que decide es si el navegador sabrá qué hacer con ella.
     * Un enlace que no se escribe se cuenta al pie como lo que es, uno que falta.
     */
    private boolean sePuedeAbrirDesdeUnExcel(EnlaceArchivo enlace) {
        String url = enlace.url() == null ? "" : enlace.url().trim().toLowerCase(Locale.ROOT);
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * El currículum: su nombre, y detrás el enlace cuando lo hay.
     *
     * <p>El texto es siempre el nombre del archivo y no la URL. Una URL firmada son
     * doscientos caracteres de ruido en una columna que se lee de un vistazo, y el nombre es
     * además lo que sirve para dar con el archivo cuando el enlace ya caducó.
     */
    private void cv(Row fila, int columna, FilaRanking datos, EnlaceArchivo enlace,
                    Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        if (enlace == null) {
            celda.setCellValue(loQueCabe(datos.archivoNombre()));
            celda.setCellStyle(pinceles.normal);
            return;
        }
        /*
          ⚠️ **Habiendo enlace se escribe SIEMPRE, aunque el archivo no tenga nombre.** Antes
          se salía por el nombre vacío, y eso dejaba lo peor de los dos mundos: la firma ya se
          había pedido, o sea que había una URL pública viva durante horas, y la celda salía
          en blanco, así que nadie podía usarla ni sabía que existía. Si el archivo se firmó,
          el enlace va; el rótulo es lo de menos y para eso está el de reserva.
        */
        celda.setCellValue(loQueCabe(nombreDelCv(datos, enlace)));
        Hyperlink url = pinceles.enlaces.createHyperlink(HyperlinkType.URL);
        url.setAddress(enlace.url());
        celda.setHyperlink(url);
        celda.setCellStyle(pinceles.enlace);
    }

    /** Cómo rotular el enlace: el nombre de la tanda, el del almacén, o uno genérico. */
    private String nombreDelCv(FilaRanking datos, EnlaceArchivo enlace) {
        if (datos.archivoNombre() != null && !datos.archivoNombre().isBlank()) {
            return datos.archivoNombre().trim();
        }
        if (enlace.nombre() != null && !enlace.nombre().isBlank()) {
            return enlace.nombre().trim();
        }
        return "currículum";
    }

    // ========================================================================
    // El pie
    // ========================================================================

    private void pie(Sheet hoja, Pinceles pinceles, List<FilaRanking> filas, List<Long> ajenas,
                     String filtroDescrito, boolean veElCv, Map<Long, EnlaceArchivo> enlaces) {
        int linea = hoja.getLastRowNum() + 2;

        linea = anotar(hoja, pinceles, linea,
                "Filtro aplicado: " + filtroDicho(filtroDescrito) + " · Generado el "
                        + LocalDate.now(reloj) + " · " + filas.size() + " candidatos");

        // Lo que vale la columna «Nota Perfil Integral» cambia con la vacante, y quien abra
        // esta hoja fuera del panel no tiene dónde averiguarlo.
        linea = anotar(hoja, pinceles, linea,
                "«Nota Perfil Integral» es la nota de esa etapa. En las vacantes con el banco "
                        + "de preguntas apagado —Administrador y Asistente Administrativo— esa "
                        + "cifra ES la nota del currículum; en las demás es la de la prueba "
                        + "RENASER del banco.");

        // Un enlace que caduca tiene que decir que caduca, y sobre todo tiene que decir lo
        // otro: mientras viva no vuelve a preguntar nada.
        long conCv = filas.stream().filter(f -> f.archivoId() != null).count();
        if (!veElCv) {
            if (conCv > 0) {
                linea = anotar(hoja, pinceles, linea,
                        "La columna CV va sin enlaces porque tu rol no tiene el permiso «"
                                + PERMISO_CV + "»: solo se escribió el nombre del archivo.");
            }
        } else if (!enlaces.isEmpty()) {
            /*
              ⚠️ **El aviso solo sale si de verdad se escribió algún enlace.** Con permiso
              pero sin un solo currículum subido, la columna va vacía y este párrafo avisaba
              igual de un riesgo que no existía: quien recibiera la hoja la trataría como
              confidencial sin motivo, y un aviso que aparece donde no toca es un aviso que
              se deja de leer donde sí toca.
            */
            linea = anotar(hoja, pinceles, linea, avisoDeLosEnlaces(enlaces));
            if (enlaces.size() < conCv) {
                linea = anotar(hoja, pinceles, linea,
                        (conCv - enlaces.size()) + " de los " + conCv + " currículums se "
                                + "quedaron sin enlace: el archivo ya no está en el almacén, o "
                                + "no se pudo firmar. El nombre sí va escrito.");
            }
        } else if (conCv > 0) {
            linea = anotar(hoja, pinceles, linea,
                    "Ninguno de los " + conCv + " currículums pudo firmarse: no hay enlaces "
                            + "que abrir en la columna CV, solo el nombre del archivo.");
        }

        if (!ajenas.isEmpty()) {
            anotar(hoja, pinceles, linea,
                    "Fuera del volcado: " + ajenas.size() + " postulaciones que no son de esta "
                            + "vacante o ya no se alcanzan (ids " + idsDichos(ajenas) + ").");
        }
    }

    /**
     * El aviso entero de la columna CV, plazo incluido.
     *
     * <p>⚠️ <b>Se ramifica por el TIPO de plazo, no comparando su texto.</b> Hay tres casos
     * en que la coletilla de siempre —«quien reciba la hoja dentro de ese plazo puede
     * abrirlos»— deja de describir nada: enlaces ya caducados, enlaces sin fecha conocida, y
     * enlaces a los que les queda menos de un minuto. La primera versión de esto comparaba el
     * plazo con un texto constante y trataba solo uno de los tres, así que los otros dos
     * salían con una frase que se contradecía a sí misma. Con el tipo delante, añadir un caso
     * nuevo al plazo obliga a decidir qué se le dice a quien lee la hoja.
     */
    private String avisoDeLosEnlaces(Map<Long, EnlaceArchivo> enlaces) {
        Plazo plazo = cuantoDuranLosEnlaces(enlaces);
        return switch (plazo.tipo()) {
            case YA_CADUCADO -> "Los enlaces de la columna CV nacieron caducados y no van a "
                    + "abrir: algo pasa con la hora del servidor o con el almacén. Vuelve a "
                    + "descargar la hoja, y si sigue igual avísalo: los nombres de archivo sí "
                    + "son buenos.";
            case DESCONOCIDO -> "Los enlaces de la columna CV abren el currículum SIN pedir "
                    + "sesión ni permiso, y no se sabe cuándo dejan de hacerlo: el almacén no "
                    + "devolvió su caducidad. Da por hecho que pueden abrir durante horas y no "
                    + "reenvíes la hoja a quien no deba verlos.";
            case INMINENTE -> "Los enlaces de la columna CV caducan en menos de un minuto: a "
                    + "efectos prácticos ya no abren. Vuelve a descargar la hoja si los "
                    + "necesitas; los nombres de archivo sí son buenos.";
            case MEDIBLE -> "Los enlaces de la columna CV abren el currículum SIN pedir sesión "
                    + "ni permiso, y caducan " + plazo.dicho() + ". Quien reciba la hoja dentro "
                    + "de ese plazo puede abrirlos: no la reenvíes a quien no deba verlos.";
        };
    }

    /**
     * Lo que se sabe del plazo, y de qué clase es.
     *
     * <p>{@code dicho} solo significa algo cuando el tipo es {@link Tipo#MEDIBLE}; en los
     * demás la frase entera la pone {@link #avisoDeLosEnlaces}, porque lo que hay que decir
     * cambia, no solo la cifra.
     */
    private record Plazo(Tipo tipo, String dicho) {
        enum Tipo { MEDIBLE, INMINENTE, YA_CADUCADO, DESCONOCIDO }

        /*
          ⚠️ Un MEDIBLE sin nada que decir escribiría «…y caducan .» en la hoja. El campo solo
          significa algo en uno de los cuatro estados, y un record así es la misma forma que
          ya permitió el fallo de ramificar comparando textos: se ata aquí, donde no se puede
          esquivar, en vez de confiar en que nadie lo construya mal.
        */
        Plazo {
            if ((tipo == Tipo.MEDIBLE) == (dicho == null || dicho.isBlank())) {
                throw new IllegalArgumentException(
                        "Un plazo MEDIBLE tiene que decir cuánto dura, y uno que no lo es no "
                                + "puede traer un plazo: " + tipo + " / «"
                                + (dicho == null ? "" : dicho) + "»");
            }
        }

        static Plazo de(String dicho) {
            return new Plazo(Tipo.MEDIBLE, dicho);
        }

        static Plazo sin(Tipo tipo) {
            return new Plazo(tipo, "");
        }
    }

    /**
     * Lo que se le perdona al plazo antes de redondear hacia abajo: cinco minutos.
     *
     * <p>Existe porque el almacén sella la caducidad al firmar y esta cuenta se hace después
     * de escribir la hoja entera: ocho horas configuradas llegan aquí como 7 h 59 min y pico,
     * y sin holgura el redondeo hacia abajo las anunciaría como siete.
     *
     * <p>⚠️ <b>Es un número FIJO y pequeño, no una fracción de la unidad</b>, y solo se aplica
     * a las horas y a los días. Las dos cosas se aprendieron rompiéndolas:
     *
     * <ul>
     *   <li>Como fracción del tramo eran 30 s sobre un paso de 60 en los minutos —media
     *       unidad, o sea redondeo al más cercano disfrazado: minuto y medio se anunciaba
     *       como «a los 2 minutos»—. Y en los días eran dos horas, que se comían la frontera.
     *   <li><b>En el tramo de los minutos no se aplica ninguna.</b> La holgura existe para
     *       recuperar un número redondo que se configuró en HORAS; por debajo de la hora no
     *       hay ningún número redondo que recuperar, y lo que importa es no exagerar. Ahí el
     *       plazo se trunca, y «a los 59 minutos» sobre 59 min 59 s es verdad.
     * </ul>
     *
     * <p>⚠️ <b>Esta holgura es exactamente lo que el aviso puede pasarse por arriba</b>, y no
     * hay forma de que sea cero sin volver a decir «siete horas» donde hay ocho. Está acotada
     * aquí, dicha en el javadoc, y sujeta por una prueba que barre los bordes.
     */
    private static final long HOLGURA = 300;

    /** A partir de dos días, las horas dejan de decir nada: «unas 720 horas» no se lee. */
    private static final long HORAS_ANTES_DE_HABLAR_DE_DIAS = 48;

    /**
     * Cuánto dura el enlace más corto de los que se escribieron.
     *
     * <p>⚠️ <b>Sale del propio enlace y no de un número escrito aquí.</b> El plazo es una
     * propiedad ({@code horas-enlace-volcado}) que existe precisamente para poder bajarla sin
     * tocar código; con un «8 horas» literal, bajarla a dos dejaba esta hoja prometiendo un
     * plazo que ya no era el suyo — y ninguna prueba se enteraba, porque también tenían el
     * ocho escrito a mano.
     *
     * <p>El más corto y no el más largo: es el primero que deja de abrir, y anunciar el más
     * largo deja media hoja muerta sin avisar.
     *
     * <p>⚠️ <b>Se cuenta en SEGUNDOS y se redondea HACIA ABAJO</b>, con la holgura de
     * {@link #HOLGURA}. Las dos mitades importan y cada una arregla un fallo distinto:
     *
     * <ul>
     *   <li><b>Segundos y no minutos ya truncados.</b> Truncar arrastra el error al tramo de
     *       abajo: con {@code toHours()} ocho horas salían como siete, y redondeando sobre
     *       minutos truncados una hora justa salía como «a los 59 minutos».
     *   <li><b>Hacia abajo y no al más cercano.</b> Redondear al más cercano se pasa por
     *       arriba hasta media unidad —1 h 30 min se anunciaban como «unas 2 horas»—, y un
     *       aviso de caducidad que promete más tiempo del que hay es el único que hace daño:
     *       quien lo lea descubrirá que el enlace no abre cuando ya contaba con él.
     * </ul>
     */
    private Plazo cuantoDuranLosEnlaces(Map<Long, EnlaceArchivo> enlaces) {
        List<Instant> caducidades = enlaces.values().stream()
                .map(EnlaceArchivo::expiraEn)
                .toList();
        /*
          ⚠️ Una sola caducidad desconocida invalida la frase entera. Saltársela y anunciar la
          del vecino promete un plazo sobre un enlace del que no se sabe nada, que es la única
          forma de equivocarse que importa aquí. Hoy el almacén siempre la sella, así que esto
          es la red y no el caso normal.
        */
        if (caducidades.isEmpty() || caducidades.stream().anyMatch(Objects::isNull)) {
            return Plazo.sin(Plazo.Tipo.DESCONOCIDO);
        }
        Instant primeroEnMorir = caducidades.stream().min(Comparator.naturalOrder()).orElseThrow();
        long segundos = Duration.between(Instant.now(reloj), primeroEnMorir).toSeconds();
        if (segundos <= 0) {
            /*
              Pasa con el reloj torcido o con una firma más lenta que su propia vida. Anunciar
              un plazo sobre enlaces que ya no abren es la peor de las salidas: quien lea la
              hoja culpará al enlace de su propio retraso.
            */
            return Plazo.sin(Plazo.Tipo.YA_CADUCADO);
        }
        if (segundos < 60) {
            return Plazo.sin(Plazo.Tipo.INMINENTE);
        }
        // Sin holgura: por debajo de la hora no hay número redondo que recuperar.
        long minutos = segundos / 60;
        if (minutos < 60) {
            // «a los 1 minuto» no lo dice nadie.
            return Plazo.de(minutos == 1
                    ? "dentro de un minuto"
                    : "a los " + minutos + " minutos de generado este archivo");
        }
        long horas = (segundos + HOLGURA) / 3600;
        /*
          La frontera se mira sobre las horas YA contadas y no sobre los segundos crudos: si
          no, un plazo que se va a escribir como «48 horas» se queda en el tramo de las horas
          por unos segundos y sale como tal, que es justo lo que este corte evita.
        */
        if (horas >= HORAS_ANTES_DE_HABLAR_DE_DIAS) {
            return Plazo.de("unos " + ((segundos + HOLGURA) / 86400)
                    + " días después de generado este archivo");
        }
        // «unas 1 hora» tampoco.
        return Plazo.de(horas == 1
                ? "una hora después de generado este archivo"
                : "unas " + horas + " horas después de generado este archivo");
    }

    /**
     * Cuántos ids se nombran y cuántos se cuentan.
     *
     * <p>Una lista de miles no la lee nadie, y es además lo que mantiene la línea muy por
     * debajo del tope de una celda: acotarla aquí es mejor que recortarla al final, porque un
     * recorte por tamaño cortaría a mitad de un número — un id partido es peor que un id que
     * falta, porque parece un id.
     */
    private static final int IDS_QUE_SE_NOMBRAN = 50;

    /**
     * Lo que cabe del filtro que mandó el cliente.
     *
     * <p>⚠️ <b>Se acota AQUÍ y no al escribir la línea.</b> {@code filtroDescrito} llega de
     * fuera y no tiene tope, y la frase del pie lleva detrás la fecha y el recuento. Recortar
     * la línea ya compuesta dejaba el filtro entero y <b>se comía justamente eso</b>: la hoja
     * perdía de qué día es y cuántos candidatos trae, que es lo que la hace legible dentro de
     * un mes. Acotado aquí, lo que se pierde es la cola de un filtro que ya nadie leía.
     */
    private static final int FILTRO_QUE_CABE = 2000;

    private String filtroDicho(String filtroDescrito) {
        if (filtroDescrito == null || filtroDescrito.isBlank()) {
            return "sin filtro: la tanda tal como se seleccionó";
        }
        String limpio = filtroDescrito.trim();
        return limpio.length() <= FILTRO_QUE_CABE
                ? limpio
                // Por `recortarA` y no por `substring` a secas: cortar en seco parte un emoji
                // por la mitad y deja un «?» huérfano justo donde ya se avisa del recorte.
                : recortarA(limpio, FILTRO_QUE_CABE) + "… (filtro recortado)";
    }

    private String idsDichos(List<Long> ajenas) {
        String primeros = ajenas.stream().limit(IDS_QUE_SE_NOMBRAN)
                .map(String::valueOf).collect(Collectors.joining(", "));
        return ajenas.size() <= IDS_QUE_SE_NOMBRAN
                ? primeros
                : primeros + " y " + (ajenas.size() - IDS_QUE_SE_NOMBRAN) + " más";
    }

    /**
     * Una línea del pie.
     *
     * <p>⚠️ <b>Pasa por {@link #loQueCabe} como cualquier otra celda</b>, y es la última red:
     * lo que llega de fuera ya viene acotado antes de componer la frase —ver
     * {@link #filtroDicho} y {@link #idsDichos}—, porque recortar la línea YA COMPUESTA se
     * come lo que va detrás del texto largo, que aquí es la fecha y el recuento.
     */
    private int anotar(Sheet hoja, Pinceles pinceles, int linea, String texto) {
        Row f = hoja.createRow(linea);
        Cell celda = f.createCell(0);
        celda.setCellValue(loQueCabe(texto));
        celda.setCellStyle(pinceles.pie);
        return linea + 1;
    }

    // ========================================================================
    // Celdas
    // ========================================================================

    private void encabezar(Sheet hoja, Pinceles pinceles, List<String> titulos, int[] anchos) {
        Row cabecera = hoja.createRow(0);
        for (int i = 0; i < titulos.size(); i++) {
            Cell celda = cabecera.createCell(i);
            // ⚠️ También aquí: el nombre de un criterio es `text` en la base y lo escribe
            // quien redacta la rúbrica, así que una cabecera puede pasarse del tope igual
            // que una celda. Sin esto, un solo criterio mal escrito tumba la descarga entera.
            celda.setCellValue(loQueCabe(titulos.get(i)));
            celda.setCellStyle(pinceles.cabecera);
            hoja.setColumnWidth(i, anchos[i] * 256);
        }
        hoja.createFreezePane(0, 1);
    }

    private void texto(Row fila, int columna, String valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(loQueCabe(valor));
        celda.setCellStyle(pinceles.normal);
    }

    /** Lo que puede traer un párrafo entero: se envuelve y se alinea arriba. */
    private void largo(Row fila, int columna, String valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(loQueCabe(valor));
        celda.setCellStyle(pinceles.envuelto);
    }

    /**
     * El tope duro de una celda de Excel: 32.767 caracteres.
     *
     * <p>No es un número nuestro, lo pone el formato, y POI no recorta: lanza
     * {@code IllegalArgumentException}. Sin esta guarda, <b>un solo candidato</b> con
     * explicaciones largas dejaba sin archivo a los otros setenta y nueve, y con un 500 que no
     * decía por qué. Y no es rebuscado: la justificación detallada junta la explicación de
     * cada criterio, una prueba puede tener doce, y el prompt con el que la IA las escribe es
     * editable desde el panel.
     */
    private static final int TOPE_DE_CELDA = 32767;

    /** Un aviso que se ve, y no un recorte mudo a mitad de una frase. */
    private static final String RECORTADO = "\n\n[…] Texto recortado: no cabe entero en una "
            + "celda de Excel. Está completo en la ficha del candidato.";

    private String loQueCabe(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        return limpio.length() <= TOPE_DE_CELDA
                ? limpio
                : recortarA(limpio, TOPE_DE_CELDA - RECORTADO.length()) + RECORTADO;
    }

    /**
     * Las primeras {@code cuantas} unidades, sin partir un carácter por la mitad.
     *
     * <p>⚠️ Un emoji ocupa DOS unidades y el corte puede caer justo en medio. La mitad suelta
     * no revienta nada —POI la cambia por un «?»— pero deja un signo de interrogación
     * huérfano donde el texto ya estaba recortado, que es exactamente el sitio donde uno se
     * pregunta si falta algo. Se retrocede una unidad y se corta antes del par.
     *
     * <p>Está aparte para que la use <b>todo</b> el que recorte. Cuando esta regla vivía
     * dentro de {@code loQueCabe}, el recorte del filtro del pie —que es otro sitio y otro
     * tope— seguía partiendo emojis, y su prueba nunca lo vio porque usaba texto ASCII.
     */
    private String recortarA(String texto, int cuantas) {
        int corte = cuantas;
        if (corte > 0 && Character.isHighSurrogate(texto.charAt(corte - 1))) {
            corte--;
        }
        return texto.substring(0, corte);
    }

    /**
     * Una cuenta, o nada.
     *
     * <p>Acepta {@code null} justamente porque un cero y una ausencia se leen igual en una
     * hoja de cálculo y no significan lo mismo.
     */
    private void numero(Row fila, int columna, Integer valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        if (valor != null) {
            celda.setCellValue(valor);
        }
        celda.setCellStyle(pinceles.normal);
    }

    /** Un número que puede faltar y cuya ausencia no es una nota sin poner: se deja en blanco. */
    private void cifra(Row fila, int columna, BigDecimal valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        if (valor != null) {
            celda.setCellValue(valor.doubleValue());
        }
        celda.setCellStyle(pinceles.normal);
    }

    /** Una nota de etapa: o el número, o por qué no lo hay. Nunca en blanco y nunca un cero. */
    private void nota(Row fila, int columna, BigDecimal valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        if (valor == null) {
            celda.setCellValue(SIN_NOTA);
        } else {
            celda.setCellValue(valor.doubleValue());
        }
        celda.setCellStyle(pinceles.normal);
    }

    /**
     * Una cifra del ponderado, o por qué no está — que <b>no</b> es «rúbrica incompleta».
     *
     * <p>Ese es el motivo de las notas de criterio y aquí sería mentira: la rúbrica puede
     * estar entera y aun así no haber cifra, porque lo que falta es una nota de <b>etapa</b>.
     */
    private void cifraOMotivo(Row fila, int columna, BigDecimal valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        if (valor == null) {
            celda.setCellValue(SIN_ETAPA);
        } else {
            celda.setCellValue(valor.doubleValue());
        }
        celda.setCellStyle(pinceles.normal);
    }

    // ========================================================================
    // Lo que se escribe en cada celda
    // ========================================================================

    /**
     * La dirección a la que se le puede escribir: la del currículum, no la de la cuenta.
     *
     * <p>La de la cuenta se la inventó el cargador en las convocatorias que se subieron en
     * bloque —un dominio que no existe—. La de verdad la sacó el agente del propio currículum.
     */
    private String correoDelCv(FilaRanking fila) {
        DatosCandidato datos = fila.datos();
        return datos == null || datos.email() == null ? "" : datos.email().trim();
    }

    /**
     * El teléfono tal cual venía escrito en el currículum.
     *
     * <p>No se normaliza: llega en media docena de formatos y unificarlos a ciegas es la
     * clase de arreglo que convierte un número raro pero correcto en uno que no existe.
     */
    private String telefonoDelCv(FilaRanking fila) {
        DatosCandidato datos = fila.datos();
        return datos == null || datos.telefono() == null ? "" : datos.telefono().trim();
    }

    // ========================================================================
    // Los estilos, creados una vez por libro
    // ========================================================================

    /** POI cobra caro cada estilo repetido: se crean una vez y se reparten por referencia. */
    private static final class Pinceles {
        private final CellStyle cabecera;
        private final CellStyle normal;
        private final CellStyle envuelto;
        private final CellStyle enlace;
        private final CellStyle pie;
        private final CreationHelper enlaces;

        private Pinceles(XSSFWorkbook libro) {
            this.enlaces = libro.getCreationHelper();

            XSSFFont blanca = libro.createFont();
            blanca.setBold(true);
            blanca.setColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));

            XSSFCellStyle deCabecera = libro.createCellStyle();
            deCabecera.setFillForegroundColor(new XSSFColor(INDIGO, null));
            deCabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            deCabecera.setFont(blanca);
            deCabecera.setVerticalAlignment(VerticalAlignment.CENTER);
            deCabecera.setWrapText(true);
            this.cabecera = deCabecera;

            CellStyle corriente = libro.createCellStyle();
            corriente.setVerticalAlignment(VerticalAlignment.TOP);
            this.normal = corriente;

            CellStyle conSaltos = libro.createCellStyle();
            conSaltos.setWrapText(true);
            conSaltos.setVerticalAlignment(VerticalAlignment.TOP);
            this.envuelto = conSaltos;

            // El azul subrayado de siempre: un enlace que no lo parece no se pulsa.
            XSSFFont azul = libro.createFont();
            azul.setUnderline(XSSFFont.U_SINGLE);
            azul.setColor(new XSSFColor(new byte[]{0x00, 0x56, (byte) 0xB3}, null));
            CellStyle deEnlace = libro.createCellStyle();
            deEnlace.setFont(azul);
            deEnlace.setVerticalAlignment(VerticalAlignment.TOP);
            this.enlace = deEnlace;

            XSSFFont cursiva = libro.createFont();
            cursiva.setItalic(true);
            CellStyle alPie = libro.createCellStyle();
            alPie.setFont(cursiva);
            alPie.setVerticalAlignment(VerticalAlignment.TOP);
            this.pie = alPie;
        }
    }
}
