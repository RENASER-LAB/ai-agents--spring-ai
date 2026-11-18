package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.comun.pdf.ColoresPdf;
import com.renaser.ai.ai_engine.comun.pdf.EstiloPdf;
import com.renaser.ai.ai_engine.comun.pdf.FlujoPdf;
import com.renaser.ai.ai_engine.comun.pdf.FlujoPdf.Renglon;
import com.renaser.ai.ai_engine.comun.pdf.LienzoPdf;
import com.renaser.ai.ai_engine.comun.pdf.RenglonesPdf;
import com.renaser.ai.ai_engine.comun.pdf.TipografiaPdf;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Anexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Dato;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Detalle;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EntregableAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Experiencia;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Recuento;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Senal;

import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.renaser.ai.ai_engine.informe.service.FormatoDelInforme.hay;

/**
 * El informe en PDF, con el diseño «Columna de noche» aprobado (maqueta del 16/11/2026).
 *
 * <p>La primera hoja lleva la columna oscura completa —nombre, anillo con la nota, datos clave y
 * enlaces— y las siguientes una franja oscura estrecha con el nombre en vertical. A la derecha, en
 * este orden: el resumen, «Qué se evaluó» con sus barras, los bloques, el porqué de cada nota y,
 * en hoja aparte, el anexo.
 *
 * <p><b>Nada se recorta</b>: un informe ocupa las hojas que necesite. Un punto, un criterio o una
 * pregunta no se parten entre dos hojas salvo que no quepan en una entera. «Qué se evaluó» y cada
 * bloque del resumen empiezan en la primera hoja —su título y al menos su primer punto— y siguen
 * en la siguiente si no caben (D-1, ver `resumen`). La nota de la etapa y la de cada
 * criterio aparecen una sola vez: en el anillo y junto a su barra.
 *
 * <p>Las medidas son las de la maqueta (794 × 1123 px) pasadas a puntos (× 0,75).
 */
@Component
public class PdfDelInforme {

    // ------------------------------------------------------------------ colores Northstar
    // Los de la paleta común viven en ColoresPdf: el brochure de la empresa los comparte.
    static final Color TINTA = ColoresPdf.TINTA;
    static final Color AZUL = ColoresPdf.AZUL;
    static final Color AZUL_OSCURO = ColoresPdf.AZUL_OSCURO;
    static final Color VERDE = ColoresPdf.VERDE;
    static final Color ROJO = ColoresPdf.ROJO;
    static final Color BRUMA = ColoresPdf.BRUMA;
    static final Color HALO = ColoresPdf.HALO;
    static final Color GRIS = ColoresPdf.GRIS;
    static final Color GRIS_MEDIO = ColoresPdf.GRIS_MEDIO;
    static final Color TEXTO2 = ColoresPdf.TEXTO2;
    static final Color FILETE = new Color(0xE7E7E7);
    static final Color NOCHE_TEXTO = new Color(0xF0F0F2);
    static final Color NOCHE_60 = new Color(0x979898);
    static final Color NOCHE_72 = new Color(0xB2B3B3);
    static final Color NOCHE_56 = new Color(0x8E8F8F);
    static final Color NOCHE_PISTA = new Color(0x2C2D2D);
    static final Color NOCHE_LINEA = new Color(0x232524);
    static final Color NOCHE_ANILLO = new Color(0x7FA0F0);
    static final Color NOCHE_ENLACE = new Color(0xC9D6F8);
    static final Color PUNTO_VERDE = new Color(0x3FBF9B);
    static final Color PUNTO_ROJO = new Color(0xF07A8A);

    // ------------------------------------------------------------------ geometría
    static final float COLUMNA = 183f;
    static final float FRANJA = 34f;
    static final float MARGEN = 25.5f;
    static final float ARRIBA = 24f;
    /** Donde acaba lo escrito, contado desde abajo; el filete del pie va más abajo, en FILETE_DEL_PIE. */
    static final float PIE = 31f;
    static final float FILETE_DEL_PIE = 26f;

    // ------------------------------------------------------------------ estilos
    private static final EstiloPdf CUERPO = EstiloPdf.texto(9.4f, TINTA);
    private static final EstiloPdf CUERPO_MEDIO = CUERPO.medio();
    private static final EstiloPdf CHICO = EstiloPdf.texto(7.9f, TEXTO2);
    private static final EstiloPdf CHICO_GRIS = EstiloPdf.texto(7.9f, GRIS);
    private static final EstiloPdf ETIQUETA = EstiloPdf.texto(7.9f, GRIS).espaciado(0.08f).enMayusculas();
    private static final EstiloPdf ETIQUETA_CHICA = EstiloPdf.texto(7.1f, GRIS).espaciado(0.08f).enMayusculas();
    private static final EstiloPdf VOZ_PIE = EstiloPdf.voz(7.1f, GRIS);
    private static final EstiloPdf TITULO_BLOQUE = EstiloPdf.texto(10.5f, TINTA).medio();

    private static final Map<String, String> GRUPOS = Map.of(
            "ALTA", "Prioridad alta",
            "POTENCIAL_CON_RIESGO", "Potencial con riesgo",
            "NO_PRIORIZADO", "No priorizado",
            "INCOMPATIBLE", "Incompatible");

    private static final Map<String, String> ORIGENES = Map.of(
            "CV", "CV", "EVALUACION", "Evaluación", "PRUEBA", "Prueba");

    /** El PDF del informe, ya armado. */
    public byte[] generar(Informe informe) {
        try (LienzoPdf lienzo = new LienzoPdf()) {
            lienzo.titulo(informe.titulo() + " · " + informe.candidato(), informe.empresa());
            Pagina pagina = new Pagina(informe);
            FlujoPdf flujo = new FlujoPdf(lienzo, pagina);
            resumen(flujo, informe);
            detalle(flujo, informe);
            anexo(flujo, informe);
            numerar(lienzo, informe);
            return lienzo.guardar();
        }
    }

    // ================================================================== la página

    /** La primera hoja con la columna oscura; las demás con la franja. Y el pie en todas. */
    private final class Pagina implements FlujoPdf.Geometria {
        private final Informe informe;

        Pagina(Informe informe) {
            this.informe = informe;
        }

        @Override
        public float x(int pagina) {
            return pagina == 1 ? COLUMNA + MARGEN : FRANJA + MARGEN + 4f;
        }

        @Override
        public float ancho(int pagina) {
            return LienzoPdf.ANCHO - x(pagina) - MARGEN;
        }

        @Override
        public float arriba(int pagina) {
            return ARRIBA;
        }

        @Override
        public float abajo(int pagina) {
            return LienzoPdf.ALTO - PIE;
        }

