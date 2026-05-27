package com.galacticodyssey.galaxy;

public final class GeneratedName {
    public final String prefix;
    public final String root;
    public final String suffix;
    public final String full;

    public GeneratedName(String prefix, String root, String suffix) {
        this.prefix = prefix;
        this.root = root;
        this.suffix = suffix;
        this.full = (prefix.isEmpty() ? "" : prefix + " ") + root + (suffix.isEmpty() ? "" : " " + suffix);
    }
}
