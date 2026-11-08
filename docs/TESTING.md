# Pruebas — Minimal Launcher

Comando: `./gradlew testDebugUnitTest` (JVM + Robolectric 4.17, SDK 36, renderizado nativo).
Cada clase se ejecuta en su propia JVM (`forkEvery = 1`) para aislar el estado de Robolectric.

**Resultado: 50/50 pruebas OK · compilación debug OK · lint: 0 errores** (16 avisos: versiones de dependencias y APIs de Configuration/Resources en Compose).

| Área exigida | Clase | Pruebas | Qué verifica |
|---|---|---|---|
| Compilación debug | `assembleDebug` | — | APK firmado con clave debug, target/compile SDK 37 |
| Motor de clasificación | `ClassifierTest` | 10 | Paquetes conocidos y prefijos, palabras clave (paquete > nombre), categoría del sistema, palabras clave de usuario, categorías eliminadas, normalización |
| Jerarquía manual/auto | `CategoryResolverTest` | 6 | Manual > automática > sin clasificar, multi-categoría, manual vacío no se sobrescribe, asignaciones a categorías borradas |
| Persistencia | `PersistenceTest` | 5 | Categorías, asignaciones, ajustes, inicio (carpetas, dock, widgets con posición/tamaño) tras cerrar y reabrir BD/DataStore; reacomodo al cambiar cuadrícula sin pérdidas; sin duplicados |
| Cuadrícula | `GridPlacementTest` | 7 | Huecos libres, nuevas páginas, colisiones, repack, conversión píxel→celda |
| Detección de apps | `AppDetectionTest` | 3 | `LauncherApps` (lista, orden, exclusión propia), callbacks de instalación y desinstalación |
| Rol HOME | `HomeRoleTest` | 3 | Intent MAIN/HOME resuelve a `MainActivity` (singleTask, configChanges), anclaje de atajos/widgets, permisos mínimos |
| Widgets | `WidgetManagerTest` | 3 | Lista de proveedores, `bindAppWidgetIdIfAllowed` / intent de permiso, configuración requerida, spans por tamaño, `AppWidgetHostView` propio, informe de tamaño |
| Búsqueda | `AppSearchTest` | 2 | Ranking, acentos, paquete |
| Adaptabilidad | `DeviceProfileTest` | 4 | Breakpoints, columnas, dock lateral, márgenes, cuadrícula por defecto |
| Navegación / gestos | `LauncherInteractionTest` | 3 | Deslizar arriba abre el cajón, búsqueda y estado vacío, pulsación larga → menú → selector de widgets, disposición inicial persistida |
| Orientación (4 escenarios) | `Phone/Tablet × Portrait/Landscape Test` | 4 | Inicio, dock, cajón, categoría y lista completa dentro de los límites de pantalla; capturas en `docs/screenshots/` |

## Escenarios validados

| Escenario | Ventana | Cuadrícula | Observaciones |
|---|---|---|---|
| Teléfono vertical | 411×891 dp | 4×5 | Dock inferior + barra de búsqueda; cajón 4 col., categorías 3 col. |
| Teléfono horizontal | 891×411 dp | 4×5 | Dock lateral, sin barra; cajón 7+ col., categorías 6 col. |
| Tablet vertical | 800×1280 dp | 6×5 | Márgenes dinámicos; cajón con ancho máximo |
| Tablet horizontal | 1280×800 dp | 6×5 | Más columnas, contenido centrado |

Sin elementos cortados ni fuera de pantalla (aserciones de límites). Las capturas de inicio usan un degradado como fondo simulado: en Robolectric no hay fondo de pantalla, iconos ni etiquetas reales.

## No verificado (sin emulador ni dispositivo: el entorno no tiene KVM)

- Ejecución en hardware real, rendimiento y fluidez de animaciones.
- Renderizado de widgets de terceros reales y sus actividades de configuración.
- Diálogo del sistema para elegir la app de inicio, accesos directos de apps (requieren ser el launcher predeterminado).
- Arrastre de iconos/widgets con gestos reales (la lógica de colocación sí está probada).
