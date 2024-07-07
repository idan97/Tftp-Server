package bgu.spl.net.srv;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.impl.tftp.TftpServer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final BidiMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private final Connections<T> connections = TftpServer.getConnections();
    private final Object socketLock = new Object();

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, BidiMessagingProtocol<T> protocol) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { // just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            protocol.start(-1, connections, this);

            while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                T nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    T response = protocol.process(nextMessage);
                    if (response != null) {
                        synchronized (socketLock) { // also can be send from the connections "sendAll" method
                            out.write(encdec.encode(response));
                            out.flush();
                        }
                    }
                }

            }

        } catch (IOException ex) {
            connections.disconnect(protocol.getConnectionId());
        }
    }

    @Override
    public void close() throws IOException {
        sock.close();
    }

    @Override
    public void send(T msg) { // called from the connections "sendAll" method
        synchronized (socketLock) {
            try {
                out.write(encdec.encode(msg));
                out.flush();
            } catch (IOException e) {
            }
        }
    }
}
