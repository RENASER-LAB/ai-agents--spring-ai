-- La ciudad de quien postula, para poder filtrar el ranking por dónde vive.
--
-- POR QUE UN CATALOGO Y NO DOS COLUMNAS DE TEXTO
-- ----------------------------------------------
-- «Lima», «lima» y «Lima Cercado» son la misma ciudad y tres filtros distintos. El texto libre
-- que ya existe en perfil_candidato.ubicacion demuestra el problema: seis filas, seis veces el
-- mismo «Arequipa, Perú» escrito a mano. Un catálogo cerrado con código de ubigeo lo cierra.
--
-- El árbol es auto-referenciado a propósito. Hoy se siembran los 25 departamentos y las 196
-- provincias; el día que haga falta el distrito se insertan 1874 filas más con nivel 3 y
-- persona.ciudad_ubigeo NO cambia, porque ya es varchar(6). Dos columnas planas
-- (departamento, provincia) obligarían a migrar la tabla de personas para ganar un nivel.
--
-- LO QUE ESTA MIGRACION NO HACE
-- -----------------------------
-- No borra perfil_candidato.ubicacion ni su contenido. Lo lee para rellenar lo que pueda y lo
-- deja intacto: son datos que el candidato escribió sobre sí mismo y una migración no es sitio
-- para destruirlos. La columna deja de leerse desde la aplicación, nada más.

create table ubigeo (
    codigo  varchar(6)  primary key,
    nivel   smallint    not null check (nivel between 1 and 3),
    padre   varchar(6)  references ubigeo(codigo),
    nombre  text        not null,
    activo  boolean     not null default true
);

comment on table  ubigeo is 'Catalogo geografico del Peru (INEI). nivel 1 departamento, 2 provincia, 3 distrito.';
comment on column ubigeo.codigo is 'Codigo INEI: 2 cifras departamento, 4 provincia, 6 distrito. EXT para fuera del Peru.';

create index ubigeo_padre_idx on ubigeo (padre) where activo;

-- Los 25 departamentos. Orden alfabetico del INEI, que es el que fija los codigos.
insert into ubigeo (codigo, nivel, padre, nombre) values
('01', 1, null, 'Amazonas'),
('02', 1, null, 'Áncash'),
('03', 1, null, 'Apurímac'),
('04', 1, null, 'Arequipa'),
('05', 1, null, 'Ayacucho'),
('06', 1, null, 'Cajamarca'),
('07', 1, null, 'Callao'),
('08', 1, null, 'Cusco'),
('09', 1, null, 'Huancavelica'),
('10', 1, null, 'Huánuco'),
('11', 1, null, 'Ica'),
('12', 1, null, 'Junín'),
('13', 1, null, 'La Libertad'),
('14', 1, null, 'Lambayeque'),
('15', 1, null, 'Lima'),
('16', 1, null, 'Loreto'),
('17', 1, null, 'Madre de Dios'),
('18', 1, null, 'Moquegua'),
('19', 1, null, 'Pasco'),
('20', 1, null, 'Piura'),
('21', 1, null, 'Puno'),
('22', 1, null, 'San Martín'),
('23', 1, null, 'Tacna'),
('24', 1, null, 'Tumbes'),
('25', 1, null, 'Ucayali');

-- Fuera del Peru. Es una fila del catalogo y no un booleano aparte: con un booleano, «donde
-- vive» tendria dos fuentes de verdad y habria que consultarlas las dos para pintar una celda.
insert into ubigeo (codigo, nivel, padre, nombre) values ('EXT', 1, null, 'Fuera del Perú');

