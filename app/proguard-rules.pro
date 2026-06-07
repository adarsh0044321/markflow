# ProGuard / R8 rules for MarkFlow Release Build

# Apache POI & XMLBeans
# Under Android, AWT and XML stream writers/ Saxon/ Batik are not present.
# We suppress their warnings as the app does not invoke these execution paths.
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.logging.log4j.**
-dontwarn aQute.bnd.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn org.osgi.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.etsi.uri.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.w3c.dom.html.**
-dontwarn org.apache.jcp.xml.dsig.internal.**
-dontwarn org.bouncycastle.**

# Additional optional or JVM-only dependencies transitively used by Apache POI & XMLBeans
-dontwarn com.github.javaparser.**
-dontwarn com.sun.org.apache.xml.internal.resolver.**
-dontwarn de.rototor.pdfbox.graphics2d.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.xml.crypto.**
-dontwarn org.apache.maven.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.tools.ant.**
-dontwarn org.apache.xml.security.**
-dontwarn org.ietf.jgss.**
-dontwarn org.w3c.dom.events.**
-dontwarn org.w3c.dom.svg.**
-dontwarn org.w3c.dom.traversal.**


# Keep classes that may be accessed via reflection in POI and XMLBeans
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.openxmlformats.schemas.** { *; }

# iText PDF
-dontwarn com.itextpdf.**
-keep class com.itextpdf.** { *; }

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Room Database
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Hilt Dependency Injection
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**
