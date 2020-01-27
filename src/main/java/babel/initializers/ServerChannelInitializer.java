package babel.initializers;

import babel.generic.ProtoMessage;
import channel.ChannelListener;
import channel.simpleclientserver.SimpleServerChannel;
import network.ISerializer;

import java.net.UnknownHostException;
import java.util.Properties;

public class ServerChannelInitializer implements ChannelInitializer<SimpleServerChannel<ProtoMessage>> {

    @Override
    public SimpleServerChannel<ProtoMessage> initialize(ISerializer<ProtoMessage> serializer,
                                                        ChannelListener<ProtoMessage> list,
                                                        Properties properties) throws UnknownHostException {
        return new SimpleServerChannel<>(serializer, list, properties);
    }
}
