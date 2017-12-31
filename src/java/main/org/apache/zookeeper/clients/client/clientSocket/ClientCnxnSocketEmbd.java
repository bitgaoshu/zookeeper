package org.apache.zookeeper.clients.client.clientSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

public class ClientCnxnSocketEmbd extends ClientCnxnSocket {
    @Override
    boolean isConnected() {
        return false;
    }

    @Override
    void connect(InetSocketAddress addr) throws IOException {

    }

    @Override
    SocketAddress getRemoteSocketAddress() {
        return null;
    }

    @Override
    SocketAddress getLocalSocketAddress() {
        return null;
    }

    @Override
    void cleanup() {

    }

    @Override
    void packetAdded() {

    }

    @Override
    void onClosing() {

    }

    @Override
    void saslCompleted() {

    }

    @Override
    void connectionPrimed() {

    }

    @Override
    void doTransport(int waitTimeOut, List<ClientCnxn.Packet> pendingQueue, ClientCnxn cnxn) throws IOException, InterruptedException {

    }

    @Override
    void testableCloseSocket() throws IOException {

    }

    @Override
    void close() {

    }

    @Override
    void sendPacket(ClientCnxn.Packet p) throws IOException {

    }
}
