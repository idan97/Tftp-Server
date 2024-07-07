package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import bgu.spl.net.api.MessagingProtocol;

public class ClientProtocol implements MessagingProtocol<byte[]> {
    private boolean shouldTerminate = false;
    private Path filePath = null;
    private byte[] sentData = new byte[0];
    private byte[] receivedData = new byte[0];
    private final int blockSize = 512;
    private short blockNumber = 1;
    private int offset = 0;
    private TftpOpcode lastOpcode = TftpOpcode.NONE;
    protected boolean wait = false;
    private boolean lastPacket = false;
    private KeyboardHandler keyboardHandler = null;
    //private static String folderPath = "." + File.separator;
    private static String folderPath = getFolderPath();
    private static Map<String, Object> files = getAllFiles();

    @Override
    public byte[] process(byte[] message) {
        short opcode = b2s(new byte[] { message[0], message[1] });
        TftpOpcode tftpOpcode = TftpOpcode.values()[opcode - 1];
        switch (tftpOpcode) {
            case DATA:
                return handleData(message);
            case ACK:
                return handleAck(message);
            case ERROR:
                return handleError(message);
            case BCAST:
                return handleBCast(message);
            default:
                break;
        }

        return null;
    }

    private byte[] handleAck(byte[] message) {
        short ackBlock = b2s(new byte[] { message[2], message[3] });
        System.out.println("ACK" + ackBlock);
        switch (lastOpcode) {
            case WRQ:
                return transferFile();
            case DATA:
                if (ackBlock != blockNumber - 1) {
                    printError(TftpErrorCode.NOT_DEFINED);
                    break;
                }
                return sendData();
            case DISC:
                shouldTerminate = true;
            default:
                wait = false;
                synchronized (keyboardHandler.lock) {
                    keyboardHandler.lock.notify();
                }
                break;
        }
        return null;
    }

    private byte[] transferFile() {
        try {
            synchronized (files) {
                if (files.containsKey(filePath.getFileName().toString())) {
                    sentData = Files.readAllBytes(filePath);
                } else {
                    printError(TftpErrorCode.FILE_NOT_FOUND);
                    return null;
                }
            }

        } catch (IOException e) {
            printError(TftpErrorCode.ACCESS_VIOLATION);
        }
        lastOpcode = TftpOpcode.DATA;
        return sendData();
    }

    private byte[] sendData() {
        int remaining = sentData.length - offset;
        if (remaining <= 0 && (!(sentData.length == 0 && blockNumber == 1))) {
            System.out.println("WRQ" + " " + filePath.getFileName().toString() + " complete");
            wait = false;
            synchronized (keyboardHandler.lock) {
                keyboardHandler.lock.notify();
            }
            blockNumber = 1;
            sentData = new byte[0];
            offset = 0;
            return null;
        }
        int currentBlockSize = Math.min(remaining, blockSize);
        byte[] block = Arrays.copyOfRange(sentData, offset, offset + currentBlockSize);
        byte[] packet = createDataPacket(block, blockNumber);
        offset += currentBlockSize;
        blockNumber++;
        return packet;
    }

    private byte[] createDataPacket(byte[] data, int blockNumber) {
        byte[] opcode = s2b((short) 3);
        byte[] packetSizeBytes = s2b((short) data.length); // Convert the size of the block to bytes
        byte[] blockNumberBytes = s2b((short) blockNumber);
        return mergeByteArrays(opcode, packetSizeBytes, blockNumberBytes, data);
    }

    private byte[] handleData(byte[] message) {
        lastPacket = recieveDataPacket(message);
        byte[] ans = ack((short) blockNumber);
        switch (lastOpcode) {
            case DIRQ:
                if (lastPacket) {
                    printFiles();
                    blockNumber = 1;
                    receivedData = new byte[0];
                    wait = false;
                    synchronized (keyboardHandler.lock) {
                        keyboardHandler.lock.notify();
                    }
                } else {// not the last packet
                    blockNumber++;
                }
                break;
            case RRQ:
                if (lastPacket) {
                    writeToFile();
                    System.out.println("RRQ" + " " + filePath.getFileName().toString() + " complete");
                    blockNumber = 1;
                    receivedData = new byte[0];
                    wait = false;
                    synchronized (keyboardHandler.lock) {
                        keyboardHandler.lock.notify();
                    }
                } else { // not the last packet
                    blockNumber++;
                }
                break;

            default:
                break;
        }

        return ans;
    }

    private byte[] ack(short i) {
        byte[] b1 = s2b((short) 4);
        byte[] b2 = s2b(i);
        return mergeByteArrays(b1, b2);
    }

    private void printFiles() {
        String dataAsString = new String(receivedData, StandardCharsets.UTF_8);
        String[] fileNames = dataAsString.split("\0");
        for (String fileName : fileNames) {
            System.out.println(fileName);
        }
    }

    private boolean recieveDataPacket(byte[] message) {
        byte[] newData = Arrays.copyOfRange(message, 6, message.length);
        receivedData = mergeByteArrays(receivedData, newData);
        return (newData.length < blockSize);
    }

    private void writeToFile() {
        synchronized (files) {
            try {
                if (!files.containsKey(filePath.getFileName().toString())) {
                    files.put(filePath.getFileName().toString(), new Object());
                    Files.write(filePath, receivedData);
                } else {
                    printError(TftpErrorCode.FILE_EXISTS);
                }
            } catch (IOException e) {
                printError(TftpErrorCode.ACCESS_VIOLATION);
            }
        }
    }

