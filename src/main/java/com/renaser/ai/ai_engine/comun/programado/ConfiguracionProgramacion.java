package com.renaser.ai.ai_engine.comun.programado;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Vive en nuestro paquete y no en la clase principal compartida: @EnableScheduling es
// global a todo el contexto, así que un solo sitio basta, y aquí no arriesgamos tocar
// un archivo que también mantiene Ricardo.
@Configuration
@EnableScheduling
public class ConfiguracionProgramacion {
}
