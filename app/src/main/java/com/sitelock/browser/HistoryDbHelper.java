package com.sitelock.browser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览历史数据库（SQLite）。
 * 表 history: id, url, title, visit_time
 * 同一 URL 重复访问时更新标题与时间，避免大量重复条目。
 */
public class HistoryDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "sitelock_history.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "history";
    private static final String COL_ID = "id";
    private static final String COL_URL = "url";
    private static final String COL_TITLE = "title";
    private static final String COL_TIME = "visit_time";

    public HistoryDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_URL + " TEXT UNIQUE, " +
                COL_TITLE + " TEXT, " +
                COL_TIME + " INTEGER)");
        db.execSQL("CREATE INDEX idx_time ON " + TABLE + "(" + COL_TIME + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    /** 记录一次访问：已存在则更新标题与时间 */
    public void record(String url, String title) {
        if (TextUtils.isEmpty(url)) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_URL, url);
        cv.put(COL_TITLE, TextUtils.isEmpty(title) ? url : title);
        cv.put(COL_TIME, System.currentTimeMillis());
        // INSERT OR REPLACE，靠 URL UNIQUE 约束去重
        db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** 删除指定 URL 的历史 */
    public void delete(String url) {
        getWritableDatabase().delete(TABLE, COL_URL + "=?", new String[]{url});
    }

    /** 清空全部历史 */
    public void clear() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    /** 按时间倒序获取历史列表 */
    public List<HistoryItem> getAll() {
        List<HistoryItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE, null, null, null, null, null, COL_TIME + " DESC");
        try {
            int iUrl = c.getColumnIndexOrThrow(COL_URL);
            int iTitle = c.getColumnIndexOrThrow(COL_TITLE);
            int iTime = c.getColumnIndexOrThrow(COL_TIME);
            while (c.moveToNext()) {
                list.add(new HistoryItem(c.getString(iUrl), c.getString(iTitle), c.getLong(iTime)));
            }
        } finally {
            c.close();
        }
        return list;
    }

    /** 历史条目 */
    public static class HistoryItem {
        public final String url;
        public final String title;
        public final long time;

        public HistoryItem(String url, String title, long time) {
            this.url = url;
            this.title = title;
            this.time = time;
        }
    }
}
