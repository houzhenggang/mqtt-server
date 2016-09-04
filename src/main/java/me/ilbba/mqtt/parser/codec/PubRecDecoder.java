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
package me.ilbba.mqtt.parser.codec;

import me.ilbba.mqtt.protocol.message.MessageIDMessage;
import me.ilbba.mqtt.protocol.message.PubRecMessage;

/**
 * @author andrea
 */
public class PubRecDecoder extends MessageIDDecoder {

    @Override
    public MessageIDMessage createMessage() {
        return new PubRecMessage();
    }
}
