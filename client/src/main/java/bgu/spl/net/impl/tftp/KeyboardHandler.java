package bgu.spl.net.impl.tftp;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Arrays;

public class KeyboardHandler implements Runnable {

    private final ClientProtocol protocol;
    private final TftpEncoderDecoder encdec;
    private final Socket sock;
    protected final Object lock = new Object();

    public KeyboardHandler(ClientBlockingConnectionHandler handler) {
        this.sock = handler.sock;
        this.encdec = handler.encdec;
        this.protocol = handler.protocol;
        protocol.setKeyboardHandler(this);
    }

    @Override
    public void run() {
        try {
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());
            while (!protocol.shouldTerminate() && !Thread.currentThread().isInterrupted()) {
                String line = keyboard.readLine();
                synchronized (lock) {
                    while (protocol.wait) {
                        try {
                            lock.wait(); // Wait until protocol.wait becomes false
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                byte[] lineToByte = protocol.processKeyboard(line);
                if (lineToByte != null) {
                    //System.out.println("Sending: " + Arrays.toString(lineToByte)); // Convert byte array to string representation
                    out.write(lineToByte); // Send the byte array as is
                    out.flush();
                }
                
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
