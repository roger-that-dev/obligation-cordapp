package net.corda.iou.service

import net.corda.core.node.PluginServiceHub
import net.corda.iou.flow.IOUIssueFlow
import net.corda.iou.flow.IOUSettleFlow
import net.corda.iou.flow.IOUTransferFlow

object IOUService {
    class Service(services: PluginServiceHub) {
        init {
            services.registerFlowInitiator(IOUIssueFlow.Initiator::class.java) { IOUIssueFlow.Responder(it) }
            services.registerFlowInitiator(IOUTransferFlow.Initiator::class.java) { IOUTransferFlow.Responder(it) }
            services.registerFlowInitiator(IOUSettleFlow.Initiator::class.java) { IOUSettleFlow.Responder(it) }

        }
    }
}