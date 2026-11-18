package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.comun.pdf.ColoresPdf;
import com.renaser.ai.ai_engine.comun.pdf.EstiloPdf;
import com.renaser.ai.ai_engine.comun.pdf.FlujoPdf;
import com.renaser.ai.ai_engine.comun.pdf.FlujoPdf.Renglon;
import com.renaser.ai.ai_engine.comun.pdf.LienzoPdf;
import com.renaser.ai.ai_engine.comun.pdf.RenglonesPdf;
import com.renaser.ai.ai_engine.comun.pdf.TipografiaPdf;
import com.renaser.ai.ai_engine.organizacion.service.ContenidoDelBrochure.Contacto;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static com.renaser.ai.ai_engine.organizacion.service.ContenidoDelBrochure.hay;

/**
 * El brochure de la empresa en PDF A4 (V86), en una de las tres plantillas de
 * {@link PlantillaDelBrochure} y uno de los seis colores de {@link ColorDelBrochure}. Usa el motor
 * del informe por etapa ({@code comun/pdf}): sus fuentes incrustadas, su texto que pasa de una
 * hoja a otra sin cortarse y sus enlaces que se pueden pulsar.
 *
 * <p>Es el perfil público pasado a papel, en este orden y solo con lo que esté lleno: el logo (o
 * las iniciales) y el nombre con «Sector · Tamaño · Fundada en AAAA»; «Quiénes somos» y «Nuestro
 * propósito»; «Misión» y «Visión»; «Beneficios»; «Dónde estamos»; «Contacto»; y al pie de la última
 * hoja «Brochure generado con EX · {fecha}».
 *
 * <ul>
 *   <li><b>Una sección vacía no se pinta, tampoco su título</b>, y sin logo ni portada ninguna
 *       plantilla deja huecos: van las iniciales sobre el color.</li>
 *   <li><b>Nada se recorta.</b> Los textos largos pasan a la hoja siguiente; un nombre o un
 *       propósito muy largos bajan de tamaño antes que salirse de su sitio.</li>
 * </ul>
 *
 * <p>Las medidas son las de la maqueta (794 × 1123 px por hoja) pasadas a puntos (× 0,75). No
 * guarda estado: cada llamada arma su propio documento.
 */
@Component
public class PdfDelBrochure {

    // ------------------------------------------------------------------ paleta
    static final Color TINTA = ColoresPdf.TINTA;
    static final Color GRIS = ColoresPdf.GRIS;
    static final Color GRIS_MEDIO = ColoresPdf.GRIS_MEDIO;
    static final Color FILETE = new Color(0xE6E7E4);
    static final Color BORDE = new Color(0xE3E4E3);
    static final Color BLANCO = Color.WHITE;

    // ------------------------------------------------------------------ geometría
    static final float ANCHO = LienzoPdf.ANCHO;
    static final float ALTO = LienzoPdf.ALTO;
    static final float MARGEN = 48f;
    static final float ARRIBA = 42f;
    static final float COLUMNA = 183f;
    static final float FRANJA = 34f;
    static final float MARGEN_COLUMNAS = 25.5f;
    static final float ARRIBA_COLUMNAS = 30f;
    /** El filete del pie, contado desde arriba; lo escrito acaba más arriba, en {@link #ABAJO}. */
    static final float FILETE_DEL_PIE = ALTO - 40f;
    static final float ABAJO = ALTO - 54f;
    private static final float LOGO_GRANDE = 57f;
    private static final float LOGO_CHICO = 27f;
    private static final float ALTO_BANDA_PORTADA = 91.5f;

    // ------------------------------------------------------------------ estilos
    private static final EstiloPdf TEXTO = EstiloPdf.texto(10.1f, GRIS_MEDIO);
    private static final float LINEA_TEXTO = 16.3f;
    private static final EstiloPdf TEXTO_COLUMNAS = EstiloPdf.texto(9.75f, GRIS_MEDIO);
    private static final float LINEA_TEXTO_COLUMNAS = 15.8f;
    private static final EstiloPdf CHIP = EstiloPdf.texto(9f, TINTA);
    private static final EstiloPdf SEDE = EstiloPdf.texto(10.5f, TINTA);
    private static final EstiloPdf ROTULO_CONTACTO = EstiloPdf.texto(9.75f, TINTA).medio();
    private static final EstiloPdf PIE = EstiloPdf.voz(7.1f, GRIS);
    private static final String PRESENTACION = "Presentación de la empresa";
    /** Desde aquí, un propósito se escribe con letra de texto y no de titular. */
    private static final int PROPOSITO_LARGO = 320;

    /**
     * El PDF del brochure, ya armado.
     *
     * @param logo    los bytes del logo, o nulo; si no se pueden leer, salen las iniciales
     * @param portada los bytes de la foto de portada, o nulo; si no se pueden leer, no hay foto
     */
    public byte[] generar(ContenidoDelBrochure contenido, byte[] logo, byte[] portada) {
        try (LienzoPdf lienzo = new LienzoPdf()) {
            lienzo.titulo("Brochure - " + contenido.nombre(), contenido.nombre());
            Armado armado = new Armado(lienzo, contenido, lienzo.leerImagen(logo),
                    lienzo.leerImagen(portada));
            switch (contenido.plantilla()) {
                case PORTADA -> armado.portada();
                case COLUMNAS -> armado.columnas();
                default -> armado.clasica();
            }
            armado.numerar();
            return lienzo.guardar();
        }
    }

    // ================================================================== el armado

    /** Un brochure a medio armar: su lienzo, lo que lleva y las imágenes ya leídas. */
    private static final class Armado {
        private final LienzoPdf lienzo;
        private final TipografiaPdf t;
        private final ContenidoDelBrochure c;
        private final ColorDelBrochure tono;
        private final LienzoPdf.Imagen logo;
        private final LienzoPdf.Imagen foto;
        private FlujoPdf.Geometria geometria;
        /** La portada de «Portada» no lleva pie ni cuenta como hoja con texto. */
        private boolean conHojaDePortada;

        Armado(LienzoPdf lienzo, ContenidoDelBrochure c, LienzoPdf.Imagen logo,
               LienzoPdf.Imagen foto) {
            this.lienzo = lienzo;
            this.t = lienzo.tipografia();
            this.c = c;
            this.tono = c.color();
            this.logo = logo;
            this.foto = foto;
        }

        private EstiloPdf estiloEtiqueta() {
            return EstiloPdf.voz(7.9f, tono.color()).espaciado(0.1f);
        }

        // ============================================================== «Clásica»

