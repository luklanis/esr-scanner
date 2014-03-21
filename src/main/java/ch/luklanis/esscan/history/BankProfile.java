package ch.luklanis.esscan.history;/*
 * Copyright 2013 Lukas Landis
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

import org.json.JSONException;
import org.json.JSONObject;

import ch.luklanis.esscan.R;

public class BankProfile {

    public static final int INVALID_BANK_PROFILE_ID = -1;
    private String name;
    private String iban;
    private int executionDay;

    public BankProfile() {
        this(null);
    }

    public BankProfile(String jsonBankProfile) {

        name = "Default";
        iban = null;
        executionDay = 26;

        if (jsonBankProfile == null) {
            return;
        }

        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(jsonBankProfile);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        if (jsonObject.has("name")) {
            try {
                this.name = jsonObject.getString("name");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (jsonObject.has("iban")) {
            try {
                this.iban = jsonObject.getString("iban");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (jsonObject.has("execDay")) {
            try {
                this.executionDay = jsonObject.getInt("execDay");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public BankProfile(String name, String iban, String executionDay) {
        this.name = name;
        this.iban = iban;
        this.executionDay = Integer.parseInt(executionDay);
    }

    @Override
    public String toString() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            jsonObject.put("iban", iban);
            jsonObject.put("execDay", executionDay);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonObject.toString();
    }

    public String getName() {
        return name;
    }

    public String getIban(String ifNotSet) {
        return iban == null ? ifNotSet : iban;
    }

    public int getExecutionDay(int ifNotSet) {
        return executionDay == 26 ? ifNotSet : executionDay;
    }

    public static int validateIBAN(String iban) {
        iban = iban.replaceAll("[\\s\\r\\n]+", "");

        if (iban.equals("")) {
            return R.string.msg_own_iban_is_not_valid;
        }

        if (iban.length() != 21) {
            return R.string.msg_own_iban_is_not_valid;
        }

        iban = iban.substring(4, 21) + iban.substring(0, 4);

        StringBuilder ibanNumber = new StringBuilder(1000);

        for (int i = 0; i < iban.length(); i++) {
            char ibanChar = iban.charAt(i);

            if (ibanChar < '0' || ibanChar > '9') {
                int ibanLetter = 10 + (ibanChar - 'A');

                if (ibanLetter < 10 || ibanLetter > (('Z' - 'A') + 10)) {
                    return R.string.msg_own_iban_is_not_valid;
                }

                ibanNumber.append(ibanLetter);
            } else {
                ibanNumber.append(ibanChar);
            }
        }

        int lastEnd = 0;
        int subIbanLength = 9;
        int subIbanLengthWithModulo = subIbanLength - 2;
        int modulo97 = 97;

        int subIban = Integer.parseInt(ibanNumber.substring(lastEnd, subIbanLength));
        int lastModulo = subIban % modulo97;
        lastEnd = subIbanLength;

        try {
            while (lastEnd < ibanNumber.length()) {
                if ((lastEnd + subIbanLengthWithModulo) < ibanNumber.length()) {
                    int newEnd = lastEnd + subIbanLengthWithModulo;
                    subIban = Integer.parseInt(String.format("%s%s",
                            lastModulo,
                            ibanNumber.substring(lastEnd, newEnd)));
                    lastEnd = newEnd;
                } else {
                    subIban = Integer.parseInt(String.format("%s%s",
                            lastModulo,
                            ibanNumber.substring(lastEnd)));
                    lastEnd = ibanNumber.length();
                }

                lastModulo = subIban % modulo97;
            }
        } catch (NumberFormatException ex) {
            return R.string.msg_own_iban_is_not_valid;
        }

        if (lastModulo != 1) {
            return R.string.msg_own_iban_is_not_valid;
        }

        return 0;
    }

    public interface SaveBankProfileCallback {
        public int save();
    }
}
