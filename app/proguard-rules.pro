# Keep Shizuku + reflective newProcess
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.Shizuku {
    private static *** newProcess(...);
}

-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**