        /** Solo tipografía, filetes, el color y el logo. No usa la foto de portada. */
        void clasica() {
            CabeceraChica chica = new CabeceraChica(MARGEN, LienzoPdf.ANCHO - 2 * MARGEN);
            CabeceraGrande grande = new CabeceraGrande();
            geometria = new Pagina(MARGEN, ANCHO - 2 * MARGEN, MARGEN, ANCHO - 2 * MARGEN) {
                @Override
                public float arriba(int pagina) {
                    return pagina == 1 ? grande.arriba : chica.arriba;
                }

                @Override
                public void decorar(LienzoPdf l, int pagina) {
                    if (pagina == 1) {
                        grande.dibujar(l);
                    } else {
                        chica.dibujar(l);
                    }
                }
            };
            FlujoPdf flujo = new FlujoPdf(lienzo, geometria);
            float hueco = 22.5f;
            poner(flujo, hueco, textoConEtiqueta("Quiénes somos", c.quienesSomos(), TEXTO, LINEA_TEXTO));
            if (c.hayProposito()) {
                poner(flujo, hueco, propositoEnCaja(15.75f, 21.7f, 19.5f, 21f));
            }
            misionYVision(flujo, hueco, false);
            poner(flujo, hueco, beneficios());
            poner(flujo, hueco, dondeEstamos());
            poner(flujo, hueco, contacto());
        }

        // ============================================================== «Portada»

        /**
         * La primera hoja es la foto de portada a toda la página, con un velo oscuro, el logo, el
         * nombre y el propósito encima; sin foto, el color con el logo (o las iniciales) en grande.
         * Las siguientes llevan el resto, con la cabecera pequeña.
         */
        void portada() {
            conHojaDePortada = true;
            CabeceraChica chica = new CabeceraChica(MARGEN, ANCHO - 2 * MARGEN);
            HojaDePortada hoja = new HojaDePortada();
            geometria = new Pagina(MARGEN, ANCHO - 2 * MARGEN, MARGEN, ANCHO - 2 * MARGEN) {
                @Override
                public float arriba(int pagina) {
                    return pagina == 1 ? ARRIBA : chica.arriba;
                }

                @Override
                public void decorar(LienzoPdf l, int pagina) {
                    if (pagina == 1) {
                        hoja.dibujar(l);
                    } else {
                        chica.dibujar(l);
                    }
                }
            };
            FlujoPdf flujo = new FlujoPdf(lienzo, geometria);
            flujo.saltarDePagina();
            float hueco = 25.5f;
            poner(flujo, hueco, textoConEtiqueta("Quiénes somos", c.quienesSomos(), TEXTO, LINEA_TEXTO));
            if (c.hayProposito() && !hoja.conProposito) {
                // No cupo en la portada ni con la letra más pequeña: va aquí, entero.
                poner(flujo, hueco, propositoEnCaja(15.75f, 21.7f, 19.5f, 21f));
            }
            misionYVision(flujo, hueco, true);
            poner(flujo, hueco, beneficios());
            poner(flujo, hueco, dondeEstamos());
            poner(flujo, hueco, contacto());
        }

        // ============================================================== «Columnas»

        /**
         * La estructura del informe por etapa: en la primera hoja, una columna del color con el
         * logo, el nombre, los datos, «Dónde estamos» y el contacto, y a la derecha la foto de
         * portada como franja y los textos; en las siguientes, una franja estrecha del color con
         * el nombre en vertical. Si la columna no da para todo (muchas sedes, muchas redes),
         * «Contacto» y luego «Dónde estamos» pasan a la derecha, antes que salirse de la hoja.
         */
        void columnas() {
            Columna columna = new Columna();
            float xPrimera = COLUMNA + MARGEN_COLUMNAS;
            float xSiguientes = FRANJA + MARGEN_COLUMNAS + 4f;
            geometria = new Pagina(xPrimera, ANCHO - xPrimera - MARGEN_COLUMNAS, xSiguientes,
                    ANCHO - xSiguientes - MARGEN_COLUMNAS) {
                @Override
                public float arriba(int pagina) {
                    return pagina == 1 && foto != null
                            ? 24f + ALTO_BANDA_PORTADA + 19.5f : ARRIBA_COLUMNAS;
                }

                @Override
                public void decorar(LienzoPdf l, int pagina) {
                    if (pagina == 1) {
                        columna.dibujar(l);
                        if (foto != null) {
                            l.imagen(foto, x(1), 24f, ancho(1), ALTO_BANDA_PORTADA, 2.25f);
                        }
                    } else {
                        l.rectangulo(0, 0, FRANJA, ALTO, tono.color());
                        l.textoVertical(FRANJA / 2f + 3f, ALTO - 36f,
                                EstiloPdf.voz(7.9f, tono.blancoAl(0.7f)).espaciado(0.1f), c.nombre());
                    }
                }
            };
            FlujoPdf flujo = new FlujoPdf(lienzo, geometria);
            float hueco = 19.5f;
            poner(flujo, hueco, textoConEtiqueta("Quiénes somos", c.quienesSomos(), TEXTO_COLUMNAS,
                    LINEA_TEXTO_COLUMNAS));
            if (c.hayProposito()) {
                poner(flujo, hueco, propositoEnCaja(13.5f, 18.9f, 15f, 16.5f));
            }
            misionYVision(flujo, hueco, false);
            poner(flujo, hueco, beneficios());
            if (!columna.conDonde) {
                poner(flujo, hueco, dondeEstamos());
            }
            if (!columna.conContacto) {
                poner(flujo, hueco, contacto());
            }
        }

        // ============================================================== el pie

        /**
         * El pie de cada hoja con texto, cuando ya se sabe cuántas son: el nombre y «Página N de M»;
         * en la última, «Brochure generado con EX · {fecha}». La portada de «Portada» no lo lleva.
         */
        void numerar() {
            int total = lienzo.paginas();
            for (int i = 1; i <= total; i++) {
                if (conHojaDePortada && i == 1) {
                    continue;
                }
                lienzo.enPagina(i);
                float x = geometria.x(i);
                float ancho = geometria.ancho(i);
                lienzo.linea(x, FILETE_DEL_PIE, x + ancho, FILETE_DEL_PIE, FILETE, 0.75f);
                String pagina = "Página " + i + " de " + total;
                lienzo.textoDerecha(x + ancho, FILETE_DEL_PIE + 8f, PIE, pagina);
                String izquierda = i == total
                        ? "Brochure generado con EX · " + c.fecha() : c.nombre();
                // Un nombre que no cabe junto al número de página no se corta: ya está arriba.
                if (t.ancho(izquierda, PIE) + t.ancho(pagina, PIE) + 18f <= ancho) {
                    lienzo.texto(x, FILETE_DEL_PIE + 8f, PIE, izquierda);
                }
            }
        }

