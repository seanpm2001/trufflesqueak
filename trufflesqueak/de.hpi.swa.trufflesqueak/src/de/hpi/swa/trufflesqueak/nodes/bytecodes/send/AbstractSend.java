package de.hpi.swa.trufflesqueak.nodes.bytecodes.send;

import java.util.Stack;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.nodes.DispatchNode;
import de.hpi.swa.trufflesqueak.nodes.DispatchNodeGen;
import de.hpi.swa.trufflesqueak.nodes.LookupNode;
import de.hpi.swa.trufflesqueak.nodes.LookupNodeGen;
import de.hpi.swa.trufflesqueak.nodes.SqueakNode;
import de.hpi.swa.trufflesqueak.nodes.SqueakTypesGen;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.SqueakBytecodeNode;
import de.hpi.swa.trufflesqueak.nodes.context.SqueakLookupClassNode;
import de.hpi.swa.trufflesqueak.nodes.context.SqueakLookupClassNodeGen;

public class AbstractSend extends SqueakBytecodeNode {
    protected final BaseSqueakObject selector;
    @Child protected SqueakNode receiverNode;
    @Child protected SqueakLookupClassNode lookupClassNode;
    @Children protected final SqueakNode[] argumentNodes;
    @Child private LookupNode lookupNode;
    @Child private DispatchNode dispatchNode;

    public AbstractSend(CompiledCodeObject method, int idx, BaseSqueakObject sel, int argcount) {
        super(method, idx);
        selector = sel;
        argumentNodes = new SqueakNode[argcount];
    }

    @Override
    @ExplodeLoop
    public Object executeGeneric(VirtualFrame frame) {
        Object receiver = receiverNode.executeGeneric(frame);
        ClassObject rcvrClass;
        try {
            rcvrClass = SqueakTypesGen.expectClassObject(lookupClassNode.executeLookup(receiver));
        } catch (UnexpectedResultException e) {
            throw new RuntimeException("receiver has no class");
        }
        CompilerAsserts.compilationConstant(argumentNodes.length);
        Object[] arguments = new Object[argumentNodes.length + 1];
        arguments[0] = receiver;
        for (int i = 0; i < argumentNodes.length; i++) {
            arguments[i + 1] = argumentNodes[i].executeGeneric(frame);
        }
        CompilerAsserts.compilationConstant(argumentNodes.length);
        Object lookupResult = lookupNode.executeLookup(rcvrClass, selector);
        return dispatchNode.executeDispatch(lookupResult, arguments);
        // TODO: OaM
    }

    @Override
    public void interpretOn(Stack<SqueakNode> stack, Stack<SqueakNode> sequence) {
        for (int i = argumentNodes.length - 1; i >= 0; i--) {
            argumentNodes[i] = stack.pop();
        }
        receiverNode = stack.pop();
        if (false) {
            // TODO: cascade
        } else {
            lookupClassNode = SqueakLookupClassNodeGen.create(method);
            dispatchNode = DispatchNodeGen.create();
            lookupNode = LookupNodeGen.create();
        }
        stack.push(this);
    }

    @Override
    public void prettyPrintOn(StringBuilder b) {
        b.append('(');
        receiverNode.prettyPrintOn(b);
        b.append(") ");
        String[] splitSelector = selector.toString().split(":");
        assert argumentNodes.length == splitSelector.length;
        if (splitSelector.length == 1 && !splitSelector[0].matches("[A-Za-z]")) {
            b.append(selector);
            if (argumentNodes.length == 1) {
                b.append(" (");
                argumentNodes[0].prettyPrintOn(b);
                b.append(')');
            }
        } else {
            for (int i = 0; i < argumentNodes.length; i++) {
                b.append(splitSelector[i]).append(": (");
                argumentNodes[i].prettyPrintOn(b);
                b.append(')');
            }
        }
        b.append(')');
    }
}
