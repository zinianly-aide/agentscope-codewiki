package com.agentscope.codewiki.web.enterprise;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;

public class SkillHotReloadService {
    private final SkillRepository skillRepository;
    private final AtomicReference<List<SkillDescriptor>> latest = new AtomicReference<>(List.of());

    public SkillHotReloadService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
        reloadSkills();
    }

    @Scheduled(fixedRateString = "PT30S")
    public void reloadSkills() {
        latest.set(skillRepository.listSkills());
    }

    public List<SkillDescriptor> latestSkills() {
        return latest.get();
    }
}
