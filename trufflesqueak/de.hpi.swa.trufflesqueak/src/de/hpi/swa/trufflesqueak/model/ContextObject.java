package de.hpi.swa.trufflesqueak.model;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.trufflesqueak.nodes.SqueakNode;
import de.hpi.swa.trufflesqueak.nodes.context.TemporaryReadNode;
import de.hpi.swa.trufflesqueak.util.FrameAccess;
import de.hpi.swa.trufflesqueak.util.FrameMarker;

public class ContextObject extends AbstractPointersObject {
    @CompilationFinal private FrameDescriptor frameDescriptor;
    private boolean isDirty;

    public static ContextObject materialize(Frame virtualFrame, SqueakImageContext img) {
        MaterializedFrame frame = virtualFrame.materialize();
        FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
        FrameSlot contextOrMarkerSlot = frameDescriptor.findFrameSlot(CompiledCodeObject.SLOT_IDENTIFIER.THIS_CONTEXT_OR_MARKER);
        Object contextOrMarker = FrameUtil.getObjectSafe(frame, contextOrMarkerSlot);
        if (contextOrMarker instanceof ContextObject) {
            return (ContextObject) contextOrMarker;
        } else if (contextOrMarker instanceof FrameMarker) {
            CompiledCodeObject method = FrameAccess.getMethod(frame);
            // do not attach ReadOnlyContextObject to thisContextSlot to avoid becoming non-virtualized
            return new ContextObject(img, frame, method);
        }
        throw new RuntimeException(String.format("Expected ContextObject or FrameMarker, got: %s.", contextOrMarker));
    }

    public static ContextObject create(SqueakImageContext img) {
        return new ContextObject(img);
    }

    public static ContextObject create(SqueakImageContext img, int size) {
        return new ContextObject(img, size);
    }

    public static ContextObject create(CompiledCodeObject code, VirtualFrame frame, int pc, int sp) {
        ContextObject context = create(code.image, code.frameSize());
        context.atput0(CONTEXT.METHOD, code);
        context.setSender(FrameAccess.getSender(frame));
        context.atput0(CONTEXT.INSTRUCTION_POINTER, pc);
        context.atput0(CONTEXT.RECEIVER, FrameAccess.getReceiver(frame));
        BlockClosureObject closure = FrameAccess.getClosure(frame);
        context.atput0(CONTEXT.CLOSURE_OR_NIL, closure == null ? code.image.nil : closure);
        context.atput0(CONTEXT.STACKPOINTER, sp);
        return context;
    }

    private ContextObject(SqueakImageContext img) {
        super(img);
    }

    private ContextObject(SqueakImageContext img, int size) {
        this(img);
        pointers = new Object[CONTEXT.TEMP_FRAME_START + size];
    }

    private ContextObject(SqueakImageContext img, Frame frame, CompiledCodeObject method) {
        this(img, method.frameSize());
        frameDescriptor = frame.getFrameDescriptor();
        BlockClosureObject closure = FrameAccess.getClosure(frame);

        setSender(FrameAccess.getSender(frame));
        atput0(CONTEXT.INSTRUCTION_POINTER, method.getInitialPC());
        int sp = FrameAccess.getStackPointer(frame);
        atput0(CONTEXT.STACKPOINTER, sp + 1);
        atput0(CONTEXT.METHOD, method);
        atput0(CONTEXT.CLOSURE_OR_NIL, closure == null ? image.nil : closure);
        atput0(CONTEXT.RECEIVER, FrameAccess.getReceiver(frame));

        for (int i = 0; i <= sp; i++) {
            SqueakNode readNode = TemporaryReadNode.create(method, i);
            Object tempValue = readNode.executeGeneric((VirtualFrame) frame);
            atTempPut(i, tempValue);
        }
    }

    private ContextObject(ContextObject original) {
        super(original.image);
        pointers = original.pointers;
    }

    @Override
    public ClassObject getSqClass() {
        return image.methodContextClass;
    }

    public void terminate() {
        atput0(CONTEXT.INSTRUCTION_POINTER, image.nil);
        setSender(image.nil); // remove sender
    }

    @Override
    public Object at0(int index) {
        assert index >= 0;
        if (index == CONTEXT.SENDER_OR_NIL) {
            return getSender(); // sender might need to be reconstructed
        }
        return super.at0(index);
    }

    @Override
    public void atput0(int index, Object value) {
        assert index >= 0 && value != null;
        if (index == CONTEXT.SENDER_OR_NIL) {
            isDirty = true;
        }
        super.atput0(index, value);
    }

    @Override
    public int instsize() {
        return CONTEXT.TEMP_FRAME_START;
    }

    public CompiledCodeObject getCodeObject() {
        BlockClosureObject closure = getClosure();
        if (closure != null) {
            return closure.getCompiledBlock();
        }
        return getMethod();
    }

