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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
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
import ch.luklanis.esscan.PreferencesActivity;
import ch.luklanis.esscan.paymentslip.EsIbanResult;
import ch.luklanis.esscan.paymentslip.PsResult;

/**
 * <p>Manages functionality related to scan history.</p>
 *
 * @author Sean Owen
 */
public final class HistoryManager {

    public static final int ITEM_ID_POSITION = 0;
    public static final int ITEM_CODE_ROW_POSITION = 1;
    public static final int ITEM_TIMESTAMP_POSITION = 2;
    public static final int ITEM_ADDRESS_ID_POSITION = 3;
    public static final int ITEM_AMOUNT_POSITION = 4;
    public static final int ITEM_REASON_POSITION = 5;
    public static final int ITEM_FILENAME_POSITION = 6;
    public static final int ITEM_BANK_ID_POSITION = 7;
    public static final int ITEM_BANK_PROFILE_POSITION = 8;
    public static final int ITEM_ADDRESS_POSITION = 9;

    private static final String ITEM_WHERE_ID_QUERY = "SELECT " +
            "hi." + DBHelper.ID_COL + ", " +
            "hi." + DBHelper.HISTORY_CODE_ROW_COL + ", " +
            "hi." + DBHelper.HISTORY_TIMESTAMP_COL + ", " +
            "hi." + DBHelper.HISTORY_ADDRESS_ID_COL + ", " +
            "hi." + DBHelper.HISTORY_AMOUNT_COL + ", " +
            "hi." + DBHelper.HISTORY_REASON_COL + ", " +
            "hi." + DBHelper.HISTORY_FILE_NAME_COL + ", " +
            "hi." + DBHelper.HISTORY_BANK_ID_COL + ", " +
            "bp." + DBHelper.ADDRESS_ADDRESS_COL + ", " +
            "ad." + DBHelper.ADDRESS_ADDRESS_COL + " " +
            "FROM " + DBHelper.HISTORY_TABLE_NAME + " AS hi " +
            "LEFT OUTER JOIN " + DBHelper.ADDRESS_TABLE_NAME + " AS bp " +
            "ON hi." + DBHelper.HISTORY_BANK_ID_COL + " = bp.id " +
            "LEFT OUTER JOIN " + DBHelper.ADDRESS_TABLE_NAME + " AS ad " +
            "ON hi." + DBHelper.HISTORY_ADDRESS_ID_COL + " = ad.id " +
            "WHERE hi." + DBHelper.ID_COL + " = ?";

    private static final String BUILD_ITEMS_QUERY = "SELECT " +
            "hi." + DBHelper.ID_COL + ", " +
            "hi." + DBHelper.HISTORY_CODE_ROW_COL + ", " +
            "hi." + DBHelper.HISTORY_TIMESTAMP_COL + ", " +
            "hi." + DBHelper.HISTORY_ADDRESS_ID_COL + ", " +
            "hi." + DBHelper.HISTORY_AMOUNT_COL + ", " +
            "hi." + DBHelper.HISTORY_REASON_COL + ", " +
            "hi." + DBHelper.HISTORY_FILE_NAME_COL + ", " +
            "hi." + DBHelper.HISTORY_BANK_ID_COL + ", " +
            "bp." + DBHelper.ADDRESS_ADDRESS_COL + ", " +
            "ad." + DBHelper.ADDRESS_ADDRESS_COL + " " +
            "FROM " + DBHelper.HISTORY_TABLE_NAME + " AS hi " +
            "LEFT OUTER JOIN " + DBHelper.ADDRESS_TABLE_NAME + " AS bp " +
            "ON hi." + DBHelper.HISTORY_BANK_ID_COL + " = bp.id " +
            "LEFT OUTER JOIN " + DBHelper.ADDRESS_TABLE_NAME + " AS ad " +
            "ON hi." + DBHelper.HISTORY_ADDRESS_ID_COL + " = ad.id " +
            "ORDER BY hi." + DBHelper.HISTORY_TIMESTAMP_COL + " DESC";

