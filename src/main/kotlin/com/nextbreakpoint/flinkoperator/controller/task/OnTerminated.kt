package com.nextbreakpoint.flinkoperator.controller.task

import com.nextbreakpoint.flinkoperator.common.model.ClusterStatus
import com.nextbreakpoint.flinkoperator.common.model.ManualAction
import com.nextbreakpoint.flinkoperator.controller.core.Task
import com.nextbreakpoint.flinkoperator.controller.core.TaskContext
import org.apache.log4j.Logger

class OnTerminated(logger: Logger) : Task(logger) {
    override fun execute(context: TaskContext) {
        if (context.hasBeenDeleted()) {
            logger.info("Removing finalizer from cluster ${context.clusterId.name}")

            context.removeFinalizer()

            return
        }

        if (!terminate(context)) {
            logger.info("Terminating cluster...")

            return
        }

        val changes = context.computeChanges()

        if (changes.contains("JOB_MANAGER") || changes.contains("TASK_MANAGER") || changes.contains("RUNTIME")) {
            logger.info("Detected changes: ${changes.joinToString(separator = ",")}")

            context.updateStatus()
            context.updateDigests()
            context.setClusterStatus(ClusterStatus.Terminated)

            return
        }

        val manualAction = context.getManualAction()

        if (manualAction == ManualAction.START) {
            context.resetManualAction()
            context.setClusterStatus(ClusterStatus.Starting)

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
    }
}