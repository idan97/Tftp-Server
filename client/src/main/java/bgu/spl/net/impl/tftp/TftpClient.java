package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.net.Socket;


public class TftpClient {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            args = new String[] { "localhost", "7777" };
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket(host, port);)
                 {

            ClientBlockingConnectionHandler handler = new ClientBlockingConnectionHandler(socket,
                    new TftpEncoderDecoder(), new ClientProtocol());
            Thread listeningThread = new Thread(handler);
            listeningThread.start();

            KeyboardHandler keyboardHandler = new KeyboardHandler(handler);
            Thread keyboardThread = new Thread((Runnable) keyboardHandler);
            keyboardThread.start();
            try {
                listeningThread.join();
                keyboardThread.interrupt();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}
