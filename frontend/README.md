# Criba de currículums · pantalla

La cara visual del módulo de selección de RENASER: enseña una tanda de currículums ordenada
de más apto a menos, y el retrato que la IA hizo de cada candidato.

## Los dos modos

**En vivo.** Habla con el backend de Spring. Se pueden lanzar las dos pasadas de
calificación y la tabla se refresca sola mientras la IA trabaja.

```bash
npm install
npm run dev                              # el backend en el 8080
API_URL=http://localhost:8081 npm run dev   # si lo levantaste en otro puerto
```

**Demo.** No hay backend. Lee una foto de los resultados congelada en
`src/datos-demo.json` y queda un sitio estático que se puede publicar en cualquier sitio.
Es de solo mirar: los botones que piden calificar no aparecen.

```bash
npm run dev:demo     # para verlo en local
npm run build:demo   # deja el sitio en dist/
```

## La foto de datos

`src/datos-demo.json` **no está en el repositorio**, y es a propósito: lleva nombres,
cargos, empresas y los juicios de la IA sobre candidatos reales. Subirla a un repositorio
público sería publicar esos datos.

Recién clonado, el proyecto no compila hasta que ese archivo exista. Para que compile con
datos vacíos:

```bash
cp src/datos-demo.ejemplo.json src/datos-demo.json
```

Y para llenarla de verdad, desde el backend, con la base levantada:

```bash
python scripts/exportar-para-demo.py --api http://localhost:8081/api/v1 --uid TU_UID
```

Por defecto tapa el correo y el teléfono de cada candidato. Con `--con-contacto` salen.

## Publicar

Como el sitio es estático, sirve cualquier alojamiento. Con Vercel, **subiendo la carpeta
ya compilada** desde tu máquina, que es la forma de no dejar los datos en el repositorio:

```bash
npm run build:demo
npx vercel deploy dist --prod
```

> **Antes de publicar.** Un despliegue de Vercel es público salvo que le pongas contraseña.
> Ahí dentro van nombres de personas reales y lo que la IA opinó de cada una. Ponle
> protección, o publícalo solo el rato que dure la reunión y bórralo después.

## Cómo está organizado

| Archivo | Qué hace |
|---|---|
| `App.jsx` | La tabla de la tanda, el embudo y los botones de las dos pasadas |
| `Ficha.jsx` | El retrato de un candidato: los ocho criterios, los hallazgos y los avisos |
| `Embudo.jsx` | Cuántos hay en cada punto del recorrido |
| `api.js` | La única puerta al backend, y el interruptor entre los dos modos |
| `estilos.css` | La paleta de RENASER OS y el semáforo del sistema |
