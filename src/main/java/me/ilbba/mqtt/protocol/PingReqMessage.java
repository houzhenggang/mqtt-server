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
package me.ilbba.mqtt.protocol;

/**
 * The PINGREQ message is an "are you alive?" message that is sent from a connected client to the server.
 *
 * Doesn't care DUP, QOS and RETAIN flags.
 *
 * @author andrea
 */
public class PingReqMessage extends ZeroLengthMessage {

    public PingReqMessage() {
        m_messageType = PINGREQ;
    }
}