        @Override
        public void decorar(LienzoPdf lienzo, int pagina) {
            if (pagina == 1) {
                columna(lienzo, informe);
            } else {
                lienzo.rectangulo(0, 0, FRANJA, LienzoPdf.ALTO, TINTA);
                lienzo.texto(9f, 22f, EstiloPdf.voz(8f, NOCHE_TEXTO).espaciado(-0.02f), "EX");
                lienzo.textoVertical(22f, LienzoPdf.ALTO - 60f,
                        EstiloPdf.voz(7.5f, NOCHE_60).espaciado(0.08f),
                        informe.candidato() + " · " + informe.titulo());
            }
            float yPie = LienzoPdf.ALTO - FILETE_DEL_PIE;
            lienzo.linea(x(pagina), yPie, x(pagina) + ancho(pagina), yPie, FILETE, 0.75f);
            lienzo.texto(x(pagina), yPie + 6f, VOZ_PIE, informe.titulo());
            lienzo.textoDerecha(x(pagina) + ancho(pagina), yPie + 6f, VOZ_PIE, informe.candidato());
        }
    }

    /** «Página 1 de 3» en la columna y «2/3» en la franja, cuando ya se sabe cuántas son. */
    private void numerar(LienzoPdf lienzo, Informe informe) {
        int total = lienzo.paginas();
        for (int i = 1; i <= total; i++) {
            lienzo.enPagina(i);
            if (i == 1) {
                lienzo.texto(19.5f, LienzoPdf.ALTO - 30f, EstiloPdf.voz(7.1f, NOCHE_56),
                        "Página 1 de " + total);
            } else {
                lienzo.textoCentrado(FRANJA / 2f, LienzoPdf.ALTO - 30f, EstiloPdf.voz(7.1f, NOCHE_60),
                        i + "/" + total);
            }
        }
    }

    // ================================================================== la columna oscura

    private void columna(LienzoPdf l, Informe informe) {
        l.rectangulo(0, 0, COLUMNA, LienzoPdf.ALTO, TINTA);
        TipografiaPdf t = l.tipografia();
        float x = 19.5f;
        float ancho = COLUMNA - 2 * x;
        float y = 30f;

        l.rectanguloRedondeado(x, y, 13.5f, 13.5f, 3f, NOCHE_TEXTO, null, 0f);
        l.texto(x + 2f, y + 3f, EstiloPdf.voz(7f, TINTA).espaciado(0f), "EX");
        EstiloPdf marca = EstiloPdf.voz(7.9f, NOCHE_60);
        List<String> empresa = RenglonesPdf.partir(t, hay(informe.empresa()) ? informe.empresa() : "EX", marca,
                ancho - 20f);
        float ye = y + 3f;
        for (String linea : empresa) {
            l.texto(x + 19.5f, ye, marca, linea);
            ye += 10f;
        }
        y = Math.max(y + 16.5f, ye) + 14f;

        EstiloPdf rotulo = EstiloPdf.texto(7.5f, NOCHE_60).espaciado(0.08f).enMayusculas();
        for (String linea : RenglonesPdf.partir(t, informe.titulo(), rotulo, ancho)) {
            l.texto(x, y, rotulo, linea);
            y += 10f;
        }
        y += 4f;
        EstiloPdf nombre = EstiloPdf.texto(22.5f, NOCHE_TEXTO).espaciado(-0.04f);
        for (String linea : RenglonesPdf.partir(t, informe.candidato(), nombre, ancho)) {
            l.texto(x, y, nombre, linea);
            y += 23f;
        }
        y += 2f;
        EstiloPdf vacante = EstiloPdf.texto(9.4f, NOCHE_72);
        if (hay(informe.vacante())) {
            for (String linea : RenglonesPdf.partir(t, informe.vacante(), vacante, ancho)) {
                l.texto(x, y, vacante, linea);
                y += 12f;
            }
        }
        y += 12f;

        // El anillo con la nota: la única vez que sale la nota de la etapa.
        float cx = COLUMNA / 2f;
        float r = 46.5f;
        float cy = y + r + 4f;
        l.circulo(cx, cy, r, null, NOCHE_PISTA, 7.5f);
        if (informe.nota().valor() != null) {
            l.arco(cx, cy, r, informe.nota().valor().floatValue() / 100f, NOCHE_ANILLO, 7.5f);
        }
        String notaTexto = informe.nota().texto();
        boolean larga = notaTexto.length() > 4;
        EstiloPdf numero = EstiloPdf.texto(larga ? 15f : 33f, NOCHE_TEXTO).espaciado(-0.04f);
        l.textoCentrado(cx, cy - (larga ? 12f : 22f), numero, notaTexto);
        if (informe.nota().sobre() != null) {
            l.textoCentrado(cx, cy + (larga ? 6f : 13f), EstiloPdf.texto(8.25f, NOCHE_60), informe.nota().sobre());
        }
        y = cy + r + 14f;

        if (informe.grupo() != null) {
            String grupo = GRUPOS.getOrDefault(informe.grupo(), informe.grupo());
            EstiloPdf pastilla = EstiloPdf.texto(8f, NOCHE_TEXTO).medio();
            float w = t.ancho(grupo, pastilla) + 16f;
            l.rectanguloRedondeado(cx - w / 2f, y, w, 16f, 8f, NOCHE_LINEA, NOCHE_60, 0.5f);
            l.textoCentrado(cx, y + 4f, pastilla, grupo);
            y += 28f;
        }

        EstiloPdf fila = EstiloPdf.texto(9f, NOCHE_TEXTO);
        for (Recuento rc : informe.recuentos()) {
            Color punto = "BIEN".equals(rc.tono()) ? PUNTO_VERDE : "MAL".equals(rc.tono()) ? PUNTO_ROJO : NOCHE_ANILLO;
            l.circulo(x + 3f, y + 4.5f, 3f, punto, null, 0f);
            l.texto(x + 12f, y, fila, rc.etiqueta());
            l.textoDerecha(x + ancho, y, fila.medio(), String.valueOf(rc.cuantos()));
            y += 15f;
        }
        if (!informe.recuentos().isEmpty()) {
            y += 6f;
        }

        EstiloPdf dt = EstiloPdf.texto(7.1f, NOCHE_56).espaciado(0.08f).enMayusculas();
        EstiloPdf dd = EstiloPdf.texto(9f, NOCHE_TEXTO);
        for (Dato dato : informe.datosClave()) {
            l.linea(x, y, x + ancho, y, NOCHE_LINEA, 0.75f);
            y += 7f;
            l.texto(x, y, dt, dato.etiqueta());
            y += 10f;
            for (String linea : RenglonesPdf.partir(t, dato.valor(), dd, ancho)) {
                l.texto(x, y, dd, linea);
                y += 11.5f;
            }
            y += 4f;
        }

        l.linea(x, y, x + ancho, y, NOCHE_LINEA, 0.75f);
        y += 9f;
        l.texto(x, y, dt, "Enlaces");
        y += 13f;
        EstiloPdf enlace = EstiloPdf.texto(9f, NOCHE_ENLACE);
        if (informe.enlaces().isEmpty()) {
            l.texto(x, y, EstiloPdf.texto(9f, NOCHE_60), "Sin enlaces");
            y += 14f;
        }
        for (Enlace e : informe.enlaces()) {
            iconoEnlace(l, x, y, e.tipo());
            float w = l.texto(x + 15f, y, enlace, e.rotulo());
            l.enlace(x, y - 2f, w + 17f, 13f, e.url());
            y += 15f;
        }
        EstiloPdf nota = EstiloPdf.texto(7.9f, NOCHE_56);
        for (String linea : RenglonesPdf.partir(t, "Los enlaces caducan 8 horas después de generar el informe.",
                nota, ancho)) {
            l.texto(x, y, nota, linea);
            y += 10f;
        }
    }

