package com.epgis.sggis.agent.skills;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SkillCatalog {

    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile(
            "\\A---\\R(?<metadata>.*?)\\R---(?:\\R|\\z)(?<content>.*)\\z", Pattern.DOTALL);
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    private static final Pattern METADATA_VALUE_PATTERN = Pattern.compile(
            "(?m)^(?<key>name|description):[ \\t]*(?<value>.+?)[ \\t]*$");
    private static final Set<String> RESOURCE_EXTENSIONS = Set.of(".md", ".txt", ".json", ".yaml", ".yml");
    private static final long MAX_RESOURCE_SIZE_BYTES = 100_000;

    private final Path skillsDirectory;

    @Autowired
    public SkillCatalog(@Value("${agent.skills.directory:skills}") String skillsDirectory) {
        this(Path.of(skillsDirectory));
    }

    SkillCatalog(Path skillsDirectory) {
        this.skillsDirectory = skillsDirectory.toAbsolutePath().normalize();
    }

    public List<Skill> discover() {
        if (!Files.isDirectory(skillsDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(skillsDirectory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(skillsDirectory)) {
            Map<String, Skill> skills = new LinkedHashMap<>();
            paths.filter(this::isSkillDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::readSkill)
                    .flatMap(java.util.Optional::stream)
                    .forEach(skill -> skills.putIfAbsent(skill.name(), skill));
            return List.copyOf(skills.values());
        }
        catch (IOException exception) {
            throw new SkillException("无法读取技能目录：" + skillsDirectory, exception);
        }
    }

    public String load(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            return "未找到指定技能。";
        }
        return discover().stream()
                .filter(skill -> skill.name().equals(name))
                .findFirst()
                .map(Skill::content)
                .orElse("未找到指定技能。");
    }

    public String loadResource(String skillName, String relativePath) {
        if (skillName == null || !NAME_PATTERN.matcher(skillName).matches()
                || relativePath == null || relativePath.isBlank()) {
            return "未找到指定技能资源。";
        }

        Path skillDirectory = skillsDirectory.resolve(skillName).normalize();
        if (!skillDirectory.getParent().equals(skillsDirectory)
                || discover().stream().noneMatch(skill -> skill.name().equals(skillName))) {
            return "未找到指定技能资源。";
        }

        Path resource;
        try {
            resource = skillDirectory.resolve(relativePath).normalize();
        }
        catch (java.nio.file.InvalidPathException exception) {
            return "未找到指定技能资源。";
        }
        if (!resource.startsWith(skillDirectory) || resource.equals(skillDirectory)
                || !hasSupportedExtension(resource)
                || !Files.isRegularFile(resource, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(resource)) {
            return "未找到指定技能资源。";
        }

        try {
            if (Files.size(resource) > MAX_RESOURCE_SIZE_BYTES) {
                return "技能资源超过 100 KB，无法加载。";
            }
            return Files.readString(resource, StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new SkillException("无法读取技能资源：" + resource, exception);
        }
    }

    public String prompt() {
        List<Skill> skills = discover();
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder("""
                可用 Agent Skills：
                当用户请求与某个技能描述相关的任务时，必须先调用 loadSkill 获取该技能的完整指令，再依照指令完成任务。不要猜测未加载技能的详细内容。
                """);
        for (Skill skill : skills) {
            prompt.append("- ").append(skill.name()).append(": ").append(skill.description()).append('\n');
        }
        return prompt.toString();
    }

    private boolean isSkillDirectory(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }

    private java.util.Optional<Skill> readSkill(Path directory) {
        Path skillFile = directory.resolve(SKILL_FILE_NAME);
        if (!Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(skillFile)) {
            return java.util.Optional.empty();
        }

        try {
            String source = Files.readString(skillFile, StandardCharsets.UTF_8);
            Matcher frontMatter = FRONT_MATTER_PATTERN.matcher(source);
            if (!frontMatter.matches()) {
                return java.util.Optional.empty();
            }

            Map<String, String> metadata = parseMetadata(frontMatter.group("metadata"));
            String name = metadata.get("name");
            String description = metadata.get("description");
            if (name == null || description == null || !NAME_PATTERN.matcher(name).matches()
                    || description.isBlank() || !name.equals(directory.getFileName().toString())) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new Skill(name, description, source));
        }
        catch (IOException exception) {
            throw new SkillException("无法读取技能文件：" + skillFile, exception);
        }
    }

    private Map<String, String> parseMetadata(String source) {
        Map<String, String> metadata = new LinkedHashMap<>();
        Matcher matcher = METADATA_VALUE_PATTERN.matcher(source);
        while (matcher.find()) {
            metadata.putIfAbsent(matcher.group("key"), unquote(matcher.group("value")));
        }
        return metadata;
    }

    private String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean hasSupportedExtension(Path resource) {
        String fileName = resource.getFileName().toString();
        return RESOURCE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}
