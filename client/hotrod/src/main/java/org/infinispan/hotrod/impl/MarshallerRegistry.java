package org.infinispan.hotrod.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.infinispan.hotrod.impl.logging.Log;
import org.infinispan.hotrod.impl.logging.LogFactory;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.marshall.Marshaller;

/**
 * A registry of {@link Marshaller} along with its {@link MediaType}.
 *
 * @since 14.0
 */
public final class MarshallerRegistry {

   public static final Log log = LogFactory.getLog(MarshallerRegistry.class, Log.class);

   private final Map<MediaType, Marshaller> marshallerByMediaType = new ConcurrentHashMap<>();

   public void registerMarshaller(Marshaller marshaller) {
      marshallerByMediaType.put(marshaller.mediaType().withoutParameters(), marshaller);
   }

   public Marshaller getMarshaller(MediaType mediaType) {
      return marshallerByMediaType.get(mediaType);
   }

   public Marshaller getMarshaller(Class<? extends Marshaller> marshallerClass) {
      return marshallerByMediaType.values().stream()
            .filter(m -> m.getClass().equals(marshallerClass)).findFirst().orElse(null);
   }

}
