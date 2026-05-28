package com.galacticodyssey.mission.job;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class JobRegistry {
    private final Map<String, JobTemplate> templates = new HashMap<>();

    public void register(JobTemplate template) { templates.put(template.id, template); }
    public JobTemplate get(String id) { return templates.get(id); }
    public Collection<JobTemplate> getAll() { return templates.values(); }
}
