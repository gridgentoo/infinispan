package org.infinispan.marshall.core;

import static org.infinispan.util.logging.Log.CONTAINER;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;

import org.infinispan.commands.RemoteCommandsFactory;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.io.ByteBuffer;
import org.infinispan.commons.io.LazyByteArrayOutputStream;
import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.commons.marshall.BufferSizePredictor;
import org.infinispan.commons.marshall.Externalizer;
import org.infinispan.commons.marshall.MarshallableTypeHints;
import org.infinispan.commons.marshall.Marshaller;
import org.infinispan.commons.marshall.MarshallingException;
import org.infinispan.commons.marshall.NotSerializableException;
import org.infinispan.commons.marshall.SerializeFunctionWith;
import org.infinispan.commons.marshall.SerializeWith;
import org.infinispan.commons.marshall.StreamAwareMarshaller;
import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.KnownComponentNames;
import org.infinispan.factories.annotations.ComponentName;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.factories.annotations.Stop;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.marshall.core.impl.ClassToExternalizerMap;
import org.infinispan.marshall.core.impl.ClassToExternalizerMap.IdToExternalizerMap;
import org.infinispan.marshall.core.impl.ExternalExternalizers;
import org.infinispan.marshall.exts.ThrowableExternalizer;
import org.infinispan.marshall.persistence.PersistenceMarshaller;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * A globally-scoped marshaller. This is needed so that the transport layer
 * can unmarshall requests even before it's known which cache's marshaller can
 * do the job.
 *
 * @author Galder Zamarreño
 * @since 5.0
 */
@Scope(Scopes.GLOBAL)
public class GlobalMarshaller implements StreamingMarshaller {

   private static final Log log = LogFactory.getLog(GlobalMarshaller.class);

   public static final int NOT_FOUND                      = -1;

   public static final int ID_NULL                        = 0x00;
   public static final int ID_PRIMITIVE                   = 0x01;
   public static final int ID_INTERNAL                    = 0x02;
   public static final int ID_EXTERNAL                    = 0x03;
   public static final int ID_ANNOTATED                   = 0x04;
   public static final int ID_UNKNOWN                     = 0x05;
   /**
    * The array will be encoded as follows:
    *
    * ID_ARRAY | flags + component type | component type info | array length | [element type | element type info] | array elements |
    * 00000110 | ssncieee               | depends on type     | 0 - 4 bytes  | [00000eee     | depends on type  ] |       ...      |
    *
    * Flags:
    * ss: {@link #FLAG_ARRAY_EMPTY}, {@link #FLAG_ARRAY_SMALL}, {@link #FLAG_ARRAY_MEDIUM}, {@link #FLAG_ARRAY_LARGE}
    * n:  {@link #FLAG_ALL_NULL}
    * i:  {@link #FLAG_SINGLE_TYPE}
    * c:  {@link #FLAG_COMPONENT_TYPE_MATCH}
    * eee: {@link #ID_INTERNAL}, {@link #ID_EXTERNAL}, {@link #ID_ANNOTATED}, {@link #ID_CLASS}
    *
    * Element type will be present only if the {@link #FLAG_SINGLE_TYPE} is set. In that case the array elements
    * won't be preceded by the instance type identifier, this will be common for all elements.
    *
    * Multidimensional arrays are not supported ATM.
    */
   static final int ID_ARRAY                       = 0x06;
   static final int ID_CLASS                       = 0x07;
   static final int ID_LAMBDA                      = 0x08;

   // Type is in last 3 bits
   static final int TYPE_MASK                      = 0x07;
   // Array size is in first 2 bits
   static final int ARRAY_SIZE_MASK                = 0xC0;
   // All elements are of the same type
   static final int FLAG_SINGLE_TYPE               = 0x08;
   // Component type matches to instance type
   static final int FLAG_COMPONENT_TYPE_MATCH      = 0x10;
   // All elements of the array are null
   static final int FLAG_ALL_NULL                  = 0x20;
   // Length of the array
   static final int FLAG_ARRAY_EMPTY               = 0x00;
   static final int FLAG_ARRAY_SMALL               = 0x40;
   static final int FLAG_ARRAY_MEDIUM              = 0x80;
   static final int FLAG_ARRAY_LARGE               = 0xC0;


