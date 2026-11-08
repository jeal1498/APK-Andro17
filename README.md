# Minimal Launcher

Launcher Android nativo (Kotlin + Jetpack Compose + Material 3) para teléfonos y tablets, con estética de Android stock y un **cajón inteligente** que organiza las apps en categorías automáticas y manuales.

- **APK de prueba:** [`dist/MinimalLauncher-debug.apk`](dist/MinimalLauncher-debug.apk)
- **Referencia visual:** Android 17 (API 37), versión estable más reciente al momento del desarrollo (canal *stable* del SDK de Google).

## Requisitos técnicos

| Elemento | Valor |
|---|---|
| compileSdk / targetSdk | 37 (Android 17) |
| minSdk | 26 (Android 8.0) |
| AGP / Gradle | 9.5.1 / 9.6.1 |
| Kotlin | 2.4.21 (Kotlin integrado de AGP 9) |
| Compose BOM | 2026.09.00 (Material 3 1.4, Adaptive 1.2) |
| Persistencia | Room 2.8 (KSP) + DataStore Preferences |
| JDK para compilar | 17 o superior |

Sin Hilt: un contenedor manual (`AppContainer`) basta para el tamaño del proyecto y evita dependencias.

## Compilar

```bash
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # pruebas (JVM + Robolectric)
```

Configura `local.properties` con `sdk.dir=/ruta/al/android-sdk` (plataforma 37 y build-tools 37).

## Instalación

1. Activa *Opciones de desarrollador → Depuración USB* (o permite instalar apps de origen desconocido).
2. `adb install -r dist/MinimalLauncher-debug.apk` (o copia el APK al dispositivo y ábrelo).
3. El paquete de debug es `com.minimal.launcher.debug`.

## Establecer como launcher predeterminado

- Pulsa **Inicio**: Android preguntará qué app de inicio usar → *Minimal Launcher* → *Siempre*.
- O bien: abre *Minimal Launcher* desde tu launcher actual → mantén pulsado un espacio vacío → **Ajustes de inicio** → **Establecer como predeterminado** (usa `RoleManager.ROLE_HOME` en Android 10+; en versiones anteriores abre *Ajustes → Apps predeterminadas → App de inicio*).

## Funcionalidades

**Pantalla de inicio**
- Fondo de pantalla del sistema, Dynamic Color, modo claro/oscuro/sistema; textos y barras de estado se adaptan al brillo del fondo (`WallpaperColors`).
- Varias páginas (`HorizontalPager`) con indicador, añadir/eliminar páginas vacías.
- Reloj y fecha (actualizado con `ACTION_TIME_TICK`, sin sondeo); toque → alarmas / calendario.
- Dock (una ranura por columna), barra de búsqueda opcional.
- Mantener pulsado: menú contextual (accesos directos de la app, información, quitar, desinstalar) y arrastre: mover entre celdas, entre páginas (bordes), al dock y del dock; soltar una app sobre otra crea carpeta; sobre una carpeta la añade.
- Carpetas: abrir, renombrar, añadir apps, sacar apps, desagrupar.
- Deslizar hacia arriba abre el cajón; *Inicio* cierra todo y vuelve a la primera página.

**Widgets (Android reales)**
- `AppWidgetHost` propio, selector con vista previa agrupado por app, flujo oficial: `allocateAppWidgetId` → `bindAppWidgetIdIfAllowed` / `ACTION_APPWIDGET_BIND` → configuración (`startAppWidgetConfigureActivityForResult`) → colocación.
- Mover, redimensionar con tiradores (respeta `minResize*`, `maxResize*`, `resizeMode` y colisiones), reconfigurar (`WIDGET_FEATURE_RECONFIGURABLE`), quitar (libera el id).
- Se informa el tamaño al proveedor (`updateAppWidgetSize`). Posiciones y tamaños en Room; restauración tras reinicio; limpieza de ids huérfanos.
- Peticiones de anclaje de apps (`requestPinShortcut` / `requestPinAppWidget`) mediante `PinItemActivity`.

**Cajón inteligente**
- Pestañas *Categorías* / *Todas*, búsqueda (sin acentos, por prefijo/palabra/contenido/paquete; *Ir* abre el primer resultado).
- Carpetas de categoría con icono, nombre, número de apps y vista previa 2×2; apertura animada.
- 13 categorías iniciales: Google, Inteligencia artificial, Conducción, Comunicación, Redes sociales, Trabajo y productividad, Entretenimiento, Finanzas personales, Compras, Transporte y movilidad, Salud y ejercicio, Herramientas, Lectura y educación, más *Sin clasificar*. Las instalaciones anteriores se migran una sola vez (se respetan nombres/iconos personalizados, categorías propias y asignaciones manuales; Juegos/Fotografía se retiran salvo que tengan asignaciones manuales).
- Crear, editar (nombre, icono, palabras clave), eliminar y reordenar categorías.
- Menú de app: añadir a inicio/dock, categorías (multiselección), quitar de la categoría, información, ocultar, desinstalar. Arrastrar una app del cajón la coloca en inicio.
- Orden A-Z, Z-A o instaladas recientemente.

