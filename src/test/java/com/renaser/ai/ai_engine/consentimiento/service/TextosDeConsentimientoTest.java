package com.renaser.ai.ai_engine.consentimiento.service;

import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.entity.TipoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El texto que se firma, compuesto con el nombre de la empresa")
class TextosDeConsentimientoTest {

    private static final long PLATAFORMA = 1L;
    private static final long EMPRESA = 7L;

    @Mock private TextoConsentimientoRepository repositorio;
    @Mock private OrganizacionRepository organizaciones;

    private TextosDeConsentimiento textos() {
        return new TextosDeConsentimiento(repositorio, new DuenoDelInstrumento(organizaciones));
    }

    private void hayPlataforma() {
        lenient().when(organizaciones.findByEsPlataformaTrue()).thenReturn(Optional.of(
                Organizacion.builder().id(PLATAFORMA).esPlataforma(true).build()));
    }

    private void publicado(long organizacionId, TipoConsentimiento tipo, Long id, String texto) {
        lenient().when(repositorio
                .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                        organizacionId, tipo.codigo()))
                .thenReturn(id == null
                        ? Optional.empty()
                        : Optional.of(TextoConsentimiento.builder()
                                .id(id).organizacionId(organizacionId).tipo(tipo.codigo())
                                .version("1.1").texto(texto).build()));
    }

    @Test
    @DisplayName("el tipo viaja a la base con el nombre de la constante, no con otro")
    void elTipoViajaConSuNombre() {
        // Si alguien renombra una constante sin migración, el CHECK de la V54 rechaza la
        // fila y el fallo sale al crear la cuenta, no aquí. Este es el candado barato.
        assertThat(TipoConsentimiento.PLATAFORMA.codigo()).isEqualTo("PLATAFORMA");
        assertThat(TipoConsentimiento.PROCESO.codigo()).isEqualTo("PROCESO");
        assertThat(TipoConsentimiento.FUTUROS_CONTACTOS.codigo()).isEqualTo("FUTUROS_CONTACTOS");
        assertThat(TipoConsentimiento.existe("PLATAFORMA")).isTrue();
        assertThat(TipoConsentimiento.existe("proceso")).isFalse();
        assertThat(TipoConsentimiento.existe("LO_QUE_SEA")).isFalse();
    }

    @Test
    @DisplayName("la empresa no publica nada y aun así sus candidatos leen su nombre")
    void sinTextoPropioSeUsaElGeneralConSuNombre() {
        // El caso de siempre, y el único: la empresa no publicó nada —ni puede— y aun así
        // sus candidatos leen un texto que la nombra a ella.
        hayPlataforma();
        publicado(PLATAFORMA, TipoConsentimiento.PROCESO, 500L,
                "Acepto que {EMPRESA} trate mis datos. {EMPRESA} decide.");

        var firmado = textos().procesoDe("Acme S.A.C.");

        assertThat(firmado.texto())
                .isEqualTo("Acepto que Acme S.A.C. trate mis datos. Acme S.A.C. decide.")
                .doesNotContain("{EMPRESA}");
        // Y apunta a la fila de la plataforma, que es de donde salió: el consentimiento
        // guarda las dos cosas, la fuente y lo que se leyó.
        assertThat(firmado.fuente().getId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("aunque la empresa tenga uno suyo publicado, manda el general")
    void elGeneralMandaSiempre() {
        // No es un caso hipotético: las empresas dadas de alta antes de la V54 tienen una
        // copia publicada del texto de Renaser. Si alguna ganara, sus candidatos firmarían
        // un texto que no nombra a nadie y que ningún abogado mantiene. Publicarlo les está
        // prohibido desde la V54, y aquí se cierra la otra punta: ni se mira.
        hayPlataforma();
        publicado(EMPRESA, TipoConsentimiento.PROCESO, 90L, "El viejo de Acme, sin huecos.");
        publicado(PLATAFORMA, TipoConsentimiento.PROCESO, 500L, "El general con {EMPRESA}.");

        var firmado = textos().procesoDe("Acme S.A.C.");

        assertThat(firmado.fuente().getId()).isEqualTo(500L);
        assertThat(firmado.texto()).isEqualTo("El general con Acme S.A.C..");
    }

    @Test
    @DisplayName("sin el general, el corte dice que nadie puede postular")
    void sinNingunoElCorteExplica() {
        // Un vacío aquí dejaría al candidato firmando sin saber quién trata sus datos:
        // el corte es la conducta correcta.
        hayPlataforma();
        publicado(PLATAFORMA, TipoConsentimiento.PROCESO, null, null);

        assertThatThrownBy(() -> textos().procesoDe("Acme S.A.C."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nadie puede postular");
    }

    @Test
    @DisplayName("sin texto PLATAFORMA, el corte habla de la cuenta y no de una vacante")
    void sinTextoPlataformaElCorteHablaDeLaCuenta() {
        publicado(PLATAFORMA, TipoConsentimiento.PLATAFORMA, null, null);

        assertThatThrownBy(() -> textos().exigirVigente(PLATAFORMA, TipoConsentimiento.PLATAFORMA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("crear una cuenta");
    }

    @Test
    @DisplayName("componer un texto sin hueco lo deja igual")
    void componerSinHuecoNoTocaNada() {
        // Un texto propio no tiene por qué llevar hueco, y sustituir lo que no está es
        // devolver lo mismo. Sin esto habría que saber de antemano cuál lleva y cuál no.
        assertThat(textos().componer("Sin huecos.", "Acme S.A.C.")).isEqualTo("Sin huecos.");
    }

    @Test
    @DisplayName("el vigente es el de esa organización y ese tipo, no el de al lado")
    void elVigenteEsElDeEsaOrganizacionYEseTipo() {
        publicado(EMPRESA, TipoConsentimiento.PROCESO, 500L, "El de la empresa.");

        assertThat(textos().vigente(EMPRESA, TipoConsentimiento.PROCESO))
                .get().extracting(TextoConsentimiento::getOrganizacionId).isEqualTo(EMPRESA);
    }
}
