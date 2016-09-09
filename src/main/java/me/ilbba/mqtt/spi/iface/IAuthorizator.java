/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package me.ilbba.mqtt.spi.iface;

/**
 * 授权接口
 */
public interface IAuthorizator {

    /**
     * Ask the implementation of the authorizator if the topic can be used in a publish.
     * */
    boolean canWrite(String topic, String user, String to);

    boolean canRead(String topic, String user, String from);
}
