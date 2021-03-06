package com.nextbreakpoint.flinkoperator.controller.task

import com.nextbreakpoint.flinkoperator.common.model.ClusterStatus
import com.nextbreakpoint.flinkoperator.common.model.ManualAction
import com.nextbreakpoint.flinkoperator.controller.core.Task
import com.nextbreakpoint.flinkoperator.controller.core.TaskContext
import org.apache.log4j.Logger

class OnFailed(logger: Logger) : Task(logger) {
    override fun execute(context: TaskContext) {
        if (context.hasBeenDeleted()) {
            context.setDeleteResources(true)
            context.resetManualAction()
            context.setClusterStatus(ClusterStatus.Stopping)

            return
        }

        if (context.doesBootstrapExists()) {
            val bootstrapResult = context.deleteBootstrapJob(context.clusterId)

            if (bootstrapResult.isCompleted()) {
                logger.info("Bootstrap job deleted")
            }

            return
        }

        val jobRunningResult = context.isJobRunning(context.clusterId)

        if (jobRunningResult.isCompleted()) {
            logger.info("Job running")

            context.setClusterStatus(ClusterStatus.Running)

            return
        }

        val manualAction = context.getManualAction()

        if (manualAction == ManualAction.START) {
            context.resetManualAction()
            context.setClusterStatus(ClusterStatus.Starting)

            return
        }

        if (manualAction == ManualAction.STOP) {
            context.resetManualAction()
            context.setDeleteResources(true)
            context.setClusterStatus(ClusterStatus.Stopping)

            return
        }

        if (manualAction == ManualAction.FORGET_SAVEPOINT) {
            context.resetManualAction()
            context.setSavepointPath("")

            logger.info("Savepoint forgotten")

            return
        }

        if (manualAction != ManualAction.NONE) {
            context.resetManualAction()

            return
        }

        if (!context.isBatchMode()) {
            if (context.getJobRestartPolicy() == "Always") {
                val changes = context.computeChanges()

                if (changes.isNotEmpty()) {
                    logger.info("Detected changes: ${changes.joinToString(separator = ",")}")

                    context.setClusterStatus(ClusterStatus.Updating)

                    return
                }
            }
        }
    }
}