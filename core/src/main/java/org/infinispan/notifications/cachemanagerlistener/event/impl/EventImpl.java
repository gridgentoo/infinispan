package org.infinispan.notifications.cachemanagerlistener.event.impl;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.infinispan.commons.util.Util;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStartedEvent;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStoppedEvent;
import org.infinispan.notifications.cachemanagerlistener.event.ConfigurationChangedEvent;
import org.infinispan.notifications.cachemanagerlistener.event.MergeEvent;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;
import org.infinispan.remoting.transport.Address;

/**
 * Implementation of cache manager events
 *
 * @author Manik Surtani
 * @since 4.0
 */
public class EventImpl implements CacheStartedEvent, CacheStoppedEvent, ViewChangedEvent, MergeEvent, ConfigurationChangedEvent {

   private String cacheName;
   private EmbeddedCacheManager cacheManager;
   private Type type;
   private List<Address> newMembers, oldMembers;
   private Address localAddress;
   private int viewId;
   private List<List<Address>> subgroupsMerged;
   private boolean mergeView;
   private String configurationEntityType;
   private String configurationEntityName;
   private EventType configurationEventType;

   public EventImpl() {
   }

   public EventImpl(String cacheName, EmbeddedCacheManager cacheManager, Type type, List<Address> newMemberList, List<Address> oldMemberList, Address localAddress, int viewId) {
      this.cacheName = cacheName;
      this.cacheManager = cacheManager;
      this.type = type;
      this.newMembers = newMemberList;
      this.oldMembers = oldMemberList;
      this.localAddress = localAddress;
      this.viewId = viewId;
   }

   @Override
   public String getCacheName() {
      return cacheName;
   }

   public void setCacheName(String cacheName) {
      this.cacheName = cacheName;
   }

   @Override
   public EmbeddedCacheManager getCacheManager() {
      return cacheManager;
   }

   public void setCacheManager(EmbeddedCacheManager cacheManager) {
      this.cacheManager = cacheManager;
   }

   @Override
   public Type getType() {
      return type;
   }

   public void setType(Type type) {
      this.type = type;
   }

   @Override
   public List<Address> getNewMembers() {
      if (newMembers == null) {
         return Collections.emptyList();
      }
      return newMembers;
   }

   public void setNewMembers(List<Address> newMembers) {
      this.newMembers = newMembers;
   }

   public void setOldMembers(List<Address> oldMembers) {
      this.oldMembers = oldMembers;
   }

   @Override
   public List<Address> getOldMembers() {
      if (oldMembers == null) {
         return Collections.emptyList();
      }
      return this.oldMembers;
   }

   @Override
   public Address getLocalAddress() {
      return localAddress;
   }

   @Override
   public int getViewId() {
      return viewId;
   }

   public void setViewId(int viewId) {
      this.viewId = viewId;
   }

   public void setLocalAddress(Address localAddress) {
      this.localAddress = localAddress;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      EventImpl event = (EventImpl) o;

      if (viewId != event.viewId) return false;
      if (!Objects.equals(cacheName, event.cacheName)) return false;
      if (!Objects.equals(localAddress, event.localAddress)) return false;
      if (!Objects.equals(newMembers, event.newMembers)) return false;
      if (!Objects.equals(oldMembers, event.oldMembers)) return false;
      if (!Util.safeEquals(subgroupsMerged, event.subgroupsMerged)) return false;
      if (type != event.type) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = cacheName != null ? cacheName.hashCode() : 0;
      result = 31 * result + (type != null ? type.hashCode() : 0);
      result = 31 * result + (newMembers != null ? newMembers.hashCode() : 0);
      result = 31 * result + (oldMembers != null ? oldMembers.hashCode() : 0);
      result = 31 * result + (localAddress != null ? localAddress.hashCode() : 0);
      result = 31 * result + viewId;
      result = 31 * result + (subgroupsMerged == null ? 0 : subgroupsMerged.hashCode());
      result = 31 * result + (mergeView ? 1 : 0);
      return result;
   }

   @Override
   public String toString() {
      return "EventImpl{" +
            "type=" + type +
            ", newMembers=" + newMembers +
            ", oldMembers=" + oldMembers +
            ", localAddress=" + localAddress +
            ", viewId=" + viewId +
            ", subgroupsMerged=" + subgroupsMerged +
            ", mergeView=" + mergeView +
            '}';
   }

   public void setSubgroupsMerged(List<List<Address>> subgroupsMerged) {
      this.subgroupsMerged = subgroupsMerged;
   }

   @Override
   public List<List<Address>> getSubgroupsMerged() {
      return this.subgroupsMerged;
   }

   @Override
   public boolean isMergeView() {
      return mergeView;
   }

   public void setMergeView(boolean b) {
      mergeView = b;
   }

   public void setConfigurationEventType(EventType eventType) {
      configurationEventType = eventType;
   }

   public void setConfigurationEntityType(String type) {
      configurationEntityType = type;
   }

   public void setConfigurationEntityName(String name) {
      configurationEntityName = name;
   }

   @Override
   public EventType getConfigurationEventType() {
      return configurationEventType;
   }

   @Override
   public String getConfigurationEntityType() {
      return configurationEntityType;
   }

   @Override
   public String getConfigurationEntityName() {
      return configurationEntityName;
   }
}