    private static void iconoEnlace(LienzoPdf l, float x, float y, String tipo) {
        if ("CV".equals(tipo)) {
            l.polilinea(NOCHE_ENLACE, 1.1f, x + 2f, y, x + 7f, y, x + 10f, y + 3f, x + 10f, y + 10f,
                    x + 2f, y + 10f, x + 2f, y);
            l.linea(x + 4f, y + 5f, x + 8f, y + 5f, NOCHE_ENLACE, 0.9f);
            l.linea(x + 4f, y + 7.5f, x + 8f, y + 7.5f, NOCHE_ENLACE, 0.9f);
        } else {
            l.circulo(x + 5.5f, y + 5f, 4.5f, null, NOCHE_ENLACE, 1.1f);
            l.linea(x + 1f, y + 5f, x + 10f, y + 5f, NOCHE_ENLACE, 0.9f);
        }
    }

    // ================================================================== el resumen

    /**
     * Una parte del resumen: sus piezas en orden y el hueco que deja detrás. «Qué se evaluó» y
     * cada bloque llevan además la cabecera con la que siguen en otra hoja: son las partes que
     * tienen que empezar en la primera (D-1).
     */
    private record Seccion(List<FlujoPdf.Pieza> piezas, float separacion, FlujoPdf.Pieza sigue) {
        boolean empiezaEnLaPrimera() {
            return sigue != null && !piezas.isEmpty();
        }
    }

    /**
     * El resumen, con cada bloque empezando en la primera hoja (D-1).
     *
     * <p>Lo normal es que todo quepa seguido: «Qué se evaluó» y los bloques uno tras otro, y el
     * último sigue en la hoja siguiente si no cabe. Solo si así algún bloque empezaría en la
     * segunda hoja —criterios con explicaciones muy largas, listas muy largas—, la primera hoja
     * guarda sitio para el comienzo de cada parte (su título y su primer punto) y lo que no cabe
     * sigue en la segunda, parte por parte y en el mismo orden, con «(sigue)» en el título. Nada
     * se recorta y ningún punto se parte.
     */
    private void resumen(FlujoPdf flujo, Informe informe) {
        List<Seccion> secciones = secciones(informe);
        if (todoEmpiezaEnLaPrimera(flujo, secciones) || !cabeLoQueEmpieza(flujo, secciones)) {
            for (Seccion s : secciones) {
                s.piezas().forEach(flujo::poner);
                flujo.separar(s.separacion());
            }
            return;
        }
        conSitioGuardado(flujo, secciones);
    }

    private List<Seccion> secciones(Informe informe) {
        List<Seccion> secciones = new ArrayList<>();
        if (hay(informe.aviso())) {
            secciones.add(new Seccion(List.of((t, ancho) -> caja(t, ancho, informe.aviso(), ROJO)), 10f, null));
        }
        if (informe.experiencia() != null) {
            secciones.add(new Seccion(List.of((t, ancho) -> experiencia(t, ancho, informe.experiencia())),
                    10f, null));
        }
        if (informe.resumen() != null && hay(informe.resumen().texto())) {
            secciones.add(new Seccion(List.of((t, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                r.add(etiqueta(informe.resumen().titulo(), null));
                r.addAll(parrafo(t, informe.resumen().texto(), EstiloPdf.texto(9.75f, TINTA), ancho, 14f, 0f, null));
                return r;
            }), 10f, null));
        }
        if (informe.evaluado() != null) {
            String titulo = informe.evaluado().titulo();
            secciones.add(new Seccion(evaluado(informe), 10f,
                    (t, ancho) -> List.of(etiqueta(titulo + " (sigue)", null), hueco(2f))));
        }
        for (Bloque bloque : informe.bloques()) {
            Color color = colorDe(bloque);
            Color fondo = fondoDe(bloque);
            secciones.add(new Seccion(bloque(bloque), 9f,
                    (t, ancho) -> List.of(cabeceraDeBloque(bloque, bloque.titulo() + " (sigue)", color, fondo, 0))));
        }
        return secciones;
    }

    /** Si, puestas seguidas, todas las partes que deben empezar en la primera hoja empiezan ahí. */
    private static boolean todoEmpiezaEnLaPrimera(FlujoPdf flujo, List<Seccion> secciones) {
        int ultima = -1;
        for (int i = 0; i < secciones.size(); i++) {
            if (secciones.get(i).empiezaEnLaPrimera()) {
                ultima = i;
            }
        }
        float disponible = flujo.queda();
        float usado = 0f;
        float hueco = 0f;
        for (int i = 0; i <= ultima; i++) {
            Seccion s = secciones.get(i);
            for (FlujoPdf.Pieza pieza : s.piezas()) {
                usado += (usado > 0f ? hueco : 0f) + flujo.alto(pieza);
                hueco = 0f;
                if (usado > disponible) {
                    return false;
                }
                if (i == ultima) {
                    return true;
                }
            }
            hueco = Math.max(hueco, s.separacion());
        }
        return true;
    }

    /** Lo que ocupa el comienzo de cada parte que debe empezar en la primera hoja, con su hueco. */
    private static float loQueEmpieza(FlujoPdf flujo, List<Seccion> secciones, int desde) {
        float total = 0f;
        for (int i = desde; i < secciones.size(); i++) {
            Seccion s = secciones.get(i);
            if (s.empiezaEnLaPrimera()) {
                total += flujo.alto(s.piezas().get(0)) + (i == 0 ? 0f : secciones.get(i - 1).separacion());
            }
        }
        return total;
    }

