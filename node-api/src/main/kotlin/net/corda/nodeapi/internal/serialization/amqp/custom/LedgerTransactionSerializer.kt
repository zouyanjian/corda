package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A serializer for [LocalDateTime] that uses a proxy object to write out the date and time.
 */
class LedgerTransactionSerializer(factory: SerializerFactory, val classLoader: ClassLoader? = null) : CustomSerializer.Proxy<LedgerTransaction, LedgerTransactionSerializer.Proxy>(LedgerTransaction::class.java, Proxy::class.java, factory) {


    data class Proxy(
            val inputs: List<StateAndRef<ContractState>>,
            val outputs: List<TransactionState<ContractState>>,
            /** Arbitrary data passed to the program of each input state. */
            val commands: List<CommandWithParties<CommandData>>,
            /** A list of [Attachment] objects identified by the transaction that are needed for this transaction to verify. */
            val attachments: List<Attachment>,
            /** The hash of the original serialised WireTransaction. */
            val id: SecureHash,
            val notary: Party?,
            val timeWindow: TimeWindow?,
            val privacySalt: PrivacySalt
    )


    override fun toProxy(obj: LedgerTransaction): LedgerTransactionSerializer.Proxy = Proxy(
        obj.inputs, obj.outputs, obj.commands, obj.attachments, obj.id,
            obj.notary, obj.timeWindow, obj.privacySalt
    )

    override fun fromProxy(proxy: Proxy): LedgerTransaction = LedgerTransaction(
            proxy.inputs, proxy.outputs, proxy.commands, proxy.attachments, proxy.id,
            proxy.notary, proxy.timeWindow, proxy.privacySalt,
            { classLoader }
    )
}