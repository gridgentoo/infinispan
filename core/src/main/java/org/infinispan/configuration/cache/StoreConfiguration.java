package org.infinispan.configuration.cache;

import java.util.Properties;

/**
 * StoreConfiguration contains generic configuration elements available to all the stores.
 *
 * @author Tristan Tarrant
 * @author Mircea Markus
 * @since 5.2
 */
public interface StoreConfiguration {

   /**
    * Configuration for the async cache loader. If enabled, this provides you with asynchronous
    * writes to the cache store, giving you 'write-behind' caching.
    */
   AsyncStoreConfiguration async();

   /**
    * If true, purges this cache store when it starts up.
    */
   boolean purgeOnStartup();

   /**
    * If true, fetch persistent state when joining a cluster. If multiple cache stores are chained,
    * only one of them can have this property enabled. Persistent state transfer with a shared cache
    * store does not make sense, as the same persistent store that provides the data will just end
    * up receiving it. Therefore, if a shared cache store is used, the cache will not allow a
    * persistent state transfer even if a cache store has this property set to true. Finally,
    * setting it to true only makes sense if in a clustered environment, and only 'replication' and
    * 'invalidation' cluster modes are supported.
    */
   boolean fetchPersistentState();

   /**
    * If true, any operation that modifies the cache (put, remove, clear, store...etc) won't be
    * applied to the cache store. This means that the cache store could become out of sync with the
    * cache.
    */
   boolean ignoreModifications();

   boolean writeOnly();

   boolean preload();

   boolean shared();

   boolean transactional();

   int maxBatchSize();

   /**
    * Whether or not this store is configured to be segmented. For a non shared store this means there will be a separate
    * instance of each store for each segment. For shared stores normally this means the store is able to do some
    * optimizations based on the segment (ie. select * from table where segment = $1)
    * @return whether this store is configured to be segmented
    */
   default boolean segmented() {
      return false;
   }

   Properties properties();
}
