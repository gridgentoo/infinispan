package org.infinispan.jmx;

import static org.infinispan.test.TestingUtil.getCacheManagerObjectName;

import java.io.Serializable;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.infinispan.Cache;
import org.infinispan.commons.jmx.MBeanServerLookup;
import org.infinispan.commons.jmx.TestMBeanServerLookup;
import org.infinispan.commons.time.TimeService;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.CacheContainer;
import org.infinispan.stats.CacheContainerStats;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.test.fwk.TransportFlags;
import org.infinispan.util.ControlledTimeService;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "jmx.CacheContainerStatsMBeanTest")
public class CacheContainerStatsMBeanTest extends MultipleCacheManagersTest {

   private final String cachename = CacheContainerStatsMBeanTest.class.getName();
   private final String cachename2 = cachename + "2";
   private static final String JMX_DOMAIN = CacheContainerStatsMBeanTest.class.getSimpleName();

   private final MBeanServerLookup mBeanServerLookup = TestMBeanServerLookup.create();

   private ControlledTimeService timeService;

   @Override
   protected void createCacheManagers() throws Throwable {
      timeService = new ControlledTimeService();
      ConfigurationBuilder defaultConfig = new ConfigurationBuilder();
      GlobalConfigurationBuilder gcb1 = GlobalConfigurationBuilder.defaultClusteredBuilder();
      gcb1.cacheContainer().statistics(true)
          .jmx().enabled(true).domain(JMX_DOMAIN).mBeanServerLookup(mBeanServerLookup)
            .metrics().accurateSize(true);
      CacheContainer cacheManager1 = TestCacheManagerFactory.createClusteredCacheManager(gcb1, defaultConfig,
            new TransportFlags());
      TestingUtil.replaceComponent(cacheManager1, TimeService.class, timeService, true);
      cacheManager1.start();

      GlobalConfigurationBuilder gcb2 = GlobalConfigurationBuilder.defaultClusteredBuilder();
      gcb2.cacheContainer().statistics(true)
          .jmx().enabled(true).domain(JMX_DOMAIN + 2).mBeanServerLookup(mBeanServerLookup);
      CacheContainer cacheManager2 = TestCacheManagerFactory.createClusteredCacheManager(gcb2, defaultConfig,
            new TransportFlags());
      TestingUtil.replaceComponent(cacheManager2, TimeService.class, timeService, true);
      cacheManager2.start();

      registerCacheManager(cacheManager1, cacheManager2);

      ConfigurationBuilder cb = new ConfigurationBuilder();
      cb.clustering().cacheMode(CacheMode.REPL_SYNC).statistics().enable();
      defineConfigurationOnAllManagers(cachename, cb);
      defineConfigurationOnAllManagers(cachename2, cb);
      waitForClusterToForm(cachename);
   }

   public void testClusterStats() throws Exception {
      Cache<String, Serializable> cache1 = manager(0).getCache(cachename);
      MBeanServer mBeanServer = mBeanServerLookup.getMBeanServer();
      ObjectName nodeStats = getCacheManagerObjectName(JMX_DOMAIN, "DefaultCacheManager",
            CacheContainerStats.OBJECT_NAME);
      mBeanServer.setAttribute(nodeStats, new Attribute("StatisticsEnabled", Boolean.TRUE));

      cache1.put("a1", "b1");
      cache1.put("a2", "b2");
      cache1.put("a3", "b3");
      cache1.put("a4", "b4");

      assertAttributeValue(mBeanServer, nodeStats, "NumberOfEntries", 4);
      assertAttributeValue(mBeanServer, nodeStats, "Stores", 4);
      assertAttributeValue(mBeanServer, nodeStats, "Evictions", 0);
      assertAttributeValueGreaterThanOrEqual(mBeanServer, nodeStats, "AverageWriteTime", 0);

      cache1.remove("a1");

      // Advance 1 second for the cached stats to expire
      timeService.advance(1000);

      assertAttributeValue(mBeanServer, nodeStats, "RemoveHits", 1);

      Cache<String, Serializable> cache3 = manager(0).getCache(cachename2);
      cache3.put("a10", "b1");
      cache3.put("a11", "b2");
      cache3.put("a12", "b3");
      cache3.put("a13", "b4");

      // Advance 1 second for the cached stats to expire
      timeService.advance(1000);

      assertAttributeValue(mBeanServer, nodeStats, "NumberOfEntries", 7);
      assertAttributeValue(mBeanServer, nodeStats, "Stores", 8);
      assertAttributeValue(mBeanServer, nodeStats, "Evictions", 0);
      assertAttributeValueGreaterThanOrEqual(mBeanServer, nodeStats, "AverageWriteTime", 0);
   }

   public void testClusterStatsDisabled() throws Exception {
      MBeanServer mBeanServer = mBeanServerLookup.getMBeanServer();
      ObjectName nodeStats = getCacheManagerObjectName(JMX_DOMAIN, "DefaultCacheManager",
            CacheContainerStats.OBJECT_NAME);
      mBeanServer.setAttribute(nodeStats, new Attribute("StatisticsEnabled", Boolean.FALSE));

      assertAttributeValue(mBeanServer, nodeStats, "NumberOfEntries", -1);
      assertAttributeValue(mBeanServer, nodeStats, "AverageReadTime", -1);
      assertAttributeValue(mBeanServer, nodeStats, "AverageRemoveTime", -1);
      assertAttributeValue(mBeanServer, nodeStats, "AverageWriteTime", -1);
      assertAttributeValue(mBeanServer, nodeStats, "Stores", -1);
      assertAttributeValue(mBeanServer, nodeStats, "Evictions", -1);
      assertAttributeValue(mBeanServer, nodeStats, "Hits", -1);
      assertAttributeValue(mBeanServer, nodeStats, "Misses", -1);
      assertAttributeValue(mBeanServer, nodeStats, "RemoveHits", -1);
   }

   private void assertAttributeValue(MBeanServer mBeanServer, ObjectName oName, String attrName, long expectedValue)
         throws Exception {
      String receivedVal = mBeanServer.getAttribute(oName, attrName).toString();
      assert Long.parseLong(receivedVal) == expectedValue : "expecting " + expectedValue + " for " + attrName
            + ", but received " + receivedVal;
   }

   private void assertAttributeValueGreaterThanOrEqual(MBeanServer mBeanServer, ObjectName oName, String attrName,
         long valueToCompare) throws Exception {
      String receivedVal = mBeanServer.getAttribute(oName, attrName).toString();
      assert Long.parseLong(receivedVal) >= valueToCompare : "expecting " + receivedVal + " for " + attrName
            + ", to be greater than" + valueToCompare;
   }
}
