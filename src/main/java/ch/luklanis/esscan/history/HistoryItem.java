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

import android.text.TextUtils;

import ch.luklanis.esscan.paymentslip.EsrResult;
import ch.luklanis.esscan.paymentslip.PsResult;

public final class HistoryItem {

    private PsResult result;
    private int addressId;
    private String amount;
    private String dtaFile;
    private boolean exported;
    private String address;
    private int bankProfileId;
    private BankProfile bankProfile;

    public HistoryItem(PsResult result) {
        this.result = result;
        this.addressId = -1;
        this.amount = "";
        this.dtaFile = null;
        this.exported = false;
        this.address = "";
        this.bankProfileId = BankProfile.DEFAULT_BANK_PROFILE_ID;
        this.bankProfile = null;
    }

    HistoryItem(PsResult result, String amount, int addressId, String dtaFile, int bankProfileId) {
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

    public int getAddressId() {
        return addressId;
    }

    public void setAddressId(int addressId) {
        this.addressId = addressId;
    }

    public String getAddress() {
        return address;
    }

    public int getBankProfileId() {
        return bankProfileId;
    }

    public void setBankProfileId(int bankId) {
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
}
