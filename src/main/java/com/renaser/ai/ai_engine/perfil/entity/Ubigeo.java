package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

// El catalogo geografico del Peru (INEI), en arbol: nivel 1 departamento, 2 provincia,
// 3 distrito. En tabla y no en un enum de Java porque el dia que haga falta el distrito
// son 1874 filas mas y ni una linea de codigo.
//
// padre es el codigo del de arriba y no un @ManyToOne a proposito: nadie navega el arbol
// hacia arriba fila a fila —el catalogo entero cabe en una consulta— y una relacion
// perezosa aqui solo serviria para que pintar una lista disparara 222 selects.
@Entity
@Table(name = "ubigeo")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Ubigeo {

    // El codigo INEI: 2 cifras el departamento, 4 la provincia, 6 el distrito. EXT es la
    // fila de quien vive fuera del Peru, y es nivel 1 sin padre.
    @Id
    private String codigo;

    // Short y no Integer: la columna es smallint y ddl-auto: validate no perdona la
    // diferencia. Revienta al arrancar, no al leer.
    private Short nivel;

    private String padre;
    private String nombre;
    private boolean activo;
}
