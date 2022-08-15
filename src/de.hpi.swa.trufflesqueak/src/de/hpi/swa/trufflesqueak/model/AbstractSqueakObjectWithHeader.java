/*
 * Copyright (c) 2017-2022 Software Architecture Group, Hasso Plattner Institute
 * Copyright (c) 2021-2022 Oracle and/or its affiliates
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.model;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions;
import de.hpi.swa.trufflesqueak.image.SqueakImageChunk;
import de.hpi.swa.trufflesqueak.image.SqueakImageConstants;
import de.hpi.swa.trufflesqueak.image.SqueakImageConstants.ObjectHeader;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.image.SqueakImageWriter;
import de.hpi.swa.trufflesqueak.interop.LookupMethodByStringNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.DispatchUneagerlyNode;
import de.hpi.swa.trufflesqueak.util.ArrayUtils;
import de.hpi.swa.trufflesqueak.util.ObjectGraphUtils.ObjectTracer;

public abstract class AbstractSqueakObjectWithHeader extends AbstractSqueakObject {
    /* Generate new hash if hash is 0 (see SpurMemoryManager>>#hashBitsOf:). */
    public static final int HASH_UNINITIALIZED = 0;

    protected long squeakObjectHeader;

    // For special/well-known objects only.
    protected AbstractSqueakObjectWithHeader(final SqueakImageContext image) {
        this(image, null);
    }

    public AbstractSqueakObjectWithHeader(final SqueakImageContext image, final long objectHeader) {
        this(image, null);
        squeakObjectHeader = objectHeader;
    }

    protected AbstractSqueakObjectWithHeader(final SqueakImageContext image, final ClassObject klass) {
        squeakObjectHeader = HASH_UNINITIALIZED;
        if (klass != null) { // FIXME
            setSqueakClass(klass);
        }
        if (image.getCurrentMarkingFlag()) {
            squeakObjectHeader = ObjectHeader.toggleMarked(squeakObjectHeader);
        }
    }

    protected AbstractSqueakObjectWithHeader(final AbstractSqueakObjectWithHeader original) {
        squeakObjectHeader = original.squeakObjectHeader;
        setSqueakHash(HASH_UNINITIALIZED);
    }

    @Override
    public int getNumSlots() {
        CompilerAsserts.neverPartOfCompilation();
        return size();
    }

    public final int getSqueakClassIndex() {
        return SqueakImageConstants.ObjectHeader.getClassIndex(squeakObjectHeader);
    }

    public final ClassObject getSqueakClass() {
        CompilerDirectives.bailout("Slow path");
        return SqueakImageContext.getSlow().lookupClass(getSqueakClassIndex());
    }

    public final String getSqueakClassName() {
        return getSqueakClass().getClassName();
    }

    public final void setSqueakClass(final ClassObject newClass) {
        assert newClass.getSqueakHash() != HASH_UNINITIALIZED;
        squeakObjectHeader = ObjectHeader.setClassIndex(squeakObjectHeader, newClass.asClassIndex());
    }

    public final void becomeOtherClass(final AbstractSqueakObjectWithHeader other) {
        final int otherClassIndex = ObjectHeader.getClassIndex(other.squeakObjectHeader);
        final int classIndex = ObjectHeader.getClassIndex(squeakObjectHeader);
        other.squeakObjectHeader = ObjectHeader.setClassIndex(other.squeakObjectHeader, classIndex);
        squeakObjectHeader = ObjectHeader.setClassIndex(squeakObjectHeader, otherClassIndex);
    }

    public final boolean hasFormatOf(final ClassObject other) {
        // FIXME: ObjectHeader.getFormat(squeakObjectHeader) == other.getFormat();
        return getSqueakClass().getFormat() == other.getFormat();
    }

    public abstract void fillin(SqueakImageChunk chunk);

    public void setObjectHeader(final long objectHeader) {
        squeakObjectHeader = objectHeader;
    }

    @Override
    public final long getSqueakHash() {
        if (needsSqueakHash()) {
            /** Lazily initialize squeakHash and derive value from hashCode. */
            initializeSqueakHash();
        }
        return getSqueakHashValue();
    }

    public final long getSqueakHash(final BranchProfile needsHashProfile) {
        if (needsSqueakHash()) {
            /** Lazily initialize squeakHash and derive value from hashCode. */
            needsHashProfile.enter();
            initializeSqueakHash();
        }
        return getSqueakHashValue();
    }

    private long getSqueakHashValue() {
        return ObjectHeader.getHash(squeakObjectHeader);
    }

    public final boolean needsSqueakHash() {
        return getSqueakHashValue() == HASH_UNINITIALIZED;
    }

    private void initializeSqueakHash() {
        setSqueakHash(System.identityHashCode(this) & ObjectHeader.HASH_AND_CLASS_INDEX_SIZE);
    }

    public final void setSqueakHash(final int newHash) {
        squeakObjectHeader = ObjectHeader.setHash(squeakObjectHeader, newHash);
    }

    public final boolean getMarkingFlag() {
        return ObjectHeader.isMarked(squeakObjectHeader);
    }

    public final boolean isMarked(final boolean currentMarkingFlag) {
        return getMarkingFlag() == currentMarkingFlag;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "a " + getSqueakClassName() + " @" + Integer.toHexString(hashCode());
    }

    @TruffleBoundary
    public final Object send(final SqueakImageContext image, final String selector, final Object... arguments) {
        final Object methodObject = LookupMethodByStringNode.getUncached().executeLookup(getSqueakClass(), selector);
        if (methodObject instanceof CompiledCodeObject) {
            final boolean wasActive = image.interrupt.isActive();
            image.interrupt.deactivate();
            try {
                return DispatchUneagerlyNode.getUncached().executeDispatch((CompiledCodeObject) methodObject, ArrayUtils.copyWithFirst(arguments, this), NilObject.SINGLETON);
            } finally {
                if (wasActive) {
                    image.interrupt.activate();
                }
            }
        } else {
            throw SqueakExceptions.SqueakException.create("CompiledMethodObject expected, got: " + methodObject);
        }
    }

    /**
     * @return <tt>false</tt> if already marked, <tt>true</tt> otherwise
     */
    public final boolean tryToMark(final boolean currentMarkingFlag) {
        if (getMarkingFlag() == currentMarkingFlag) {
            return false;
        } else {
            squeakObjectHeader = ObjectHeader.toggleMarked(squeakObjectHeader);
            return true;
        }
    }

    @SuppressWarnings("unused")
    public void pointersBecomeOneWay(final Object[] from, final Object[] to) {
        // Do nothing by default.
    }

    protected static final void pointersBecomeOneWay(final Object[] target, final Object[] from, final Object[] to) {
        for (int i = 0; i < from.length; i++) {
            final Object fromPointer = from[i];
            for (int j = 0; j < target.length; j++) {
                final Object newPointer = target[j];
                if (newPointer == fromPointer) {
                    final Object toPointer = to[i];
                    target[j] = toPointer;
                }
            }
        }
    }

    public void tracePointers(@SuppressWarnings("unused") final ObjectTracer objectTracer) {
        // Nothing to trace by default.
    }

    public void trace(final SqueakImageWriter writer) {
        writer.traceIfNecessary(getSqueakClass());
    }

    public abstract void write(SqueakImageWriter writer);

    /* Returns true if more content is following. */
    protected final boolean writeHeader(final SqueakImageWriter writer) {
        return writeHeader(writer, 0);
    }

    protected final boolean writeHeader(final SqueakImageWriter writer, final int formatOffset) {
        final long numSlots = getNumSlots();
        if (numSlots >= SqueakImageConstants.OVERFLOW_SLOTS) {
            writer.writeLong(numSlots | SqueakImageConstants.SLOTS_MASK);
            writer.writeObjectHeader(SqueakImageConstants.OVERFLOW_SLOTS, getSqueakHash(), getSqueakClass(), formatOffset);
        } else {
            writer.writeObjectHeader(numSlots, getSqueakHash(), getSqueakClass(), formatOffset);
        }
        if (numSlots == 0) {
            writer.writePadding(SqueakImageConstants.WORD_SIZE); /* Write alignment word. */
            return false;
        } else {
            return true;
        }
    }
}