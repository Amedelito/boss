package dev.titan;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Events implements Listener {
    private final VoidTitanPlugin pl;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public Events(VoidTitanPlugin pl) { this.pl = pl; }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a == Action.PHYSICAL || a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) return;
        String type = pl.items.typeOf(e.getItem());
        if (type == null) return;
        Player p = e.getPlayer();

        if (type.equals("boss_egg")) {
            e.setCancelled(true);
            if (a != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
            Block target = e.getClickedBlock().getRelative(e.getBlockFace());
            Location loc = target.getLocation().add(0.5, 0, 0.5);
            // Comprobar espacio libre (5 bloques de alto)
            for (int y = 0; y < 5; y++) {
                if (!loc.clone().add(0, y, 0).getBlock().isPassable()) {
                    p.sendActionBar(VoidTitanPlugin.MM.deserialize("<red>No hay espacio suficiente para invocar al Titán."));
                    return;
                }
            }
            loc.setYaw(p.getLocation().getYaw() + 180f);
            if (p.getGameMode() != GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
            pl.manager.spawn(loc, p);
        } else if (type.equals("propulsor")) {
            e.setCancelled(true);
            long now = System.currentTimeMillis();
            long cdMs = pl.getConfig().getInt("propulsor.cooldown-ticks", 25) * 50L;
            Long last = cooldowns.get(p.getUniqueId());
            if (last != null && now - last < cdMs) return;
            cooldowns.put(p.getUniqueId(), now);

            Vector dir = p.getLocation().getDirection();
            Vector v;
            if (p.isSneaking()) {
                double jump = pl.getConfig().getDouble("propulsor.jump-power", 1.5);
                v = new Vector(dir.getX() * 0.5, jump, dir.getZ() * 0.5);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.7f);
            } else {
                double power = pl.getConfig().getDouble("propulsor.dash-power", 2.2);
                v = dir.clone().multiply(power);
                if (v.getY() < 0.25) v.setY(0.25);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.3f);
            }
            p.setVelocity(v);
            p.setFallDistance(0f);
            Location at = p.getLocation().add(0, 0.2, 0);
            p.getWorld().spawnParticle(Particle.END_ROD, at, 25, 0.3, 0.1, 0.3, 0.15);
            p.getWorld().spawnParticle(Particle.CLOUD, at, 12, 0.3, 0.1, 0.3, 0.05);
            p.getWorld().playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.4f);
        }
    }

    /** Sin daño de caída si llevas el Propulsor en el inventario. */
    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        for (ItemStack it : p.getInventory().getContents()) {
            if ("propulsor".equals(pl.items.typeOf(it))) { e.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onDispense(BlockDispenseEvent e) {
        if ("boss_egg".equals(pl.items.typeOf(e.getItem()))) e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        VoidTitan boss = pl.manager.get(victim);

        // El jefe y sus esbirros no se dañan entre sí con proyectiles
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Zombie z && pl.manager.get(z) != null) {
            if (boss != null || victim.getScoreboardTags().contains("titan_minion")) { e.setCancelled(true); return; }
        }
        if (boss == null) return;

        Entity d = e.getDamager();
        Player attacker = null;
        if (d instanceof Player p) attacker = p;
        else if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker != null && !e.isCancelled()) boss.registerDamage(attacker, e.getFinalDamage());
        boss.hurt();
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (pl.manager.get(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        VoidTitan boss = pl.manager.get(e.getEntity());
        if (boss != null) {
            boss.onDeath(e);
            pl.manager.forget(e.getEntity().getUniqueId());
        } else if (e.getEntity().getScoreboardTags().contains("titan_minion")) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }
}