    /** Si caben en la primera hoja la cabecera fija y el comienzo de cada parte. */
    private static boolean cabeLoQueEmpieza(FlujoPdf flujo, List<Seccion> secciones) {
        float fijo = 0f;
        for (Seccion s : secciones) {
            if (s.sigue() == null) {
                for (FlujoPdf.Pieza pieza : s.piezas()) {
                    fijo += flujo.alto(pieza);
                }
                fijo += s.separacion();
            }
        }
        return fijo + loQueEmpieza(flujo, secciones, 0) <= flujo.queda();
    }

    /**
     * La primera hoja guarda sitio para el comienzo de cada parte; lo que no cabe sin quitárselo
     * a las siguientes pasa a la segunda hoja, en el mismo orden y con «(sigue)».
     */
    private static void conSitioGuardado(FlujoPdf flujo, List<Seccion> secciones) {
        List<List<FlujoPdf.Pieza>> siguen = new ArrayList<>();
        for (int i = 0; i < secciones.size(); i++) {
            Seccion s = secciones.get(i);
            List<FlujoPdf.Pieza> suyas = new ArrayList<>();
            siguen.add(suyas);
            float guardado = loQueEmpieza(flujo, secciones, i + 1);
            for (int j = 0; j < s.piezas().size(); j++) {
                FlujoPdf.Pieza pieza = s.piezas().get(j);
                boolean comienzo = j == 0 || s.sigue() == null;
                float alto = flujo.alto(pieza) + flujo.huecoPendiente();
                if (comienzo || suyas.isEmpty() && flujo.pagina() == 1 && flujo.queda() - alto >= guardado) {
                    flujo.poner(pieza);
                } else {
                    suyas.add(pieza);
                }
            }
            flujo.separar(s.separacion());
        }
        boolean primera = true;
        for (int i = 0; i < secciones.size(); i++) {
            List<FlujoPdf.Pieza> suyas = siguen.get(i);
            if (suyas.isEmpty()) {
                continue;
            }
            if (primera) {
                flujo.saltarDePagina();
                primera = false;
            }
            FlujoPdf.Pieza cabecera = secciones.get(i).sigue();
            FlujoPdf.Pieza primerPunto = suyas.get(0);
            flujo.poner((t, ancho) -> {
                List<Renglon> r = new ArrayList<>(cabecera.renglones(t, ancho));
                r.addAll(primerPunto.renglones(t, ancho));
                return r;
            });
            suyas.subList(1, suyas.size()).forEach(flujo::poner);
            flujo.separar(secciones.get(i).separacion());
        }
    }

    private List<FlujoPdf.Pieza> evaluado(Informe informe) {
        List<FlujoPdf.Pieza> piezas = new ArrayList<>();
        List<Criterio> criterios = informe.evaluado().criterios();
        boolean conQuien = EtapaDelInforme.PRUEBA.name().equals(informe.etapa());
        if (criterios.isEmpty()) {
            piezas.add((t, ancho) -> List.of(etiqueta(informe.evaluado().titulo(), null),
                    texto(CHICO_GRIS, "Sin datos", 12f, 0f)));
            return piezas;
        }
        for (int i = 0; i < criterios.size(); i++) {
            Criterio c = criterios.get(i);
            boolean primero = i == 0;
            piezas.add((t, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                if (primero) {
                    r.add(etiqueta(informe.evaluado().titulo(), informe.evaluado().escala()));
                    r.add(hueco(2f));
                }
                r.addAll(criterio(t, ancho, c, conQuien));
                r.add(hueco(3.5f));
                return r;
            });
        }
        boolean algunaAjustada = criterios.stream().anyMatch(Criterio::ajustada);
        String pie = informe.evaluado().pie();
        if (algunaAjustada || hay(pie)) {
            piezas.add((t, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                if (algunaAjustada) {
                    r.add(new R(11f, (l, x, y, w) -> {
                        lapiz(l, x, y + 1f, GRIS);
                        l.texto(x + 10f, y, EstiloPdf.texto(7.5f, GRIS), "ajustada a mano");
                    }));
                }
                if (hay(pie)) {
                    r.add(texto(EstiloPdf.texto(7.5f, GRIS), pie, 11f, 0f));
                }
                return r;
            });
        }
        return piezas;
    }

    /** Un criterio: nombre, barra y nota en una fila; debajo su texto completo. */
    private List<Renglon> criterio(TipografiaPdf t, float ancho, Criterio c, boolean conQuien) {
        List<Renglon> r = new ArrayList<>();
        float columnaNombre = Math.min(150f, ancho * 0.42f);
        String nota = c.nota() == null ? FormatoDelInforme.HUECO
                : c.notaTexto() + (c.maximoTexto() == null ? "" : " " + c.maximoTexto());
        float anchoNota = Math.max(24f, t.ancho(nota, CUERPO_MEDIO) + 4f);
        float anchoQuien = conQuien && c.quien() != null ? t.ancho(c.quien().toUpperCase(java.util.Locale.ROOT), ETIQUETA_CHICA) + 12f : 0f;
        List<String> nombre = RenglonesPdf.partir(t, c.nombre(), CUERPO_MEDIO,
                columnaNombre - (c.ajustada() ? 10f : 0f));
        float alto = Math.max(12f, nombre.size() * 11.5f);
        r.add(new R(alto + 1f, (l, x, y, w) -> {
            float yy = y;
            for (int i = 0; i < nombre.size(); i++) {
                float escrito = l.texto(x, yy, CUERPO_MEDIO, nombre.get(i));
                if (c.ajustada() && i == nombre.size() - 1) {
                    lapiz(l, x + escrito + 3f, yy + 1f, AZUL);
                }
                yy += 11.5f;
            }
            float xBarra = x + columnaNombre + 8f;
            float finBarra = x + w - anchoNota - anchoQuien - 8f;
            float yBarra = y + 3f;
            l.rectanguloRedondeado(xBarra, yBarra, Math.max(10f, finBarra - xBarra), 5.25f, 2.6f, HALO, null, 0f);
            if (c.porcentaje() != null && c.porcentaje() > 0) {
                float lleno = (finBarra - xBarra) * c.porcentaje() / 100f;
                l.rectanguloRedondeado(xBarra, yBarra, Math.max(5.25f, lleno), 5.25f, 2.6f, AZUL, null, 0f);
            }
            if (anchoQuien > 0f) {
                float xq = x + w - anchoNota - anchoQuien + 2f;
                l.rectanguloRedondeado(xq, y, anchoQuien - 6f, 10.5f, 5f, null, new Color(0xD4D6D3), 0.6f);
                l.texto(xq + 4f, y + 2.2f, ETIQUETA_CHICA.conColor(GRIS_MEDIO), c.quien());
            }
            l.textoDerecha(x + w, y, CUERPO_MEDIO, nota);
        }));
        if (hay(c.texto())) {
            boolean deRedactor = !Objects.equals(c.texto(), c.explicacion());
            String prefijo = deRedactor ? "Lo que demostró: " : "";
            r.addAll(parrafo(t, prefijo + c.texto(), EstiloPdf.texto(7.9f, deRedactor ? TEXTO2 : GRIS_MEDIO),
                    ancho, 9.4f, 0f, null));
        }
        if (c.ajustada()) {
            r.addAll(parrafo(t, "Ajustada a mano" + (hay(c.motivoAjuste()) ? ": " + c.motivoAjuste() : "."),
                    EstiloPdf.texto(7.9f, AZUL), ancho, 9.4f, 0f, null));
        }
        if (c.nota() == null && hay(c.pendiente())) {
            r.addAll(parrafo(t, c.pendiente(), EstiloPdf.texto(7.9f, GRIS), ancho, 9.4f, 0f, null));
        }
        if (hay(c.preguntas())) {
            r.add(texto(EstiloPdf.texto(7.5f, GRIS), c.preguntas(), 10f, 0f));
        }
        return r;
    }

