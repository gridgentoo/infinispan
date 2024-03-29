package org.infinispan.persistence.remote;

/**
 * 1800 to 1899 range is reserved for this module
 *
 * @author gustavonalle
 * @since 8.2
 */
public interface ExternalizerIds {

   Integer MIGRATION_TASK = 1900;
   Integer REMOVED_FILTER = 1901;
   Integer DISCONNECT_REMOTE_STORE = 1902;
   Integer ENTRY_WRITER = 1903;
   Integer ADD_REMOTE_STORE = 1904;
   Integer CHECK_REMOTE_STORE = 1905;
}
