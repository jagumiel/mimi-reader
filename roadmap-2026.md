# Hoja de ruta 2026 de Mimi Reader

Última revisión: 20 de octubre de 2026.

## Objetivo

Actualizar Mimi Reader sin abandonar los dispositivos antiguos que siguen siendo útiles. La aplicación mantiene `minSdk 23` y se valida expresamente en Android 10 y Android 12, aunque compile y publique contra Android 16 (`compileSdk` y `targetSdk` 36).

La aplicación debe permitir tanto leer como participar: mostrar CAPTCHA que el usuario pueda resolver y publicar mensajes forma parte de la funcionalidad esencial, no de una mejora de baja prioridad.

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

### Orden de trabajo inmediato

1. Cuarto bloque de limpieza registrado en `faaf5a7`; confirmar su matriz remota junto al siguiente bloque.
2. Completar la validación de publicación y CAPTCHA: integración, resolución humana y una respuesta pública autorizada comprobadas en Android 12. Quedan Android 10, la matriz remota y casos de adjuntos/hilos nuevos con autorización específica; alcance en la fase 5 y `docs/POSTING.md`.
3. Retomar AppCompat/Fragment y los demás bloques de dependencias y refactorización.
4. Incorporar el escaneo CVE/GHSA después de los bloques acordados; el changelog 8.0 sigue requiriendo consulta previa y el README completo queda para el final.

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
  - [x] Sincronizar las 77 boards del API, incluidas `/r/` y `/trash/`, clasificarlas por tema y mostrar su estado SFW/NSFW usando `ws_board`.

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

- [ ] Hacer funcional y validar con contenido real la visualización de archivos Flash (`.swf`).
  - [x] Integrar Ruffle 0.7.1 dentro de la APK, incluidas sus variantes WebAssembly estándar y de compatibilidad, sin depender de un CDN ni de Flash nativo.
  - [x] Aislar el reproductor en un WebView con origen HTTPS interno, acceso a archivos deshabilitado y bloqueo de navegación, enlaces y peticiones de red externas.
  - [x] Pausar los SWF fuera de pantalla o cuando la aplicación pasa a segundo plano y cubrir detección, MIME y aislamiento con pruebas.
  - [x] Corregir la causa principal: `/f/` enlaza los SWF mediante el nombre original codificado, no mediante `tim`. Compartir ese contrato entre galería, enlaces de los posts y descargas por lotes; conservar las URL propias de los archivos archivados.
  - [x] Mostrar tarjetas SWF locales y pulsables en catálogo, hilos, respuestas, cuadrícula, historial y marcadores: `/f/` devuelve `tn_w=0`/`tn_h=0` y no proporciona JPEG de previsualización. Estas tarjetas identifican el formato; no son fotogramas del archivo.
  - [x] Distinguir los archivos realmente ausentes del CDN (HTTP 404), mostrar un mensaje explícito y permitir reintentar; el descargador antiguo aceptaba el HTML del error como si fuese contenido válido.
  - [x] Rechazar respuestas HTTP erróneas y descargas incompletas, escribir mediante archivos parciales, comprobar la firma `FWS`/`CWS`/`ZWS` antes de iniciar Ruffle y corregir la CSP que deformaba sus controles.
  - [x] Esperar al evento de película cargada de Ruffle antes de marcar el contenido como listo; propagar los errores de carga al contenedor nativo y evitar que una parada temporal de la actividad cancele para siempre la carga inicial de la galería.
  - [x] Validar en el Motorola Edge 20 con Android 12/API 31 la descarga y reproducción de tres archivos reales de `/f/`: `quixabelle.swf` (166.351 bytes), `susu_hiragana_anti_pexeso.swf` (2.582.366 bytes) y `tv_show.swf` (10.334.903 bytes). Comprobar metadatos, canvas activo y capturas; prueba instrumentada optativa `FlashBoardSmokeTest` con `flashThreadIds` para no depender del CDN en CI.
  - [x] Ampliar la validación a `daijobuu.swf` y `partysover.swf`, y confirmar con el usuario la reproducción de `daijobuu.swf`. El 18 de octubre de 2026, 16 de las 28 URL del catálogo respondían HTTP 200 y 12 respondían HTTP 404, incluida `jesus on the cross.swf`; que un archivo siga listado no garantiza que el CDN lo entregue.
  - [x] Sustituir el fixture vacío de Ruffle por una película original de dos fotogramas, con dimensiones y colores conocidos, para comprobar carga, pausa y reanudación en CI.
  - [x] Probar tarjetas SWF visibles y pulsables con dimensiones de miniatura nulas y el mensaje/reintento de HTTP 404. Corregir la recursión del cálculo de columnas del catálogo que la prueba de cuadrícula puso de manifiesto.
  - [ ] Repetir la validación de SWF reales en Android 10 y ampliar la muestra con archivos ActionScript 3 e interacción táctil. La disponibilidad del archivo y las limitaciones de emulación de Ruffle siguen condicionando cada SWF.
