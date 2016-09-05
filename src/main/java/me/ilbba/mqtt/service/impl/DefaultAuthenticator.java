package me.ilbba.mqtt.service.impl;

import me.ilbba.mqtt.service.IAuthenticator;

/**
 * @author liangbo
 * @date 16/9/4 11:36
 * @since 1.0
 */
public class DefaultAuthenticator implements IAuthenticator {

    public boolean checkValid(String username, String password) {
        return false;
    }
}
