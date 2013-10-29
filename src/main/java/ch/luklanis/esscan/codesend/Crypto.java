package ch.luklanis.esscan.codesend;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

// info: AES enc/dec best practice from http://stackoverflow.com/q/8622367
public class Crypto {

    public static final String PROVIDER = "BC";
    public static final int PBE_ITERATION_COUNT = 1000;

    private static final String HASH_ALGORITHM = "SHA-512";
    //    private static final String PBE_ALGORITHM = "PBEWithSHA256And256BitAES-CBC-BC";       // AES
    private static final String PBE_ALGORITHM = "PBEWithSHAAnd3-KeyTripleDES-CBC";
    //    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";       // AES
    private static final String CIPHER_ALGORITHM = "DESede/CBC/PKCS5Padding";
    //    private static final String SECRET_KEY_ALGORITHM = "AES";       // AES
    private static final String SECRET_KEY_ALGORITHM = "DESede";

    //    private static final int KEY_LENGTH = 256;       // AES
    private static final int KEY_LENGTH = 192;

    @TargetApi(Build.VERSION_CODES.FROYO)
    public static String[] encrypt(SecretKey secret, String cleartext) throws Exception {
        Cipher encryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM, PROVIDER);
        encryptionCipher.init(Cipher.ENCRYPT_MODE, secret);
        AlgorithmParameters params = encryptionCipher.getParameters();
        byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
        byte[] encryptedText = encryptionCipher.doFinal(cleartext.getBytes("UTF-8"));

        return new String[]{Base64.encodeToString(iv, Base64.NO_WRAP), Base64.encodeToString(
                encryptedText,
                Base64.NO_WRAP)};
    }

    public static SecretKey getSecretKey(String password, String salt) throws
                                                                       NoSuchProviderException,
                                                                       NoSuchAlgorithmException,
                                                                       InvalidKeySpecException,
                                                                       UnsupportedEncodingException {
        char[] pw = toHexString(password.getBytes("UTF-8")).toCharArray();
        PBEKeySpec pbeKeySpec = new PBEKeySpec(pw,
                salt.getBytes("UTF-8"),
                PBE_ITERATION_COUNT, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBE_ALGORITHM, PROVIDER);
        SecretKey tmp = factory.generateSecret(pbeKeySpec);
        return new SecretKeySpec(tmp.getEncoded(), SECRET_KEY_ALGORITHM);
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    public static String getHash(String password, String salt)
            throws NoSuchProviderException, NoSuchAlgorithmException, UnsupportedEncodingException {
        String input = password + salt;
        MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM, PROVIDER);
        byte[] out = md.digest(input.getBytes("UTF-8"));
        return Base64.encodeToString(out, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    private static String toHexString(byte[] data) {
        StringBuilder hexString = new StringBuilder();

        for (int i = 0; i < data.length; i++) {
            String hex = Integer.toHexString(0xff & data[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }
}
