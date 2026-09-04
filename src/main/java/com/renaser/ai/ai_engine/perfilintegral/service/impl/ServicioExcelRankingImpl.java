package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.ExcelDeRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.PedidoExcelRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DatosCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.FilaRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.Ponderado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.NotaCriterioResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioExcelRanking;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPerfilIntegralPanel;
import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPrueba;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El volcado del ranking a un .xlsx.
 *
 * <p><b>De dónde salen los datos.</b> De la misma tanda que pinta la pantalla
 * ({@link ServicioPerfilIntegralPanel#ranking}) y, en la prueba del puesto, de la rúbrica que
 * ya sabe leer {@link ServicioCalificacionPrueba#verNotas}. No se toca ningún repositorio:
 * así el filtro por organización y el alcance del permiso los sigue aplicando el mismo
 * guardián de siempre, y este volcado no puede enseñar una fila que la pantalla no enseñaría.
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

    private static final String PERFIL_INTEGRAL = "PERFIL_INTEGRAL";
    private static final String PRUEBA_PUESTO = "PRUEBA_PUESTO";

    /**
     * Lo que dice una celda de nota sin nota. Nunca en blanco y nunca un cero: un cero es un
     * juicio que nadie ha hecho, y un blanco en una columna de números se lee como un cero.
     */
    private static final String SIN_NOTA = "rúbrica incompleta";

    /**
     * El permiso que pide leer la rúbrica de la prueba, criterio a criterio.
     *
     * <p>⚠️ <b>Tiene que ser el MISMO que pide {@code ServicioCalificacionPrueba.verNotas}</b>,
     * que es a quien llama la hoja de abajo. Cuando eran distintos —aquel pedía
     * {@code ajustar_nota}— este Excel le decía a Responsable de Área que no podía ver un
     * detalle que la pantalla sí le enseñaba.
     */
    private static final String PERMISO_DETALLE = "abrir_ficha_candidato";

    /** El permiso bajo el que viaja la pretensión salarial (V36). */
    private static final String PERMISO_PRETENSION = "ver_pretension";

    /** El índigo de la cabecera, el mismo que el resto de los volcados: 4338CA. */
    private static final byte[] INDIGO = {0x43, 0x38, (byte) 0xCA};

    private final ServicioPerfilIntegralPanel tandas;
    private final ServicioCalificacionPrueba notasDeLaPrueba;
    private final Clock reloj;

    @Autowired
    public ServicioExcelRankingImpl(ServicioPerfilIntegralPanel tandas,
                                    ServicioCalificacionPrueba notasDeLaPrueba) {
        this(tandas, notasDeLaPrueba, Clock.systemDefaultZone());
    }

    // El reloj entra por el constructor para que la fecha del nombre del archivo se pueda
    // comprobar sin quemar el día de hoy en una prueba: las fechas quemadas caducan.
    ServicioExcelRankingImpl(ServicioPerfilIntegralPanel tandas,
                             ServicioCalificacionPrueba notasDeLaPrueba, Clock reloj) {
        this.tandas = tandas;
        this.notasDeLaPrueba = notasDeLaPrueba;
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

        byte[] contenido = escribir(quien, etapa, enElOrdenPedido, ajenas, pedido.filtroDescrito());
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
                            List<Long> ajenas, String filtroDescrito) {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Pinceles pinceles = new Pinceles(libro);
            boolean vePretension = quien.tiene(PERMISO_PRETENSION);

            Sheet resumen = libro.createSheet("Resumen");
            Sheet detalle = libro.createSheet("Detalle");

            if (PERFIL_INTEGRAL.equals(etapa)) {
                resumenDelPerfil(resumen, pinceles, filas, vePretension);
                detalleDelPerfil(detalle, pinceles, filas);
            } else {
                resumenDeLaPrueba(resumen, pinceles, filas, vePretension);
                detalleDeLaPrueba(detalle, pinceles, quien, filas);
            }
            pie(resumen, pinceles, filas, ajenas, filtroDescrito, vePretension);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            // Escribir en memoria no falla por E/S; si falla, no hay nada que reintentar y
            // callarlo dejaría un archivo de cero bytes que el navegador baja igual.
            throw new UncheckedIOException("No se pudo armar el Excel del ranking", e);
        }
    }

    // ========================================================================
    // PERFIL_INTEGRAL
    // ========================================================================

    private void resumenDelPerfil(Sheet hoja, Pinceles pinceles, List<FilaRanking> filas,
                                  boolean vePretension) {
        encabezar(hoja, pinceles,
                List.of("#", "Candidato", "Correo", "Teléfono", "Ciudad",
                        cabeceraDePretension(vePretension), "Nota del perfil", "Pasada",
                        "Adecuación", "Potencial", "Confianza de evidencia",
                        "Grupo de prioridad", "Alto rendimiento", "Riesgos", "Alertas",
                        "Resumen", "Fortalezas",
                        // Lo ya rendido, al final y no junto a la nota del perfil: las cuatro
                        // se leen juntas, y añadirlas en medio habría corrido de sitio todas
                        // las columnas que ya existían.
                        // Sin repetir el perfil integral: en esta hoja «Nota del perfil»
                        // ya es esa misma cifra.
                        "Currículum /100", "Prueba /100", "Ponderado /100"),
                new int[]{5, 34, 38, 15, 26, 24, 15, 10, 13, 12, 21, 19, 17, 9, 9, 90, 12,
                        16, 13, 16});

        int numero = 1;
        for (FilaRanking fila : filas) {
            Row f = hoja.createRow(numero);
            int c = 0;
            // El «#» es el puesto EN EL RANKING, el mismo que se ve en la mesa, y no la
            // posición en esta hoja. Dos razones: la hoja ya numera sus propias filas por el
            // margen, así que una columna que repita eso no añade nada; y con la posición de
            // la hoja, la misma descarga decía «#2 Camila» en pantalla y «#1 Camila» en el
            // archivo, con la misma cabecera. El orden de las filas sí es el pedido.
            numero(f, c++, fila.puesto(), pinceles);
            texto(f, c++, fila.candidato(), pinceles);
            texto(f, c++, correoDelCv(fila), pinceles);
            texto(f, c++, telefonoDelCv(fila), pinceles);
            texto(f, c++, fila.ciudad(), pinceles);
            texto(f, c++, pretension(fila), pinceles);
            nota(f, c++, fila.notaEtapa(), pinceles);
            texto(f, c++, fila.pasada(), pinceles);
            // Adecuación, potencial y compañía se dejan en blanco si faltan, y no dicen
            // «rúbrica incompleta»: que falten significa otra cosa —que la IA todavía no ha
            // hecho el retrato—, y la celda de al lado ya avisa de que no hay nota.
            cifra(f, c++, fila.adecuacion(), pinceles);
            cifra(f, c++, fila.potencial(), pinceles);
            cifra(f, c++, fila.confianzaEvidencia(), pinceles);
            texto(f, c++, fila.grupoPrioridad(), pinceles);
            cifra(f, c++, fila.altoRendimiento(), pinceles);
            // Riesgos, alertas y fortalezas son CUENTAS y llegan como int, así que sin
            // retrato valen 0 —no null—. Escribir ese 0 diría «no se le encontró ningún
            // riesgo», que es un juicio que nadie ha emitido: lo que pasa es que la IA aún
            // no ha mirado. Se dejan en blanco por la misma razón que adecuación y
            // potencial, y así las cinco columnas del retrato faltan juntas o están juntas.
            boolean hayRetrato = tieneRetrato(fila);
            numero(f, c++, hayRetrato ? fila.riesgosCriticos() : null, pinceles);
            numero(f, c++, hayRetrato ? fila.alertas() : null, pinceles);
            largo(f, c++, fila.resumen(), pinceles);
            numero(f, c++, hayRetrato ? fila.fortalezas() : null, pinceles);
            ponderado(f, c, fila, pinceles, false, true);
            numero++;
        }
    }

    private void detalleDelPerfil(Sheet hoja, Pinceles pinceles, List<FilaRanking> filas) {
        // Sin columna «Puso»: es una decisión tomada, no un olvido. Lo que respalda la nota
        // es la explicación, y el motivo del ajuste ya dice cuándo la corrigió una persona.
        encabezar(hoja, pinceles,
                List.of("Candidato", "Criterio", "Puntaje", "Máximo", "Peso", "Confianza",
                        "Motivo del ajuste", "Explicación"),
                new int[]{34, 42, 9, 9, 8, 11, 44, 110});

        int linea = 1;
        for (FilaRanking fila : filas) {
            List<NotaCriterioResponse> notas = fila.notasCriterio() == null
                    ? List.of() : fila.notasCriterio();
            if (notas.isEmpty()) {
                // Callar aquí dejaría al candidato sin ninguna línea, y una hoja de detalle a
                // la que le faltan candidatos se lee como si esos no tuvieran nada que contar.
                linea = sinDetalle(hoja, pinceles, linea, fila.candidato(),
                        "Sin notas del currículum todavía (calificación: "
                                + (fila.estadoCalificacion() == null
                                        ? "sin empezar" : fila.estadoCalificacion()) + ")");
                continue;
            }
            for (NotaCriterioResponse nota : notas) {
                Row f = hoja.createRow(linea++);
                int c = 0;
                texto(f, c++, fila.candidato(), pinceles);
                texto(f, c++, nota.criterio(), pinceles);
                nota(f, c++, nota.puntaje(), pinceles);
                cifra(f, c++, nota.maximo(), pinceles);
                cifra(f, c++, nota.peso(), pinceles);
                // De 0 a 100, como el puntaje. Quien la lea no tiene que multiplicar por nada.
                cifra(f, c++, nota.confianza(), pinceles);
                largo(f, c++, nota.motivoAjuste(), pinceles);
                largo(f, c, nota.explicacion(), pinceles);
            }
        }
    }

    // ========================================================================
    // PRUEBA_PUESTO
    // ========================================================================

    private void resumenDeLaPrueba(Sheet hoja, Pinceles pinceles, List<FilaRanking> filas,
                                   boolean vePretension) {
        encabezar(hoja, pinceles,
                List.of("#", "Candidato", "Correo", "Teléfono", "Ciudad",
                        cabeceraDePretension(vePretension), "Nota /100",
                        // Sin repetir la prueba: en esta hoja «Nota /100» ya ES esa cifra, y
                        // dos columnas con el mismo número y distinto nombre harían dudar de
                        // cuál es cuál.
                        "Currículum /100", "Perfil integral /100", "Ponderado /100"),
                new int[]{5, 34, 38, 15, 26, 24, 11, 16, 21, 16});

        int numero = 1;
        for (FilaRanking fila : filas) {
            Row f = hoja.createRow(numero);
            int c = 0;
            // El puesto del ranking, igual que en la otra hoja y que en la mesa. Ver el
            // comentario del Resumen del perfil: la hoja ya numera sus filas por el margen.
            numero(f, c++, fila.puesto(), pinceles);
            texto(f, c++, fila.candidato(), pinceles);
            texto(f, c++, correoDelCv(fila), pinceles);
            texto(f, c++, telefonoDelCv(fila), pinceles);
            texto(f, c++, fila.ciudad(), pinceles);
            texto(f, c++, pretension(fila), pinceles);
            nota(f, c++, fila.notaEtapa(), pinceles);
            ponderado(f, c, fila, pinceles, true, false);
            numero++;
        }
    }

    private void detalleDeLaPrueba(Sheet hoja, Pinceles pinceles, ContextoUsuario quien,
                                   List<FilaRanking> filas) {
        encabezar(hoja, pinceles,
                List.of("Candidato", "Criterio", "Puntaje", "Máximo", "Explicación"),
                new int[]{34, 42, 9, 9, 110});

        // La rúbrica pide su propio permiso y este volcado se abre con «ver_embudo»: hay
        // roles que tienen el segundo y no el primero. Se pregunta una vez y se dice en una
        // línea,
        // en vez de dejar que la primera llamada tumbe el archivo entero con un 403 —o, peor,
        // tragarse ochenta excepciones iguales y devolver una hoja muda.
        if (!quien.tiene(PERMISO_DETALLE)) {
            sinDetalle(hoja, pinceles, 1, "—",
                    "El detalle de la rúbrica pide el permiso «" + PERMISO_DETALLE
                            + "», que tu rol no tiene. El Resumen sí va completo.");
            return;
        }

        int linea = 1;
        for (FilaRanking fila : filas) {
            List<DtosCalificacionPrueba.NotaCriterioResponse> notas;
            try {
                notas = notasDeLaPrueba.verNotas(quien, fila.postulacionId());
            } catch (ResourceNotFoundException noHayRubrica) {
                // El ranking de la etapa trae a TODA la tanda, también a quien todavía no
                // tiene prueba: para ese, verNotas no devuelve vacío, revienta con un 404. Sin
                // esto, un solo candidato así deja sin Excel a los otros setenta y nueve.
                linea = sinDetalle(hoja, pinceles, linea, fila.candidato(),
                        porQueNoHayRubrica(noHayRubrica));
                continue;
            }
            if (notas == null || notas.isEmpty()) {
                // Pasa de verdad: la vacante que rinde el CUESTIONARIO_TECNICO no tiene
                // rúbrica —se califica pregunta a pregunta— y verNotas devuelve la lista
                // vacía. Sin esta línea el candidato desaparecería del Detalle sin más.
                linea = sinDetalle(hoja, pinceles, linea, fila.candidato(),
                        "Sin rúbrica que volcar: su etapa técnica no se puntúa por criterios "
                                + "(es lo que ocurre con el cuestionario técnico, que se "
                                + "califica pregunta a pregunta).");
                continue;
            }
            for (DtosCalificacionPrueba.NotaCriterioResponse nota : notas) {
                Row f = hoja.createRow(linea++);
                int c = 0;
                texto(f, c++, fila.candidato(), pinceles);
                // OJO: este record es el de prueba.dto y sus nombres NO son los del otro.
                // Aquí el criterio se llama nombre() y el máximo puntosMaximos().
                texto(f, c++, nota.nombre(), pinceles);
                nota(f, c++, nota.puntaje(), pinceles);
                cifra(f, c++, nota.puntosMaximos(), pinceles);
                largo(f, c, nota.explicacion(), pinceles);
            }
        }
    }

    /**
     * Por qué esa postulación no trae rúbrica, dicho sin adivinar.
     *
     * <p>El 404 de {@code verNotas} tiene dos causas distintas y solo el recurso que nombra
     * las separa: la prueba, cuando el candidato no la tiene todavía; la postulación, cuando
     * el alcance de {@code ajustar_nota} es más estrecho que el de {@code ver_embudo} con el
     * que se abrió este volcado. La segunda rama va redactada para seguir siendo cierta
     * aunque ese nombre cambie al otro lado: no afirma cuál de las dos fue.
     */
    private String porQueNoHayRubrica(ResourceNotFoundException noHay) {
        return "Prueba del puesto".equals(noHay.getResourceName())
                ? "Todavía no tiene prueba del puesto: no hay rúbrica que volcar."
                : "No se pudo leer su rúbrica: o no tiene prueba, o el alcance de tu permiso "
                        + "«" + PERMISO_DETALLE + "» no llega a esta postulación.";
    }

    /**
     * Una línea que dice por qué no hay detalle, en vez de dejar el hueco.
     *
     * <p>Va en la columna del criterio y no en la del puntaje a propósito: lo que falta es la
     * rúbrica entera, no una nota suelta, y una explicación en la columna «Puntaje» se
     * ordenaría junto a los números.
     */
    private int sinDetalle(Sheet hoja, Pinceles pinceles, int linea, String candidato,
                           String porque) {
        Row f = hoja.createRow(linea);
        texto(f, 0, candidato, pinceles);
        largo(f, 1, porque, pinceles);
        return linea + 1;
    }

    // ========================================================================
    // El pie del Resumen
    // ========================================================================

    private void pie(Sheet hoja, Pinceles pinceles, List<FilaRanking> filas, List<Long> ajenas,
                     String filtroDescrito, boolean vePretension) {
        int linea = hoja.getLastRowNum() + 2;

        String filtro = filtroDescrito == null || filtroDescrito.isBlank()
                ? "sin filtro: la tanda tal como se seleccionó" : filtroDescrito.trim();
        linea = anotar(hoja, pinceles, linea,
                "Filtro aplicado: " + filtro + " · Generado el " + LocalDate.now(reloj)
                        + " · " + filas.size() + " candidatos");

        // La columna de pretensión vacía se puede leer de dos formas opuestas, y solo una es
        // verdad cada vez. Sin el permiso el dato ni se consultó; con él, vacío significa que
        // nadie lo declaró. Decir la que no es sería peor que no decir nada.
        if (!vePretension) {
            linea = anotar(hoja, pinceles, linea,
                    "La columna Pretensión va vacía porque tu rol no tiene el permiso «"
                            + PERMISO_PRETENSION + "»: el dato no se consultó. NO significa "
                            + "que estos candidatos no declararan sueldo.");
        } else if (filas.stream().allMatch(f -> pretension(f).isEmpty())) {
            linea = anotar(hoja, pinceles, linea,
                    "Ninguno de los " + filas.size() + " candidatos volcados declaró "
                            + "pretensión salarial.");
        }

        if (!ajenas.isEmpty()) {
            anotar(hoja, pinceles, linea,
                    "Fuera del volcado: " + ajenas.size() + " postulaciones que no son de esta "
                            + "vacante o ya no se alcanzan (ids " + ajenas.stream()
                                    .map(String::valueOf).collect(Collectors.joining(", "))
                            + ").");
        }
    }

    private int anotar(Sheet hoja, Pinceles pinceles, int linea, String texto) {
        Row f = hoja.createRow(linea);
        Cell celda = f.createCell(0);
        celda.setCellValue(texto);
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
            celda.setCellValue(titulos.get(i));
            celda.setCellStyle(pinceles.cabecera);
            hoja.setColumnWidth(i, anchos[i] * 256);
        }
        hoja.createFreezePane(0, 1);
    }

    private void texto(Row fila, int columna, String valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor == null ? "" : valor.trim());
        celda.setCellStyle(pinceles.normal);
    }

    /** Lo que puede traer un párrafo entero: se envuelve y se alinea arriba. */
    private void largo(Row fila, int columna, String valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor == null ? "" : valor.trim());
        celda.setCellStyle(pinceles.envuelto);
    }

    /**
     * Una cuenta, o nada.
     *
     * <p>Acepta {@code null} justamente porque un cero y una ausencia se leen igual en una
     * hoja de cálculo y no significan lo mismo: «no tiene riesgos» es una conclusión, y
     * «todavía no se ha mirado» es la falta de una.
     */
    private void numero(Row fila, int columna, Integer valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        if (valor != null) {
            celda.setCellValue(valor);
        }
        celda.setCellStyle(pinceles.normal);
    }

    /**
     * Si la IA llegó a hacerle el retrato del currículum a esta persona.
     *
     * <p>Se pregunta por adecuación y potencial porque son las dos cifras que el retrato
     * escribe siempre y que llegan como objeto —pueden ser null—, al revés que las cuentas.
     * Sin ellas no hay retrato, y entonces ninguna de las cinco columnas dice nada.
     */
    private boolean tieneRetrato(FilaRanking fila) {
        return fila.adecuacion() != null || fila.potencial() != null;
    }

    /** Un número que puede faltar y cuya ausencia no es una nota sin poner: se deja en blanco. */
    private void cifra(Row fila, int columna, BigDecimal valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        if (valor != null) {
            celda.setCellValue(valor.doubleValue());
        }
        celda.setCellStyle(pinceles.normal);
    }

    private void cifra(Row fila, int columna, Double valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        if (valor != null) {
            celda.setCellValue(valor);
        }
        celda.setCellStyle(pinceles.normal);
    }

    /** Una nota: o el número, o por qué no lo hay. Nunca en blanco y nunca un cero. */
    private void nota(Row fila, int columna, BigDecimal valor, Pinceles pinceles) {
        nota(fila, columna, valor == null ? null : valor.doubleValue(), pinceles);
    }

    private void nota(Row fila, int columna, Double valor, Pinceles pinceles) {
        Cell celda = fila.createCell(columna);
        if (valor == null) {
            celda.setCellValue(SIN_NOTA);
        } else {
            celda.setCellValue(valor);
        }
        celda.setCellStyle(pinceles.normal);
    }

    /**
     * Lo ya rendido: currículum, perfil integral, prueba, y el ponderado de los tres.
     *
     * <p>Se escriben juntas y en este orden en las dos hojas de Resumen, con una diferencia:
     * cada hoja se salta la cifra que su propia columna «Nota» ya enseña, para no poner dos
     * veces el mismo número con dos nombres distintos.
     *
     * <p><b>No hay columna del banco de preguntas</b>, y no es un olvido: esa nota no se
     * guarda suelta en ninguna parte —lo guardado es su mezcla con el currículum— y
     * despejarla restando da un número falso en dos casos reales. El Perfil Integral, que sí
     * es exacto, ya la contiene; con estas tres cifras el ponderado se rehace a mano.
     *
     * <p>Las cuatro pasan por {@link #nota}, así que una que falte dice por qué falta en vez
     * de quedarse en blanco — igual que cualquier otra nota de estas hojas.
     */
    private void ponderado(Row fila, int columna, FilaRanking datos, Pinceles pinceles,
                           boolean conElPerfil, boolean conLaPrueba) {
        Ponderado suyo = datos.ponderado();
        BigDecimal cv = suyo == null ? null : suyo.cv();
        BigDecimal perfil = suyo == null ? null : suyo.perfil();
        BigDecimal prueba = suyo == null ? null : suyo.prueba();
        BigDecimal sobre100 = suyo == null ? null : suyo.sobre100();

        int c = columna;
        nota(fila, c++, cv, pinceles);
        if (conElPerfil) {
            nota(fila, c++, perfil, pinceles);
        }
        if (conLaPrueba) {
            nota(fila, c++, prueba, pinceles);
        }
        nota(fila, c, sobre100, pinceles);
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

    /**
     * «S/ 3,000 – 4,500», «desde S/ 3,000», o vacío. Vacío lo explica el pie, no la celda.
     *
     * <p>Se escribe igual que en el panel —mismo símbolo, mismo separador de millares— porque
     * es la MISMA cifra: la hoja sale del panel y se reenvía, y era la menos legible de las
     * dos. Y el símbolo no se supone: sin moneda declarada van las cifras solas, que un
     * candidato pidiendo 3.000 dólares y leído como «S/ 3,000» es una llamada perdida.
     */
    private String pretension(FilaRanking fila) {
        BigDecimal min = fila.pretensionMin();
        BigDecimal max = fila.pretensionMax();
        if (min == null && max == null) {
            return "";
        }
        String simbolo = simboloDe(fila.pretensionMoneda());
        String con = simbolo.isEmpty() ? "" : simbolo + " ";
        if (min != null && max != null) {
            return con + cantidad(min) + " – " + cantidad(max);
        }
        return min != null ? "desde " + con + cantidad(min) : "hasta " + con + cantidad(max);
    }

    /** Los símbolos que el panel ya usa. Una moneda que no se conozca viaja con su código. */
    private static final Map<String, String> SIMBOLO_DE_MONEDA =
            Map.of("PEN", "S/", "USD", "US$");

    private String simboloDe(String moneda) {
        if (moneda == null || moneda.isBlank()) {
            return "";
        }
        return SIMBOLO_DE_MONEDA.getOrDefault(moneda.trim(), moneda.trim());
    }

    /**
     * La cifra con separador de millares, como la escribe el panel.
     *
     * <p>Locale fijo y no el del servidor: el separador de una hoja que se reenvía no puede
     * depender de en qué máquina se generó. {@code es-PE} agrupa con coma, que es lo que
     * devuelve el {@code Intl.NumberFormat} del panel para esta misma cifra.
     */
    private String cantidad(BigDecimal valor) {
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PE"))
                .format(valor.stripTrailingZeros());
    }

    /**
     * La cabecera de Pretensión dice si la columna está bajo llave.
     *
     * <p>Una columna vacía sin explicación se lee como «nadie pidió sueldo», y para un rol sin
     * el permiso eso es falso: el dato existe y no se consultó.
     */
    private String cabeceraDePretension(boolean vePretension) {
        return vePretension ? "Pretensión" : "Pretensión (sin permiso: no se consultó)";
    }

    // ========================================================================
    // Los estilos, creados una vez por libro
    // ========================================================================

    /** POI cobra caro cada estilo repetido: se crean una vez y se reparten por referencia. */
    private static final class Pinceles {
        private final CellStyle cabecera;
        private final CellStyle normal;
        private final CellStyle envuelto;
        private final CellStyle pie;

        private Pinceles(XSSFWorkbook libro) {
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

            XSSFFont cursiva = libro.createFont();
            cursiva.setItalic(true);
            CellStyle alPie = libro.createCellStyle();
            alPie.setFont(cursiva);
            alPie.setVerticalAlignment(VerticalAlignment.TOP);
            this.pie = alPie;
        }
    }
}
