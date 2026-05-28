#!/bin/bash
set -e

# Configuración de nombres y rutas
APP_NAME="SubtitleEditor"
PACKAGE="com/subtitle/editor"
BUILD_DIR="build"
GEN_DIR="build/gen"
OBJ_DIR="build/obj"

# 1. Intentar localizar el SDK de Android
SDK_ROOT=""
if [ -d "$ANDROID_HOME" ]; then
    SDK_ROOT="$ANDROID_HOME"
elif [ -d "$PREFIX/share/android-sdk" ]; then
    SDK_ROOT="$PREFIX/share/android-sdk"
elif [ -d "$HOME/android-sdk" ]; then
    SDK_ROOT="$HOME/android-sdk"
elif [ -d "/usr/local/lib/android/sdk" ]; then
    SDK_ROOT="/usr/local/lib/android/sdk"
fi

# Localizar el android.jar más reciente
ANDROID_JAR=$(find $SDK_ROOT/platforms/ -name "android.jar" | sort -V | tail -n 1 2>/dev/null || echo "")
if [ -z "$ANDROID_JAR" ]; then
    echo "Error: No se encontró android.jar"
    exit 1
fi

# 2. Localizar herramientas correctas dentro de build-tools (AAPT2 y D8)
AAPT2_BIN=$(find $SDK_ROOT/build-tools/ -name "aapt2" | sort -V | tail -n 1 2>/dev/null || echo "aapt2")
D8_BIN=$(find $SDK_ROOT/build-tools/ -name "d8" | sort -V | tail -n 1 2>/dev/null || echo "")

echo "-> Usando SDK: $ANDROID_JAR"
echo "-> Usando AAPT2: $AAPT2_BIN"
if [ -n "$D8_BIN" ]; then
    echo "-> Usando D8: $D8_BIN"
fi

echo "[1/5] Limpiando entorno..."
rm -rf $BUILD_DIR
mkdir -p $GEN_DIR $OBJ_DIR

echo "[2/5] Procesando recursos de la interfaz (AAPT2)..."
$AAPT2_BIN compile --dir app/src/main/res -o build/resources.zip
$AAPT2_BIN link --manifest app/src/main/AndroidManifest.xml \
    -I "$ANDROID_JAR" \
    --java $GEN_DIR \
    -o build/unaligned.apk \
    build/resources.zip

echo "[3/5] Compilando código fuente Java..."
# CORRECCIÓN: Búsqueda masiva y flexible de cualquier archivo .java dentro de main
JAVA_FILES=$(find app/src/main -type f -name "*.java")

if [ -z "$JAVA_FILES" ]; then
    echo "❌ ERROR CRÍTICO: No se encontró ningún archivo .java en app/src/main"
    exit 1
fi

echo "-> Archivos Java detectados:"
echo "$JAVA_FILES"

# Compilamos vinculando tus archivos encontrados y los recursos mapeados en R.java
javac -source 1.8 -target 1.8 -d $OBJ_DIR -cp "$ANDROID_JAR:$GEN_DIR" \
    $GEN_DIR/$PACKAGE/R.java \
    $JAVA_FILES

echo "[4/5] Convirtiendo clases a formato Dalvik (.dex)..."
CLASS_FILES=$(find $OBJ_DIR -type f -name "*.class")

if [ -z "$CLASS_FILES" ]; then
    echo "❌ Error: No se generaron archivos .class en $OBJ_DIR"
    exit 1
fi

# CORRECCIÓN: Pasamos el directorio $OBJ_DIR en vez de archivos sueltos para que d8 conserve la ruta interna com/subtitle/editor
if [ -n "$D8_BIN" ]; then
    $D8_BIN --output build/classes.zip --lib "$ANDROID_JAR" $OBJ_DIR
    unzip -p build/classes.zip classes.dex > build/classes.dex
elif command -v dx &> /dev/null; then
    dx --dex --output=build/classes.dex $OBJ_DIR
else
    echo "Error: No se encontró d8 ni dx válidos."
    exit 1
fi

# Añadimos classes.dex al APK usando la utilidad zip de Linux
echo "Añadiendo classes.dex al APK..."
cd build
zip -u unaligned.apk classes.dex
cd ..

echo "[5/5] Alineando y firmando el APK..."
# Buscamos zipalign en las herramientas del SDK si no está global
ZIPALIGN_BIN=$(find $SDK_ROOT/build-tools/ -name "zipalign" | sort -V | tail -n 1 2>/dev/null || echo "zipalign")
$ZIPALIGN_BIN -f 4 build/unaligned.apk build/$APP_NAME.apk

# Crear una clave de prueba si no existe para firmar
if [ ! -f debug.keystore ]; then
    keytool -genkey -v -keystore debug.keystore -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
fi

# Buscamos apksigner oficial en las herramientas del SDK con sintaxis limpia
APKSIGNER_BIN=$(find $SDK_ROOT/build-tools/ -name "apksigner" | sort -V | tail -n 1 2>/dev/null)
if [ -z "$APKSIGNER_BIN" ]; then
    APKSIGNER_BIN="apksigner"
fi

$APKSIGNER_BIN sign --ks debug.keystore --ks-pass pass:android --out build/$APP_NAME-signed.apk build/$APP_NAME.apk

echo "----------------------------------------"
echo "¡ÉXITO! APK generado en: build/$APP_NAME-signed.apk"
echo "----------------------------------------"