-- Las 196 provincias. El codigo es DDPP y PP corre de 01 hacia arriba sin huecos dentro de su
-- departamento: esa es la invariante que verifica UbigeoSemillaTest, y es lo que hace que esto
-- se pueda revisar contando 25 cifras en vez de leyendo 196 filas.
insert into ubigeo (codigo, nivel, padre, nombre) values
-- Amazonas · 7
('0101', 2, '01', 'Chachapoyas'), ('0102', 2, '01', 'Bagua'), ('0103', 2, '01', 'Bongará'),
('0104', 2, '01', 'Condorcanqui'), ('0105', 2, '01', 'Luya'),
('0106', 2, '01', 'Rodríguez de Mendoza'), ('0107', 2, '01', 'Utcubamba'),
-- Áncash · 20
('0201', 2, '02', 'Huaraz'), ('0202', 2, '02', 'Aija'), ('0203', 2, '02', 'Antonio Raymondi'),
('0204', 2, '02', 'Asunción'), ('0205', 2, '02', 'Bolognesi'), ('0206', 2, '02', 'Carhuaz'),
('0207', 2, '02', 'Carlos Fermín Fitzcarrald'), ('0208', 2, '02', 'Casma'),
('0209', 2, '02', 'Corongo'), ('0210', 2, '02', 'Huari'), ('0211', 2, '02', 'Huarmey'),
('0212', 2, '02', 'Huaylas'), ('0213', 2, '02', 'Mariscal Luzuriaga'), ('0214', 2, '02', 'Ocros'),
('0215', 2, '02', 'Pallasca'), ('0216', 2, '02', 'Pomabamba'), ('0217', 2, '02', 'Recuay'),
('0218', 2, '02', 'Santa'), ('0219', 2, '02', 'Sihuas'), ('0220', 2, '02', 'Yungay'),
-- Apurímac · 7
('0301', 2, '03', 'Abancay'), ('0302', 2, '03', 'Andahuaylas'), ('0303', 2, '03', 'Antabamba'),
('0304', 2, '03', 'Aymaraes'), ('0305', 2, '03', 'Cotabambas'), ('0306', 2, '03', 'Chincheros'),
('0307', 2, '03', 'Grau'),
-- Arequipa · 8
('0401', 2, '04', 'Arequipa'), ('0402', 2, '04', 'Camaná'), ('0403', 2, '04', 'Caravelí'),
('0404', 2, '04', 'Castilla'), ('0405', 2, '04', 'Caylloma'), ('0406', 2, '04', 'Condesuyos'),
('0407', 2, '04', 'Islay'), ('0408', 2, '04', 'La Unión'),
-- Ayacucho · 11
('0501', 2, '05', 'Huamanga'), ('0502', 2, '05', 'Cangallo'), ('0503', 2, '05', 'Huanca Sancos'),
('0504', 2, '05', 'Huanta'), ('0505', 2, '05', 'La Mar'), ('0506', 2, '05', 'Lucanas'),
('0507', 2, '05', 'Parinacochas'), ('0508', 2, '05', 'Páucar del Sara Sara'),
('0509', 2, '05', 'Sucre'), ('0510', 2, '05', 'Víctor Fajardo'), ('0511', 2, '05', 'Vilcas Huamán'),
-- Cajamarca · 13
('0601', 2, '06', 'Cajamarca'), ('0602', 2, '06', 'Cajabamba'), ('0603', 2, '06', 'Celendín'),
('0604', 2, '06', 'Chota'), ('0605', 2, '06', 'Contumazá'), ('0606', 2, '06', 'Cutervo'),
('0607', 2, '06', 'Hualgayoc'), ('0608', 2, '06', 'Jaén'), ('0609', 2, '06', 'San Ignacio'),
('0610', 2, '06', 'San Marcos'), ('0611', 2, '06', 'San Miguel'), ('0612', 2, '06', 'San Pablo'),
('0613', 2, '06', 'Santa Cruz'),
-- Callao · 1
('0701', 2, '07', 'Prov. Const. del Callao'),
-- Cusco · 13
('0801', 2, '08', 'Cusco'), ('0802', 2, '08', 'Acomayo'), ('0803', 2, '08', 'Anta'),
('0804', 2, '08', 'Calca'), ('0805', 2, '08', 'Canas'), ('0806', 2, '08', 'Canchis'),
('0807', 2, '08', 'Chumbivilcas'), ('0808', 2, '08', 'Espinar'), ('0809', 2, '08', 'La Convención'),
('0810', 2, '08', 'Paruro'), ('0811', 2, '08', 'Paucartambo'), ('0812', 2, '08', 'Quispicanchi'),
('0813', 2, '08', 'Urubamba'),
-- Huancavelica · 7
('0901', 2, '09', 'Huancavelica'), ('0902', 2, '09', 'Acobamba'), ('0903', 2, '09', 'Angaraes'),
('0904', 2, '09', 'Castrovirreyna'), ('0905', 2, '09', 'Churcampa'), ('0906', 2, '09', 'Huaytará'),
('0907', 2, '09', 'Tayacaja'),
-- Huánuco · 11
('1001', 2, '10', 'Huánuco'), ('1002', 2, '10', 'Ambo'), ('1003', 2, '10', 'Dos de Mayo'),
('1004', 2, '10', 'Huacaybamba'), ('1005', 2, '10', 'Huamalíes'), ('1006', 2, '10', 'Leoncio Prado'),
('1007', 2, '10', 'Marañón'), ('1008', 2, '10', 'Pachitea'), ('1009', 2, '10', 'Puerto Inca'),
('1010', 2, '10', 'Lauricocha'), ('1011', 2, '10', 'Yarowilca'),
-- Ica · 5
('1101', 2, '11', 'Ica'), ('1102', 2, '11', 'Chincha'), ('1103', 2, '11', 'Nasca'),
('1104', 2, '11', 'Palpa'), ('1105', 2, '11', 'Pisco'),
-- Junín · 9
('1201', 2, '12', 'Huancayo'), ('1202', 2, '12', 'Concepción'), ('1203', 2, '12', 'Chanchamayo'),
('1204', 2, '12', 'Jauja'), ('1205', 2, '12', 'Junín'), ('1206', 2, '12', 'Satipo'),
('1207', 2, '12', 'Tarma'), ('1208', 2, '12', 'Yauli'), ('1209', 2, '12', 'Chupaca'),
-- La Libertad · 12
('1301', 2, '13', 'Trujillo'), ('1302', 2, '13', 'Ascope'), ('1303', 2, '13', 'Bolívar'),
('1304', 2, '13', 'Chepén'), ('1305', 2, '13', 'Julcán'), ('1306', 2, '13', 'Otuzco'),
('1307', 2, '13', 'Pacasmayo'), ('1308', 2, '13', 'Pataz'), ('1309', 2, '13', 'Sánchez Carrión'),
('1310', 2, '13', 'Santiago de Chuco'), ('1311', 2, '13', 'Gran Chimú'), ('1312', 2, '13', 'Virú'),
-- Lambayeque · 3
('1401', 2, '14', 'Chiclayo'), ('1402', 2, '14', 'Ferreñafe'), ('1403', 2, '14', 'Lambayeque'),
-- Lima · 10
('1501', 2, '15', 'Lima'), ('1502', 2, '15', 'Barranca'), ('1503', 2, '15', 'Cajatambo'),
('1504', 2, '15', 'Canta'), ('1505', 2, '15', 'Cañete'), ('1506', 2, '15', 'Huaral'),
('1507', 2, '15', 'Huarochirí'), ('1508', 2, '15', 'Huaura'), ('1509', 2, '15', 'Oyón'),
('1510', 2, '15', 'Yauyos'),
-- Loreto · 8
('1601', 2, '16', 'Maynas'), ('1602', 2, '16', 'Alto Amazonas'), ('1603', 2, '16', 'Loreto'),
('1604', 2, '16', 'Mariscal Ramón Castilla'), ('1605', 2, '16', 'Requena'),
('1606', 2, '16', 'Ucayali'), ('1607', 2, '16', 'Datem del Marañón'), ('1608', 2, '16', 'Putumayo'),
-- Madre de Dios · 3
('1701', 2, '17', 'Tambopata'), ('1702', 2, '17', 'Manu'), ('1703', 2, '17', 'Tahuamanu'),
-- Moquegua · 3
('1801', 2, '18', 'Mariscal Nieto'), ('1802', 2, '18', 'General Sánchez Cerro'), ('1803', 2, '18', 'Ilo'),
-- Pasco · 3
('1901', 2, '19', 'Pasco'), ('1902', 2, '19', 'Daniel Alcides Carrión'), ('1903', 2, '19', 'Oxapampa'),
-- Piura · 8
('2001', 2, '20', 'Piura'), ('2002', 2, '20', 'Ayabaca'), ('2003', 2, '20', 'Huancabamba'),
('2004', 2, '20', 'Morropón'), ('2005', 2, '20', 'Paita'), ('2006', 2, '20', 'Sullana'),
('2007', 2, '20', 'Talara'), ('2008', 2, '20', 'Sechura'),
-- Puno · 13
('2101', 2, '21', 'Puno'), ('2102', 2, '21', 'Azángaro'), ('2103', 2, '21', 'Carabaya'),
('2104', 2, '21', 'Chucuito'), ('2105', 2, '21', 'El Collao'), ('2106', 2, '21', 'Huancané'),
('2107', 2, '21', 'Lampa'), ('2108', 2, '21', 'Melgar'), ('2109', 2, '21', 'Moho'),
('2110', 2, '21', 'San Antonio de Putina'), ('2111', 2, '21', 'San Román'), ('2112', 2, '21', 'Sandia'),
('2113', 2, '21', 'Yunguyo'),
-- San Martín · 10
('2201', 2, '22', 'Moyobamba'), ('2202', 2, '22', 'Bellavista'), ('2203', 2, '22', 'El Dorado'),
('2204', 2, '22', 'Huallaga'), ('2205', 2, '22', 'Lamas'), ('2206', 2, '22', 'Mariscal Cáceres'),
('2207', 2, '22', 'Picota'), ('2208', 2, '22', 'Rioja'), ('2209', 2, '22', 'San Martín'),
('2210', 2, '22', 'Tocache'),
-- Tacna · 4
('2301', 2, '23', 'Tacna'), ('2302', 2, '23', 'Candarave'), ('2303', 2, '23', 'Jorge Basadre'),
('2304', 2, '23', 'Tarata'),
-- Tumbes · 3
('2401', 2, '24', 'Tumbes'), ('2402', 2, '24', 'Contralmirante Villar'), ('2403', 2, '24', 'Zarumilla'),
-- Ucayali · 4
('2501', 2, '25', 'Coronel Portillo'), ('2502', 2, '25', 'Atalaya'), ('2503', 2, '25', 'Padre Abad'),
('2504', 2, '25', 'Purús');