        // ============================================================== las secciones

        private void poner(FlujoPdf flujo, float hueco, FlujoPdf.Pieza pieza) {
            if (pieza == null) {
                return;
            }
            flujo.separar(hueco);
            flujo.poner(pieza);
        }

        /** Un título pequeño en mayúsculas y su texto, en renglones que pueden pasar de hoja. */
        private FlujoPdf.Pieza textoConEtiqueta(String titulo, String texto, EstiloPdf estilo,
                                                float interlineado) {
            if (!hay(texto)) {
                return null;
            }
            return (tipografia, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                r.add(etiqueta(titulo, estiloEtiqueta(), 0f));
                r.addAll(parrafo(tipografia, texto, estilo, ancho, interlineado, 0f, null));
                return r;
            };
        }

        /** «Nuestro propósito» en su caja del halo del color, con la letra grande si es corto. */
        private FlujoPdf.Pieza propositoEnCaja(float tamano, float interlineado, float rellenoV,
                                               float rellenoH) {
            String texto = c.proposito();
            boolean largo = texto.strip().length() > PROPOSITO_LARGO;
            EstiloPdf estilo = largo ? EstiloPdf.texto(10.8f, tono.oscuro())
                    : EstiloPdf.texto(tamano, tono.oscuro()).espaciado(-0.02f);
            float linea = largo ? 16.8f : interlineado;
            Color fondo = tono.halo();
            return (tipografia, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                r.add(conFondo(hueco(rellenoV), fondo));
                r.add(conFondo(etiqueta("Nuestro propósito",
                        EstiloPdf.voz(7.9f, tono.oscuro()).espaciado(0.1f), rellenoH), fondo));
                for (Renglon renglon : parrafo(tipografia, texto, estilo, ancho - rellenoH, linea,
                        rellenoH, null)) {
                    r.add(conFondo(renglon, fondo));
                }
                r.add(conFondo(hueco(rellenoV), fondo));
                return r;
            };
        }

        /**
         * «Misión» y «Visión»: con las dos, una al lado de la otra; con una sola, a todo el ancho y
         * sin el título de la otra. En «Portada» van en tarjetas del halo del color.
         */
        private void misionYVision(FlujoPdf flujo, float hueco, boolean enTarjetas) {
            boolean mision = c.hayMision();
            boolean vision = c.hayVision();
            if (mision && vision) {
                FlujoPdf.Pieza juntas = dosColumnas(enTarjetas);
                // Dos columnas no se parten entre hojas: si fueran más altas que media hoja (un
                // texto lleno de saltos de línea), van una debajo de otra para poder pasar de hoja.
                if (flujo.alto(juntas) <= (ABAJO - ARRIBA) * 0.6f) {
                    poner(flujo, hueco, juntas);
                    return;
                }
            }
            if (mision) {
                poner(flujo, hueco, enTarjetas ? tarjeta("Misión", c.mision())
                        : textoConEtiqueta("Misión", c.mision(), estiloTexto(), lineaTexto()));
            }
            if (vision) {
                poner(flujo, hueco, enTarjetas ? tarjeta("Visión", c.vision())
                        : textoConEtiqueta("Visión", c.vision(), estiloTexto(), lineaTexto()));
            }
        }

        private EstiloPdf estiloTexto() {
            return c.plantilla() == PlantillaDelBrochure.COLUMNAS ? TEXTO_COLUMNAS : TEXTO;
        }

        private float lineaTexto() {
            return c.plantilla() == PlantillaDelBrochure.COLUMNAS ? LINEA_TEXTO_COLUMNAS : LINEA_TEXTO;
        }

        /** Las dos, lado a lado en un solo bloque que no se parte. */
        private FlujoPdf.Pieza dosColumnas(boolean enTarjetas) {
            EstiloPdf estilo = enTarjetas ? estiloTexto().conColor(tono.oscuro()) : estiloTexto();
            float linea = lineaTexto();
            return (tipografia, ancho) -> {
                float separacion = enTarjetas ? 15f : 34f;
                float columna = (ancho - separacion) / 2f;
                float relleno = enTarjetas ? 18f : 0f;
                float rellenoV = enTarjetas ? 16.5f : 0f;
                List<String> izquierda = RenglonesPdf.partir(tipografia, c.mision(), estilo,
                        columna - 2 * relleno);
                List<String> derecha = RenglonesPdf.partir(tipografia, c.vision(), estilo,
                        columna - 2 * relleno);
                float alto = 2 * rellenoV + 15.5f + Math.max(izquierda.size(), derecha.size()) * linea;
                return List.of(new R(alto, (l, x, y, w) -> {
                    float x2 = x + columna + separacion;
                    if (enTarjetas) {
                        l.rectanguloRedondeado(x, y, columna, alto, 3f, tono.halo(), null, 0f);
                        l.rectanguloRedondeado(x2, y, columna, alto, 3f, tono.halo(), null, 0f);
                    } else {
                        l.linea(x + columna + separacion / 2f, y, x + columna + separacion / 2f,
                                y + alto, FILETE, 0.75f);
                    }
                    columnaDeTexto(l, x + relleno, y + rellenoV, "Misión", izquierda, estilo, linea);
                    columnaDeTexto(l, x2 + relleno, y + rellenoV, "Visión", derecha, estilo, linea);
                }));
            };
        }

        private void columnaDeTexto(LienzoPdf l, float x, float y, String titulo,
                                    List<String> lineas, EstiloPdf estilo, float linea) {
            l.texto(x, y, estiloEtiqueta(), titulo);
            float yy = y + 15.5f;
            for (String texto : lineas) {
                l.texto(x, yy + (linea - estilo.tamano()) / 2f, estilo, texto);
                yy += linea;
            }
        }

        /** Una sola, en su tarjeta a todo el ancho: renglones que pueden pasar de hoja. */
        private FlujoPdf.Pieza tarjeta(String titulo, String texto) {
            Color fondo = tono.halo();
            EstiloPdf estilo = TEXTO.conColor(tono.oscuro());
            return (tipografia, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                r.add(conFondo(hueco(16.5f), fondo));
                r.add(conFondo(etiqueta(titulo, estiloEtiqueta(), 18f), fondo));
                for (Renglon renglon : parrafo(tipografia, texto, estilo, ancho - 18f, LINEA_TEXTO,
                        18f, null)) {
                    r.add(conFondo(renglon, fondo));
                }
                r.add(conFondo(hueco(16.5f), fondo));
                return r;
            };
        }

