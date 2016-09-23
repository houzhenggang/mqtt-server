package me.ilbba.mqtt.spi.impl;

import me.ilbba.mqtt.spi.iface.IAuthenticator;

/**
 * 身份认证接口
 *
 * @author liangbo
 * @date 16/9/4 11:36
 * @since 1.0
 */
public class DefaultAuthenticator implements IAuthenticator {

    //TODO: 查询数据库或者redis
    public boolean checkValid(String username, String password) {
        return true;
    }
}
