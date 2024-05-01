package bgu.spl.net.impl.tftp;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;

import bgu.spl.net.api.ConnectionHandler;

public class ClientBlockingConnectionHandler implements Runnable, ConnectionHandler<byte[]> {

    protected final ClientProtocol protocol;
    protected final TftpEncoderDecoder encdec;
    protected final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;


    public ClientBlockingConnectionHandler(Socket sock, TftpEncoderDecoder reader, ClientProtocol protocol) {
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
            System.out.println("connected to server");

            while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    //System.out.println("Received message: " + Arrays.toString((byte[]) nextMessage));
                    byte[] response = protocol.process(nextMessage);
                    if (response != null) {
                        //System.out.println("Sending response: " + Arrays.toString((byte[]) response));
                        out.write(encdec.encode(response));
                        out.flush();
                    }
                }
            }

        } catch (IOException ex) {
        }

    }

    @Override
    public void close() throws IOException {
        sock.close();
    }

    @Override
    public void send(byte[] msg) {
        try {
            out.write(encdec.encode(msg));
            out.flush();
        } catch (IOException e) {
        }
    }
}
