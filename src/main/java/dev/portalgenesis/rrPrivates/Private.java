package dev.portalgenesis.rrPrivates;

import org.bukkit.util.BoundingBox;

import java.util.HashSet;
import java.util.Set;

public class Private {

    public BoundingBox box;

    public String player;

    public Set<String> enteredPlayers = new HashSet<>();

    public Private(BoundingBox box, String player) {

        this.box = box;
        this.player = player;

    }

    public Private() {}



}
