package com.renaser.ai.ai_engine.perfil.service;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionCatalogo;
import com.renaser.ai.ai_engine.perfil.repository.NivelEducativoRepository;
import com.renaser.ai.ai_engine.perfil.repository.NivelIdiomaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Los dos catálogos del perfil, servidos como opciones. Concreto y sin interfaz, como
 * {@code ServicioParametros}: reenviar dos listas no necesita más ceremonia, y la regla de
 * capas (un controlador no toca repositorios) queda cumplida.
 */
@Service
@RequiredArgsConstructor
public class CatalogosDelPerfil {

    private final NivelEducativoRepository nivelesEducativos;
    private final NivelIdiomaRepository nivelesIdioma;

    public List<OpcionCatalogo> nivelesEducativos() {
        return nivelesEducativos.findAllByOrderByOrden().stream()
                .map(n -> new OpcionCatalogo(n.getCodigo(), n.getNombre())).toList();
    }

    public List<OpcionCatalogo> nivelesIdioma() {
        return nivelesIdioma.findAllByOrderByOrden().stream()
                .map(n -> new OpcionCatalogo(n.getCodigo(), n.getNombre())).toList();
    }
}
