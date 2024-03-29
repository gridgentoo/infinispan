package org.infinispan.server.hotrod;

import static org.infinispan.server.hotrod.test.HotRodTestingUtil.host;
import static org.infinispan.server.hotrod.test.HotRodTestingUtil.serverPort;
import static org.infinispan.server.hotrod.test.HotRodTestingUtil.startHotRodServer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.infinispan.commons.util.SslContextFactory;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.server.core.configuration.SslConfiguration;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;
import org.infinispan.server.hotrod.test.HotRodClient;
import org.testng.annotations.Test;

/**
 * Hot Rod server functional test over SSL
 *
 * @author Tristan Tarrant
 * @since 5.3
 */
@Test(groups = "functional", testName = "server.hotrod.HotRodSslFunctionalTest")
public class HotRodSslFunctionalTest extends HotRodFunctionalTest {

   private final String keyStoreFileName = getClass().getClassLoader().getResource("default_server_keystore.p12").getPath();
   private final String trustStoreFileName = getClass().getClassLoader().getResource("default_client_truststore.p12").getPath();

   @Override
   protected HotRodServer createStartHotRodServer(EmbeddedCacheManager cacheManager) {
      HotRodServerConfigurationBuilder builder = new HotRodServerConfigurationBuilder();
      builder.proxyHost(host()).proxyPort(serverPort()).idleTimeout(0);
      builder.ssl().enable()
            .keyStoreFileName(keyStoreFileName)
            .keyStorePassword("secret".toCharArray())
            .keyStoreType("pkcs12")
            .trustStoreFileName(trustStoreFileName)
            .trustStorePassword("secret".toCharArray())
            .trustStoreType("pkcs12");
      return startHotRodServer(cacheManager, serverPort(), builder);
   }

   @Override
   protected HotRodClient connectClient(byte protocolVersion) {
      SslConfiguration ssl = hotRodServer.getConfiguration().ssl();
      SSLContext sslContext = new SslContextFactory()
            .keyStoreFileName(ssl.keyStoreFileName())
            .keyStorePassword(ssl.keyStorePassword())
            .keyStoreType("pkcs12")
            .trustStoreFileName(ssl.trustStoreFileName())
            .trustStorePassword(ssl.trustStorePassword())
            .trustStoreType("pkcs12")
            .getContext();
      SSLEngine sslEngine = SslContextFactory.getEngine(sslContext, true, false);
      return new HotRodClient(hotRodServer.getHost(), hotRodServer.getPort(), cacheName,
                              HotRodClient.DEFAULT_TIMEOUT_SECONDS, protocolVersion, sslEngine);
   }
}
