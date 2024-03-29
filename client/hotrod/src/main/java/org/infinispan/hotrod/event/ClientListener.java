package org.infinispan.hotrod.event;

/**
 * Annotation that marks a class to receive remote events from Hot Rod caches.
 * Classes with this annotation are expected to have at least one callback
 * annotated with one of the events it can receive:
 * {@link org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated},
 * {@link org.infinispan.client.hotrod.annotation.ClientCacheEntryModified},
 * {@link org.infinispan.client.hotrod.annotation.ClientCacheEntryRemoved},
 * {@link org.infinispan.client.hotrod.annotation.ClientCacheFailover}
 *
 * @author Galder Zamarreño
 */
public interface ClientListener {

   /**
    * Defines the key/value filter factory for this client listener. Filter
    * factories create filters that help decide which events should be sent
    * to this client listener. This helps with reducing traffic from server
    * to client. By default, no filtering is applied.
    */
   String filterFactoryName();

   /**
    * Defines the converter factory for this client listener. Converter
    * factories create event converters that customize the contents of the
    * events sent to this listener. When event customization is enabled,
    * {@link org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated},
    * {@link org.infinispan.client.hotrod.annotation.ClientCacheEntryModified},
    * and {@link org.infinispan.client.hotrod.annotation.ClientCacheEntryRemoved}
    * callbacks receive {@link org.infinispan.client.hotrod.event.ClientCacheEntryCustomEvent}
    * instances as parameters instead of their corresponding create/modified/removed
    * event. Event customization helps reduce the payload of events, or
    * increase to send even more information back to the client listener.
    * By default, no event customization is applied.
    */
   String converterFactoryName();

   /**
    * This option affects the type of the parameters received by a configured
    * filter and/or converter. If using raw data, filter and/or converter
    * classes receive raw binary arrays as parameters instead of unmarshalled
    * instances, which is the default. On top of that, when raw data is
    * enabled, custom events produced by the converters are expected to be
    * byte arrays. This option is useful when trying to avoid marshalling
    * costs involved in unmarshalling data to pass to filter/converter
    * callbacks or costs involved in marshalling custom event POJOs.
    * Using raw data also helps with potential classloading issues related to
    * loading callback parameter classes or custom event POJOs. By using raw
    * data, there's no need for class sharing between the server and client.
    * By default, using raw binary data for filter/converter callbacks is
    * disabled.
    */
   boolean useRawData();

   /**
    * This flag enables cached state to be sent back to remote clients when
    * either adding a cache listener for the first time, or when the node where
    * a remote listener is registered changes. When enabled, state is sent
    * back as cache entry created events to the clients. In the special case
    * that the node where the remote listener is registered changes, before
    * sending any cache entry created events, the client receives a failover
    * event so that it's aware of the change of node. This is useful in order
    * to do local clean up before receiving the state again. For example, a
    * client building a local near cache and keeping it up to date with remote
    * events might decide to clear in when the failover event is received and
    * before the state is received.
    *
    * If disabled, no state is sent back to the client when adding a listener,
    * nor it gets state when the node where the listener is registered changes.
    *
    * By default, including state is disabled in order to provide best
    * performance. If clients must receive all events, enable including state.
    */
   boolean includeCurrentState();

}
