# Hoja de ruta 2026 de Mimi Reader

Última revisión: 5 de octubre de 2026.

## Objetivo

Actualizar Mimi Reader sin abandonar los dispositivos antiguos que siguen siendo útiles. La aplicación mantiene `minSdk 23` y se valida expresamente en Android 10 y Android 12, aunque compile y publique contra Android 16 (`compileSdk` y `targetSdk` 36).

## Estado actual

### Completado

- [x] Actualizar la cadena de compilación y el objetivo de Android a API 36.
- [x] Mantener compatibilidad de ejecución con Android 10 y Android 12.
- [x] Añadir reproducción de MP4 con AndroidX Media3, conservando WebM.
- [x] Adaptar guardado y selección de carpetas a Storage Access Framework y almacenamiento acotado.
- [x] Corregir el arranque en Android 12 mediante una versión actual de WorkManager.
- [x] Eliminar los anuncios intercalados cada diez elementos y sus dependencias de interfaz.
- [x] Ajustar permisos, notificaciones y componentes exportados a los requisitos modernos de Android.

### En curso: estabilidad y trabajo en segundo plano

- [x] Evitar que abrir, sustituir o cerrar una pestaña destruya todos los fragmentos existentes.
- [x] Dejar que `ViewPager` gestione el ciclo de vida de las pestañas, sin instanciarlas manualmente desde la actividad.
- [x] Sustituir el antiguo `IntentService` de descargas por un `Worker` persistente.
- [x] Añadir progreso, cancelación, reintentos con espera exponencial y recuperación tras cerrar el proceso.
- [x] Reanudar lotes omitiendo archivos que ya se descargaron correctamente.
- [x] Validar la APK en el Motorola Edge 20 con Android 12 abriendo diez hilos y restaurándolos tras matar el proceso.
- [x] Validar una descarga SAF por lotes en un dispositivo real (105 archivos, finalización correcta).
- [x] Validar la cancelación durante una descarga lenta y la reanudación después de matar el proceso.
  - Prueba final del 5 de octubre de 2026: cancelación sin temporales residuales y recuperación de 151 archivos con cero diferencias de tamaño.

## Siguientes fases

### 1. Red y compatibilidad con la API

- [x] Actualizar OkHttp, Retrofit, Gson, Jsoup y Conscrypt de forma incremental.
  - [x] Gson actualizado de 2.8.6 a 2.14.0 y unificado entre los módulos.
  - [x] Retrofit unificado en 2.11.0, manteniendo OkHttp 4.9.0; Retrofit 2.12.0 queda aplazado hasta migrar el proyecto a Kotlin 2.1.
  - [x] OkHttp actualizado de 4.9.0 a 4.12.0, la última versión de la rama 4.x compatible con la migración incremental actual.
  - [x] Jsoup actualizado de 1.13.1 a 1.23.2 con desugaring NIO para conservar la compatibilidad con Android antiguos.
  - [x] Retirar Conscrypt 2.5.1, que no se utilizaba explícitamente, y usar el proveedor TLS actualizado de la plataforma Android.
- [x] Añadir contratos de deserialización para catálogo, hilos, MP4, WebM, campos opcionales y JSON malformado.
- [x] Añadir pruebas HTTP simuladas para rutas, `Cache-Control`, respuestas 404/429/503 y cuerpos JSON truncados.
- [x] Añadir contratos de parsing HTML para comentarios, login y errores de publicación.
- [x] Centralizar errores HTTP, límites de peticiones y tiempos de espera, respetando el máximo de una petición por segundo de la API sin limitar las descargas multimedia.
- [x] Probar y proteger catálogo, hilo, miniaturas y multimedia ante respuestas incompletas o cambios de la API.
- [x] Añadir caché y estados de error recuperables para evitar pantallas vacías.
  - [x] Conservar catálogos separados por board y reutilizarlos cuando falle una actualización.
  - [x] Mantener visibles boards, catálogos e hilos ya cargados, indicando que son contenido guardado y ofreciendo reintento.
- [x] Añadir filtro persistente de boards: todas, solo SFW o solo NSFW, independiente del criterio de ordenación.

### 2. Persistencia y migraciones