        /** Los beneficios como fichas con su marca, en filas que se parten si hace falta. */
        private FlujoPdf.Pieza beneficios() {
            if (c.beneficios().isEmpty()) {
                return null;
            }
            return (tipografia, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                r.add(etiqueta("Beneficios", estiloEtiqueta(), 0f));
                r.addAll(filasDeFichas(tipografia, ancho));
                return r;
            };
        }

        private List<Renglon> filasDeFichas(TipografiaPdf tipografia, float ancho) {
            float separacion = 6f;
            float sangria = 6.75f + 9.75f + 4.5f;
            List<Renglon> filas = new ArrayList<>();
            List<float[]> medidas = new ArrayList<>();
            List<List<String>> textos = new ArrayList<>();
            float usado = 0f;
            float altoFila = 0f;
            for (String beneficio : c.beneficios()) {
                List<String> lineas = RenglonesPdf.partir(tipografia, beneficio, CHIP,
                        ancho - sangria - 8.25f);
                float w = sangria + 8.25f;
                for (String linea : lineas) {
                    w = Math.max(w, sangria + tipografia.ancho(linea, CHIP) + 8.25f);
                }
                float h = 9f + 12f * Math.max(1, lineas.size());
                if (!medidas.isEmpty() && usado + w > ancho) {
                    filas.add(filaDeFichas(medidas, textos, altoFila, separacion));
                    medidas = new ArrayList<>();
                    textos = new ArrayList<>();
                    usado = 0f;
                    altoFila = 0f;
                }
                medidas.add(new float[] {usado, w, h});
                textos.add(lineas);
                usado += w + separacion;
                altoFila = Math.max(altoFila, h);
            }
            if (!medidas.isEmpty()) {
                filas.add(filaDeFichas(medidas, textos, altoFila, separacion));
            }
            return filas;
        }

        private Renglon filaDeFichas(List<float[]> medidas, List<List<String>> textos, float altoFila,
                                     float separacion) {
            Color marca = tono.color();
            return new R(altoFila + separacion, (l, x, y, w) -> {
                for (int i = 0; i < medidas.size(); i++) {
                    float[] m = medidas.get(i);
                    float fx = x + m[0];
                    l.rectanguloRedondeado(fx, y, m[1], m[2], 3f, null, BORDE, 0.75f);
                    float ix = fx + 6.75f;
                    float iy = y + 5.6f;
                    float k = 9.75f / 24f;
                    l.polilinea(marca, 1f, ix + 5f * k, iy + 12.8f * k, ix + 9.5f * k, iy + 17.3f * k,
                            ix + 19f * k, iy + 7.5f * k);
                    float ty = y + 4.5f;
                    for (String linea : textos.get(i)) {
                        l.texto(fx + 6.75f + 9.75f + 4.5f, ty + 1.5f, CHIP, linea);
                        ty += 12f;
                    }
                }
            });
        }

        /** Las provincias de las sedes activas, sin direcciones, cada una con su marcador. */
        private FlujoPdf.Pieza dondeEstamos() {
            if (c.provincias().isEmpty()) {
                return null;
            }
            return (tipografia, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                r.add(etiqueta("Dónde estamos", estiloEtiqueta(), 0f));
                List<String> fila = new ArrayList<>();
                float usado = 0f;
                for (String provincia : c.provincias()) {
                    float w = 16.5f + Math.min(tipografia.ancho(provincia, SEDE), ancho - 16.5f);
                    if (!fila.isEmpty() && usado + w > ancho) {
                        r.addAll(filaDeSedes(tipografia, fila, ancho));
                        fila = new ArrayList<>();
                        usado = 0f;
                    }
                    fila.add(provincia);
                    usado += w + 18f;
                }
                if (!fila.isEmpty()) {
                    r.addAll(filaDeSedes(tipografia, fila, ancho));
                }
                return r;
            };
        }

        private List<Renglon> filaDeSedes(TipografiaPdf tipografia, List<String> fila, float ancho) {
            Color marca = tono.color();
            // Una provincia más larga que el renglón entero (no la hay, pero no se corta) se
            // parte en varias líneas debajo de su marcador.
            List<List<String>> partidas = new ArrayList<>();
            int lineas = 1;
            for (String provincia : fila) {
                List<String> trozos = RenglonesPdf.partir(tipografia, provincia, SEDE, ancho - 16.5f);
                partidas.add(trozos);
                lineas = Math.max(lineas, trozos.size());
            }
            float alto = 6f + 15f * lineas;
            return List.of(new R(alto, (l, x, y, w) -> {
                float fx = x;
                for (List<String> trozos : partidas) {
                    marcador(l, fx, y + 1f, marca);
                    float ty = y;
                    float ancho1 = 0f;
                    for (String trozo : trozos) {
                        ancho1 = Math.max(ancho1, l.texto(fx + 16.5f, ty + 2f, SEDE, trozo));
                        ty += 15f;
                    }
                    fx += 16.5f + ancho1 + 18f;
                }
            }));
        }

        /** «Contacto»: una fila por enlace, con su nombre y la dirección escrita que se pulsa. */
        private FlujoPdf.Pieza contacto() {
            if (c.contacto().isEmpty()) {
                return null;
            }
            return (tipografia, ancho) -> {
                List<Renglon> r = new ArrayList<>();
                r.add(etiqueta("Contacto", estiloEtiqueta(), 0f));
                boolean primera = true;
                for (Contacto enlace : c.contacto()) {
                    r.add(filaDeContacto(tipografia, enlace, ancho, primera));
                    primera = false;
                }
                return r;
            };
        }

        private Renglon filaDeContacto(TipografiaPdf tipografia, Contacto enlace, float ancho,
                                       boolean primera) {
            float relleno = 7.5f;
            float xRotulo = 24f;
            float xDireccion = Math.min(129f, ancho * 0.36f);
            EstiloPdf direccion = EstiloPdf.texto(9.75f, tono.color());
            List<String> rotulo = RenglonesPdf.partir(tipografia, enlace.rotulo(), ROTULO_CONTACTO,
                    xDireccion - xRotulo - 6f);
            List<String> escrito = RenglonesPdf.partir(tipografia, enlace.escrito(), direccion,
                    ancho - xDireccion);
            float alto = 2 * relleno + 13.5f * Math.max(rotulo.size(), escrito.size());
            Color marca = tono.color();
            return new R(alto, (l, x, y, w) -> {
                if (primera) {
                    l.linea(x, y, x + w, y, FILETE, 0.75f);
                }
                globo(l, x + 1f, y + relleno + 0.5f, marca);
                float ty = y + relleno;
                for (String linea : rotulo) {
                    l.texto(x + xRotulo, ty + 2f, ROTULO_CONTACTO, linea);
                    ty += 13.5f;
                }
                ty = y + relleno;
                float mayor = 0f;
                for (String linea : escrito) {
                    mayor = Math.max(mayor, l.texto(x + xDireccion, ty + 2f, direccion, linea));
                    ty += 13.5f;
                }
                l.enlace(x + xDireccion, y + relleno, mayor, 13.5f * escrito.size(), enlace.url());
                l.linea(x, y + alto, x + w, y + alto, FILETE, 0.75f);
            });
        }

