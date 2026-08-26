package com.renaser.ai.ai_engine.usuario.service;

import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Traducir ids a nombres sin enseñar los de quien pidió que le borraran los datos.
 *
 * <p>Esta regla estaba metida en la bandeja del panel mientras fue el único sitio que
 * enseñaba nombres. Ahora la usan dos —la bandeja y los inscritos de una sesión— y se prueba
 * donde vive: si volviera a haber dos copias, la que se olvide de actualizar es la que
 * filtra un nombre que ya no debería salir.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cómo se llama cada usuario")
class NombresDeUsuariosTest {

    @Mock private UsuarioRepository usuarios;
    @Mock private PersonaRepository personas;

    @InjectMocks private NombresDeUsuarios nombres;

    @Test
    @DisplayName("Sin usuario, sin persona o con la persona borrada sale «(anonimizado)»")
    void lasTresFormasDeNoTenerNombre() {
        // El borrado de datos NO borra al usuario: vacía a la persona y le pone fecha. Las
        // tres formas de quedarse sin nombre acaban en lo mismo porque desde fuera son el
        // mismo caso: alguien que existió y a quien ya no se puede nombrar.
        when(usuarios.findAllById(any())).thenReturn(List.of(
                Usuario.builder().id(901L).build(),                        // sin persona
                Usuario.builder().id(902L).personaId(502L).build(),        // la persona no está
                Usuario.builder().id(903L).personaId(503L).build(),        // vaciada
                Usuario.builder().id(904L).personaId(504L).build()));      // con nombre
        when(personas.findAllById(any())).thenReturn(List.of(
                Persona.builder().id(503L).anonimizadoEn(Instant.now()).build(),
                Persona.builder().id(504L).nombre("Lucía").apellidos("Ortega").build()));

        Map<Long, String> resueltos = nombres.porUsuario(List.of(900L, 901L, 902L, 903L, 904L));

        // El 900 ni siquiera salió de la base y también tiene entrada: quien llama hace get()
        // y pinta la fila, sin repetir la comprobación del nulo en cada sitio.
        assertThat(resueltos).containsOnlyKeys(900L, 901L, 902L, 903L, 904L);
        assertThat(resueltos.get(900L)).isEqualTo(NombresDeUsuarios.ANONIMO);
        assertThat(resueltos.get(901L)).isEqualTo(NombresDeUsuarios.ANONIMO);
        assertThat(resueltos.get(902L)).isEqualTo(NombresDeUsuarios.ANONIMO);
        assertThat(resueltos.get(903L)).isEqualTo(NombresDeUsuarios.ANONIMO);
        assertThat(resueltos.get(904L)).isEqualTo("Lucía Ortega");
    }

    @Test
    @DisplayName("Una persona a medio rellenar no deja un nombre vacío")
    void aMedioRellenarTampocoEsUnNombre() {
        when(usuarios.findAllById(any())).thenReturn(List.of(
                Usuario.builder().id(901L).personaId(501L).build(),
                Usuario.builder().id(902L).personaId(502L).build()));
        when(personas.findAllById(any())).thenReturn(List.of(
                Persona.builder().id(501L).nombre("Ana").build(),   // sin apellidos
                Persona.builder().id(502L).build()));               // sin nada

        Map<Long, String> resueltos = nombres.porUsuario(List.of(901L, 902L));

        // Con apellidos vacíos el nombre sigue sirviendo. Sin nada, una cadena vacía en el
        // panel se lee como un fallo de carga; «(anonimizado)» al menos dice qué pasó.
        assertThat(resueltos.get(901L)).isEqualTo("Ana");
        assertThat(resueltos.get(902L)).isEqualTo(NombresDeUsuarios.ANONIMO);
    }

    @Test
    @DisplayName("Una tanda entera cuesta dos consultas, y los ids repetidos van una sola vez")
    void laTandaCuestaDosConsultas() {
        when(usuarios.findAllById(any())).thenReturn(List.of(
                Usuario.builder().id(901L).personaId(501L).build()));
        when(personas.findAllById(any())).thenReturn(List.of(
                Persona.builder().id(501L).nombre("Ana").apellidos("Ruiz").build()));

        nombres.porUsuario(List.of(901L, 901L, 901L));

        // Contra Supabase cada viaje cuesta ~140 ms y van en serie: pedir tres veces el
        // mismo id es volver al problema que este colaborador existe para no tener.
        ArgumentCaptor<Iterable<Long>> pedidos = ArgumentCaptor.captor();
        verify(usuarios, times(1)).findAllById(pedidos.capture());
        assertThat(pedidos.getValue()).containsExactly(901L);
        verify(personas, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("Sin ids no se pregunta nada")
    void sinIdsNoSePreguntaNada() {
        assertThat(nombres.porUsuario(List.of())).isEmpty();

        // Un findAllById con la lista vacía no devuelve nada y en algunos motores ni
        // siquiera es SQL válido: el viaje sobra entero.
        verifyNoInteractions(usuarios, personas);
    }

    @Test
    @DisplayName("Un id que no existe, pedido suelto, tampoco revienta")
    void unoSueltoQueNoExiste() {
        when(usuarios.findAllById(any())).thenReturn(List.of());

        assertThat(nombres.de(404L)).isEqualTo(NombresDeUsuarios.ANONIMO);
    }
}