   private final MarshallableTypeHints marshallableTypeHints = new MarshallableTypeHints();

   @Inject GlobalComponentRegistry gcr;
   @Inject RemoteCommandsFactory cmdFactory;
   @Inject @ComponentName(KnownComponentNames.PERSISTENCE_MARSHALLER)
   PersistenceMarshaller persistenceMarshaller;

   ClassToExternalizerMap internalExts;
   IdToExternalizerMap reverseInternalExts;
   ClassToExternalizerMap externalExts;
   IdToExternalizerMap reverseExternalExts;
   private ClassIdentifiers classIdentifiers;
   private ClassLoader classLoader;

   public GlobalMarshaller() {
   }

   @Override
   @Start(priority = 8) // Should start after the externalizer table and before transport
   public void start() {
      GlobalConfiguration globalCfg = gcr.getGlobalConfiguration();
      classLoader = globalCfg.classLoader();
      internalExts = InternalExternalizers.load(gcr, cmdFactory);
      reverseInternalExts = internalExts.reverseMap(Ids.MAX_ID);
      if (log.isTraceEnabled()) {
         log.tracef("Internal class to externalizer ids: %s", internalExts);
         log.tracef("Internal reverse externalizers: %s", reverseInternalExts);
      }

      externalExts = ExternalExternalizers.load(globalCfg);
      reverseExternalExts = externalExts.reverseMap();
      if (log.isTraceEnabled()) {
         log.tracef("External class to externalizer ids: %s", externalExts);
         log.tracef("External reverse externalizers: %s", reverseExternalExts);
      }

      classIdentifiers = ClassIdentifiers.load(globalCfg);
   }

   @Override
   @Stop(priority = 130) // Stop after transport to avoid send/receive and marshaller not being ready
   public void stop() {
      internalExts = null;
      reverseInternalExts = null;
      externalExts = null;
      reverseExternalExts = null;
      classIdentifiers = null;
      persistenceMarshaller.stop();
   }

   public PersistenceMarshaller getPersistenceMarshaller() {
      return persistenceMarshaller;
   }

   @Override
   public byte[] objectToByteBuffer(Object obj) throws IOException, InterruptedException {
      try {
         BytesObjectOutput out = writeObjectOutput(obj);
         return out.toBytes(); // trim out unused bytes
      } catch (java.io.NotSerializableException nse) {
         if (log.isDebugEnabled()) log.debug("Object is not serializable", nse);
         throw new NotSerializableException(nse.getMessage(), nse.getCause());
      }
   }

   private BytesObjectOutput writeObjectOutput(Object obj) throws IOException {
      BufferSizePredictor sizePredictor = marshallableTypeHints.getBufferSizePredictor(obj);
      BytesObjectOutput out = writeObjectOutput(obj, sizePredictor.nextSize(obj));
      sizePredictor.recordSize(out.pos);
      return out;
   }

   private BytesObjectOutput writeObjectOutput(Object obj, int estimatedSize) throws IOException {
      BytesObjectOutput out = new BytesObjectOutput(estimatedSize, this);
      writeNullableObject(obj, out);
      return out;
   }

   @Override
   public Object objectFromByteBuffer(byte[] buf) throws IOException, ClassNotFoundException {
      BytesObjectInput in = BytesObjectInput.from(buf, this);
      return objectFromObjectInput(in);
   }

   private Object objectFromObjectInput(BytesObjectInput in) throws IOException, ClassNotFoundException {
      return readNullableObject(in);
   }

   @Override
   public ObjectOutput startObjectOutput(OutputStream os, boolean isReentrant, int estimatedSize) throws IOException {
      BytesObjectOutput out = new BytesObjectOutput(estimatedSize, this);
      return new StreamBytesObjectOutput(os, out);
   }

   @Override
   public void objectToObjectStream(Object obj, ObjectOutput out) throws IOException {
      out.writeObject(obj);
   }

   @Override
   public void finishObjectOutput(ObjectOutput oo) {
      try {
         oo.flush();
      } catch (IOException e) {
         // ignored
      }
   }