        // ============================================================== la cabecera grande

        /** La de la primera hoja de «Clásica»: el logo, el rótulo, el nombre, la línea y el filete. */
        private final class CabeceraGrande {
            private final float x = MARGEN;
            private final float ancho = ANCHO - 2 * MARGEN;
            private final float xTexto = x + LOGO_GRANDE + 16.5f;
            private final EstiloPdf nombre;
            private final List<String> lineasNombre;
            private final List<String> lineasDatos;
            private final float alto;
            private final float altoTexto;
            final float arriba;
            private static final EstiloPdf DATOS = EstiloPdf.texto(9.75f, GRIS_MEDIO);

            CabeceraGrande() {
                float anchoTexto = ancho - (xTexto - x);
                nombre = ajustar(c.nombre(), EstiloPdf.texto(30f, TINTA).espaciado(-0.05f),
                        anchoTexto, 3, 18f);
                lineasNombre = RenglonesPdf.partir(t, c.nombre(), nombre, anchoTexto);
                lineasDatos = RenglonesPdf.partir(t, c.linea(), DATOS, anchoTexto);
                float h = 7.9f + 4.5f + lineasNombre.size() * nombre.tamano() * 1.04f;
                if (!lineasDatos.isEmpty()) {
                    h += 6f + lineasDatos.size() * 13f;
                }
                altoTexto = h;
                alto = Math.max(LOGO_GRANDE, h);
                arriba = ARRIBA + alto + 21f + 1.5f + 22.5f;
            }

            void dibujar(LienzoPdf l) {
                float yLogo = ARRIBA + (alto - LOGO_GRANDE) / 2f;
                logoOIniciales(l, x, yLogo, LOGO_GRANDE, 6f, true);
                float y = ARRIBA + (alto - altoTexto) / 2f;
                l.texto(xTexto, y, estiloEtiqueta(), PRESENTACION);
                y += 7.9f + 4.5f;
                float linea = nombre.tamano() * 1.04f;
                for (String trozo : lineasNombre) {
                    l.texto(xTexto, y + (linea - nombre.tamano()) / 2f, nombre, trozo);
                    y += linea;
                }
                if (!lineasDatos.isEmpty()) {
                    y += 6f;
                    for (String trozo : lineasDatos) {
                        l.texto(xTexto, y, DATOS, trozo);
                        y += 13f;
                    }
                }
                l.rectangulo(x, ARRIBA + alto + 21f, ancho, 1.5f, tono.color());
            }
        }

        // ============================================================== la cabecera chica

        /** La de las hojas siguientes de «Clásica» y «Portada»: el nombre, el logo y el filete. */
        private final class CabeceraChica {
            private final float x;
            private final float ancho;
            private final List<String> lineas;
            private final float alto;
            final float arriba;
            private static final EstiloPdf NOMBRE = EstiloPdf.texto(11.25f, TINTA).espaciado(-0.02f);

            CabeceraChica(float x, float ancho) {
                this.x = x;
                this.ancho = ancho;
                lineas = RenglonesPdf.partir(t, c.nombre(), NOMBRE, ancho - LOGO_CHICO - 12f);
                alto = Math.max(LOGO_CHICO, lineas.size() * 14f);
                arriba = ARRIBA + alto + 10.5f + 1.5f + 22.5f;
            }

            void dibujar(LienzoPdf l) {
                float y = ARRIBA + (alto - lineas.size() * 14f) / 2f;
                for (String trozo : lineas) {
                    l.texto(x, y + 1.5f, NOMBRE, trozo);
                    y += 14f;
                }
                logoOIniciales(l, x + ancho - LOGO_CHICO, ARRIBA + (alto - LOGO_CHICO) / 2f,
                        LOGO_CHICO, 3.75f, true);
                l.rectangulo(x, ARRIBA + alto + 10.5f, ancho, 1.5f, tono.color());
            }
        }

        // ============================================================== la hoja de portada

        /**
         * La primera hoja de «Portada». Abajo, el bloque con el logo, el nombre, la línea de datos
         * y el propósito; el propósito baja de tamaño hasta caber y, si ni así cabe, pasa entero a
         * la hoja siguiente (lo dice {@link #conProposito}).
         */
        private final class HojaDePortada {
            private static final float BAJO = 63f;
            private static final float CAJA = 165f;
            private static final float Y_CAJA = 142.5f;
            private final float ancho = ANCHO - 2 * MARGEN;
            private final Color fondo;
            private final EstiloPdf nombre;
            private final List<String> lineasNombre;
            private final List<String> lineasDatos;
            /** Si el logo (o las iniciales) va en la caja grande de arriba, sin foto de portada. */
            private final boolean cajaGrande;
            boolean conProposito;
            private EstiloPdf estiloProposito;
            private List<String> lineasProposito = List.of();
            private float lineaProposito;
            private static final EstiloPdf DATOS = EstiloPdf.texto(11.25f, BLANCO);

            HojaDePortada() {
                fondo = foto != null ? velo() : tono.color();
                nombre = ajustar(c.nombre(), EstiloPdf.texto(45f, BLANCO).espaciado(-0.05f), ancho,
                        3, 24f);
                lineasNombre = RenglonesPdf.partir(t, c.nombre(), nombre, ancho);
                lineasDatos = RenglonesPdf.partir(t, c.linea(), DATOS, ancho);
                float techo = foto != null ? 80f : Y_CAJA + CAJA + 30f;
                boolean caja = foto == null;
                if (c.hayProposito()) {
                    boolean cupo = acomodarProposito(ALTO - BAJO - techo - bloqueSinProposito(caja));
                    if (!cupo && caja) {
                        // Sin foto, la caja grande deja poco sitio: el logo va pequeño en el
                        // bloque. Si ni así cabe, el propósito pasa a la hoja siguiente y la
                        // caja grande se queda.
                        cupo = acomodarProposito(ALTO - BAJO - 80f - bloqueSinProposito(false));
                        caja = !cupo;
                    }
                }
                cajaGrande = caja;
            }

