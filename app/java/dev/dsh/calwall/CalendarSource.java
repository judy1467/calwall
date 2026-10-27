package dev.dsh.calwall;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.CalendarContract;
import java.util.ArrayList;
import java.util.List;

public final class CalendarSource {

    public static final class Ev {
        public final long begin;
        public final long end;
        public final String title;
        public final boolean allDay;
        public final int color;

        Ev(long begin, long end, String title, boolean allDay, int color) {
            this.begin = begin;
            this.end = end;
            this.title = title == null ? "" : title;
            this.allDay = allDay;
            this.color = color;
        }
    }

    private static final String[] PROJECTION = {
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_COLOR
    };

    public static List<Ev> query(Context c, long from, long to) {
        List<Ev> out = new ArrayList<Ev>();
        try {
            ContentResolver cr = c.getContentResolver();
            Cursor cur = CalendarContract.Instances.query(cr, PROJECTION, from, to);
            if (cur == null) {
                return out;
            }
            int iBegin = cur.getColumnIndex(CalendarContract.Instances.BEGIN);
            int iEnd = cur.getColumnIndex(CalendarContract.Instances.END);
            int iTitle = cur.getColumnIndex(CalendarContract.Instances.TITLE);
            int iAll = cur.getColumnIndex(CalendarContract.Instances.ALL_DAY);
            int iColor = cur.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR);
            while (cur.moveToNext()) {
                long b = cur.getLong(iBegin);
                long e = cur.getLong(iEnd);
                String t = iTitle >= 0 ? cur.getString(iTitle) : "";
                boolean all = iAll >= 0 && cur.getInt(iAll) != 0;
                int col = iColor >= 0 ? cur.getInt(iColor) : 0;
                out.add(new Ev(b, e, t, all, col));
            }
            cur.close();
        } catch (Throwable ignored) {
        }
        return out;
    }
}
