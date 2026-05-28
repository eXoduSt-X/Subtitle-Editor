cat << 'EOF' > build.sh
#!/bin/bash
set -e

# Configuración de nombres y rutas
APP_NAME="lrcmaker"
PACKAGE="com/ejemplo/lrc"
BUILD_DIR="build"
GEN_DIR="build/gen"
OBJ_DIR="build/obj"

# Intentar localizar el SDK mínimo en Termux
ANDROID_JAR=$(find $PREFIX/share/android-sdk/platforms/ -name "android.jar" | sort -V | tail -n 1 2>/dev/null || echo "")
if [ -z "$ANDROID_JAR" ]; then
    # Ruta alternativa común si usas paquetes personalizados
    ANDROID_JAR="$HOME/android-sdk/platforms/android-26/android.jar"
fi

echo "[1/5] Limpiando entorno..."
rm -rf $BUILD_DIR
mkdir -p $GEN_DIR $OBJ_DIR

echo "[2/5] Procesando recursos de la interfaz (AAPT2)..."
aapt2 compile --dir app/src/main/res -o build/resources.zip
aapt2 link --manifest app/src/main/AndroidManifest.xml \
    -I "$ANDROID_JAR" \
    --java $GEN_DIR \
    -o build/unaligned.apk \
    build/resources.zip

echo "[3/5] Compilando código fuente Java..."
ecj -d $OBJ_DIR -cp "$ANDROID_JAR" \
    $GEN_DIR/$PACKAGE/R.java \
    app/src/main/java/$PACKAGE/MainActivity.java

echo "[4/5] Convirtiendo clases a formato Dalvik (.dex)..."
if command -v d8 &> /dev/null; then
    d8 --output build/classes.dex --lib "$ANDROID_JAR" $OBJ_DIR/$PACKAGE/*.class
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
EOF
chmod +x build.sh
