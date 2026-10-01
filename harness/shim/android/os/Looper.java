package android.os;

/** JVM 联调用的极简 shim（APK 里由 Android 框架提供）。 */
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return MAIN; }
    public void quit() { }
}