- [x] Añadir una opción de menú para borrar la caché de la aplicación: imágenes, vídeos y archivos temporales.
  - [x] Solicitar confirmación, ejecutar la limpieza fuera del hilo principal e informar del espacio liberado.
  - [x] Vaciar Glide, HTTP y los temporales internos/externos sin tocar descargas del usuario, base de datos, historial, marcadores ni preferencias.
- [x] Añadir reproducción automática opcional en la galería multimedia.
  - [x] Permitir activarla y pausarla desde un botón visible en la barra de la galería, conservando el estado elegido.
  - [x] Avanzar las imágenes tras 3 segundos de visualización.
  - [x] Avanzar al finalizar la reproducción de cada vídeo.
  - [x] Avanzar al completar un ciclo de cada GIF animado.
- [x] Mantener la precarga de toda la galería, limitando las descargas simultáneas y reordenando la cola según la distancia al elemento visible: primero las imágenes y vídeos más cercanos y después los más lejanos.
  - [x] Aplicar un máximo estricto de dos descargas, también cuando las páginas visibles registran listeners.
  - [x] Reordenar los elementos pendientes al abrir la galería y cada vez que cambia la página visible, priorizando primero el siguiente elemento en caso de empate.
- [x] Migrar gradualmente de `ViewPager` a `ViewPager2` o a una navegación equivalente.
  - [x] Migrar las pestañas dinámicas de la pantalla principal a `ViewPager2`, conservando identidades estables, restauración de fragments y cierre individual de hilos.
  - [x] Migrar el paginador secundario de hilos y retirar los adaptadores heredados que ya no se utilicen.
- [x] Migrar la navegación Atrás a `OnBackPressedDispatcher`, conservar las acciones internas de cada pantalla y habilitar el gesto Atrás predictivo.
- [x] Revisar edge-to-edge, barras del sistema, rotación y restauración del estado.
  - [x] Activar edge-to-edge de forma retrocompatible, aplicar insets seguros a toolbars, contenido y cajones, y mantener iconos legibles con los temas claro, oscuro y negro.
  - [x] Dejar que Android recree la pantalla principal al rotar y comprobar que las pestañas y su selección se restauran sin duplicar los insets.
  - [x] Extender la restauración gestionada por Android a las pantallas heredadas de lista, detalle y panel doble, evitando fragments duplicados y conservando el lado visible del panel.
- [x] Limitar y recuperar con claridad pestañas que no puedan restaurarse por falta de memoria.
  - [x] Limitar la pantalla principal a 12 pestañas, cerrando la pestaña inactiva más antigua con un aviso antes de que una sesión excesiva agote la memoria.
  - [x] Validar el estado guardado, descartar pestañas dañadas o duplicadas y conservar siempre la pestaña seleccionada al restaurar una sesión recortada.
- [x] Permitir actualizar desde el menú todas las pestañas principales y ofrecer una acción directa de reintento cuando una pestaña muestre un error.

### 4. Calidad y publicación

- [x] Crear pruebas unitarias para URLs, formatos multimedia y nombres de archivo, y pruebas instrumentadas para migraciones.
  - [x] Cubrir rutas API y enlaces externos, incluidas rutas inválidas y respuestas HTTP defectuosas.
  - [x] Cubrir normalización de extensiones, detección MP4/WebM y nombres finales/temporales de descarga.
  - [x] Validar las migraciones Room 21 -> 22 -> 23 y la migración de preferencias conservando datos existentes.
- [x] Añadir pruebas instrumentadas de arranque, pestañas, reproducción y descargas.
  - [x] Verificar que el launcher respeta la pantalla de inicio configurada, conserva los extras y recupera el destino predeterminado ante preferencias inválidas.
  - [x] Verificar creación, sustitución, límite y restauración de pestañas, navegación Atrás y recreación de la actividad.
  - [x] Verificar reproducción, autoplay y transición entre imágenes, GIF y vídeos con recursos locales en un dispositivo Android 12.
  - [x] Verificar inicio, cancelación, reintento y recuperación de descargas persistentes con WorkManager en un dispositivo Android 12.
