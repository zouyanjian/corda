package net.corda.node.statemachine

import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import java.util.concurrent.Executor

/**
 * Created by rossnicoll on 24/07/2016.
 */
class AkkaStateMachineManager(val serviceHub: ServiceHubInternal,
                              tokenizableServices: List<Any>,
                              val checkpointStorage: CheckpointStorage,
                              val executor: Executor) {

}
