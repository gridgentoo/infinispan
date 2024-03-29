package org.infinispan.hotrod.exceptions;

/**
 * When try to create a transactional {@code org.infinispan.hotrod.RemoteCache} but the cache in the Hot Rod
 * server isn't transactional, this exception is thrown.
 *
 * Check if the cache is properly configured in the server configuration.
 *
 * @since 14.0
 */
public class CacheNotTransactionalException extends HotRodClientException {
   public CacheNotTransactionalException() {
   }

   public CacheNotTransactionalException(String message) {
      super(message);
   }

   public CacheNotTransactionalException(Throwable cause) {
      super(cause);
   }

   public CacheNotTransactionalException(String message, Throwable cause) {
      super(message, cause);
   }

   public CacheNotTransactionalException(String remoteMessage, long messageId, int errorStatusCode) {
      super(remoteMessage, messageId, errorStatusCode);
   }
}
