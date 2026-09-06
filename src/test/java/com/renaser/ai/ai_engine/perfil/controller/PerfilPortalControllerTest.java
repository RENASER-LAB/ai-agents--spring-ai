package com.renaser.ai.ai_engine.perfil.controller;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.ElegirPortada;
import com.renaser.ai.ai_engine.perfil.service.ServicioArchivosDelPerfil;
import com.renaser.ai.ai_engine.perfil.service.ServicioArchivosDelPerfil.Contenido;
import com.renaser.ai.ai_engine.perfil.service.ServicioPerfilPortal;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las rutas de los archivos del perfil.
 *
 * ⚠️ **Lo que se prueba aquí es que los bytes se SIRVEN, con su nombre y su
 * tipo.** Es la decisión que sostiene la pantalla: un `<img src>` no manda
 * cabecera `Authorization`, así que un enlace firmado no vale para pintar una
 * foto, y en local el almacén es el de memoria, cuya url `memoria://` no la abre
 * ningún navegador. Si alguien cambia esto por una redirección, la foto deja de
 * verse en local y nadie se entera hasta desplegar.
 *
 * Sin MockMvc a propósito: el proyecto no tiene tests de controlador y esto es
 * un delegador con dos ayudantes de cabeceras, no un enrutador que probar.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Las rutas de mis archivos del perfil")
class PerfilPortalControllerTest {

    @Mock private ServicioPerfilPortal servicio;
    @Mock private ServicioArchivosDelPerfil archivos;
    @Mock private Permisos permisos;

    private PerfilPortalController controlador;

    private static final ContextoUsuario QUIEN =
            new ContextoUsuario(3L, 7L, 1L, "CANDIDATO", List.of(), Map.of());

    @BeforeEach
    void montar() {
        lenient().when(permisos.actual()).thenReturn(QUIEN);
        controlador = new PerfilPortalController(servicio, archivos, permisos);
    }

    private static MultipartFile unaImagen() {
        return new MockMultipartFile("archivo", "yo.png", "image/png", new byte[] {1, 2});
    }

    @Test
    @DisplayName("La foto se sirve «inline», con su tipo y su nombre")
    void laFotoSeSirveInline() {
        // `inline` y no `attachment`: se pinta en un `<img>`, no se descarga.
        when(archivos.foto(QUIEN))
                .thenReturn(new Contenido(new byte[] {1, 2, 3}, "yo.png", "image/png"));

        ResponseEntity<byte[]> r = controlador.foto();

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getHeaders().getContentType()).hasToString("image/png");
        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=\"yo.png\"");
        assertThat(r.getBody()).hasSize(3);
    }

    @Test
    @DisplayName("Sin tipo declarado se sirve como binario, no como texto")
    void sinTipoEsBinario() {
        // Un archivo antiguo puede tener el tipo a null; adivinarlo mal haría que
        // el navegador intente pintar un PDF como si fuera texto.
        when(archivos.portada(QUIEN))
                .thenReturn(new Contenido(new byte[] {1}, "fondo", null));

        assertThat(controlador.portada().getHeaders().getContentType())
                .hasToString("application/octet-stream");
    }

    @Test
    @DisplayName("El currículum se DESCARGA: va como adjunto")
    void elCurriculumSeDescarga() {
        // Es el único de los cuatro que la persona quiere en su disco.
        when(archivos.curriculum(QUIEN))
                .thenReturn(new Contenido(new byte[] {1}, "cv.pdf", "application/pdf"));

        ResponseEntity<byte[]> r = controlador.curriculum();

        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"cv.pdf\"");
    }

    @Test
    @DisplayName("Cada ruta actúa en nombre de quien llama, nunca de otro")
    void todasActuanEnNombreDeQuienLlama() {
        controlador.subirFoto(unaImagen());
        controlador.quitarFoto();
        controlador.subirPortada(unaImagen());
        controlador.elegirPortada(new ElegirPortada("CANTO_AQUA"));
        controlador.quitarPortada();

        verify(archivos).guardarFoto(org.mockito.ArgumentMatchers.eq(QUIEN), any());
        verify(archivos).quitarFoto(QUIEN);
        verify(archivos).guardarPortada(org.mockito.ArgumentMatchers.eq(QUIEN), any());
        verify(archivos).elegirPortadaDeGaleria(QUIEN, "CANTO_AQUA");
        verify(archivos).quitarPortada(QUIEN);
    }

    @Test
    @DisplayName("El diploma se sirve inline: es para mirarlo, no para guardarlo")
    void elDiplomaSeMira() {
        when(archivos.diploma(QUIEN, 5L))
                .thenReturn(new Contenido(new byte[] {1}, "diploma.pdf", "application/pdf"));

        assertThat(controlador.diploma(5L).getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline;");
        verify(archivos).diploma(QUIEN, 5L);
    }

    @Test
    @DisplayName("Subir y quitar el currículum y los diplomas pasan por el servicio")
    void elRestoDelega() {
        controlador.subirCurriculum(unaImagen());
        controlador.quitarCurriculum();
        controlador.subirDiploma(5L, unaImagen());
        controlador.quitarDiploma(5L);

        verify(archivos).guardarCurriculum(org.mockito.ArgumentMatchers.eq(QUIEN), any());
        verify(archivos).quitarCurriculum(QUIEN);
        verify(archivos).guardarDiploma(
                org.mockito.ArgumentMatchers.eq(QUIEN), org.mockito.ArgumentMatchers.eq(5L), any());
        verify(archivos).quitarDiploma(QUIEN, 5L);
    }
}
