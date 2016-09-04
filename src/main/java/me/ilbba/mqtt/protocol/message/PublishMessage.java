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
package me.ilbba.mqtt.protocol.message;

import java.nio.ByteBuffer;

/**
 * The response to a PUBLISH message depends on the QoS level.
 *
 *  Qos 0 None
 *  Qos 1 PUBCK
 *  Qos 2 PUBREC
 *
 * @author andrea
 */
public class PublishMessage extends MessageIDMessage {

    protected String m_topicName;
    protected ByteBuffer m_payload;

    public PublishMessage() {
        m_messageType = PUBLISH;
    }

    public String getTopicName() {
        return m_topicName;
    }

    public void setTopicName(String topicName) {
        this.m_topicName = topicName;
    }

    public ByteBuffer getPayload() {
        return m_payload;
    }

    public void setPayload(ByteBuffer payload) {
        this.m_payload = payload;
    }

}
