package com.renaser.ai.ai_engine.comun.programado;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Vive en nuestro paquete y no en la clase principal compartida: @EnableScheduling es
// global a todo el contexto, así que un solo sitio basta, y aquí no arriesgamos tocar
// la clase principal, que es compartida con el motor de agentes.
@Configuration
@EnableScheduling
public class ConfiguracionProgramacion {
}