    private static final String BUILD_UNEXPORTED_ITEMS_QUERY = "SELECT " +
            "hi." + DBHelper.ID_COL + ", " +
            "hi." + DBHelper.HISTORY_CODE_ROW_COL + ", " +
            "hi." + DBHelper.HISTORY_TIMESTAMP_COL + ", " +
            "hi." + DBHelper.HISTORY_ADDRESS_ID_COL + ", " +
            "hi." + DBHelper.HISTORY_AMOUNT_COL + ", " +
            "hi." + DBHelper.HISTORY_REASON_COL + ", " +
            "hi." + DBHelper.HISTORY_FILE_NAME_COL + ", " +
            "hi." + DBHelper.HISTORY_BANK_ID_COL + ", " +
            "bp." + DBHelper.ADDRESS_ADDRESS_COL + ", " +
            "ad." + DBHelper.ADDRESS_ADDRESS_COL + " " +
            "FROM " + DBHelper.HISTORY_TABLE_NAME + " AS hi " +
            "LEFT OUTER JOIN " + DBHelper.ADDRESS_TABLE_NAME + " AS bp " +
            "ON hi." + DBHelper.HISTORY_BANK_ID_COL + " = bp.id " +
            "LEFT OUTER JOIN " + DBHelper.ADDRESS_TABLE_NAME + " AS ad " +
            "ON hi." + DBHelper.HISTORY_ADDRESS_ID_COL + " = ad.id " +
            "WHERE hi." + DBHelper.HISTORY_FILE_NAME_COL + " IS NULL " +
            "AND hi." + DBHelper.HISTORY_BANK_ID_COL + " = %d " +
            "ORDER BY hi." + DBHelper.HISTORY_TIMESTAMP_COL + " DESC";

    private static final String TAG = HistoryManager.class.getSimpleName();
    private static final int MAX_ITEMS = 500;
    private static final String[] HISTORY_COLUMNS = {DBHelper.ID_COL, DBHelper.HISTORY_CODE_ROW_COL, DBHelper.HISTORY_TIMESTAMP_COL, DBHelper.HISTORY_ADDRESS_ID_COL, DBHelper.HISTORY_AMOUNT_COL, DBHelper.HISTORY_REASON_COL, DBHelper.HISTORY_FILE_NAME_COL, DBHelper.HISTORY_BANK_ID_COL};
    private static final String[] ADDRESS_COLUMNS = {DBHelper.ADDRESS_ACCOUNT_COL, DBHelper.ADDRESS_TIMESTAMP_COL, DBHelper.ADDRESS_ADDRESS_COL};
    private static final String[] COUNT_COLUMN = {"COUNT(1)"};
    private static final String[] ID_COL_PROJECTION = {DBHelper.ID_COL};
    private static final String[] ID_HISTORY_BANK_COL_PROJECTION = {DBHelper.ID_COL, DBHelper.HISTORY_BANK_ID_COL};
    private static final String[] ID_HISTORY_ADDRESS_COL_PROJECTION = {DBHelper.ID_COL, DBHelper.HISTORY_ADDRESS_ID_COL};
    private static final String[] ID_HISTORY_AMOUNT_COL_PROJECTION = {DBHelper.ID_COL, DBHelper.HISTORY_AMOUNT_COL};
    private static final String[] ID_HISTORY_FILE_NAME_COL_PROJECTION = {DBHelper.ID_COL, DBHelper.HISTORY_FILE_NAME_COL};
    private static final String[] ID_ADDRESS_COUNT_PROJECTION = {"COUNT(" + DBHelper.ID_COL + ")"};
    private static final DateFormat EXPORT_DATE_TIME_FORMAT = DateFormat.getDateTimeInstance();

    private final Context mContext;
    private final SQLiteOpenHelper mHelper;

