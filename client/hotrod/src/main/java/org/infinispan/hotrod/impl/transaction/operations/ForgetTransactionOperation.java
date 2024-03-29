package org.infinispan.hotrod.impl.transaction.operations;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.infinispan.api.common.CacheOptions;
import org.infinispan.hotrod.impl.operations.OperationContext;
import org.infinispan.hotrod.impl.operations.RetryOnFailureOperation;
import org.infinispan.hotrod.impl.transport.netty.ByteBufUtil;
import org.infinispan.hotrod.impl.transport.netty.HeaderDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * It forgets the transaction identified by {@link Xid} in the server.
 * <p>
 * It affects all caches involved in the transaction. It is requested from {@link XAResource#forget(Xid)}.
 *
 * @since 14.0
 */
public class ForgetTransactionOperation extends RetryOnFailureOperation<Void> {
   private final Xid xid;

   public ForgetTransactionOperation(OperationContext operationContext, Xid xid) {
      super(operationContext, FORGET_TX_REQUEST, FORGET_TX_RESPONSE, CacheOptions.DEFAULT, null);
      this.xid = xid;
   }

   @Override
   public void acceptResponse(ByteBuf buf, short status, HeaderDecoder decoder) {
      complete(null);
   }

   @Override
   protected void executeOperation(Channel channel) {
      scheduleRead(channel);
      ByteBuf buf = channel.alloc().buffer(estimateSize());
      operationContext.getCodec().writeHeader(buf, header);
      ByteBufUtil.writeXid(buf, xid);
      channel.writeAndFlush(buf);
   }

   private int estimateSize() {
      return operationContext.getCodec().estimateHeaderSize(header) + ByteBufUtil.estimateXidSize(xid);
   }

}
