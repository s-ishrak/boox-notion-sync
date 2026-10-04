# Pebble templating engine resolves template variables and extensions via reflection
-keep class io.pebbletemplates.** { *; }
-dontwarn io.pebbletemplates.**

# Pebble depends on slf4j-api; no logging backend is bundled on Android and none is needed
-dontwarn org.slf4j.**