            /** Lo que mide el bloque de abajo sin el propósito. */
            private float bloqueSinProposito(boolean caja) {
                float h = caja ? 0f : 72f + 19.5f;
                h += lineasNombre.size() * nombre.tamano();
                if (!lineasDatos.isEmpty()) {
                    h += 10.5f + lineasDatos.size() * 15f;
                }
                return h;
            }

            /** Busca el tamaño de letra con el que el propósito cabe en ese alto. */
            private boolean acomodarProposito(float disponible) {
                float[][] tamanos = {{16.5f, 22.4f, 420f}, {14f, 19.5f, ancho}, {12.5f, 17.5f, ancho},
                        {11f, 15.5f, ancho}, {10f, 14f, ancho}};
                for (float[] tamano : tamanos) {
                    EstiloPdf estilo = EstiloPdf.texto(tamano[0], BLANCO).espaciado(-0.02f);
                    List<String> lineas = RenglonesPdf.partir(t, c.proposito(), estilo, tamano[2]);
                    float alto = 25.5f + 16.5f + 7.9f + 7.5f + lineas.size() * tamano[1];
                    if (alto <= disponible) {
                        conProposito = true;
                        estiloProposito = estilo;
                        lineasProposito = lineas;
                        lineaProposito = tamano[1];
                        return true;
                    }
                }
                conProposito = false;
                return false;
            }

            void dibujar(LienzoPdf l) {
                if (foto != null) {
                    l.imagen(foto, 0f, 0f, ANCHO, ALTO, 0f);
                    pintarVelo(l);
                } else {
                    l.rectangulo(0f, 0f, ANCHO, ALTO, tono.color());
                }
                EstiloPdf arriba = EstiloPdf.voz(7.9f, mezcla(BLANCO, fondo, foto != null ? 0.9f : 0.86f))
                        .espaciado(0.1f);
                l.texto(MARGEN, 36f, arriba, PRESENTACION);
                if (hay(c.sector()) && t.ancho(PRESENTACION, arriba) + t.ancho(c.sector(), arriba) + 24f
                        <= ancho) {
                    l.textoDerecha(ANCHO - MARGEN, 36f, arriba, c.sector());
                }
                if (cajaGrande) {
                    cajaGrande(l);
                }
                bloque(l);
                l.rectangulo(0f, ALTO - 9f, ANCHO, 9f, foto != null ? tono.color() : tono.oscuro());
            }

            private void cajaGrande(LienzoPdf l) {
                if (logo != null) {
                    l.rectanguloRedondeado(MARGEN - 3f, Y_CAJA - 3f, CAJA + 6f, CAJA + 6f, 12f, BLANCO,
                            null, 0f);
                    l.imagen(logo, MARGEN, Y_CAJA, CAJA, CAJA, 9f);
                    return;
                }
                l.rectanguloRedondeado(MARGEN, Y_CAJA, CAJA, CAJA, 9f, tono.blancoAl(0.12f),
                        tono.blancoAl(0.24f), 0.75f);
                EstiloPdf letras = EstiloPdf.texto(72f, BLANCO).medio().espaciado(-0.04f);
                l.textoCentrado(MARGEN + CAJA / 2f, Y_CAJA + CAJA / 2f - 72f * 0.4f, letras,
                        c.iniciales());
            }

            /** Lo que mide el bloque de abajo tal como se va a dibujar. */
            private float altoDelBloque() {
                return bloqueSinProposito(cajaGrande)
                        + (conProposito ? 25.5f + 16.5f + 7.9f + 7.5f
                        + lineasProposito.size() * lineaProposito : 0f);
            }

            private void bloque(LienzoPdf l) {
                float y = ALTO - BAJO - altoDelBloque();
                if (!cajaGrande) {
                    if (logo != null) {
                        l.rectanguloRedondeado(MARGEN - 3f, y - 3f, 78f, 78f, 8f, BLANCO, null, 0f);
                        l.imagen(logo, MARGEN, y, 72f, 72f, 5f);
                    } else {
                        l.rectanguloRedondeado(MARGEN, y, 72f, 72f, 6f, tono.color(),
                                mezcla(BLANCO, tono.color(), 0.6f), 1.5f);
                        l.textoCentrado(MARGEN + 36f, y + 36f - 26f * 0.45f,
                                EstiloPdf.texto(26f, BLANCO).medio(), c.iniciales());
                    }
                    y += 72f + 19.5f;
                }
                for (String trozo : lineasNombre) {
                    l.texto(MARGEN, y, nombre, trozo);
                    y += nombre.tamano();
                }
                Color suave = mezcla(BLANCO, fondo, 0.8f);
                if (!lineasDatos.isEmpty()) {
                    y += 10.5f;
                    for (String trozo : lineasDatos) {
                        l.texto(MARGEN, y, DATOS.conColor(suave), trozo);
                        y += 15f;
                    }
                }
                if (conProposito) {
                    y += 25.5f;
                    l.linea(MARGEN, y, MARGEN + Math.min(420f, ancho), y,
                            mezcla(BLANCO, fondo, 0.28f), 0.75f);
                    y += 16.5f;
                    l.texto(MARGEN, y, EstiloPdf.voz(7.9f, mezcla(BLANCO, fondo, 0.7f)).espaciado(0.1f),
                            "Nuestro propósito");
                    y += 7.9f + 7.5f;
                    for (String trozo : lineasProposito) {
                        l.texto(MARGEN, y + (lineaProposito - estiloProposito.tamano()) / 2f,
                                estiloProposito, trozo);
                        y += lineaProposito;
                    }
                }
            }

            /** El color del velo: el oscuro del color elegido, más oscuro todavía. */
            private Color velo() {
                return mezcla(Color.BLACK, tono.oscuro(), 0.45f);
            }

            /**
             * El velo, de casi transparente arriba a casi opaco abajo: el degradado de la maqueta
             * sin necesitar sombreados en el PDF. Son capas que empiezan cada vez más abajo y
             * llegan hasta el pie, cada una un poco más opaca que la suma de las de arriba: así no
             * quedan costuras entre franjas. Lo oscuro empieza por encima del bloque de texto, para
             * que un propósito largo se lea igual de bien.
             */
            private void pintarVelo(LienzoPdf l) {
                float inicioDelBloque = ALTO - BAJO - altoDelBloque();
                float oscuro = Math.max(0.2f, Math.min(0.72f, (inicioDelBloque - 12f) / ALTO));
                int capas = 120;
                float paso = ALTO / capas;
                float llevada = 0f;
                for (int i = 0; i < capas; i++) {
                    float objetivo = opacidadDelVelo((i + 0.5f) / capas, oscuro);
                    if (objetivo <= llevada || llevada >= 1f) {
                        continue;
                    }
                    float opacidad = 1f - (1f - objetivo) / (1f - llevada);
                    l.rectangulo(0f, i * paso, ANCHO, ALTO - i * paso, fondo, opacidad);
                    llevada = objetivo;
                }
            }
        }

