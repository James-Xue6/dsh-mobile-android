package android.content;

/** JVM 垫片：只为让 App 真实 net 层源码能在桌面上编译运行（P0 修复的离线验证用）。 */
public class Context {
    public Context getApplicationContext() { return this; }
}
