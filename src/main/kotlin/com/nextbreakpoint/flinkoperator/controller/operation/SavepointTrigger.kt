package com.nextbreakpoint.flinkoperator.controller.operation

import com.nextbreakpoint.flinkoperator.common.model.ClusterId
import com.nextbreakpoint.flinkoperator.common.model.FlinkOptions
import com.nextbreakpoint.flinkoperator.common.model.SavepointOptions
import com.nextbreakpoint.flinkoperator.common.model.SavepointRequest
import com.nextbreakpoint.flinkoperator.common.utils.FlinkClient
import com.nextbreakpoint.flinkoperator.common.utils.KubeClient
import com.nextbreakpoint.flinkoperator.controller.core.Operation
import com.nextbreakpoint.flinkoperator.controller.core.OperationResult
import com.nextbreakpoint.flinkoperator.controller.core.OperationStatus
import org.apache.log4j.Logger

class SavepointTrigger(flinkOptions: FlinkOptions, flinkClient: FlinkClient, kubeClient: KubeClient) : Operation<SavepointOptions, SavepointRequest>(flinkOptions, flinkClient, kubeClient) {
    companion object {
        private val logger = Logger.getLogger(SavepointTrigger::class.simpleName)
    }

    override fun execute(clusterId: ClusterId, params: SavepointOptions): OperationResult<SavepointRequest> {
        try {
            val address = kubeClient.findFlinkAddress(flinkOptions, clusterId.namespace, clusterId.name)

            val runningJobs = flinkClient.listRunningJobs(address)

            if (runningJobs.size > 1) {
                logger.warn("[name=${clusterId.name}] There are multiple jobs running")
            }

            if (runningJobs.size != 1) {
                logger.warn("[name=${clusterId.name}] Can't find a running job")

                return OperationResult(
                    OperationStatus.FAILED,
                    SavepointRequest("", "")
                )
            }

            val checkpointingStatistics = flinkClient.getCheckpointingStatistics(address, runningJobs)

            if (checkpointingStatistics.filter { it.value.counts.inProgress > 0 }.isNotEmpty()) {
                logger.warn("[name=${clusterId.name}] Savepoint in progress for job")

                return OperationResult(
                    OperationStatus.RETRY,
                    SavepointRequest("", "")
                )
            }

            val savepointRequests = flinkClient.triggerSavepoints(address, runningJobs, params.targetPath)

            return OperationResult(
                OperationStatus.COMPLETED,
                savepointRequests.map {
                    SavepointRequest(
                        jobId = it.key,
                        triggerId = it.value
                    )
                }.first()
            )
        } catch (e : Exception) {
            logger.error("[name=${clusterId.name}] Can't trigger savepoint for job", e)

            return OperationResult(
                OperationStatus.FAILED,
                SavepointRequest("", "")
            )
        }
    }
}