- [x] Automatizar compilación, lint y pruebas unitarias en integración continua.
  - [x] Validar el wrapper y ejecutar `freeDebug` con JDK 17 y Android SDK 36 en GitHub Actions.
  - [x] Hacer que lint bloquee errores nuevos y corregir los diez errores heredados que impedían activarlo.
  - [x] Conservar durante 14 días la APK y los informes de pruebas y lint de cada ejecución.
- [x] Revisar reglas R8, firma, versionado, privacidad y artefactos de publicación.
  - [x] Sustituir las reglas heredadas que ocultaban advertencias por reglas R8 acotadas para Retrofit, Gson, Parcelable, fragments, workers y bibliotecas nativas.
  - [x] Permitir compilaciones release reproducibles y sin firmar en CI, cargando la firma únicamente desde un `keystore.properties` local validado.
  - [x] Hacer configurables `versionCode` y `versionName`, y documentar la creación, verificación y conservación de APK, AAB, checksums y mapas de ofuscación.
  - [x] Retirar Stetho y el registro de cabeceras HTTP, y actualizar la política incluida en la aplicación para describir los datos locales, permisos y conexiones reales.
  - [x] Generar en CI APK y AAB release minificados, su checksum SHA-256 y el `mapping.txt`, además de la APK de depuración.
- [x] Preparar una matriz mínima de pruebas: Android 10, 12, 14 y 16.
  - [x] Investigar los fallos de las ejecuciones 11–13: WebView antiguo en API 29, fallo de shaders de Ruffle/wgpu en API 31/36 y descarga incompleta de la imagen API 34. Preferir WebGL compatible, informar de WebView incompatible y errores de arranque, fijar/verificar un proveedor de pruebas solo para el emulador API 29 y reintentar únicamente la preparación del SDK. Detalles y procedencia en `docs/CI.md`.
  - [x] Confirmar en la ejecución remota 15 (`eee2ebd`) los cuatro trabajos instrumentados de Android 10, 12, 14 y 16, además de compilación, lint y SBOM. Cada limpieza posterior requiere volver a pasar CI con su propio commit.
    - [x] Confirmar en la ejecución 14 que compilación/SBOM y las suites de Android 12, 14 y 16 pasan. La comprobación del WebView original de Android 10 también pasa; corregir el rechazo erróneo de su respuesta `Success` al seleccionar el proveedor fijado y verificar el paquete/versión realmente activos. Ampliar las pruebas auxiliares de CI a 21 casos.
    - [x] Confirmar en la ejecución 15 la suite completa de Android 10 después de instalar/seleccionar el WebView de pruebas, sin omitir la reproducción SWF obligatoria.
  - [x] Repetir la suite completa del Motorola con el teléfono desbloqueado: el 19 de octubre, 30 pruebas superadas y 2 optativas omitidas, sin fallos; comprobar además por separado la prueba optativa de capacidad/reproducción del WebView original (1 prueba superada con WebView 155). Se han validado también 80 pruebas unitarias, lint, SBOM y APK/AAB release. La ejecución remota 15 confirma la matriz completa y la comprobación del proveedor original de API 29; las pruebas auxiliares de CI se ampliaron a 21.
  - [x] Configurar en CI la ejecución paralela de las 27 pruebas instrumentadas sobre API 29, 31, 34 y 36 mediante emuladores acelerados.
  - [x] Conservar durante 14 días informes HTML, resultados JUnit, información del dispositivo y logcat de cada API.
  - [x] Validar la suite completa en el Motorola Edge 20 físico con Android 12/API 31: 27 pruebas superadas el 17 de octubre de 2026. El 18 de octubre se amplió la prueba técnica de Ruffle a una película de dos fotogramas y se verificaron además tres SWF reales de `/f/` mediante una prueba optativa con red.
