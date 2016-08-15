package com.r3corda.uniqueness

import akka.actor.ActorRef
import akka.japi.pf.ReceiveBuilder
import akka.persistence.SnapshotOffer
import com.r3corda.protocols.twoparty.Coordinator
import com.rbmhtechnology.eventuate.AbstractEventsourcedActor
import java.io.Serializable
import java.util.*

class UniquenessValidatorEventuate(eventLog: ActorRef) : AbstractEventsourcedActor("uniqueness-validator", eventLog) {
    internal class UniqueTxnReqCmd(val uniquenessRef: Int, val inputStates: List<Int>) : Serializable {
        private val serialVersionUID = 1L;
    }

    internal class UniqueTxnEvt(val inputStates: List<Int>) : Serializable {
        private val serialVersionUID = 1L;
    }

    // validator state is a separate object that can be copied if a snapshot needs to be taken
    internal class ValidatorState(val consumedStates: HashSet<Int> = HashSet()) : Serializable {

        fun update(evt: UniqueTxnEvt) {
            consumedStates.addAll(evt.inputStates)
        }

        fun copy(): ValidatorState {
            return ValidatorState(HashSet<Int>(consumedStates))
        }
    }

    init {
        onReceiveCommand(ReceiveBuilder
                .match(UniqueTxnReqCmd::class.java, { msg: UniqueTxnReqCmd -> doUniqueTransaction(msg) })
                .match(String::class.java, { msg: String -> doStringCommand(msg) })
                .build())
        onReceiveEvent(ReceiveBuilder
                .match(UniqueTxnEvt::class.java, { msg -> validatorState.update(msg) })
                .match(SnapshotOffer::class.java, { msg -> validatorState = msg.snapshot() as ValidatorState })
                .build())
    }

    private fun doStringCommand(msg: String) {
        if (msg.equals("snap")) {
            save(validatorState.copy()) { metadata, err ->
                if (err == null) {
                    // Tell the sender we succeeded
                } else {
                    // Tell the sender we failed
                }
            }
        } else if (msg.equals("print")) {
            println(validatorState)
        } else {
            unhandled(msg)
        }
    }

    private fun doUniqueTransaction(msg: UniqueTxnReqCmd) {
        // first validate the request - that none of the incoming states have previously been consumed
        if (msg.inputStates.intersect(validatorState.consumedStates).isEmpty()) {
            // create an event based on the new states - this can be replayed at time of recovery
            val event = UniqueTxnEvt(msg.inputStates)
            // persist the event
            persist(event) { evt, error ->
                validatorState.update(evt)
                // broadcast event on eventstream
                context().system().eventStream().publish(evt)
            }
            // tell the sender that the states were added successfully
            sender().tell(Coordinator.UniqueTxnResCmd(msg.uniquenessRef), self())
        }
    }

    private var validatorState = ValidatorState()

}