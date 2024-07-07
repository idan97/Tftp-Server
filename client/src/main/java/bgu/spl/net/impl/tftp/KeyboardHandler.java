package bgu.spl.net.impl.tftp;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

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
                String command = null;
                synchronized (lock) {
                    while (protocol.wait) {
                        try {
                            lock.wait(); // Wait until protocol.wait becomes false
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    displayMenu();
                    String choice = keyboard.readLine();
                    command = processUserInput(choice, keyboard);
                }

                if (command != null) {
                    byte[] lineToByte = protocol.processKeyboard(command);
                    if (lineToByte != null) {
                        synchronized (lock) {
                            out.write(lineToByte); // Send the byte array as is
                            out.flush();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void displayMenu() {
        System.out.println();
        System.out.println("Please choose one of the following instructions:");
        System.out.println("1. READ REQUEST (RRQ)");
        System.out.println("2. WRITE REQUEST (WRQ)");
        System.out.println("3. DELETE REQUEST (DELRQ)");
        System.out.println("4. DIRECTORY REQUEST (DIRQ)");
        System.out.println("5. LOGIN (LOGRQ)");
        System.out.println("6. DISCONNECT (DISC)");
        System.out.print("Enter your choice: ");
    }

    private String processUserInput(String choice, BufferedReader keyboard) throws IOException {
        switch (choice) {
            case "1":
                System.out.print("Enter filename to read: ");
                String rrqFilename = keyboard.readLine();
                return "RRQ " + rrqFilename;
            case "2":
                System.out.print("Enter filename to write: ");
                String wrqFilename = keyboard.readLine();
                return "WRQ " + wrqFilename;
            case "3":
                System.out.print("Enter filename to delete: ");
                String delrqFilename = keyboard.readLine();
                return "DELRQ " + delrqFilename;
            case "4":
                return "DIRQ";
            case "5":
                System.out.print("Enter username to login: ");
                String username = keyboard.readLine();
                return "LOGRQ " + username;
            case "6":
                return "DISC";
            default:
                System.out.println("Invalid choice. Please try again.");
                return null;
        }
    }
}
