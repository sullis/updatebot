/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.updatebot.kind.maven;

import io.fabric8.updatebot.commands.CommandContext;
import io.fabric8.updatebot.commands.PushVersionChangesContext;
import io.fabric8.updatebot.kind.Kind;
import io.fabric8.updatebot.kind.KindDependenciesCheck;
import io.fabric8.updatebot.kind.Updater;
import io.fabric8.updatebot.model.Dependencies;
import io.fabric8.updatebot.model.DependencyVersionChange;
import io.fabric8.updatebot.model.MavenArtifactVersionChange;
import io.fabric8.updatebot.model.MavenArtifactVersionChanges;
import io.fabric8.updatebot.support.Commands;
import io.fabric8.updatebot.support.FileHelper;
import io.fabric8.updatebot.support.MarkupHelper;
import io.fabric8.utils.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 */
public class MavenUpdater implements Updater {
    private static final transient Logger LOG = LoggerFactory.getLogger(MavenUpdater.class);

    // TODO load dynamically!
    String updateBotPluginVersion = "1.0-SNAPSHOT";

    @Override
    public boolean isApplicable(CommandContext context) {
        return FileHelper.isFile(context.file("pom.xml"));
    }

    @Override
    public void addVersionChangesFromSource(CommandContext context, Dependencies dependencyConfig, List<DependencyVersionChange> list) throws IOException {
        File file = context.file("pom.xml");
        if (Files.isFile(file)) {
            // lets run the maven plugin to generate the export versions file
            String configFile = context.getConfiguration().getConfigFile();
            File versionsFile = createVersionsYamlFile(context);
            if (runCommandAndLogOutput(context, "mvn",
                    "io.fabric8.updatebot:updatebot-maven-plugin:" + updateBotPluginVersion + ":export",
                    "-DdestFile=" + versionsFile,
                    "-DupdateBotYaml=" + configFile)) {
                if (!Files.isFile(versionsFile)) {
                    LOG.warn("Should have generated the export versions file " + versionsFile);
                    return;
                }

                MavenArtifactVersionChanges changes;
                try {
                    changes = MarkupHelper.loadYaml(versionsFile, MavenArtifactVersionChanges.class);
                } catch (IOException e) {
                    throw new IOException("Failed to load " + versionsFile + ". " + e, e);
                }
                List<MavenArtifactVersionChange> changeList = changes.getChanges();
                if (list != null) {
                    for (MavenArtifactVersionChange change : changeList) {
                        list.add(change.createDependencyVersionChange());
                    }
                }
            }
        }
    }


    @Override
    public boolean pushVersions(PushVersionChangesContext context) throws IOException {
        File file = context.file("pom.xml");
        boolean answer = false;
        if (Files.isFile(file)) {
            Properties properties = new Properties();
            List<PushVersionChangesContext.Change> changes = context.getChanges();
            for (PushVersionChangesContext.Change change : changes) {
                String name = change.getName();
                String newValue = change.getNewValue();
                properties.put(name, newValue);
            }
            if (!properties.isEmpty()) {
                File versionsFile = createVersionsYamlFile(context);
                String configFile = context.getConfiguration().getConfigFile();
                properties.store(new FileWriter(versionsFile), "Generated by updatebot");
                if (runCommandAndLogOutput(context, "mvn",
                        "io.fabric8.updatebot:updatebot-maven-plugin:" + updateBotPluginVersion + ":update",
                        "-DupdateBotYaml=" + configFile)) {
                    // TODO check for update
                }

            }
        }
        return answer;
    }

    protected File createVersionsYamlFile(CommandContext context) {
        return new File(context.getDir(), "target/updatebot-versions.yaml");
    }

    public static boolean runCommandAndLogOutput(CommandContext context, String... commands) {
        if (Commands.runCommandAndLogOutput(context.getDir(), commands)) {
            // TODO check if we have changed the source at all
            return true;
        }
        return false;
    }

    @Override
    public boolean pullVersions(CommandContext context) throws IOException {
        // TODO
        return false;
    }

    @Override
    public KindDependenciesCheck checkDependencies(CommandContext context, List<DependencyVersionChange> value) {
        // TODO
        return null;
    }

}
