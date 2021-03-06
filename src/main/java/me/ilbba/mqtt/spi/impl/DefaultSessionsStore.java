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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import me.ilbba.mqtt.spi.iface.IMessagesStore;
import me.ilbba.mqtt.spi.iface.ISessionsStore;
import me.ilbba.mqtt.spi.subscription.Subscription;
import me.ilbba.mqtt.util.MapUtils;
import org.mapdb.DB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * ISessionsStore implementation backed by MapDB.
 *
 * @author andrea
 */
class DefaultSessionsStore implements ISessionsStore {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSessionsStore.class);

    //maps clientID->[MessageId -> guid]
    private ConcurrentMap<String, Map<Integer, String>> m_inflightStore;
    //map clientID <-> set of currently in flight packet identifiers
    private Map<String, Set<Integer>> m_inFlightIds;
    private ConcurrentMap<String, DefaultPersistentStore.PersistentSession> m_persistentSessions;
    //maps clientID->[guid*], insertion order cares, it's queue
    private ConcurrentMap<String, List<String>> m_enqueuedStore;
    //maps clientID->[messageID*]
    private ConcurrentMap<String, Set<Integer>> m_secondPhaseStore;

    private final DB m_db;
    private final IMessagesStore m_messagesStore;

    DefaultSessionsStore(DB db, IMessagesStore messagesStore) {
        m_db = db;
        m_messagesStore = messagesStore;
    }

    @Override
    public void initStore() {
        m_inflightStore = m_db.getHashMap("inflight");
        m_inFlightIds = m_db.getHashMap("inflightPacketIDs");
        m_persistentSessions = m_db.getHashMap("sessions");
        m_enqueuedStore = m_db.getHashMap("sessionQueue");
        m_secondPhaseStore = m_db.getHashMap("secondPhase");
    }

    @Override
    public void addNewSubscription(Subscription newSubscription) {
        logger.debug("addNewSubscription invoked with subscription {}", newSubscription);
        final String clientID = newSubscription.getClientId();
        m_db.getHashMap("subscriptions_" + clientID).put(newSubscription.getTopicFilter(), newSubscription);

        if (logger.isTraceEnabled()) {
            logger.trace("subscriptions_{}: {}", clientID, m_db.getHashMap("subscriptions_" + clientID));
        }
    }

    @Override
    public void removeSubscription(String topicFilter, String clientID) {
        logger.debug("removeSubscription topic filter: {} for clientID: {}", topicFilter, clientID);
        if (!m_db.exists("subscriptions_" + clientID)) {
            return;
        }
        m_db.getHashMap("subscriptions_" + clientID).remove(topicFilter);
    }

    @Override
    public void wipeSubscriptions(String clientID) {
        logger.debug("wipeSubscriptions");
        if (logger.isTraceEnabled()) {
            logger.trace("Subscription pre wipe: subscriptions_{}: {}", clientID, m_db.getHashMap("subscriptions_" + clientID));
        }
        m_db.delete("subscriptions_" + clientID);
        if (logger.isTraceEnabled()) {
            logger.trace("Subscription post wipe: subscriptions_{}: {}", clientID, m_db.getHashMap("subscriptions_" + clientID));
        }
    }

    @Override
    public List<ClientTopicCouple> listAllSubscriptions() {
        final List<ClientTopicCouple> allSubscriptions = Lists.newArrayList();
        for (String clientID : m_persistentSessions.keySet()) {
            ConcurrentMap<String, Subscription> clientSubscriptions = m_db.getHashMap("subscriptions_" + clientID);
            for (String topicFilter : clientSubscriptions.keySet()) {
                allSubscriptions.add(new ClientTopicCouple(clientID, topicFilter));
            }
        }
        logger.debug("retrieveAllSubscriptions returning subs {}", allSubscriptions);
        return allSubscriptions;
    }

    @Override
    public Subscription getSubscription(ClientTopicCouple couple) {
        ConcurrentMap<String, Subscription> clientSubscriptions = m_db.getHashMap("subscriptions_" + couple.clientID);
        logger.debug("subscriptions_{}: {}", couple.clientID, clientSubscriptions);
        return clientSubscriptions.get(couple.topicFilter);
    }

    @Override
    public boolean contains(String clientID) {
        return m_db.exists("subscriptions_" + clientID);
    }

