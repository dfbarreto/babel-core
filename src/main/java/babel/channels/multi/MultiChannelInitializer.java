package babel.channels.multi;

import babel.generic.ProtoMessage;
import babel.initializers.ChannelInitializer;
import channel.ChannelListener;
import channel.IChannel;
import network.ISerializer;

import java.io.IOException;
import java.util.Properties;

public class MultiChannelInitializer implements ChannelInitializer<IChannel<ProtoMessage>> {
    @Override
    public MultiChannel initialize(ISerializer<ProtoMessage> serializer, ChannelListener<ProtoMessage> list,
                                   Properties properties, short protoId) throws IOException {
        return MultiChannel.getInstance(serializer, list, protoId, properties);
    }
}
