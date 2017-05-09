package de.hpi.swa.trufflesqueak.model;

import java.util.Vector;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.utilities.CyclicAssumption;

import de.hpi.swa.trufflesqueak.SqueakImageContext;
import de.hpi.swa.trufflesqueak.exceptions.UnwrappingError;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.SqueakBytecodeNode;
import de.hpi.swa.trufflesqueak.nodes.roots.SqueakMethodNode;
import de.hpi.swa.trufflesqueak.util.BitSplitter;
import de.hpi.swa.trufflesqueak.util.Chunk;
import de.hpi.swa.trufflesqueak.util.Decompiler;

public abstract class CompiledCodeObject extends SqueakObject {
    // constants
    public static final String CLOSURE = "closure";
    public static final String SELF = "self";
    public static final String RECEIVER = "receiver";
    public static final String PC = "pc";
    public static final String STACK_POINTER = "stackPointer";
    public static final String MARKER = "marker";
    // code
    protected byte[] bytes;
    private SqueakBytecodeNode[] ast;
    // frame info
    private FrameDescriptor frameDescriptor;
    @CompilationFinal public FrameSlot receiverSlot;
    @CompilationFinal public FrameSlot selfSlot;
    @CompilationFinal public FrameSlot closureSlot;
    @CompilationFinal public FrameSlot stackPointerSlot;
    @CompilationFinal public FrameSlot pcSlot;
    @CompilationFinal(dimensions = 1) public FrameSlot[] stackSlots;
    @CompilationFinal public FrameSlot markerSlot;
    private RootCallTarget callTarget;
    private final CyclicAssumption callTargetStable = new CyclicAssumption("Compiled method assumption");
    // header info and data
    @CompilationFinal(dimensions = 1) protected BaseSqueakObject[] literals;
    @CompilationFinal protected int numArgs;
    @SuppressWarnings("unused") private int numLiterals;
    @SuppressWarnings("unused") private boolean isOptimized;
    protected boolean hasPrimitive;
    boolean needsLargeFrame;
    @CompilationFinal int numTemps;
    @SuppressWarnings("unused") private int accessModifier;
    @SuppressWarnings("unused") private boolean altInstructionSet;

    abstract public NativeObject getCompiledInSelector();

    abstract public ClassObject getCompiledInClass();

    public CompiledCodeObject(SqueakImageContext img) {
        this(img, img.compiledMethodClass);
    }

    public CompiledCodeObject(SqueakImageContext img, BaseSqueakObject klass) {
        super(img, klass);
    }

    @TruffleBoundary
    protected void setBytesAndLiterals(BaseSqueakObject[] lits, byte[] bc) {
        literals = lits;
        bytes = bc;
        decodeHeader();
        prepareFrameDescriptor();
        ast = new Decompiler(this).getAST();
        callTarget = Truffle.getRuntime().createCallTarget(new SqueakMethodNode(image.getLanguage(), this));
        callTargetStable.invalidate();
    }

    private void prepareFrameDescriptor() {
        frameDescriptor = new FrameDescriptor(image.nil);
        int squeakFrameSize = 16;
        if (needsLargeFrame) {
            squeakFrameSize = 40;
        }
        stackSlots = new FrameSlot[squeakFrameSize];
        for (int i = 0; i < numTemps + numArgs; i++) {
            stackSlots[i] = frameDescriptor.addFrameSlot(i, FrameSlotKind.Illegal);
        }
        pcSlot = frameDescriptor.addFrameSlot(PC, FrameSlotKind.Int);
        stackPointerSlot = frameDescriptor.addFrameSlot(STACK_POINTER, FrameSlotKind.Int);
        receiverSlot = frameDescriptor.addFrameSlot(RECEIVER, FrameSlotKind.Illegal);
        selfSlot = frameDescriptor.addFrameSlot(SELF, FrameSlotKind.Object);
        closureSlot = frameDescriptor.addFrameSlot(CLOSURE, FrameSlotKind.Object);
        markerSlot = frameDescriptor.addFrameSlot(MARKER, FrameSlotKind.Object);
    }

    public VirtualFrame createTestFrame(BaseSqueakObject receiver) {
        return createTestFrame(receiver, new BaseSqueakObject[]{});
    }

    public VirtualFrame createTestFrame(BaseSqueakObject receiver, BaseSqueakObject[] arguments) {
        Object[] args = new Object[arguments.length + 1];
        int i = 0;
        args[i++] = receiver;
        for (BaseSqueakObject o : arguments) {
            args[i++] = o;
        }
        return Truffle.getRuntime().createVirtualFrame(args, frameDescriptor);
    }

