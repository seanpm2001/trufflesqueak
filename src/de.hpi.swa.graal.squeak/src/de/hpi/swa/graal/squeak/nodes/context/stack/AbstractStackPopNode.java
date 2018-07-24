package de.hpi.swa.graal.squeak.nodes.context.stack;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.nodes.accessing.CompiledCodeNodes.GetCompiledMethodNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackWriteNode;

public abstract class AbstractStackPopNode extends AbstractStackNode {
    @Child private GetCompiledMethodNode compiledMethodNode = GetCompiledMethodNode.create();
    @Child private FrameStackWriteNode writeNode;

    public AbstractStackPopNode(final CompiledCodeObject code) {
        super(code);
    }

    protected final Object atStackAndClear(final VirtualFrame frame, final int index) {
        final Object value = getReadNode().execute(frame, index);
        final CompiledMethodObject method = compiledMethodNode.execute(code);
        if (index >= 1 + method.getNumArgs() + method.getNumTemps()) {
            // only nil out stack values, not receiver, arguments, or temporary variables
            getWriteNode().execute(frame, index, code.image.nil);
        }
        return value;
    }

    protected final Object atStackAndClear(final ContextObject context, final long argumentIndex) {
        final Object value = context.atStack(argumentIndex);
        final CompiledMethodObject method = compiledMethodNode.execute(code);
        if (argumentIndex >= 1 + method.getNumArgs() + method.getNumTemps()) {
            // only nil out stack values, not receiver, arguments, or temporary variables
            context.atStackPut(argumentIndex, code.image.nil);
        }
        return value;
    }

    private FrameStackWriteNode getWriteNode() {
        if (writeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            writeNode = insert(FrameStackWriteNode.create());
        }
        return writeNode;
    }
}
