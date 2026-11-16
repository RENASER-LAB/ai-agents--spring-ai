package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.NotaCriterioPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.CierreDeLaPruebaPropia;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La prueba escrita en el editor, leída para el informe por etapa: sin nadie conectado y solo
 * cuando el intento es de verdad del editor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La prueba del editor para el informe")
class ServicioCalificacionPruebaPropiaImplTest {

    private static final Long POSTULACION = 10L;

    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private PostulacionRepository postulaciones;
    @Mock private IntentoPruebaRepository intentos;
    @Mock private NotaCriterioPruebaRepository notas;
    @Mock private EntregableRepository subidas;
    @Mock private ArchivoRepository archivos;
    @Mock private CalificacionDeLaPruebaPropia calculo;
    @Mock private CierreDeLaPruebaPropia cierre;
    @Mock private ColaCalificacionIa cola;
    @Mock private MaquinaEstados maquina;
    @Mock private NombresDeUsuarios nombres;
    @Mock private ServicioAuditoria auditoria;

    @InjectMocks private ServicioCalificacionPruebaPropiaImpl servicio;

    private final Postulacion postulacion = Postulacion.builder().id(POSTULACION).organizacionId(3L).build();

    @Test
    @DisplayName("sin intento no hay prueba del editor que leer")
    void sinIntento() {
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        assertThat(servicio.paraElInforme(postulacion)).isNull();
        verifyNoInteractions(calculo, alcance);
    }

    @Test
    @DisplayName("el intento de una plantilla no es del editor: lo lee el servicio de las plantillas")
    void intentoDeUnaPlantilla() {
        when(intentos.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                IntentoPrueba.builder().postulacionId(POSTULACION).versionPlantillaPruebaId(80L).build()));

        assertThat(servicio.paraElInforme(postulacion)).isNull();
        verifyNoInteractions(calculo, alcance);
    }
}
