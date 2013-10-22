package ch.luklanis.esscan.codesend;

/**
 * Created by lukas on 10/22/13.
 */
public interface IEsrSender {
    public void sendToListener(final String dataToSend);
    public void sendToListener(final String dataToSend, final int position);
}