- [ ] Ejecutar una fase final de refactorización y retirada de dependencias obsoletas antes de preparar el changelog.
  - [ ] Inventariar dependencias directas y transitivas, retirar las que no se utilicen y sustituir las abandonadas o incompatibles con el mantenimiento futuro.
    - [x] Generar un inventario versionado de declaraciones y resoluciones de Gradle: release, desugaring, pruebas, procesadores y herramientas de compilación. Documentar candidatos de limpieza y diferencias entre versiones declaradas y resueltas en `docs/DEPENDENCIES.md`.
    - [ ] Retirar o sustituir los candidatos confirmados en cambios incrementales con sus pruebas.
      - [x] Primer bloque: retirar `commons-codec:1.12`, `room-guava:2.8.5` y el falso procesador `kapt androidx.annotation`; conservar las anotaciones normales y las integraciones Rx utilizadas. El SBOM pasa de 157 a 154 componentes al desaparecer también `kotlinx-coroutines-guava`; no cambian las versiones de los componentes conservados. Inventario actualizado de 77 a 74 declaraciones.
        - [x] Retirar además la supresión amplia de advertencias Apache en R8. Validar 80 pruebas unitarias de aplicación, 21 auxiliares de CI, lint, SBOM, APK/AAB release y la suite del Motorola (30 superadas, 2 optativas omitidas). Registrar tamaños antes/después y el fallo interno inicial de lint en `docs/DEPENDENCIES.md`.
        - [x] Cobertura acumulada confirmada por el usuario en la matriz remota de Android 10/12/14/16 de `eeb2e31`, que ya incluye esta limpieza.
      - [x] Segundo bloque: retirar `AudioSettingsHost`, `MimiTab` y los layouts huérfanos `activity_gallery`, `activity_video_ad` y `activity_youtube`, tras comprobar referencias en código, XML, manifiesto, reglas R8, view binding y variantes. Conservar `activity_gallery2`, los contratos actuales de pestañas/audio, los enlaces externos de YouTube y el ID `gallery_container` compartido con otra pantalla. No borrar archivos solo por su antigüedad; auditoría en `docs/DEPENDENCIES.md`.
        - [x] Compilar `freeDebug`, su APK de pruebas y `paidDebug`; verificar las 80 pruebas unitarias de aplicación, las 21 auxiliares de CI y el SBOM sin cambios de componentes ni snapshots. Confirmar que los tres bindings huérfanos ya no se generan en ninguna de las dos variantes.
        - [x] Validar lint sin errores (avisos `UnusedResources` de 490 a 487), APK/AAB release minificados y la suite completa del Motorola con Android 12: 30 pruebas superadas y dos optativas omitidas, sin fallos. Restaurar el ajuste temporal de pantalla encendida y documentar los tamaños locales sin atribuir mejoras de rendimiento no medidas.
        - [x] Cobertura acumulada confirmada por el usuario en la matriz remota de Android 10/12/14/16 de `eeb2e31`, que ya incluye estas retiradas de recursos.
      - [x] Tercer bloque: consolidar la única aplicación gratuita, retirando `free`/`paid` de ambos módulos y la variante `amazon` de `chanlib`. Mantener solo `debug`/`release`, los identificadores y la firma actuales; adaptar tareas, CI, artefactos, diagnósticos e inventario/SBOM sin modificar versiones de dependencias. Añadir `verifyApplicationVariants` a `check` y CI para proteger los identificadores y evitar variantes comerciales accidentales.
        - [x] Validar 80 pruebas unitarias, 22 auxiliares de CI, lint sin errores, SBOM y APK/AAB release minificados. Confirmar que los 154 componentes y sus grafos/versiones/huellas no cambian tras renombrar variantes/configuraciones; renombrar el snapshot a `mimi-reader-release.cdx.json`.
        - [x] Actualizar la instalación existente con `adb install -r`, comprobar las nueve huellas de bases de datos/preferencias sin cambios y el arranque correcto. Suite del Motorola con Android 12: 30 pruebas superadas, dos optativas omitidas y cero fallos; ajuste temporal de pantalla restaurado.
        - [x] Confirmada por el usuario la ejecución de GitHub Actions de `eeb2e31`: matriz remota de Android 10/12/14/16 y subida de artefactos con las rutas sin flavors.
      - [x] Cuarto bloque: retirar repositorios locales y declaraciones vacías/comentadas, compartir las versiones de RxJava y JUnit y sustituir las plantillas de pruebas de `chanlib` por cobertura real. Refactorizar el cálculo de colores sin cambiar su resultado y retirar referencias documentales a la antigua edición de donación, sin tocar instalaciones ni datos.
        - [x] Confirmar el uso indirecto de Kotlin Serialization por Lifecycle saved-state y conservar el BOM 1.8.1: retirarlo podría cambiar la versión seleccionada, aunque no haya imports directos en la app.
        - [x] Validar 80 pruebas unitarias de app, ocho de biblioteca y 22 auxiliares de CI; compilar APK/APK de pruebas y comprobar el SBOM de runtime sin cambios. Añadir la suite de biblioteca a CI y una prueba instrumentada de equivalencia de colores con 136 entradas.
        - [x] Suite completa del Motorola con Android 12: 31 pruebas superadas y dos optativas omitidas, sin fallos; restaurar el ajuste de pantalla encendida. Lint sin errores, sin nuevas supresiones y con los avisos anteriores.
        - [x] Validar APK/AAB release minificados y vital lint, conservando identificador, versión, `minSdk 23` y objetivo API 36. Registrar tamaños locales sin atribuir mejoras de rendimiento no medidas.
        - [ ] Confirmar la matriz remota de Android 10/12/14/16 con los cambios de este bloque.
      - [ ] Grupo AndroidX posterior a recuperar CAPTCHA/publicación: rastrear y sustituir las selecciones alpha heredadas de AppCompat/Fragment, revisando también Preference y los adaptadores legacy que las introducen. Validar temas, diálogos, navegación/restauración, insets y R8 antes de ampliar el grupo.
  - [x] Generar y versionar en el repositorio un SBOM que incluya las dependencias de Gradle y los componentes empaquetados manualmente, como Ruffle.
    - [x] Cubrir el grafo de `release` (antes `freeRelease`) antes de R8 mediante CycloneDX 1.6: 152 componentes Maven tras la primera limpieza, `chanlib`, Ruffle y las huellas SHA-256 de sus siete archivos.
    - [x] Añadir `generateSbom`, `updateSbom`, `verifySbom` y pruebas negativas del verificador; comprobar esquema, referencias, cobertura, hashes y frescura de los snapshots en GitHub Actions y conservar los informes durante 14 días.
  - [ ] Añadir a GitHub Actions una validación del SBOM y un análisis de vulnerabilidades conocidas (CVE/GHSA), publicar los resultados en SARIF y bloquear inicialmente solo vulnerabilidades `high` o `critical` no justificadas.
    - Recuperar primero CAPTCHA/publicación y continuar después con los bloques acordados de limpieza de dependencias y refactorización; el escaneo queda aplazado, no es la siguiente tarea automática.
    - [x] Validar el formato y la frescura del SBOM en CI; el análisis CVE/GHSA y SARIF sigue pendiente.
    - [ ] Ejecutar el análisis en cada `pull_request`, al actualizar el SBOM y de forma programada para detectar vulnerabilidades publicadas después del último cambio de código.
    - [ ] Identificar Ruffle y cualquier componente manual mediante versión y Package URL; documentar excepciones temporales con caducidad para evitar que los falsos positivos se conviertan en exclusiones permanentes.
  - [ ] Separar las clases con demasiadas responsabilidades y eliminar adaptadores, recursos y rutas heredadas que hayan quedado sin uso tras la modernización.
    - [ ] Revisar la acción de valorar la app y sus rutas antiguas de Play Store/Amazon en Acerca de; no son código huérfano, pero Mimi ya no se distribuye en Play Store. Tratarlo como una revisión funcional de enlaces y preferencias, con sus pruebas.
  - [ ] Añadir pruebas unitarias o instrumentadas para cada sustitución y ejecutar la matriz completa de Android 10, 12, 14 y 16 para detectar regresiones.
  - [ ] Comparar tamaño de artefactos, tiempos de arranque y comportamiento de R8 antes y después de la limpieza.
