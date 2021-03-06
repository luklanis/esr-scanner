package ch.luklanis.esscan.paymentslip;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import ch.luklanis.esscan.CaptureActivity;
import ch.luklanis.esscan.PreferencesActivity;
import ch.luklanis.esscan.R;
import ch.luklanis.esscan.history.BankProfile;
import ch.luklanis.esscan.history.HistoryItem;

public class DTAFileCreator {

    private static final String TAG = DTAFileCreator.class.getName();
    private static final String NEWLINE_PATTERN = "[\\r\\n]+";
    private static final String SPACE_PATTERN = "\\s";
    private Context context;
    private String fileName;
    private File historyFile;
    private File historyRoot;

    public DTAFileCreator(Context context) {
        this.context = context;

        File bsRoot = new File(Environment.getExternalStorageDirectory(),
                CaptureActivity.EXTERNAL_STORAGE_DIRECTORY);

        historyRoot = new File(bsRoot, "DTA");
        fileName = "DTA-" + System.currentTimeMillis() + ".txt";
        historyFile = new File(historyRoot, fileName);
    }

    public Uri getDTAFileUri() {
        return Uri.fromFile(historyFile);
    }

    /**
     * <p>
     * Builds a text representation of the scanning history. Each scan is
     * encoded on one line, terminated by a line break (\r\n). The values in
     * each line are comma-separated, and double-quoted. Double-quotes within
     * values are escaped with a sequence of two double-quotes. The fields
     * output are:
     * </p>
     * <p/>
     * <ul>
     * <li>Code row</li>
     * <li>Formatted version of timestamp</li>
     * <li>Address text</li>
     * <li>Paid timespamp</li>
     * </ul>
     */
    public CharSequence buildDTA(BankProfile bankProfile, List<HistoryItem> historyItems) {
        StringBuilder dtaText = new StringBuilder(1000);

        String today = getDateFormated(new Date(System.currentTimeMillis()));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String iban = bankProfile.getIban("").replaceAll(SPACE_PATTERN, "");

        String[] ownAddress = prefs.getString(PreferencesActivity.KEY_ADDRESS, "")
                .split(NEWLINE_PATTERN);

        float totalAmount = 0;

        if (iban == "") {
            return "";
        }

        String ownClearing = String.valueOf((Integer.parseInt(iban.substring(4, 9))));

        List<HistoryItem> filteredHistoryItem = new ArrayList<HistoryItem>();

        for (HistoryItem historyItem : historyItems) {

            if (historyItem.getResult().getType().equals(EsrResult.PS_TYPE_NAME)) {

                EsrResult result = new EsrResult(historyItem.getResult().getCompleteCode());

                if (!result.getCurrency().equals("CHF")) {
                    continue;
                }
            }

            filteredHistoryItem.add(historyItem);
        }

        for (int i = 0; i < filteredHistoryItem.size(); i++) {

            PsResult psResult = null;
            HistoryItem historyItem = filteredHistoryItem.get(i);

            String referenzNumber;
            String[] reason = null;
            String account = "";
            String clearing = "";

            if (filteredHistoryItem.get(i).getResult().getType().equals(EsrResult.PS_TYPE_NAME)) {
                EsrResult esrResult = (EsrResult) filteredHistoryItem.get(i).getResult();
                referenzNumber = esrResult.getReference().replaceAll(SPACE_PATTERN, "");
                psResult = esrResult;
                reason = new String[0];
                account = esrResult.getAccountUnformated();
            } else {
                EsIbanResult esIbanResult = (EsIbanResult) filteredHistoryItem.get(i).getResult();
                referenzNumber = esIbanResult.getReference();
                clearing = esIbanResult.getClearing();
                psResult = esIbanResult;

                String reasonInOneLine = esIbanResult.getReason();
                reason = reasonInOneLine != null ? reasonInOneLine.split(NEWLINE_PATTERN) : new String[0];
            }

            String addressLine = historyItem.getAddress();
            String[] address = addressLine != null ? addressLine.split(NEWLINE_PATTERN) : new String[0];

            CharSequence paddedSequenz = padded(String.valueOf(i + 1), '0', 5, false);

            String amount = filteredHistoryItem.get(i).getAmount();

            totalAmount += Float.parseFloat(amount);

            // HEADER for ESR/ES
            dtaText.append("01") // Segment number
                    .append(getExecutionDateFormated(bankProfile.getExecutionDay(26))) // desired execution
                            // date
                    .append(spacePaddedEnd(clearing, 12)) // Clearing number of the
                            // target bank (on ESR = ""; on ES = "07...")
                    .append(padded("", '0', 5, true)) // Sequenz number (has to
                            // be 5 x 0)
                    .append(nullToEmpty(today)) // creation date
                    .append(spacePaddedEnd(ownClearing, 7)) // own clearing number
                    .append(padded("", 'X', 5, true)) // identification number
                    .append(paddedSequenz); // sequenz number

            // transaction type (ESR = 826/ES = 827), payment
            // type (ESR = 0) and a flag (always 0)
            if (psResult.getType().equals(EsrResult.PS_TYPE_NAME)) {
                dtaText.append("82600");
            } else {
                dtaText.append("82700");
            }

            // ESR/ES
            dtaText.append(padded("", 'X', 5, true))
                    // identification number (again)
                    .append("WZ0000")
                            // transaction number part 1
                    .append(paddedSequenz)
                            // transaction number part 2
                    .append(spacePaddedEnd(iban, 24))
                            // own IBAN
                    .append(spacePaddedEnd("", 6))
                            // Valuta (Blanks in ESR and ES)
                    .append(psResult.getCurrency())
                    .append(spacePaddedEnd(amount.replace('.', ','), 12)).append(spacePaddedEnd("",
                    14))
                    // Reserve
                    .append("02")
                            // Begin Segment 02
                    .append(spacePaddedEnd(ownAddress[0], 20))
                    .append(spacePaddedEnd(ownAddress[1], 20));

            String ownAddressTemp = "";
            if (ownAddress.length > 2) {
                ownAddressTemp += spacePaddedEnd(ownAddress[2], 20);
            }
            if (ownAddress.length > 3) {
                ownAddressTemp += spacePaddedEnd(ownAddress[3], 20);
            }

            dtaText.append(spacePaddedEnd(ownAddressTemp, 40))
                    .append(spacePaddedEnd("", 46)); // Reserve

            dtaText.append("03") // Begin Segment 03
                    .append("/C/"); // Account begin

            if (psResult.getType().equals(EsrResult.PS_TYPE_NAME)) {
                dtaText.append(padded(account, '0', 9, false)); // Account
            } else {
                dtaText.append(padded(referenzNumber, '0', 27, false));
            }

            String addressTemp = "";
            int lineLength = psResult.getMaxAddressLength();
            if (address.length > 0) {
                addressTemp += spacePaddedEnd(address[0], lineLength);
            }
            if (address.length > 1) {
                addressTemp += spacePaddedEnd(address[1], lineLength);
            }
            if (address.length > 2) {
                addressTemp += spacePaddedEnd(address[2], lineLength);
            }
            if (address.length > 3) {
                addressTemp += spacePaddedEnd(address[3], lineLength);
            }

            dtaText.append(spacePaddedEnd(addressTemp, 4 * lineLength));

            if (psResult.getType().equals(EsrResult.PS_TYPE_NAME)) {
                // ESR only (has a referenz number)

                if (account.length() > 5) {
                    dtaText.append(padded(referenzNumber, '0', 27, false)); // Referenz
                    // number
                } else {
                    dtaText.append(spacePaddedEnd(referenzNumber, 27)); // Refernz
                    // number
                    // (with
                    // 5
                    // digits
                    // account)
                    Log.w(TAG, "account only 5 digits long -> this will not work!");
                }

                dtaText.append("  ") // ESR Checksum (only with 5 digits, which
                        // is
                        // not supported)
                        .append(spacePaddedEnd("", 5)); // Reserve
            } else {
                // ES only has a segment 04 with a payment reason as text
                // 05 seems to be not needed for iban payments
                dtaText.append("04"); // Begin Segment 04

                String reasonTemp = "";
                lineLength = 28;
                if (reason.length > 0) {
                    reasonTemp += spacePaddedEnd(reason[0], lineLength);
                }
                if (reason.length > 1) {
                    reasonTemp += spacePaddedEnd(reason[1], lineLength);
                }
                if (reason.length > 2) {
                    reasonTemp += spacePaddedEnd(reason[2], lineLength);
                }
                if (reason.length > 3) {
                    reasonTemp += spacePaddedEnd(reason[3], lineLength);
                }

                dtaText.append(spacePaddedEnd(reasonTemp, 126));
            }

            HistoryItem item = filteredHistoryItem.get(i);
            filteredHistoryItem.get(i)
                    .update(new HistoryItem.Builder(item).setDtaFile("exported").create());
        }

        // HEADER for Total Record
        dtaText.append("01") // Segment number
                .append(padded("", '0', 6, true)) // desired execution date (not
                        // set in Total Record)
                .append(spacePaddedEnd("", 12)) // Clearing number of the target
                        // bank (not needed for ESR
                        // payments)
                .append(padded("", '0', 5, true)) // Sequenz number (has to be 5
                        // x 0)
                .append(nullToEmpty(today)) // creation date
                .append(spacePaddedEnd("", 7)) // own clearing number (not set
                        // in Total Record)
                .append(padded("", 'X', 5, true))
                        // identification number
                .append(padded(String.valueOf(filteredHistoryItem.size() + 1),
                        '0',
                        5,
                        false)) // sequenz number
                .append("89000"); // transaction type (Total Record = 890),
        // payment type (ESR = 0) and a flag (always
        // 0)

        String[] totalAmountSplit = String.valueOf(totalAmount).split("\\.");

        String totalAmountTemp = totalAmountSplit[0] + "," + padded(totalAmountSplit[1],
                '0',
                3,
                true);

        // Total Record
        dtaText.append(spacePaddedEnd(totalAmountTemp, 16))
                .append(spacePaddedEnd("", 59)); // Reserve

        return dtaText;
    }

