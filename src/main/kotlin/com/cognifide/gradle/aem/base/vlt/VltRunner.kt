package com.cognifide.gradle.aem.base.vlt

import com.cognifide.gradle.aem.api.AemConfig
import com.cognifide.gradle.aem.instance.Instance
import com.cognifide.gradle.aem.internal.PropertyParser
import com.cognifide.gradle.aem.internal.file.FileOperations
import com.cognifide.gradle.aem.pkg.PackagePlugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Internal
import java.io.File

// TODO https://github.com/Cognifide/gradle-aem-plugin/issues/135
class VltRunner(val project: Project) {

    val logger: Logger = project.logger

    val props = PropertyParser(project)

    val config = AemConfig.of(project)

    fun raw(command: String, props: Map<String, Any> = mapOf()) {
        val app = VltApp(project)
        val allProps = mapOf("instances" to config.instances) + props
        val fullCommand = this.props.expand("${config.vaultGlobalOptions} $command".trim(), allProps)

        app.execute(fullCommand)
    }

    fun checkout() {
        val contentDir = File(config.contentPath)
        if (!contentDir.exists()) {
            logger.info("JCR content directory to be checked out does not exist: ${contentDir.absolutePath}")
        }

        checkoutFilter.use {
            raw("checkout --force --filter ${checkoutFilter.file.absolutePath} ${checkoutInstance.httpUrl}/crx/server/crx.default")
        }
    }

    val checkoutFilter by lazy { determineCheckoutFilter() }

    private fun determineCheckoutFilter(): VltFilter {
        val cmdFilterRoots = props.list("aem.checkout.filterRoots")

        return if (cmdFilterRoots.isNotEmpty()) {
            logger.info("Using Vault filter roots specified as command line property: $cmdFilterRoots")
            VltFilter.temporary(project, cmdFilterRoots)
        } else {
            if (config.checkoutFilterPath.isNotBlank()) {
                val configFilter = FileOperations.find(project, config.vaultPath, config.checkoutFilterPath)
                        ?: throw VltException("Vault check out filter file does not exist at path: ${config.checkoutFilterPath} (or under directory: ${config.vaultPath}).")
                VltFilter(configFilter)
            } else {
                val conventionFilter = FileOperations.find(project, config.vaultPath, config.checkoutFilterPaths)
                        ?: throw VltException("None of Vault check out filter file does not exist at one of convention paths: ${config.checkoutFilterPaths}.")
                VltFilter(conventionFilter)
            }
        }
    }

    val checkoutInstance: Instance by lazy { determineCheckoutInstance() }

    private fun determineCheckoutInstance(): Instance {
        val cmdInstanceArg = props.string("aem.checkout.instance")
        if (!cmdInstanceArg.isNullOrBlank()) {
            val cmdInstance = Instance.parse(cmdInstanceArg!!).first()
            cmdInstance.validate()

            logger.info("Using instance specified by command line parameter: $cmdInstance")
            return cmdInstance
        }

        val namedInstance = Instance.filter(project, config.instanceName).firstOrNull()
        if (namedInstance != null) {
            logger.info("Using first instance matching filter '${config.instanceName}': $namedInstance")
            return namedInstance
        }

        val anyInstance = Instance.filter(project, Instance.FILTER_ANY).firstOrNull()
        if (anyInstance != null) {
            logger.info("Using first instance matching filter '${Instance.FILTER_ANY}': $anyInstance")
            return anyInstance
        }

        throw VltException("Vault instance cannot be determined neither by command line parameter nor AEM config.")
    }

    fun clean() {
        val contentDir = File(config.contentPath)
        if (!contentDir.exists()) {
            logger.warn("JCR content directory to be cleaned does not exist: ${contentDir.absolutePath}")
            return
        }

        logger.info("Cleaning using $checkoutFilter")

        val roots = checkoutFilter.rootPaths
                .map { File(contentDir, "${PackagePlugin.JCR_ROOT}/${it.removeSurrounding("/")}") }
                .filter { it.exists() }
        if (roots.isEmpty()) {
            logger.warn("For given filter there is no existing roots to be cleaned.")
            return
        }

        roots.forEach { root ->
            logger.lifecycle("Cleaning root: $root")
            VltCleaner(project, root).clean()
        }
    }

    fun rcp() {
        val sourcePath = project.properties["aem.rcp.source.path"]
                ?: throw VltException("RCP source path is not specified.")
        val targetPath = project.properties["aem.rcp.target.path"] as String?
                ?: throw VltException("RCP target path is not specified.")

        val opts = project.properties["aem.rcp.target.path"] as String? ?: "-b 100 -r -u"

        raw("rcp $opts ${sourceInstance.httpBasicAuthUrl}/crx/-/jcr:root$sourcePath ${targetInstance.httpBasicAuthUrl}/crx/-/jcr:root$targetPath")
    }

    @get:Internal
    private val sourceInstance: Instance
        get() = config.parseInstance(project.properties["aem.rcp.source.instance"] as String?
                ?: throw VltException("RCP source instance is not specified."))

    @get:Internal
    private val targetInstance: Instance
        get() = config.parseInstance(project.properties["aem.rcp.target.instance"] as String?
                ?: throw VltException("RCP target instance is not specified."))

}