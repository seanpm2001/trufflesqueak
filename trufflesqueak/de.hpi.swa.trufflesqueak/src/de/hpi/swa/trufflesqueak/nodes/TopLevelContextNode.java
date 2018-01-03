package de.hpi.swa.trufflesqueak.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import de.hpi.swa.trufflesqueak.SqueakImageContext;
import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.exceptions.ProcessSwitch;
import de.hpi.swa.trufflesqueak.exceptions.SqueakQuit;
import de.hpi.swa.trufflesqueak.exceptions.TopLevelReturn;
import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.util.KnownClasses.CONTEXT;

public class TopLevelContextNode extends RootNode {
    @CompilationFinal private final SqueakImageContext image;
    @CompilationFinal private final ContextObject initialContext;

    public static TopLevelContextNode create(SqueakLanguage language, ContextObject context) {
        return new TopLevelContextNode(language, context, context.getCodeObject());
    }

    public static TopLevelContextNode create(SqueakLanguage language, Object receiver, CompiledCodeObject code, BaseSqueakObject senderContext) {
        ContextObject newContext = ContextObject.createWriteableContextObject(code.image, code.frameSize());
        newContext.atput0(CONTEXT.METHOD, code);
        newContext.atput0(CONTEXT.INSTRUCTION_POINTER, newContext.getCodeObject().getBytecodeOffset() + 1);
        newContext.atput0(CONTEXT.RECEIVER, receiver);
        newContext.atput0(CONTEXT.SENDER, senderContext);
        // newContext.atput0(CONTEXT.STACKPOINTER, 0); // not needed
        return new TopLevelContextNode(language, newContext, code);
    }

    private TopLevelContextNode(SqueakLanguage language, ContextObject context, CompiledCodeObject code) {
        super(language, code.getFrameDescriptor());
        this.image = code.image;
        this.initialContext = context;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            executeLoop();
        } catch (TopLevelReturn e) {
            return e.getReturnValue();
        } catch (SqueakQuit e) {
            System.out.println("Squeak is quitting...");
            System.exit(e.getExitCode());
        } finally {
            image.display.close();
        }
        throw new RuntimeException("Top level context did not return");
    }

    public void executeLoop() {
        ContextObject activeContext = initialContext;
        while (true) {
            try {
                RootCallTarget target = Truffle.getRuntime().createCallTarget(new MethodContextNode(image.getLanguage(), activeContext, activeContext.getCodeObject()));
                target.call(activeContext.getFrameArguments());
            } catch (ProcessSwitch ps) {
                activeContext = ps.getNewContext();
// } catch (LocalReturn lr) {
// activeContext = unwindContextChainLocal(activeContext, lr.getReturnValue);
// } catch (NonLocalReturn nlr) {
// throw new RuntimeException("Not implemented yet"); // TODO: support NonLocalReturn
// // activeContext = unwindContextChainNonLocal(activeContext, nlr.returnValue);
            }
        }
    }

    private ContextObject unwindContextChainLocal(ContextObject context, Object result) {
        if (context.at0(CONTEXT.SENDER) == image.nil) {
            throw new TopLevelReturn(result);
        }
        context.push(result);
        return context;
    }
}
