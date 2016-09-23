package me.ilbba.mqtt.spi.impl;

import me.ilbba.mqtt.Const;
import me.ilbba.mqtt.spi.iface.ISslContextCreator;
import me.ilbba.mqtt.util.Prop;
import me.ilbba.mqtt.util.PropKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;

/**
 * Created by liangbo on 16/9/18.
 */
public class DefaultSslContextCreator implements ISslContextCreator {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSslContextCreator.class);

    private static final Prop props = PropKit.use("config.properties", "utf-8");

    @Override
    public SSLContext initSSLContext() {
        final String jksPath = props.get(Const.JKS_PATH_PROPERTY_NAME);
        logger.info("Starting SSL using keystore at {}", jksPath);
        if (jksPath == null || jksPath.isEmpty()) {
            //key_store_password or key_manager_password are empty
            logger.warn("You have configured the SSL port but not the jks_path, SSL not started");
            return null;
        }

        //if we have the port also the jks then keyStorePassword and keyManagerPassword
        //has to be defined
        final String keyStorePassword = props.get(Const.KEY_STORE_PASSWORD_PROPERTY_NAME);
        final String keyManagerPassword = props.get(Const.KEY_MANAGER_PASSWORD_PROPERTY_NAME);
        if (keyStorePassword == null || keyStorePassword.isEmpty()) {
            //key_store_password or key_manager_password are empty
            logger.warn("You have configured the SSL port but not the key_store_password, SSL not started");
            return null;
        }
        if (keyManagerPassword == null || keyManagerPassword.isEmpty()) {
            //key_manager_password or key_manager_password are empty
            logger.warn("You have configured the SSL port but not the key_manager_password, SSL not started");
            return null;
        }

        // if client authentification is enabled a trustmanager needs to be
        // added to the ServerContext
        String sNeedsClientAuth = props.get(Const.NEED_CLIENT_AUTH, "false");
        boolean needsClientAuth = Boolean.valueOf(sNeedsClientAuth);

        try {
            InputStream jksInputStream = jksDatastore(jksPath);
            SSLContext serverContext = SSLContext.getInstance("TLS");
            final KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(jksInputStream, keyStorePassword.toCharArray());
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyManagerPassword.toCharArray());
            TrustManager[] trustManagers = null;
            if (needsClientAuth) {
                // use keystore as truststore, as server needs to trust certificates signed by the server certificates
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);
                trustManagers = tmf.getTrustManagers();
            }
            // init sslContext
            serverContext.init(kmf.getKeyManagers(), trustManagers, null);

            return serverContext;
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException | KeyStoreException
                | KeyManagementException | IOException ex) {
            logger.error("Can't start SSL layer!", ex);
            return null;
        }
    }

    private InputStream jksDatastore(String jksPath) throws FileNotFoundException {
        URL jksUrl = getClass().getClassLoader().getResource(jksPath);
        if (jksUrl != null) {
            logger.info("Starting with jks at {}, jks normal {}", jksUrl.toExternalForm(), jksUrl);
            return getClass().getClassLoader().getResourceAsStream(jksPath);
        }
        logger.info("jks not found in bundled resources, try on the filesystem");
        File jksFile = new File(jksPath);
        if (jksFile.exists()) {
            logger.info("Using {} ", jksFile.getAbsolutePath());
            return new FileInputStream(jksFile);
        }
        logger.warn("File {} doesn't exists", jksFile.getAbsolutePath());
        return null;
    }
}
