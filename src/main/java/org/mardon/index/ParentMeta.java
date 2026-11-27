package org.mardon.index;

public final class ParentMeta {
    private boolean complete;
    private long version;

    public ParentMeta(boolean complete) {
        this.complete = complete;
        this.version = 0L;
    }

    public boolean isComplete() {
        return complete;
    }

    public long getVersion() {
        return version;
    }

    public void markComplete() {
        this.complete = true;
        this.version++;
    }

    public void invalidate() {
        this.complete = false;
        this.version++;
    }
}