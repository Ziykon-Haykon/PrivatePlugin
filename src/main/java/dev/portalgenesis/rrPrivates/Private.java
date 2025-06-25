package dev.portalgenesis.rrPrivates;

import org.bukkit.util.BoundingBox;

import java.util.HashSet;
import java.util.Set;

public class Private {

    public final BoundingBox box;
    public final String owner;
    /**
     * Players that were before in this private
     */
    public final Set<String> wereBefore = new HashSet<>();

    public Private(BoundingBox box, String owner) {
        this.box = box;
        this.owner = owner;
    }
}
