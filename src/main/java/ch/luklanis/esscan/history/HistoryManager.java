/*
 * Copyright (C) 2009 ZXing authors
 * Copyright (C) 2012 Lukas Landis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.luklanis.esscan.history;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ch.luklanis.esscan.CaptureActivity;
import ch.luklanis.esscan.paymentslip.EsResult;
import ch.luklanis.esscan.paymentslip.PsResult;

/**
 * <p>Manages functionality related to scan history.</p>
 *
 * @author Sean Owen
 */
public final class HistoryManager {

    private static final String TAG = HistoryManager.class.getSimpleName();

    private static final int MAX_ITEMS = 500;

    private static final String[] HISTORY_COLUMNS = {DBHelper.HISTORY_CODE_ROW_COL, DBHelper.HISTORY_TIMESTAMP_COL, DBHelper.HISTORY_ADDRESS_ID_COL, DBHelper.HISTORY_AMOUNT_COL, DBHelper.HISTORY_REASON_COL, DBHelper.HISTORY_FILE_NAME_COL};

    private static final String[] ADDRESS_COLUMNS = {DBHelper.ADDRESS_ACCOUNT_COL, DBHelper.ADDRESS_TIMESTAMP_COL, DBHelper.ADDRESS_ADDRESS_COL};

    static final String ITEM_WHERE_CODE_ROW_QUERY = "SELECT " +
            "hi." + DBHelper.HISTORY_CODE_ROW_COL + ", " +
            "hi." + DBHelper.HISTORY_TIMESTAMP_COL + ", " +
            "hi." + DBHelper.HISTORY_ADDRESS_ID_COL + ", " +
            "hi." + DBHelper.HISTORY_AMOUNT_COL + ", " +
            "hi." + DBHelper.HISTORY_REASON_COL + ", " +
            "hi." + DBHelper.HISTORY_FILE_NAME_COL + ", " +
            "ad." + DBHelper.ADDRESS_ADDRESS_COL + " " +
            "FROM " + DBHelper.HISTORY_TABLE_NAME + " AS hi " +
            "LEFT OUTER JOIN " + DBHelper.ADDRESS_TABLE_NAME + " AS ad " +
            "ON hi." + DBHelper.HISTORY_ADDRESS_ID_COL + " = ad.id " +
            "WHERE hi." + DBHelper.HISTORY_CODE_ROW_COL + " = ? " +
            "ORDER BY hi." + DBHelper.HISTORY_TIMESTAMP_COL + " DESC";

    static final String BUILD_ITEMS_QUERY = "SELECT " +
            "hi." + DBHelper.HISTORY_CODE_ROW_COL + ", " +
            "hi." + DBHelper.HISTORY_TIMESTAMP_COL + ", " +
            "hi." + DBHelper.HISTORY_ADDRESS_ID_COL + ", " +
            "hi." + DBHelper.HISTORY_AMOUNT_COL + ", " +
            "hi." + DBHelper.HISTORY_REASON_COL + ", " +
            "hi." + DBHelper.HISTORY_FILE_NAME_COL + ", " +
            "ad." + DBHelper.ADDRESS_ADDRESS_COL + " " +
            "FROM " + DBHelper.HISTORY_TABLE_NAME + " AS hi " +
            "LEFT OUTER JOIN " + DBHelper.ADDRESS_TABLE_NAME + " AS ad " +
            "ON hi." + DBHelper.HISTORY_ADDRESS_ID_COL + " = ad.id " +
            "ORDER BY hi." + DBHelper.HISTORY_TIMESTAMP_COL + " DESC";

    static final String BUILD_UNEXPORTED_ITEMS_QUERY = "SELECT " +
            "hi." + DBHelper.HISTORY_CODE_ROW_COL + ", " +
            "hi." + DBHelper.HISTORY_TIMESTAMP_COL + ", " +
            "hi." + DBHelper.HISTORY_ADDRESS_ID_COL + ", " +
            "hi." + DBHelper.HISTORY_AMOUNT_COL + ", " +
            "hi." + DBHelper.HISTORY_REASON_COL + ", " +
            "hi." + DBHelper.HISTORY_FILE_NAME_COL + ", " +
            "ad." + DBHelper.ADDRESS_ADDRESS_COL + " " +
            "FROM " + DBHelper.HISTORY_TABLE_NAME + " AS hi " +
            "LEFT OUTER JOIN " + DBHelper.ADDRESS_TABLE_NAME + " AS ad " +
            "ON hi." + DBHelper.HISTORY_ADDRESS_ID_COL + " = ad.id " +
            "WHERE hi." + DBHelper.HISTORY_FILE_NAME_COL + " IS NULL " +
            "ORDER BY hi." + DBHelper.HISTORY_TIMESTAMP_COL + " DESC";

