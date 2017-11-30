package net.corda.node.services.audit

import net.corda.core.context.InvocationContext
import net.corda.core.context.Origin
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.packageName
import net.corda.node.services.api.AuditService
import net.corda.node.services.api.SystemAuditEvent
import net.corda.node.services.config.DatabaseConfig
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.rigorousMock
import org.junit.BeforeClass
import org.junit.Test
import java.time.Instant

class PersistentAuditServiceTest {

    private lateinit var auditService: AuditService

    @BeforeClass
    fun setUp() {
        val schemas = mutableListOf(PersistentAuditService.AuditRecord::class.packageName)
        val dataSourceProps = makeTestDataSourceProperties(nodeName = SecureHash.randomSHA256().toString() + "_audit_")
        val database = configureDatabase(dataSourceProps, DatabaseConfig(), rigorousMock(), NodeSchemaService(schemas))
        auditService = PersistentAuditService(database)
    }

    @Test
    fun recordAuditEvent() {
        val auditEvent = SystemAuditEvent(Instant.now(),
                                          InvocationContext.newInstance(Origin.Shell),
                                          "This is a test system audit event",
                                          mapOf("data 1" to "value 1",
                                                  "data 2" to "value 2",
                                                  "data 3" to "value 3"
                                          ))
        auditService.recordAuditEvent(auditEvent)
    }
}