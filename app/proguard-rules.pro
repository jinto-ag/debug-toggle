# Keep TileService and Activity classes
-keep public class com.jintoag.debugtoggle.** extends android.app.Activity
-keep public class com.jintoag.debugtoggle.** extends android.service.quicksettings.TileService
-keep public class com.jintoag.debugtoggle.** extends android.content.ContentProvider

# Keep Shizuku classes and AIDL interfaces
-keep class rikka.shizuku.** { *; }
-keep interface moe.shizuku.server.** { *; }
-keep class moe.shizuku.server.** extends android.os.Binder { *; }
