package bgu.spl.net.srv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    private final Map<Integer, ConnectionHandler<T>> connectionHandlers;

    public ConnectionsImpl() {
        this.connectionHandlers = new ConcurrentHashMap<>();
    }

    @Override
    public boolean connect(int connectionId, ConnectionHandler<T> handler) {
        if (!connectionHandlers.containsKey(connectionId)) {
            connectionHandlers.put(connectionId, handler);
            return true;
        }
        return false;
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connectionHandlers.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) {
        connectionHandlers.remove(connectionId);
    }

    @Override
    public void sendAll(T msg) {
        connectionHandlers.values().forEach(handler -> handler.send(msg));
    }

}