    public HistoryManager(Context context) {
        this.mContext = context;
        mHelper = new DBHelper(mContext);
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

    public boolean hasHistoryItems() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getReadableDatabase();
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

    public List<HistoryItem> buildHistoryItemsForDTA(long bankId) {
        return buildHistoryItems(bankId);
    }

    public List<HistoryItem> buildAllHistoryItems() {
        return buildHistoryItems(-2);
    }

    /**
     * Builds history items for list and DTA generation
     *
     * @param bankId -2: build over all items,
     *               -1: build for default bank profile,
     *               others: build for specific bank profile
     * @return
     */
    public List<HistoryItem> buildHistoryItems(long bankId) {
        List<HistoryItem> items = new ArrayList<HistoryItem>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getReadableDatabase();

            cursor = db.rawQuery(bankId == -2 ? BUILD_ITEMS_QUERY : String.format(
                    BUILD_UNEXPORTED_ITEMS_QUERY,
                    bankId), null);

            while (cursor.moveToNext()) {
                long itemId = cursor.getLong(ITEM_ID_POSITION);
                String code_row = cursor.getString(ITEM_CODE_ROW_POSITION);
                long timestamp = cursor.getLong(ITEM_TIMESTAMP_POSITION);
                long addressId = cursor.getLong(ITEM_ADDRESS_ID_POSITION);
                String amount = cursor.getString(ITEM_AMOUNT_POSITION);
                String reason = cursor.getString(ITEM_REASON_POSITION);
                String dtaFile = cursor.getString(ITEM_FILENAME_POSITION);
                long bankProfileId = cursor.getLong(ITEM_BANK_ID_POSITION);

                PsResult result = PsResult.getInstance(code_row, timestamp);

                if (result instanceof EsIbanResult) {
                    ((EsIbanResult) result).setReason(reason);
                }

                HistoryItem item = new HistoryItem.Builder().setItemId(itemId)
                        .setResult(result)
                        .setAmount(amount)
                        .setAddressId(addressId)
                        .setDtaFile(dtaFile)
                        .setBankProfileId(bankProfileId)
                        .create(cursor);

                items.add(item);
            }
        } finally {
            close(cursor, db);
        }
        return items;
    }

    public HistoryItem buildHistoryItem(int number) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getReadableDatabase();
            cursor = db.rawQuery(BUILD_ITEMS_QUERY + " LIMIT " + number + ", 1", null);

