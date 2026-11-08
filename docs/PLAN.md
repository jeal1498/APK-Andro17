# Plan de implementación

Rama de trabajo: `claude/frequent-apps-split-screen-h8odzc`. Un commit por fase, con pruebas (`./gradlew testDebugUnitTest`) en verde antes de seguir. No crear pull request salvo que se pida. Explicar todo de forma breve.

## Fase 0: Base común
- Migración de Room con tres tablas: `app_usage` (app, franja horaria, día de la semana, conteo, última apertura), `app_pairs` (id, apps de 2 o 3 elementos, categoría) y `hidden_apps`.
- Registro de cada apertura de app en `AppActions.kt`.
- Sección nueva en Ajustes con un patrón reutilizable para opciones.
- Prueba de la migración (`PersistenceTest`).

## Fase 1: Frecuentes
- Fila "Frecuentes" con las 5 a 8 apps más abiertas, calculadas con `app_usage` y decaimiento por días. Opción de fijar apps a mano.
- Aparece en el inicio y en el cajón.
- Posición configurable: arriba, abajo, barra lateral (tablet, vía `DeviceProfile`) u oculta. Movible arrastrando en modo edición.

## Fase 2: Pares de pantalla dividida
- Pares de 2 apps y de 3 apps (la tercera como ventana flotante, si el dispositivo lo permite).
- Servicio de Accesibilidad nuevo, declarado en el manifest, con flujo guiado para pedir el permiso una sola vez.
- Al tocar el par: abrir la app A, activar la división con `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` y abrir la app B con `FLAG_ACTIVITY_LAUNCH_ADJACENT | FLAG_ACTIVITY_NEW_TASK`.
- Si una app no admite multiventana, avisar con claridad.
- Los pares NO van en una carpeta aparte. Cada par se guarda en la categoría de sus apps vía `CategoryResolver` (ej. Uber + Didi dentro de "Conducción", junto a sus apps individuales), con ícono combinado. Si las categorías difieren, va en la de la primera app y se puede cambiar a mano.
- Se crean con pulsación larga en una app, "Combinar en par".

## Fase 3: Gestos generales en la pantalla de inicio
- Deslizar arriba, abajo, izquierda y derecha; doble toque; pellizco; deslizar con dos dedos; pulsación larga en un espacio vacío.
- Pantalla "Gestos" en Ajustes: cada gesto se asigna a una acción (app, par, cajón, búsqueda, notificaciones, vista de páginas o modo edición, linterna, apagar pantalla).
- Apagar pantalla reutiliza el servicio de Accesibilidad de la Fase 2.
- Sin choques con el gesto de navegación de Android, el cambio de páginas, `CellLayout` ni `DragController`.

## Fase 4: Sugerencias por contexto
- Fila "Sugeridas" por franja horaria y día (laboral o fin de semana) sobre `app_usage`.
- Reglas por eventos: audífonos sugieren música; cargador del coche sugiere mapas, Uber o Didi.
- Todo local, sin enviar datos fuera del dispositivo.
- Pulsación larga para descartar una sugerencia en ese horario.

## Fase 5: Búsqueda universal
- Ampliar `AppSearch.kt` con contactos (permiso opcional), ajustes del sistema y acciones rápidas como "llamar a…".

## Fase 6: Ocultar apps con huella
- Ocultar apps y desbloquearlas con `BiometricPrompt`.
- Las apps ocultas se excluyen del cajón, la búsqueda, Frecuentes y Sugeridas.

## Reglas
- Sin dependencias innecesarias.
- Decir claramente lo que no se puede probar sin dispositivo real (pantalla dividida, gestos).
- Al final de cada fase: push, APK debug y un resumen corto de qué probar en teléfono y tablet.
- Al terminar: actualizar el README y dejar el APK en `dist/`.