- [ ] Antes de preparar el release, definir conjuntamente el alcance y el formato del changelog; no iniciar esta tarea sin consultar primero al responsable del proyecto.
  - La nueva versión se publicará como **8.0** debido al alcance excepcional de la modernización.
  - El changelog debe reconstruir y resumir todos los cambios desde **7.0.1**, punto de partida del fork.
- [ ] Como última tarea, actualizar el `README.md` con una descripción actual de la aplicación, los cambios incorporados durante la modernización, las instrucciones de compilación e instalación y la aclaración de que Mimi ya no está disponible en Google Play.

### 5. Publicación de contenido (implementación y validación prioritaria)

- [x] Revisar el flujo de publicación y CAPTCHA vigente del sitio y el código heredado de la app, sin asumir que la integración antigua sigue siendo válida.
  - El formulario actual usa TCaptcha (`t-challenge`/`t-response`), no la integración Google antigua. Contratos, fuentes oficiales y límites de WebView documentados en `docs/POSTING.md`.
- [ ] Mostrar un desafío completo, legible e interactivo que pueda resolver manualmente el usuario, con WebView o una integración de navegador compatible según lo que admita el sitio.
  - [x] Implementar un diálogo dedicado sin puente JavaScript nativo, con recursos restringidos, recarga y acceso alternativo a la página en navegador. Corregir la altura cero del WebView detectada al revisar la pantalla física y protegerla con una aserción de tamaño visible.
  - [x] Confirmar carga y resolución humana del desafío real en el Motorola Edge 20 / Android 12 / WebView 155: vista de prueba superada el 20 de octubre (`OK, 1 test`) tras permitir Spur/Monocle. El sitio aplica sus propios tiempos de espera y el recibo de la respuesta en la app no equivale a validación de una publicación por el servidor.
  - [ ] Repetir la aceptación manual en Android 10 y validar teclado/regreso desde navegador. La aceptación por los proveedores depende de WebView, dispositivo y red; el navegador externo no transfiere automáticamente su sesión al formulario nativo.