**Motor de clasificación híbrido (100 % local)**
- Jerarquía: 1) asignación manual explícita → 2) reglas especiales (Conducción > IA > Google) → 3) regla automática → 4) sin clasificar. Una app puede tener varias categorías automáticas (p. ej. Gemini: IA + Google; Gmail: Google + Comunicación). Conducción es exclusiva (Uber Driver, DiDi Conductor, Checa, Finanzas, Garaje). Una asignación manual (incluso “ninguna categoría”) nunca se sobrescribe.
- Señales automáticas: paquetes conocidos (incl. prefijos de editores de juegos), palabras clave del usuario por categoría, palabras clave en tokens del paquete y del nombre (ES/EN, sin acentos) y `ApplicationInfo.category`.
- Asignaciones manuales en Room; la clasificación automática se recalcula de forma determinista. Restaurar automática por app o global.

**Adaptabilidad**
- `WindowSizeClass` (`currentWindowAdaptiveInfo`) con los puntos de corte oficiales (600/840 dp de ancho, 480 dp de alto).
- Teléfono: 4×5; tablet (≥600 dp): 6×5; ≥840 dp: 7×5. Columnas de cajón/categorías automáticas por ancho; márgenes dinámicos en tablets; contenido del cajón con ancho máximo.
- Teléfono horizontal: dock lateral vertical, se ocultan nombres si no caben.
- La rotación se gestiona sin recrear la actividad (`configChanges`), preservando widgets y estado. Cada dispositivo guarda su propia organización (sin nube).

**Ajustes**: tema, colores del fondo, cuadrícula (reacomoda sin perder elementos), nombres en inicio/cajón, rotación, barra de búsqueda, columnas, orden, pestaña inicial, categorías, apps ocultas, restaurar clasificación, widgets colocados, fondo de pantalla, información y launcher predeterminado.

## Arquitectura

```
app/src/main/java/com/minimal/launcher/
├── LauncherApplication.kt   AppContainer (DI manual) y limpieza tras desinstalaciones
├── MainActivity.kt          Actividad HOME, flujo de widgets, botón Inicio
├── LauncherViewModel.kt     Estado de UI (StateFlow) y acciones
├── PinItemActivity.kt       Anclaje de accesos directos / widgets solicitado por apps
├── applications/            LauncherApps (detección incremental), iconos (LruCache), acciones
├── categories/              Classifier (reglas locales), CategoryResolver (jerarquía), repositorio
├── data/db, data/prefs      Room (categorías, asignaciones, ocultas, elementos de inicio) y DataStore
├── home/                    HomeScreen, CellLayout, arrastre, carpetas, reloj, GridPlacement
├── widgets/                 AppWidgetHost, host view con long-press, selector, puente AndroidView
├── drawer/                  Cajón, categorías, diálogos
├── search/                  Búsqueda local
├── settings/                Ajustes (Navigation Compose)
└── ui/                      Tema M3 + Dynamic Color, DeviceProfile adaptativo, componentes
```

Decisiones clave:
- **Un solo detector de gestos por página** (tap / long-press / arrastre) para evitar conflictos entre elementos y fondo; los widgets detectan su propia pulsación larga porque sus `RemoteViews` consumen los toques.
- **Tabla única `home_items`** (escritorio, dock y carpetas), como Launcher3: operaciones atómicas con `withTransaction`.
- **Clasificación automática no persistida**: es determinista y barata; así los cambios de reglas/palabras clave se reflejan al instante. Solo lo manual se guarda.
- **Visibilidad de paquetes** con `<queries>` específicas; sin `QUERY_ALL_PACKAGES`. Único permiso: `REQUEST_DELETE_PACKAGES` (diálogo de desinstalación del sistema).
- **Copia de seguridad**: se excluye la base de datos (los ids de widgets son específicos del dispositivo).

## Pruebas

`./gradlew testDebugUnitTest` ejecuta pruebas JVM y Robolectric (SDK 36). Ver [`docs/TESTING.md`](docs/TESTING.md) para el detalle y los resultados. Las capturas de los cuatro escenarios están en [`docs/screenshots/`](docs/screenshots/).

## Limitaciones conocidas

- **Sin emulador/dispositivo en el entorno de desarrollo** (sin KVM): la UI se validó con Robolectric (renderizado nativo) y no en hardware real. En Robolectric no hay iconos ni etiquetas reales ni wallpaper (capturas con fondo transparente).
- **Accesos directos de apps** (menú y anclados) requieren ser el launcher predeterminado (restricción de Android).
- **Deslizar hacia abajo para notificaciones** no existe como API pública; no se implementa.
- No hay *pull-to-search* de servicios externos ni sugerencias web (todo es local por diseño).
- Arrastrar desde dentro de una carpeta abierta no está soportado (se usa “Sacar de la carpeta”).
- La restauración de widgets desde una copia de seguridad del sistema (`APPWIDGET_HOST_RESTORED`) no está implementada; la BD se excluye del backup.
- Previsualizaciones de widgets basadas en `previewLayout` (API 31) no se renderizan; se usa `previewImage` o el icono.
- Rotación bloqueable solo en teléfonos (Android 16+ ignora el bloqueo en pantallas grandes).

## Próximas mejoras

- Pruebas instrumentadas en dispositivo (Espresso/Compose) y macrobenchmarks de arranque.
- Reordenar apps dentro de carpetas y del cajón por arrastre; arrastrar fuera de carpetas.
- Iconos temáticos (monocromos) y formas de icono.
- Transiciones compartidas (container transform) al abrir carpetas y apps.
- Restauración de widgets tras backup/migración de dispositivo.
- Importar disposición desde otro launcher.
