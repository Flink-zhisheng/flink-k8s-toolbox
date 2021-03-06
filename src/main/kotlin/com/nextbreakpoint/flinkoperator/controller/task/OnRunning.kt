package com.nextbreakpoint.flinkoperator.controller.task

import com.nextbreakpoint.flinkoperator.common.model.ClusterStatus
import com.nextbreakpoint.flinkoperator.common.model.ManualAction
import com.nextbreakpoint.flinkoperator.controller.core.Task
import com.nextbreakpoint.flinkoperator.controller.core.TaskContext
import com.nextbreakpoint.flinkoperator.controller.core.Timeout
import org.apache.log4j.Logger

class OnRunning(logger: Logger) : Task(logger) {
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

        val jobmanagerServiceExists = context.doesJobManagerServiceExists()
        val jobmanagerStatefuleSetExists = context.doesJobManagerStatefulSetExists()
        val taskmanagerStatefulSetExists = context.doesTaskManagerStatefulSetExists()

        if (!jobmanagerServiceExists || !jobmanagerStatefuleSetExists || !taskmanagerStatefulSetExists) {
            context.resetSavepointRequest()
            context.resetManualAction()
            context.setClusterStatus(ClusterStatus.Starting)

            return
        }

        val jobFinishedResult = context.isJobFinished(context.clusterId)

        if (jobFinishedResult.isCompleted()) {
            logger.info("Job has finished")

            context.setDeleteResources(false)
            context.resetManualAction()
            context.setClusterStatus(ClusterStatus.Stopping)

            return
        }

        val jobRunningResult = context.isJobRunning(context.clusterId)

        if (!jobRunningResult.isCompleted()) {
            logger.warn("Job not running")

            context.resetManualAction()
            context.setClusterStatus(ClusterStatus.Failed)

            return
        }

        if (!context.isBatchMode()) {
            val changes = context.computeChanges()

            if (changes.isNotEmpty()) {
                logger.info("Detected changes: ${changes.joinToString(separator = ",")}")

                context.resetManualAction()
                context.setClusterStatus(ClusterStatus.Updating)

                return
            }
        }

        val savepointRequest = context.getSavepointRequest()

        if (savepointRequest != null) {
            val savepointResult = context.getLatestSavepoint(context.clusterId, savepointRequest)

            if (savepointResult.isCompleted()) {
                logger.info("Savepoint created for job ${savepointRequest.jobId} (${savepointResult.output})")

                context.resetSavepointRequest()
                context.setSavepointPath(savepointResult.output)

                return
            }

            val seconds = context.timeSinceLastUpdateInSeconds()

            if (seconds > Timeout.TASK_TIMEOUT) {
                logger.error("Savepoint not created after $seconds seconds")

                context.resetSavepointRequest()

                return
            }
        } else {
            val savepointMode = context.getSavepointMode()

            if (savepointMode?.toUpperCase() == "AUTOMATIC") {
                val savepointIntervalInSeconds = context.getSavepointInterval()

                if (context.timeSinceLastSavepointRequestInSeconds() >= savepointIntervalInSeconds) {
                    val options = context.getSavepointOtions()

                    val response = context.triggerSavepoint(context.clusterId, options)

                    if (response.isCompleted()) {
                        logger.info("Savepoint requested created")

                        context.resetManualAction()
                        context.setSavepointRequest(response.output)

                        return
                    }

                    logger.error("Savepoint request failed. Skipping automatic savepoint")
                }
            }
        }

        val manualAction = context.getManualAction()

        if (manualAction == ManualAction.STOP) {
            context.resetManualAction()
            context.setClusterStatus(ClusterStatus.Stopping)

            return
        }

        if (manualAction == ManualAction.TRIGGER_SAVEPOINT) {
            if (savepointRequest == null) {
                val options = context.getSavepointOtions()

                val response = context.triggerSavepoint(context.clusterId, options)

                if (response.isCompleted()) {
                    logger.info("Savepoint requested created")

                    context.resetManualAction()
                    context.setSavepointRequest(response.output)

                    return
                }

                logger.error("Savepoint request failed. Skipping manual savepoint")
            }
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
            val desiredTaskManagers = context.getDesiredTaskManagers()
            val currentTaskManagers = context.getTaskManagers()

            if (currentTaskManagers != desiredTaskManagers) {
                context.resetManualAction()
                context.setClusterStatus(ClusterStatus.Scaling)

                return
            }
        }
    }
}