    private byte[] mergeByteArrays(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int destPos = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, destPos, array.length);
            destPos += array.length;
        }
        return result;
    }

    private byte[] handleBCast(byte[] message) {
        // Extract the operation type (add/del) and file name from the message
        String operation = (message[2] == 0) ? "del" : "add"; // 0 for delete, 1 for add
        String fileName = new String(Arrays.copyOfRange(message, 3, message.length), StandardCharsets.UTF_8);

        // Print the BCAST information to the terminal
        System.out.println("BCAST " + operation + " " + fileName);

        return null;
    }

    private byte[] handleError(byte[] message) {
        blockNumber = 1;
        sentData = new byte[0];
        receivedData = new byte[0];
        printError(message);
        wait = false;
        switch (lastOpcode) {
            case RRQ:
                break;
            case DISC:
                shouldTerminate = true;
                break;
            default:
                break;
        }
        synchronized (keyboardHandler.lock) {
            keyboardHandler.lock.notify();
        }
        return null;
    }

    private void printError(TftpErrorCode err) {
        System.out.println("Error " + err.getValue() + " " + err.getMeaning());
    }

    private void printError(byte[] message) {
        short errorCode = b2s(new byte[] { message[2], message[3] });
        String errMsg = new String(Arrays.copyOfRange(message, 4, message.length - 1), StandardCharsets.UTF_8);
        System.out.println("ERROR " + errorCode + " " + errMsg);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    public byte[] processKeyboard(String line) {
        line = line.trim();

        // Split the trimmed line into two parts at the first occurrence of a space
        // character
        String[] parts = line.split("\\s+", 2);
        //String[] parts = line.split(" ", 2);
        String command = parts[0];
        String name = "";

        // Check if there are at least two parts
        if (parts.length < 2 && !(command.equals("DIRQ") || command.equals("DISC"))) {
            printError(TftpErrorCode.ILLEGAL_OPERATION);
            return null;
        } else if (parts.length > 1) {
            name = parts[1];
        }

        switch (command) {
            case "RRQ":
                lastOpcode = TftpOpcode.RRQ;
                return createRRQ(name);
            case "WRQ":
                lastOpcode = TftpOpcode.WRQ;
                return createWRQ(name);
            case "LOGRQ":
                lastOpcode = TftpOpcode.LOGRQ;
                wait = true;
                return createLOGRQ(name);
            case "DIRQ":
                lastOpcode = TftpOpcode.DIRQ;
                wait = true;
                return createDIRQ();
            case "DISC":
                lastOpcode = TftpOpcode.DISC;
                return createDISC();
            case "DELRQ":
                lastOpcode = TftpOpcode.DELRQ;
                wait = true;
                return createDELRQ(name);
            default:
                printError(TftpErrorCode.ILLEGAL_OPERATION);
                break;
        }
        return null;
    }

    private byte[] createRRQ(String fileName) {
        synchronized (files) {
            if (files.containsKey(fileName)) {
                printError(TftpErrorCode.FILE_EXISTS);
                return null;
            }
        }
        wait = true;
        String stringFilePath = folderPath + fileName;
        filePath = Paths.get(stringFilePath);
        byte[] opcode = s2b((short) 1);
        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        return mergeByteArrays(opcode, nameBytes, new byte[] { 0 });
    }

    private static String getFolderPath() {
        String projectPath = System.getProperty("user.dir"); // Get the current working directory of the server
        return projectPath + File.separator + "client" + File.separator + "Files" + File.separator;
    }

    private byte[] createWRQ(String fileName) {
        synchronized (files) {
            if (!files.containsKey(fileName)) {
                printError(TftpErrorCode.FILE_NOT_FOUND);
                return null;
            }
        }
        wait = true;
        String stringFilePath = folderPath + fileName;
        filePath = Paths.get(stringFilePath);
        byte[] opcode = s2b((short) 2);
        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        return mergeByteArrays(opcode, nameBytes, new byte[] { 0 });
    }

    private byte[] createDELRQ(String fileName) {
        String stringFilePath = folderPath + fileName;
        filePath = Paths.get(stringFilePath);
        byte[] opcode = s2b((short) 8);
        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        return mergeByteArrays(opcode, nameBytes, new byte[] { 0 });
    }

    private byte[] createLOGRQ(String username) {
        byte[] opcode = s2b((short) 7);
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        return mergeByteArrays(opcode, usernameBytes, new byte[] { 0 });
    }

    private byte[] createDIRQ() {
        return s2b((short) 6);
    }

    private byte[] createDISC() {
        shouldTerminate = true;
        return s2b((short) 10);
    }

    private short b2s(byte[] bytes) {
        return (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0xff);
    }

    private byte[] s2b(short s) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((s >> 8) & 0xff);
        bytes[1] = (byte) (s & 0xff);
        return bytes;
    }

    public void setKeyboardHandler(KeyboardHandler keyboardHandler) {
        this.keyboardHandler = keyboardHandler;
    }

    private static Map<String, Object> getAllFiles() {
        Map<String, Object> files = new HashMap<>();
        File directory = new File(folderPath);
        File[] filesList = directory.listFiles();
        if (filesList != null) {
            for (File file : filesList) {
                files.put(file.getName(), new Object());
            }
        }
        return files;
    }
}
