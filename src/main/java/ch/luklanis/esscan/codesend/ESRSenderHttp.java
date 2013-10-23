package ch.luklanis.esscan.codesend;

import com.google.gson.JsonObject;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import ch.luklanis.esscan.R;

/**
 * Created by lukas on 10/22/13.
 */
public class ESRSenderHttp implements IEsrSender {

    private final String url = "http://esr-relay.herokuapp.com";
    private final String password;
    private final String emailAddress;
    private final String hash;
    private final Context context;
    private Handler mDataSentHandler;

    public ESRSenderHttp(Context context, String emailAddress, String password)
            throws NoSuchAlgorithmException,
            NoSuchProviderException,
            UnsupportedEncodingException {
        this.context = context;
        this.password = password;
        this.emailAddress = emailAddress;
        hash = Crypto.getHash(password, emailAddress);
    }

    public void registerDataSentHandler(Handler dataSentCallback) {
        mDataSentHandler = dataSentCallback;
    }

    public void sendToListener(final String dataToSend) {
        sendToListener(dataToSend, -1);
    }

    public void sendToListener(final String dataToSend, final int position) {
        JsonObject json = new JsonObject();
        json.addProperty("hash", hash);
        json.addProperty("id", emailAddress);

        try {
            String[] encrypted = Crypto.encrypt(Crypto.getSecretKey(password, emailAddress), dataToSend);
            json.addProperty("iv", encrypted[0]);
            json.addProperty("message", encrypted[1]);
        } catch (Exception e) {
            e.printStackTrace();

            if (mDataSentHandler != null) {
                Message message = Message.obtain(mDataSentHandler, R.id.es_send_failed);
                message.arg1 = position;
                message.obj = dataToSend;
                message.sendToTarget();
            }
        }

        Ion.with(context, url)
                .setJsonObjectBody(json)
                .asString()
                .setCallback(new FutureCallback<String>() {
                    @Override
                    public void onCompleted(Exception e, String result) {

                        if (mDataSentHandler != null) {
                            Message message = Message.obtain(mDataSentHandler,
                                    result.equals("OK") ? R.id.es_send_succeeded :
                                            R.id.es_send_failed);
                            message.arg1 = position;
                            message.obj = dataToSend;
                            message.sendToTarget();
                        }
                    }
                });
    }
}