            if (cursor.moveToNext()) {
                int itemId = cursor.getInt(ITEM_ID_POSITION);
                String codeRow = cursor.getString(ITEM_CODE_ROW_POSITION);
                long timestamp = cursor.getLong(ITEM_TIMESTAMP_POSITION);
                int addressId = cursor.getInt(ITEM_ADDRESS_ID_POSITION);
                String amount = cursor.getString(ITEM_AMOUNT_POSITION);
                String reason = cursor.getString(ITEM_REASON_POSITION);
                String dtaFile = cursor.getString(ITEM_FILENAME_POSITION);
                int bankProfileId = cursor.getInt(ITEM_BANK_ID_POSITION);

                PsResult result = PsResult.getInstance(codeRow, timestamp);

                if (result instanceof EsIbanResult) {
                    ((EsIbanResult) result).setReason(reason);
                }

                HistoryItem item = new HistoryItem.Builder().setItemId(itemId)
                        .setResult(result)
                        .setAmount(amount)
                        .setAddressId(addressId)
                        .setDtaFile(dtaFile)
                        .setBankProfileId(bankProfileId)
                        .create(this);

                return item;
            } else {
                return null;
            }
        } finally {
            close(cursor, db);
        }
    }

    public void deleteHistoryItem(int number) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getWritableDatabase();
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
        //    if (!mContext.getIntent().getBooleanExtra(Intents.Scan.SAVE_HISTORY, true)) {
        //      return;
        //    }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int bankProfileNumber = Integer.valueOf(prefs.getString(PreferencesActivity.KEY_DEFAULT_BANK_PROFILE_NUMBER,
                "0"));
        long newBankProfileId = getBankProfileId(bankProfileNumber);
        //	  if (!prefs.getBoolean(PreferencesActivity.KEY_REMEMBER_DUPLICATES, false)) {
        //		  deletePrevious(result.getText());
        //	  }
        SQLiteDatabase db = null;
        try {
            db = mHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(DBHelper.HISTORY_CODE_ROW_COL, result.getCompleteCode());
            values.put(DBHelper.HISTORY_TIMESTAMP_COL, result.getTimestamp());
            values.put(DBHelper.HISTORY_ADDRESS_ID_COL, -1);
            values.put(DBHelper.HISTORY_BANK_ID_COL, newBankProfileId);

            // Insert the new entry into the DB.
            long itemId = db.insert(DBHelper.HISTORY_TABLE_NAME,
                    DBHelper.HISTORY_TIMESTAMP_COL,
                    values);

            return new HistoryItem.Builder().setItemId(itemId)
                    .setResult(result)
                    .setBankProfileId(newBankProfileId)
                    .create(this);
        } finally {
            close(null, db);
        }
    }

    public void updateHistoryItemAddressId(long itemId, long itemAddressId) {
        updateHistoryItemLong(ID_HISTORY_ADDRESS_COL_PROJECTION,
                DBHelper.HISTORY_ADDRESS_ID_COL,
                itemId,
                itemAddressId);
    }

    public void updateHistoryItemBankProfileId(long itemId, long bankProfileId) {
        updateHistoryItemLong(ID_HISTORY_BANK_COL_PROJECTION,
                DBHelper.HISTORY_BANK_ID_COL,
                itemId,
                bankProfileId);
    }

    public void updateHistoryItemLong(String[] projection, String column, long itemId,
                                      long itemAddressId) {
        // As we're going to do an updateDtaFilename only we don't need need to worry
        // about the preferences; if the item wasn't saved it won't be updated
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getWritableDatabase();
            cursor = db.query(DBHelper.HISTORY_TABLE_NAME,
                    projection,
                    DBHelper.ID_COL + "=?",
                    new String[]{String.valueOf(itemId)},
                    null,
                    null,
                    DBHelper.HISTORY_TIMESTAMP_COL + " DESC",
                    "1");
            String oldID = null;
            if (cursor.moveToNext()) {
                oldID = cursor.getString(0);

                ContentValues values = new ContentValues();
                values.put(column, itemAddressId);

                db.update(DBHelper.HISTORY_TABLE_NAME,
                        values,
                        DBHelper.ID_COL + "=?",
                        new String[]{oldID});
            }

        } finally {
            close(cursor, db);
        }
    }

    public void updateHistoryItemAmount(long itemId, String itemAmount) {
        updateHistoryItem(ID_HISTORY_AMOUNT_COL_PROJECTION,
                DBHelper.HISTORY_AMOUNT_COL,
                itemId,
                itemAmount);
    }

    public void updateHistoryItemReason(long itemId, String itemReason) {
        updateHistoryItem(ID_HISTORY_AMOUNT_COL_PROJECTION,
                DBHelper.HISTORY_REASON_COL,
                itemId,
                itemReason);
    }

    public void updateHistoryItemFileName(long itemId, String itemFileName) {
        updateHistoryItem(ID_HISTORY_FILE_NAME_COL_PROJECTION,
                DBHelper.HISTORY_FILE_NAME_COL,
                itemId,
                itemFileName);
    }

    private void updateHistoryItem(String[] projection, String col_name, long itemId, String item) {
        // As we're going to do an updateDtaFilename only we don't need to worry
        // about the preferences; if the item wasn't saved it won't be updated
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getWritableDatabase();
            cursor = db.query(DBHelper.HISTORY_TABLE_NAME,
                    projection,
                    DBHelper.ID_COL + "=?",
                    new String[]{String.valueOf(itemId)},
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
        SQLiteDatabase db = null;
        try {
            db = mHelper.getWritableDatabase();
            db.delete(DBHelper.HISTORY_TABLE_NAME,
                    DBHelper.HISTORY_CODE_ROW_COL + "=?",
                    new String[]{text});
        } finally {
            close(null, db);
        }
    }

    public void trimHistory() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getWritableDatabase();
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
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getReadableDatabase();
            cursor = db.query(DBHelper.HISTORY_TABLE_NAME,
                    HISTORY_COLUMNS,
                    null,
                    null,
                    null,
                    null,
                    DBHelper.HISTORY_TIMESTAMP_COL + " DESC");

            while (cursor.moveToNext()) {

                String code_row = cursor.getString(1);
                long timestamp = cursor.getLong(2);
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
                        .append(messageHistoryField(cursor.getString(3)).split("[\\r\\n]+")[0])
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
        SQLiteDatabase db = null;
        try {
            db = mHelper.getWritableDatabase();
            db.delete(DBHelper.HISTORY_TABLE_NAME, null, null);
        } finally {
            close(null, db);
        }
    }

    public void updateAddress(long addressId, String address) {
        SQLiteDatabase db = null;

        try {
            db = mHelper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(DBHelper.ADDRESS_ADDRESS_COL, address);
            db.update(DBHelper.ADDRESS_TABLE_NAME, values, DBHelper.ID_COL + '=' + addressId, null);
        } finally {
            close(null, db);
        }
    }

    public long addAddress(String account, String address) {
        SQLiteDatabase db = null;
        long addressId = 0;
        try {
            db = mHelper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(DBHelper.ADDRESS_ACCOUNT_COL, account);
            values.put(DBHelper.ADDRESS_TIMESTAMP_COL, System.currentTimeMillis());
            values.put(DBHelper.ADDRESS_ADDRESS_COL, address);

            // Insert the new entry into the DB.
            addressId = db.insert(DBHelper.ADDRESS_TABLE_NAME, null, values);
        } finally {
            close(null, db);
        }

        return addressId;
    }

    public List<String> getAddresses(String account) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        List<String> addresses = new ArrayList<String>();
        try {
            db = mHelper.getReadableDatabase();
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

    public String getAddress(long addressId) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getReadableDatabase();
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

    public BankProfile getBankProfile(long bankProfileId) {
        return new BankProfile(getAddress(bankProfileId));
    }

    public long addBankProfile(BankProfile bankProfile) {
//        StackTraceElement[] stacks = new Throwable().getStackTrace();
//        StringBuilder stringBuilder = new StringBuilder();
//
//        for (StackTraceElement element:stacks) {
//            stringBuilder.append(element.toString() + "\n");
//        }
//        Log.e(TAG, "created a bank profile: " + stringBuilder.toString());
        return addAddress("BP", bankProfile.toString());
    }

    public void updateBankProfile(long bankProfileId, BankProfile bankProfile) {
        updateAddress(bankProfileId, bankProfile.toString());
    }

    public long getAddressId(String account, int addressNumber) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = mHelper.getWritableDatabase();
            cursor = db.query(DBHelper.ADDRESS_TABLE_NAME,
                    new String[]{DBHelper.ID_COL},
                    DBHelper.ADDRESS_ACCOUNT_COL + "=?",
                    new String[]{account},
                    null,
                    null,
                    DBHelper.ADDRESS_TIMESTAMP_COL);
            if (cursor.move(addressNumber + 1)) {
                return cursor.getLong(0);
            }

            return -1;
        } finally {
            close(null, db);
        }
    }

    public List<BankProfile> getBankProfiles() {
        List<String> addresses = getAddresses("BP");
        List<BankProfile> banks = new ArrayList<BankProfile>();

        for (String bank : addresses) {
            banks.add(new BankProfile(bank));
        }

        return banks;
    }

    public long getBankProfileId(int bankNumber) {
        return getAddressId("BP", bankNumber);
    }

    public Cursor getCursorForHistoryItem(long itemId) {
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = mHelper.getReadableDatabase();
            cursor = db.rawQuery(ITEM_WHERE_ID_QUERY, new String[]{String.valueOf(itemId)});

            cursor.moveToFirst();
        } finally {
            if (db != null) {
                db.close();
            }
        }

        return cursor;
    }
}
