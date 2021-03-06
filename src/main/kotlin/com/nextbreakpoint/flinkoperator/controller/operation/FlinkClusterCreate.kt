package com.nextbreakpoint.flinkoperator.controller.operation

import com.nextbreakpoint.flinkoperator.common.crd.V1FlinkCluster
import com.nextbreakpoint.flinkoperator.common.model.ClusterId
import com.nextbreakpoint.flinkoperator.common.model.FlinkOptions
import com.nextbreakpoint.flinkoperator.common.utils.FlinkClient
import com.nextbreakpoint.flinkoperator.common.utils.KubeClient
import com.nextbreakpoint.flinkoperator.controller.core.Operation
import com.nextbreakpoint.flinkoperator.controller.core.OperationResult
import com.nextbreakpoint.flinkoperator.controller.core.OperationStatus
import org.apache.log4j.Logger

class FlinkClusterCreate(flinkOptions: FlinkOptions, flinkClient: FlinkClient, kubeClient: KubeClient) : Operation<V1FlinkCluster, Void?>(flinkOptions, flinkClient, kubeClient) {
    companion object {
        private val logger = Logger.getLogger(FlinkClusterCreate::class.simpleName)
    }

    override fun execute(clusterId: ClusterId, params: V1FlinkCluster): OperationResult<Void?> {
        try {
            val flinkCluster = V1FlinkCluster()
                .apiVersion("nextbreakpoint.com/v1")
                .kind("FlinkCluster")
                .metadata(params.metadata)
                .spec(params.spec)

            val response = kubeClient.createFlinkCluster(flinkCluster)

            if (response.statusCode == 201) {
                logger.info("[name=${clusterId.name}] Custom object created")

                return OperationResult(
                    OperationStatus.COMPLETED,
                    null
                )
            } else {
                logger.error("[name=${clusterId.name}] Can't create custom object")

                return OperationResult(
                    OperationStatus.FAILED,
                    null
                )
            }
        } catch (e : Exception) {
            logger.error("[name=${clusterId.name}] Can't create cluster resource", e)

            return OperationResult(
                OperationStatus.FAILED,
                null
            )
        }
    }
}