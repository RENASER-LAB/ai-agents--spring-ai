package com.renaser.ai.ai_engine.portal.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.entity.OrganizacionRedSocial;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRedSocialRepository;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.EmpresaDeLaVacante;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.RedPublica;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.VideoPublico;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lo que el portal enseña de cada empresa junto a sus vacantes (V81). */
@ExtendWith(MockitoExtension.class)
@DisplayName("La presentación de las empresas en el portal")
class PresentacionEnElPortalTest {

    @Mock private OrganizacionRedSocialRepository redes;

    private static Organizacion empresa(long id, String codigo) {
        return Organizacion.builder().id(id).codigo(codigo).nombre(codigo).esActiva(true).build();
    }

    @Test
    @DisplayName("el lote entero con UNA consulta de redes, nunca una por empresa ni por vacante")
    void unaSolaConsulta() {
        Organizacion conDatos = empresa(1L, "MOLINOS");
        conDatos.setPropositoEmpresa("Que ninguna familia se quede sin pan.");
        conDatos.setVideoPresentacion("https://www.youtube.com/watch?v=abc123DEF45");
        Organizacion soloRedes = empresa(2L, "ANDES");
        Organizacion sinNada = empresa(3L, "VACIA");
        when(redes.findByOrganizacionIdIn(anyCollection())).thenReturn(List.of(
                OrganizacionRedSocial.builder().organizacionId(2L).red("YOUTUBE")
                        .enlace("https://www.youtube.com/@andes").build(),
                OrganizacionRedSocial.builder().organizacionId(2L).red("FACEBOOK")
                        .enlace("https://www.facebook.com/andes").build()));

        Map<Long, EmpresaDeLaVacante> lote = new PresentacionEnElPortal(redes)
                .deLas(List.of(conDatos, soloRedes, sinNada));

        verify(redes, times(1)).findByOrganizacionIdIn(anyCollection());
        verify(redes, never()).findByOrganizacionId(anyLong());
        assertThat(lote.get(1L).proposito()).isEqualTo("Que ninguna familia se quede sin pan.");
        assertThat(lote.get(1L).video())
                .isEqualTo(new VideoPublico("YOUTUBE", "abc123DEF45", null,
                        "https://www.youtube.com/watch?v=abc123DEF45"));
        // En el orden de siempre: Facebook antes que YouTube, guarde la base como guarde.
        assertThat(lote.get(2L).redes()).extracting(RedPublica::red).containsExactly("FACEBOOK", "YOUTUBE");
        assertThat(lote.get(2L).proposito()).isNull();
        assertThat(lote).doesNotContainKey(3L);
    }

    @Test
    @DisplayName("sin vacantes no se pregunta nada")
    void loteVacio() {
        assertThat(new PresentacionEnElPortal(redes).deLas(List.of())).isEmpty();
        verify(redes, never()).findByOrganizacionIdIn(anyCollection());
    }

    @Test
    @DisplayName("solo el logo ya es un dato: la vacante lo pinta junto al nombre")
    void soloLogo() {
        Organizacion conLogo = empresa(4L, "LOGO");
        conLogo.setLogoArchivoId(9L);
        when(redes.findByOrganizacionId(4L)).thenReturn(List.of());

        EmpresaDeLaVacante suya = new PresentacionEnElPortal(redes).deUna(conLogo);

        assertThat(suya.logo()).isEqualTo("/api/v1/portal/empresas/logo/imagenes/logo?v=9");
        assertThat(suya.proposito()).isNull();
        assertThat(suya.redes()).isEmpty();
    }

    @Test
    @DisplayName("un video guardado que ya no se reconoce no se pinta")
    void videoQueNoSeReconoce() {
        assertThat(PresentacionEnElPortal.videoDe("https://www.facebook.com/x/videos/1")).isNull();
        assertThat(PresentacionEnElPortal.videoDe(null)).isNull();
        assertThat(PresentacionEnElPortal.videoDe("https://vimeo.com/123456789/abcdef1234"))
                .isEqualTo(new VideoPublico("VIMEO", "123456789", "abcdef1234",
                        "https://vimeo.com/123456789/abcdef1234"));
    }
}
