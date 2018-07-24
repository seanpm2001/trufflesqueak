package de.hpi.swa.graal.squeak.nodes.context.stack;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.nodes.SqueakNodeWithCode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackReadNode;

public abstract class AbstractStackNode extends SqueakNodeWithCode {
    @Child private FrameStackReadNode readNode;

    public AbstractStackNode(final CompiledCodeObject code) {
        super(code);
    }

    protected static final int frameStackPointer(final VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, CompiledCodeObject.stackPointerSlot);
    }

    protected static final void setFrameStackPointer(final VirtualFrame frame, final int value) {
        frame.setInt(CompiledCodeObject.stackPointerSlot, value);
    }

    protected final FrameStackReadNode getReadNode() {
        if (readNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readNode = insert(FrameStackReadNode.create());
        }
        return readNode;
    }
}
