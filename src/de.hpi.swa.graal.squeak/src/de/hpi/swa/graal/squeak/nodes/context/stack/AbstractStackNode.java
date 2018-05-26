package de.hpi.swa.graal.squeak.nodes.context.stack;

import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.nodes.SqueakNodeWithCode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameSlotReadNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameSlotWriteNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameStackReadNode;

public abstract class AbstractStackNode extends SqueakNodeWithCode {
    @Child private FrameSlotReadNode stackPointerReadNode = FrameSlotReadNode.createForStackPointer();
    @Child private FrameSlotWriteNode stackPointerWriteNode = FrameSlotWriteNode.createForStackPointer();
    @Child protected FrameStackReadNode readNode = FrameStackReadNode.create();

    public AbstractStackNode(final CompiledCodeObject code) {
        super(code);
    }

    protected final int frameStackPointer(final VirtualFrame frame) {
        return (int) stackPointerReadNode.executeRead(frame);
    }

    protected final void setFrameStackPointer(final VirtualFrame frame, final int value) {
        stackPointerWriteNode.executeWrite(frame, value);
    }
}