    public boolean saveDTAFile(String dta) {
        if (!historyRoot.exists() && !historyRoot.mkdirs()) {
            Log.w(TAG, "Couldn't make dir " + historyRoot);
            return false;
        }

        OutputStreamWriter out = null;
        try {
            out = new OutputStreamWriter(new FileOutputStream(historyFile),
                    Charset.forName("ISO-8859-1"));
            out.write(dta);
            return true;
        } catch (IOException ioe) {
            Log.w(TAG, "Couldn't access file " + historyFile + " due to " + ioe);
            return false;
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

    public static int validateAddress(String address, PsResult psResult) {
        return validateAddress(address,
                psResult.getMaxAddressLength(),
                psResult.getType()
                        .equals(EsrResult.PS_TYPE_NAME) ? R.string.msg_address_line_to_long_20 : R.string.msg_address_line_to_long_24
        );
    }

    public static int validateAddress(String address) {
        return validateAddress(address, 20, R.string.msg_address_line_to_long_20);
    }

    public static int validateAddress(String address, int maxCharsPerLine, int errorToReturn) {
        if (address.length() > 0) {
            String[] lines = address.split("[\\r\\n]+");

            boolean error = false;

            if (lines.length > 4) {
                error = true;
            } else {
                for (String line : lines) {
                    if (line.length() > maxCharsPerLine) {
                        error = true;
                    }
                }
            }

            if (error) {
                return errorToReturn;
            }
        }

        return 0;
    }

    public static int validateReason(String reason) {
        if (reason.length() > 0) {
            String[] lines = reason.split("[\\r\\n]+");

            if (lines.length > 4) {
                return R.string.msg_reason_line_to_long;
            } else {
                for (String line : lines) {
                    if (line.length() > 20) {
                        return R.string.msg_reason_line_to_long;
                    }
                }
            }
        }

        return 0;
    }


    public int getFirstErrorId() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String[] ownAddress = prefs.getString(PreferencesActivity.KEY_ADDRESS, "")
                .split(NEWLINE_PATTERN);

        if (ownAddress.length < 2) {
            return R.string.msg_own_address_is_not_set;
        }

        return 0;
    }

    public String getFirstError(BankProfile bankProfile, List<HistoryItem> historyItems) {
        int error = getFirstErrorId();

        Resources res = context.getResources();

        if (error != 0) {
            return res.getString(error);
        }

        String iban = bankProfile.getIban("").replaceAll("\\s", "");

        if (TextUtils.isEmpty(iban)) {
            return res.getString(R.string.msg_own_iban_is_not_set);
        }

        error = BankProfile.validateIBAN(iban);
        if (error != 0) {
            return res.getString(error);
        }

        if (historyItems != null) {
            List<HistoryItem> items = new ArrayList<HistoryItem>();

            boolean nothingToExport = true;

            for (HistoryItem historyItem : historyItems) {

                PsResult result = PsResult.getInstance(historyItem.getResult().getCompleteCode());

                if (result.getCurrency() == "CHF") {
                    items.add(historyItem);
                }

                if (historyItem.getDTAFilename() == null) {
                    nothingToExport = false;
                }
            }

            if (nothingToExport) {
                return res.getString(R.string.msg_nothing_to_export);
            }

            for (HistoryItem item : items) {
                if (nullToEmpty(item.getAmount()).equals("") || Float.parseFloat(item.getAmount()) == 0.00) {
                    return res.getString(R.string.msg_amount_is_empty,
                            item.getResult().getAccount());
                }
            }
        }

        return "";
    }

    private static String getDateFormated(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
        return sdf.format(date);
    }

    private String getExecutionDateFormated(int day) {
        Date now = new Date(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");

        Calendar nowCalendar = Calendar.getInstance();
        nowCalendar.setTime(now);

        Calendar expectedCalendar = Calendar.getInstance();
        expectedCalendar.setTime(now);
        expectedCalendar.set(Calendar.DAY_OF_MONTH, day);

        if (!expectedCalendar.after(nowCalendar)) {
            // int month = expectedCalendar.get(Calendar.MONTH);
            //
            // if(month < Calendar.DECEMBER){
            // expectedCalendar.set(Calendar.MONTH, month + 1);
            // }
            // else{
            // expectedCalendar.set(Calendar.MONTH, Calendar.JANUARY);
            // expectedCalendar.set(Calendar.YEAR,
            // (expectedCalendar.get(Calendar.YEAR) + 1));
            // }
            expectedCalendar.add(Calendar.MONTH, 1);
        }

        int dayOfWeek = expectedCalendar.get(Calendar.DAY_OF_WEEK);

        switch (dayOfWeek) {
            case Calendar.SATURDAY:
                expectedCalendar.add(Calendar.DAY_OF_WEEK, 2);
                break;
            case Calendar.SUNDAY:
                expectedCalendar.add(Calendar.DAY_OF_WEEK, 1);
                break;
            default:
                break;
        }

        return sdf.format(expectedCalendar.getTime());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static CharSequence spacePaddedEnd(String text, int length) {
        return padded(text, ' ', length, true);
    }

    private static CharSequence padded(String text, char pad, int length, boolean padEnd) {
        if (text.length() > length) {
            return text.subSequence(0, length);
        }

        StringBuilder paddedText = new StringBuilder(length);

        if (padEnd) {
            paddedText.append(text);
        }

        for (int i = 0; i < length - text.length(); i++) {
            paddedText.append(pad);
        }

        if (!padEnd) {
            paddedText.append(text);
        }

        return paddedText;
    }
}
