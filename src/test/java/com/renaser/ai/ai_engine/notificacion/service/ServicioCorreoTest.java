package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.CorreoEnviado;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreo;
import com.renaser.ai.ai_engine.notificacion.repository.CorreoEnviadoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;
import com.renaser.ai.ai_engine.notificacion.service.impl.EnviadorCorreoLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El aviso al candidato: qué se guarda, qué sale, y qué se anota sobre si salió.
 *
 * <p>Lo que más importa aquí es la honestidad del registro. Antes {@code estado_entrega}
 * existía como columna y no la escribía nadie, así que una fila podía decir que el correo se
 * mandó cuando el servidor estaba caído. Ese es el error que no da síntoma: se descubre meses
 * después, cuando el candidato dice que nunca le llegó y el sistema le contesta que sí.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El aviso por correo")
class ServicioCorreoTest {

    private static final Long ORG = 1L;
    private static final Long USUARIO = 7L;
    private static final String DESTINO = "candidato@ejemplo.test";

    @Mock private PlantillaCorreoRepository plantillas;
    @Mock private CorreoEnviadoRepository enviados;

    private ServicioCorreo servicio;
    private TransporteDeMentira transporte;

    /** Un transporte que contesta lo que le digan, y recuerda lo que le pidieron mandar. */
    private static final class TransporteDeMentira implements EnviadorCorreo {
        private Resultado aDevolver = Resultado.ENVIADO;
        private String destino;
        private String asunto;
        private String cuerpo;
        private int veces;

        @Override
        public Resultado enviar(String correoDestino, String asunto, String cuerpo) {
            this.destino = correoDestino;
            this.asunto = asunto;
            this.cuerpo = cuerpo;
            this.veces++;
            return aDevolver;
        }
    }

    @BeforeEach
    void armar() {
        transporte = new TransporteDeMentira();
        servicio = new ServicioCorreo(plantillas, enviados, transporte);
    }

    private void hayPlantilla() {
        when(plantillas.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                ORG, "PRUEBA_POR_CONFIRMAR"))
                .thenReturn(Optional.of(PlantillaCorreo.builder()
                        .id(3L).organizacionId(ORG).codigo("PRUEBA_POR_CONFIRMAR").version(2)
                        .asunto("Hola {{nombre}}, te toca la prueba")
                        .cuerpo("{{nombre}}: entra en {{enlace}} para hacer tu prueba.")
                        .esActiva(true)
                        .build()));
        when(enviados.save(any(CorreoEnviado.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CorreoEnviado loGuardado() {
        ArgumentCaptor<CorreoEnviado> captor = ArgumentCaptor.forClass(CorreoEnviado.class);
        verify(enviados, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        List<CorreoEnviado> todos = captor.getAllValues();
        return todos.get(todos.size() - 1);
    }

    @Test
    @DisplayName("guarda el texto EXACTO que salió, no la plantilla")
    void guardaElTextoYaReemplazado() {
        hayPlantilla();

        servicio.enviar(ORG, USUARIO, DESTINO, "PRUEBA_POR_CONFIRMAR",
                Map.of("nombre", "Charles", "enlace", "https://portal.test/acceso?token=abc"));

        CorreoEnviado fila = loGuardado();
        assertThat(fila.getAsunto()).isEqualTo("Hola Charles, te toca la prueba");
        assertThat(fila.getCuerpo())
                .as("si un candidato reclama meses después, se lee lo que se le dijo, "
                        + "no lo que la plantilla diga hoy")
                .contains("Charles").contains("https://portal.test/acceso?token=abc")
                .doesNotContain("{{");
        assertThat(fila.getVersionPlantilla()).isEqualTo(2);
    }

    @Test
    @DisplayName("una variable que nadie rellenó no deja el hueco a la vista")
    void unaVariableSinValorNoSeVe() {
        hayPlantilla();

        servicio.enviar(ORG, USUARIO, DESTINO, "PRUEBA_POR_CONFIRMAR",
                java.util.Collections.singletonMap("nombre", null));

        assertThat(transporte.cuerpo).doesNotContain("{{nombre}}");
    }

    @Test
    @DisplayName("si el envío sale bien, la fila lo dice")
    void anotaEnviado() {
        hayPlantilla();
        transporte.aDevolver = EnviadorCorreo.Resultado.ENVIADO;

        servicio.enviar(ORG, USUARIO, DESTINO, "PRUEBA_POR_CONFIRMAR", Map.of());

        assertThat(loGuardado().getEstadoEntrega()).isEqualTo("ENVIADO");
        assertThat(transporte.veces).isEqualTo(1);
        assertThat(transporte.destino).isEqualTo(DESTINO);
    }

    @Test
    @DisplayName("si el envío falla, la fila NO dice que se envió")
    void anotaFallido() {
        hayPlantilla();
        transporte.aDevolver = EnviadorCorreo.Resultado.FALLIDO;

        servicio.enviar(ORG, USUARIO, DESTINO, "PRUEBA_POR_CONFIRMAR", Map.of());

        assertThat(loGuardado().getEstadoEntrega())
                .as("una fila que dice «enviado» cuando nadie lo recibió es peor que no tenerla")
                .isEqualTo("FALLIDO");
    }

    @Test
    @DisplayName("sin dirección de destino no se intenta mandar nada")
    void sinDestinoNoIntenta() {
        hayPlantilla();

        servicio.enviar(ORG, USUARIO, null, "PRUEBA_POR_CONFIRMAR", Map.of());

        assertThat(transporte.veces).isZero();
        assertThat(loGuardado().getEstadoEntrega()).isEqualTo("NO_ENVIADO");
    }

    @Test
    @DisplayName("que falte la plantilla no puede frenar una postulación")
    void sinPlantillaSigueAdelante() {
        when(plantillas.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                ORG, "NO_EXISTE")).thenReturn(Optional.empty());

        servicio.enviar(ORG, USUARIO, DESTINO, "NO_EXISTE", Map.of());

        verify(enviados, never()).save(any());
        assertThat(transporte.veces).isZero();
    }

    // ============ La plantilla de recambio (contraseña nueva) ============

    private static final Long PLATAFORMA = 1L;
    private static final Long EMPRESA = 5L;

    private PlantillaCorreo plantillaDe(Long organizacionId) {
        return PlantillaCorreo.builder().id(organizacionId * 10).organizacionId(organizacionId)
                .codigo("RECUPERAR_CLAVE_EQUIPO").version(1)
                .asunto("Contraseña nueva para {{nombre_empresa}}")
                .cuerpo("Abre {{enlace}} antes del {{vence}}.").esActiva(true).build();
    }

    @Test
    @DisplayName("con plantilla propia, la empresa usa la suya y no mira la de la plataforma")
    void recambioUsaLaPropiaSiLaHay() {
        when(plantillas.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                EMPRESA, "RECUPERAR_CLAVE_EQUIPO")).thenReturn(Optional.of(plantillaDe(EMPRESA)));

        assertThat(servicio.plantillaConRecambio(EMPRESA, PLATAFORMA, "RECUPERAR_CLAVE_EQUIPO"))
                .get().extracting(PlantillaCorreo::getOrganizacionId).isEqualTo(EMPRESA);
        verify(plantillas, never()).findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                PLATAFORMA, "RECUPERAR_CLAVE_EQUIPO");
    }

    @Test
    @DisplayName("a una empresa sin la plantilla le sale la de la plataforma, no el silencio")
    void recambioCaeALaPlataforma() {
        when(plantillas.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                EMPRESA, "RECUPERAR_CLAVE_EQUIPO")).thenReturn(Optional.empty());
        when(plantillas.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                PLATAFORMA, "RECUPERAR_CLAVE_EQUIPO")).thenReturn(Optional.of(plantillaDe(PLATAFORMA)));

