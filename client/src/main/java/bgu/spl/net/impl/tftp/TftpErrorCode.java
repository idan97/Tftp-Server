package bgu.spl.net.impl.tftp;

// Enum class for TFTP error codes
public enum TftpErrorCode {
    NOT_DEFINED((short) 0, "Not defined, see error message (if any)"),
    FILE_NOT_FOUND((short) 1, "File not found"),
    ACCESS_VIOLATION((short) 2, "Access violation"),
    DISK_FULL((short) 3, "Disk full or allocation exceeded"),
    ILLEGAL_OPERATION((short) 4, "Illegal TFTP operation"),
    FILE_EXISTS((short) 5, "File already exists"),
    USER_NOT_LOGGED_IN((short) 6, "User not logged in"),
    USER_ALREADY_LOGGED_IN((short) 7, "User already logged in");

    private final short value;
    private final String meaning;

    TftpErrorCode(short value, String meaning) {
        this.value = value;
        this.meaning = meaning;
    }

    public short getValue() {
        return value;
    }

    public String getMeaning() {
        return meaning;
    }
}

