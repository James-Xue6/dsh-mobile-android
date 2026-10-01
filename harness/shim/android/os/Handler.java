package android.os;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** JVM 联调用的极简 shim（APK 里由 Android 框架提供）。 */
public final class Handler {

    private static final ScheduledExecutorService POOL = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "shim-handler");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<Runnable, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public Handler() { }

    public Handler(Looper looper) { }

    public boolean post(Runnable r) {
        POOL.execute(r);
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        removeCallbacks(r);
        ScheduledFuture<?> f = POOL.schedule(r, delayMillis, TimeUnit.MILLISECONDS);
        pending.put(r, f);
        return true;
    }

    public void removeCallbacks(Runnable r) {
        ScheduledFuture<?> f = pending.remove(r);
        if (f != null) f.cancel(false);
    }
}
