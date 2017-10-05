package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.internal.StartedNode
import kotlin.reflect.KClass

/**
 * Allows to simplify writing flows that simply rend a message back to an initiating flow.
 */
class Answer<out R : Any>(session: FlowSession, override val answer: R, closure: (result: R) -> Unit = {}) : SimpleAnswer<R>(session, closure)

/**
 * Allows to simplify writing flows that simply rend a message back to an initiating flow.
 */
abstract class SimpleAnswer<out R : Any>(private val session: FlowSession, private val closure: (result: R) -> Unit = {}) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val tmp = answer
        closure(tmp)
        session.send(tmp)
    }

    protected abstract val answer: R
}

/**
 * A flow that does not do anything when triggered.
 */
class NoAnswer(private val closure: () -> Unit = {}) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = closure()
}

/**
 * Allows to register a flow of type [R] against an initiating flow of type [I].
 */
inline fun <I : FlowLogic<*>, reified R : FlowLogic<*>> StartedNode<*>.registerInitiatedFlow(initiatingFlowType: KClass<I>, crossinline construct: (session: FlowSession) -> R) {
    internals.internalRegisterFlowFactory(initiatingFlowType.java, InitiatedFlowFactory.Core { session -> construct(session) }, R::class.java, true)
}