        // ============================================================== la columna

        /**
         * La columna del color de la primera hoja de «Columnas». Primero se mide: si no cabe
         * entera, «Contacto» y después «Dónde estamos» pasan al texto de la derecha.
         */
        private final class Columna {
            private static final float X = 19.5f;
            private static final float ANCHO_COLUMNA = COLUMNA - 2 * X;
            private final EstiloPdf nombre;
            boolean conDonde = true;
            boolean conContacto = true;

            Columna() {
                nombre = ajustar(c.nombre(), EstiloPdf.texto(22.5f, BLANCO).espaciado(-0.05f),
                        ANCHO_COLUMNA, 4, 13f);
                float limite = ALTO - 21f - 22f;
                if (recorrer(null) > limite) {
                    conContacto = false;
                    if (recorrer(null) > limite) {
                        conDonde = false;
                    }
                }
            }

            void dibujar(LienzoPdf l) {
                l.rectangulo(0f, 0f, COLUMNA, ALTO, tono.color());
                recorrer(l);
                l.texto(X, ALTO - 21f - 7.1f, EstiloPdf.voz(7.1f, tono.blancoAl(0.56f)),
                        conLoQueCabe(c.nombre(), EstiloPdf.voz(7.1f, BLANCO), ANCHO_COLUMNA));
            }

            /**
             * Dibuja la columna (o solo la mide, con el lienzo nulo) y devuelve dónde acaba. Medir
             * y dibujar van por el mismo camino: así no pueden decir cosas distintas.
             */
            private float recorrer(LienzoPdf l) {
                float y = 30f;
                if (l != null) {
                    logoOIniciales(l, X, y, 54f, 6f, false);
                }
                y += 54f + 18f;
                y = rotulo(l, y, PRESENTACION) + 6f;
                for (String trozo : RenglonesPdf.partir(t, c.nombre(), nombre, ANCHO_COLUMNA)) {
                    if (l != null) {
                        l.texto(X, y, nombre, trozo);
                    }
                    y += nombre.tamano();
                }
                y = datos(l, y + 18f);
                if (conDonde && !c.provincias().isEmpty()) {
                    y = rotulo(l, y, "Dónde estamos") + 6f;
                    y = lista(l, y, c.provincias(), true);
                    y += 18f;
                }
                if (conContacto && !c.contacto().isEmpty()) {
                    y = rotulo(l, y, "Contacto") + 6f;
                    y = enlaces(l, y);
                }
                return y;
            }

            private float rotulo(LienzoPdf l, float y, String texto) {
                if (l != null) {
                    l.texto(X, y, rotuloEstilo(), texto);
                }
                return y + 7.1f;
            }

            private EstiloPdf rotuloEstilo() {
                return EstiloPdf.voz(7.1f, tono.blancoAl(0.6f)).espaciado(0.1f);
            }

            /** Sector, tamaño y año, cada uno con su filete encima; los que no hay, no salen. */
            private float datos(LienzoPdf l, float y) {
                String[][] filas = {{"Sector", c.sector()}, {"Tamaño", c.tamanoTexto()},
                        {"Fundada en", c.anioFundacion() == null ? null : String.valueOf(c.anioFundacion())}};
                EstiloPdf valor = EstiloPdf.texto(9f, BLANCO);
                boolean alguno = false;
                for (String[] fila : filas) {
                    if (!hay(fila[1])) {
                        continue;
                    }
                    alguno = true;
                    if (l != null) {
                        l.linea(X, y, X + ANCHO_COLUMNA, y, tono.blancoAl(0.14f), 0.75f);
                    }
                    y += 6.75f;
                    y = rotulo(l, y, fila[0]) + 3f;
                    for (String trozo : RenglonesPdf.partir(t, fila[1], valor, ANCHO_COLUMNA)) {
                        if (l != null) {
                            l.texto(X, y, valor, trozo);
                        }
                        y += 12f;
                    }
                    y += 7.5f;
                }
                return alguno ? y + 10.5f : y;
            }

            private float lista(LienzoPdf l, float y, List<String> elementos, boolean conMarcador) {
                if (l != null) {
                    l.linea(X, y, X + ANCHO_COLUMNA, y, tono.blancoAl(0.14f), 0.75f);
                }
                y += 9f;
                EstiloPdf estilo = EstiloPdf.texto(9f, BLANCO);
                for (String elemento : elementos) {
                    float sangria = conMarcador ? 13.5f : 0f;
                    for (String trozo : RenglonesPdf.partir(t, elemento, estilo, ANCHO_COLUMNA - sangria)) {
                        if (l != null) {
                            l.texto(X + sangria, y, estilo, trozo);
                        }
                        y += 12f;
                    }
                    if (l != null && conMarcador) {
                        marcadorChico(l, X, y - 12f, BLANCO);
                    }
                    y += 4.5f;
                }
                return y;
            }

            /** Cada enlace con su nombre que se pulsa y, debajo, la dirección escrita. */
            private float enlaces(LienzoPdf l, float y) {
                if (l != null) {
                    l.linea(X, y, X + ANCHO_COLUMNA, y, tono.blancoAl(0.14f), 0.75f);
                }
                y += 9f;
                EstiloPdf rotulo = EstiloPdf.texto(9f, tono.enlace());
                EstiloPdf escrito = EstiloPdf.texto(7.6f, tono.blancoAl(0.66f));
                for (Contacto enlace : c.contacto()) {
                    float inicio = y;
                    float mayor = 0f;
                    for (String trozo : RenglonesPdf.partir(t, enlace.rotulo(), rotulo, ANCHO_COLUMNA)) {
                        if (l != null) {
                            mayor = Math.max(mayor, l.texto(X, y, rotulo, trozo));
                        }
                        y += 12f;
                    }
                    for (String trozo : RenglonesPdf.partir(t, enlace.escrito(), escrito, ANCHO_COLUMNA)) {
                        if (l != null) {
                            mayor = Math.max(mayor, l.texto(X, y, escrito, trozo));
                        }
                        y += 10f;
                    }
                    if (l != null) {
                        l.enlace(X, inicio - 1f, mayor, y - inicio, enlace.url());
                    }
                    y += 5f;
                }
                return y;
            }
        }

