/*
 * Copyright (c) 2017-2021 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.interop;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.BlockClosureObject;
import de.hpi.swa.trufflesqueak.model.BooleanObject;
import de.hpi.swa.trufflesqueak.model.CharacterObject;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.model.EmptyObject;
import de.hpi.swa.trufflesqueak.model.FloatObject;
import de.hpi.swa.trufflesqueak.model.LargeIntegerObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.model.VariablePointersObject;
import de.hpi.swa.trufflesqueak.model.WeakVariablePointersObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayout;
import de.hpi.swa.trufflesqueak.nodes.SqueakGuards;

@SuppressWarnings("static-method")
@ExportLibrary(InteropLibrary.class)
public final class JavaObjectWrapper implements TruffleObject {
    protected static final int LIMIT = 2;
    private static final WeakHashMap<Object, JavaObjectWrapper> CACHE = new WeakHashMap<>();
    private static final ClassValue<HashMap<String, Field>> CLASSES_TO_FIELDS = new ClassValue<HashMap<String, Field>>() {
        @Override
        @SuppressWarnings("deprecation") // isAccessible deprecated in Java 11
        protected HashMap<String, Field> computeValue(final Class<?> type) {
            final HashMap<String, Field> result = new HashMap<>();
            Class<?> currentClass = type;
            while (currentClass != null) {
                for (final Field field : currentClass.getDeclaredFields()) {
                    if (!field.isAccessible()) {
                        try {
                            field.setAccessible(true);
                        } catch (final Exception e) {
                            continue; // skip inaccessible fields
                        }
                    }
                    final String name = field.getName();
                    if (name.indexOf('$') < 0) {
                        result.put(field.getName(), field);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            return result;
        }
    };
    private static final ClassValue<HashMap<String, Method>> CLASSES_TO_METHODS = new ClassValue<HashMap<String, Method>>() {
        @Override
        @SuppressWarnings("deprecation") // isAccessible deprecated in Java 11
        protected HashMap<String, Method> computeValue(final Class<?> type) {
            final HashMap<String, Method> result = new HashMap<>();
            Class<?> currentClass = type;
            while (currentClass != null) {
                for (final Method method : currentClass.getDeclaredMethods()) {
                    if (!method.isAccessible()) {
                        try {
                            method.setAccessible(true);
                        } catch (final Exception e) {
                            continue; // skip inaccessible methods
                        }
                    }
                    final String name = method.getName();
                    if (name.indexOf('$') < 0) {
                        result.put(method.getName(), method);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            return result;
        }
    };
    private static final ClassValue<InteropArray> CLASSES_TO_MEMBERS = new ClassValue<InteropArray>() {
        @Override
        protected InteropArray computeValue(final Class<?> type) {
            final HashSet<String> members = new HashSet<>(CLASSES_TO_FIELDS.get(type).keySet());
            members.addAll(CLASSES_TO_METHODS.get(type).keySet());
            return new InteropArray(members.toArray(new String[0]));
        }
    };

    static {
        /*
         * Pre-initialize CLASSES_TO_MEMBERS, CLASSES_TO_METHODS, and CLASSES_TO_FIELDS for certain
         * classes to provide access when TruffleSqueak is compiled with native-image.
         */
        if (TruffleOptions.AOT) {
            // General classes
            CLASSES_TO_MEMBERS.get(ArrayList.class);
            CLASSES_TO_MEMBERS.get(HashMap.class);
            CLASSES_TO_MEMBERS.get(HashSet.class);
            CLASSES_TO_MEMBERS.get(TreeSet.class); // Used to store mime types in LanguageInfo.class

            // Truffle classes exposed by PolyglotPlugin
            CLASSES_TO_MEMBERS.get(LanguageInfo.class);
            CLASSES_TO_MEMBERS.get(SourceSection.class);

            // Non-abstract classes of TruffleSqueak model
            CLASSES_TO_MEMBERS.get(ArrayObject.class);
            CLASSES_TO_MEMBERS.get(BlockClosureObject.class);
            CLASSES_TO_MEMBERS.get(BooleanObject.class);
            CLASSES_TO_MEMBERS.get(CharacterObject.class);
            CLASSES_TO_MEMBERS.get(ClassObject.class);
            CLASSES_TO_MEMBERS.get(CompiledCodeObject.class);
            CLASSES_TO_MEMBERS.get(ContextObject.class);
            CLASSES_TO_MEMBERS.get(EmptyObject.class);
            CLASSES_TO_MEMBERS.get(FloatObject.class);
            CLASSES_TO_MEMBERS.get(LargeIntegerObject.class);
            CLASSES_TO_MEMBERS.get(NativeObject.class);
            CLASSES_TO_MEMBERS.get(NilObject.class);
            CLASSES_TO_MEMBERS.get(PointersObject.class);
            CLASSES_TO_MEMBERS.get(VariablePointersObject.class);
            CLASSES_TO_MEMBERS.get(WeakVariablePointersObject.class);

            // TruffleSqueak's object layout
            CLASSES_TO_MEMBERS.get(ObjectLayout.class);
        }
    }

    @CompilationFinal private static Class<? extends TruffleLanguage<?>> hostLanguage;
    private final Object wrappedObject;

    private JavaObjectWrapper(final Object object) {
        wrappedObject = object;
    }

    @TruffleBoundary
    public static Object wrap(final Object object) {
        if (object == null) {
            return NilObject.SINGLETON;
        } else if (SqueakGuards.isUsedJavaPrimitive(object) || object instanceof JavaObjectWrapper) {
            return object;
        } else if (object instanceof Byte) {
            return (long) (byte) object;
        } else if (object instanceof Integer) {
            return (long) (int) object;
        } else if (object instanceof Float) {
            return (double) (float) object;
        } else {
            return CACHE.computeIfAbsent(object, o -> new JavaObjectWrapper(o));
        }
    }

    @TruffleBoundary
    private HashMap<String, Field> lookupFields() {
        return CLASSES_TO_FIELDS.get(wrappedObject.getClass());
    }

    @TruffleBoundary
    private HashMap<String, Method> lookupMethods() {
        return CLASSES_TO_METHODS.get(wrappedObject.getClass());
    }

    @TruffleBoundary
    private InteropArray lookupMembers() {
        return CLASSES_TO_MEMBERS.get(wrappedObject.getClass());
    }

    protected boolean isClass() {
        return wrappedObject instanceof Class<?>;
    }

    protected boolean isArrayClass() {
        return isClass() && asClass().isArray();
    }

    protected boolean isDefaultClass() {
        return isClass() && !asClass().isArray();
    }

    private Class<?> asClass() {
        assert isClass();
        return (Class<?>) wrappedObject;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(wrappedObject);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof JavaObjectWrapper) {
            return wrappedObject.equals(((JavaObjectWrapper) other).wrappedObject);
        }
        return false;
    }

    @Override
    public String toString() {
        return "JavaObject[" + wrappedObject.getClass().getName() + "]";
    }

    @ExportMessage
    @TruffleBoundary
    protected Object readMember(final String member) throws UnknownIdentifierException {
        final Field field = lookupFields().get(member);
        if (field != null) {
            try {
                return wrap(field.get(wrappedObject));
            } catch (final Exception e) {
                throw UnknownIdentifierException.create(member);
            }
        } else {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") final boolean includeInternal) {
        return lookupMembers();
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @TruffleBoundary
    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    protected boolean containsField(final String member) {
        return lookupFields().containsKey(member);
    }

    @ExportMessage
    protected boolean isMemberInsertable(@SuppressWarnings("unused") final String member) {
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    protected boolean isMemberInvocable(final String member) {
        return lookupMethods().containsKey(member);
    }

    @ExportMessage
    @TruffleBoundary
    protected Object invokeMember(final String member, final Object... arguments) throws UnknownIdentifierException, UnsupportedTypeException {
        final Method method = lookupMethods().get(member);
        if (method != null) {
            try {
                return wrap(method.invoke(wrappedObject, arguments));
            } catch (final Exception e) {
                throw UnsupportedTypeException.create(arguments);
            }
        } else {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("deprecation") // isAccessible deprecated in Java 11
    protected void writeMember(final String key, final Object value) {
        final Field field = lookupFields().get(key);
        if (field != null) {
            try {
                field.set(wrappedObject, value instanceof JavaObjectWrapper ? ((JavaObjectWrapper) value).wrappedObject : value);
            } catch (final Exception e) {
                throw new UnsupportedOperationException(e);
            }
        } else {
            throw new UnsupportedOperationException(wrappedObject + " has not member " + key);
        }
    }

    @ExportMessage
    protected boolean isNull(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) {
        return lib.isNull(wrappedObject);
    }

    @ExportMessage
    protected boolean isNumber() {
        final Class<?> c = wrappedObject.getClass();
        return c == Byte.class || c == Short.class || c == Integer.class || c == Long.class || c == Float.class || c == Double.class;
    }

    @ExportMessage
    protected boolean fitsInByte(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) {
        if (isNumber()) {
            return lib.fitsInByte(wrappedObject);
        } else {
            return false;
        }
    }

    @ExportMessage
    protected boolean fitsInShort(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) {
        if (isNumber()) {
            return lib.fitsInShort(wrappedObject);
        } else {
            return false;
        }
    }

    @ExportMessage
    protected boolean fitsInInt(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) {
        if (isNumber()) {
            return lib.fitsInInt(wrappedObject);
        } else {
            return false;
        }
    }

    @ExportMessage
    protected boolean fitsInLong(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) {
        if (isNumber()) {
            return lib.fitsInLong(wrappedObject);
        } else {
            return false;
        }
    }

    @ExportMessage
    protected boolean fitsInFloat(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) {
        if (isNumber()) {
            return lib.fitsInFloat(wrappedObject);
        } else {
            return false;
        }
    }

    @ExportMessage
    protected boolean fitsInDouble(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) {
        if (isNumber()) {
            return lib.fitsInDouble(wrappedObject);
        } else {
            return false;
        }
    }

    @ExportMessage
    protected byte asByte(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) throws UnsupportedMessageException {
        if (isNumber()) {
            return lib.asByte(wrappedObject);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    protected short asShort(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) throws UnsupportedMessageException {
        if (isNumber()) {
            return lib.asShort(wrappedObject);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    protected int asInt(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) throws UnsupportedMessageException {
        if (isNumber()) {
            return lib.asInt(wrappedObject);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    protected long asLong(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) throws UnsupportedMessageException {
        if (isNumber()) {
            return lib.asLong(wrappedObject);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    protected float asFloat(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) throws UnsupportedMessageException {
        if (isNumber()) {
            return lib.asFloat(wrappedObject);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    protected double asDouble(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) throws UnsupportedMessageException {
        if (isNumber()) {
            return lib.asDouble(wrappedObject);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    protected boolean isInstantiable() {
        return isClass();
    }

    @ExportMessage
    protected static class Instantiate {

        @Specialization(guards = "!receiver.isClass()")
        @SuppressWarnings("unused")
        protected static final Object doUnsupported(final JavaObjectWrapper receiver, final Object[] args) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @TruffleBoundary
        @Specialization(guards = "receiver.isArrayClass()")
        protected static final Object doArrayCached(final JavaObjectWrapper receiver, final Object[] args,
                        @CachedLibrary(limit = "1") final InteropLibrary lib) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            if (args.length != 1) {
                throw ArityException.create(1, args.length);
            }
            final Object arg0 = args[0];
            final int length;
            if (lib.fitsInInt(arg0)) {
                length = lib.asInt(arg0);
            } else {
                throw UnsupportedTypeException.create(args);
            }
            return wrap(Array.newInstance(receiver.asClass().getComponentType(), length));
        }

        @TruffleBoundary
        @Specialization(guards = "receiver.isDefaultClass()")
        protected static final Object doObjectCached(final JavaObjectWrapper receiver, final Object[] args) throws UnsupportedTypeException {
            assert !receiver.isArrayClass();
            iterateConstructors: for (final Constructor<?> constructor : receiver.asClass().getConstructors()) {
                if (constructor.getParameterCount() == args.length) {
                    for (int i = 0; i < args.length; i++) {
                        if (!constructor.getParameterTypes()[i].isAssignableFrom(args[i].getClass())) {
                            continue iterateConstructors;
                        }
                    }
                    // Arguments should fit into constructor.
                    try {
                        return wrap(constructor.newInstance(args));
                    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        throw UnsupportedTypeException.create(args);
                    }
                }
            }
            throw UnsupportedTypeException.create(args);
        }
    }

    @ExportMessage
    protected boolean isString() {
        return wrappedObject instanceof String;
    }

    @ExportMessage
    protected String asString() throws UnsupportedMessageException {
        try {
            return (String) wrappedObject;
        } catch (final ClassCastException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    protected String toDisplayString(@SuppressWarnings("unused") final boolean allowSideEffects) {
        return toString(); // TODO: String.valueOf(wrappedObject);
    }

    @ExportMessage
    @TruffleBoundary
    protected boolean hasArrayElements(@Shared("lib") @CachedLibrary(limit = "LIMIT") final InteropLibrary lib) {
        return wrappedObject.getClass().isArray() || wrappedObject instanceof TruffleObject && lib.hasArrayElements(wrappedObject);
    }

    @ExportMessage
    @ExportMessage(name = "isArrayElementModifiable")
    @TruffleBoundary
    protected boolean isArrayElementReadable(final long index, @Shared("sizeNode") @Cached final ArraySizeNode sizeNode) {
        try {
            return 0 <= index && index < sizeNode.execute(wrappedObject);
        } catch (final UnsupportedSpecializationException | UnsupportedMessageException e) {
            return false;
        }
    }

    @ExportMessage
    protected boolean isArrayElementInsertable(@SuppressWarnings("unused") final long index) {
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    protected long getArraySize(@Shared("sizeNode") @Cached final ArraySizeNode sizeNode) throws UnsupportedMessageException {
        try {
            return sizeNode.execute(wrappedObject);
        } catch (final UnsupportedSpecializationException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @GenerateUncached
    protected abstract static class ArraySizeNode extends Node {
        protected abstract int execute(Object object) throws UnsupportedSpecializationException, UnsupportedMessageException;

        @Specialization
        protected static final int doBoolean(final boolean[] object) {
            return object.length;
        }

        @Specialization
        protected static final int doByte(final byte[] object) {
            return object.length;
        }

        @Specialization
        protected static final int doChar(final char[] object) {
            return object.length;
        }

        @Specialization
        protected static final int doShort(final short[] object) {
            return object.length;
        }

        @Specialization
        protected static final int doInteger(final int[] object) {
            return object.length;
        }

        @Specialization
        protected static final int doLong(final long[] object) {
            return object.length;
        }

        @Specialization
        protected static final int doFloat(final float[] object) {
            return object.length;
        }

        @Specialization
        protected static final int doDouble(final double[] object) {
            return object.length;
        }

        @Specialization
        protected static final int doObject(final Object[] object) {
            return object.length;
        }

        @Specialization(limit = "1")
        protected static final int doTruffleObject(final TruffleObject object, @CachedLibrary("object") final InteropLibrary lib) throws UnsupportedMessageException {
            return (int) lib.getArraySize(object);
        }
    }

    @ExportMessage
    protected Object readArrayElement(final long index, @Cached final ReadArrayElementNode readNode) throws InvalidArrayIndexException, UnsupportedMessageException {
        try {
            return readNode.execute(wrappedObject, (int) index);
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw InvalidArrayIndexException.create(index);
        } catch (final UnsupportedSpecializationException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @GenerateUncached
    protected abstract static class ReadArrayElementNode extends Node {
        protected abstract Object execute(Object object, int index) throws UnsupportedMessageException, InvalidArrayIndexException;

        @Specialization
        protected static final boolean doBoolean(final boolean[] object, final int index) {
            return BooleanObject.wrap(object[index]);
        }

        @Specialization
        protected static final long doByte(final byte[] object, final int index) {
            return object[index];
        }

        @Specialization
        protected static final char doChar(final char[] object, final int index) {
            return object[index];
        }

        @Specialization
        protected static final long doShort(final short[] object, final int index) {
            return object[index];
        }

        @Specialization
        protected static final long doInteger(final int[] object, final int index) {
            return object[index];
        }

        @Specialization
        protected static final long doLong(final long[] object, final int index) {
            return object[index];
        }

        @Specialization
        protected static final double doFloat(final float[] object, final int index) {
            return object[index];
        }

        @Specialization
        protected static final double doDouble(final double[] object, final int index) {
            return object[index];
        }

        @Specialization
        protected static final Object doObject(final Object[] object, final int index) {
            return wrap(object[index]);
        }

        @Specialization(limit = "1")
        protected static final Object doTruffleObject(final TruffleObject object, final int index, @CachedLibrary("object") final InteropLibrary lib)
                        throws UnsupportedMessageException, InvalidArrayIndexException {
            return lib.readArrayElement(object, index);
        }
    }

    @ExportMessage
    protected void writeArrayElement(final long index, final Object value, @Cached final WriteArrayElementNode writeNode)
                    throws InvalidArrayIndexException, UnsupportedMessageException, UnsupportedTypeException {
        try {
            writeNode.execute(wrappedObject, (int) index, value);
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw InvalidArrayIndexException.create(index);
        } catch (final UnsupportedSpecializationException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @GenerateUncached
    protected abstract static class WriteArrayElementNode extends Node {
        protected abstract void execute(Object object, int index, Object value) throws UnsupportedMessageException, InvalidArrayIndexException, UnsupportedTypeException;

        @Specialization
        protected static final void doBoolean(final boolean[] object, final int index, final boolean value) {
            object[index] = value;
        }

        @Specialization
        protected static final void doByte(final byte[] object, final int index, final byte value) {
            object[index] = value;
        }

        @Specialization
        protected static final void doChar(final char[] object, final int index, final char value) {
            object[index] = value;
        }

        @Specialization
        protected static final void doShort(final short[] object, final int index, final short value) {
            object[index] = value;
        }

        @Specialization
        protected static final void doInteger(final int[] object, final int index, final int value) {
            object[index] = value;
        }

        @Specialization
        protected static final void doLong(final long[] object, final int index, final long value) {
            object[index] = value;
        }

        @Specialization
        protected static final void doFloat(final float[] object, final int index, final float value) {
            object[index] = value;
        }

        @Specialization
        protected static final void doDouble(final double[] object, final int index, final double value) {
            object[index] = value;
        }

        @Specialization
        protected static final void doObject(final Object[] object, final int index, final Object value) {
            object[index] = value;
        }

        @Specialization(limit = "1")
        protected static final void doTruffleObject(final TruffleObject object, final int index, final Object value, @CachedLibrary("object") final InteropLibrary lib)
                        throws UnsupportedMessageException, InvalidArrayIndexException, UnsupportedTypeException {
            lib.writeArrayElement(object, index, value);
        }
    }

    // Meta Object API

    @ExportMessage
    protected boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    protected Object getMetaObject() {
        return wrap(wrappedObject.getClass());
    }

    @ExportMessage
    protected boolean isMetaObject() {
        return isClass();
    }

    @ExportMessage
    @TruffleBoundary
    protected Object getMetaQualifiedName() throws UnsupportedMessageException {
        if (isClass()) {
            return asClass().getTypeName();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    protected Object getMetaSimpleName() throws UnsupportedMessageException {
        if (isClass()) {
            return asClass().getSimpleName();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    protected boolean isMetaInstance(final Object other) throws UnsupportedMessageException {
        if (isClass()) {
            final Class<?> c = asClass();
            if (other instanceof JavaObjectWrapper) {
                final Object otherWrappedObject = ((JavaObjectWrapper) other).wrappedObject;
                assert otherWrappedObject != null;
                return c.isInstance(otherWrappedObject);
            } else {
                return false;
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    protected boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    protected Class<? extends TruffleLanguage<?>> getLanguage() {
        if (hostLanguage == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            try {
                final Object hostObject = SqueakLanguage.getContext().env.asGuestValue(Truffle.getRuntime());
                hostLanguage = InteropLibrary.getUncached().getLanguage(hostObject);
            } catch (final UnsupportedMessageException e) {
                e.printStackTrace();
            }
        }
        return hostLanguage;
    }
}
