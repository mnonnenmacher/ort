/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.cli

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.concurrent.shouldCompleteWithin
import io.kotest.matchers.shouldBe

import java.util.concurrent.TimeUnit

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.analyze
import org.ossreviewtoolkit.downloader.vcs.GitRepo
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult2
import org.ossreviewtoolkit.utils.test.toYaml

class AnalyzerFunTest : WordSpec({
    "An analysis" should {
        "correctly report VcsInfo for git-repo projects" {
            val expectedResultFile = getAssetFile("git-repo-expected-output.yml")
            val pkg = Package.EMPTY.copy(
                vcsProcessed = VcsInfo(
                    type = VcsType.GIT_REPO,
                    url = "https://github.com/oss-review-toolkit/ort-test-data-git-repo?manifest=manifest.xml",
                    revision = "31588aa8f8555474e1c3c66a359ec99e4cd4b1fa"
                )
            )
            val outputDir = createTestTempDir().also { GitRepo().download(pkg, it) }

            val result = analyze(outputDir).toYaml()

            patchActualResult(result, patchStartAndEndTime = true) shouldBe patchExpectedResult2(expectedResultFile)
        }

        "resolve dependencies from other package managers" {
            val definitionFile = getAssetFile("projects/synthetic/spdx-subproject-conan/project-xyz.spdx.yml")
            val expectedResultFile = getAssetFile(
                "projects/synthetic/spdx-project-xyz-expected-output-subproject-conan.yml"
            )

            val result = analyze(definitionFile.parentFile, allowDynamicVersions = true).analyzer!!.result

            result.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }
    }

    "A globally configured 'mustRunAfter'" should {
        "not block when depending on a package manager for which no definition files have been found" {
            val inputDir = createTestTempDir()
            val gradleDefinitionFile = inputDir.resolve("gradle.build").apply { writeText("// Dummy file") }

            val gradleConfig = PackageManagerConfiguration(mustRunAfter = listOf("NPM"))
            val analyzerConfig = AnalyzerConfiguration().copy(packageManagers = mapOf("Gradle" to gradleConfig))
            val repoConfig = RepositoryConfiguration()

            val analyzer = Analyzer(analyzerConfig)
            val gradleFactory = PackageManager.ALL.getValue("Gradle")
            val gradle = gradleFactory.create(inputDir, analyzerConfig, repoConfig)
            val info = Analyzer.ManagedFileInfo(
                inputDir,
                mapOf(gradle to listOf(gradleDefinitionFile)),
                repoConfig
            )

            shouldCompleteWithin(120, TimeUnit.SECONDS) {
                analyzer.analyze(info)
            }
        }
    }
})
