package pt.unl.fct.di.novasys.babel.channels.multi;

import pt.unl.fct.di.novasys.babel.internal.BabelMessage;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.channel.ChannelListener;
import pt.unl.fct.di.novasys.channel.base.SingleThreadedBiChannel;
import pt.unl.fct.di.novasys.network.AttributeValidator;
import pt.unl.fct.di.novasys.network.Connection;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.NetworkManager;
import pt.unl.fct.di.novasys.network.data.Attributes;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MultiChannel extends SingleThreadedBiChannel<BabelMessage, BabelMessage> implements AttributeValidator {

    private static final Logger logger = LogManager.getLogger(MultiChannel.class);
    private static final short TCP_MAGIC_NUMBER = 0x2727;

    protected static final String LISTEN_ADDRESS_ATTRIBUTE = "listen_address";

    private static final int DEFAULT_PORT = 12727; //Decimal Ascii for DEL (127) ESC (27)

    private final NetworkManager<BabelMessage> network;
    private final Map<Short, ChannelListener<BabelMessage>> listeners;

    private Attributes attributes;

    private Map<Integer, ProtoConnections> protocolConnections;

    private static MultiChannel multiChannelInstance = null;

    public static MultiChannel getInstance(ISerializer<BabelMessage> serializer,
                                           ChannelListener<BabelMessage> list,
                                           short protoId,
                                           Properties properties)  throws IOException {
        if(multiChannelInstance == null)
            multiChannelInstance = new MultiChannel(serializer, properties);

        multiChannelInstance.addListener(protoId, list);
        return multiChannelInstance;
    }

    private void addListener(short protoId, ChannelListener<BabelMessage> list) {
        if(this.listeners.putIfAbsent(protoId, list) != null)
            throw new RuntimeException("Protocol with id " + protoId + " asked for Multi Channel twice");
    }


    private MultiChannel(ISerializer<BabelMessage> serializer, Properties properties) throws IOException {

        super("MultiChannel");
        this.listeners = new HashMap<>();
        this.protocolConnections = new HashMap<>();

        InetAddress addr = null;
        if(properties.containsKey("address"))
            addr = Inet4Address.getByName(properties.getProperty("address"));

        if(addr == null)
            throw new AssertionError("No address received in Multi Channel properties");

        int port = DEFAULT_PORT;
        if(properties.containsKey("port"))
            port = Integer.parseInt(properties.getProperty("port"));

        network = new NetworkManager<>(serializer, this, 1000, 3000, 1000);


        int nThreads = Integer.parseInt(properties.getProperty("nThreads", "0"));

        Host listenAddress = new Host(addr, port);
        network.createServerSocket(this, listenAddress, this, nThreads);

        attributes = new Attributes();
        attributes.putShort(CHANNEL_MAGIC_ATTRIBUTE, TCP_MAGIC_NUMBER);
        attributes.putHost(LISTEN_ADDRESS_ATTRIBUTE, listenAddress);

    }

    @Override
    protected void onInboundConnectionUp(Connection<BabelMessage> connection) {
        Host clientSocket;
        short protoId;
        try {
            clientSocket = connection.getPeerAttributes().getHost(LISTEN_ADDRESS_ATTRIBUTE);
            protoId = connection.getPeerAttributes().getShort(ProtoConnections.PROTO_ID);
        } catch (IOException e) {
            logger.error("Inbound connection without valid listen address: " + e.getMessage());
            connection.disconnect();
            return;
        }

        logger.debug("Inbound up: " + connection + " " + clientSocket);
        protocolConnections.computeIfAbsent((int) protoId,
                k-> new ProtoConnections(loop, protoId, attributes, listeners.get(protoId), network, this))
                .addInboundConnection(clientSocket, connection);

    }

    @Override
    protected void onInboundConnectionDown(Connection<BabelMessage> connection, Throwable cause) {
        short protoId = connection.getPeerAttributes().getShort(ProtoConnections.PROTO_ID);
        ProtoConnections protoConnections = protocolConnections.get((int) protoId);
        if(protoConnections != null) protoConnections.removeInboundConnection(connection, cause);
    }

    @Override
    protected void onServerSocketBind(boolean success, Throwable cause) {
        if (success)
            logger.debug("Server socket ready");
        else
            logger.error("Server socket bind failed: " + cause);
    }

    @Override
    protected void onServerSocketClose(boolean success, Throwable cause) {
        logger.debug("Server socket closed. " + (success ? "" : "Cause: " + cause));
    }

    @Override
    protected void onOutboundConnectionUp(Connection<BabelMessage> connection) {
        short protoId = connection.getSelfAttributes().getShort(ProtoConnections.PROTO_ID);
        protocolConnections.computeIfAbsent((int) protoId,
                k-> new ProtoConnections(loop, protoId, attributes, listeners.get(protoId), network, this))
                .addOutboundConnection(connection);

    }

    @Override
    protected void onOutboundConnectionDown(Connection<BabelMessage> connection, Throwable cause) {
        short protoId = connection.getSelfAttributes().getShort(ProtoConnections.PROTO_ID);
        ProtoConnections protoConnections = protocolConnections.get((int) protoId);
        if(protoConnections != null) protoConnections.removeOutboundConnection(connection, cause);
    }

    @Override
    protected void onOutboundConnectionFailed(Connection<BabelMessage> connection, Throwable cause) {
        short protoId = connection.getSelfAttributes().getShort(ProtoConnections.PROTO_ID);
        ProtoConnections protoConnections = protocolConnections.get((int) protoId);
        if(protoConnections != null) protoConnections.failedOutboundConnection(connection, cause);

    }

    @Override
    protected void onSendMessage(BabelMessage protoMessage, Host host, int mode) {
        protocolConnections.computeIfAbsent((int)protoMessage.getDestProto(),
                k-> new ProtoConnections(loop, protoMessage.getDestProto(), attributes,
                        listeners.get(protoMessage.getDestProto()), network, this))
                .sendMessage(protoMessage, host, mode);

    }

    @Override
    protected void onCloseConnection(Host host, int connection) {
        ProtoConnections protoConnections = protocolConnections.get(connection);
        if (protoConnections != null) protoConnections.disconnect(host);
    }

    @Override
    protected void onDeliverMessage(BabelMessage protoMessage, Connection<BabelMessage> connection) {
        ProtoConnections protoConnections = protocolConnections.get((int)protoMessage.getSourceProto());
        if (protoConnections != null) protoConnections.deliverMessage(protoMessage, connection);
    }

    @Override
    protected void onOpenConnection(Host host) {
        throw new NotImplementedException("Pls fix me");
    }

    @Override
    public boolean validateAttributes(Attributes attributes) {
        Short channel = attributes.getShort(CHANNEL_MAGIC_ATTRIBUTE);
        return channel != null && channel == TCP_MAGIC_NUMBER;
    }
}