   @Override
   public Object objectFromByteBuffer(byte[] bytes, int offset, int len) throws IOException, ClassNotFoundException {
      // Ignore length since boundary checks are not so useful here where the
      // unmarshalling code knows what to expect specifically. E.g. if reading
      // a byte[] subset within it, it's always appended with length.
      BytesObjectInput in = BytesObjectInput.from(bytes, offset, this);
      return objectFromObjectInput(in);
   }

   @Override
   public Object objectFromInputStream(InputStream is) throws IOException, ClassNotFoundException {
      // This is a very limited use case, e.g. reading from a JDBC ResultSet InputStream
      // So, this copying of the stream into a byte[] has not been problematic so far,
      // though it's not really ideal.
      int len = is.available();
      LazyByteArrayOutputStream bytes;
      byte[] buf;
      if(len > 0) {
         bytes = new LazyByteArrayOutputStream(len);
         buf = new byte[Math.min(len, 1024)];
      } else {
         // Some input stream providers do not implement available()
         bytes = new LazyByteArrayOutputStream();
         buf = new byte[1024];
      }
      int bytesRead;
      while ((bytesRead = is.read(buf, 0, buf.length)) != -1) bytes.write(buf, 0, bytesRead);
      return objectFromByteBuffer(bytes.getRawBuffer(), 0, bytes.size());
   }

   @Override
   public boolean isMarshallable(Object o) throws Exception {
      Class<?> clazz = o.getClass();
      boolean containsMarshallable = marshallableTypeHints.isKnownMarshallable(clazz);
      if (containsMarshallable) {
         boolean marshallable = marshallableTypeHints.isMarshallable(clazz);
         if (log.isTraceEnabled())
            log.tracef("Marshallable type '%s' known and is marshallable=%b",
                  clazz.getName(), marshallable);

         return marshallable;
      } else {
         if (isMarshallableCandidate(o)) {
            boolean isMarshallable = true;
            try {
               objectToBuffer(o);
            } catch (Exception e) {
               isMarshallable = false;
               throw e;
            } finally {
               marshallableTypeHints.markMarshallable(clazz, isMarshallable);
            }
            return isMarshallable;
         }
         return false;
      }
   }

   private boolean isMarshallableCandidate(Object o) {
      return o instanceof Serializable
             || getExternalizer(internalExts, o.getClass()) != null
             || getExternalizer(externalExts, o.getClass()) != null
             || o.getClass().getAnnotation(SerializeWith.class) != null
             || isExternalMarshallable(o);
   }

   private boolean isExternalMarshallable(Object o) {
      try {
         return persistenceMarshaller.isMarshallable(o);
      } catch (Exception e) {
         throw new MarshallingException("Object of type " + o.getClass() + " expected to be marshallable", e);
      }
   }

   @Override
   public BufferSizePredictor getBufferSizePredictor(Object o) {
      return marshallableTypeHints.getBufferSizePredictor(o.getClass());
   }

   @Override
   public MediaType mediaType() {
      return MediaType.APPLICATION_INFINISPAN_MARSHALLED;
   }

   @Override
   public ByteBuffer objectToBuffer(Object o) throws IOException, InterruptedException {
      try {
         BytesObjectOutput out = writeObjectOutput(o);
         return out.toByteBuffer();
      } catch (java.io.NotSerializableException nse) {
         if (log.isDebugEnabled()) log.debug("Object is not serializable", nse);
         throw new NotSerializableException(nse.getMessage(), nse.getCause());
      }
   }

   @Override
   public byte[] objectToByteBuffer(Object obj, int estimatedSize) throws IOException, InterruptedException {
      try {
         BytesObjectOutput out = writeObjectOutput(obj, estimatedSize);
         return out.toBytes();
      } catch (java.io.NotSerializableException nse) {
         if (log.isDebugEnabled()) log.debug("Object is not serializable", nse);
         throw new NotSerializableException(nse.getMessage(), nse.getCause());
      }
   }

   @Override
   public ObjectInput startObjectInput(InputStream is, boolean isReentrant) {
      throw new UnsupportedOperationException("No longer in use");
   }

   @Override
   public void finishObjectInput(ObjectInput oi) {
      throw new UnsupportedOperationException("No longer in use");
   }

   @Override
   public Object objectFromObjectStream(ObjectInput in) {
      throw new UnsupportedOperationException("No longer in use");
   }

