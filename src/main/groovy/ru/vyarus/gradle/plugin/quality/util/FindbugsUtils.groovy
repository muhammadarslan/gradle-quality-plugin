package ru.vyarus.gradle.plugin.quality.util

import groovy.xml.XmlUtil
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.tasks.SourceSet
import org.slf4j.Logger
import ru.vyarus.gradle.plugin.quality.QualityExtension

/**
 * Findbugs helper utils.
 *
 * @author Vyacheslav Rusakov
 * @since 17.03.2017
 */
class FindbugsUtils {

    /**
     * Replace exclusion file with extended one when exclusions are required.
     *
     * @param task findbugs task
     * @param extension extension instance
     * @param logger project logger for error messages
     */
    static void replaceExcludeFilter(FindBugs task, QualityExtension extension, Logger logger) {
        Set<File> ignored = FileUtils.resolveIgnoredFiles(task.source, extension.exclude)
        if (extension.excludeSources) {
            // add directly excluded files
            ignored.addAll(extension.excludeSources.asFileTree.matching { include '**/*.java' }.files)
        }
        if (!ignored) {
            // no excluded files
            return
        }
        SourceSet set = FileUtils.findMatchingSet('findbugs', task.name, extension.sourceSets)
        if (!set) {
            logger.error("[Findbugs] Failed to find source set for task ${task.name}: exclusions " +
                    ' will not be applied')
            return
        }
        task.excludeFilter = mergeExcludes(task.excludeFilter, ignored, set.allJava.srcDirs)
    }

    /**
     * Findbugs task is a {@link org.gradle.api.tasks.SourceTask}, but does not properly support exclusions.
     * To overcome this limitation, source exclusions could be added to findbugs exclusions filter xml file.
     *
     * @param src original excludes file (default of user defined)
     * @param exclude files to exclude
     * @param roots source directories (to resolve class files)
     */
    @SuppressWarnings('FileCreateTempFile')
    static File mergeExcludes(File src, Collection<File> exclude, Collection<File> roots) {

        Node xml = new XmlParser().parse(src)
        exclude.each {
            String clazz = FileUtils.extractJavaClass(roots, it)
            if (clazz) {
                Node match = xml.appendNode('Match').appendNode('Class')
                match.attributes().put('name', clazz)
            }
        }

        File tmp = File.createTempFile('findbugs-extended-exclude', '.xml')
        tmp.deleteOnExit()
        tmp.withWriter { XmlUtil.serialize(xml, it) }
        return tmp
    }
}
