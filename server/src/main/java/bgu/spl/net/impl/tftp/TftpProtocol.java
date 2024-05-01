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

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private int connectionId = -1;
    private boolean isLoggedIn = false;
    private Connections<byte[]> connections = null;
    private boolean shouldTerminate = false;
    private BlockingConnectionHandler<byte[]> handler = null;
    private Path filePath = null;
    private byte[] sentData = new byte[0];
    private byte[] receivedData = new byte[0];
    private final int blockSize = 512;
    private short blockNumber = 1;
    private int offset = 0;
    private static String folderPath = "Files" + File.separator;
    private static Map<String, Object> files = getAllFiles();
    

    @Override
    public void start(int connectionId, Connections<byte[]> connections, BlockingConnectionHandler<byte[]> handler) {
        this.connections = connections;
        this.connectionId = connectionId;
        this.shouldTerminate = false;
        this.handler = handler;
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

    @Override
    public byte[] process(byte[] message) {
        short opcode = b2s(new byte[] { message[0], message[1] });
        if (opcode < 1 || opcode > 10) {
            return error2Byte(TftpErrorCode.ILLEGAL_OPERATION);
        }
        TftpOpcode tftpOpcode = TftpOpcode.values()[opcode - 1];
        byte[] err = isValidMessageFormat(message, tftpOpcode);
        if (err != null) {
            return err;
        }
        switch (tftpOpcode) {
            case LOGRQ:
                return login(message);
            case DISC:
                return disconnect();
            case RRQ:
                return handleReadRequest(message);
            case WRQ:
                return handleWriteRequest(message);
            case DATA:
                return handleData(message);
            case ACK:
                return handleAck(message);
            case DIRQ:
                return handleDir();
            case DELRQ:
                return handleDelete();
            default:
                break;
        }
        return null;
    }

    private void broadcast(byte deletedAddedByte) {
        String filename = filePath.getFileName().toString();

        // Construct the BCAST packet
        byte[] opcode = s2b((short) 9);
        byte[] deletedAdded = { deletedAddedByte };
        byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] zeroByte = { 0 };

        // Merge the bytes to form the BCAST packet
        byte[] packet = mergeByteArrays(opcode, deletedAdded, filenameBytes, zeroByte);

        // Send the BCAST packet to all connections
        connections.sendAll(packet);
    }

    private byte[] handleDelete() {
        try {
            synchronized (files) {
                if (!files.containsKey(filePath.getFileName().toString())) {
                    return error2Byte(TftpErrorCode.FILE_NOT_FOUND);
                }
                files.remove(filePath.getFileName().toString());
                Files.delete(filePath); // Delete the file specified by the filePath
            }        
            broadcast((byte) 0); // Broadcast a deleted file
            return ack((short) 0); // Return an ACK indicating successful deletion
        } catch (IOException e) {
            // If an error occurs during deletion, return an error response
            return error2Byte(TftpErrorCode.ACCESS_VIOLATION);
        }
    }

    private byte[] handleDir() {
        StringBuilder directoryListing = new StringBuilder();
        
        // Synchronize access to the files map
        synchronized (files) {
            for (String fileName : files.keySet()) {
                directoryListing.append(fileName).append((char) 0); // Separate file names with null bytes
            }
        }
    
        // Convert the directory listing to bytes
        sentData = directoryListing.toString().getBytes(StandardCharsets.UTF_8);
        blockNumber = 1;
        offset = 0;
    
        return sendData();
    }    

    private byte[] handleData(byte[] message) {
        short packetSize = b2s(Arrays.copyOfRange(message, 2, 4));
        short blockNumber = b2s(Arrays.copyOfRange(message, 4, 6));
        byte[] data = Arrays.copyOfRange(message, 6, message.length);
        receivedData = mergeByteArrays(receivedData, data);

        if (packetSize < 512) {
            try {
                synchronized (files) {
                    if (files.containsKey(filePath.getFileName().toString())) {
                        return error2Byte(TftpErrorCode.FILE_EXISTS);
                    }
                    files.put(filePath.getFileName().toString(), new Object());
                    Files.createFile(filePath);
                    Files.write(filePath, receivedData);
                }
                handler.send(ack(blockNumber));
                receivedData = new byte[0];
                broadcast((byte) 1);
            } catch (IOException e) {
                return error2Byte(TftpErrorCode.NOT_DEFINED);
            }
            return null;
        }
        blockNumber++;
        return ack((short) (blockNumber - 1));
    }

    private byte[] disconnect() {
        connections.disconnect(connectionId);
        shouldTerminate = true;
        isLoggedIn = false;
        return ack((short) 0);
    }

    private byte[] handleAck(byte[] message) {
        short ackBlockNumber = b2s(new byte[] { message[2], message[3] });
        if (ackBlockNumber == blockNumber - 1) {
            return sendData();
        } else {
            return error2Byte(TftpErrorCode.NOT_DEFINED);
        }
    }

    private int extractUsername(byte[] message) {
        byte[] usernameBytes = Arrays.copyOfRange(message, 2, message.length - 1);
        String username = new String(usernameBytes, StandardCharsets.UTF_8);
        return username.hashCode(); // Hash the username to get an integer representation
    }

    private byte[] handleReadRequest(byte[] message) {
        blockNumber = 1;
        offset = 0;
        try {
            synchronized (files) {
                String fileName = filePath.getFileName().toString();
                if (!files.containsKey(fileName)) {
                    return error2Byte(TftpErrorCode.FILE_NOT_FOUND);
                }
                sentData = Files.readAllBytes(filePath);
            }
            
        } catch (Exception e) {
            return error2Byte(TftpErrorCode.NOT_DEFINED);
        }

        return sendData();
    }

    private byte[] sendData() {
        if (offset < sentData.length || (sentData.length == 0 && blockNumber == 1)) {
            int remaining = sentData.length - offset;
            int currentBlockSize = Math.min(remaining, blockSize);
            byte[] block = Arrays.copyOfRange(sentData, offset, offset + currentBlockSize);
            byte[] packet = createDataPacket(block, blockNumber);
            offset += currentBlockSize;
            blockNumber++;
            return packet;
        }
        return null;
    }

    private byte[] createDataPacket(byte[] data, int blockNumber) {
        byte[] opcode = s2b((short) 3);
        byte[] packetSizeBytes = s2b((short) data.length); // Convert the size of the block to bytes
        byte[] blockNumberBytes = s2b((short) blockNumber);
        return mergeByteArrays(opcode, packetSizeBytes, blockNumberBytes, data);
    }

    private byte[] handleWriteRequest(byte[] message) {
        blockNumber = 1;
        return ack((short) 0);
    }

    private byte[] login(byte[] message) {
        connectionId = extractUsername(message);
        if (!isLoggedIn && connections.connect(connectionId, handler)) {
            isLoggedIn = true;
            return ack((short) 0);
        } else {
            return error2Byte(TftpErrorCode.USER_ALREADY_LOGGED_IN);
        }
    }

    private byte[] ack(short i) {
        byte[] b1 = s2b((short) 4);
        byte[] b2 = s2b(i);
        return mergeByteArrays(b1, b2);
    }

    private byte[] error2Byte(TftpErrorCode error) {
        byte[] b1 = s2b((short) 5);
        byte[] b2 = s2b(error.getValue());
        byte[] b3 = error.getMeaning().getBytes();
        byte[] b4 = { 0 };

        return mergeByteArrays(b1, b2, b3, b4);
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

    private short b2s(byte[] bytes) {
        return (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0xff);
    }

    private byte[] s2b(short s) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((s >> 8) & 0xff);
        bytes[1] = (byte) (s & 0xff);
        return bytes;
    }

    private byte[] isValidMessageFormat(byte[] message, TftpOpcode opcode) {
        if (opcode == TftpOpcode.NONE || opcode == TftpOpcode.ERROR) {
            return error2Byte(TftpErrorCode.ILLEGAL_OPERATION);
        }
        if (opcode == TftpOpcode.RRQ || opcode == TftpOpcode.DELRQ) {
            if (!fileExists(message)) {
                return error2Byte(TftpErrorCode.FILE_NOT_FOUND);
            }
        }
        if (opcode == TftpOpcode.WRQ) {
            if (fileExists(message)) {
                return error2Byte(TftpErrorCode.FILE_EXISTS);
            }
        }
        if (opcode != TftpOpcode.LOGRQ && !isLoggedIn) {
            return error2Byte(TftpErrorCode.USER_NOT_LOGGED_IN);
        }
        return null;
    }

    private boolean fileExists(byte[] message) {
        String fileName = new String(Arrays.copyOfRange(message, 2, message.length - 1), StandardCharsets.UTF_8);
        String stringFilePath = folderPath + fileName;
        filePath = Paths.get(stringFilePath);
        synchronized (files) {
            return files.containsKey(fileName);
        }
    }

    private static String getFolderPath() {
        String projectPath = System.getProperty("user.dir"); // Get the current working directory of the server
        return projectPath + File.separator + "Server" + File.separator + "Files" + File.separator;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    @Override
    public int getConnectionId() {
        return connectionId;
    }
}
