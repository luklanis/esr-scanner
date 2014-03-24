/*
 * Copyright 2012 Lukas Landis
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
package ch.luklanis.esscan.paymentslip;

import android.util.Log;

public class EsrValidation extends PsValidation {
    private static final String TAG = "ESR Validation";
    private static final int STEP_COUNT = 3;

    private static final char[] CONTROL_CHARS_IN_STEP = {'>', '+', '>'};

    private static final int[][] VALID_LENGTHS_IN_STEP = {{4, 14}, {28, 17}, {10, -1}};

    private static final String[] STEP_FORMAT = {"%s", "%s", " %s"};

    public EsrValidation() {
        completeCode = new String[STEP_COUNT];
    }

    @Override
    public int getStepCount() {
        return STEP_COUNT;
    }

    @Override
    public boolean validate(String text) {
        try {
            resetStartSearchIndexes();
            text = preformatText(text);
            // Log.d(TAG, String.format("text: %s", text));
            String related;
            while (!(related = getNextRelatedText(text)).isEmpty()) {
                // Log.d(TAG, String.format("related: %s", related));

                for (int validLength : VALID_LENGTHS_IN_STEP[currentStep]) {
                    if (validLength == -1 || related.length() < (validLength + 1)) {
                        continue;
                    }

                    if (related.charAt(related.length() - (validLength + 1)) != (currentStep > 0 ? CONTROL_CHARS_IN_STEP[currentStep - 1] : SEPERATOR)) {
                        continue;
                    }

                    String possiblePart = related.substring(related.length() - validLength,
                            related.length());

                    if (hasNonDigts(possiblePart, possiblePart.length() - 1)) {
                        continue;
                    }

                    int[] digits = getDigitsFromText(possiblePart, possiblePart.length() - 1);
                    int[] withoutCheckDigit = new int[digits.length - 1];

                    System.arraycopy(digits, 0, withoutCheckDigit, 0, withoutCheckDigit.length);

                    int checkDigit = getCheckDigit(withoutCheckDigit);

                    if (checkDigit == digits[digits.length - 1] && additionalStepTest(possiblePart)) {
                        completeCode[currentStep] = String.format(STEP_FORMAT[currentStep],
                                possiblePart);
                        return true;
                    }
                }
            }
        } catch (Exception exc) {
            Log.e(TAG, exc.toString());
            return false;
        }

        return false;
    }

    protected boolean additionalStepTest(String related) {
        if (currentStep == 0) {
            int esrType = Integer.parseInt(related.substring(0, 2));

            switch (esrType) {
                case 4:
                case 14:
                case 31:
                case 33:
                    return related.length() == 4;
                default:
                    return related.length() > 4;
            }
        }

        return true;
    }

    @Override
    public String getCurrentRelatedText() {
        return relatedText;
    }

    @Override
    public String getNextRelatedText(String text) {
        if (text == null || text == "") {
            return "";
        }

        relatedText = text;

//        if (currentStep > 0) {
//            indexOfControlCharBefore = relatedText.indexOf(String.valueOf(CONTROL_CHARS_IN_STEP[currentStep - 1]), indexOfControlCharBefore);
//
//            if (indexOfControlCharBefore != -1 && indexOfControlCharBefore < (relatedText.length() - 1)) {
//                relatedText = relatedText.substring(indexOfControlCharBefore + 1);
//            } else {
//                return "";
//            }
//        }

//        indexOfCurrentControlChar = relatedText.indexOf(String.valueOf(CONTROL_CHARS_IN_STEP[currentStep]), indexOfControlCharBefore + indexOfCurrentControlChar + 1);
        indexOfCurrentControlChar = relatedText.indexOf(String.valueOf(CONTROL_CHARS_IN_STEP[currentStep]),
                indexOfCurrentControlChar + 1);

        if (indexOfCurrentControlChar != -1) {
            relatedText = relatedText.substring(0, indexOfCurrentControlChar + 1);
        } else {
            return "";
        }

        return relatedText;
    }

    @Override
    public String getSpokenType() {
        return EsrResult.PS_TYPE_NAME;
    }
}
