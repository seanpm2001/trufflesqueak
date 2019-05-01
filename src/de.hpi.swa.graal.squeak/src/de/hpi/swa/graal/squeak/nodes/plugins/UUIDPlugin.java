package de.hpi.swa.graal.squeak.nodes.plugins;

import java.util.List;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

public final class UUIDPlugin extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return UUIDPluginFactory.getFactories();
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveMakeUUID")
    protected abstract static class PrimMakeUUIDNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimMakeUUIDNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"receiver.isByteType()", "receiver.getByteLength() == 16"})
        protected static final Object doUUID(final NativeObject receiver) {
            final byte[] bytes = receiver.getByteStorage();
            ArrayUtils.fillRandomly(bytes);
            bytes[6] = (byte) (bytes[6] & 0x0F | 0x40); // Version 4
            bytes[8] = (byte) (bytes[8] & 0x3F | 0x80); // Fixed 8..b value
            return receiver;
        }
    }
}