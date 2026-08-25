package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Cv;
import com.renaser.ai.ai_engine.postulacion.entity.DatoCv;
import com.renaser.ai.ai_engine.postulacion.repository.CvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La decisión que cuesta dinero: leer el currículum, o copiar una lectura ya pagada.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La lectura del currículum al postular")
class ServicioLecturaCvImplTest {

    private static final long PERSONA = 30L;
    private static final long POSTULACION = 10L;
    private static final long POSTULACION_VIEJA = 5L;
    private static final String HASH = "a".repeat(64);

    @Mock private CvRepository cvs;
    @Mock private ArchivoRepository archivos;
    @Mock private DatoCvRepository datosCv;
    @Mock private ColaCalificacionIa cola;

    private ServicioLecturaCvImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioLecturaCvImpl(cvs, archivos, datosCv, cola, true);
        lenient().when(cvs.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                Cv.builder().id(1L).postulacionId(POSTULACION).archivoOriginalId(100L).build()));
        lenient().when(archivos.findById(100L)).thenReturn(Optional.of(
                Archivo.builder().id(100L).contenidoHash(HASH).build()));
    }

    private DatoCv fichaVieja() {
        return DatoCv.builder().id(7L).postulacionId(POSTULACION_VIEJA)
                .nombre("Camila").habilidades("Excel | SQL").experienciaMesesTotal(48)
                .ejecucionIaId(99L).actualizadoEn(Instant.now()).build();
    }

    @Test
    @DisplayName("El mismo archivo de la misma persona no se vuelve a pagar: se copia la ficha")
    void mismoHashCopiaSinEncolar() {
        when(datosCv.fichasDeLaPersonaConHash(PERSONA, HASH))
                .thenReturn(List.of(fichaVieja()));
        when(datosCv.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(datosCv.save(any())).thenAnswer(i -> i.getArgument(0));

        servicio.trasPostular(PERSONA, POSTULACION);

        verify(cola, never()).encolarDatosCv(any());
        ArgumentCaptor<DatoCv> copiada = ArgumentCaptor.forClass(DatoCv.class);
        verify(datosCv).save(copiada.capture());
        assertThat(copiada.getValue().getPostulacionId()).isEqualTo(POSTULACION);
        assertThat(copiada.getValue().getNombre()).isEqualTo("Camila");
        // La trazabilidad viaja con la copia: se sabe de que llamada al modelo salio.
        assertThat(copiada.getValue().getEjecucionIaId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("Un archivo distinto sí se lee: es la única razón para pagar otra llamada")
    void hashDistintoEncola() {
        when(datosCv.fichasDeLaPersonaConHash(PERSONA, HASH)).thenReturn(List.of());

        servicio.trasPostular(PERSONA, POSTULACION);

        verify(cola).encolarDatosCv(POSTULACION);
        verify(datosCv, never()).save(any());
    }

    @Test
    @DisplayName("Sin huella (archivo de antes de la columna) se lee: no hay forma de saber")
    void sinHashEncola() {
        when(archivos.findById(100L)).thenReturn(Optional.of(
                Archivo.builder().id(100L).contenidoHash(null).build()));

        servicio.trasPostular(PERSONA, POSTULACION);

        verify(cola).encolarDatosCv(POSTULACION);
    }

    @Test
    @DisplayName("La ficha de la propia postulación no cuenta como lectura previa")
    void laPropiaNoCuenta() {
        DatoCv propia = fichaVieja();
        propia.setPostulacionId(POSTULACION);
        when(datosCv.fichasDeLaPersonaConHash(PERSONA, HASH)).thenReturn(List.of(propia));

        servicio.trasPostular(PERSONA, POSTULACION);

        verify(cola).encolarDatosCv(POSTULACION);
    }

    @Test
    @DisplayName("Si decidir falla, postular no se cae: el CV se queda sin leer y ya")
    void unFalloNoTumbaLaPostulacion() {
        when(cvs.findByPostulacionId(POSTULACION))
                .thenThrow(new RuntimeException("la base parpadeó"));

        servicio.trasPostular(PERSONA, POSTULACION);   // no lanza

        verify(cola, never()).encolarDatosCv(any());
    }
}