    private static Color colorDe(Bloque bloque) {
        return switch (bloque.tono()) {
            case "BIEN" -> VERDE;
            case "MAL" -> ROJO;
            case "PENDIENTE" -> AZUL_OSCURO;
            case "NEUTRO" -> TEXTO2;
            default -> TINTA;
        };
    }

    private static Color fondoDe(Bloque bloque) {
        return "PENDIENTE".equals(bloque.tono()) ? BRUMA : null;
    }

    /** Las piezas de un bloque: la primera lleva su cabecera; cada punto va entero en una hoja. */
    private List<FlujoPdf.Pieza> bloque(Bloque bloque) {
        Color color = colorDe(bloque);
        Color fondo = fondoDe(bloque);
        float sangria = 30f;
        List<Punto> puntos = bloque.puntos();
        List<FlujoPdf.Pieza> piezas = new ArrayList<>();
        if (puntos.isEmpty()) {
            piezas.add((t, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                r.add(cabeceraDeBloque(bloque, bloque.titulo(), color, fondo, 0));
                r.add(conFondo(texto(CHICO_GRIS, bloque.vacio(), 13f, sangria), fondo));
                r.add(conFondo(hueco(5f), fondo));
                return r;
            });
            return piezas;
        }
        String grupoAnterior = null;
        for (int i = 0; i < puntos.size(); i++) {
            Punto p = puntos.get(i);
            boolean primero = i == 0;
            boolean ultimo = i == puntos.size() - 1;
            boolean nuevoGrupo = hay(p.grupo()) && !p.grupo().equals(grupoAnterior);
            grupoAnterior = hay(p.grupo()) ? p.grupo() : grupoAnterior;
            int cuantos = puntos.size();
            piezas.add((t, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                if (primero) {
                    r.add(cabeceraDeBloque(bloque, bloque.titulo(), color, fondo, cuantos));
                }
                if (nuevoGrupo) {
                    r.add(conFondo(hueco(3f), fondo));
                    r.add(conFondo(texto(ETIQUETA_CHICA.conColor(AZUL), p.grupo(), 11f, sangria), fondo));
                }
                r.addAll(punto(t, ancho, p, bloque, sangria, fondo));
                r.add(conFondo(hueco(ultimo ? 6f : 3f), fondo));
                return r;
            });
        }
        return piezas;
    }

    /** El icono y el título del bloque; en los pendientes, cuántos son (no en el «(sigue)»). */
    private static Renglon cabeceraDeBloque(Bloque bloque, String titulo, Color color, Color fondo, int cuantos) {
        return conFondo(new R(20f, (l, x, y, w) -> {
            icono(l, bloque.tono(), x + 4f, y + 2f, color);
            l.texto(x + 30f, y + 4.5f, TITULO_BLOQUE.conColor(color), titulo);
            if (cuantos > 0 && ("PENDIENTE".equals(bloque.tono()) || "CV".equals(bloque.tono()))) {
                l.textoDerecha(x + w - 8f, y + 5.5f, CHICO_GRIS, String.valueOf(cuantos));
            }
        }), fondo);
    }

    private List<Renglon> punto(TipografiaPdf t, float ancho, Punto p, Bloque bloque, float sangria, Color fondo) {
        List<Renglon> r = new ArrayList<>();
        boolean pendiente = "PENDIENTE".equals(bloque.tono()) || "CV".equals(bloque.tono());
        float derecha = fondo != null ? 10f : 0f;
        float anchoTexto = ancho - sangria - derecha - 9f;
        List<String> etiquetas = new ArrayList<>();
        if (pendiente && p.origen() != null && ORIGENES.containsKey(p.origen())) {
            etiquetas.add(ORIGENES.get(p.origen()));
        }
        if (hay(p.etiqueta())) {
            etiquetas.add(p.etiqueta());
        }
        if (!etiquetas.isEmpty()) {
            String chips = String.join(" · ", etiquetas);
            r.add(conFondo(new R(11.5f, (l, x, y, w) -> {
                EstiloPdf e = ETIQUETA_CHICA.conColor(AZUL);
                float wc = l.tipografia().ancho(chips, e) + 10f;
                l.rectanguloRedondeado(x + sangria, y, wc, 10f, 5f, HALO, null, 0f);
                l.texto(x + sangria + 5f, y + 2f, e, chips);
            }), fondo));
        }
        EstiloPdf estilo = pendiente ? CUERPO_MEDIO : CUERPO;
        List<String> lineas = RenglonesPdf.partir(t, p.texto(), estilo, anchoTexto);
        for (int i = 0; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            boolean primera = i == 0;
            r.add(conFondo(new R(12.8f, (l, x, y, w) -> {
                if (primera && !pendiente) {
                    l.circulo(x + sangria + 2.5f, y + 5f, 1.4f, TINTA, null, 0f);
                }
                l.texto(x + sangria + (pendiente ? 0f : 9f), y, estilo, linea);
            }), fondo));
        }
        if (hay(p.detalle())) {
            String detalle = "NEUTRO".equals(bloque.tono()) ? "› " + p.detalle() : p.detalle();
            EstiloPdf e = EstiloPdf.texto(8.6f, pendiente ? AZUL_OSCURO : GRIS_MEDIO);
            for (String linea : RenglonesPdf.partir(t, detalle, e, anchoTexto)) {
                r.add(conFondo(new R(11.5f, (l, x, y, w) ->
                        l.texto(x + sangria + (pendiente ? 0f : 9f), y, e, linea)), fondo));
            }
        }
        return r;
    }

    // ================================================================== el detalle