    public CompiledCodeObject getMethod() {
        return (CompiledCodeObject) at0(CONTEXT.METHOD);
    }

    @Override
    public BaseSqueakObject shallowCopy() {
        return new ContextObject(this);
    }

    public Object[] getReceiverAndArguments() {
        int numArgs = getCodeObject().getNumArgsAndCopiedValues();
        Object[] arguments = new Object[1 + numArgs];
        BlockClosureObject closure = getClosure();
        arguments[0] = closure != null ? closure.getReceiver() : at0(CONTEXT.RECEIVER);
        for (int i = 0; i < numArgs; i++) {
            arguments[1 + i] = at0(CONTEXT.TEMP_FRAME_START + i);
        }
        return arguments;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public BaseSqueakObject getSender() {
        Object sender = super.at0(CONTEXT.SENDER_OR_NIL);
        if (sender instanceof ContextObject || sender instanceof NilObject) {
            return (BaseSqueakObject) sender;
        } else if (sender instanceof FrameMarker) { // null indicates virtual frame, reconstructing contexts...
            ContextObject reconstructedSender = FrameAccess.findContextForMarker((FrameMarker) sender, image);
            if (reconstructedSender == null) {
                throw new RuntimeException("Unable to find sender");
            }
            setSender(reconstructedSender);
            return reconstructedSender;
        }
        throw new RuntimeException("Unexpected sender: " + sender);
    }

    public ContextObject getNotNilSender() {
        Object sender = super.at0(CONTEXT.SENDER_OR_NIL);
        if (sender instanceof ContextObject) {
            return (ContextObject) sender;
        } else if (sender instanceof FrameMarker) { // null indicates virtual frame, reconstructing contexts...
            ContextObject reconstructedSender = FrameAccess.findContextForMarker((FrameMarker) sender, image);
            if (reconstructedSender == null) {
                throw new RuntimeException("Unable to find sender");
            }
            setSender(reconstructedSender);
            return reconstructedSender;
        }
        throw new RuntimeException("Unexpected sender: " + sender);
    }

    /*
     * Set sender without flagging context as dirty.
     */
    public void setSender(Object sender) {
        super.atput0(CONTEXT.SENDER_OR_NIL, sender);
    }

    public void push(Object value) {
        assert value != null;
        int newSP = stackPointer() + 1;
        atput0(newSP, value);
        setStackPointer(newSP);
    }

    private int stackPointer() {
        return decodeSqueakStackPointer((int) at0(CONTEXT.STACKPOINTER));
    }

    private void setStackPointer(int newSP) {
        int encodedSP = toSqueakStackPointer(newSP);
        assert encodedSP >= -1;
        atput0(CONTEXT.STACKPOINTER, encodedSP);
    }

    @Override
    public String toString() {
        return String.format("Context for %s", at0(CONTEXT.METHOD));
    }

    public Object top() {
        return peek(0);
    }

    public Object peek(int offset) {
        return at0(stackPointer() - offset);
    }

    public Object pop() {
        int sp = stackPointer();
        setStackPointer(sp - 1);
        return at0(sp);
    }

    public Object[] popNReversed(int numPop) {
        int sp = stackPointer();
        assert sp - numPop >= 0;
        Object[] result = new Object[numPop];
        for (int i = 0; i < numPop; i++) {
            result[numPop - 1 - i] = at0(sp - i);
        }
        setStackPointer(sp - numPop);
        return result;
    }

    public Object getReceiver() {
        return at0(CONTEXT.RECEIVER);
    }

    public Object atTemp(int argumentIndex) {
        return at0(CONTEXT.TEMP_FRAME_START + argumentIndex);
    }

    public void atTempPut(int argumentIndex, Object value) {
        atput0(CONTEXT.TEMP_FRAME_START + argumentIndex, value);
    }

    public BlockClosureObject getClosure() {
        Object closureOrNil = at0(CONTEXT.CLOSURE_OR_NIL);
        return closureOrNil == image.nil ? null : (BlockClosureObject) closureOrNil;
    }

    /*
     * pc is offset by the initial pc
     */
    public static int encodeSqPC(int pc, CompiledCodeObject code) {
        return pc + code.getInitialPC();
    }

    public static int decodeSqPC(int pc, CompiledCodeObject code) {
        return pc - code.getInitialPC();
    }

    /*
     * sp is offset by CONTEXT.TEMP_FRAME_START, -1 for zero-based addressing
     */
    public static int toSqueakStackPointer(int sp) {
        return sp - (CONTEXT.TEMP_FRAME_START - 1);
    }

    public static int decodeSqueakStackPointer(int sp) {
        return sp + (CONTEXT.TEMP_FRAME_START - 1);
    }
}