   public <T> Externalizer<T> findExternalizerFor(Object obj) {
      Class<?> clazz = obj.getClass();
      Externalizer ext = getExternalizer(internalExts, clazz);
      if (ext == null) {
         ext = getExternalizer(externalExts, clazz);
         if (ext == null)
            ext = findAnnotatedExternalizer(clazz);
      }

      return ext;
   }

   void writeNullableObject(Object obj, BytesObjectOutput out) throws IOException {
      if (obj == null)
         out.writeByte(ID_NULL);
      else
         writeNonNullableObject(obj, out);
   }


   Object readNullableObject(BytesObjectInput in) throws IOException, ClassNotFoundException {
      int type = in.readUnsignedByte();
      return type == ID_NULL ? null : readNonNullableObject(type, in);
   }

   private void writeNonNullableObject(Object obj, BytesObjectOutput out) throws IOException {
      Class<?> clazz = obj.getClass();
      int id = Primitives.PRIMITIVES.getOrDefault(clazz, NOT_FOUND);
      if (id != NOT_FOUND) {
         writePrimitive(obj, out, id);
      } else if (clazz.isArray()) {
         writeArray(clazz, obj, out);
      } else {
         AdvancedExternalizer ext = getExternalizer(internalExts, clazz, obj);
         if (ext != null) {
            writeInternal(obj, ext, out);
         } else {
            ext = getExternalizer(externalExts, clazz);
            if (ext != null) {
               writeExternal(obj, ext, out);
            } else {
               Externalizer annotExt = findAnnotatedExternalizer(clazz);
               if (annotExt != null) {
                  writeAnnotated(obj, out, annotExt);
               } else {
                  if (clazz.isSynthetic())
                     writeUnknownLambda(obj, out);
                  else
                     writeUnknown(obj, out);
               }
            }
         }
      }
   }

   public static AdvancedExternalizer getInteralExternalizer(GlobalMarshaller gm, Class<?> clazz) {
      return gm.getExternalizer(gm.internalExts, clazz);
   }

   public static AdvancedExternalizer getExternalExternalizer(GlobalMarshaller gm, Class<?> clazz) {
      return gm.getExternalizer(gm.externalExts, clazz);
   }

   public static Object readObjectFromObjectInput(GlobalMarshaller gm, ObjectInput in) throws IOException, ClassNotFoundException {
      int type = in.readUnsignedByte();
      switch (type) {
         case ID_INTERNAL:
            return gm.getExternalizer(gm.reverseInternalExts, in.readUnsignedByte()).readObject(in);
         case ID_EXTERNAL:
            return gm.getExternalizer(gm.reverseExternalExts, in.readInt()).readObject(in);
         case ID_UNKNOWN:
            return gm.readUnknown(gm.persistenceMarshaller, in);
         default:
            return null;
      }
   }

   AdvancedExternalizer getExternalizer(ClassToExternalizerMap class2ExternalizerMap, Class<?> clazz, Object o) {
      AdvancedExternalizer ext = getExternalizer(class2ExternalizerMap, clazz);
      if (ext != null)
         return ext;

      return o instanceof Throwable ? ThrowableExternalizer.INSTANCE : null;
   }

   AdvancedExternalizer getExternalizer(ClassToExternalizerMap class2ExternalizerMap, Class<?> clazz) {
      if (class2ExternalizerMap == null) {
         throw CONTAINER.cacheManagerIsStopping();
      }
      return class2ExternalizerMap.get(clazz);
   }

   AdvancedExternalizer getExternalizer(IdToExternalizerMap id2ExternalizerMap, int i) {
      if (id2ExternalizerMap == null) {
         throw CONTAINER.cacheManagerIsStopping();
      }
      return id2ExternalizerMap.get(i);
   }

