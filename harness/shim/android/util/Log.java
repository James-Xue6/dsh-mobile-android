package android.util;

/** JVM 垫片：把 App 里的 android.util.Log 调用打到 stdout。 */
public final class Log {
    private Log() { }
    public static int i(String tag, String msg) { System.out.println("LOG/" + tag + ": " + msg); return 0; }
    public static int w(String tag, String msg) { System.out.println("LOG/" + tag + ": " + msg); return 0; }
    public static int e(String tag, String msg) { System.out.println("LOG/" + tag + ": " + msg); return 0; }
    public static int d(String tag, String msg) { System.out.println("LOG/" + tag + ": " + msg); return 0; }
}
