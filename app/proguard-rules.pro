# Add project-specific ProGuard rules here.

# WorkManager creates its Room database implementation reflectively during
# AndroidX Startup initialization. Keep the generated class name and its
# no-argument constructor when release shrinking is enabled.
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}

# pdfbox-android references an optional JP2/JPEG2000 decoder (com.gemalto.jp2)
# only when a PDF uses JPX image filters. We don't ship that optional dep, so
# R8 must not fail the build on the dangling reference.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.**
