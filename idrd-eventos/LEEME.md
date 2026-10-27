# Eventos deportivos IDRD (Java + Swing)

Muestra los eventos del Excel `data/Eventos_IDRD.xlsx` con filtros y permite registrar un correo
para recibir un aviso cuando el evento esté cerca.

## Cómo abrirlo en IntelliJ IDEA
1. Descomprime la carpeta `idrd-eventos`.
2. IntelliJ: **File > Open...** y selecciona el archivo `pom.xml` (o la carpeta) > **Open as Project**.
3. Espera a que Maven descargue las librerías (POI para Excel y Angus Mail para correo). Necesita internet la primera vez.
4. **File > Project Structure > Project > SDK**: elige JDK 17 o superior.
5. Abre `src/main/java/co/idrd/eventos/Main.java` y pulsa el triángulo verde junto a `main` > **Run 'Main.main()'**.

El directorio de trabajo debe ser la carpeta del proyecto (es el valor por defecto en IntelliJ),
porque `config.properties` y `data/` se leen con rutas relativas.

## Qué hace
- **Filtros:** tipo de evento, disciplina (depende del tipo), localidad, mes, texto libre y "solo próximos".
  Se pueden ordenar las columnas haciendo clic en el encabezado.
- **Avisos:** selecciona uno o varios eventos en la tabla, escribe tu correo, elige cuántos días antes
  quieres el aviso y pulsa **Registrar aviso**. Se guardan en `data/suscripciones.csv`.
  **Mis avisos...** permite ver y eliminar los registrados.
- **Cuándo se envían:** al abrir el programa y luego cada 30 minutos (configurable) MIENTRAS esté abierto.
  Cada aviso se envía una sola vez.
  Para que funcione con el programa cerrado, programa el comando `java -jar target/eventos-idrd-1.0.jar --notificar`
  (después de `mvn package`) en el Programador de tareas de Windows o en cron, una vez al día.

## Configurar el envío real de correos
Edita `config.properties`: `smtp.usuario` y `smtp.clave`.
Con Gmail necesitas una "contraseña de aplicación" (verificación en 2 pasos activada).
Sin esos datos el programa funciona en **modo simulación**: muestra en el panel "Notificaciones"
el aviso que enviaría, sin enviar nada.

## Sobre el Excel
- Las columnas se leen por su nombre de encabezado: ID, Categoria, Disciplina, Mes (1-12), Localidad, Asistentes.
- El Excel original solo traía el **mes**. Para poder avisar con días de anticipación, a la copia en
  `data/` se le agregaron las columnas **Dia** y **Anio** (valores de ejemplo inventados, igual que el resto
  de datos). Si no existen, el programa usa el día 1 del mes y el año de `anio.por.defecto`.
- Puedes agregar filas nuevas debajo de la última; se leen solas. También puedes usar **Cargar otro Excel...**.
