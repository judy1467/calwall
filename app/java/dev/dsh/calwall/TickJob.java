package dev.dsh.calwall;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class TickJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        Scheduler.scheduleDaily(this);
        if (Prefs.isAuto(this) && Prefs.staleToday(this)) {
            new Thread(new Runnable() {
                public void run() {
                    Applier.apply(TickJob.this);
                    jobFinished(params, false);
                }
            }, "calwall-tick").start();
            return true;
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