        // ============================================================== logo e iniciales

        /**
         * El logo en su recuadro, o las iniciales si no hay: sobre el color (en la página blanca)
         * o sobre el halo (en la columna del color). Nunca un recuadro vacío.
         */
        private void logoOIniciales(LienzoPdf l, float x, float y, float lado, float radio,
                                    boolean sobreBlanco) {
            if (logo != null) {
                if (!sobreBlanco) {
                    l.rectanguloRedondeado(x - 2.25f, y - 2.25f, lado + 4.5f, lado + 4.5f,
                            radio + 2f, BLANCO, null, 0f);
                }
                l.imagen(logo, x, y, lado, lado, radio);
                if (sobreBlanco) {
                    l.rectanguloRedondeado(x, y, lado, lado, radio, null, BORDE, 0.75f);
                }
                return;
            }
            Color fondo = sobreBlanco ? tono.color() : tono.halo();
            Color tinta = sobreBlanco ? BLANCO : tono.oscuro();
            l.rectanguloRedondeado(x, y, lado, lado, radio, fondo, null, 0f);
            float tamano = lado * 0.36f;
            l.textoCentrado(x + lado / 2f, y + lado / 2f - tamano * 0.45f,
                    EstiloPdf.texto(tamano, tinta).medio(), c.iniciales());
        }

        // ============================================================== ayudas de medida

        /** El estilo con el tamaño más grande con el que el texto cabe en {@code maxLineas}. */
        private EstiloPdf ajustar(String texto, EstiloPdf base, float ancho, int maxLineas,
                                  float minimo) {
            EstiloPdf estilo = base;
            while (estilo.tamano() > minimo
                    && RenglonesPdf.partir(t, texto, estilo, ancho).size() > maxLineas) {
                estilo = estilo.conTamano(Math.max(minimo, estilo.tamano() * 0.9f));
            }
            return estilo;
        }

        /** El texto si cabe en una línea; si no, nada (es un rótulo que ya está en otro sitio). */
        private String conLoQueCabe(String texto, EstiloPdf estilo, float ancho) {
            return t.ancho(texto, estilo) <= ancho ? texto : "";
        }
    }

    // ================================================================== la página

    /**
     * La geometría de una plantilla: dónde se escribe en la primera hoja y en las demás. Lo que
     * cambia según la plantilla —lo de arriba y lo que se dibuja detrás— lo pone cada una.
     */
    private abstract static class Pagina implements FlujoPdf.Geometria {
        private final float xPrimera;
        private final float anchoPrimera;
        private final float xSiguientes;
        private final float anchoSiguientes;

        Pagina(float xPrimera, float anchoPrimera, float xSiguientes, float anchoSiguientes) {
            this.xPrimera = xPrimera;
            this.anchoPrimera = anchoPrimera;
            this.xSiguientes = xSiguientes;
            this.anchoSiguientes = anchoSiguientes;
        }

        @Override
        public float x(int pagina) {
            return pagina == 1 ? xPrimera : xSiguientes;
        }

        @Override
        public float ancho(int pagina) {
            return pagina == 1 ? anchoPrimera : anchoSiguientes;
        }

        @Override
        public float abajo(int pagina) {
            return ABAJO;
        }
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

    private static Renglon etiqueta(String texto, EstiloPdf estilo, float sangria) {
        return new R(15.5f, (l, x, y, w) -> l.texto(x + sangria, y, estilo, texto));
    }

    private static List<Renglon> parrafo(TipografiaPdf t, String texto, EstiloPdf estilo, float ancho,
                                         float alto, float sangria, Color fondo) {
        List<Renglon> r = new ArrayList<>();
        float bajada = (alto - estilo.tamano()) / 2f;
        for (String linea : RenglonesPdf.partir(t, texto, estilo, ancho - sangria)) {
            r.add(conFondo(new R(alto, (l, x, y, w) -> l.texto(x + sangria, y + bajada, estilo, linea)),
                    fondo));
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

    /** El marcador de lugar: una gota con su punto, de 12 pt. */
    private static void marcador(LienzoPdf l, float x, float y, Color color) {
        l.circulo(x + 6f, y + 5f, 3.6f, null, color, 1.1f);
        l.polilinea(color, 1.1f, x + 2.9f, y + 6.9f, x + 6f, y + 11.5f, x + 9.1f, y + 6.9f);
        l.circulo(x + 6f, y + 5f, 1.1f, color, null, 0f);
    }

    private static void marcadorChico(LienzoPdf l, float x, float y, Color color) {
        l.circulo(x + 4.5f, y + 4f, 2.8f, null, color, 0.9f);
        l.circulo(x + 4.5f, y + 4f, 0.8f, color, null, 0f);
    }

    /** El icono de enlace: un globo con su ecuador. */
    private static void globo(LienzoPdf l, float x, float y, Color color) {
        l.circulo(x + 6f, y + 6f, 5.5f, null, color, 1f);
        l.linea(x + 0.5f, y + 6f, x + 11.5f, y + 6f, color, 0.8f);
        l.linea(x + 6f, y + 0.5f, x + 6f, y + 11.5f, color, 0.8f);
    }

    /** La opacidad del velo a esa altura (0 arriba, 1 abajo), con los cortes de la maqueta. */
    static float opacidadDelVelo(float f) {
        return opacidadDelVelo(f, 0.72f);
    }

    /**
     * Lo mismo, con lo oscuro (0,9) empezando a la altura {@code oscuro}: la maqueta lo pone al
     * 72 %, y sube si el bloque de texto de la portada empieza más arriba.
     */
    static float opacidadDelVelo(float f, float oscuro) {
        float[][] cortes = {{0f, 0.15f}, {oscuro * 0.53f, 0.35f}, {oscuro, 0.9f}, {1f, 0.96f}};
        for (int i = 1; i < cortes.length; i++) {
            if (f <= cortes[i][0]) {
                float tramo = (f - cortes[i - 1][0]) / (cortes[i][0] - cortes[i - 1][0]);
                return cortes[i - 1][1] + tramo * (cortes[i][1] - cortes[i - 1][1]);
            }
        }
        return cortes[cortes.length - 1][1];
    }

    /** {@code a} sobre {@code b} con esa opacidad, ya mezclado. */
    static Color mezcla(Color a, Color b, float opacidad) {
        float o = Math.max(0f, Math.min(1f, opacidad));
        return new Color(
                Math.round(a.getRed() * o + b.getRed() * (1 - o)),
                Math.round(a.getGreen() * o + b.getGreen() * (1 - o)),
                Math.round(a.getBlue() * o + b.getBlue() * (1 - o)));
    }
}
