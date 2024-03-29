package org.infinispan.jcache.annotation;

import java.io.Serializable;

import javax.cache.Cache;
import javax.cache.annotation.CacheKeyGenerator;
import javax.cache.annotation.CacheRemove;
import javax.cache.annotation.CacheResolver;
import javax.cache.annotation.GeneratedCacheKey;
import javax.interceptor.InvocationContext;

import org.infinispan.jcache.logging.Log;

/**
 * <p>{@link javax.cache.annotation.CacheRemove} interceptor implementation.This interceptor uses the following algorithm describes in
 * JSR-107.</p>
 *
 * <p>The interceptor that intercepts method annotated with {@code @CacheRemoveEntry} must do the following, generate a
 * key based on InvocationContext using the specified {@link javax.cache.annotation.CacheKeyGenerator}, use this key to remove the entry in the
 * cache. The remove occurs after the method body is executed. This can be overridden by specifying a afterInvocation
 * attribute value of false. If afterInvocation is true and the annotated method throws an exception the remove will not
 * happen.</p>
 *
 * @author Kevin Pollet &lt;kevin.pollet@serli.com&gt; (C) 2011 SERLI
 * @author Galder Zamarreño
 */
public abstract class AbstractCacheRemoveEntryInterceptor implements Serializable {
   private static final long serialVersionUID = -9079291622309963969L;

   private final CacheResolver defaultCacheResolver;
   private final CacheKeyInvocationContextFactory contextFactory;

   public AbstractCacheRemoveEntryInterceptor(CacheResolver defaultCacheResolver, CacheKeyInvocationContextFactory contextFactory) {
      this.defaultCacheResolver = defaultCacheResolver;
      this.contextFactory = contextFactory;
   }

   public Object cacheRemoveEntry(InvocationContext invocationContext) throws Exception {
      if (getLog().isTraceEnabled()) {
         getLog().tracef("Interception of method '%s.%s'",
                         invocationContext.getMethod().getDeclaringClass().getName(),
                         invocationContext.getMethod().getName());
      }

      final CacheKeyInvocationContextImpl<CacheRemove> cacheKeyInvocationContext = contextFactory.getCacheKeyInvocationContext(invocationContext);
      final CacheKeyGenerator cacheKeyGenerator = cacheKeyInvocationContext.getCacheKeyGenerator();
      CacheResolver cacheResolver = cacheKeyInvocationContext.getCacheResolver();
      if (cacheResolver == null) {
         cacheResolver = defaultCacheResolver;
      }
      final Cache<GeneratedCacheKey, Object> cache = cacheResolver.resolveCache(cacheKeyInvocationContext);
      final CacheRemove cacheRemoveEntry = cacheKeyInvocationContext.getCacheAnnotation();
      final GeneratedCacheKey cacheKey = cacheKeyGenerator.generateCacheKey(cacheKeyInvocationContext);

      if (!cacheRemoveEntry.afterInvocation()) {
         cache.remove(cacheKey);
         if (getLog().isTraceEnabled()) {
            getLog().tracef("Removed before invocation in cache '%s' key '%s'", cache.getName(), cacheKey);
         }
      }

      final Object result = invocationContext.proceed();

      if (cacheRemoveEntry.afterInvocation()) {
         cache.remove(cacheKey);
         if (getLog().isTraceEnabled()) {
            getLog().tracef("Removed after invocation in cache '%s' key '%s'", cache.getName(), cacheKey);
         }
      }

      return result;
   }

   protected abstract Log getLog();

}
