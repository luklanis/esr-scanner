package ch.luklanis.esscan.codesend;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;

/**
 * Created by lukas on 8/30/13.
 */
public class ESSendServer extends WebSocketServer {

    private static final String TAG = ESRSender.class.getName();

    private static final String STOP_CONNECTION = "STOP";

    private static final String START_SERVER = "START";

    private static final String ACK = "ACK";

    public ESSendServer(int port) throws UnknownHostException {
        super( new InetSocketAddress( port ) );
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {

    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {

    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {

    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {

    }

    public boolean send(String codeRow) {
        Collection<WebSocket> con = connections();

        if (con.isEmpty()) {
            return false;
        }

        synchronized ( con ) {
            for( WebSocket c : con ) {
                c.send( codeRow );
            }
        }

        return true;
    }
}
