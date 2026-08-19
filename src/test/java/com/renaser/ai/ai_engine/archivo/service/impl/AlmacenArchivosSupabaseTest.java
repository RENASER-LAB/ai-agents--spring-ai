package com.renaser.ai.ai_engine.archivo.service.impl;

import com.renaser.ai.ai_engine.archivo.config.PropiedadesAlmacen;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El almacén de currículums cuando viven en un bucket y no en el disco del backend.
 *
 * <p>Se prueba contra un servidor falso, no contra Supabase: lo que hay que comprobar es que
 * se pide lo correcto —qué ruta, con qué clave, con qué caducidad— y que lo que vuelve se
 * interpreta bien. Llamar al Supabase de verdad probaría a Supabase, que ya funciona.
 *
 * <p>Lo que más importa de aquí es el <b>enlace firmado</b>: es lo que hace que un currículum
 * de diez megas no entre y salga del backend cada vez que alguien lo abre.
 */
@ExtendWith(MockitoExtension.class)
class AlmacenArchivosSupabaseTest {

    private static final String URL = "https://proyecto.supabase.co";
    private static final String CLAVE = "clave-de-servicio";
    private static final long ORGANIZACION = 1L;

    @Mock private ArchivoRepository archivos;

    private MockRestServiceServer supabase;
    private AlmacenArchivosSupabase almacen;

    @BeforeEach
    void montarElAlmacen() {
        PropiedadesAlmacen propiedades = new PropiedadesAlmacen();
        propiedades.setTipo("supabase");
        propiedades.getSupabase().setUrl(URL);
        propiedades.getSupabase().setClave(CLAVE);
        propiedades.getSupabase().setBucket("curriculums");
        propiedades.getSupabase().setMinutosEnlace(5);

        RestClient.Builder constructor = RestClient.builder();
        supabase = MockRestServiceServer.bindTo(constructor).build();

        lenient().when(archivos.save(any(Archivo.class))).thenAnswer(i -> i.getArgument(0));
        almacen = new AlmacenArchivosSupabase(archivos, propiedades, constructor);
    }

    // ============ Que falle pronto y no tarde ============

