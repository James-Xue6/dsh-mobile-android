package com.dsh.mobile.notify;

import android.content.Context;

/** JVM 垫片：常驻通知钩子，离线验证时不需要真的发通知。 */
public final class Notifier {
    private Notifier() { }
    public static void onGatewayState(Context c, boolean ready) { }
}
