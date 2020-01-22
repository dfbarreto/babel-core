package babel.genericprotocol.handlers;

import babel.runtime.protocol.ProtoMessage;
import network.data.Host;

/**
 * Represents an operation that accepts a single input argument and returns no
 * result. Unlike most other functional interfaces, {@code Consumer} is expected
 * to operate via side-effects.
 *
 */
@FunctionalInterface
public interface MessageSentHandler<T extends ProtoMessage> {

    /**
     * Performs this operation on the ProtocolMessage.
     *
     * @param msg the received message
     */
    void onMessageSent(T msg, Host to, short destProto, int channelId);
}
