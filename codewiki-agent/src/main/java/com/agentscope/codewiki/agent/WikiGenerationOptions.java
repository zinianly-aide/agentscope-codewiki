package com.agentscope.codewiki.agent;

import java.util.EnumSet;
import java.util.Set;

/** Options controlling full, incremental, or single-page wiki generation. */
public class WikiGenerationOptions {

    private final boolean incremental;
    private final boolean mermaidEnabled;
    private final Set<WikiPage> pages;

    private WikiGenerationOptions(
            boolean incremental, boolean mermaidEnabled, Set<WikiPage> pages) {
        this.incremental = incremental;
        this.mermaidEnabled = mermaidEnabled;
        this.pages = pages.isEmpty() ? EnumSet.allOf(WikiPage.class) : EnumSet.copyOf(pages);
    }

    public static WikiGenerationOptions fullRegeneration() {
        return new WikiGenerationOptions(false, true, EnumSet.allOf(WikiPage.class));
    }

    public static WikiGenerationOptions incremental() {
        return new WikiGenerationOptions(true, true, EnumSet.allOf(WikiPage.class));
    }

    public static WikiGenerationOptions singlePage(WikiPage page) {
        return new WikiGenerationOptions(false, true, EnumSet.of(page));
    }

    public boolean isIncremental() {
        return incremental;
    }

    public boolean isMermaidEnabled() {
        return mermaidEnabled;
    }

    public Set<WikiPage> getPages() {
        return EnumSet.copyOf(pages);
    }

    public Builder toBuilder() {
        return new Builder().incremental(incremental).mermaidEnabled(mermaidEnabled).pages(pages);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link WikiGenerationOptions}. */
    public static final class Builder {
        private boolean incremental;
        private boolean mermaidEnabled = true;
        private Set<WikiPage> pages = EnumSet.allOf(WikiPage.class);

        private Builder() {}

        public Builder incremental(boolean incremental) {
            this.incremental = incremental;
            return this;
        }

        public Builder mermaidEnabled(boolean mermaidEnabled) {
            this.mermaidEnabled = mermaidEnabled;
            return this;
        }

        public Builder pages(Set<WikiPage> pages) {
            this.pages =
                    pages == null || pages.isEmpty()
                            ? EnumSet.allOf(WikiPage.class)
                            : EnumSet.copyOf(pages);
            return this;
        }

        public Builder page(WikiPage page) {
            this.pages = EnumSet.of(page);
            return this;
        }

        public WikiGenerationOptions build() {
            return new WikiGenerationOptions(incremental, mermaidEnabled, pages);
        }
    }
}
