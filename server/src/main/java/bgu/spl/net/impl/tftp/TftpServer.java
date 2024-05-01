package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.*;

public class TftpServer<T> {
    private static Connections<byte[]> connections = new ConnectionsImpl<>(); //one shared object
    private static int connectionId = 0;
    private static final Object idLock = new Object();
    
    public static <T> Connections<T> getConnections() {
        return (Connections<T>) connections;
    }

    public static void main(String[] args) {    
        int port = 7777;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } 
        Server.threadPerClient(
                port, // port
                TftpProtocol::new, // protocol factory
                TftpEncoderDecoder::new // message encoder decoder factory
        ).serve();
    }
    
}
