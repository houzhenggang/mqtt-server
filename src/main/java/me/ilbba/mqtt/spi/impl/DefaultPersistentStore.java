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

package me.ilbba.mqtt.spi.impl;

import me.ilbba.mqtt.spi.iface.IMessagesStore;
import me.ilbba.mqtt.spi.iface.ISessionsStore;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MapDB main persistence implementation
 */
public class DefaultPersistentStore {

    /**
     * This is a DTO used to persist minimal status (clean session and activation status) of
     * a session.
     */
    public static class PersistentSession implements Serializable {
        public final boolean cleanSession;

        public PersistentSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPersistentStore.class);

    private DB m_db;
    private final String m_storePath;
    private final int m_autosaveInterval; // in seconds

    protected final ScheduledExecutorService m_scheduler = Executors.newScheduledThreadPool(1);


    public DefaultPersistentStore() {
        this.m_storePath = System.getProperty("user.dir") + File.separator + "persistent_store";
        this.m_autosaveInterval = 30;
    }

    /**
     * Factory method to create message store backed by MapDB
     */
//    public IMessagesStore messagesStore() {
//        //TODO check m_db is valid and
//        IMessagesStore msgStore = new MapDBMessagesStore(m_db);
//        msgStore.initStore();
//        return msgStore;
//    }

    public ISessionsStore sessionsStore(IMessagesStore msgStore) {
        ISessionsStore sessionsStore = new DefaultSessionsStore(m_db, msgStore);
        sessionsStore.initStore();
        return sessionsStore;
    }

    public void initStore() {
        if (m_storePath == null || m_storePath.length() == 0) {
            m_db = DBMaker.newMemoryDB().make();
        } else {
            File tmpFile;
            try {
                tmpFile = new File(m_storePath);
                boolean fileNewlyCreated = tmpFile.createNewFile();
                LOG.info("Starting with {} [{}] db file", fileNewlyCreated ? "fresh" : "existing", m_storePath);
            } catch (IOException ex) {
                LOG.error(null, ex);
                throw new RuntimeException("Can't create temp file for subscriptions storage [" + m_storePath + "]", ex);
            }
            m_db = DBMaker.newFileDB(tmpFile).make();
        }
        m_scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                m_db.commit();
            }
        }, this.m_autosaveInterval, this.m_autosaveInterval, TimeUnit.SECONDS);
    }

    public void close() {
        if (this.m_db.isClosed()) {
            LOG.debug("already closed");
            return;
        }
        this.m_db.commit();
        //LOG.debug("persisted subscriptions {}", m_persistentSubscriptions);
        this.m_db.close();
        LOG.debug("closed disk storage");
        this.m_scheduler.shutdown();
        LOG.debug("Persistence commit scheduler is shutdown");
    }

    public static void main(String[] args) {
        DefaultPersistentStore persistentStore = new DefaultPersistentStore();
        persistentStore.initStore();


    }
}
