package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private byte[] bytes = new byte[1 << 10]; // initial capacity for received bytes
    private int len = 0;
    private short packetSize = 0; // for DATA packets the number of bytes in the packet
    TftpOpcode opVal = TftpOpcode.NONE;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        pushByte(nextByte);
        if (shouldTerminate(nextByte)) {
            return extractMessage();
        }
        return null;
    }

    private byte[] extractMessage() {
        byte[] result = Arrays.copyOf(bytes, len);
        len = 0;
        bytes = new byte[1 << 10];
        opVal = TftpOpcode.NONE;
        packetSize = 0;
        return result;
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }
        bytes[len++] = nextByte;
        if (len == 2) {
            short opcode = b2s(Arrays.copyOfRange(bytes, 0, 2));
            if (opcode < 1 || opcode > 10) {
                opVal = TftpOpcode.NONE;
            } else {
                opVal = TftpOpcode.values()[opcode - 1];
            }
        } else if (len == 4 && opVal == TftpOpcode.DATA) {
            packetSize = b2s(Arrays.copyOfRange(bytes, 2, 4));
        }

    }

    private boolean shouldTerminate(byte nextByte) {
        switch (opVal) {
            case LOGRQ:
                return nextByte == '\0';
            case DIRQ:
                return true;
            case RRQ:
                return nextByte == '\0' && len > 3;
            case WRQ:
                return nextByte == '\0' && len > 3;
            case DELRQ:
                return nextByte == '\0' && len > 3;
            case BCAST:
                return nextByte == '\0' && len > 3;
            case DATA:
                return len >= 6 + packetSize && len >= 6;
            case ACK:
                return len == 4;
            case DISC:
                return len == 2;
            case ERROR:
                return (len > 4 && nextByte == '\0');
            case NONE:
                return false;
            default:
                return false;
        }
    }

    @Override
    public byte[] encode(byte[] message) {
        return message; // no encoding needed for this protocol
    }

    private short b2s(byte[] bytes) {
        return (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0xff);
    }

}