- [x] Integrar la respuesta o token del CAPTCHA con el formulario y el envío explícito del usuario. Permitir responder en hilos y crear hilos donde la board lo admita, conservando los adjuntos ya admitidos por la app.
  - Cobertura local de multipart, respuesta vacía válida, adjunto ausente y recibo oficial de éxito con IDs largos. La publicación real sigue pendiente de autorización y validación.
- [x] Permitir renovar el desafío cuando falle o caduque, cancelar y reintentar sin perder el borrador ni provocar envíos duplicados.
  - Retener un único intento durante rotación, no reintentar automáticamente y advertir de entrega incierta tras muerte del proceso. El formulario se cierra solo con recibo confirmado; los fallos conservan texto y adjunto.
- [x] Mostrar con claridad errores de CAPTCHA, validación, red, límites de publicación o restricciones del sitio. No registrar respuestas, tokens ni cookies de sesión en logs.
  - No aceptar HTTP 200 genérico, redirecciones ni páginas de bloqueo como éxito; preservar la sesión nativa Pass frente a cookies antiguas del navegador y limitar cookies de verificación al host de publicación.
- [ ] Cubrir formulario, transmisión de la respuesta/token, caducidad, cancelación y reintento con pruebas simuladas e instrumentadas; validar interacción, teclado, rotación y regreso a la app en Android 10 y 12, además de la matriz CI habitual.
  - [x] Añadir 14 pruebas unitarias de protocolo y seis pruebas instrumentadas locales del nuevo flujo; vista real opcional sin API de publicación conectada, excluida de CI normal. La sexta cubre el formulario completo hasta HTTP simulado y recuperación tras rechazo.
  - [x] Aclarar el último paso: “Done” termina el CAPTCHA, no publica; “Get Captcha” solicita otro. Habilitar el botón nativo “Send message” solo con verificación lista, sin envíos ni recargas automáticos.
    - Validación posterior con el Motorola desbloqueado: suite completa de 40 casos, 37 superados y tres optativos omitidos, sin fallos; seis casos locales de publicación y 94 pruebas unitarias de app correctos. Ajuste de pantalla encendida restaurado a 0. El intento previo interrumpido por el bloqueo no se cuenta como correcto.
  - [ ] Completar la matriz de interacción manual Android 10/12 y confirmar la matriz remota de este commit. En Android 12 ya se ha resuelto manualmente un desafío real sin publicar.
- [x] Realizar una prueba de publicación real solo después de acordar con el usuario el contenido y el destino y recibir su confirmación. Las pruebas automatizadas de CI no publicarán mensajes reales.
  - El 20 de octubre, el usuario autorizó el hilo `/g/110111399`, preparó el mensaje y resolvió el desafío. Una sola confirmación publicó `110119159`, comprobado por recibo y por lectura independiente del JSON público. No se hicieron más publicaciones de prueba. Cobertura: respuesta de texto en Android 12; no implica validar adjuntos ni creación de hilos.

Resolver el CAPTCHA y decidir publicar son acciones del usuario. Esta fase tiene prioridad sobre AppCompat/Fragment y el escaneo; el resto de la modernización se retomará después.

## Criterios de compatibilidad

- No elevar `minSdk` sin una decisión explícita.
- Cada migración de biblioteca debe compilar y probarse en Android 10 y Android 12.
- Las actualizaciones no deben borrar ni invalidar historial, marcadores, filtros o preferencias.
- Los cambios de almacenamiento deben conservar el acceso a carpetas ya elegidas cuando Android mantenga el permiso SAF.
- Si una API moderna no existe en Android 10 o 12, debe protegerse por versión o disponer de una alternativa compatible.
