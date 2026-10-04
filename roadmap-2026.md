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

- [ ] Actualizar OkHttp, Retrofit, Gson, Jsoup y Conscrypt de forma incremental.
  - [x] Gson actualizado de 2.8.6 a 2.14.0 y unificado entre los módulos.
  - [ ] Actualizar Retrofit sin mezclar el cambio con la migración principal de OkHttp.
  - [ ] Actualizar OkHttp, Jsoup y Conscrypt en incrementos independientes.
- [x] Añadir contratos de deserialización para catálogo, hilos, MP4, WebM, campos opcionales y JSON malformado.
- Centralizar errores HTTP, límites de peticiones y tiempos de espera.
- Probar catálogo, hilo, miniaturas y multimedia ante respuestas incompletas o cambios de la API.
- Añadir caché y estados de error recuperables para evitar pantallas vacías.

### 2. Persistencia y migraciones

- Actualizar Room y eliminar APIs de ciclo de vida obsoletas.
- Versionar y probar todas las migraciones de base de datos con copias reales.
- Garantizar que historial, marcadores, filtros y preferencias sobreviven a una actualización.
- Reducir el trabajo de base de datos en el hilo principal.

### 3. Navegación e interfaz moderna

- Migrar gradualmente de `ViewPager` a `ViewPager2` o a una navegación equivalente.
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