    private static final String[] COUNT_COLUMN = {"COUNT(1)"};

    private static final String[] ID_COL_PROJECTION = {DBHelper.ID_COL};
    private static final String[] ID_HISTORY_ADDRESS_COL_PROJECTION = {DBHelper.ID_COL, DBHelper.HISTORY_ADDRESS_ID_COL};
    private static final String[] ID_HISTORY_AMOUNT_COL_PROJECTION = {DBHelper.ID_COL, DBHelper.HISTORY_AMOUNT_COL};
    private static final String[] ID_HISTORY_FILE_NAME_COL_PROJECTION = {DBHelper.ID_COL, DBHelper.HISTORY_FILE_NAME_COL};

    private static final String[] ID_ADDRESS_COUNT_PROJECTION = {"COUNT(" + DBHelper.ID_COL + ")"};

    private static final DateFormat EXPORT_DATE_TIME_FORMAT = DateFormat.getDateTimeInstance();

    private final Activity activity;

    public HistoryManager(Activity activity) {
        this.activity = activity;
    }

    public boolean hasHistoryItems() {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getReadableDatabase();
            cursor = db.query(DBHelper.HISTORY_TABLE_NAME,
                    COUNT_COLUMN,
                    null,
                    null,
                    null,
                    null,
                    null);
            cursor.moveToFirst();
            return cursor.getInt(0) > 0;
        } finally {
            close(cursor, db);
        }
    }

    public List<HistoryItem> buildHistoryItemsForDTA() {
        return buildHistoryItems(true);
    }

    public List<HistoryItem> buildAllHistoryItems() {
        return buildHistoryItems(false);
    }

    public List<HistoryItem> buildHistoryItems(boolean onlyUnexported) {
        SQLiteOpenHelper helper = new DBHelper(activity);
        List<HistoryItem> items = new ArrayList<HistoryItem>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getReadableDatabase();

            cursor = db.rawQuery(onlyUnexported ? BUILD_UNEXPORTED_ITEMS_QUERY : BUILD_ITEMS_QUERY,
                    null);

            while (cursor.moveToNext()) {
                String code_row = cursor.getString(0);
                long timestamp = cursor.getLong(1);
                int addressNumber = cursor.getInt(2);
                String amount = cursor.getString(3);
                String reason = cursor.getString(4);
                String dtaFile = cursor.getString(5);

                PsResult result = PsResult.getInstance(code_row, timestamp);

                if (result instanceof EsResult) {
                    ((EsResult) result).setReason(reason);
                }

                HistoryItem item = new HistoryItem(result, amount, addressNumber, dtaFile);

                if (addressNumber != -1) {
                    item.setAddress(cursor.getString(6));
                }
                items.add(item);
            }
        } finally {
            close(cursor, db);
        }
        return items;
    }

    public HistoryItem buildHistoryItem(int number) {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getReadableDatabase();
            cursor = db.rawQuery(BUILD_ITEMS_QUERY + " LIMIT " + number + ", 1", null);

            if (cursor.moveToNext()) {
                String text = cursor.getString(0);
                long timestamp = cursor.getLong(1);
                int addressId = cursor.getInt(2);
                String amount = cursor.getString(3);
                String reason = cursor.getString(4);
                String dtaFile = cursor.getString(5);

                PsResult result = PsResult.getInstance(text, timestamp);

                if (result instanceof EsResult) {
                    ((EsResult) result).setReason(reason);
                }

                HistoryItem item = new HistoryItem(result, amount, addressId, dtaFile);

                if (addressId != -1) {
                    item.setAddress(cursor.getString(6));
                }

                return item;
            } else {
                return null;
            }
        } finally {
            close(cursor, db);
        }
    }

    public void deleteHistoryItem(int number) {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getWritableDatabase();
            cursor = db.query(DBHelper.HISTORY_TABLE_NAME,
                    ID_COL_PROJECTION,
                    null,
                    null,
                    null,
                    null,
                    DBHelper.HISTORY_TIMESTAMP_COL + " DESC");

            if (cursor.move(number + 1)) {
                db.delete(DBHelper.HISTORY_TABLE_NAME,
                        DBHelper.ID_COL + '=' + cursor.getString(0),
                        null);
            }
        } finally {
            close(cursor, db);
        }
    }

    public HistoryItem addHistoryItem(PsResult result) {
        // Do not save this item to the history if the preference is turned off, or the contents are
        // considered secure.
        //    if (!activity.getIntent().getBooleanExtra(Intents.Scan.SAVE_HISTORY, true)) {
        //      return;
        //    }

        //	  SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        //	  if (!prefs.getBoolean(PreferencesActivity.KEY_REMEMBER_DUPLICATES, false)) {
        //		  deletePrevious(result.getText());
        //	  }
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getWritableDatabase();
            cursor = db.rawQuery(ITEM_WHERE_CODE_ROW_QUERY, new String[]{result.getCompleteCode()});
            if (cursor.moveToNext()) {

                int addressId = cursor.getInt(2);
                HistoryItem item = new HistoryItem(result,
                        cursor.getString(3),
                        addressId,
                        cursor.getString(4));

                if (addressId != -1) {
                    item.setAddress(cursor.getString(6));
                }

                ContentValues values = new ContentValues();
                values.put(DBHelper.HISTORY_TIMESTAMP_COL, result.getTimestamp());

                // Update timestamp
                db.update(DBHelper.HISTORY_TABLE_NAME,
                        values,
                        DBHelper.HISTORY_CODE_ROW_COL + "=?",
                        new String[]{result.getCompleteCode()});

                return item;
            } else {
                ContentValues values = new ContentValues();
                values.put(DBHelper.HISTORY_CODE_ROW_COL, result.getCompleteCode());
                values.put(DBHelper.HISTORY_TIMESTAMP_COL, result.getTimestamp());
                values.put(DBHelper.HISTORY_ADDRESS_ID_COL, -1);

                // Insert the new entry into the DB.
                db.insert(DBHelper.HISTORY_TABLE_NAME, DBHelper.HISTORY_TIMESTAMP_COL, values);

                return new HistoryItem(result);
            }
        } finally {
            close(null, db);
        }
    }

    public void updateHistoryItemAddressId(String code_row, int itemAddressId) {
        // As we're going to do an update only we don't need need to worry
        // about the preferences; if the item wasn't saved it won't be updated
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getWritableDatabase();
            cursor = db.query(DBHelper.HISTORY_TABLE_NAME,
                    ID_HISTORY_ADDRESS_COL_PROJECTION,
                    DBHelper.HISTORY_CODE_ROW_COL + "=?",
                    new String[]{code_row},
                    null,
                    null,
                    DBHelper.HISTORY_TIMESTAMP_COL + " DESC",
                    "1");
            String oldID = null;
            if (cursor.moveToNext()) {
                oldID = cursor.getString(0);

                ContentValues values = new ContentValues();
                values.put(DBHelper.HISTORY_ADDRESS_ID_COL, itemAddressId);

                db.update(DBHelper.HISTORY_TABLE_NAME,
                        values,
                        DBHelper.ID_COL + "=?",
                        new String[]{oldID});
            }

        } finally {
            close(cursor, db);
        }
    }

    public void updateHistoryItemAmount(String code_row, String itemAmount) {
        updateHistoryItem(ID_HISTORY_AMOUNT_COL_PROJECTION,
                DBHelper.HISTORY_AMOUNT_COL,
                code_row,
                itemAmount);
    }

    public void updateHistoryItemReason(String code_row, String itemReason) {
        updateHistoryItem(ID_HISTORY_AMOUNT_COL_PROJECTION,
                DBHelper.HISTORY_REASON_COL,
                code_row,
                itemReason);
    }

    public void updateHistoryItemFileName(String code_row, String itemFileName) {
        updateHistoryItem(ID_HISTORY_FILE_NAME_COL_PROJECTION,
                DBHelper.HISTORY_FILE_NAME_COL,
                code_row,
                itemFileName);
    }

    private void updateHistoryItem(String[] projection, String col_name, String code_row,
                                   String item) {
        // As we're going to do an update only we don't need to worry
        // about the preferences; if the item wasn't saved it won't be updated
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getWritableDatabase();
            cursor = db.query(DBHelper.HISTORY_TABLE_NAME,
                    projection,
                    DBHelper.HISTORY_CODE_ROW_COL + "=?",
                    new String[]{code_row},
                    null,
                    null,
                    DBHelper.HISTORY_TIMESTAMP_COL + " DESC",
                    "1");
            String oldID = null;
            if (cursor.moveToNext()) {
                oldID = cursor.getString(0);

                ContentValues values = new ContentValues();
                values.put(col_name, item);

                db.update(DBHelper.HISTORY_TABLE_NAME,
                        values,
                        DBHelper.ID_COL + "=?",
                        new String[]{oldID});
            }

        } finally {
            close(cursor, db);
        }
    }

    @SuppressWarnings("unused")
    private void deletePrevious(String text) {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        try {
            db = helper.getWritableDatabase();
            db.delete(DBHelper.HISTORY_TABLE_NAME,
                    DBHelper.HISTORY_CODE_ROW_COL + "=?",
                    new String[]{text});
        } finally {
            close(null, db);
        }
    }

    public void trimHistory() {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getWritableDatabase();
            cursor = db.query(DBHelper.HISTORY_TABLE_NAME,
                    ID_COL_PROJECTION,
                    null,
                    null,
                    null,
                    null,
                    DBHelper.HISTORY_TIMESTAMP_COL + " DESC");
            cursor.move(MAX_ITEMS);
            while (cursor.moveToNext()) {
                db.delete(DBHelper.HISTORY_TABLE_NAME,
                        DBHelper.ID_COL + '=' + cursor.getString(0),
                        null);
            }
        } finally {
            close(cursor, db);
        }
    }

    /**
     * <p>Builds a text representation of the scanning history. Each scan is encoded on one
     * line, terminated by a line break (\r\n). The values in each line are comma-separated,
     * and double-quoted. Double-quotes within values are escaped with a sequence of two
     * double-quotes. The fields output are:</p>
     * <p/>
     * <ul>
     * <li>Code row</li>
     * <li>Formatted version of timestamp</li>
     * <li>Address text</li>
     * <li>Paid timespamp</li>
     * </ul>
     */
    CharSequence buildHistory() {
        StringBuilder historyText = new StringBuilder(1000);
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getWritableDatabase();
            cursor = db.query(DBHelper.HISTORY_TABLE_NAME,
                    HISTORY_COLUMNS,
                    null,
                    null,
                    null,
                    null,
                    DBHelper.HISTORY_TIMESTAMP_COL + " DESC");

            while (cursor.moveToNext()) {

                String code_row = cursor.getString(0);
                long timestamp = cursor.getLong(1);
                PsResult result = PsResult.getInstance(code_row, timestamp);

                // Add timestamp, formatted
                historyText.append('"')
                        .append(messageHistoryField(EXPORT_DATE_TIME_FORMAT.format(new Date(
                                timestamp))))
                        .append("\",");

                historyText.append('"')
                        .append(messageHistoryField(result.toString()))
                        .append("\",");

                historyText.append('"')
                        .append(messageHistoryField(cursor.getString(2)).split("[\\r\\n]+")[0])
                        .append("\",");

                historyText.append('"')
                        .append(messageHistoryField(result.getCompleteCode()))
                        .append("\"\r\n");
            }
            return historyText;
        } finally {
            close(cursor, db);
        }
    }

    void clearHistory() {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        try {
            db = helper.getWritableDatabase();
            db.delete(DBHelper.HISTORY_TABLE_NAME, null, null);
        } finally {
            close(null, db);
        }
    }

    static Uri saveHistory(String history) {
        File bsRoot = new File(Environment.getExternalStorageDirectory(),
                CaptureActivity.EXTERNAL_STORAGE_DIRECTORY);
        File historyRoot = new File(bsRoot, "History");
        if (!historyRoot.exists() && !historyRoot.mkdirs()) {
            Log.w(TAG, "Couldn't make dir " + historyRoot);
            return null;
        }
        File historyFile = new File(historyRoot, "history-" + System.currentTimeMillis() + ".csv");
        OutputStreamWriter out = null;
        try {
            out = new OutputStreamWriter(new FileOutputStream(historyFile),
                    Charset.forName("UTF-8"));
            out.write(history);
            return Uri.parse("file://" + historyFile.getAbsolutePath());
        } catch (IOException ioe) {
            Log.w(TAG, "Couldn't access file " + historyFile + " due to " + ioe);
            return null;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ioe) {
                    // do nothing
                }
            }
        }
    }

    public void updateAddress(int addressId, String address) {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;

        try {
            db = helper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(DBHelper.ADDRESS_ADDRESS_COL, address);
            db.update(DBHelper.ADDRESS_TABLE_NAME, values, DBHelper.ID_COL + '=' + addressId, null);
        } finally {
            close(null, db);
        }
    }

    public int addAddress(String account, String address) {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        int number = 0;
        try {
            db = helper.getWritableDatabase();

            cursor = db.query(DBHelper.ADDRESS_TABLE_NAME,
                    ID_ADDRESS_COUNT_PROJECTION,
                    DBHelper.ADDRESS_ACCOUNT_COL + "=?",
                    new String[]{account},
                    null,
                    null,
                    DBHelper.ADDRESS_TIMESTAMP_COL);
            if (cursor.moveToNext()) {
                number = cursor.getInt(0);
            }

            ContentValues values = new ContentValues();
            values.put(DBHelper.ADDRESS_ACCOUNT_COL, account);
            values.put(DBHelper.ADDRESS_TIMESTAMP_COL, System.currentTimeMillis());
            values.put(DBHelper.ADDRESS_ADDRESS_COL, address);

            // Insert the new entry into the DB.
            db.insert(DBHelper.ADDRESS_TABLE_NAME, null, values);
        } finally {
            close(null, db);
        }

        return number;
    }

    public List<String> getAddresses(String account) {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        List<String> addresses = new ArrayList<String>();
        try {
            db = helper.getWritableDatabase();
            cursor = db.query(DBHelper.ADDRESS_TABLE_NAME,
                    ADDRESS_COLUMNS,
                    DBHelper.ADDRESS_ACCOUNT_COL + "=?",
                    new String[]{account},
                    null,
                    null,
                    DBHelper.ADDRESS_TIMESTAMP_COL);
            while (cursor.moveToNext()) {
                addresses.add(cursor.getString(2));
            }

            return addresses;
        } finally {
            close(null, db);
        }
    }

    private static String messageHistoryField(String value) {
        return value == null ? "" : value.replace("\"", "\"\"");
    }

    private static void close(Cursor cursor, SQLiteDatabase database) {
        if (cursor != null) {
            cursor.close();
        }
        if (database != null) {
            database.close();
        }
    }

    public String getAddress(int addressId) {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getWritableDatabase();
            cursor = db.query(DBHelper.ADDRESS_TABLE_NAME,
                    new String[]{DBHelper.ADDRESS_ADDRESS_COL},
                    DBHelper.ID_COL + " = ?",
                    new String[]{String.valueOf(addressId)},
                    null,
                    null,
                    DBHelper.ADDRESS_TIMESTAMP_COL);
            if (cursor.moveToNext()) {
                return cursor.getString(0);
            }

            return "";
        } finally {
            close(null, db);
        }
    }

    public int getAddressId(String account, int addressNumber) {
        SQLiteOpenHelper helper = new DBHelper(activity);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = helper.getWritableDatabase();
            cursor = db.query(DBHelper.ADDRESS_TABLE_NAME,
                    new String[]{DBHelper.ID_COL},
                    DBHelper.ADDRESS_ACCOUNT_COL + "=?",
                    new String[]{account},
                    null,
                    null,
                    DBHelper.ADDRESS_TIMESTAMP_COL);
            if (cursor.move(addressNumber + 1)) {
                return cursor.getInt(0);
            }

            return -1;
        } finally {
            close(null, db);
        }
    }

}