package net.corda.testing.node

import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.cordapp.CordappService
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.hexToByteArray
import java.nio.file.Paths

class MockCordappService : CordappService {
    val cordappRegistry = mutableListOf<Pair<Cordapp, AttachmentId>>()

    fun addMockCordapp(contractClassName: ContractClassName, services: ServiceHub) {
        val cordapp = Cordapp(listOf(contractClassName), emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), Paths.get(".").toUri().toURL())
        cordappRegistry.add(Pair(cordapp, services.attachments.importAttachment(contractClassName.toByteArray().inputStream())))
    }
    override fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId? = cordappRegistry.find { it.first.contractClassNames.contains(contractClassName) }?.second
    override fun getAppContext(): CordappContext = TODO()
}