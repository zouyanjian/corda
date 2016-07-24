package com.r3corda.uniqueness

import akka.japi.Procedure
import akka.persistence.SnapshotOffer
import com.r3corda.protocols.twoparty.Coordinator
import com.rbmhtechnology.eventuate.AbstractEventsourcedActor
import java.io.Serializable
import java.util.*

class UniquenessValidatorEventuate : AbstractEventsourcedActor() {
    internal class UniqueTxnReqCmd(val uniquenessRef: Int, val inputStates : List<Int>) : Serializable {
        private val serialVersionUID = 1L;
    }

    internal class UniqueTxnEvt(val inputStates : List<Int>) : Serializable {
        private val serialVersionUID = 1L;
    }

    // validator state is a separate object that can be copied if a snapshot needs to be taken
    internal class ValidatorState(val consumedStates: HashSet<Int> = HashSet()) : Serializable {

        fun update(evt: UniqueTxnEvt) {
            consumedStates.addAll(evt.inputStates)
        }

        fun copy() : ValidatorState {
            return ValidatorState(HashSet<Int>(consumedStates))
        }
    }

    override fun onReceiveCommand(msg: Any) {
        if (msg is UniqueTxnReqCmd) {
            // first validate the request - that none of the incoming states have previously been consumed
            if (msg.inputStates.intersect(validatorState.consumedStates).isEmpty()) {
                // create an event based on the new states - this can be replayed at time of recovery
                val event = UniqueTxnEvt(msg.inputStates)
                // persist the event
                persist(event, object : Procedure<UniqueTxnEvt> {
                    @Throws(Exception::class)
                    override fun apply(evt: UniqueTxnEvt) {
                        validatorState.update(evt)
                        // broadcast event on eventstream
                        getContext().system().eventStream().publish(evt)
                    }
                })
                // tell the sender that the states were added successfully
                sender.tell(Coordinator.UniqueTxnResCmd(msg.uniquenessRef), self)
            }
        } else if (msg.equals("snap")) {
            saveSnapshot(validatorState.copy())
        } else if (msg.equals("print")) {
            println(validatorState)
        } else {
            unhandled(msg)
        }
    }

    override fun onReceiveRecover(msg: Any) {
        // mechanism for loading all previously generated events into actor state
        if (msg is UniqueTxnEvt) {
            validatorState.update(msg)
        } else if (msg is SnapshotOffer) {
            validatorState = msg.snapshot() as ValidatorState
        } else {
            unhandled(msg)
        }
    }

    override fun persistenceId(): String {
        return "uniqueness-validator";
    }

    private var validatorState = ValidatorState()

}