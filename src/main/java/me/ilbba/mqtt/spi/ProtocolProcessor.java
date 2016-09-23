package me.ilbba.mqtt.spi;

import me.ilbba.mqtt.spi.iface.IAuthenticator;
import me.ilbba.mqtt.spi.iface.IMessagesStore;
import me.ilbba.mqtt.spi.iface.ISessionsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by liangbo on 16/9/9.
 */
public class ProtocolProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolProcessor.class);

    private IAuthenticator authenticator;
    private ISessionsStore sessionsStore;
    private IMessagesStore messagesStore;




}
