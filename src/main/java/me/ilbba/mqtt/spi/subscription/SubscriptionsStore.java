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
package me.ilbba.mqtt.spi.subscription;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import me.ilbba.mqtt.spi.iface.ISessionsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;

import me.ilbba.mqtt.spi.iface.ISessionsStore.ClientTopicCouple;

/**
 * 订阅存储类,存储订阅的topic,即订阅树，订阅的通配符支持是此处的一个难点，</br>
 * 根据协议P41，也可以选择不支持通配符主题过滤器，若不支持通配符过滤器，则必须</br>
 * 拒绝含通配符过滤器的订阅请求
 *
 * Represents a tree of topics subscriptions.
 *
 * @author andrea
 */
public class SubscriptionsStore {

    public static class NodeCouple {
        final TreeNode root;
        final TreeNode createdNode;

        public NodeCouple(TreeNode root, TreeNode createdNode) {
            this.root = root;
            this.createdNode = createdNode;
        }
    }

    /**
     * Check if the topic filter of the subscription is well formed
     */
    public static boolean validate(String topicFilter) {
        try {
            parseTopic(topicFilter);
            return true;
        } catch (ParseException pex) {
            LOG.info("Bad matching topic filter <{}>", topicFilter);
            return false;
        }
    }

    public interface IVisitor<T> {
        void visit(TreeNode node, int deep);

        T getResult();
    }

    private class DumpTreeVisitor implements IVisitor<String> {

        String s = "";

        @Override
        public void visit(TreeNode node, int deep) {
            String subScriptionsStr = "";
            String indentTabs = indentTabs(deep);
            for (ClientTopicCouple couple : node.m_subscriptions) {
                subScriptionsStr += indentTabs + couple.toString() + "\n";
            }
            s += node.getToken() == null ? "" : node.getToken().toString();
            s += "\n" + (node.m_subscriptions.isEmpty() ? indentTabs : "") + subScriptionsStr /*+ "\n"*/;
        }

        private String indentTabs(int deep) {
            String s = "";
            for (int i = 0; i < deep; i++) {
                s += "\t";
//                s += "--";
            }
            return s;
        }

        @Override
        public String getResult() {
            return s;
        }
    }

