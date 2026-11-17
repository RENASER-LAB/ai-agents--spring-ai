package com.renaser.ai.ai_engine.postulacion.service.impl;

import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El texto del CV que ve la IA, con la regla del anonimizador de hoy (spec
 * la-ia-sabe-que-dia-es-hoy): lo recortado con la regla vieja tapaba los años de experiencia, y una
 * recalificación tiene que verlos sin volver a leer el archivo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El texto del CV para la IA")
class ServicioTextoCvImplTest {

    private static final Long POSTULACION = 30L;
    private static final String EXTRAIDO = "Juan Pérez, 34 años\nTengo más de 5 años de experiencia en Recursos Humanos";
    private static final String CON_LA_REGLA_VIEJA =
            "Juan Pérez, [DATO NO UTILIZABLE]\nTengo más de [DATO NO UTILIZABLE] de experiencia en Recursos Humanos";

    @Mock private CvRepository cvs;
    @Mock private ArchivoRepository archivos;
    @Mock private AlmacenArchivos almacen;
    @Mock private ExtractorTextoCv extractor;

    private ServicioTextoCvImpl servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioTextoCvImpl(cvs, archivos, almacen, extractor, new AnonimizadorCv());
    }

    @Test
    @DisplayName("lo recortado con la regla vieja se rehace desde el texto extraído, sin leer el archivo, y se guarda")
    void seRehaceConLaReglaDeHoy() {
        Instant antes = Instant.now().minusSeconds(3600);
        Cv cv = Cv.builder().id(1L).postulacionId(POSTULACION).textoExtraido(EXTRAIDO)
                .textoAnonimizado(CON_LA_REGLA_VIEJA).anonimizadoEn(antes).build();
        when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(cv));

        String texto = servicio.prepararParaIa(POSTULACION);

        assertThat(texto).isEqualTo("Juan Pérez, [DATO NO UTILIZABLE]\n"
                + "Tengo más de 5 años de experiencia en Recursos Humanos");
        assertThat(cv.getTextoAnonimizado()).isEqualTo(texto);
        assertThat(cv.getAnonimizadoEn()).isAfter(antes);
        verify(cvs).save(cv);
        verifyNoInteractions(archivos, almacen, extractor);
    }

    @Test
    @DisplayName("si ya está al día no se vuelve a guardar")
    void alDiaNoSeGuarda() {
        String alDia = new AnonimizadorCv().anonimizar(EXTRAIDO);
        Cv cv = Cv.builder().id(1L).postulacionId(POSTULACION).textoExtraido(EXTRAIDO).textoAnonimizado(alDia).build();
        when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(cv));

        assertThat(servicio.prepararParaIa(POSTULACION)).isEqualTo(alDia);
        verify(cvs, never()).save(any());
    }

    @Test
    @DisplayName("sin el texto extraído (se borraron sus datos) se usa lo que hay")
    void sinTextoExtraido() {
        Cv cv = Cv.builder().id(1L).postulacionId(POSTULACION).textoAnonimizado(CON_LA_REGLA_VIEJA).build();
        when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(cv));

        assertThat(servicio.prepararParaIa(POSTULACION)).isEqualTo(CON_LA_REGLA_VIEJA);
        verify(cvs, never()).save(any());
    }
}
