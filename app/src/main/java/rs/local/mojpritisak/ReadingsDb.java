package rs.local.mojpritisak;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class ReadingsDb extends SQLiteOpenHelper {
    ReadingsDb(Context context) { super(context, "blood_pressure.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE readings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "measured_at INTEGER NOT NULL," +
                "systolic INTEGER NOT NULL," +
                "diastolic INTEGER NOT NULL," +
                "pulse INTEGER NOT NULL," +
                "note TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX readings_time ON readings(measured_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    void save(Reading r) {
        ContentValues c = new ContentValues();
        c.put("measured_at", r.timestamp);
        c.put("systolic", r.sys);
        c.put("diastolic", r.dia);
        c.put("pulse", r.pulse);
        c.put("note", r.note);
        if (r.id < 0) getWritableDatabase().insertOrThrow("readings", null, c);
        else getWritableDatabase().update("readings", c, "id=?", new String[]{Long.toString(r.id)});
    }

    void delete(long id) {
        getWritableDatabase().delete("readings", "id=?", new String[]{Long.toString(id)});
    }

    List<Reading> all() {
        List<Reading> result = new ArrayList<>();
        try (Cursor cur = getReadableDatabase().rawQuery(
                "SELECT id, measured_at, systolic, diastolic, pulse, note FROM readings ORDER BY measured_at DESC, id DESC", null)) {
            while (cur.moveToNext()) result.add(new Reading(
                    cur.getLong(0), cur.getLong(1), cur.getInt(2), cur.getInt(3), cur.getInt(4), cur.getString(5)));
        }
        return result;
    }
}
