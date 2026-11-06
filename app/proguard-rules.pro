# PdfBox-Android loads fonts, encodings and CMaps by name at run time, so
# R8 must not rename or drop its classes.
-keep class com.tom_roush.** { *; }

# Optional extras PdfBox can use but PDF Reader doesn't ship:
# JPEG 2000 images (JP2Android) and encryption (BouncyCastle).
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.bind.**
