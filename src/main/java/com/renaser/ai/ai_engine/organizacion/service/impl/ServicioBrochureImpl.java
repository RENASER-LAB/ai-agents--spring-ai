package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.entity.OrganizacionBeneficio;
import com.renaser.ai.ai_engine.organizacion.entity.OrganizacionRedSocial;
import com.renaser.ai.ai_engine.organizacion.entity.Sede;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionBeneficioRepository;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRedSocialRepository;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.repository.SedeRepository;
import com.renaser.ai.ai_engine.organizacion.service.ColorDelBrochure;
import com.renaser.ai.ai_engine.organizacion.service.ContenidoDelBrochure;
import com.renaser.ai.ai_engine.organizacion.service.ContenidoDelBrochure.Contacto;
import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa;
import com.renaser.ai.ai_engine.organizacion.service.PdfDelBrochure;
import com.renaser.ai.ai_engine.organizacion.service.PlantillaDelBrochure;
import com.renaser.ai.ai_engine.organizacion.service.RedSocial;
import com.renaser.ai.ai_engine.organizacion.service.SedesDeLaEmpresa;
import com.renaser.ai.ai_engine.organizacion.service.ServicioBrochure;
import com.renaser.ai.ai_engine.organizacion.service.TamanoDeEmpresa;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Ver {@link ServicioBrochure}. */
@Slf4j
@Service
public class ServicioBrochureImpl implements ServicioBrochure {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** El nombre de la empresa en el nombre del archivo se recorta a tantos caracteres. */
    static final int MAXIMO_NOMBRE_EN_ARCHIVO = 80;
    /** Lo único que se lee del almacén como imagen: lo que deja el re-codificado de la V85. */
    private static final Set<String> TIPOS_DE_IMAGEN = Set.of("image/png", "image/jpeg");

    private final OrganizacionRepository organizaciones;
    private final OrganizacionBeneficioRepository beneficios;
    private final OrganizacionRedSocialRepository redes;
    private final SedeRepository sedes;
    private final CatalogosDelPerfil catalogos;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final PdfDelBrochure generador;
    private final String portal;

    /**
     * El último PDF de cada empresa con lo que llevaba. Uno por empresa: el siguiente con otro
     * contenido lo sustituye. Se pierde al reiniciar, y no pasa nada: se vuelve a armar.
     */
    private final Map<Long, Recordado> recordados = new ConcurrentHashMap<>();

    /** El día de hoy en Lima: la fecha del pie. Las pruebas lo cambian para ver que se rearma. */
    private Supplier<LocalDate> hoy = () -> LocalDate.now(LIMA);

    public ServicioBrochureImpl(OrganizacionRepository organizaciones,
                                OrganizacionBeneficioRepository beneficios,
                                OrganizacionRedSocialRepository redes, SedeRepository sedes,
                                CatalogosDelPerfil catalogos, ArchivoRepository archivos,
                                AlmacenArchivos almacen, PdfDelBrochure generador,
                                @Value("${renaser.portal.url:http://localhost:5174}") String portal) {
        this.organizaciones = organizaciones;
        this.beneficios = beneficios;
        this.redes = redes;
        this.sedes = sedes;
        this.catalogos = catalogos;
        this.archivos = archivos;
        this.almacen = almacen;
        this.generador = generador;
        this.portal = portal;
    }

    /** Lo que se recuerda de una empresa: el contenido y el PDF que salió de él. */
    private static final class Recordado {
        private final ContenidoDelBrochure contenido;
        private final byte[] pdf;

