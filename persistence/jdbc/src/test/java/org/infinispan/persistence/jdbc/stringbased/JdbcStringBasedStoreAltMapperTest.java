package org.infinispan.persistence.jdbc.stringbased;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.concurrent.CompletionStage;

import org.infinispan.commons.test.Exceptions;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.ch.impl.SingleSegmentKeyPartitioner;
import org.infinispan.marshall.TestObjectStreamMarshaller;
import org.infinispan.marshall.persistence.PersistenceMarshaller;
import org.infinispan.marshall.persistence.impl.MarshalledEntryUtil;
import org.infinispan.persistence.jdbc.UnitTestDatabaseManager;
import org.infinispan.persistence.jdbc.common.connectionfactory.ConnectionFactory;
import org.infinispan.persistence.jdbc.configuration.JdbcStringBasedStoreConfigurationBuilder;
import org.infinispan.persistence.jdbc.impl.table.TableManager;
import org.infinispan.persistence.jdbc.impl.table.TableName;
import org.infinispan.persistence.keymappers.UnsupportedKeyTypeException;
import org.infinispan.persistence.spi.PersistenceException;
import org.infinispan.persistence.support.WaitDelegatingNonBlockingStore;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.TestDataSCI;
import org.infinispan.test.data.Person;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.test.fwk.TestInternalCacheEntryFactory;
import org.infinispan.util.PersistenceMockUtil;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tester for {@link JdbcStringBasedStore} with an alternative {@link org.infinispan.persistence.keymappers.Key2StringMapper}.
 *
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "persistence.jdbc.stringbased.JdbcStringBasedStoreAltMapperTest")
public class JdbcStringBasedStoreAltMapperTest extends AbstractInfinispanTest {

   protected WaitDelegatingNonBlockingStore cacheStore;
   protected TableManager tableManager;
   protected static final Person MIRCEA = new Person("Mircea");
   protected static final Person MANIK = new Person("Manik");
   protected PersistenceMarshaller marshaller;

   protected JdbcStringBasedStoreConfigurationBuilder createJdbcConfig(ConfigurationBuilder builder) {
      JdbcStringBasedStoreConfigurationBuilder storeBuilder = builder
            .persistence()
            .addStore(JdbcStringBasedStoreConfigurationBuilder.class)
            .key2StringMapper(PersonKey2StringMapper.class);
      return storeBuilder;
   }

   @BeforeClass
   public void createCacheStore() throws PersistenceException {
      ConfigurationBuilder builder = TestCacheManagerFactory.getDefaultCacheConfiguration(false);
      JdbcStringBasedStoreConfigurationBuilder storeBuilder = createJdbcConfig(builder);

      UnitTestDatabaseManager.buildTableManipulation(storeBuilder.table());
      UnitTestDatabaseManager.configureUniqueConnectionFactory(storeBuilder);
      JdbcStringBasedStore jdbcStringBasedStore = new JdbcStringBasedStore();
      cacheStore = new WaitDelegatingNonBlockingStore(jdbcStringBasedStore, SingleSegmentKeyPartitioner.getInstance());
      marshaller = new TestObjectStreamMarshaller(TestDataSCI.INSTANCE);
      cacheStore.startAndWait(PersistenceMockUtil.createContext(getClass(), builder.build(), marshaller));
      tableManager = jdbcStringBasedStore.getTableManager();
   }

   @AfterMethod
   public void clearStore() {
      cacheStore.clear();
      assertRowCount(0);
   }

   @AfterClass
   public void destroyStore() throws PersistenceException {
      cacheStore.stop();
      marshaller.stop();
   }

   /**
    * When trying to persist an unsupported object an exception is expected.
    */
   public void persistUnsupportedObject() throws Exception {
      CompletionStage<Void> stage = cacheStore.write(0, MarshalledEntryUtil.create("key", "value", marshaller));
      Exceptions.expectCompletionException(UnsupportedKeyTypeException.class, stage);
      //just check that an person object will be persisted okay
      cacheStore.write(MarshalledEntryUtil.create(MIRCEA, "Cluj Napoca", marshaller));
   }


   public void testStoreLoadRemove() throws Exception {
      assertRowCount(0);
      assertNull("should not be present in the store", cacheStore.loadEntry(MIRCEA));
      String value = "adsdsadsa";
      cacheStore.write(MarshalledEntryUtil.create(MIRCEA, value, marshaller));
      assertRowCount(1);
      assertEquals(value, cacheStore.loadEntry(MIRCEA).getValue());
      assertFalse(cacheStore.delete(MANIK));
      assertEquals(value, cacheStore.loadEntry(MIRCEA).getValue());
      assertRowCount(1);
      assertTrue(cacheStore.delete(MIRCEA));
      assertRowCount(0);
   }


   public void testClear() throws Exception {
      assertRowCount(0);
      cacheStore.write(MarshalledEntryUtil.create(MIRCEA, "value", marshaller));
      cacheStore.write(MarshalledEntryUtil.create(MANIK, "value", marshaller));
      assertRowCount(2);
      cacheStore.clear();
      assertRowCount(0);
   }

   public void testPurgeExpired() throws Exception {
      InternalCacheEntry first = TestInternalCacheEntryFactory.create(MIRCEA, "val", 1000);
      InternalCacheEntry second = TestInternalCacheEntryFactory.create(MANIK, "val2");
      cacheStore.write(MarshalledEntryUtil.create(first, marshaller));
      cacheStore.write(MarshalledEntryUtil.create(second, marshaller));
      assertRowCount(2);
      Thread.sleep(1100);
      cacheStore.purge();
      assertRowCount(1);
      assertEquals("val2", cacheStore.loadEntry(MANIK).getValue());
   }

   protected int rowCount() {
      ConnectionFactory connectionFactory = getConnection();
      TableName tableName = tableManager.getDataTableName();
      return UnitTestDatabaseManager.rowCount(connectionFactory, tableName);
   }

   protected ConnectionFactory getConnection() {
      JdbcStringBasedStore store = (JdbcStringBasedStore) cacheStore.delegate();
      return store.getConnectionFactory();
   }

   protected void assertRowCount(int size) {
      assertEquals(size, rowCount());
   }
}
