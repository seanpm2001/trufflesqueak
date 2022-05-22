/*
 * Copyright (c) 2017-2022 Software Architecture Group, Hasso Plattner Institute
 * Copyright (c) 2021-2022 Oracle and/or its affiliates
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.plugins;

import java.time.ZonedDateTime;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.trufflesqueak.image.SqueakImageConstants;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.BooleanObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectSizeNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectWriteNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveFallbacks.BinaryPrimitiveFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.trufflesqueak.util.MiscUtils;

public final class NullPlugin extends AbstractPrimitiveFactoryHolder {
    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveScreenScaleFactor")
    protected abstract static class PrimScreenScaleFactorNode extends AbstractPrimitiveNode {
        @Specialization
        protected static final double doGet(@SuppressWarnings("unused") final Object receiver) {
            return 1.0d;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveHighResClock")
    protected abstract static class PrimHighResClockNode extends AbstractPrimitiveNode {
        @Specialization
        protected static final long doHighResClock(@SuppressWarnings("unused") final Object receiver) {
            return System.nanoTime();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveMultipleBytecodeSetsActive")
    protected abstract static class PrimMultipleBytecodeSetsActive0Node extends AbstractPrimitiveNode {
        @Specialization
        protected final boolean doGet(@SuppressWarnings("unused") final Object receiver) {
            return BooleanObject.wrap((getContext().imageFormat & SqueakImageConstants.MULTIPLE_BYTECODE_SETS_BITMASK) != 0);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveMultipleBytecodeSetsActive")
    protected abstract static class PrimMultipleBytecodeSetsActive1Node extends AbstractPrimitiveNode implements BinaryPrimitiveFallback {
        @Specialization
        protected final boolean doSet(@SuppressWarnings("unused") final Object receiver, final boolean value) {
            final int imageFormat = getContext().imageFormat;
            getContext().imageFormat = value ? imageFormat | SqueakImageConstants.MULTIPLE_BYTECODE_SETS_BITMASK : imageFormat & ~SqueakImageConstants.MULTIPLE_BYTECODE_SETS_BITMASK;
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveUtcWithOffset")
    protected abstract static class PrimUtcWithOffset1Node extends AbstractPrimitiveNode {
        @Specialization
        protected final ArrayObject doUTC(@SuppressWarnings("unused") final Object receiver) {
            return getContext().asArrayOfLongs(getUTCMicroseconds(), getOffsetFromGTMInSeconds());
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveUtcWithOffset")
    protected abstract static class PrimUtcWithOffset2Node extends AbstractPrimitiveNode implements BinaryPrimitiveFallback {
        @Specialization(guards = "objectWithTwoSlots.size() == 2")
        protected static final PointersObject doUTC(@SuppressWarnings("unused") final Object receiver, final PointersObject objectWithTwoSlots,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(objectWithTwoSlots, 0, getUTCMicroseconds());
            writeNode.execute(objectWithTwoSlots, 1, getOffsetFromGTMInSeconds());
            return objectWithTwoSlots;
        }

        @Specialization(guards = "sizeNode.execute(arrayWithTwoSlots) == 2", limit = "1")
        protected static final ArrayObject doUTC(@SuppressWarnings("unused") final Object receiver, final ArrayObject arrayWithTwoSlots,
                        @SuppressWarnings("unused") @Cached final ArrayObjectSizeNode sizeNode,
                        @Cached final ArrayObjectWriteNode writeNode) {
            writeNode.execute(arrayWithTwoSlots, 0, getUTCMicroseconds());
            writeNode.execute(arrayWithTwoSlots, 1, getOffsetFromGTMInSeconds());
            return arrayWithTwoSlots;
        }
    }

    private static long getUTCMicroseconds() {
        return MiscUtils.currentTimeMillis() * 1000L;
    }

    @TruffleBoundary
    private static long getOffsetFromGTMInSeconds() {
        return ZonedDateTime.now().getOffset().getTotalSeconds();
    }

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return NullPluginFactory.getFactories();
    }
}
