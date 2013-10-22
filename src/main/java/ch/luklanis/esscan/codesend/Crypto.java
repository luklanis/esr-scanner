package ch.luklanis.esscan.codesend;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
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
    public static final int IV_LENGTH = 16;
    public static final int PBE_ITERATION_COUNT = 10000;

    private static final String RANDOM_ALGORITHM = "SHA1PRNG";
    private static final String HASH_ALGORITHM = "SHA-512";
    private static final String PBE_ALGORITHM = "PBEWithSHA256And256BitAES-CBC";
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String SECRET_KEY_ALGORITHM = "AES";

    @TargetApi(Build.VERSION_CODES.FROYO)
    public static String[] encrypt(SecretKey secret, String cleartext) throws Exception {
        Cipher encryptionCipher = null;
            encryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM, PROVIDER);
        encryptionCipher.init(Cipher.ENCRYPT_MODE, secret);
            AlgorithmParameters params = encryptionCipher.getParameters();
            byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
            byte[] encryptedText = encryptionCipher.doFinal(cleartext.getBytes("UTF-8"));

            return new String[] {
                    Base64.encodeToString(iv, Base64.DEFAULT),
                    Base64.encodeToString(encryptedText, Base64.DEFAULT)};
    }

    public static SecretKey getSecretKey(String password, String salt) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
            PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), PBE_ITERATION_COUNT, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBE_ALGORITHM, PROVIDER);
            SecretKey tmp = factory.generateSecret(pbeKeySpec);
            SecretKey secret = new SecretKeySpec(tmp.getEncoded(), SECRET_KEY_ALGORITHM);
            return secret;
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    public static String getHash(String password, String salt) throws NoSuchProviderException, NoSuchAlgorithmException, UnsupportedEncodingException {
            String input = password + salt;
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM, PROVIDER);
            byte[] out = md.digest(input.getBytes("UTF-8"));
            return Base64.encodeToString(out, Base64.DEFAULT);
    }

    private static byte[] generateIv() throws NoSuchAlgorithmException, NoSuchProviderException {
        SecureRandom random = SecureRandom.getInstance(RANDOM_ALGORITHM);
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        return iv;
    }

}
