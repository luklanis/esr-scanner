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

public abstract class PsValidation {
    private static final int STEP_COUNT = 1;

    protected static final char SEPERATOR = '|';

    protected static final int[][] MODULO10 = {{0, 9, 4, 6, 8, 2, 7, 1, 3, 5}, {9, 4, 6, 8, 2, 7, 1, 3, 5, 0}, {4, 6, 8, 2, 7, 1, 3, 5, 0, 9}, {6, 8, 2, 7, 1, 3, 5, 0, 9, 4}, {8, 2, 7, 1, 3, 5, 0, 9, 4, 6}, {2, 7, 1, 3, 5, 0, 9, 4, 6, 8}, {7, 1, 3, 5, 0, 9, 4, 6, 8, 2}, {1, 3, 5, 0, 9, 4, 6, 8, 2, 7}, {3, 5, 0, 9, 4, 6, 8, 2, 7, 1}, {5, 0, 9, 4, 6, 8, 2, 7, 1, 3}};

    protected static final int[] CHECK_DIGIT = {0, 9, 8, 7, 6, 5, 4, 3, 2, 1};

    protected int currentStep;
    protected int indexOfCurrentControlChar;
    protected int indexOfControlCharBefore;

    protected String relatedText;
    protected boolean finished;

    protected String[] completeCode;

    public PsValidation() {
        gotoBeginning(true);
    }

    protected int getCheckDigit(int[] digits) {
        int lastValue = 0;

        for (int digit : digits) {
            lastValue = MODULO10[lastValue][digit];
        }

        return CHECK_DIGIT[lastValue];
    }

    public int getStepCount() {
        return STEP_COUNT;
    }

    public int getCurrentStep() {
        return currentStep + 1;
    }

    public boolean nextStep() {
        indexOfControlCharBefore = indexOfCurrentControlChar;
        indexOfCurrentControlChar = 0;

        if (currentStep < getStepCount() - 1) {
            currentStep++;
            relatedText = null;
            return true;
        }

        finished = true;
        return false;
    }

    public void gotoBeginning(boolean reset) {
        currentStep = 0;
        finished = false;
        relatedText = null;
        resetCompleteCode();
    }

    public boolean finished() {
        return finished;
    }

    public boolean hasNonDigts(String text, int length) throws Exception {
        for (int i = 0; i < length; i++) {
            int digit = text.charAt(i) - '0';

            if (digit < 0 || digit > 9) {
                return true;
            }
        }

        return false;
    }

    public int[] getDigitsFromText(String text, int length) throws Exception {
        int[] digits = new int[length];

        for (int i = 0; i < digits.length; i++) {
            digits[i] = text.charAt(i) - '0';

            if (digits[i] < 0 || digits[i] > 9) {
                throw new Exception(String.format("%s is not a digit", digits[i]));
            }
        }

        return digits;
    }

    public String getCompleteCode() {
        String result = "";

        for (String codePart : completeCode) {
            if (codePart != null) {
                result += codePart;
            }
        }

        return result;
    }

    protected void resetStartSearchIndexes() {
        indexOfCurrentControlChar = 0;
        indexOfControlCharBefore = 0;
    }

    protected String preformatText(String text) {
        return SEPERATOR + text.replaceAll("\\+\\s", "+")
                .replaceAll("[\\s\\r\\n]+", String.valueOf(SEPERATOR));
    }

    protected void resetCompleteCode() {
        resetStartSearchIndexes();

        if (completeCode == null) {
            return;
        }

        for (int i = 0; i < completeCode.length; i++) {
            completeCode[i] = null;
        }
    }

    public abstract boolean validate(String text);

    public abstract String getCurrentRelatedText();

    public abstract String getNextRelatedText(String text);

    public abstract String getSpokenType();

    protected abstract boolean additionalStepTest(String related);
}
