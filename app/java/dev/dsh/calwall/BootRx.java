package dev.dsh.calwall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootRx extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Scheduler.ensure(ctx);
        boolean afterUpdate = intent != null
                && Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction());
        if (Prefs.isAuto(ctx) && (afterUpdate || Prefs.staleToday(ctx))) {
            AlarmRx.runAsync(ctx, goAsync());
        }
    }
}
