package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.internal.StartedNode
import kotlin.reflect.KClass

class Answer<out R : Any>(session: FlowSession, override val answer: R, closure: (result: R) -> Unit = {}) : SimpleAnswer<R>(session, closure)

abstract class SimpleAnswer<out R : Any>(private val session: FlowSession, private val closure: (result: R) -> Unit = {}) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val tmp = answer
        closure(tmp)
        session.send(tmp)
    }

    protected abstract val answer: R
}

class NoAnswer(private val closure: () -> Unit = {}) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = closure()
}

inline fun <I : FlowLogic<*>, reified R : FlowLogic<*>> StartedNode<*>.registerInitiatedFlow(initiatingFlowType: KClass<I>, crossinline construct: (session: FlowSession) -> R) {
    internals.internalRegisterFlowFactory(initiatingFlowType.java, InitiatedFlowFactory.Core { session -> construct(session) }, R::class.java, true)
}