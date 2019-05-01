package de.hpi.swa.graal.squeak.nodes.process;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS_SCHEDULER;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithImage;
import de.hpi.swa.graal.squeak.nodes.GetOrCreateContextNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectSizeNode;

public final class WakeHighestPriorityNode extends AbstractNodeWithImage {
    @Child private ArrayObjectReadNode arrayReadNode = ArrayObjectReadNode.create();
    @Child private ArrayObjectSizeNode arraySizeNode = ArrayObjectSizeNode.create();
    private final BranchProfile errorProfile = BranchProfile.create();
    @Child private GetOrCreateContextNode contextNode;

    private WakeHighestPriorityNode(final CompiledCodeObject code) {
        super(code.image);
        contextNode = GetOrCreateContextNode.create(code);
    }

    public static WakeHighestPriorityNode create(final CompiledCodeObject code) {
        return new WakeHighestPriorityNode(code);
    }

    public void executeWake(final VirtualFrame frame) {
        // Return the highest priority process that is ready to run.
        // Note: It is a fatal VM error if there is no runnable process.
        final ArrayObject schedLists = (ArrayObject) image.getScheduler().at0(PROCESS_SCHEDULER.PROCESS_LISTS);
        long p = arraySizeNode.execute(schedLists) - 1;  // index of last indexable field
        PointersObject processList;
        do {
            if (p < 0) {
                errorProfile.enter();
                throw SqueakException.create("scheduler could not find a runnable process");
            }
            processList = (PointersObject) arrayReadNode.execute(schedLists, p--);
        } while (processList.isEmptyList());
        final PointersObject newProcess = processList.removeFirstLinkOfList();
        contextNode.executeGet(frame).transferTo(newProcess);
    }
}