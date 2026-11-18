package com.renaser.ai.ai_engine.organizacion.service.impl;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;
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
import com.renaser.ai.ai_engine.organizacion.service.PdfDelBrochure;
import com.renaser.ai.ai_engine.organizacion.service.PlantillaDelBrochure;
import com.renaser.ai.ai_engine.organizacion.service.ServicioBrochure;
import com.renaser.ai.ai_engine.organizacion.service.ServicioBrochure.ArchivoDelBrochure;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El servicio del brochure sin base de datos: qué lleva, quién lo puede pedir, cómo se llama el
 * archivo y cuándo se vuelve a armar en vez de servir el recordado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El brochure: lo que lleva y cuándo se vuelve a armar")
class ServicioBrochureImplTest {

    private static final long ACME = 7L;
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};

    @Mock OrganizacionRepository organizaciones;
    @Mock OrganizacionBeneficioRepository beneficios;
    @Mock OrganizacionRedSocialRepository redes;
    @Mock SedeRepository sedes;
    @Mock CatalogosDelPerfil catalogos;
    @Mock ArchivoRepository archivos;
    @Mock AlmacenArchivos almacen;
    @Mock PdfDelBrochure generador;

    private ServicioBrochureImpl servicio;
    private Organizacion acme;
    private final ContextoUsuario talento = new ContextoUsuario(1L, 1L, ACME, "EQUIPO", List.of(),
            Map.of("editar_presentacion_empresa", "TODO"));

    @BeforeEach
    void preparar() {
        servicio = new ServicioBrochureImpl(organizaciones, beneficios, redes, sedes, catalogos,
                archivos, almacen, generador, "https://ex.renaser.pe/");
        servicio.cambiarElDia(() -> LocalDate.of(2026, 11, 17));
        acme = Organizacion.builder().id(ACME).codigo("ACME").nombre("Acme S.A.C.").esActiva(true)
                .quienesSomos("Construimos casas que duran.").propositoEmpresa("Casas para todos.")
                .mision("Construir bien.").paginaWeb("https://acme.pe").plantillaBrochure("COLUMNAS")
                .colorBrochure("PETROLEO").videoPresentacion("https://www.youtube.com/watch?v=abc123DEF45")
                .build();
        lenient().when(organizaciones.findById(ACME)).thenReturn(Optional.of(acme));
        lenient().when(beneficios.findByOrganizacionIdOrderByOrden(ACME)).thenReturn(List.of(
                OrganizacionBeneficio.builder().organizacionId(ACME).texto("Seguro EPS").orden(1).build()));
        lenient().when(redes.findByOrganizacionId(ACME)).thenReturn(List.of(
                red("INSTAGRAM", "https://www.instagram.com/acme"),
                red("FACEBOOK", "https://facebook.com/acme/")));
        lenient().when(sedes.findByOrganizacionIdAndEsActivaTrueOrderByNombre(ACME)).thenReturn(List.of(
                sede("Obra norte", "0101", "Av. Siempre Viva 742")));
        lenient().when(catalogos.ciudadesPorCodigo()).thenReturn(Map.of(
                "0101", new OpcionUbigeo("0101", "Chachapoyas", "Amazonas"),
                "1501", new OpcionUbigeo("1501", "Lima", "Lima")));
        lenient().when(generador.generar(any(), any(), any())).thenReturn(PDF);
    }

    @Test
    @DisplayName("AC-03 · lleva lo guardado: plantilla, color, provincias sin dirección y el contacto en orden")
    void llevaLoGuardado() {
        ArchivoDelBrochure archivo = servicio.delPanel(talento);

        assertThat(archivo.nombre()).isEqualTo("Brochure - Acme S.A.C..pdf");
        assertThat(archivo.contenido()).isEqualTo(PDF);
        ContenidoDelBrochure c = elContenido(1);
        assertThat(c.plantilla()).isEqualTo(PlantillaDelBrochure.COLUMNAS);
        assertThat(c.color()).isEqualTo(ColorDelBrochure.PETROLEO);
        assertThat(c.quienesSomos()).isEqualTo("Construimos casas que duran.");
        assertThat(c.mision()).isEqualTo("Construir bien.");
        assertThat(c.vision()).isNull();
        assertThat(c.beneficios()).containsExactly("Seguro EPS");
        // AC-12: la provincia, nunca la dirección.
        assertThat(c.provincias()).containsExactly("Chachapoyas");
        assertThat(c.toString()).doesNotContain("Siempre Viva");
        assertThat(c.fecha()).isEqualTo("17/11/2026");
        assertThat(c.contacto()).extracting(Contacto::rotulo).containsExactly("Perfil en EX",
                "Página web", "Facebook", "Instagram", "Video de presentación");
        assertThat(c.contacto().get(0)).isEqualTo(new Contacto("Perfil en EX",
                "https://ex.renaser.pe/empresas/acme", "ex.renaser.pe/empresas/acme"));
        assertThat(c.contacto().get(2).escrito()).isEqualTo("facebook.com/acme");
        assertThat(c.contacto().get(3).escrito()).isEqualTo("instagram.com/acme");
    }

    @Test
    @DisplayName("dentro del PDF solo van los enlaces http o https, aunque se hayan guardado otros")
    void soloEnlacesWeb() {
        acme.setPaginaWeb("javascript:alert(1)");
        acme.setVideoPresentacion("ftp://acme.pe/video");

        servicio.delPanel(talento);

        assertThat(elContenido(1).contacto()).extracting(Contacto::url)
                .allMatch(u -> u.startsWith("https://"))
                .doesNotContain("javascript:alert(1)", "ftp://acme.pe/video");
    }

    @Test
    @DisplayName("AC-07 · sin «Quiénes somos»: 409 en el panel y vacío en el perfil, sin armar nada")
    void sinLoMinimo() {
        acme.setQuienesSomos(null);

        assertThatThrownBy(() -> servicio.delPanel(talento)).isInstanceOf(IllegalStateException.class)
                .hasMessage(ServicioBrochure.FALTA_QUIENES_SOMOS);
        assertThat(servicio.dePublica(acme)).isEmpty();
        assertThat(ServicioBrochure.tieneLoMinimo(acme)).isFalse();
        verify(generador, never()).generar(any(), any(), any());
    }

    @Test
    @DisplayName("se recuerda: pedirlo otra vez sin cambios no vuelve a armarlo")
    void seRecuerda() {
        servicio.delPanel(talento);
        servicio.delPanel(talento);
        servicio.dePublica(acme);

        verify(generador, times(1)).generar(any(), any(), any());
    }

    @Test
    @DisplayName("QA-BROCH-01/02 · el panel lo arma y la dirección pública sirve ese mismo PDF, ya "
            + "recordado y con el mismo nombre: es a donde el panel lleva la pestaña")
    void elPanelYElPerfilSirvenElMismo() {
        ArchivoDelBrochure delPanel = servicio.delPanel(talento);

        assertThat(servicio.dePublica(acme)).contains(delPanel);
        assertThat(delPanel.nombre()).isEqualTo("Brochure - Acme S.A.C..pdf");
        verify(generador, times(1)).generar(any(), any(), any());
    }

    @Test
    @DisplayName("AC-08 · se vuelve a armar si cambia lo guardado, una sede, la plantilla o el día del pie")
    void seVuelveAArmar() {
        servicio.delPanel(talento);

        acme.setQuienesSomos("Construimos casas que duran cien años.");
        servicio.delPanel(talento);
        assertThat(elContenido(2).quienesSomos()).isEqualTo("Construimos casas que duran cien años.");

        when(sedes.findByOrganizacionIdAndEsActivaTrueOrderByNombre(ACME)).thenReturn(List.of(
                sede("Obra norte", "0101", null), sede("Oficina", "1501", null)));
        servicio.delPanel(talento);
        assertThat(elContenido(3).provincias()).containsExactly("Chachapoyas", "Lima");

        acme.setPlantillaBrochure("PORTADA");
        servicio.delPanel(talento);
        assertThat(elContenido(4).plantilla()).isEqualTo(PlantillaDelBrochure.PORTADA);

        servicio.cambiarElDia(() -> LocalDate.of(2026, 11, 18));
        servicio.delPanel(talento);
        assertThat(elContenido(5).fecha()).isEqualTo("18/11/2026");
    }

    @Test
    @DisplayName("AC-14 · el logo se busca con la organización de la empresa, nunca suelto")
    void elLogoConSuOrganizacion() {
        acme.setLogoArchivoId(55L);
        Archivo logo = Archivo.builder().id(55L).organizacionId(ACME).ruta("org/7/logo.png")
                .tipo("image/png").build();
        when(archivos.findByIdAndOrganizacionId(55L, ACME)).thenReturn(Optional.of(logo));
        when(almacen.leer(logo)).thenReturn(new byte[] {1, 2, 3});

        servicio.delPanel(talento);

        verify(archivos).findByIdAndOrganizacionId(55L, ACME);
        verify(generador).generar(any(), eq(new byte[] {1, 2, 3}), isNull());
    }

    @Test
    @DisplayName("un logo de otra empresa, borrado o que no es imagen no se lee: sale sin él")
    void logoAjeno() {
        acme.setLogoArchivoId(66L);
        when(archivos.findByIdAndOrganizacionId(66L, ACME)).thenReturn(Optional.empty());

        servicio.delPanel(talento);

        verify(almacen, never()).leer(any());
        verify(generador).generar(any(), isNull(), isNull());
    }

    @Test
    @DisplayName("si el almacén falla al leer el logo, sale sin él y no se recuerda: la siguiente vuelve a intentarlo")
    void almacenQueFalla() {
        acme.setLogoArchivoId(55L);
        Archivo logo = Archivo.builder().id(55L).organizacionId(ACME).ruta("org/7/logo.png")
                .tipo("image/png").build();
        when(archivos.findByIdAndOrganizacionId(anyLong(), eq(ACME))).thenReturn(Optional.of(logo));
        when(almacen.leer(logo)).thenThrow(new IllegalStateException("sin red"));

        servicio.delPanel(talento);
        servicio.delPanel(talento);

        verify(generador, times(2)).generar(any(), isNull(), isNull());
    }

    @Test
    @DisplayName("si el armado falla, el error sale (y queda en el registro) y no se recuerda nada")
    void armadoQueFalla() {
        when(generador.generar(any(), any(), any())).thenThrow(new IllegalStateException("PDFBox"))
                .thenReturn(PDF);

        assertThatThrownBy(() -> servicio.delPanel(talento)).hasMessage("PDFBox");
        assertThat(servicio.delPanel(talento).contenido()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("el nombre del archivo: «Brochure - {empresa}.pdf», sin signos raros y con la empresa en 80 caracteres")
    void nombreDelArchivo() {
        assertThat(ServicioBrochureImpl.nombreDelArchivo("Acme / Lima: \"Sur\"  S.A.C."))
                .isEqualTo("Brochure - Acme Lima Sur S.A.C..pdf");
        String largo = "Corporación Industrial ".repeat(8);
        String nombre = ServicioBrochureImpl.nombreDelArchivo(largo);
        String empresa = nombre.substring("Brochure - ".length(), nombre.length() - ".pdf".length());
        assertThat(empresa.length()).isLessThanOrEqualTo(80);
        assertThat(largo).startsWith(empresa);
        assertThat(ServicioBrochureImpl.nombreDelArchivo("Panadería Ñuñoa"))
                .isEqualTo("Brochure - Panadería Ñuñoa.pdf");
    }

    @Test
    @DisplayName("la dirección escrita para papel: sin esquema, sin «www.» y sin barra final")
    void direccionEscrita() {
        assertThat(ServicioBrochureImpl.escrito("https://www.facebook.com/acme/")).isEqualTo("facebook.com/acme");
        assertThat(ServicioBrochureImpl.escrito("http://acme.pe")).isEqualTo("acme.pe");
        assertThat(ServicioBrochureImpl.esWeb("javascript:alert(1)")).isFalse();
        assertThat(ServicioBrochureImpl.esWeb(null)).isFalse();
    }

    // ================================================================== ayudas

    /** El contenido con el que se armó la vez {@code n} (desde 1). */
    private ContenidoDelBrochure elContenido(int n) {
        ArgumentCaptor<ContenidoDelBrochure> captor = ArgumentCaptor.forClass(ContenidoDelBrochure.class);
        verify(generador, times(n)).generar(captor.capture(), any(), any());
        return captor.getAllValues().get(n - 1);
    }

    private static OrganizacionRedSocial red(String red, String enlace) {
        return OrganizacionRedSocial.builder().organizacionId(ACME).red(red).enlace(enlace).build();
    }

    private static Sede sede(String nombre, String provincia, String direccion) {
        return Sede.builder().organizacionId(ACME).nombre(nombre).provinciaUbigeo(provincia)
                .direccion(direccion).esActiva(true).build();
    }
}
