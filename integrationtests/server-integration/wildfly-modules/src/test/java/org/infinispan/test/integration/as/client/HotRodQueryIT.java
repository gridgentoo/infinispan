package org.infinispan.test.integration.as.client;

import java.io.IOException;

import org.infinispan.commons.util.Version;
import org.infinispan.test.integration.data.Book;
import org.infinispan.test.integration.data.Person;
import org.infinispan.test.integration.remote.AbstractHotRodQueryIT;
import org.infinispan.test.integration.remote.proto.BookQuerySchema;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.spec.se.manifest.ManifestDescriptor;
import org.junit.runner.RunWith;

/**
 * Test remote query.
 *
 * @author anistor@redhat.com
 * @since 8.2
 */
@RunWith(Arquillian.class)
public class HotRodQueryIT extends AbstractHotRodQueryIT {

   @Deployment
   public static Archive<?> deployment() throws IOException {
      return ShrinkWrap
            .create(WebArchive.class, "remote-query.war")
            .addClasses(HotRodQueryIT.class, AbstractHotRodQueryIT.class, Person.class, Book.class)
            .addPackage(BookQuerySchema.class.getPackage().getName())
            .addAsResource("proto/book.proto")
            .add(manifest(), "META-INF/MANIFEST.MF");
   }

   private static Asset manifest() {
      String manifest = Descriptors.create(ManifestDescriptor.class)
            .attribute("Dependencies", "org.infinispan:" + Version.getModuleSlot() + " services")
            .exportAsString();
      return new StringAsset(manifest);
   }

}