-- Donde vive quien postula. Va en persona y no en perfil_candidato porque perfil_candidato se
-- crea perezosamente —solo cuando el agente propone datos del CV— y la ciudad se pide en el
-- registro, cuando la unica fila que existe es esta.
alter table persona add column ciudad_ubigeo varchar(6) references ubigeo(codigo);
create index persona_ciudad_idx on persona (ciudad_ubigeo) where ciudad_ubigeo is not null;

-- Rescate de lo que ya habia escrito a mano. Casa el texto libre contra el nombre de la
-- provincia, ignorando mayusculas, tildes y espacios de sobra. Lo que no case se queda sin
-- ciudad, que es lo honesto: inventarle una provincia a alguien es peor que no tenerla.
--
-- Va en DOS pasadas porque la gente escribe su direccion de dos maneras y las dos son
-- normales: «Arequipa, Peru» empieza por la provincia, y «Miraflores, Lima, Peru» empieza
-- por el distrito. Probado contra los dos formatos y contra mayusculas, tildes y espacios;
-- de siete textos de muestra rescata cinco, y los dos que no —un distrito suelto y un texto
-- sin sentido— se quedan en blanco a proposito.
--
-- El orden importa: primero el trozo mas probable. Si se hicieran a la vez, «Lima, Lima»
-- daria lo mismo pero «Camana, Arequipa» podria caer en Arequipa en vez de en Camana.
UPDATE persona p
SET ciudad_ubigeo = u.codigo
FROM perfil_candidato pc
JOIN ubigeo u
  ON u.nivel = 2
 AND translate(lower(u.nombre), 'áéíóúüñ', 'aeiouun')
   = translate(lower(btrim(split_part(pc.ubicacion, ',', 1))), 'áéíóúüñ', 'aeiouun')
WHERE pc.persona_id = p.id
  AND pc.ubicacion IS NOT NULL
  AND p.ciudad_ubigeo IS NULL;

-- Segunda pasada: el segundo trozo, para quien puso el distrito delante.
UPDATE persona p
SET ciudad_ubigeo = u.codigo
FROM perfil_candidato pc
JOIN ubigeo u
  ON u.nivel = 2
 AND translate(lower(u.nombre), 'áéíóúüñ', 'aeiouun')
   = translate(lower(btrim(split_part(pc.ubicacion, ',', 2))), 'áéíóúüñ', 'aeiouun')
WHERE pc.persona_id = p.id
  AND pc.ubicacion IS NOT NULL
  AND p.ciudad_ubigeo IS NULL;