   private void writeArray(Class<?> clazz, Object array, BytesObjectOutput out) throws IOException {
      out.writeByte(ID_ARRAY);
      Class<?> componentType = clazz.getComponentType();

      int length = Array.getLength(array);
      boolean singleType = true;
      Class<?> elementType = null;
      int flags;
      if (length == 0) {
         flags = FLAG_ARRAY_EMPTY;
      } else {
         if (length <= Primitives.SMALL_ARRAY_MAX) {
            flags = FLAG_ARRAY_SMALL;
         } else if (length <= Primitives.MEDIUM_ARRAY_MAX) {
            flags = FLAG_ARRAY_MEDIUM;
         } else {
            flags = FLAG_ARRAY_LARGE;
         }
         Object firstElement = Array.get(array, 0);
         if (firstElement != null) {
            elementType = firstElement.getClass();
         }
         for (int i = 1; i < length; ++i) {
            Object element = Array.get(array, i);
            if (element == null) {
               if (elementType != null) {
                  singleType = false;
                  break;
               }
            } else if (element.getClass() != elementType) {
               singleType = false;
               break;
            }
         }
      }
      boolean componentTypeMatch = false;
      if (singleType) {
         flags |= FLAG_SINGLE_TYPE;
         if (elementType == null) {
            flags |= FLAG_ALL_NULL;
         } else if (elementType == componentType) {
            flags |= FLAG_COMPONENT_TYPE_MATCH;
            componentTypeMatch = true;
         }
      }

      AdvancedExternalizer ext;
      if ((ext = getExternalizer(internalExts, componentType)) != null) {
         writeFlagsWithExternalizer(out, componentType, componentTypeMatch, ext, flags, ID_INTERNAL);
      } else if ((ext = getExternalizer(externalExts, componentType)) != null) {
         writeFlagsWithExternalizer(out, componentType, componentTypeMatch, ext, flags, ID_EXTERNAL);
      } else {
         // We cannot use annotated externalizer to specify the component type, so we will
         // clear the component type match flag and use class identifier or full name.
         // Theoretically we could write annotated externalizer and component type saving one byte
         // but it's not worth the complexity.
         componentTypeMatch = false;
         flags &= ~FLAG_COMPONENT_TYPE_MATCH;

         int classId;
         if ((classId = classIdentifiers.getId(componentType)) != -1) {
            out.writeByte(flags | ID_CLASS);
            if (classId < ClassIds.MAX_ID) {
               out.writeByte(classId);
            } else {
               out.writeByte(ClassIds.MAX_ID);
               out.writeInt(classId);
            }
         } else {
            out.writeByte(flags | ID_UNKNOWN);
            out.writeObject(componentType);
         }
      }

      if (length == 0) {
      } else if (length <= Primitives.SMALL_ARRAY_MAX) {
         out.writeByte(length - Primitives.SMALL_ARRAY_MIN);
      } else if (length <= Primitives.MEDIUM_ARRAY_MAX) {
         out.writeShort(length - Primitives.MEDIUM_ARRAY_MIN);
      } else {
         out.writeInt(length);
      }

      if (singleType) {
         Externalizer elementExt;
         int primitiveId;
         if (elementType == null) {
            return;
         } else if (componentTypeMatch) {
            // Note: ext can be null here!
            elementExt = ext;
         } else if ((elementExt = getExternalizer(internalExts, elementType)) != null) {
            out.writeByte(ID_INTERNAL);
            out.writeByte(((AdvancedExternalizer) elementExt).getId());
         } else if ((elementExt = getExternalizer(externalExts, elementType)) != null) {
            out.writeByte(ID_EXTERNAL);
            out.writeInt(((AdvancedExternalizer) elementExt).getId());
         } else if ((elementExt = findAnnotatedExternalizer(elementType)) != null) {
            // We could try if the externalizer class is registered in ClassIdentifier but most likely it is not,
            // because if an user registered it, he could rather explicitly register AdvancedExternalizer instead.
            out.writeByte(ID_ANNOTATED);
            out.writeObject(elementExt.getClass());
         } else if ((primitiveId = Primitives.PRIMITIVES.getOrDefault(elementType, NOT_FOUND)) != NOT_FOUND) {
            out.writeByte(ID_PRIMITIVE);
            out.writeByte(primitiveId);
            for (int i = 0; i < length; ++i) {
               Object element = Array.get(array, i);
               assert element != null;
               Primitives.writeRawPrimitive(element, out, primitiveId);
            }
            // We are finished
            return;
         } else {
            out.writeByte(ID_UNKNOWN);
            // Do not write element type!
         }
         if (elementExt != null) {
            for (int i = 0; i < length; ++i) {
               Object element = Array.get(array, i);
               assert element != null;
               elementExt.writeObject(out, element);
            }
         } else {
            // component type matches but this type does not have an externalizer
            for (int i = 0; i < length; ++i) {
               Object element = Array.get(array, i);
               assert element != null;
               writeRawUnknown(element, out);
            }
         }
      } else {
         for (int i = 0; i < length; ++i) {
            Object element = Array.get(array, i);
            writeNullableObject(element, out);
         }
      }
   }

