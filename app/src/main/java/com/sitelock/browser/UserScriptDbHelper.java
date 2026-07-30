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
 * 用户脚本存储（SQLite）。
 * 表 scripts: id, name, description, code, enabled, update_time
 */
public class UserScriptDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "sitelock_userscripts.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "scripts";
    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_DESC = "description";
    private static final String COL_CODE = "code";
    private static final String COL_ENABLED = "enabled";
    private static final String COL_TIME = "update_time";

    public UserScriptDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT, " +
                COL_DESC + " TEXT, " +
                COL_CODE + " TEXT, " +
                COL_ENABLED + " INTEGER DEFAULT 1, " +
                COL_TIME + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    /** 新增脚本，返回新 id。会自动解析头部元信息填入 name/description */
    public long insert(String code) {
        UserScript s = new UserScript();
        s.code = code == null ? "" : code;
        s.parseMeta();
        s.enabled = true;
        s.updateTime = System.currentTimeMillis();
        return getWritableDatabase().insert(TABLE, null, toContentValues(s));
    }

    /** 更新脚本代码（重新解析元信息） */
    public void update(long id, String code) {
        UserScript s = new UserScript();
        s.id = id;
        s.code = code == null ? "" : code;
        s.parseMeta();
        s.updateTime = System.currentTimeMillis();
        // 保留原 enabled 状态
        UserScript old = get(id);
        s.enabled = old != null ? old.enabled : true;
        getWritableDatabase().update(TABLE, toContentValues(s), COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** 切换启用状态 */
    public void setEnabled(long id, boolean enabled) {
        ContentValues cv = new ContentValues();
        cv.put(COL_ENABLED, enabled ? 1 : 0);
        getWritableDatabase().update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** 删除 */
    public void delete(long id) {
        getWritableDatabase().delete(TABLE, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** 获取单个 */
    public UserScript get(long id) {
        Cursor c = getReadableDatabase().query(TABLE, null, COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (c.moveToFirst()) return fromCursor(c);
            return null;
        } finally {
            c.close();
        }
    }

    /** 获取全部脚本 */
    public List<UserScript> getAll() {
        List<UserScript> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE, null, null, null, null, null, COL_TIME + " DESC");
        try {
            while (c.moveToNext()) list.add(fromCursor(c));
        } finally {
            c.close();
        }
        return list;
    }

    /** 获取所有启用的脚本 */
    public List<UserScript> getEnabled() {
        List<UserScript> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE, null, COL_ENABLED + "=1", null, null, null, COL_TIME + " DESC");
        try {
            while (c.moveToNext()) list.add(fromCursor(c));
        } finally {
            c.close();
        }
        return list;
    }

    private ContentValues toContentValues(UserScript s) {
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, s.name);
        cv.put(COL_DESC, s.description);
        cv.put(COL_CODE, s.code);
        cv.put(COL_ENABLED, s.enabled ? 1 : 0);
        cv.put(COL_TIME, s.updateTime);
        return cv;
    }

    private UserScript fromCursor(Cursor c) {
        UserScript s = new UserScript();
        s.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        s.name = c.getString(c.getColumnIndexOrThrow(COL_NAME));
        s.description = c.getString(c.getColumnIndexOrThrow(COL_DESC));
        s.code = c.getString(c.getColumnIndexOrThrow(COL_CODE));
        s.enabled = c.getInt(c.getColumnIndexOrThrow(COL_ENABLED)) == 1;
        s.updateTime = c.getLong(c.getColumnIndexOrThrow(COL_TIME));
        s.parseMeta();
        return s;
    }
}