    public RootCallTarget getCallTarget() {
        if (callTarget == null) {
            CompilerDirectives.transferToInterpreter();
            setBytesAndLiterals(literals, bytes);
        }
        return callTarget;
    }

    public Assumption getCallTargetStable() {
        return callTargetStable.getAssumption();
    }

    public ForeignAccess getForeignAccess() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String toString() {
        String className = "UnknownClass";
        String selector = "unknownSelector";
        ClassObject classObject = getCompiledInClass();
        if (classObject != null) {
            className = classObject.nameAsClass();
        }
        NativeObject selectorObj = getCompiledInSelector();
        if (selectorObj != null) {
            selector = selectorObj.toString();
        }
        return className + ">>" + selector;
    }

    public SqueakBytecodeNode[] getBytecodeAST() {
        return ast;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public final int getNumTemps() {
        return numTemps;
    }

    public final int getNumArgs() {
        return numArgs;
    }

    public void setHeader(int hdr) {
        setLiteral(0, image.wrapInt(hdr));
    }

    public void setBytes(byte[] bc) {
        setBytesAndLiterals(literals, bc);
    }

    public void setLiterals(BaseSqueakObject[] lits) {
        setBytesAndLiterals(lits, bytes);
    }

    @Override
    public void fillin(Chunk chunk) {
        super.fillin(chunk);
        Vector<Integer> data = chunk.data();
        int header = data.get(0) >> 1; // header is a tagged small integer
        int literalsize = header & 0x7fff;
        BaseSqueakObject[] ptrs = chunk.getPointers(literalsize + 1);
        literals = ptrs;
        bytes = chunk.getBytes(ptrs.length);
    }

    void decodeHeader() {
        int hdr = getHeader();
        int[] splitHeader = BitSplitter.splitter(hdr, new int[]{15, 1, 1, 1, 6, 4, 2, 1});
        numLiterals = splitHeader[0];
        isOptimized = splitHeader[1] == 1;
        hasPrimitive = splitHeader[2] == 1;
        needsLargeFrame = splitHeader[3] == 1;
        numTemps = splitHeader[4];
        numArgs = splitHeader[5];
        accessModifier = splitHeader[6];
        altInstructionSet = splitHeader[7] == 1;
    }

    public int getHeader() {
        BaseSqueakObject baseSqueakObject = literals[0];
        assert baseSqueakObject instanceof SmallInteger;
        int hdr = (int) baseSqueakObject.unsafeUnwrapInt();
        return hdr;
    }

    public void setNumArgs(int num) {
        int hdrWithoutArgs = getHeader() & ~(0xF << 24);
        setHeader((num & 0xF << 24) | hdrWithoutArgs);
    }

    public void setNumTemps(int num) {
        int hdrWithoutTemps = getHeader() & ~(0x3F << 18);
        setHeader((num & 0x3F << 18) | hdrWithoutTemps);
    }

    @Override
    public boolean become(BaseSqueakObject other) {
        if (other instanceof CompiledMethodObject) {
            if (super.become(other)) {
                BaseSqueakObject[] literals2 = ((CompiledCodeObject) other).literals;
                byte[] bytes2 = ((CompiledCodeObject) other).bytes;
                ((CompiledCodeObject) other).setBytesAndLiterals(literals, bytes);
                this.setBytesAndLiterals(literals2, bytes2);
                return true;
            }
        }
        return false;
    }

    public int getBytecodeOffset() {
        return literals.length * 4;
    }

    @Override
    public int size() {
        return literals.length * 4 + bytes.length;
    }

    @Override
    public BaseSqueakObject at0(int idx) {
        if (idx < literals.length) {
            return literals[idx / 4];
        } else {
            return image.wrapInt(bytes[idx]);
        }
    }

    @Override
    public void atput0(int idx, BaseSqueakObject obj) throws UnwrappingError {
        if (idx < literals.length) {
            setLiteral(idx / 4, obj);
        } else {
            bytes[idx] = (byte) obj.unwrapInt();
            setBytesAndLiterals(literals, bytes);
        }
    }

    public BaseSqueakObject getLiteral(int idx) {
        if (literals.length > idx + 1) {
            return literals[idx + 1];
        } else {
            return literals[0];
        }
    }

    public void setLiteral(int i, BaseSqueakObject obj) {
        literals[i] = obj;
        setBytesAndLiterals(literals, bytes);
    }

    @Override
    public int instsize() {
        return 0;
    }

    public boolean hasPrimitive() {
        return hasPrimitive;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public BaseSqueakObject[] getLiterals() {
        return literals;
    }
}