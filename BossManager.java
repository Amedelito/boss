package dev.titan;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BossManager {
    private final VoidTitanPlugin plugin;
    private final Map<UUID, VoidTitan> bosses = new HashMap<>();

    public BossManager(VoidTitanPlugin plugin) { this.plugin = plugin; }

    public VoidTitan spawn(Location loc, Player summoner) {
        VoidTitan t = new VoidTitan(plugin, this, loc, summoner);
        bosses.put(t.id(), t);
        return t;
    }

    public VoidTitan get(Entity e) { return e == null ? null : bosses.get(e.getUniqueId()); }

    public void forget(UUID id) { bosses.remove(id); }

    public void removeAll() {
        for (VoidTitan t : new ArrayList<>(bosses.values())) t.removeNow();
        bosses.clear();
    }
}
