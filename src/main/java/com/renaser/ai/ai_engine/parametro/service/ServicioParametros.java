package com.renaser.ai.ai_engine.parametro.service;

import com.renaser.ai.ai_engine.parametro.repository.ParametroRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

// Lectura tipada de los parámetros que Renaser cambia sin programar. Si el parámetro
// no existe se usa el valor por defecto que pasa quien llama: el sistema no se cae
// porque alguien borró una fila de configuración.
@Service
@RequiredArgsConstructor
public class ServicioParametros {

    private final ParametroRepository parametros;

    public int entero(Long organizacionId, String codigo, int porDefecto) {
        return parametros.findByOrganizacionIdAndCodigo(organizacionId, codigo)
                .map(p -> {
                    try {
                        return Integer.parseInt(p.getValor().trim());
                    } catch (NumberFormatException e) {
                        return porDefecto;
                    }
                })
                .orElse(porDefecto);
    }

    /**
     * Un parámetro de tipo LISTA, separado por comas.
     *
     * <p>Es lo que permite que «qué roles pueden facilitar una simulación» sea una fila de
     * configuración y no una constante en el código: cambiarlo es editar el parámetro desde el
     * panel, sin desplegar nada.
     */
    public List<String> lista(Long organizacionId, String codigo, List<String> porDefecto) {
        return parametros.findByOrganizacionIdAndCodigo(organizacionId, codigo)
                .map(p -> Arrays.stream(p.getValor().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList())
                .filter(l -> !l.isEmpty())
                .orElse(porDefecto);
    }
}
