package dev.dsh.calwall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class AlarmRx extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Scheduler.scheduleDaily(ctx);
        runAsync(ctx, goAsync());
    }

    static void runAsync(final Context ctx, final PendingResult pr) {
        final PowerManager.WakeLock wl;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "calwall:apply");
        wl.setReferenceCounted(false);
        wl.acquire(60000L);
        new Thread(new Runnable() {
            public void run() {
                try {
                    Applier.apply(ctx);
                } finally {
                    try {
                        wl.release();
                    } catch (Throwable ignored) {
                    }
                    if (pr != null) {
                        pr.finish();
                    }
                }
            }
        }, "calwall-apply").start();
    }
}
