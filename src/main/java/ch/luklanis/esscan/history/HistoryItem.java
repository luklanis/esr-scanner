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

import android.app.Activity;
import android.text.TextUtils;

import ch.luklanis.esscan.paymentslip.EsrResult;
import ch.luklanis.esscan.paymentslip.PsResult;

public final class HistoryItem {

    private long itemId;
    private PsResult result;
    private long addressId;
    private String amount;
    private String dtaFile;
    private boolean exported;
    private String address;
    private long bankProfileId;
    private BankProfile bankProfile;

    public HistoryItem(PsResult result) {
        this.result = result;
        this.itemId = -1;
        this.addressId = -1;
        this.amount = "";
        this.dtaFile = null;
        this.exported = false;
        this.address = "";
        this.bankProfileId = BankProfile.INVALID_BANK_PROFILE_ID;
        this.bankProfile = null;
    }

    HistoryItem(long itemId, PsResult result, String amount, long addressId, String dtaFile,
                long bankProfileId) {
        this.itemId = itemId;
        this.result = result;
        this.addressId = addressId;
        this.amount = amount;
        this.dtaFile = dtaFile;
        this.exported = false;
        this.address = "";
        this.bankProfileId = bankProfileId;
        this.bankProfile = null;
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

    public boolean getExported() {
        return this.exported || this.dtaFile != null;
    }

    public void setExported(boolean exported) {
        this.exported = exported;
    }

    public String getDTAFilename() {
        return this.dtaFile;
    }

    public long getAddressId() {
        return addressId;
    }

    public void setAddressId(long addressId) {
        this.addressId = addressId;
    }

    public String getAddress() {
        return address;
    }

    public long getBankProfileId() {
        return bankProfileId;
    }

    public void setBankProfileId(long bankProfileId) {
        this.bankProfileId = bankProfileId;
    }

    public BankProfile getBankProfile() {
        return bankProfile;
    }

    public void setBankProfile(BankProfile bankProfile) {
        this.bankProfile = bankProfile;
    }

    public void setAddress(String address) {
        this.address = address == null ? "" : address;
    }

    public void update(HistoryItem item) {
        this.result = item.getResult();
        this.addressId = item.getAddressId();
        this.amount = item.getAmount();
        this.dtaFile = item.getDTAFilename();
        this.exported = item.getExported();
        this.address = item.getAddress();
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

    public class Builder {
        private PsResult result;
        private long itemId;
        private String amount;
        private long addressId;
        private String dtaFile;
        private long bankProfileId;
        private boolean exported;

        public Builder() {
            this.result = null;
            this.itemId = -1;
            this.addressId = -1;
            this.amount = "";
            this.dtaFile = null;
            this.exported = false;
            this.bankProfileId = BankProfile.INVALID_BANK_PROFILE_ID;
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

        public Builder setExported(boolean exported) {
            this.exported = exported;
            return this;
        }

        public Builder setBankProfileId(long bankProfileId) {
            this.bankProfileId = bankProfileId;
            return this;
        }

        public HistoryItem create(Activity activity) {
            HistoryManager historyManager = new HistoryManager(activity);
            HistoryItem historyItem = new HistoryItem(itemId,
                    result,
                    amount,
                    addressId,
                    dtaFile,
                    bankProfileId);
            historyItem.setExported(exported);
            historyItem.setAddress(historyManager.getAddress(historyItem.getAddressId()));
            historyItem.setBankProfile(historyManager.getBankProfile(historyItem.getBankProfileId()));

            return historyItem;
        }
    }
}