   private void writeFlagsWithExternalizer(BytesObjectOutput out, Class<?> componentType, boolean componentTypeMatch, AdvancedExternalizer ext, int flags, int externalizerType) throws IOException {
      // If the class can be identified by its externalizer, do that.
      // If there's a component type match, write the externalizer here anyway, otherwise we would
      // have to write it as element type later
      // If the class cannot be uniquely identified by its externalizer, try class identifiers
      boolean hasSingleClass = ext.getTypeClasses().size() == 1;
      int classId = -1;
      if (componentTypeMatch || hasSingleClass) {
         out.writeByte(flags | externalizerType);
         switch (externalizerType) {
            case ID_INTERNAL:
               out.writeByte(ext.getId());
               break;
            case ID_EXTERNAL:
               out.writeInt(ext.getId());
               break;
            default:
               throw new IllegalStateException();
         }
         if (!hasSingleClass) {
            classId = classIdentifiers.getId(componentType);
         }
      } else if ((classId = classIdentifiers.getId(componentType)) >= 0) {
         out.writeByte(flags | ID_CLASS);
      } else {
         out.writeByte(flags | ID_UNKNOWN);
      }
      if (!hasSingleClass) {
         if (classId < 0) {
            out.writeObject(componentType);
         } else if (classId < ClassIds.MAX_ID){
            out.writeByte(classId);
         } else {
            out.writeByte(ClassIds.MAX_ID);
            out.writeInt(classId);
         }
      }
   }

   private void writeUnknownLambda(Object obj, ObjectOutput out) throws IOException {
      out.writeByte(ID_LAMBDA);
      LambdaMarshaller.write(out, obj);
   }

   private void writeUnknown(Object obj, BytesObjectOutput out) throws IOException {
      writeUnknown(persistenceMarshaller, obj, out);
   }

   private void writeRawUnknown(Object obj, ObjectOutput out) throws IOException {
      writeRawUnknown(persistenceMarshaller, obj, out);
   }

   public static void writeUnknown(Marshaller marshaller, Object obj, ObjectOutput out) throws IOException {
      out.writeByte(ID_UNKNOWN);
      writeRawUnknown(marshaller, obj, out);
   }