        Recordado(ContenidoDelBrochure contenido, byte[] pdf) {
            this.contenido = contenido;
            this.pdf = pdf;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ArchivoDelBrochure delPanel(ContextoUsuario quien) {
        Organizacion org = organizaciones.findById(quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Organización", "id",
                        quien.organizacionId()));
        if (!ServicioBrochure.tieneLoMinimo(org)) {
            throw new IllegalStateException(FALTA_QUIENES_SOMOS);
        }
        return armar(org);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArchivoDelBrochure> dePublica(Organizacion empresa) {
        if (!ServicioBrochure.tieneLoMinimo(empresa)) {
            return Optional.empty();
        }
        return Optional.of(armar(empresa));
    }

    void cambiarElDia(Supplier<LocalDate> otro) {
        this.hoy = otro;
    }

    /** El PDF recordado si lo que lleva no cambió; si no, uno nuevo, que pasa a recordarse. */
    private ArchivoDelBrochure armar(Organizacion org) {
        ContenidoDelBrochure contenido = contenidoDe(org);
        String nombre = nombreDelArchivo(org.getNombre());
        Recordado ya = recordados.get(org.getId());
        if (ya != null && ya.contenido.equals(contenido)) {
            return new ArchivoDelBrochure(nombre, ya.pdf);
        }
        LeidaONula logo = imagen(org.getId(), contenido.logoArchivoId());
        LeidaONula portada = imagen(org.getId(), contenido.portadaArchivoId());
        byte[] pdf;
        try {
            pdf = generador.generar(contenido, logo.bytes(), portada.bytes());
        } catch (RuntimeException fallo) {
            log.error("No se pudo armar el brochure de la empresa {}", org.getId(), fallo);
            throw fallo;
        }
        // Si una imagen no se pudo leer del almacén, este PDF sale sin ella y no se recuerda:
        // la siguiente descarga vuelve a intentarlo.
        if (!logo.fallo() && !portada.fallo()) {
            recordados.put(org.getId(), new Recordado(contenido, pdf));
        }
        return new ArchivoDelBrochure(nombre, pdf);
    }

    /** Todo lo que lleva el brochure, leído ahora: también es la huella del PDF recordado. */
    private ContenidoDelBrochure contenidoDe(Organizacion org) {
        List<String> suyos = beneficios.findByOrganizacionIdOrderByOrden(org.getId()).stream()
                .map(OrganizacionBeneficio::getTexto).toList();
        List<Sede> activas = sedes.findByOrganizacionIdAndEsActivaTrueOrderByNombre(org.getId());
        Map<String, OpcionUbigeo> provincias = SedesDeLaEmpresa.algunaConProvincia(activas)
                ? catalogos.ciudadesPorCodigo() : Map.of();
        return new ContenidoDelBrochure(org.getNombre(), org.getSector(),
                TamanoDeEmpresa.textoDe(org.getTamano()), org.getAnioFundacion(),
                org.getQuienesSomos(), org.getPropositoEmpresa(), org.getMision(), org.getVision(),
                suyos, SedesDeLaEmpresa.provincias(activas, provincias), contactoDe(org),
                org.getLogoArchivoId(), org.getPortadaArchivoId(),
                PlantillaDelBrochure.deLoGuardado(org.getPlantillaBrochure()),
                ColorDelBrochure.deLoGuardado(org.getColorBrochure()), hoy.get().format(FECHA));
    }

    /**
     * El perfil en EX, la web, las redes (en el orden de siempre) y el video. Solo los que son
     * http o https, aunque el guardado ya los haya comprobado.
     */
    private List<Contacto> contactoDe(Organizacion org) {
        List<Contacto> lista = new ArrayList<>();
        String perfil = sinBarraFinal(portal) + "/empresas/"
                + ImagenesDeLaEmpresa.codigoEnLaDireccion(org.getCodigo());
        anadir(lista, "Perfil en EX", perfil);
        anadir(lista, "Página web", org.getPaginaWeb());
        redes.findByOrganizacionId(org.getId()).stream()
                .filter(r -> RedSocial.deCodigo(r.getRed()).isPresent())
                .sorted(Comparator.comparingInt(r -> RedSocial.deCodigo(r.getRed())
                        .map(Enum::ordinal).orElse(Integer.MAX_VALUE)))
                .forEach((OrganizacionRedSocial r) -> anadir(lista,
                        RedSocial.deCodigo(r.getRed()).map(RedSocial::nombre).orElse(r.getRed()),
                        r.getEnlace()));
        anadir(lista, "Video de presentación", org.getVideoPresentacion());
        return lista;
    }

    private static void anadir(List<Contacto> lista, String rotulo, String url) {
        if (esWeb(url)) {
            lista.add(new Contacto(rotulo, url.strip(), escrito(url.strip())));
        }
    }

    /** Lo mismo que deja pulsar el motor de PDF: http o https, y nada más. */
    static boolean esWeb(String url) {
        if (url == null) {
            return false;
        }
        String limpio = url.strip();
        return limpio.startsWith("https://") || limpio.startsWith("http://");
    }

    /** La dirección para leerla en papel: sin el esquema, sin «www.» y sin la barra final. */
    static String escrito(String url) {
        String sin = url;
        for (String esquema : new String[] {"https://", "http://"}) {
            if (sin.startsWith(esquema)) {
                sin = sin.substring(esquema.length());
                break;
            }
        }
        if (sin.toLowerCase(Locale.ROOT).startsWith("www.")) {
            sin = sin.substring(4);
        }
        return sinBarraFinal(sin);
    }

    private static String sinBarraFinal(String texto) {
        String salida = texto == null ? "" : texto.strip();
        while (salida.endsWith("/")) {
            salida = salida.substring(0, salida.length() - 1);
        }
        return salida;
    }

    /**
     * «Brochure - {empresa}.pdf», sin los signos que un disco rechaza y con el nombre recortado a
     * {@value #MAXIMO_NOMBRE_EN_ARCHIVO} caracteres.
     */
    static String nombreDelArchivo(String empresa) {
        StringBuilder limpio = new StringBuilder();
        for (char c : (empresa == null ? "" : empresa).toCharArray()) {
            boolean prohibido = "\\/:*?\"<>|".indexOf(c) >= 0 || Character.isISOControl(c);
            char poner = prohibido || Character.isWhitespace(c) ? ' ' : c;
            if (poner != ' ' || (!limpio.isEmpty() && limpio.charAt(limpio.length() - 1) != ' ')) {
                limpio.append(poner);
            }
        }
        String nombre = limpio.toString().strip();
        if (nombre.codePointCount(0, nombre.length()) > MAXIMO_NOMBRE_EN_ARCHIVO) {
            nombre = nombre.substring(0, nombre.offsetByCodePoints(0, MAXIMO_NOMBRE_EN_ARCHIVO))
                    .strip();
        }
        return "Brochure - " + (nombre.isEmpty() ? "empresa" : nombre) + ".pdf";
    }

    /** Los bytes de una imagen de la propia empresa, y si falló al leerla. */
    private record LeidaONula(byte[] bytes, boolean fallo) {

        @Override
        public boolean equals(Object otro) {
            return otro instanceof LeidaONula l && java.util.Arrays.equals(bytes, l.bytes)
                    && fallo == l.fallo;
        }

        @Override
        public int hashCode() {
            return 31 * java.util.Arrays.hashCode(bytes) + Boolean.hashCode(fallo);
        }

        @Override
        public String toString() {
            return "LeidaONula[bytes=" + (bytes == null ? 0 : bytes.length) + ", fallo=" + fallo + "]";
        }
    }

    /**
     * El logo o la portada, buscados con la organización: solo una imagen de esta empresa, viva y
     * de las que se re-codificaron al subir. Si el almacén falla, el brochure sale sin ella antes
     * que no salir.
     */
    private LeidaONula imagen(Long organizacionId, Long archivoId) {
        if (archivoId == null) {
            return new LeidaONula(null, false);
        }
        return archivos.findByIdAndOrganizacionId(archivoId, organizacionId)
                .filter(a -> a.getBorradoEn() == null && a.getRuta() != null)
                .filter(a -> TIPOS_DE_IMAGEN.contains(a.getTipo()))
                .map(a -> {
                    try {
                        return new LeidaONula(almacen.leer(a), false);
                    } catch (RuntimeException fallo) {
                        log.warn("El brochure de la empresa {} sale sin la imagen {}: {}",
                                organizacionId, archivoId, fallo.getMessage());
                        return new LeidaONula(null, true);
                    }
                })
                .orElse(new LeidaONula(null, false));
    }
}
