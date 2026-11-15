# Experience apps read hidden platform classes reflectively (SystemProperties,
# IOverlayManager). Those live in the boot classpath and are not affected by R8;
# nothing in the app needs to be kept for them. Keep source/line info for crash logs.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