   private static void writeRawUnknown(Marshaller marshaller, Object obj, ObjectOutput out) throws IOException {
      if (marshaller instanceof StreamingMarshaller) {
         ((StreamingMarshaller) marshaller).objectToObjectStream(obj, out);
      } else if (marshaller instanceof StreamAwareMarshaller && out instanceof StreamBytesObjectOutput) {
         OutputStream outputStream = ((StreamBytesObjectOutput) out).stream;
         ((StreamAwareMarshaller) marshaller).writeObject(obj, outputStream);
      } else {
         try {
            byte[] bytes = marshaller.objectToByteBuffer(obj);
            out.writeInt(bytes.length);
            out.write(bytes);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }
   }

   private void writeAnnotated(Object obj, BytesObjectOutput out, Externalizer ext) throws IOException {
      out.writeByte(ID_ANNOTATED);
      out.writeObject(ext.getClass());
      ext.writeObject(out, obj);
   }

   static void writeInternal(Object obj, AdvancedExternalizer ext, ObjectOutput out) throws IOException {
      out.writeByte(ID_INTERNAL);
      out.writeByte(ext.getId());
      ext.writeObject(out, obj);
   }

   public static void writeInternalClean(Object obj, AdvancedExternalizer ext, ObjectOutput out) {
      try {
         writeInternal(obj, ext, out);
      } catch (IOException e) {
         throw new CacheException(e);
      }
   }

   private static void writeExternal(Object obj, AdvancedExternalizer ext, ObjectOutput out) throws IOException {
      out.writeByte(ID_EXTERNAL);
      out.writeInt(ext.getId());
      ext.writeObject(out, obj);
   }

   public static void writeExternalClean(Object obj, AdvancedExternalizer ext, ObjectOutput out) {
      try {
         writeExternal(obj, ext, out);
      } catch (IOException e) {
         throw new CacheException(e);
      }
   }

   private void writePrimitive(Object obj, BytesObjectOutput out, int id) throws IOException {
      out.writeByte(ID_PRIMITIVE);
      Primitives.writePrimitive(obj, out, id);
   }

   private <T> Externalizer<T> findAnnotatedExternalizer(Class<?> clazz) {
      try {
         SerializeWith serialAnn = clazz.getAnnotation(SerializeWith.class);
         if (serialAnn != null) {
            return (Externalizer<T>) serialAnn.value().newInstance();
         } else {
            SerializeFunctionWith funcSerialAnn = clazz.getAnnotation(SerializeFunctionWith.class);
            if (funcSerialAnn != null)
               return (Externalizer<T>) funcSerialAnn.value().newInstance();
         }

         return null;
      } catch (Exception e) {
         throw new IllegalArgumentException(String.format(
               "Cannot instantiate externalizer for %s", clazz), e);
      }
   }

   private Object readNonNullableObject(int type, BytesObjectInput in) throws IOException, ClassNotFoundException {
      switch (type) {
         case ID_PRIMITIVE:
            return Primitives.readPrimitive(in);
         case ID_INTERNAL:
            return readWithExternalizer(in.readUnsignedByte(), reverseInternalExts, in);
         case ID_EXTERNAL:
            return readWithExternalizer(in.readInt(), reverseExternalExts, in);
         case ID_ANNOTATED:
            return readAnnotated(in);
         case ID_UNKNOWN:
            return readUnknown(in);
         case ID_ARRAY:
            return readArray(in);
         case ID_LAMBDA:
            return LambdaMarshaller.read(in, classLoader);
         default:
            throw new IOException("Unknown type: " + type);
      }
   }

   private Object readWithExternalizer(int id, IdToExternalizerMap reverseMap, BytesObjectInput in)
         throws IOException, ClassNotFoundException {
      AdvancedExternalizer ext = getExternalizer(reverseMap, id);
      return ext.readObject(in);
   }

   private Object readAnnotated(BytesObjectInput in) throws IOException, ClassNotFoundException {
      Class<? extends Externalizer> clazz =
            (Class<? extends Externalizer>) in.readObject();
      try {
         Externalizer ext = clazz.newInstance();
         return ext.readObject(in);
      } catch (Exception e) {
         throw new CacheException("Error instantiating class: " + clazz, e);
      }
   }

   private Object readArray(BytesObjectInput in) throws IOException, ClassNotFoundException {
      int flags = in.readByte();
      int type = flags & TYPE_MASK;
      AdvancedExternalizer<?> componentExt = null;
      Class<?> extClazz = null;
      Class<?> componentType;
      switch (type) {
         case ID_NULL:
         case ID_PRIMITIVE:
         case ID_ARRAY:
            throw new IOException("Unexpected component type: " + type);
         case ID_INTERNAL:
            componentExt = getExternalizer(reverseInternalExts, in.readByte());
            componentType = getOrReadClass(in, componentExt);
            break;
         case ID_EXTERNAL:
            componentExt = getExternalizer(reverseExternalExts, in.readInt());
            componentType = getOrReadClass(in, componentExt);
            break;
         case ID_ANNOTATED:
            extClazz = (Class<?>) in.readObject();
            // intentional no break
         case ID_UNKNOWN:
            componentType = (Class<?>) in.readObject();
            break;
         case ID_CLASS:
            int classId = in.readByte();
            if (classId < ClassIds.MAX_ID) {
               componentType = classIdentifiers.getClass(classId);
            } else {
               componentType = classIdentifiers.getClass(in.readInt());
            }
            break;
         default:
            throw new IOException("Unknown component type: " + type);
      }
      int length;
      int maskedSize = flags & ARRAY_SIZE_MASK;
      switch (maskedSize) {
         case FLAG_ARRAY_EMPTY:
            length = 0;
            break;
         case FLAG_ARRAY_SMALL:
            length = in.readUnsignedByte() + Primitives.SMALL_ARRAY_MIN;
            break;
         case FLAG_ARRAY_MEDIUM:
            length = in.readUnsignedShort() + Primitives.MEDIUM_ARRAY_MIN;
            break;
         case FLAG_ARRAY_LARGE:
            length = in.readInt();
            break;
         default:
            throw new IOException("Unknown array size: " + maskedSize);
      }
      Object array = Array.newInstance(componentType, length);
      if ((flags & FLAG_ALL_NULL) != 0) {
         return array;
      }

      boolean singleType = (flags & FLAG_SINGLE_TYPE) != 0;
      boolean componentTypeMatch = (flags & FLAG_COMPONENT_TYPE_MATCH) != 0;
      // If component type match is set, this must be a single type
      assert componentTypeMatch ? singleType : true;
      if (singleType) {
         Externalizer<?> ext;
         if (componentTypeMatch) {
            ext = getArrayElementExternalizer(type, componentExt, extClazz);
         } else {
            type = in.readByte();
            ext = readExternalizer(in, type);
         }
         if (ext != null) {
            for (int i = 0; i < length; ++i) {
               Array.set(array, i, ext.readObject(in));
            }
         } else {
            switch (type) {
               case ID_UNKNOWN:
                  for (int i = 0; i < length; ++i) {
                     Array.set(array, i, readUnknown(in));
                  }
                  return array;
               case ID_PRIMITIVE:
                  int primitiveId = in.readByte();
                  for (int i = 0; i < length; ++i) {
                     Array.set(array, i, Primitives.readRawPrimitive(in, primitiveId));
                  }
                  return array;
               default:
                  throw new IllegalStateException();
            }
         }
      } else {
         for (int i = 0; i < length; ++i) {
            Array.set(array, i, readNullableObject(in));
         }
      }
      return array;
   }

   private Externalizer<?> getArrayElementExternalizer(int type, AdvancedExternalizer<?> componentExt, Class<?> extClazz) throws IOException {
      switch (type) {
         case ID_INTERNAL:
         case ID_EXTERNAL:
            return componentExt;
         case ID_ANNOTATED:
            try {
               return (Externalizer<?>) extClazz.newInstance();
            } catch (Exception e) {
               throw new CacheException("Error instantiating class: " + extClazz, e);
            }
         case ID_UNKNOWN:
            return null;
         default:
            throw new IOException("Unexpected component type: " + type);
      }
   }

   private Externalizer<?> readExternalizer(BytesObjectInput in, int type) throws ClassNotFoundException, IOException {
      Class<?> extClazz;
      switch (type) {
         case ID_INTERNAL:
            return getExternalizer(reverseInternalExts, 0xFF & in.readByte());
         case ID_EXTERNAL:
            return getExternalizer(reverseExternalExts, in.readInt());
         case ID_ANNOTATED:
            extClazz = (Class<?>) in.readObject();
            try {
               return (Externalizer<?>) extClazz.newInstance();
            } catch (Exception e) {
               throw new CacheException("Error instantiating class: " + extClazz, e);
            }
         case ID_UNKNOWN:
         case ID_PRIMITIVE:
            return null;
         default:
            throw new IOException("Unexpected component type: " + type);
      }
   }

   private Class<?> getOrReadClass(BytesObjectInput in, AdvancedExternalizer<?> componentExt) throws ClassNotFoundException, IOException {
      if (componentExt.getTypeClasses().size() == 1) {
         return componentExt.getTypeClasses().iterator().next();
      } else {
         return (Class<?>) in.readObject();
      }
   }

   private Object readUnknown(ObjectInput in) throws IOException, ClassNotFoundException {
      return readUnknown(persistenceMarshaller, in);
   }

   Object readUnknown(Marshaller marshaller, ObjectInput in) throws IOException, ClassNotFoundException {
      if (marshaller instanceof StreamingMarshaller) {
         try {
            return ((StreamingMarshaller) marshaller).objectFromObjectStream(in);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
         }
      } else {
         int length = in.readInt();
         byte[] bytes = new byte[length];
         in.readFully(bytes);
         return marshaller.objectFromByteBuffer(bytes);
      }
   }
}
