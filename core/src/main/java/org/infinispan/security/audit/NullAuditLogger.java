package org.infinispan.security.audit;

import javax.security.auth.Subject;

import org.infinispan.security.AuditContext;
import org.infinispan.security.AuditLogger;
import org.infinispan.security.AuditResponse;
import org.infinispan.security.AuthorizationPermission;

/**
 * NullAuditLogger. A simple {@link AuditLogger} which drops all audit messages
 *
 * @author Tristan Tarrant
 * @since 7.0
 * @api.public
 */
public class NullAuditLogger implements AuditLogger {

   @Override
   public void audit(Subject subject, AuditContext context, String contextName, AuthorizationPermission permission,
         AuditResponse response) {
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof NullAuditLogger;
   }
}