        assertThat(servicio.plantillaConRecambio(EMPRESA, PLATAFORMA, "RECUPERAR_CLAVE_EQUIPO"))
                .get().extracting(PlantillaCorreo::getOrganizacionId).isEqualTo(PLATAFORMA);
    }

    @Test
    @DisplayName("si no la tiene nadie, vacío: quien llama decide, y no se promete nada")
    void recambioSinNingunaEsVacio() {
        when(plantillas.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                any(), anyString())).thenReturn(Optional.empty());

        assertThat(servicio.plantillaConRecambio(EMPRESA, PLATAFORMA, "RECUPERAR_CLAVE_EQUIPO"))
                .isEmpty();
        // La propia plataforma no se pregunta dos veces a sí misma
        assertThat(servicio.plantillaConRecambio(PLATAFORMA, PLATAFORMA, "RECUPERAR_CLAVE_EQUIPO"))
                .isEmpty();
    }

    @Test
    @DisplayName("enviar con una plantilla ya elegida deja la fila y devuelve cómo acabó")
    void enviarConDevuelveElResultado() {
        when(enviados.save(any(CorreoEnviado.class))).thenAnswer(i -> i.getArgument(0));
        transporte.aDevolver = EnviadorCorreo.Resultado.FALLIDO;

        EnviadorCorreo.Resultado resultado = servicio.enviarCon(plantillaDe(PLATAFORMA), USUARIO,
                DESTINO, Map.of("nombre_empresa", "Acme", "enlace", "https://x.test/r?token=t",
                        "vence", "22/09/2026 a las 20:15"));

        assertThat(resultado).isEqualTo(EnviadorCorreo.Resultado.FALLIDO);
        CorreoEnviado fila = loGuardado();
        assertThat(fila.getEstadoEntrega()).isEqualTo("FALLIDO");
        assertThat(fila.getAsunto()).isEqualTo("Contraseña nueva para Acme");
        assertThat(fila.getCuerpo()).isEqualTo("Abre https://x.test/r?token=t antes del 22/09/2026 a las 20:15.");
        assertThat(fila.getPlantillaCorreoCodigo()).isEqualTo("RECUPERAR_CLAVE_EQUIPO");
    }

    // ============ El transporte por defecto ============

    @Test
    @DisplayName("el transporte de log no manda nada, y lo dice")
    void elDeLogNoManda() {
        assertThat(new EnviadorCorreoLog().enviar(DESTINO, "asunto", "cuerpo"))
                .as("NO_ENVIADO y no ENVIADO: la diferencia es justo el punto de la columna")
                .isEqualTo(EnviadorCorreo.Resultado.NO_ENVIADO);
    }
}
