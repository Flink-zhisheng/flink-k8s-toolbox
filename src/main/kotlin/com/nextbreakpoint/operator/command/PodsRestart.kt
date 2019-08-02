package com.nextbreakpoint.operator.command

import com.nextbreakpoint.common.Kubernetes
import com.nextbreakpoint.common.model.ClusterId
import com.nextbreakpoint.common.model.FlinkOptions
import com.nextbreakpoint.common.model.Result
import com.nextbreakpoint.common.model.ResultStatus
import com.nextbreakpoint.operator.OperatorCommand
import com.nextbreakpoint.operator.resources.ClusterResources
import io.kubernetes.client.apis.AppsV1Api
import org.apache.log4j.Logger

class PodsRestart(flinkOptions: FlinkOptions) : OperatorCommand<ClusterResources, Void?>(flinkOptions) {
    companion object {
        private val logger = Logger.getLogger(PodsRestart::class.simpleName)
    }

    override fun execute(clusterId: ClusterId, params: ClusterResources): Result<Void?> {
        try {
            logger.info("Restarting resources of cluster ${clusterId.name}...")

            restartJobManagerStatefulSets(Kubernetes.appsApi, "flink-operator", clusterId, params)

            restartTaskManagerStatefulSets(Kubernetes.appsApi, "flink-operator", clusterId, params)

            return Result(ResultStatus.SUCCESS, null)
        } catch (e : Exception) {
            logger.error("Can't restart resources of cluster ${clusterId.name}", e)

            return Result(ResultStatus.FAILED, null)
        }
    }

    private fun restartJobManagerStatefulSets(api: AppsV1Api, owner: String, clusterId: ClusterId, resources: ClusterResources) {
        val statefulSets = api.listNamespacedStatefulSet(
            clusterId.namespace,
            null,
            null,
            null,
            null,
            "name=${clusterId.name},uid=${clusterId.uuid},owner=$owner,role=jobmanager",
            null,
            null,
            30,
            null
        )

        statefulSets.items.forEach { statefulSet ->
            try {
                logger.info("Scaling StatefulSet ${statefulSet.metadata.name}...")

                val patch = listOf(
                    mapOf<String, Any?>(
                        "op" to "add",
                        "path" to "/spec/replicas",
                        "value" to (resources.jobmanagerStatefulSet?.spec?.replicas ?: 1)
                    )
                )

                val response = api.patchNamespacedStatefulSetScaleCall(
                    statefulSet.metadata.name,
                    clusterId.namespace,
                    patch,
                    null,
                    null,
                    null,
                    null
                ).execute()

                if (response.isSuccessful) {
                    logger.info("Scaled StatefulSet ${statefulSet.metadata.name}")
                } else {
                    logger.warn("Can't scale StatefulSet ${statefulSet.metadata.name}")
                }
            } catch (e: Exception) {
                logger.warn("Failed to scale StatefulSet ${statefulSet.metadata.name}", e)
                // ignore. see bug https://github.com/kubernetes/kubernetes/issues/59501
            }
        }
    }

    private fun restartTaskManagerStatefulSets(api: AppsV1Api, owner: String, clusterId: ClusterId, resources: ClusterResources) {
        val statefulSets = api.listNamespacedStatefulSet(
            clusterId.namespace,
            null,
            null,
            null,
            null,
            "name=${clusterId.name},uid=${clusterId.uuid},owner=$owner,role=taskmanager",
            null,
            null,
            30,
            null
        )

        statefulSets.items.forEach { statefulSet ->
            try {
                logger.info("Scaling StatefulSet ${statefulSet.metadata.name}...")

                val patch = listOf(
                    mapOf<String, Any?>(
                        "op" to "add",
                        "path" to "/spec/replicas",
                        "value" to (resources.taskmanagerStatefulSet?.spec?.replicas ?: 1)
                    )
                )

                val response = api.patchNamespacedStatefulSetScaleCall(
                    statefulSet.metadata.name,
                    clusterId.namespace,
                    patch,
                    null,
                    null,
                    null,
                    null
                ).execute()

                if (response.isSuccessful) {
                    logger.info("Scaled StatefulSet ${statefulSet.metadata.name}")
                } else {
                    logger.warn("Can't scale StatefulSet ${statefulSet.metadata.name}")
                }
            } catch (e: Exception) {
                logger.warn("Failed to scale StatefulSet ${statefulSet.metadata.name}", e)
                // ignore. see bug https://github.com/kubernetes/kubernetes/issues/59501
            }
        }
    }
}