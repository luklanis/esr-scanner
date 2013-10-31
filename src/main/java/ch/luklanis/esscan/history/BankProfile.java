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

public class BankProfile {

    private String name;
    private String iban;
    private int execution;

    public BankProfile(String jsonBankProfile) {
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(jsonBankProfile);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        name = "Default";
        if (jsonObject.has("name")) {
            try {
                this.name = jsonObject.getString("name");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        iban = null;
        if (jsonObject.has("iban")) {
            try {
                this.iban = jsonObject.getString("iban");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        execution = -1;
        if (jsonObject.has("execution")) {
            try {
                this.execution = jsonObject.getInt("execution");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public String getName() {
        return name;
    }

    public String getIban(String ifNotSet) {
        return iban == null ? ifNotSet : iban;
    }

    public int getExecution(int ifNotSet) {
        return execution == -1 ? ifNotSet : execution;
    }

    @Override
    public String toString() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            jsonObject.put("iban", iban);
            jsonObject.put("execution", execution);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonObject.toString();
    }
}
