package bgu.spl.net.impl.tftp;

// Enum class for TFTP opcodes
public enum TftpOpcode {
    RRQ((short) 1, "Read request"),
    WRQ((short) 2, "Write request"),
    DATA((short) 3, "Data packet"),
    ACK((short) 4, "Acknowledgment"),
    ERROR((short) 5, "Error"),
    DIRQ((short) 6, "Directory listing request"),
    LOGRQ((short) 7, "Login request"),
    DELRQ((short) 8, "Delete file request"),
    BCAST((short) 9, "Broadcast file"),
    DISC((short) 10, "Disconnect"),
    NONE((short) 0, "Not defined");

    private final short value;
    private final String description;

    TftpOpcode(short value, String description) {
        this.value = value;
        this.description = description;
    }

    public short getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}

