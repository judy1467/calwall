package dev.dsh.calwall;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

public final class Scheduler {
    static final int JOB_ID = 7301;
    static final int ALARM_REQ = 7302;

    public static long nextRunMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 5);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long t = cal.getTimeInMillis();
        if (t <= System.currentTimeMillis()) {
            t += 86400000L;
        }
        return t;
    }

    public static void scheduleDaily(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, AlarmRx.class).setAction("dev.dsh.calwall.ALARM");
            PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_REQ, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            long when = nextRunMillis();
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void cancelDaily(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, AlarmRx.class).setAction("dev.dsh.calwall.ALARM");
            PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_REQ, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            am.cancel(pi);
        } catch (Throwable ignored) {
        }
    }

    public static void scheduleJob(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            for (JobInfo j : js.getAllPendingJobs()) {
                if (j.getId() == JOB_ID) return;
            }
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, TickJob.class))
                    .setPersisted(true)
                    .setPeriodic(6L * 3600L * 1000L)
                    .build());
        } catch (Throwable ignored) {
        }
    }

    public static void ensure(Context c) {
        if (!Prefs.isAuto(c)) return;
        scheduleDaily(c);
        scheduleJob(c);
    }
}

