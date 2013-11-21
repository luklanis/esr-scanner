package ch.luklanis.esscan.test;/*
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

import android.test.AndroidTestCase;

import ch.luklanis.esscan.history.BankProfile;
import ch.luklanis.esscan.history.HistoryItem;
import ch.luklanis.esscan.history.HistoryManager;
import ch.luklanis.esscan.paymentslip.PsResult;

public class HistoryTest extends AndroidTestCase {
    private static final String ESR_CODE_ROW = "1100003949754>210000000003139471430009017+ 010001628>";
    private static final String VALID_IBAN = "CH93 0076 2011 6238 5295 7";

    public void testAddAndUseItemWithoutBankProfile() {
        PsResult psResult = PsResult.getInstance(ESR_CODE_ROW);

        HistoryManager historyManager = new HistoryManager(getContext());

        HistoryItem item = historyManager.addHistoryItem(psResult);

        assertTrue("Should have a valid id", item.getItemId() != 0 && item.getItemId() != -1);

        assertEquals(ESR_CODE_ROW, item.getResult().getCompleteCode());
    }

    public void testAddAndUseBankProfile() {
        HistoryManager historyManager = new HistoryManager(getContext());
        HistoryItem item = historyManager.addHistoryItem(PsResult.getInstance(ESR_CODE_ROW));

        assertEquals(BankProfile.INVALID_BANK_PROFILE_ID, item.getBankProfileId());
        assertNull(item.getBankProfile());

        long bankProfileId = historyManager.addBankProfile(new BankProfile("Test",
                VALID_IBAN,
                "26"));

        historyManager.updateHistoryItemBankProfileId(item.getItemId(), bankProfileId);

        item.update(new HistoryItem.Builder(item).setBankProfileId(bankProfileId).create());

        assertEquals(item.getBankProfile().getName(), "Test");
        assertEquals(item.getBankProfile().getIban(""), VALID_IBAN);
        assertEquals(26, item.getBankProfile().getExecutionDay(26));
    }
}