//    @Override
//    public ClientSession createNewSession(String clientID, boolean cleanSession) {
//        logger.debug("createNewSession for client <{}> with clean flag <{}>", clientID, cleanSession);
//        if (m_persistentSessions.containsKey(clientID)) {
//            logger.error("already exists a session for client <{}>, bad condition", clientID);
//            throw new IllegalArgumentException("Can't create a session with the ID of an already existing" + clientID);
//        }
//        logger.debug("clientID {} is a newcome, creating it's empty subscriptions set", clientID);
//        m_persistentSessions.putIfAbsent(clientID, new DefaultPersistentStore.PersistentSession(cleanSession));
//        return new ClientSession(clientID, m_messagesStore, this, cleanSession);
//    }
//
//    @Override
//    public ClientSession sessionForClient(String clientID) {
//        if (!m_persistentSessions.containsKey(clientID)) {
//            return null;
//        }
//
//        DefaultPersistentStore.PersistentSession storedSession = m_persistentSessions.get(clientID);
//        return new ClientSession(clientID, m_messagesStore, this, storedSession.cleanSession);
//    }

    @Override
    public void updateCleanStatus(String clientID, boolean cleanSession) {
        m_persistentSessions.put(clientID, new DefaultPersistentStore.PersistentSession(cleanSession));
    }

    /**
     * Return the next valid packetIdentifier for the given client session.
     */
    @Override
    public int nextPacketID(String clientID) {
        Set<Integer> inFlightForClient = this.m_inFlightIds.get(clientID);
        if (inFlightForClient == null) {
            int nextPacketId = 1;
            inFlightForClient = new HashSet<Integer>();
            inFlightForClient.add(nextPacketId);
            this.m_inFlightIds.put(clientID, inFlightForClient);
            return nextPacketId;

        }

        int maxId = inFlightForClient.isEmpty() ? 0 : Collections.max(inFlightForClient);
        int nextPacketId = (maxId + 1) % 0xFFFF;
        inFlightForClient.add(nextPacketId);
        return nextPacketId;
    }

    @Override
    public void inFlightAck(String clientID, int messageID) {
        Map<Integer, String> m = this.m_inflightStore.get(clientID);
        if (m == null) {
            logger.error("Can't find the inFlight record for client <{}>", clientID);
            return;
        }
        m.remove(messageID);

        //remove from the ids store
        Set<Integer> inFlightForClient = this.m_inFlightIds.get(clientID);
        if (inFlightForClient != null) {
            inFlightForClient.remove(messageID);
        }
    }

    @Override
    public void inFlight(String clientID, int messageID, String guid) {
        Map<Integer, String> m = this.m_inflightStore.get(clientID);
        if (m == null) {
            m = Maps.newHashMap();
        }
        m.put(messageID, guid);
        this.m_inflightStore.put(clientID, m);
    }

    @Override
    public void bindToDeliver(String guid, String clientID) {
        List<String> guids = MapUtils.defaultGet(m_enqueuedStore, clientID, new ArrayList<String>());
        guids.add(guid);
        m_enqueuedStore.put(clientID, guids);
    }

    @Override
    public Collection<String> enqueued(String clientID) {
        return MapUtils.defaultGet(m_enqueuedStore, clientID, new ArrayList<String>());
    }

    @Override
    public void removeEnqueued(String clientID, String guid) {
        List<String> guids = MapUtils.defaultGet(m_enqueuedStore, clientID, new ArrayList<String>());
        guids.remove(guid);
        m_enqueuedStore.put(clientID, guids);
    }

    @Override
    public void secondPhaseAcknowledged(String clientID, int messageID) {
        Set<Integer> messageIDs = MapUtils.defaultGet(m_secondPhaseStore, clientID, new HashSet<Integer>());
        messageIDs.remove(messageID);
        m_secondPhaseStore.put(clientID, messageIDs);
    }

    @Override
    public void secondPhaseAckWaiting(String clientID, int messageID) {
        Set<Integer> messageIDs = MapUtils.defaultGet(m_secondPhaseStore, clientID, new HashSet<Integer>());
        messageIDs.add(messageID);
        m_secondPhaseStore.put(clientID, messageIDs);
    }

    @Override
    public String mapToGuid(String clientID, int messageID) {
        ConcurrentMap<Integer, String> messageIdToGuid = m_db.getHashMap(messageId2GuidsMapName(clientID));
        return messageIdToGuid.get(messageID);
    }

    static String messageId2GuidsMapName(String clientID) {
        return "guidsMapping_" + clientID;
    }
}
