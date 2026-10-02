# Add project-specific ProGuard rules here.

# WorkManager creates its Room database implementation reflectively during
# AndroidX Startup initialization. Keep the generated class name and its
# no-argument constructor when release shrinking is enabled.
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
