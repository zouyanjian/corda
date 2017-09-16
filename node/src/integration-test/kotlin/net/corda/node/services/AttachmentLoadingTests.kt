package net.corda.node.services

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PartyAndReference
import net.corda.core.cordapp.CordappService
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.contracts.isolated.AnotherDummyContract
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProvider
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.node.MockServices
import org.junit.Assert
import org.junit.Test

class AttachmentLoadingTests : TestDependencyInjectionBase() {
    private data class State(val data: String = "test", override val participants: List<AbstractParty> = emptyList()) : ContractState
    private class Services : MockServices() {
        val provider = CordappProvider(attachments, CordappLoader.createDevMode(listOf(isolatedJAR)))
        override val cordappService: CordappService = provider
    }

    companion object {
        private val isolatedJAR = this::class.java.getResource("isolated.jar")!!
        private val emptyJAR = this::class.java.getResource("empty.jar")!!
        private val ISOLATED_CONTRACT_ID = "net.corda.finance.contracts.isolated.AnotherDummyContract"
    }

    @Test
    fun `test a wire transaction has loaded the correct attachment`() {
        val services = Services()
        services.provider.start()
        val contractClass = services.provider.getAppContext(services.provider.cordapps.first()).classLoader.loadClass(ISOLATED_CONTRACT_ID).asSubclass(AnotherDummyContract::class.java)
        val txBuilder = contractClass.newInstance().generateInitial(PartyAndReference(DUMMY_BANK_A, OpaqueBytes(kotlin.ByteArray(1))), 1, DUMMY_NOTARY)
        val ledgerTx = txBuilder.toLedgerTransaction(services)
        ledgerTx.verify()

        val actual = ledgerTx.attachments.first()
        val expected = services.attachments.openAttachment(services.provider.getAppContext(services.provider.cordapps.first()).attachmentId!!)!!

        Assert.assertEquals(expected, actual)
    }
}