package me.ilbba.mqtt.handler;

import me.ilbba.mqtt.spi.ProtocolProcessor;

/**
 * Created by liangbo on 16/9/18.
 */
public class MQTTHandler {

    private final ProtocolProcessor m_processor;

    public MQTTHandler(ProtocolProcessor processor) {
        m_processor = processor;
    }
}
