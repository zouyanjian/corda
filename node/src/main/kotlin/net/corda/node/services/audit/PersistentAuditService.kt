package net.corda.node.services.audit

import net.corda.core.utilities.contextLogger
import net.corda.node.services.api.AuditEvent
import net.corda.node.services.api.AuditService
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.currentDBSession
import java.time.Instant
import javax.persistence.*

/**
 * An RDBMS based implementation of the [AuditService] API.
 *
 * This implementation does not assume usage of the same RDBMS as the underlying Corda node.
 */
class PersistentAuditService(val database: CordaPersistence) : AuditService {

    companion object {
        private val log = contextLogger()
    }

    override fun recordAuditEvent(event: AuditEvent) {
        log.info("Recording audit event: $event")
        val auditRecord = AuditRecord(eventType = event.javaClass.canonicalName,
                                      principal = event.context.origin.principal().toString(),
                                      origin = event.context.origin.javaClass.canonicalName,
                                      timestamp = event.timestamp,
                                      description = event.description,
                                      contextData = event.contextData.toMutableMap())
        database.transaction {
            val session = currentDBSession()
            session.use {
                session.save(auditRecord)
            }
        }
    }

    // JPA schema representation of audit record data
    @Entity
    @Table(name = "audit_record_data")
    class AuditRecord(
            @Id
            @GeneratedValue
            @Column(name = "id")
            var id: Long = 0,

            /* audit event type: references an event type that extends the [AuditEvent] class: [FlowAppAuditEvent], [SystemAuditEvent], [RPCAuditEvent] */
            @Column(name = "type")
            var eventType: String,

            /** refers to the identity of the entity triggering this audit record.
             *  specifically, this refers to the [Principal] associated with the [Origin] of the events [InvocationContext] */
            @Column(name = "principal")
            var principal: String,

            /** refers to the origin of the invocation triggering this audit record.
             *  specifically, this refers to the [Origin] associated with the events [InvocationContext]: RPC, Peer, Service, Scheduled, Shell */
            @Column(name = "origin")
            var origin: String,

            /** refers to UTC time point at which the audit event happened */
            @Column(name = "timestamp")
            var timestamp: Instant,

            /** A human readable description of audit event */
            @Column(name = "description")
            var description: String,

            /** Further tagged details that should be recorded along with the common data of the audit event. */
            @ElementCollection
            @CollectionTable(name = "audit_record_contextual_data",
                             joinColumns = arrayOf(JoinColumn(name = "audit_id", referencedColumnName = "id")))
            @Column(name = "contextual_data")
            var contextData: MutableMap<String,String>
    )
}
