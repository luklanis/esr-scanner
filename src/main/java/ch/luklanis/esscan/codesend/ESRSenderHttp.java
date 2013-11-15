package ch.luklanis.esscan.codesend;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import ch.luklanis.esscan.R;

/*
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
public class ESRSenderHttp implements IEsrSender {

    private final String url = "http://esr-relay.herokuapp.com";
    private final String password;
    private final String emailAddress;
    private final String hash;
    private final Context context;
    private Handler mDataSentHandler;

    public ESRSenderHttp(Context context, String emailAddress, String password)
            throws NoSuchAlgorithmException, NoSuchProviderException, UnsupportedEncodingException {
        this.context = context;
        this.password = password;
        this.emailAddress = emailAddress;
        hash = Crypto.getHash(password, emailAddress);
    }

    public void registerDataSentHandler(Handler dataSentCallback) {
        mDataSentHandler = dataSentCallback;
    }

    public void sendToListener(final String dataToSend) {
        sendToListener(dataToSend, -1, -1);
    }

    public void sendToListener(final String dataToSend, final long itemId, final int position) {

        AsyncTask<Object, Integer, JsonObject> asyncTask = new AsyncTask<Object, Integer, JsonObject>() {
            @Override
            protected JsonObject doInBackground(Object... objects) {
                try {
                    JsonObject json = new JsonObject();
                    json.addProperty("hash", hash);
                    json.addProperty("id", emailAddress);

                    String[] encrypted = Crypto.encrypt(Crypto.getSecretKey(password, emailAddress),
                            dataToSend);
                    json.addProperty("iv", encrypted[0]);
                    json.addProperty("message", encrypted[1]);

                    return json;
                } catch (Exception e) {
                    e.printStackTrace();

                    if (mDataSentHandler != null) {
                        Message message = Message.obtain(mDataSentHandler, R.id.es_send_failed);
                        message.obj = itemId;
                        message.arg1 = position;
                        message.sendToTarget();
                    }

                    return null;
                }
            }

            @Override
            protected void onPostExecute(JsonObject json) {
                if (json != null) {
                    Ion.with(context, url)
                            .setJsonObjectBody(json)
                            .asString()
                            .setCallback(new FutureCallback<String>() {
                                @Override
                                public void onCompleted(Exception e, String result) {

                                    if (mDataSentHandler != null) {
                                        Message message = Message.obtain(mDataSentHandler,
                                                result.equals("OK") ? R.id.es_send_succeeded : R.id.es_send_failed);
                                        message.obj = itemId;
                                        message.arg1 = position;
                                        message.sendToTarget();
                                    }
                                }
                            });
                }
            }

        };

        asyncTask.execute();
    }
}
