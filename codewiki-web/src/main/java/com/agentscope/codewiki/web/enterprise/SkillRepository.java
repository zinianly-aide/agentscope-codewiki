package com.agentscope.codewiki.web.enterprise;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local filesystem skill repository that exposes metadata first and full content on demand. */
public class SkillRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillRepository.class);
    private final Path skillsRoot;
    private final long maxSkillFileBytes;

    public SkillRepository(Path skillsRoot, long maxSkillFileBytes) {
        this.skillsRoot = skillsRoot.toAbsolutePath().normalize();
        this.maxSkillFileBytes = maxSkillFileBytes;
    }

    public List<SkillDescriptor> listSkills() {
        if (!Files.isDirectory(skillsRoot)) {
            return List.of();
        }
        List<SkillDescriptor> descriptors = new ArrayList<>();
        Set<String> names = new HashSet<>();
        try (var stream = Files.list(skillsRoot)) {
            for (Path skillFile :
                    stream.filter(Files::isDirectory)
                            .map(path -> path.resolve("SKILL.md"))
                            .filter(Files::isRegularFile)
                            .toList()) {
                SkillDescriptor descriptor = readDescriptor(skillFile);
                if (descriptor == null) {
                    continue;
                }
                if (!names.add(descriptor.name())) {
                    LOGGER.warn("duplicate skill name ignored skillName={}", descriptor.name());
                    continue;
                }
                descriptors.add(descriptor);
            }
        } catch (IOException ex) {
            LOGGER.warn("failed to list skills: {}", ex.getClass().getSimpleName());
            return List.of();
        }
        return List.copyOf(descriptors);
    }

    public int skillCount() {
        return listSkills().size();
    }

    public String readSkill(String name) {
        if (!isSafeSkillName(name)) {
            LOGGER.warn("unsafe skill name rejected");
            return "";
        }
        Path skillPath = skillsRoot.resolve(name).resolve("SKILL.md").toAbsolutePath().normalize();
        if (!skillPath.startsWith(skillsRoot) || !Files.isRegularFile(skillPath)) {
            return "";
        }
        return readSkillFile(skillPath);
    }

    public boolean matchesSmsSkill(String message) {
        String text = message == null ? "" : message;
        return text.contains("短信")
                || text.contains("敏感词")
                || text.contains("回执")
                || text.contains("发送量")
                || text.contains("成功率")
                || text.toLowerCase(Locale.ROOT).contains("sms");
    }

    private SkillDescriptor readDescriptor(Path skillPath) {
        String content = readSkillFile(skillPath);
        if (content.isBlank()) {
            return null;
        }
        String name = valueAfter(content, "name:");
        String description = description(content);
        if (name.isBlank()) {
            name = skillPath.getParent().getFileName().toString();
        }
        if (!isSafeSkillName(name)) {
            LOGGER.warn("invalid skill name ignored");
            return null;
        }
        return new SkillDescriptor(name, description);
    }

    private String readSkillFile(Path skillPath) {
        try {
            Path normalized = skillPath.toAbsolutePath().normalize();
            if (!normalized.startsWith(skillsRoot)) {
                LOGGER.warn("skill path outside root rejected");
                return "";
            }
            long size = Files.size(normalized);
            if (size > maxSkillFileBytes) {
                LOGGER.warn("skill file too large ignored skillFileSize={}", size);
                return "";
            }
            byte[] bytes = Files.readAllBytes(normalized);
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            LOGGER.warn("skill file is not valid UTF-8");
            return "";
        } catch (IOException ex) {
            LOGGER.warn("failed to read skill file: {}", ex.getClass().getSimpleName());
            return "";
        }
    }

    private static boolean isSafeSkillName(String name) {
        return name != null && name.matches("[a-zA-Z0-9_-]{1,64}");
    }

    private static String valueAfter(String content, String key) {
        for (String line : content.split("\\R")) {
            if (line.trim().startsWith(key)) {
                return line.substring(line.indexOf(key) + key.length()).trim();
            }
        }
        return "";
    }

    private static String description(String content) {
        int index = content.indexOf("description:");
        if (index < 0) {
            return "";
        }
        int end = content.indexOf("---", index + 12);
        String block = end < 0 ? content.substring(index + 12) : content.substring(index + 12, end);
        return block.replace(">", "").replace('\n', ' ').trim();
    }
}
