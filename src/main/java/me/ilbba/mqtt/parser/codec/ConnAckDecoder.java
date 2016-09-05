package me.ilbba.mqtt.parser.codec;

import io.netty.buffer.ByteBuf;
import io.netty.util.AttributeMap;
import me.ilbba.mqtt.protocol.ConnAckMessage;

import java.util.List;

/**
 * @author andrea
 */
public class ConnAckDecoder extends DemuxDecoder {

    @Override
    public void decode(AttributeMap ctx, ByteBuf in, List<Object> out) throws Exception {
        in.resetReaderIndex();
        //Common decoding part
        ConnAckMessage message = new ConnAckMessage();
        if (!decodeCommonHeader(message, 0x00, in)) {
            in.resetReaderIndex();
            return;
        }
        //skip reserved byte
        in.skipBytes(1);

        //read  return code
        message.setReturnCode(in.readByte());
        out.add(message);
    }

}
