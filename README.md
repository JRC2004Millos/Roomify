# Roomify - Flujo de Trabajo con Git

Este proyecto sigue una estrategia basada en **Trunk-Based Development** con organización por **sprints** y ramas de funcionalidades.

## 📁 Estructura de Ramas

| Rama          | Propósito                                                                  |
| ------------- | -------------------------------------------------------------------------- |
| `main`        | Rama estable. Solo contiene versiones funcionales y probadas del proyecto. |
| `sprint-n`    | Rama base por sprint (ej: `sprint-1`, `sprint-2`, etc.)                    |
| `feature/...` | Ramas de desarrollo por funcionalidad (ej: `feature/captura`)              |
| `bugfix/...`  | Ramas de corrección específicas desde `main`                               |

---

## 🔄 Flujo General

1. **Inicio del Sprint**

   ```bash
   git checkout -b sprint-1
   ```

2. **Crear rama por funcionalidad**

   ```bash
   git checkout -b feature/nombre-funcionalidad
   ```

3. **Desarrollar, hacer commit y push**

   ```bash
   git add .
   git commit -m "Tarea T-1.2: interfaz de captura"
   git push -u origin feature/nombre-funcionalidad
   ```

4. **Pull Request hacia `sprint-n`**

   - Desde GitHub: `feature/...` ➔ `sprint-1`
   - Revisar, aprobar, fusionar

5. **Cierre del Sprint**
   - Fusionar `sprint-1` ➔ `main` tras validación y pruebas

---

## 🔧 Ramas recomendadas en `sprint-1`

- `feature/permissions`
- `feature/camera-ui`
- `feature/image-capture`
- `feature/image-storage`

---

## 🚑 Manejo de Errores en Producción

1. Crear rama desde `main`:

   ```bash
   git checkout main
   git pull
   git checkout -b bugfix/captura-permiso
   ```

2. Hacer correcciones y push:

   ```bash
   git add .
   git commit -m "Corrige error de permisos"
   git push origin bugfix/captura-permiso
   ```

3. Pull request a `main`

---

## 🧪 Buenas Prácticas

- No desarrollar directamente en `main`
- Hacer `git pull origin rama --rebase` para mantener historial limpio
- Usar `.gitignore` para evitar archivos innecesarios en el repo
