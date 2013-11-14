/*
 * Copyright (C) 2008 ZXing authors
 * Copyright 2011 Robert Theis
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
package ch.luklanis.esscan;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ch.luklanis.esscan.dialogs.OkDialog;
import ch.luklanis.esscan.history.BankProfile;
import ch.luklanis.esscan.history.DBHelper;
import ch.luklanis.esscan.history.HistoryManager;
import ch.luklanis.esscan.paymentslip.DTAFileCreator;

/**
 * Class to handle preferences that are saved across sessions of the app. Shows
 * a hierarchy of preferences to the user, organized into sections. These
 * preferences are displayed in the options menu that is shown when the user
 * presses the MENU button.
 * <p/>
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
public class PreferencesActivity extends PreferenceActivity
        implements OnSharedPreferenceChangeListener {

    // Preference keys not carried over from ZXing project
    public static final String KEY_SOURCE_LANGUAGE_PREFERENCE = "preferences_source_language";
    public static final String KEY_ONLY_MACRO_FOCUS = "preferences_only_macro_focus";
    public static final String KEY_NO_CONTINUES_AUTO_FOCUS = "preferences_no_continous_auto_focus";
    public static final String KEY_ENABLE_TORCH = "preferences_enable_torch";
    public static final String KEY_ADDRESS = "preferences_address";
    public static final String KEY_EMAIL_ADDRESS = "preferences_email_address";
    public static final String KEY_BANK_PROFILE_NAME = "preferences_bank_profile_name";
    public static final String KEY_IBAN = "preferences_iban";
    public static final String KEY_EXECUTION_DAY = "preferences_execution_day";
    public static final String KEY_BANK_PROFILE_NAME_NEW = "preferences_bank_profile_name_new";
    public static final String KEY_IBAN_NEW = "preferences_iban_new";
    public static final String KEY_EXECUTION_DAY_NEW = "preferences_execution_day_new";
    public static final String KEY_DEFAULT_BANK_PROFILE_NUMBER = "preferences_default_bank_profile_number";
    public static final String KEY_BANK_PROFILE_EDIT = "preferences_bank_profile_edit";
    public static final String KEY_ONLY_COPY = "preferences_only_copy";
    public static final String KEY_COPY_PART = "preferences_copy_part";
    public static final String KEY_USERNAME = "preferences_username";
    public static final String KEY_PASSWORD = "preferences_password";
    // Preference keys carried over from ZXing project
    public static final String KEY_REVERSE_IMAGE = "preferences_reverse_image";
    public static final String KEY_PLAY_BEEP = "preferences_play_beep";
    public static final String KEY_VIBRATE = "preferences_vibrate";
    public static final String KEY_HELP_VERSION_SHOWN = "preferences_help_version_shown";
    public static final String KEY_SHOW_OCR_RESULT_PREFERENCE = "preferences_show_ocr_result";
    public static final String KEY_SHOW_SCAN_RESULT_PREFERENCE = "preferences_show_scan_result";
    public static final String KEY_NOT_SHOW_ALERT = "preferences_not_show_alertid_";
    public static final String KEY_ENABLE_STREAM_MODE = "preferences_enable_stream_mode";
    public static final String KEY_BUTTON_BACKUP = "preferences_button_backup";
    public static final String KEY_BUTTON_RESTORE = "preferences_button_restore";
    public static final String KEY_SERVER_PORT = "preferences_server_port";
    private static final String TAG = PreferencesActivity.class.getName();

    private static BankProfile.SaveBankProfileCallback sSaveBankProfileCallback;

    /**
     * Set the default preference values.
     *
     * @param savedInstanceState the current Activity's state, as passed by
     *                           Android
     */
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        // Hide Icon in ActionBar
//        getActionBar().setDisplayShowHomeEnabled(false);
//
//        addPreferencesFromResource(R.xml.preferences);
//    }

    /**
     * Interface definition for a callback to be invoked when a shared
     * preference is changed. Sets summary text for the app's preferences. Summary text values show the
     * current settings for the values.
     *
     * @param sharedPreferences the Android.content.SharedPreferences that received the change
     * @param key               the key of the preference that was changed, added, or removed
     */
    @SuppressLint("DefaultLocale")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        //    // Update preference summary values to show current preferences
        //    if(key.equals(KEY_SOURCE_LANGUAGE_PREFERENCE)) {
        //
        //      // Set the summary text for the source language name
        //      listPreferenceSourceLanguage.setSummary(LanguageCodeHelper.getOcrLanguageName(getBaseContext(), sharedPreferences.getString(key, "deu")));
        //
        //      // Retrieve the character whitelist for the new language
        //      String whitelist = OcrCharacterHelper.getWhitelist(sharedPreferences, listPreferenceSourceLanguage.getValue());
        //
        //      // Save the character whitelist to preferences
        //      sharedPreferences.edit().putString(KEY_CHARACTER_WHITELIST, whitelist).commit();
        //
        //      // Set the whitelist summary text
        //      editTextPreferenceCharacterWhitelist.setSummary(whitelist);
        //
        //    } else if (key.equals(KEY_CHARACTER_WHITELIST)) {
        //
        //      // Save a separate, language-specific character blacklist for this language
        //      OcrCharacterHelper.setWhitelist(sharedPreferences,
        //          listPreferenceSourceLanguage.getValue(),
        //          sharedPreferences.getString(key, OcrCharacterHelper.getDefaultWhitelist(listPreferenceSourceLanguage.getValue())));
        //
        //      // Set the summary text
        //      editTextPreferenceCharacterWhitelist.setSummary(sharedPreferences.getString(key, OcrCharacterHelper.getDefaultWhitelist(listPreferenceSourceLanguage.getValue())));

        if (key.equals(KEY_IBAN) || key.equals(KEY_IBAN_NEW)) {
            String iban = sharedPreferences.getString(key, "").toUpperCase();
            sharedPreferences.edit().putString(key, iban).commit();

            int warning = BankProfile.validateIBAN(iban);
            if (warning != 0) {
                new OkDialog(warning).show(getFragmentManager(), "OkAlert");
            }
        } else if (key.equals(KEY_ADDRESS)) {
            String address = sharedPreferences.getString(key, "");

            int warning = DTAFileCreator.validateAddress(address);
            if (warning != 0) {
                new OkDialog(warning).show(getFragmentManager(), "OkAlert");
            }
        }
    }

    private void reload() {
        startActivity(getIntent());
        finish();
    }

    /**
     * Sets up initial preference summary text
     * values and registers the OnSharedPreferenceChangeListener.
     */
    @Override
    protected void onResume() {
        super.onResume();

        setSaveBankProfileCallback(null);

        // Set up a listener whenever a key changes
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && sSaveBankProfileCallback != null) {
            int error = sSaveBankProfileCallback.save();

            if (error > 0) {
                new OkDialog(error).setOkClickListener(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                }).show(getFragmentManager(), "PreferenceActivity.onKeyDown");

                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Called when Activity is about to lose focus. Unregisters the
     * OnSharedPreferenceChangeListener.
     */
    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    /**
     * This fragment shows the preferences for the first header.
     */
    public static class StreamFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.fragmented_stream);
        }
    }

    public static void setSaveBankProfileCallback(
            BankProfile.SaveBankProfileCallback saveBankProfileCallback) {
        sSaveBankProfileCallback = saveBankProfileCallback;
    }

    /**
     * This fragment shows the preferences for the second header.
     */
    public static class DtaFormatFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Can retrieve arguments from headers XML.
            Log.i("args", "Arguments: " + getArguments());

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.fragmented_dta_format);
        }

        @Override
        public void onResume() {
            super.onResume();

            HistoryManager historyManager = new HistoryManager(getActivity());
            List<BankProfile> banks = historyManager.getBankProfiles();

            ListPreference defaultBankProfileList = (ListPreference) findPreference(
                    KEY_DEFAULT_BANK_PROFILE_NUMBER);
            PreferenceScreen editBankProfile = (PreferenceScreen) findPreference(
                    KEY_BANK_PROFILE_EDIT);

            if (banks.isEmpty()) {
                defaultBankProfileList.setEnabled(false);
                editBankProfile.setEnabled(false);
            } else {
                CharSequence[] entries = new CharSequence[banks.size()];
                CharSequence[] values = new CharSequence[banks.size()];
                for (int i = 0; i < banks.size(); i++) {
                    entries[i] = banks.get(i).getName();
                    values[i] = String.valueOf(i);
                }

                defaultBankProfileList.setEntries(entries);
                defaultBankProfileList.setEntryValues(values);

                defaultBankProfileList.setEnabled(true);
                editBankProfile.setEnabled(true);
            }
        }
    }

    /**
     * This fragment contains a second-level set of preference that you
     * can get to by tapping an item in the first preferences fragment.
     */
    public static class NewBankProfile extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Can retrieve arguments from preference XML.
            Log.i("args", "Arguments: " + getArguments());

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.fragmented_new_bank_profile);
        }

        @Override
        public void onResume() {
            super.onResume();

            setSaveBankProfileCallback(new BankProfile.SaveBankProfileCallback() {
                @Override
                public int save() {
                    EditTextPreference namePreference = (EditTextPreference) findPreference(
                            KEY_BANK_PROFILE_NAME_NEW);
                    String name = namePreference.getText();

                    EditTextPreference ibanPreference = (EditTextPreference) findPreference(
                            KEY_IBAN_NEW);
                    String iban = ibanPreference.getText();

                    ListPreference executionDayPreference = (ListPreference) findPreference(
                            KEY_EXECUTION_DAY_NEW);

                    HistoryManager historyManager = new HistoryManager(getActivity());

                    int msgNameIsEmpty = TextUtils.isEmpty(name) ? R.string.msg_bank_profile_needs_name : 0;
                    int error = msgNameIsEmpty == 0 ? BankProfile.validateIBAN(iban) : msgNameIsEmpty;

                    if (error > 0) {
                        return error;
                    }
                    historyManager.addBankProfile(new BankProfile(namePreference.getText(),
                            ibanPreference.getText(),
                            executionDayPreference.getValue()));

                    return 0;
                }
            });
        }
    }

    /**
     * This fragment contains a second-level set of preference that you
     * can get to by tapping an item in the first preferences fragment.
     */
    public static class EditBankProfile extends PreferenceFragment {
        private String mBankProfileNumber;
        private EditTextPreference mNamePreference;
        private EditTextPreference mIbanPreference;
        private ListPreference mExecutionDayPreference;
        private long mBankId;
        private HistoryManager mHistoryManager;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Can retrieve arguments from preference XML.
            Log.i("args", "Arguments: " + getArguments());

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.fragmented_edit_bank_profile);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                    getActivity());
            mBankProfileNumber = sharedPreferences.getString(KEY_DEFAULT_BANK_PROFILE_NUMBER, "0");
            mNamePreference = (EditTextPreference) findPreference(KEY_BANK_PROFILE_NAME);
            mIbanPreference = (EditTextPreference) findPreference(KEY_IBAN);
            mExecutionDayPreference = (ListPreference) findPreference(KEY_EXECUTION_DAY);
        }

        @Override
        public void onResume() {
            super.onResume();

            mHistoryManager = new HistoryManager(getActivity());

            mBankId = mHistoryManager.getBankProfileId(Integer.valueOf(mBankProfileNumber));
            BankProfile bankProfile = mHistoryManager.getBankProfile(mBankId);

            mNamePreference.setText(bankProfile.getName());
            mIbanPreference.setText(bankProfile.getIban(""));
            mExecutionDayPreference.setValue(String.valueOf(bankProfile.getExecutionDay(26)));

            setSaveBankProfileCallback(new BankProfile.SaveBankProfileCallback() {
                @Override
                public int save() {
                    String name = mNamePreference.getText();
                    String iban = mIbanPreference.getText();

                    int msgNameIsEmpty = TextUtils.isEmpty(name) ? R.string.msg_bank_profile_needs_name : 0;
                    int error = msgNameIsEmpty == 0 ? BankProfile.validateIBAN(iban) : msgNameIsEmpty;

                    if (error > 0) {
                        return error;
                    }

                    mHistoryManager.updateBankProfile(mBankId,
                            new BankProfile(name, iban, mExecutionDayPreference.getValue()));

                    return 0;
                }
            });
        }
    }

    /**
     * This fragment shows the preferences for the second header.
     */
    public static class AdvancedFragment extends PreferenceFragment
            implements OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Can retrieve arguments from headers XML.
            Log.i("args", "Arguments: " + getArguments());

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.fragmented_advanced);
        }

        @Override
        public void onResume() {
            super.onResume();

            // Set up a listener whenever a key changes
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();

            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(KEY_ONLY_MACRO_FOCUS)) {
                Preference continuesFocusPref = findPreference(KEY_NO_CONTINUES_AUTO_FOCUS);
                continuesFocusPref.setEnabled(!sharedPreferences.getBoolean(KEY_ONLY_MACRO_FOCUS,
                        false));
            }
        }
    }

    /**
     * This fragment shows the preferences for the second header.
     */
    public static class BackupFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Can retrieve arguments from headers XML.
            Log.i("args", "Arguments: " + getArguments());

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.fragmented_backup_restore);

            Preference backupButton = findPreference(KEY_BUTTON_BACKUP);
            backupButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    backupData();
                    return true;
                }
            });

            Preference restoreButton = findPreference(KEY_BUTTON_RESTORE);
            restoreButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    restoreData();
                    return true;
                }
            });
        }

        private void backupData() {
            FileChannel src = null;
            FileChannel dst = null;
            FileInputStream inputStream = null;
            FileOutputStream outputStream = null;
            ObjectOutputStream prefBackup = null;

            try {
                File sd = Environment.getExternalStorageDirectory();
                File data = Environment.getDataDirectory();

                if (sd.canWrite()) {
                    File bsRoot = new File(Environment.getExternalStorageDirectory(),
                            CaptureActivity.EXTERNAL_STORAGE_DIRECTORY);
                    if (!bsRoot.exists() && !bsRoot.mkdirs()) {
                        Log.w(TAG, "Couldn't make dir " + bsRoot);
                        return;
                    }

                    String currentDBPath = "data/" + getActivity().getPackageName() + "/databases/" + DBHelper.DB_NAME;
                    String backupDBPath = CaptureActivity.EXTERNAL_STORAGE_DIRECTORY + File.separator + DBHelper.DB_NAME;
                    String backupPrefsPath = CaptureActivity.EXTERNAL_STORAGE_DIRECTORY + "/preferences.xml";
                    File currentDB = new File(data, currentDBPath);
                    File backupDB = new File(sd, backupDBPath);
                    File backupPrefs = new File(sd, backupPrefsPath);

                    if (currentDB.exists()) {
                        inputStream = new FileInputStream(currentDB);
                        outputStream = new FileOutputStream(backupDB);

                        src = inputStream.getChannel();
                        dst = outputStream.getChannel();
                        dst.transferFrom(src, 0, src.size());

                        prefBackup = new ObjectOutputStream(new FileOutputStream(backupPrefs));
                        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(
                                getActivity());
                        prefBackup.writeObject(pref.getAll());

                        Toast toast = Toast.makeText(getActivity(),
                                getResources().getString(R.string.msg_database_saved),
                                Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.BOTTOM, 0, 0);
                        toast.show();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "restore failed", e);
            } finally {
                try {
                    if (prefBackup != null) {
                        prefBackup.close();
                    }
                } catch (IOException ex) {
                }

                try {
                    if (src != null) {
                        src.close();
                    }
                } catch (IOException ex) {
                }

                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException ex) {
                }

                try {
                    if (dst != null) {
                        dst.close();
                    }
                } catch (IOException ex) {
                }

                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (IOException ex) {
                }
            }
        }

        private void restoreData() {
            ObjectInputStream input = null;
            FileChannel src = null;
            FileChannel dst = null;
            FileInputStream inputStream = null;
            FileOutputStream outputStream = null;

            try {
                File sd = Environment.getExternalStorageDirectory();
                File data = Environment.getDataDirectory();

                String currentDBPath = "data/" + getActivity().getPackageName() + "/databases/" + DBHelper.DB_NAME;
                String backupDBPath = CaptureActivity.EXTERNAL_STORAGE_DIRECTORY + "/" + DBHelper.DB_NAME;
                String backupPrefsPath = CaptureActivity.EXTERNAL_STORAGE_DIRECTORY + "/preferences.xml";
                File currentDB = new File(data, currentDBPath);
                File backupDB = new File(sd, backupDBPath);
                File backupPrefs = new File(sd, backupPrefsPath);

                if (currentDB.canWrite() && backupDB.exists()) {
                    inputStream = new FileInputStream(backupDB);
                    outputStream = new FileOutputStream(currentDB);
                    src = inputStream.getChannel();
                    dst = outputStream.getChannel();
                    dst.transferFrom(src, 0, src.size());

                    input = new ObjectInputStream(new FileInputStream(backupPrefs));
                    Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(getActivity())
                            .edit();
                    prefEdit.clear();

                    @SuppressWarnings("unchecked") Map<String, ?> entries = (Map<String, ?>) input.readObject();
                    for (Entry<String, ?> entry : entries.entrySet()) {
                        Object v = entry.getValue();
                        String key = entry.getKey();

                        if (v instanceof Boolean)
                            prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
                        else if (v instanceof Float)
                            prefEdit.putFloat(key, ((Float) v).floatValue());
                        else if (v instanceof Integer)
                            prefEdit.putInt(key, ((Integer) v).intValue());
                        else if (v instanceof Long) prefEdit.putLong(key, ((Long) v).longValue());
                        else if (v instanceof String) prefEdit.putString(key, ((String) v));
                    }
                    prefEdit.commit();

                    Toast toast = Toast.makeText(getActivity(),
                            getResources().getString(R.string.msg_database_restored),
                            Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.BOTTOM, 0, 0);
                    toast.show();

                    getActivity().finish();
                }
            } catch (Exception e) {
                Log.w(TAG, "restore failed", e);
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                try {
                    if (src != null) {
                        src.close();
                    }
                } catch (IOException ex) {
                }

                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException ex) {
                }

                try {
                    if (dst != null) {
                        dst.close();
                    }
                } catch (IOException ex) {
                }

                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (IOException ex) {
                }
            }
        }
    }
}