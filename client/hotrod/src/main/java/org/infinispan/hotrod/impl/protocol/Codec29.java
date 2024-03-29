package org.infinispan.hotrod.impl.protocol;

import org.infinispan.hotrod.impl.operations.PingResponse;
import org.infinispan.commons.dataconversion.MediaType;

import io.netty.buffer.ByteBuf;

/**
 * @since 14.0
 */
public class Codec29 extends Codec28 {

   @Override
   public HeaderParams writeHeader(ByteBuf buf, HeaderParams params) {
      return writeHeader(buf, params, HotRodConstants.VERSION_29);
   }

   @Override
   public MediaType readKeyType(ByteBuf buf) {
      return CodecUtils.readMediaType(buf);
   }

   @Override
   public MediaType readValueType(ByteBuf buf) {
      return CodecUtils.readMediaType(buf);
   }

   @Override
   public boolean isObjectStorageHinted(PingResponse pingResponse) {
      return pingResponse.isObjectStorage();
   }
}
