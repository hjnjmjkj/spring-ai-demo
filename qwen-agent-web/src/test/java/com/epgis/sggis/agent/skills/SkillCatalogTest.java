package com.epgis.sggis.agent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class SkillCatalogTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversValidSkillsAndBuildsProgressiveDisclosurePrompt() throws IOException {
        writeSkill("map-analysis", """
                ---
                name: map-analysis
                description: "分析地图数据"
                ---
                # 地图分析
                """);

        SkillCatalog catalog = new SkillCatalog(temporaryDirectory);

        assertThat(catalog.discover()).singleElement()
                .extracting(Skill::name, Skill::description)
                .containsExactly("map-analysis", "分析地图数据");
        assertThat(catalog.prompt()).contains("map-analysis: 分析地图数据").doesNotContain("# 地图分析");
        assertThat(catalog.load("map-analysis")).contains("# 地图分析");
    }

    @Test
    void rescansSkillsForEveryOperation() throws IOException {
        SkillCatalog catalog = new SkillCatalog(temporaryDirectory);

        assertThat(catalog.discover()).isEmpty();

        writeSkill("device-search", """
                ---
                name: device-search
                description: 查找设备
                ---
                使用设备查询。
                """);

        assertThat(catalog.discover()).extracting(Skill::name).containsExactly("device-search");
    }

    @Test
    void rejectsInvalidAndUnsafeSkillNames() throws IOException {
        writeSkill("different-directory", """
                ---
                name: another-name
                description: 不应发现
                ---
                内容
                """);
        writeSkill("invalid-metadata", """
                # 缺少 front matter
                """);

        SkillCatalog catalog = new SkillCatalog(temporaryDirectory);

        assertThat(catalog.discover()).isEmpty();
        assertThat(catalog.load("../outside")).isEqualTo("未找到指定技能。");
    }

    @Test
    void toolLoadsOnlyDiscoveredSkillNames() throws IOException {
        writeSkill("power-flow", """
                ---
                name: power-flow
                description: 潮流分析
                ---
                执行潮流分析。
                """);
        SkillToolCallback callback = new SkillToolCallback(
                new SkillCatalog(temporaryDirectory), new ObjectMapper());

        assertThat(callback.call("{\"name\":\"power-flow\"}")).contains("执行潮流分析。");
        assertThat(callback.call("{\"name\":\"../outside\"}")).isEqualTo("未找到指定技能。");
        assertThat(callback.call("{\"other\":\"power-flow\"}")).isEqualTo("未找到指定技能。");
    }

    @Test
    void loadsOnlySupportedResourcesInsideDiscoveredSkillDirectory() throws IOException {
        writeSkill("power-flow", """
                ---
                name: power-flow
                description: 潮流分析
                ---
                读取 references/terms.md。
                """);
        Path references = Files.createDirectories(temporaryDirectory.resolve("power-flow/references"));
        Files.writeString(references.resolve("terms.md"), "节点电压");
        Files.writeString(references.resolve("secret.csv"), "不应读取");
        SkillCatalog catalog = new SkillCatalog(temporaryDirectory);
        SkillResourceToolCallback callback = new SkillResourceToolCallback(catalog, new ObjectMapper());

        assertThat(catalog.loadResource("power-flow", "references/terms.md")).isEqualTo("节点电压");
        assertThat(callback.call("{\"skillName\":\"power-flow\",\"path\":\"references/terms.md\"}"))
                .isEqualTo("节点电压");
        assertThat(catalog.loadResource("power-flow", "../outside.md")).isEqualTo("未找到指定技能资源。");
        assertThat(catalog.loadResource("power-flow", "references/secret.csv")).isEqualTo("未找到指定技能资源。");
    }

    private void writeSkill(String directory, String content) throws IOException {
        Path skillDirectory = Files.createDirectories(temporaryDirectory.resolve(directory));
        Files.writeString(skillDirectory.resolve("SKILL.md"), content);
    }
}
