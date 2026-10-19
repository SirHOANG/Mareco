# Mareco has no reflection and no third-party libraries, so the default rules are enough.
# Classes referenced from AndroidManifest.xml (activities, the accessibility service, the
# content provider) are kept automatically.

# Enums are read with Enum.valueOf() when settings and .mareco files are loaded.
-keepclassmembers enum com.mareco.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
