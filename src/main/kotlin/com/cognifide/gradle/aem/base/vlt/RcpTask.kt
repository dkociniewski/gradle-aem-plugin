package com.cognifide.gradle.aem.base.vlt

import com.cognifide.gradle.aem.api.AemDefaultTask
import org.gradle.api.tasks.TaskAction

open class RcpTask : AemDefaultTask() {

    companion object {
        val NAME = "aemRcp"
    }

    init {
        description = "Copy JCR content from instance to another."
    }

    @TaskAction
    fun rcp() {
        val runner = VltRunner(project)
        val summary = runner.rcp()

        logger.info("RCP details: $summary")

        if (!runner.rcpSourceInstance.cmd && !runner.rcpTargetInstance.cmd) {
            notifier.default("RCP finished", "Copied ${summary.copiedPaths} JCR root(s) from instance ${runner.rcpSourceInstance.name} to ${runner.rcpTargetInstance.name}.")
        } else {
            notifier.default("RCP finished", "Copied ${summary.copiedPaths} JCR root(s) between instances.")
        }
    }

}