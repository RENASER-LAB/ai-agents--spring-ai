/**
 * Saca el nombre y el enlace de cada currículum de una carpeta de Drive.
 *
 * Sirve para dar el paso que falta: hoy la pantalla enseña el nombre del archivo y un
 * enlace a la carpeta, y quien revisa tiene que buscarlo dentro. Con esto cada candidato
 * puede tener el enlace directo a SU currículum.
 *
 * **Corre dentro de tu propio Drive, con tu sesión.** No hay que compartir la carpeta con
 * nadie ni copiar los archivos a ningún sitio: el control de quién ve un currículum sigue
 * siendo el de Drive.
 *
 * Cómo usarlo:
 *
 *   1. Abre https://script.google.com y crea un proyecto nuevo.
 *   2. Pega este archivo entero, reemplazando lo que haya.
 *   3. Cambia CARPETAS de abajo por los ids de tus carpetas. El id es lo que va después
 *      de /folders/ en la URL:
 *        https://drive.google.com/drive/folders/1rmctP8znXcaPcXCMpOsHnRtMCrblzSUw
 *                                               ^--------- esto es el id ---------^
 *   4. Ejecuta la función `listar`. La primera vez te pedirá permiso para leer tu Drive.
 *   5. Se crea una hoja de cálculo llamada «CV de las convocatorias». Compártela conmigo
 *      o pégame su contenido, y yo dejo los enlaces puestos en la pantalla.
 */

// La clave de cada carpeta tiene que coincidir con la de scripts/convocatorias.json.
const CARPETAS = {
  'talento': '1rmctP8znXcaPcXCMpOsHnRtMCrblzSUw',
  'asistente-admin': 'PON_AQUI_EL_ID',
  'administrador': 'PON_AQUI_EL_ID',
};

function listar() {
  const hoja = SpreadsheetApp.create('CV de las convocatorias').getActiveSheet();
  hoja.appendRow(['convocatoria', 'archivo', 'enlace']);

  let total = 0;
  for (const [convocatoria, id] of Object.entries(CARPETAS)) {
    if (!id || id === 'PON_AQUI_EL_ID') {
      Logger.log('Sin id, se salta: ' + convocatoria);
      continue;
    }

    let carpeta;
    try {
      carpeta = DriveApp.getFolderById(id);
    } catch (e) {
      // Un id mal copiado no debe tumbar las demás carpetas: se avisa y se sigue.
      Logger.log('No se pudo abrir la carpeta de ' + convocatoria + ': ' + e.message);
      continue;
    }

    const archivos = carpeta.getFiles();
    let cuantos = 0;
    while (archivos.hasNext()) {
      const archivo = archivos.next();
      hoja.appendRow([convocatoria, archivo.getName(), archivo.getUrl()]);
      cuantos++;
    }
    Logger.log(convocatoria + ': ' + cuantos + ' archivos');
    total += cuantos;
  }

  hoja.autoResizeColumns(1, 3);
  Logger.log('Listo: ' + total + ' archivos en total');
}
