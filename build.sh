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
if [ -d "$PREFIX/share/android-sdk" ]; then
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

# 2. Localizar el AAPT2 correcto dentro de build-tools
AAPT2_BIN=$(find $SDK_ROOT/build-tools/ -name "aapt2" | sort -V | tail -n 1 2>/dev/null || echo "aapt2")

echo "-> Usando SDK: $ANDROID_JAR"
echo "-> Usando AAPT2: $AAPT2_BIN"

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
# CORRECCIÓN: Se añadieron los flags -source 1.8 y -target 1.8 para dar soporte a anotaciones y varargs
ecj -source 1.8 -target 1.8 -d $OBJ_DIR -cp "$ANDROID_JAR" \
    $GEN_DIR/$PACKAGE/R.java \
    app/src/main/java/$PACKAGE/MainActivity.java

echo "[4/5] Convirtiendo clases a formato Dalvik (.dex)..."
# Buscamos de forma dinámica todos los archivos .class generados para evitar problemas de rutas fijas
CLASS_FILES=$(find $OBJ_DIR -name "*.class")

if command -v d8 &> /dev/null; then
    d8 --output build/classes.dex --lib "$ANDROID_JAR" $CLASS_FILES
else
    dx --dex --output=build/classes.dex $OBJ_DIR
fi

# Añadir el archivo dex dentro del APK generado
cd build
aapt add unaligned.apk classes.dex > /dev/null
cd ..

echo "[5/5] Alineando y firmando el APK..."
zipalign -f 4 build/unaligned.apk build/$APP_NAME.apk

# Crear una clave de prueba si no existe para firmar
if [ ! -f debug.keystore ]; then
    keytool -genkey -v -keystore debug.keystore -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
fi

apksigner sign --ks debug.keystore --ks-pass pass:android --out build/$APP_NAME-signed.apk build/$APP_NAME.apk

echo "----------------------------------------"
echo "¡ÉXITO! APK generado en: build/$APP_NAME-signed.apk"
echo "----------------------------------------"