- [x] Actualizar Room a 2.8.5, migrar a Kotlin 2.3.21 y sustituir las APIs de ciclo de vida obsoletas por los módulos y observadores actuales de Lifecycle 2.11.0.
- [ ] Versionar y probar todas las migraciones de base de datos con copias reales.
  - [x] Añadir una prueba instrumentada de la migración `22 → 23` que valida el esquema y la conservación de historial, boards favoritas, filtros e hilos ocultos.
  - [x] Cubrir `21 → 22 → 23` con una base v21 reproducible reconstruida desde la migración, incluidos valores nulos heredados, el renombrado de filtros y la incorporación de `board_name` a los posts. El historial del repositorio comienza en v22 y no contiene una exportación original de v21.
  - [ ] Incorporar una copia real anonimizada anterior a la versión 22 si se recupera de una instalación antigua, para contrastarla con la base reconstruida.
- [x] Garantizar que historial, marcadores, filtros y preferencias sobreviven a una actualización.
  - [x] Probar en Room las rutas `21 → 22 → 23` y `22 → 23` conservando los datos persistentes del usuario.
  - [x] Versionar las preferencias, conservar claves desconocidas y normalizar sin pérdida los tipos heredados antes de que la aplicación los lea durante el arranque.
  - [x] Validar una reinstalación con `adb install -r` en el Motorola Edge 20 con Android 12: preferencias y base de datos conservaron sus huellas, y la aplicación arrancó sin crashes tras la actualización.
- [x] Reducir el trabajo de base de datos en el hilo principal.
  - [x] Encapsular las escrituras síncronas de Room en operaciones diferidas ejecutadas en el planificador de E/S.
  - [x] Hacer atómica la sustitución de los posts almacenados de un hilo.
  - [x] Eliminar la lectura bloqueante del historial al observar un hilo y esperar a que finalicen las escrituras del refresco en segundo plano.

### 3. Navegación e interfaz moderna

- [ ] Añadir visualización de archivos Flash (`.swf`), evaluando una emulación local segura y compatible con Android 10 y Android 12.
- [x] Mantener la precarga de toda la galería, limitando las descargas simultáneas y reordenando la cola según la distancia al elemento visible: primero las imágenes y vídeos más cercanos y después los más lejanos.
  - [x] Aplicar un máximo estricto de dos descargas, también cuando las páginas visibles registran listeners.
  - [x] Reordenar los elementos pendientes al abrir la galería y cada vez que cambia la página visible, priorizando primero el siguiente elemento en caso de empate.
- [ ] Migrar gradualmente de `ViewPager` a `ViewPager2` o a una navegación equivalente.
  - [x] Migrar las pestañas dinámicas de la pantalla principal a `ViewPager2`, conservando identidades estables, restauración de fragments y cierre individual de hilos.
  - [ ] Migrar el paginador secundario de hilos y retirar los adaptadores heredados que ya no se utilicen.
- Implementar correctamente el gesto Atrás predictivo antes de retirar la desactivación temporal.
- Revisar edge-to-edge, barras del sistema, rotación y restauración del estado.
- Limitar y recuperar con claridad pestañas que no puedan restaurarse por falta de memoria.

### 4. Calidad y publicación

- Crear pruebas unitarias para URLs, formatos multimedia, nombres de archivo y migraciones.
- Añadir pruebas instrumentadas de arranque, pestañas, reproducción y descargas.
- Automatizar compilación, lint y pruebas en integración continua.
- Revisar reglas R8, firma, versionado, privacidad y artefactos de publicación.
- Preparar una matriz mínima de pruebas: Android 10, 12, 14 y 16.

### 5. Publicación de contenido (prioridad baja)

- Investigar el CAPTCHA vigente y su flujo compatible con WebView o navegador.
- Recuperar la publicación solo después de estabilizar lectura, multimedia y descargas.

## Criterios de compatibilidad

- No elevar `minSdk` sin una decisión explícita.
- Cada migración de biblioteca debe compilar y probarse en Android 10 y Android 12.
- Las actualizaciones no deben borrar ni invalidar historial, marcadores, filtros o preferencias.
- Los cambios de almacenamiento deben conservar el acceso a carpetas ya elegidas cuando Android mantenga el permiso SAF.
- Si una API moderna no existe en Android 10 o 12, debe protegerse por versión o disponer de una alternativa compatible.
