package me.ilbba.mqtt.server;

import java.io.IOException;

/**
 * Created by liangbo on 16/9/18.
 */
public interface ServerAcceptor {

    void initialize() throws IOException;

    void close();
}