    private void detalle(FlujoPdf flujo, Informe informe) {
        List<Detalle> entradas = informe.detalle();
        if (entradas.isEmpty() && !hay(informe.detallePie())) {
            return;
        }
        flujo.separar(6f);
        for (int i = 0; i < entradas.size(); i++) {
            Detalle d = entradas.get(i);
            boolean primero = i == 0;
            flujo.poner((t, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                if (primero) {
                    r.add(etiqueta(informe.detalleTitulo(), null));
                    r.add(hueco(2f));
                }
                r.addAll(entradaDeDetalle(t, ancho, d));
                r.add(hueco(8f));
                return r;
            });
        }
        if (hay(informe.detallePie())) {
            flujo.poner((t, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                if (entradas.isEmpty()) {
                    r.add(etiqueta(informe.detalleTitulo(), null));
                }
                r.addAll(parrafo(t, informe.detallePie(), CHICO, ancho, 10.5f, 0f, null));
                return r;
            });
        }
    }

    private List<Renglon> entradaDeDetalle(TipografiaPdf t, float ancho, Detalle d) {
        List<Renglon> r = new ArrayList<>();
        String titulo = (d.numero() == null ? "" : d.numero() + ". ") + d.titulo();
        float anchoNota = d.notaTexto() == null ? 0f : t.ancho(d.notaTexto(), CUERPO_MEDIO) + 18f;
        List<String> lineas = RenglonesPdf.partir(t, titulo, CUERPO_MEDIO, ancho - anchoNota - 6f);
        for (int i = 0; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            boolean primera = i == 0;
            r.add(new R(12f, (l, x, y, w) -> {
                l.texto(x, y, CUERPO_MEDIO, linea);
                if (primera && d.notaTexto() != null) {
                    float wn = anchoNota - 6f;
                    l.rectanguloRedondeado(x + w - wn, y - 1.5f, wn, 12.5f, 6f, HALO, null, 0f);
                    l.textoDerecha(x + w - 6f, y + 0.5f, CUERPO_MEDIO.conColor(AZUL_OSCURO), d.notaTexto());
                }
            }));
        }
        if (hay(d.meta())) {
            r.add(texto(ETIQUETA_CHICA, d.meta(), 11f, 0f));
        }
        if (d.ajustada()) {
            r.addAll(parrafo(t, "Ajustada a mano" + (hay(d.motivoAjuste()) ? ": " + d.motivoAjuste() : "."),
                    EstiloPdf.texto(8.3f, AZUL), ancho, 11f, 0f, null));
        }
        if (hay(d.explicacion())) {
            r.addAll(parrafo(t, d.explicacion(), EstiloPdf.texto(8.6f, TEXTO2), ancho, 11.6f, 0f, null));
        }
        if (hay(d.evidencia())) {
            r.addAll(parrafo(t, "Citó: «" + d.evidencia() + "»", EstiloPdf.texto(8.3f, GRIS_MEDIO), ancho,
                    11f, 0f, null));
        }
        if (!d.senales().isEmpty()) {
            StringBuilder s = new StringBuilder();
            for (Senal senal : d.senales()) {
                if (!s.isEmpty()) {
                    s.append(" · ");
                }
                s.append(senal.nombre()).append(": ").append(senal.cumple() ? "sí" : "no");
            }
            r.addAll(parrafo(t, s.toString(), EstiloPdf.texto(8f, GRIS), ancho, 11f, 0f, null));
        }
        if (hay(d.pendiente())) {
            r.add(texto(EstiloPdf.texto(8f, GRIS), d.pendiente(), 11f, 0f));
        }
        return r;
    }

    // ================================================================== el anexo

    private void anexo(FlujoPdf flujo, Informe informe) {
        Anexo anexo = informe.anexo();
        if (anexo == null) {
            return;
        }
        flujo.saltarDePagina();
        flujo.poner((t, ancho) -> {
            List<Renglon> r = new ArrayList<>();
            r.add(texto(ETIQUETA, informe.titulo() + " · Anexo", 13f, 0f));
            EstiloPdf grande = EstiloPdf.texto(21f, TINTA).espaciado(-0.04f);
            r.add(new R(28f, (l, x, y, w) -> {
                l.texto(x, y, grande, anexo.titulo());
                if (hay(anexo.subtitulo())) {
                    l.textoDerecha(x + w, y + 8f, CHICO_GRIS, anexo.subtitulo());
                }
            }));
            r.add(new R(10f, (l, x, y, w) -> l.linea(x, y + 3f, x + w, y + 3f, FILETE, 0.75f)));
            return r;
        });
        if (hay(anexo.caso())) {
            flujo.poner((t, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                r.add(conFondo(hueco(6f), BRUMA));
                r.add(conFondo(texto(ETIQUETA_CHICA.conColor(AZUL), "El caso que se le planteó", 12f, 10f), BRUMA));
                for (Renglon renglon : parrafo(t, anexo.caso(), EstiloPdf.texto(8.8f, TEXTO2), ancho - 20f, 12f, 10f, null)) {
                    r.add(conFondo(renglon, BRUMA));
                }
                r.add(conFondo(hueco(6f), BRUMA));
                return r;
            });
            flujo.separar(12f);
        }
        if (anexo.preguntas().isEmpty()) {
            flujo.poner((t, ancho) -> List.of(texto(CHICO_GRIS, "Sin preguntas que enseñar.", 14f, 0f)));
        }
        for (PreguntaAnexo p : anexo.preguntas()) {
            flujo.poner((t, ancho) -> pregunta(t, ancho, p));
            flujo.separar(10f);
        }
        if (!anexo.entregables().isEmpty()) {
            flujo.separar(8f);
            List<EntregableAnexo> entregables = anexo.entregables();
            for (int i = 0; i < entregables.size(); i++) {
                EntregableAnexo e = entregables.get(i);
                boolean primero = i == 0;
                flujo.poner((t, ancho) -> {
                    List<Renglon> r = new ArrayList<>();
                    if (primero) {
                        r.add(new R(18f, (l, x, y, w) -> {
                            l.texto(x, y, ETIQUETA, "Entregables pedidos");
                            if (hay(anexo.entregablesResumen())) {
                                l.textoDerecha(x + w, y, CHICO_GRIS, anexo.entregablesResumen());
                            }
                        }));
                    }
                    r.addAll(entregable(t, ancho, e));
                    r.add(hueco(5f));
                    return r;
                });
            }
        }
    }

