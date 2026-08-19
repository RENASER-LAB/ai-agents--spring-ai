package com.renaser.ai.ai_engine.archivo.service;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;

import org.springframework.web.multipart.MultipartFile;

// Almacén propio y privado: los archivos no viven en la base ni en un servicio de
// terceros. La interfaz existe para poder cambiar disco local por otro almacén sin
// tocar a quien la usa.
public interface AlmacenArchivos {

    // Guarda el archivo y devuelve la fila de `archivo` ya persistida
    Archivo guardar(Long organizacionId, MultipartFile archivo);

    // El contenido, para la descarga con permiso
    byte[] leer(Archivo archivo);

    // Borra el contenido físico y anula la ruta; la fila se conserva (anonimización)
    void borrarContenido(Archivo archivo);
}
