package org.infinispan.jcache.annotation;

import java.io.Serializable;

import javax.cache.Cache;
import javax.cache.annotation.CacheRemoveAll;
import javax.cache.annotation.CacheResolver;
import javax.cache.annotation.GeneratedCacheKey;
import javax.interceptor.InvocationContext;

import org.infinispan.jcache.logging.Log;

/**
 * <p>{@link javax.cache.annotation.CacheRemoveAll} interceptor implementation. This interceptor uses the following algorithm describes in
 * JSR-107.</p>
 *
 * <p>The interceptor that intercepts method annotated with {@code @CacheRemoveAll} must do the following, remove all
 * entries associated with the cache. The removeAll occurs after the method body is executed. This can be overridden by
 * specifying a afterInvocation attribute value of false. If afterInvocation is true and the annotated method throws an
 * exception, the removeAll will not happen.</p>
 *
 * @author Kevin Pollet &lt;kevin.pollet@serli.com&gt; (C) 2011 SERLI
 */
public abstract class AbstractCacheRemoveAllInterceptor implements Serializable {
   private static final long serialVersionUID = -8763819640664021763L;

   private final CacheResolver defaultCacheResolver;
   private final CacheKeyInvocationContextFactory contextFactory;

   public AbstractCacheRemoveAllInterceptor(CacheResolver defaultCacheResolver, CacheKeyInvocationContextFactory contextFactory) {
      this.defaultCacheResolver = defaultCacheResolver;
      this.contextFactory = contextFactory;
   }

   public Object cacheRemoveAll(InvocationContext invocationContext) throws Exception {
      if (getLog().isTraceEnabled()) {
         getLog().tracef("Interception of method named '%s'", invocationContext.getMethod().getName());
      }

      final CacheKeyInvocationContextImpl<CacheRemoveAll> cacheKeyInvocationContext =
            contextFactory.getCacheKeyInvocationContext(invocationContext);
      final CacheRemoveAll cacheRemoveAll = cacheKeyInvocationContext.getCacheAnnotation();
      CacheResolver cacheResolver = cacheKeyInvocationContext.getCacheResolver();
      if (cacheResolver == null) {
         cacheResolver = defaultCacheResolver;
      }
      final Cache<GeneratedCacheKey, Object> cache = cacheResolver.resolveCache(cacheKeyInvocationContext);

      if (!cacheRemoveAll.afterInvocation()) {
         cache.clear();
         if (getLog().isTraceEnabled()) {
            getLog().tracef("Clear cache '%s' before method invocation", cache.getName());
         }
      }

      final Object result = invocationContext.proceed();

      if (cacheRemoveAll.afterInvocation()) {
         cache.clear();
         if (getLog().isTraceEnabled()) {
            getLog().tracef("Clear cache '%s' after method invocation", cache.getName());
         }
      }

      return result;
   }

   protected abstract Log getLog();

}
