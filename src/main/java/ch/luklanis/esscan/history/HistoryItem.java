/*
 * Copyright 2012 ZXing authors
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

import android.database.Cursor;
import android.text.TextUtils;

import ch.luklanis.esscan.paymentslip.EsrResult;
import ch.luklanis.esscan.paymentslip.PsResult;

public final class HistoryItem {

    private long itemId;
    private PsResult result;
    private long addressId;
    private String amount;
    private String dtaFile;
    private String address;
    private long bankProfileId;
    private BankProfile bankProfile;

    private HistoryItem(long itemId, PsResult result, String amount, long addressId, String address,
                        String dtaFile, long bankProfileId, BankProfile bankProfile) {
        this.itemId = itemId;
        this.result = result;
        this.addressId = addressId;
        this.amount = amount;
        this.dtaFile = dtaFile;
        this.address = address;
        this.bankProfileId = bankProfileId;
        this.bankProfile = bankProfile;
    }

    public PsResult getResult() {
        return result;
    }

//	public void setAddress(String address){
//		this.address = address;
//	}

    public String getAmount() {

        if (result.getType().equals(EsrResult.PS_TYPE_NAME)) {

            EsrResult esrResult = (EsrResult) result;

            if (!TextUtils.isEmpty(esrResult.getAmount())) {
                return esrResult.getAmount();
            }
        }

        return amount == null ? "" : amount;
    }

    public String getDTAFilename() {
        return this.dtaFile;
    }

    public long getAddressId() {
        return addressId;
    }

    public String getAddress() {
        return address;
    }

    public long getBankProfileId() {
        return bankProfileId;
    }

    public BankProfile getBankProfile() {
        return bankProfile;
    }

    public void update(HistoryItem item) {
        this.itemId = item.getItemId();
        this.result = item.getResult();
        this.addressId = item.getAddressId();
        this.amount = item.getAmount();
        this.dtaFile = item.getDTAFilename();
        this.address = item.getAddress();
        this.bankProfileId = item.getBankProfileId();
        this.bankProfile = item.getBankProfile();
    }

    @Override
    public String toString() {
        return address.replaceAll("[\\r\\n]+",
                ", ") + ", " + result.toString() + (!TextUtils.isEmpty(dtaFile) ? (", " + dtaFile) : "");
    }

    public long getItemId() {
        return itemId;
    }

    public void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public static class Builder {
        private String address;
        private BankProfile bankProfile;
        private PsResult result;
        private long itemId;
        private String amount;
        private long addressId;
        private String dtaFile;
        private long bankProfileId;

        public Builder() {
            this.itemId = -1;
            this.result = null;
            this.addressId = -1;
            this.address = "";
            this.amount = "";
            this.dtaFile = null;
            this.bankProfileId = BankProfile.INVALID_BANK_PROFILE_ID;
            this.bankProfile = null;
        }

        public Builder(HistoryItem historyItem) {
            this.itemId = historyItem.getItemId();
            this.result = historyItem.getResult();
            this.addressId = historyItem.getAddressId();
            this.address = historyItem.getAddress();
            this.amount = historyItem.getAmount();
            this.dtaFile = historyItem.getDTAFilename();
            this.bankProfileId = historyItem.getBankProfileId();
            this.bankProfile = historyItem.getBankProfile();
        }

        static public HistoryItem createEmptyInstance() {
            return new Builder().create((Cursor) null);
        }

        public Builder setResult(PsResult result) {
            this.result = result;
            return this;
        }

        public Builder setItemId(long itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder setAmount(String amount) {
            this.amount = amount;
            return this;
        }

        public Builder setAddressId(long addressId) {
            this.addressId = addressId;
            return this;
        }

        public Builder setDtaFile(String dtaFile) {
            this.dtaFile = dtaFile;
            return this;
        }

        public Builder setBankProfileId(long bankProfileId) {
            this.bankProfileId = bankProfileId;
            return this;
        }

        public HistoryItem create() {
            return create((Cursor) null);
        }

        public HistoryItem create(HistoryManager historyManager) {
            Cursor cursor = null;
            try {
                cursor = historyManager.getCursorForHistoryItem(this.itemId);
                return this.create(cursor);
            } finally {
                cursor.close();
            }
        }

        public HistoryItem create(Cursor cursor) {

            if (addressId != -1) {
                if (TextUtils.isEmpty(address)) {
                    if (cursor == null) {
                        throw new RuntimeException("Could not get address because cursor is null");
                    } else {
                        address = cursor.getString(HistoryManager.ITEM_ADDRESS_POSITION);
                    }
                }
            } else {
                address = "";
            }

            if (bankProfileId != BankProfile.INVALID_BANK_PROFILE_ID) {
                if (bankProfile == null) {
                    if (cursor == null) {
                        throw new RuntimeException(
                                "Could not get bank profile because cursor is null");
                    } else {
                        bankProfile = new BankProfile(cursor.getString(HistoryManager.ITEM_BANK_PROFILE_POSITION));
                    }
                }
            } else {
                bankProfile = null;
            }

            return new HistoryItem(itemId,
                    result,
                    amount,
                    addressId,
                    address,
                    dtaFile,
                    bankProfileId,
                    bankProfile);
        }
    }
}
