# Tracker Alertas — Aplicación Android

Aplicación móvil Android nativa en Kotlin que recibe notificaciones push desde el backend AWS cuando el sensor MPU6050 detecta golpes, sacudidas o caídas, y muestra una alerta a pantalla completa con sonido personalizable.

Esta es la **Parte III** del proyecto. Las partes I y II (hardware ESP32, backend Flask en AWS y dashboard web) están en las carpetas hermanas del repositorio raíz.

---

## ✨ Características

- 🚨 **Pantalla de alerta a pantalla completa** estilo llamada entrante, aparece sobre la pantalla bloqueada y sobre cualquier app
- 🎨 **Tema dinámico** según el tipo de evento (rojo para GOLPE, naranja para CAÍDA, violeta para SACUDIDA)
- 🎵 **Audio de alarma personalizable** desde un selector dentro de la app
- 💫 **Animaciones** de ondas pulsantes y latido del ícono
- 📳 **Vibración** prolongada con patrón repetitivo
- 🔔 Funciona con Firebase Cloud Messaging (FCM)
- ✅ Soporta Android 8 (Oreo) hasta Android 14 con todos los permisos especiales gestionados

---

## 📋 Requisitos

- Android Studio 2024.x o superior
- Android SDK 35 (Android 15)
- Kotlin 2.0.21 / JDK 11
- Un dispositivo Android físico con Android 8.0 (API 26) o superior
  - Recomendado: Android 14 para probar todas las restricciones nuevas
- Una cuenta de Firebase (gratuita)
- El backend Flask de la Parte I/II corriendo y accesible

---

## 🚀 Instalación y configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/DominicDelgado/Sistemas-Embebidos-SENSOR.git
cd Sistemas-Embebidos-SENSOR/android
```

### 2. Crear un proyecto Firebase

1. Ve a [Firebase Console](https://console.firebase.google.com)
2. Crea un nuevo proyecto (puedes llamarlo como quieras)
3. Añade una app Android con el package name `com.tracker.alertas`
4. Descarga el archivo `google-services.json`
5. Cópialo a `android/app/google-services.json`

> ⚠️ El archivo `google-services.json` NO está incluido en el repositorio por seguridad. Debes generar el tuyo propio.

### 3. Configurar la IP del backend

Abre los siguientes archivos y reemplaza la IP `98.87.5.225` por la IP pública de tu instancia EC2:

- `app/src/main/java/com/tracker/alertas/MainActivity.kt` (constante `BACKEND_URL`)
- `app/src/main/java/com/tracker/alertas/AlertaFirebaseService.kt` (función `registrarTokenEnBackend`)
- `app/src/main/res/xml/network_security_config.xml` (etiqueta `<domain>`)

### 4. Añadir tus propios audios (opcional)

Por temas de derechos de autor, los archivos MP3 originales no están incluidos en el repositorio. Para añadir los tuyos:

1. Coloca los archivos `.mp3` en `app/src/main/res/raw/`
2. Reglas de nombre: **solo letras minúsculas (a-z), números y guion bajo (_)**. No espacios, no tildes, no eñe.
3. Edita la lista `audios` en `app/src/main/java/com/tracker/alertas/AudioPrefs.kt` para reflejar los nombres de tus archivos.

Si no añades audios personalizados, la app usará la alarma del sistema por defecto.

### 5. Compilar y ejecutar

1. Abre el proyecto en Android Studio (`File → Open` → selecciona la carpeta `android/`)
2. Espera a que termine el Gradle Sync
3. Conecta un dispositivo Android físico vía USB con la depuración activada
4. Click en ▶️ Run

---

## 🔧 Configurar el backend para envío de push

El backend Flask (Parte I/II) necesita la librería `firebase-admin` instalada y un archivo `firebase-credentials.json` con las credenciales de servicio de tu proyecto Firebase.

```bash
pip install firebase-admin
```

Para generar las credenciales: Firebase Console → ⚙️ Configuración del proyecto → Cuentas de servicio → **Generar nueva clave privada**.

Sube el JSON resultante a tu servidor:

```bash
scp -i tu-key.pem firebase-credentials.json ubuntu@tu-ip:/home/ubuntu/mpu6050/
chmod 600 ~/mpu6050/firebase-credentials.json
```

El código del backend modificado (con la lógica de envío push) está en el archivo `backend/app.py` del repositorio raíz.

---

## 🔐 Permisos críticos (Android 14)

La primera vez que abras la app, te aparecerán **3 diálogos secuenciales** pidiendo activar permisos especiales. Es indispensable aceptar los 3 para que las alertas funcionen correctamente:

| Permiso | Para qué sirve |
|---|---|
| 🔔 **POST_NOTIFICATIONS** | Permitir notificaciones (Android 13+) |
| 📺 **USE_FULL_SCREEN_INTENT** | Mostrar la pantalla de alerta sobre cualquier app y sobre el bloqueo |
| 🪟 **SYSTEM_ALERT_WINDOW** | Permitir que la Activity aparezca sobre otras apps |
| 🔋 **Optimización de batería** | Que el celular reciba pushes con la pantalla apagada |

---

## 🧪 Probar la alerta sin mover el sensor

Hay dos formas:

1. **Botón "🚨 Disparar alerta de prueba"** dentro de la app
2. Desde un navegador, abre:
   - `http://TU_IP/test-push?tipo=GOLPE`
   - `http://TU_IP/test-push?tipo=CAIDA`
   - `http://TU_IP/test-push?tipo=SACUDIDA`

---

## 📁 Estructura del proyecto
android/
├── build.gradle.kts              # Configuración Gradle del proyecto
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml        # Versiones centralizadas
└── app/
├── build.gradle.kts          # Configuración Gradle del módulo
├── google-services.json      # ⚠️ NO incluido — debes generar el tuyo
└── src/main/
├── AndroidManifest.xml
├── java/com/tracker/alertas/
│   ├── MainActivity.kt              # Pantalla principal + permisos + selector audio
│   ├── AlertaActivity.kt            # Pantalla de alarma a pantalla completa
│   ├── AlertaFirebaseService.kt     # Recibe los push de FCM
│   └── AudioPrefs.kt                # Lista de audios y persistencia
└── res/
├── layout/
│   ├── activity_main.xml
│   └── activity_alerta.xml
├── drawable/                    # Gradientes, círculos, badges
├── raw/                         # ⚠️ Audios MP3 (no incluidos)
└── xml/
└── network_security_config.xml
---

## 🛠️ Tecnologías usadas

- **Lenguaje:** Kotlin 2.0.21
- **Build system:** Gradle Kotlin DSL
- **Min SDK:** API 26 (Android 8.0 Oreo)
- **Target SDK:** API 35 (Android 15)
- **Firebase BOM:** 33.5.1
- **OkHttp:** 4.12.0 para peticiones HTTP
- **AndroidX:** AppCompat, ConstraintLayout, Activity-KTX, Core-KTX
- **Material Components:** 1.12.0

---

## 📜 Licencia

MIT License — proyecto académico de uso libre con fines educativos.

---

## 👨‍🎓 Autor

**Dominic Jair Delgado Huamputupa**
Código: 230251
Curso: Sistemas Embebidos
Docente: José Mauro Pillco Quispe
UNSAAC - 2026 - I