    @Test
    void sinConfiguracionNoArranca() {
        // Descubrir que falta la clave cuando un candidato ya pulsó «enviar» significa
        // perder su currículum y su tiempo. Mejor no arrancar.
        PropiedadesAlmacen aMedias = new PropiedadesAlmacen();
        aMedias.setTipo("supabase");
        aMedias.getSupabase().setBucket("curriculums");

        assertThatThrownBy(() ->
                new AlmacenArchivosSupabase(archivos, aMedias, RestClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.archivos.supabase.url");
    }

    // ============ Guardar ============

    @Test
    void elCurriculumVaAlBucketYLaFilaGuardaSuRutaDeAhi() {
        supabase.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        URL + "/storage/v1/object/curriculums/" + ORGANIZACION + "/")))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + CLAVE))
                .andRespond(withSuccess("{\"Key\":\"curriculums/x\"}", MediaType.APPLICATION_JSON));

        Archivo guardado = almacen.guardar(ORGANIZACION, unPdf());

        supabase.verify();
        // La ruta ya no es una carpeta de esta maquina: es el objeto dentro del bucket.
        assertThat(guardado.getRuta())
                .startsWith(ORGANIZACION + "/")
                .endsWith(".pdf")
                .doesNotContain("\\", ":");
        assertThat(guardado.getSubidoEn()).isNotNull();
    }

    @Test
    void loQueNoEsCurriculumNiSeIntentaSubir() {
        assertThatThrownBy(() -> almacen.guardar(ORGANIZACION,
                new MockMultipartFile("cv", "virus.exe", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF o Word");

        // Y no se llamó a nadie: la comprobación es antes de gastar una petición.
        supabase.verify();
        verify(archivos, never()).save(any(Archivo.class));
    }

    // ============ El enlace de descarga, que es lo que evita el doble viaje ============

    @Test
    void elEnlaceDeDescargaSaleFirmadoYConSuCaducidad() {
        supabase.expect(requestTo(URL + "/storage/v1/object/sign/curriculums/1/abc.pdf"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"signedURL\":\"/object/sign/curriculums/1/abc.pdf?token=t\"}",
                        MediaType.APPLICATION_JSON));

        Optional<AlmacenArchivos.EnlaceFirmado> enlace = almacen.urlDeDescarga(archivoEn("1/abc.pdf"));

        supabase.verify();
        assertThat(enlace).isPresent();
        assertThat(enlace.get().url())
                .isEqualTo(URL + "/storage/v1/object/sign/curriculums/1/abc.pdf?token=t");
        // Caduca pronto: el enlace no vuelve a preguntar quién eres, así que mientras viva
        // es tan bueno como el currículum.
        assertThat(enlace.get().expira()).isBefore(java.time.Instant.now().plusSeconds(310));
    }

    @Test
    void deUnArchivoYaBorradoNoSeFirmaNada() {
        // La fila se conserva tras la anonimización, pero sin ruta. Firmar aquí daría un
        // enlace a ninguna parte, y el candidato pidió que sus datos se fueran.
        assertThatThrownBy(() -> almacen.urlDeDescarga(archivoEn(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("borrado");
    }

    // ============ El enlace de subida ============

    @Test
    void elEnlaceDeSubidaDejaLaFilaHechaPeroTodaviaVacia() {
        supabase.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        URL + "/storage/v1/object/upload/sign/curriculums/1/")))
                .andRespond(withSuccess(
                        "{\"url\":\"/object/upload/sign/curriculums/1/abc.pdf?token=t\"}",
                        MediaType.APPLICATION_JSON));

        var subida = almacen.urlDeSubida(ORGANIZACION, "cv.pdf", "application/pdf");

        supabase.verify();
        assertThat(subida).isPresent();
        assertThat(subida.get().url()).startsWith(URL + "/storage/v1/object/upload/sign/");
        // El hueco existe; el contenido no. Quien mire esta fila tiene que poder
        // distinguirlo, o acabará habiendo currículums que constan y no están.
        assertThat(subida.get().archivo().getRuta()).isNotNull();
        assertThat(subida.get().archivo().getSubidoEn()).isNull();
    }

    @Test
    void unaSubidaQueNuncaLlegoNoSeDaPorBuena() {
        // Entre pedir el enlace y usarlo puede no haber pasado nada. Fiarse de quien avisa
        // dejaría una fila prometiendo un currículum que nadie subió, y eso se descubre
        // semanas después, cuando alguien intenta abrirlo.
        supabase.expect(requestTo(URL + "/storage/v1/object/curriculums/1/abc.pdf"))
                .andExpect(method(org.springframework.http.HttpMethod.HEAD))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> almacen.confirmarSubida(archivoEn("1/abc.pdf")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no se da por buena");

        supabase.verify();
    }

    // ============ Leer y borrar, que es lo que usa el resto del sistema ============

    @Test
    void leerTraeLosBytesDelBucket() {
        // Lo usa el extractor de texto: es el unico que necesita mirar DENTRO del archivo,
        // para darle el contenido al modelo. Entregarselo a una persona no pasa por aqui.
        supabase.expect(requestTo(URL + "/storage/v1/object/curriculums/1/abc.pdf"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + CLAVE))
                .andRespond(withSuccess("%PDF-1.4 contenido".getBytes(),
                        MediaType.APPLICATION_OCTET_STREAM));

        assertThat(almacen.leer(archivoEn("1/abc.pdf"))).asString().startsWith("%PDF");
        supabase.verify();
    }

    @Test
    void borrarQuitaElObjetoPeroConservaLaFila() {
        // La anonimizacion borra el contenido y deja la fila: se sabe que existio sin poder
        // recuperarlo. Si la fila se fuera tambien, desapareceria el rastro de que hubo algo.
        supabase.expect(requestTo(URL + "/storage/v1/object/curriculums/1/abc.pdf"))
                .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                .andRespond(withSuccess());

        Archivo archivo = archivoEn("1/abc.pdf");
        almacen.borrarContenido(archivo);

        supabase.verify();
        assertThat(archivo.getRuta()).isNull();
        assertThat(archivo.getBorradoEn()).isNotNull();
        verify(archivos).save(archivo);
    }

    @Test
    void siElBucketNoDejaBorrarLaFilaSeMarcaIgual() {
        // La anonimizacion de la base no puede quedarse a medias porque el bucket tuviera un
        // mal dia. Se anota el fallo y se sigue: lo que el candidato pidio es que sus datos
        // dejen de estar, y la fila es la que los tiene.
        supabase.expect(requestTo(URL + "/storage/v1/object/curriculums/1/abc.pdf"))
                .andRespond(withServerError());

        Archivo archivo = archivoEn("1/abc.pdf");
        almacen.borrarContenido(archivo);

        assertThat(archivo.getRuta()).isNull();
        assertThat(archivo.getBorradoEn()).isNotNull();
    }

    @Test
    void unaSubidaQueSiLlegoAnotaSuTamanoDeVerdad() {
        // El tamano se le pregunta al bucket, no a quien subio. Fiarse de lo que diga el
        // cliente es como no comprobarlo.
        supabase.expect(requestTo(URL + "/storage/v1/object/curriculums/1/abc.pdf"))
                .andExpect(method(org.springframework.http.HttpMethod.HEAD))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .headers(cabecerasCon(4096)));

        Archivo confirmado = almacen.confirmarSubida(archivoEn("1/abc.pdf"));

        supabase.verify();
        assertThat(confirmado.getTamano()).isEqualTo(4096);
        assertThat(confirmado.getSubidoEn()).isNotNull();
    }

    @Test
    void deUnArchivoBorradoNoSeLeeNada() {
        assertThatThrownBy(() -> almacen.leer(archivoEn(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("borrado");
    }

    // ============ Apoyo ============

    private MockMultipartFile unPdf() {
        return new MockMultipartFile("cv", "curriculum.pdf", "application/pdf",
                "%PDF-1.4 lo que sea".getBytes());
    }

    /** Las cabeceras que devolveria el bucket para un objeto de ese tamano. */
    private HttpHeaders cabecerasCon(long tamano) {
        HttpHeaders h = new HttpHeaders();
        h.setContentLength(tamano);
        return h;
    }

    private Archivo archivoEn(String ruta) {
        return Archivo.builder().id(9L).organizacionId(ORGANIZACION).ruta(ruta)
                .nombreOriginal("curriculum.pdf").tipo("application/pdf").build();
    }
}
