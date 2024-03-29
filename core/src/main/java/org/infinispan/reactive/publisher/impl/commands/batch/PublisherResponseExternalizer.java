package org.infinispan.reactive.publisher.impl.commands.batch;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.infinispan.commons.io.UnsignedNumeric;
import org.infinispan.commons.marshall.AbstractExternalizer;
import org.infinispan.commons.marshall.Ids;
import org.infinispan.commons.util.IntSet;
import org.infinispan.commons.util.Util;
import org.infinispan.reactive.publisher.impl.PublisherHandler;

public class PublisherResponseExternalizer extends AbstractExternalizer<PublisherResponse> {

   @Override
   public Integer getId() {
      return Ids.PUBLISHER_RESPONSE;
   }

   @Override
   public Set<Class<? extends PublisherResponse>> getTypeClasses() {
      return Util.asSet(PublisherResponse.class, KeyPublisherResponse.class);
   }

   @Override
   public void writeObject(ObjectOutput output, PublisherResponse object) throws IOException {
      output.writeObject(object.completedSegments);
      output.writeObject(object.lostSegments);
      output.writeBoolean(object.complete);

      UnsignedNumeric.writeUnsignedInt(output, object.segmentResults.size());
      for (PublisherHandler.SegmentResult result : object.segmentResults) {
         UnsignedNumeric.writeUnsignedInt(output, result.getSegment());
         UnsignedNumeric.writeUnsignedInt(output, result.getEntryCount());
      }

      if (object instanceof KeyPublisherResponse) {
         KeyPublisherResponse keyResponse = (KeyPublisherResponse) object;
         // Just send the combined count of both arrays - the read handles both the same way
         // segmentOffset for a KeyPublisherResponse is actually the extra value size
         UnsignedNumeric.writeUnsignedInt(output, keyResponse.size + keyResponse.extraSize);
         for (int i = 0; i < keyResponse.size; ++i) {
            output.writeObject(keyResponse.results[i]);
         }
         for (int i = 0; i < keyResponse.extraSize; ++i) {
            output.writeObject(keyResponse.extraObjects[i]);
         }

         output.writeBoolean(true);

         UnsignedNumeric.writeUnsignedInt(output, keyResponse.keySize);
         for (int i = 0; i < keyResponse.keySize; ++i) {
            output.writeObject(keyResponse.keys[i]);
         }
      } else {
         UnsignedNumeric.writeUnsignedInt(output, object.size);
         for (int i = 0; i < object.size; ++i) {
            output.writeObject(object.results[i]);
         }

         output.writeBoolean(false);
      }
   }

   @Override
   public PublisherResponse readObject(ObjectInput input) throws IOException, ClassNotFoundException {
      IntSet completedSegments = (IntSet) input.readObject();
      IntSet lostSegments = (IntSet) input.readObject();
      boolean complete = input.readBoolean();

      int segmentResultSize = UnsignedNumeric.readUnsignedInt(input);
      List<PublisherHandler.SegmentResult> segmentResults = new ArrayList<>(segmentResultSize);
      for (int i = 0; i < segmentResultSize; ++i) {
         int segment = UnsignedNumeric.readUnsignedInt(input);
         int entryCount = UnsignedNumeric.readUnsignedInt(input);
         segmentResults.add(new PublisherHandler.SegmentResult(segment, entryCount));
      }

      int size = UnsignedNumeric.readUnsignedInt(input);
      Object[] values = new Object[size];
      for (int i = 0; i < size; ++i) {
         values[i] = input.readObject();
      }

      boolean keyResponse = input.readBoolean();
      if (keyResponse) {
         int keySize = UnsignedNumeric.readUnsignedInt(input);
         Object[] keys = new Object[keySize];
         for (int i = 0; i < keySize; ++i) {
            keys[i] = input.readObject();
         }
         // All of the extra objects were just smashed into our values - so there is no separate extraObjects array
         return new KeyPublisherResponse(values, completedSegments, lostSegments, size, complete, segmentResults, null, 0, keys, keySize);
      } else {
         return new PublisherResponse(values, completedSegments, lostSegments, size, complete, segmentResults);
      }
   }
}