    private List<Renglon> pregunta(TipografiaPdf t, float ancho, PreguntaAnexo p) {
        List<Renglon> r = new ArrayList<>();
        float sangria = 26f;
        float anchoTexto = ancho - sangria;
        List<String> enunciado = RenglonesPdf.partir(t, p.enunciado(), CUERPO_MEDIO, anchoTexto);
        for (int i = 0; i < enunciado.size(); i++) {
            String linea = enunciado.get(i);
            boolean primera = i == 0;
            r.add(new R(12.5f, (l, x, y, w) -> {
                if (primera) {
                    l.circulo(x + 9f, y + 5f, 9f, TINTA, null, 0f);
                    l.textoCentrado(x + 9f, y + 1.2f, EstiloPdf.texto(8.5f, NOCHE_TEXTO), String.valueOf(p.numero()));
                }
                l.texto(x + sangria, y, CUERPO_MEDIO, linea);
            }));
        }
        if (hay(p.meta())) {
            r.add(texto(ETIQUETA_CHICA, p.meta(), 12f, sangria));
        }
        r.add(hueco(3f));
        if (p.respuesta() == null) {
            r.add(new R(20f, (l, x, y, w) -> {
                l.rectanguloPunteado(x + sangria, y, w - sangria, 17f, 4f, new Color(0xB9BDBC), 0.75f);
                l.texto(x + sangria + 8f, y + 4.5f, EstiloPdf.texto(8.6f, GRIS), "Sin respuesta.");
            }));
        } else {
            r.add(burbuja(hueco(5f), sangria));
            for (Renglon renglon : parrafo(t, p.respuesta(), EstiloPdf.texto(8.8f, TEXTO2), anchoTexto - 18f,
                    12.3f, sangria + 9f, null)) {
                r.add(burbuja(renglon, sangria));
            }
            r.add(burbuja(hueco(5f), sangria));
        }
        if (hay(p.loQueDemostro()) && !Objects.equals(p.loQueDemostro(), p.explicacion())) {
            r.add(hueco(3f));
            r.addAll(parrafo(t, "Lo que demostró: " + p.loQueDemostro(), EstiloPdf.texto(8.3f, GRIS_MEDIO),
                    anchoTexto, 11f, sangria, null));
        }
        return r;
    }

    private List<Renglon> entregable(TipografiaPdf t, float ancho, EntregableAnexo e) {
        List<Renglon> r = new ArrayList<>();
        String estado = e.entregado() ? "Entregado" : "No entregado";
        EstiloPdf chip = ETIQUETA_CHICA.conColor(e.entregado() ? VERDE : ROJO);
        float anchoChip = t.ancho(estado.toUpperCase(java.util.Locale.ROOT), chip) + 14f;
        List<String> nombre = RenglonesPdf.partir(t, e.nombre(), CUERPO_MEDIO, ancho - anchoChip - 40f);
        List<String> detalle = RenglonesPdf.partir(t, e.detalle(), CHICO_GRIS, ancho - anchoChip - 40f);
        float alto = 12f + nombre.size() * 11.5f + detalle.size() * 10.5f;
        r.add(new R(alto, (l, x, y, w) -> {
            l.rectanguloRedondeado(x, y, w, alto - 2f, 3f, null, FILETE, 0.75f);
            if (e.entregado()) {
                l.polilinea(VERDE, 1.3f, x + 10f, y + 10f, x + 13f, y + 13f, x + 18f, y + 7f);
            } else {
                l.linea(x + 10f, y + 7f, x + 17f, y + 14f, ROJO, 1.3f);
                l.linea(x + 17f, y + 7f, x + 10f, y + 14f, ROJO, 1.3f);
            }
            float yy = y + 6f;
            // Con algo con que abrirlo (solo con «descargar_entregables»), el nombre es un enlace:
            // el firmado de 8 horas de un archivo, como el del CV, o la dirección que pegó.
            boolean abre = e.entregado() && LoComunDelInforme.esWeb(e.url());
            for (String linea : nombre) {
                float anchoLinea = l.texto(x + 28f, yy, abre ? CUERPO_MEDIO.conColor(AZUL) : CUERPO_MEDIO, linea);
                if (abre) {
                    l.enlace(x + 28f, yy - 2f, anchoLinea, 13f, e.url().strip());
                }
                yy += 11.5f;
            }
            for (String linea : detalle) {
                l.texto(x + 28f, yy, CHICO_GRIS, linea);
                yy += 10.5f;
            }
            float xc = x + w - anchoChip - 8f;
            l.rectanguloRedondeado(xc, y + 6f, anchoChip, 11f, 5.5f, e.entregado() ? new Color(0xE3F3EE)
                    : new Color(0xF9E3E6), null, 0f);
            l.texto(xc + 7f, y + 8.5f, chip, estado);
        }));
        return r;
    }

    // ================================================================== piezas de dibujo

    /** Un renglón ya medido. */
    private record R(float alto, Dibujo dibujo) implements Renglon {
        @Override
        public void dibujar(LienzoPdf lienzo, float x, float y, float ancho) {
            dibujo.dibujar(lienzo, x, y, ancho);
        }
    }

    @FunctionalInterface
    private interface Dibujo {
        void dibujar(LienzoPdf lienzo, float x, float y, float ancho);
    }

    private static Renglon hueco(float alto) {
        return new R(alto, (l, x, y, w) -> { });
    }

    private static Renglon texto(EstiloPdf estilo, String texto, float alto, float sangria) {
        return new R(alto, (l, x, y, w) -> l.texto(x + sangria, y, estilo, texto));
    }

    private static Renglon etiqueta(String texto, String derecha) {
        return new R(15f, (l, x, y, w) -> {
            l.texto(x, y, ETIQUETA, texto);
            if (hay(derecha)) {
                l.textoDerecha(x + w, y, CHICO_GRIS, derecha);
            }
        });
    }

    private static List<Renglon> parrafo(TipografiaPdf t, String texto, EstiloPdf estilo, float ancho,
                                         float alto, float sangria, Color fondo) {
        List<Renglon> r = new ArrayList<>();
        for (String linea : RenglonesPdf.partir(t, texto, estilo, ancho - sangria)) {
            r.add(conFondo(new R(alto, (l, x, y, w) -> l.texto(x + sangria, y, estilo, linea)), fondo));
        }
        return r;
    }

    /** El mismo renglón con su franja de fondo: renglón a renglón se forma la caja entera. */
    private static Renglon conFondo(Renglon renglon, Color fondo) {
        if (fondo == null) {
            return renglon;
        }
        return new R(renglon.alto(), (l, x, y, w) -> {
            l.rectangulo(x, y - 0.2f, w, renglon.alto() + 0.4f, fondo);
            renglon.dibujar(l, x, y, w);
        });
    }

    /** La respuesta como bocadillo: el fondo bruma a partir de la sangría. */
    private static Renglon burbuja(Renglon renglon, float sangria) {
        return new R(renglon.alto(), (l, x, y, w) -> {
            l.rectangulo(x + sangria, y - 0.2f, w - sangria, renglon.alto() + 0.4f, BRUMA);
            renglon.dibujar(l, x, y, w);
        });
    }