    private AtomicReference<TreeNode> subscriptions = new AtomicReference<TreeNode>(new TreeNode());
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionsStore.class);
    private volatile ISessionsStore m_sessionsStore;

    /**
     * Initialize the subscription tree with the list of subscriptions.
     * Maintained for compatibility reasons.
     */
    public void init(ISessionsStore sessionsStore) {
        LOG.debug("init invoked");
        m_sessionsStore = sessionsStore;
        List<ClientTopicCouple> subscriptions = sessionsStore.listAllSubscriptions();
        //reload any subscriptions persisted
        if (LOG.isDebugEnabled()) {
            LOG.debug("Reloading all stored subscriptions...subscription tree before {}", dumpTree());
        }

        for (ClientTopicCouple clientTopic : subscriptions) {
            LOG.debug("Re-subscribing {} to topic {}", clientTopic.clientID, clientTopic.topicFilter);
            add(clientTopic);
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Finished loading. Subscription tree after {}", dumpTree());
        }
    }

    public void add(ClientTopicCouple newSubscription) {
        TreeNode oldRoot;
        NodeCouple couple;
        do {
            oldRoot = subscriptions.get();
            couple = recreatePath(newSubscription.topicFilter, oldRoot);
            couple.createdNode.addSubscription(newSubscription); //createdNode could be null?
            //spin lock repeating till we can, swap root, if can't swap just re-do the operation
        } while (!subscriptions.compareAndSet(oldRoot, couple.root));
        LOG.debug("root ref {}, original root was {}", couple.root, oldRoot);
    }


    protected NodeCouple recreatePath(String topic, final TreeNode oldRoot) {
        List<Token> tokens = Lists.newArrayList();
        try {
            tokens = parseTopic(topic);
        } catch (ParseException ex) {
            //TODO handle the parse exception
            LOG.error(null, ex);
        }

        final TreeNode newRoot = oldRoot.copy();
        TreeNode parent = newRoot;
        TreeNode current = newRoot;
        for (Token token : tokens) {
            TreeNode matchingChildren;

            //check if a children with the same token already exists
            if ((matchingChildren = current.childWithToken(token)) != null) {
                //copy the traversed node
                current = matchingChildren.copy();
                //update the child just added in the children list
                parent.updateChild(matchingChildren, current);
                parent = current;
            } else {
                //create a new node for the newly inserted token
                matchingChildren = new TreeNode();
                matchingChildren.setToken(token);
                current.addChild(matchingChildren);
                current = matchingChildren;
            }
        }
        return new NodeCouple(newRoot, current);
    }

    public void removeSubscription(String topic, String clientID) {
        TreeNode oldRoot;
        NodeCouple couple;
        do {
            oldRoot = subscriptions.get();
            couple = recreatePath(topic, oldRoot);

            couple.createdNode.remove(new ClientTopicCouple(clientID, topic));
            //spin lock repeating till we can, swap root, if can't swap just re-do the operation
        } while (!subscriptions.compareAndSet(oldRoot, couple.root));
    }

    /**
     * Visit the topics tree to remove matching subscriptions with clientID.
     * It's a mutating structure operation so create a new subscription tree (partial or total).
     */
    public void removeForClient(String clientID) {
        TreeNode oldRoot;
        TreeNode newRoot;
        do {
            oldRoot = subscriptions.get();
            newRoot = oldRoot.removeClientSubscriptions(clientID);
            //spin lock repeating till we can, swap root, if can't swap just re-do the operation
        } while (!subscriptions.compareAndSet(oldRoot, newRoot));
    }


    /**
     * Given a topic string return the clients subscriptions that matches it.
     * Topic string can't contain character # and + because they are reserved to
     * listeners subscriptions, and not topic publishing.
     */
    public List<Subscription> matches(String topic) {
        List<Token> tokens;
        try {
            tokens = parseTopic(topic);
        } catch (ParseException ex) {
            //TODO handle the parse exception
            LOG.error(null, ex);
            return Collections.emptyList();
        }

        Queue<Token> tokenQueue = new LinkedBlockingDeque<Token>(tokens);
        List<ClientTopicCouple> matchingSubs = Lists.newArrayList();
        subscriptions.get().matches(tokenQueue, matchingSubs);

        //remove the overlapping subscriptions, selecting ones with greatest qos
        Map<String, Subscription> subsForClient = Maps.newHashMap();
        for (ClientTopicCouple matchingCouple : matchingSubs) {
            Subscription existingSub = subsForClient.get(matchingCouple.clientID);
            Subscription sub = m_sessionsStore.getSubscription(matchingCouple);
            if (sub == null) {
                //if the m_sessionStore hasn't the sub because the client disconnected
                continue;
            }
            //update the selected subscriptions if not present or if has a greater qos
            if (existingSub == null || existingSub.getRequestedQos().value() < sub.getRequestedQos().value()) {
                subsForClient.put(matchingCouple.clientID, sub);
            }
        }
        return new ArrayList<Subscription>(subsForClient.values());
    }

    public boolean contains(Subscription sub) {
        return !matches(sub.topicFilter).isEmpty();
    }

    public int size() {
        return subscriptions.get().size();
    }

    public String dumpTree() {
        DumpTreeVisitor visitor = new DumpTreeVisitor();
        bfsVisit(subscriptions.get(), visitor, 0);
        return visitor.getResult();
    }

    private void bfsVisit(TreeNode node, IVisitor visitor, int deep) {
        if (node == null) {
            return;
        }
        visitor.visit(node, deep);
        for (TreeNode child : node.m_children) {
            bfsVisit(child, visitor, ++deep);
        }
    }

    /**
     * Verify if the 2 topics matching respecting the rules of MQTT Appendix A
     */
    //TODO reimplement with iterators or with queues
    public static boolean matchTopics(String msgTopic, String subscriptionTopic) {
        try {
            List<Token> msgTokens = SubscriptionsStore.parseTopic(msgTopic);
            List<Token> subscriptionTokens = SubscriptionsStore.parseTopic(subscriptionTopic);
            int i = 0;
            for (; i < subscriptionTokens.size(); i++) {
                Token subToken = subscriptionTokens.get(i);
                if (subToken != Token.MULTI && subToken != Token.SINGLE) {
                    if (i >= msgTokens.size()) {
                        return false;
                    }
                    Token msgToken = msgTokens.get(i);
                    if (!msgToken.equals(subToken)) {
                        return false;
                    }
                } else {
                    if (subToken == Token.MULTI) {
                        return true;
                    }
                    if (subToken == Token.SINGLE) {
                        //skip a step forward
                    }
                }
            }
            //if last token was a SINGLE then treat it as an empty
//            if (subToken == Token.SINGLE && (i - msgTokens.size() == 1)) {
//               i--;
//            }
            return i == msgTokens.size();
        } catch (ParseException ex) {
            LOG.error(null, ex);
            throw new RuntimeException(ex);
        }
    }

    protected static List<Token> parseTopic(String topic) throws ParseException {
        List<Token> res = Lists.newArrayList();
        String[] splitted = topic.split("/");

        if (splitted.length == 0) {
            res.add(Token.EMPTY);
        }

        if (topic.endsWith("/")) {
            //Add a fictious space 
            String[] newSplitted = new String[splitted.length + 1];
            System.arraycopy(splitted, 0, newSplitted, 0, splitted.length);
            newSplitted[splitted.length] = "";
            splitted = newSplitted;
        }

        for (int i = 0; i < splitted.length; i++) {
            String s = splitted[i];
            if (s.isEmpty()) {
//                if (i != 0) {
//                    throw new ParseException("Bad format of topic, expetec topic name between separators", i);
//                }
                res.add(Token.EMPTY);
            } else if (s.equals("#")) {
                //check that multi is the last symbol
                if (i != splitted.length - 1) {
                    throw new ParseException("Bad format of topic, the multi symbol (#) has to be the last one after a separator", i);
                }
                res.add(Token.MULTI);
            } else if (s.contains("#")) {
                throw new ParseException("Bad format of topic, invalid subtopic name: " + s, i);
            } else if (s.equals("+")) {
                res.add(Token.SINGLE);
            } else if (s.contains("+")) {
                throw new ParseException("Bad format of topic, invalid subtopic name: " + s, i);
            } else {
                res.add(new Token(s));
            }
        }

        return res;
    }
}
