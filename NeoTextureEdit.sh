#!/bin/sh
LWJGL_VERS=2.9.1
exec java -Djava.library.path=lib/lwjgl-$LWJGL_VERS/ -cp lib/lwjgl-$LWJGL_VERS/jar/lwjgl.jar:lib/lwjgl-$LWJGL_VERS/jar/lwjgl_util.jar:NeoTextureEdit.jar com.mystictri.neotextureedit.TextureEditor $@