    private List<Renglon> caja(TipografiaPdf t, float ancho, String texto, Color color) {
        List<Renglon> r = new ArrayList<>();
        Color fondo = new Color(0xFBEFF1);
        r.add(conFondo(hueco(7f), fondo));
        for (Renglon renglon : parrafo(t, texto, EstiloPdf.texto(9f, color), ancho - 20f, 12.5f, 10f, null)) {
            r.add(conFondo(renglon, fondo));
        }
        r.add(conFondo(hueco(7f), fondo));
        return r;
    }

    private List<Renglon> experiencia(TipografiaPdf t, float ancho, Experiencia e) {
        List<Renglon> r = new ArrayList<>();
        r.add(etiqueta("Experiencia profesional", null));
        float c1 = 84f;
        float gap = 12f;
        float resto = ancho - c1 - 2 * gap;
        float c2 = resto * 1.25f / 2.25f;
        float c3 = resto - c2;
        EstiloPdf grande = EstiloPdf.texto(15f, TINTA).espaciado(-0.03f);
        EstiloPdf fuerte = EstiloPdf.texto(9.75f, TINTA).medio();
        EstiloPdf sub = EstiloPdf.texto(9f, GRIS_MEDIO);
        List<String> total = RenglonesPdf.partir(t, e.total(), grande, c1);
        List<String> puesto = RenglonesPdf.partir(t, e.ultimoPuesto(), fuerte, c2 - 11f);
        List<String> puestoSub = RenglonesPdf.partir(t, e.ultimoDetalle(), sub, c2 - 11f);
        List<String> formacion = RenglonesPdf.partir(t, e.formacion(), fuerte, c3 - 11f);
        float alto1 = total.size() * 17f;
        float alto2 = puesto.size() * 12.5f + puestoSub.size() * 11.5f;
        float alto3 = formacion.size() * 12.5f;
        float alto = 12f + Math.max(alto1, Math.max(alto2, alto3)) + 6f;
        r.add(new R(alto, (l, x, y, w) -> {
            l.texto(x, y, ETIQUETA_CHICA, "Total");
            l.texto(x + c1 + gap + 11f, y, ETIQUETA_CHICA, "Último puesto");
            l.texto(x + c1 + gap + c2 + gap + 11f, y, ETIQUETA_CHICA, "Formación máxima");
            l.linea(x + c1 + gap, y, x + c1 + gap, y + alto - 6f, new Color(0xE3E6EE), 0.75f);
            l.linea(x + c1 + gap + c2 + gap, y, x + c1 + gap + c2 + gap, y + alto - 6f, new Color(0xE3E6EE), 0.75f);
            float yy = y + 12f;
            for (String linea : total) {
                l.texto(x, yy, grande, linea);
                yy += 17f;
            }
            yy = y + 12f;
            for (String linea : puesto) {
                l.texto(x + c1 + gap + 11f, yy, fuerte, linea);
                yy += 12.5f;
            }
            for (String linea : puestoSub) {
                l.texto(x + c1 + gap + 11f, yy, sub, linea);
                yy += 11.5f;
            }
            yy = y + 12f;
            for (String linea : formacion) {
                l.texto(x + c1 + gap + c2 + gap + 11f, yy, fuerte, linea);
                yy += 12.5f;
            }
        }));
        if (!e.habilidades().isEmpty()) {
            EstiloPdf pastilla = EstiloPdf.texto(7.9f, AZUL_OSCURO);
            List<List<String>> filas = new ArrayList<>();
            List<String> actual = new ArrayList<>();
            float usado = 0f;
            for (String h : e.habilidades()) {
                float w = t.ancho(h, pastilla) + 12f + 4f;
                if (!actual.isEmpty() && usado + w > ancho) {
                    filas.add(actual);
                    actual = new ArrayList<>();
                    usado = 0f;
                }
                actual.add(h);
                usado += w;
            }
            filas.add(actual);
            for (List<String> fila : filas) {
                r.add(new R(15f, (l, x, y, w) -> {
                    float xx = x;
                    for (String h : fila) {
                        float wh = Math.min(w, l.tipografia().ancho(h, pastilla) + 12f);
                        l.rectanguloRedondeado(xx, y, wh, 12.5f, 6.25f, HALO, null, 0f);
                        l.texto(xx + 6f, y + 2.5f, pastilla, h);
                        xx += wh + 4f;
                    }
                }));
            }
        }
        if (hay(e.perfil())) {
            r.add(hueco(3f));
            r.addAll(parrafo(t, e.perfil(), EstiloPdf.texto(9.4f, TEXTO2), ancho, 12.5f, 0f, null));
        }
        return r;
    }

    /** Los iconos de los bloques, en trazo, de 21 pt: ✓, aspa, triángulo, flecha y bocadillo. */
    private static void icono(LienzoPdf l, String tono, float x, float y, Color color) {
        float c = 8f;
        switch (tono) {
            case "BIEN" -> {
                l.circulo(x + c, y + c, c, null, color, 1.4f);
                l.polilinea(color, 1.4f, x + 5f, y + 8.2f, x + 7.3f, y + 10.6f, x + 11.3f, y + 5.8f);
            }
            case "MAL" -> {
                l.circulo(x + c, y + c, c, null, color, 1.4f);
                l.linea(x + 5.5f, y + 5.5f, x + 10.5f, y + 10.5f, color, 1.4f);
                l.linea(x + 10.5f, y + 5.5f, x + 5.5f, y + 10.5f, color, 1.4f);
            }
            case "NEUTRO" -> {
                l.linea(x + 2f, y + c, x + 14f, y + c, color, 1.4f);
                l.polilinea(color, 1.4f, x + 9.5f, y + 3.5f, x + 14f, y + c, x + 9.5f, y + 12.5f);
            }
            default -> {
                l.polilinea(color, 1.3f, x + 1f, y + 1f, x + 15f, y + 1f, x + 15f, y + 11f, x + 6f, y + 11f,
                        x + 2.5f, y + 15f, x + 2.5f, y + 11f, x + 1f, y + 11f, x + 1f, y + 1f);
                l.linea(x + 5f, y + 5f, x + 11f, y + 5f, color, 1f);
                l.linea(x + 5f, y + 7.8f, x + 9f, y + 7.8f, color, 1f);
            }
        }
    }

    /** El lápiz de «ajustada a mano»: el ✎ de la maqueta, dibujado. */
    private static void lapiz(LienzoPdf l, float x, float y, Color color) {
        l.polilinea(color, 0.9f, x + 1f, y + 7f, x + 6.5f, y + 1.5f, x + 8f, y + 3f, x + 2.5f, y + 8.5f,
                x + 0.5f, y + 9f, x + 1f, y + 7f);
    }
}
