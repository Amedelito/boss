package dev.titan;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static dev.titan.VoidTitanPlugin.MM;

/** Jefe "Titán del Vacío": 3 fases, 5 ataques, modelo 3D animado con ItemDisplay. */
public final class VoidTitan {
    private enum Anim { SPAWN, IDLE, SLAM, CAST, DASH }
    private enum Atk { SLAM, BOLTS, SUMMON, DASH, METEORS }

    private static final class Part {
        final ItemDisplay d; final Vector3f pos; final Vector3f scale;
        Part(ItemDisplay d, Vector3f pos, Vector3f scale) { this.d = d; this.pos = pos; this.scale = scale; }
    }

    private final VoidTitanPlugin pl;
    private final BossManager mgr;
    private final World w;
    private final Zombie z;
    private final BossBar bar;
    private final String name;
    private final double maxHp, baseDmg, baseSpeed;
    private final float S, K, F, yawOff;
    private final Part body, head, armP, armN, legP, legN;
    private final List<Part> parts = new ArrayList<>();
    private final Map<UUID, Double> damage = new HashMap<>();
    private final List<Vex> minions = new ArrayList<>();
    private final Set<UUID> dashHit = new HashSet<>();

    private int phase = 1, t = 0, cd = 60, animT = 0, transT = 0, hurtT = 0;
    private Anim anim = Anim.SPAWN;
    private Atk atk, lastAtk;
    private boolean transition = false, dying = false, moving = false;
    private Player target;
    private Location lastLoc;
    private Vector dashDir = new Vector(0, 0, 1);
    private BukkitTask task;

    public VoidTitan(VoidTitanPlugin pl, BossManager mgr, Location loc, Player summoner) {
        this.pl = pl; this.mgr = mgr; this.w = loc.getWorld();
        var c = pl.getConfig();
        this.name = c.getString("boss.name", "<light_purple>Titán del Vacío");
        double hp = c.getDouble("boss.health", 1500);
        double dmg = c.getDouble("boss.damage", 12);
        double spd = c.getDouble("boss.speed", 0.27);
        float scale = (float) c.getDouble("boss.scale", 2.5);
        this.maxHp = hp; this.baseDmg = dmg; this.baseSpeed = spd; this.S = scale;
        this.K = scale * 1.2f; this.F = K / 3f;
        this.yawOff = (float) c.getDouble("model-yaw-offset", 0);

        // --- Entidad real (hitbox, IA, vida) ---
        this.z = w.spawn(loc, Zombie.class, e -> {
            e.setBaby(false);
            e.setShouldBurnInDay(false);
            e.setCanBreakDoors(false);
            e.setInvisible(true);
            e.setSilent(true);
            e.setRemoveWhenFarAway(false);
            e.setPersistent(true);
            e.setCanPickupItems(false);
            e.getEquipment().clear();
            attr(e, Attribute.MAX_HEALTH, hp);
            e.setHealth(hp);
            attr(e, Attribute.SCALE, scale);
            attr(e, Attribute.ATTACK_DAMAGE, dmg);
            attr(e, Attribute.MOVEMENT_SPEED, spd);
            attr(e, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            attr(e, Attribute.FOLLOW_RANGE, 64);
            attr(e, Attribute.STEP_HEIGHT, 2.0);
            e.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
            e.addScoreboardTag("void_titan");
            e.setInvulnerable(true);
            e.setAware(false);
        });

        // --- Modelo 3D (partes animables) ---
        body = addPart(loc, "boss_body", 0f, 3.0f, 0f, 1f, 1f, 1f);
        head = addPart(loc, "boss_head", 0f, 4.7f, 0f, 1f, 1f, 1f);
        armP = addPart(loc, "boss_limb", 1.25f, 3.9f, 0f, 1f, 1f, 1f);
        armN = addPart(loc, "boss_limb", -1.25f, 3.9f, 0f, 1f, 1f, 1f);
        legP = addPart(loc, "boss_limb", 0.45f, 1.85f, 0f, 1.2f, 0.55f, 1.2f);
        legN = addPart(loc, "boss_limb", -0.45f, 1.85f, 0f, 1.2f, 0.55f, 1.2f);

        // --- Barra de vida ---
        bar = BossBar.bossBar(MM.deserialize(name), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);

        announceSpawn(summoner);
        animate();
        task = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1L, 1L);
    }

    public UUID id() { return z.getUniqueId(); }

    private static void attr(Attributable e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private Part addPart(Location loc, String model, float x, float y, float zz, float sx, float sy, float sz) {
        ItemDisplay d = w.spawn(loc, ItemDisplay.class, e -> {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setItemModel(pl.items.model(model));
            it.setItemMeta(m);
            e.setItemStack(it);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBrightness(new Display.Brightness(13, 13));
            e.setViewRange(4f);
            e.setPersistent(false);
            e.setTeleportDuration(3);
            e.setInterpolationDuration(3);
        });
        Part p = new Part(d, new Vector3f(x, y, zz), new Vector3f(sx, sy, sz));
        parts.add(p);
        return p;
    }

    // ===================================================== ANUNCIOS

    private void announceSpawn(Player summoner) {
        String who = summoner == null ? "Alguien" : summoner.getName();
        Bukkit.broadcast(MM.deserialize("<dark_gray>[<dark_purple>☠<dark_gray>] ")
                .append(MM.deserialize(name))
                .append(MM.deserialize(" <gray>ha sido invocado por <white>" + who + "<gray>. <red>¡Preparaos!")));
        Title title = Title.title(MM.deserialize(name), MM.deserialize("<red>¡Ha despertado!"),
                Title.Times.times(Duration.ofMillis(600), Duration.ofSeconds(3), Duration.ofSeconds(1)));
        for (Player p : w.getPlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
        }
    }

    private void announceNearby(Component chat, Title title, Sound s, float pitch) {
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(z.getLocation()) > 80 * 80) continue;
            p.sendMessage(chat);
            if (title != null) p.showTitle(title);
            p.playSound(p.getLocation(), s, 1.2f, pitch);
        }
    }

    // ===================================================== TICK PRINCIPAL

    private void tick() {
        if (dying) return;
        if (!z.isValid() || z.isDead()) { removeNow(); return; }
        t++;
        if (hurtT > 0) hurtT--;
        Location cur = z.getLocation();
        moving = lastLoc != null && lastLoc.getWorld() == cur.getWorld() && lastLoc.distanceSquared(cur) > 0.003;
        lastLoc = cur.clone();

        pickTarget();
        updateBar();
        minions.removeIf(v -> !v.isValid());
        ambience(cur);

        double ratio = z.getHealth() / maxHp;
        int newPhase = ratio > 0.66 ? 1 : ratio > 0.33 ? 2 : 3;

        if (anim == Anim.SPAWN) tickSpawn();
        else if (transition) tickTransition();
        else if (newPhase > phase) startTransition(newPhase);
        else if (anim == Anim.IDLE) {
            if (target != null && --cd <= 0) startAttack();
        } else tickAttack();

        animate();
        sync();
    }

    private void pickTarget() {
        Player best = null; double bd = 50 * 50;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE)) continue;
            double d = p.getLocation().distanceSquared(z.getLocation());
            if (d < bd) { bd = d; best = p; }
        }
        target = best;
        if (target != null && t % 20 == 0 && anim == Anim.IDLE) z.setTarget(target);
    }

    private void updateBar() {
        float prog = (float) Math.max(0, Math.min(1, z.getHealth() / maxHp));
        bar.progress(prog);
        bar.name(MM.deserialize(name).append(MM.deserialize(" <gray>· <red>" + (int) Math.ceil(z.getHealth()) + " ❤ <gray>· <yellow>Fase " + phase + "/3")));
        double range = pl.getConfig().getDouble("boss.bar-range", 70);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(z.getLocation()) <= range * range) p.showBossBar(bar);
            else p.hideBossBar(bar);
        }
    }

    private void ambience(Location cur) {
        if (moving && t % 14 == 0) {
            w.playSound(cur, Sound.ENTITY_IRON_GOLEM_STEP, 2.5f, 0.5f);
            w.spawnParticle(Particle.BLOCK, cur, 12, 1.2, 0.1, 1.2, 0, Material.DEEPSLATE.createBlockData());
        }
        if (t % 220 == 0) w.playSound(cur, Sound.ENTITY_WARDEN_AMBIENT, 3f, 0.6f);
        if (t % 4 == 0) w.spawnParticle(Particle.REVERSE_PORTAL, cur.clone().add(0, 3.2 * F, 0), 4, 0.8 * F, 1.2 * F, 0.8 * F, 0.02);
    }

    // ===================================================== SPAWN

    private void tickSpawn() {
        animT++;
        Location l = z.getLocation();
        double prog = Math.min(1, animT / 100.0);
        for (int i = 0; i < 3; i++) {
            double a = (t * 0.3) + i * (2 * Math.PI / 3);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.getX() + Math.cos(a) * 3, l.getY() + prog * 5, l.getZ() + Math.sin(a) * 3, 1, 0, 0, 0, 0);
        }
        if (animT % 3 == 0) w.spawnParticle(Particle.BLOCK, l, 20, 2.5, 0.2, 2.5, 0, Material.DEEPSLATE.createBlockData());
        if (animT == 40) w.playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, 4f, 0.7f);
        if (animT == 100) {
            w.playSound(l, Sound.ENTITY_RAVAGER_ROAR, 4f, 0.5f);
            w.playSound(l, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.8f);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 3, 1.5, 0.2, 1.5, 0);
            anim = Anim.IDLE; animT = 0; cd = 40;
            z.setAware(true); z.setInvulnerable(false);
        }
    }

    // ===================================================== FASES

    private void startTransition(int np) {
        phase = np; transition = true; transT = 0; anim = Anim.CAST; animT = 0; atk = null;
        z.setAware(false); z.setInvulnerable(true);
        z.setHealth(Math.min(maxHp, z.getHealth() + maxHp * 0.04));
        attr(z, Attribute.MOVEMENT_SPEED, baseSpeed * (1 + 0.15 * (np - 1)));
        attr(z, Attribute.ATTACK_DAMAGE, baseDmg * (1 + 0.25 * (np - 1)));
        bar.color(np == 2 ? BossBar.Color.PINK : BossBar.Color.RED);
        String sub = np == 2 ? "<light_purple>El Titán invoca esbirros y embiste" : "<red>¡Furia total! Meteoros del vacío";
        Title title = Title.title(MM.deserialize("<dark_red>☠ FASE " + np + " ☠"), MM.deserialize(sub),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600)));
        announceNearby(MM.deserialize("<dark_gray>[<dark_purple>☠<dark_gray>] ").append(MM.deserialize(name))
                .append(MM.deserialize(" <gray>entra en la <red>Fase " + np + "<gray>.")), title, Sound.ENTITY_WITHER_SPAWN, 0.8f);
        // Onda expansiva
        Location l = z.getLocation();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 4, 2, 1, 2, 0);
        for (Player p : nearby(14)) {
            Vector away = p.getLocation().toVector().subtract(l.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
            p.setVelocity(away.normalize().multiply(1.6).setY(0.7));
        }
    }

    private void tickTransition() {
        transT++; animT++;
        Location l = z.getLocation();
        for (int i = 0; i < 4; i++) {
            double a = transT * 0.4 + i * Math.PI / 2;
            double r = 3.5 - transT * 0.04;
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.getX() + Math.cos(a) * r, l.getY() + transT * 0.07, l.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (transT % 10 == 0) w.playSound(l, Sound.BLOCK_BEACON_POWER_SELECT, 3f, 0.5f + transT / 100f);
        if (transT >= 60) {
            transition = false; z.setAware(true); z.setInvulnerable(false);
            anim = Anim.IDLE; animT = 0; cd = 30;
            w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
        }
    }

    // ===================================================== ATAQUES

    private void startAttack() {
        List<Atk> pool = new ArrayList<>(List.of(Atk.SLAM, Atk.BOLTS));
        if (phase >= 2) { pool.add(Atk.SUMMON); pool.add(Atk.DASH); }
        if (phase >= 3) { pool.add(Atk.METEORS); pool.add(Atk.SLAM); }
        if (minions.size() >= 6) pool.remove(Atk.SUMMON);
        if (target.getLocation().distanceSquared(z.getLocation()) < 36) pool.remove(Atk.DASH);
        if (pool.size() > 1) pool.removeIf(a -> a == lastAtk);
        atk = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        lastAtk = atk;
        animT = 0; dashHit.clear();
        z.setAware(false);
        anim = atk == Atk.SLAM ? Anim.SLAM : atk == Atk.DASH ? Anim.DASH : Anim.CAST;
    }

    private void endAttack() {
        anim = Anim.IDLE; animT = 0; atk = null;
        z.setAware(true);
        cd = 100 - phase * 22;
    }

    private void tickAttack() {
        animT++;
        Location feet = z.getLocation();
        switch (atk) {
            case SLAM -> {
                double R = 6 + phase * 1.5;
                if (animT < 22 && target != null) face(target);
                if (animT == 1) w.playSound(feet, Sound.ENTITY_RAVAGER_ROAR, 3f, 0.6f);
                if (animT < 24 && animT % 2 == 0) ring(feet, R, Color.RED, 50);
                if (animT == 24) {
                    w.playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
                    w.playSound(feet, Sound.BLOCK_ANVIL_LAND, 4f, 0.5f);
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, feet, 2, 1.5, 0.1, 1.5, 0);
                    for (double r = 1.5; r <= R; r += 1.5)
                        for (int i = 0; i < 30; i++) {
                            double a = 2 * Math.PI * i / 30;
                            w.spawnParticle(Particle.CLOUD, feet.getX() + Math.cos(a) * r, feet.getY() + 0.2, feet.getZ() + Math.sin(a) * r, 0, Math.cos(a), 0.05, Math.sin(a), 0.4);
                        }
                    for (Player p : nearby(R)) {
                        if (p.getLocation().getY() - feet.getY() > 1.6) continue; // esquivado saltando
                        p.damage(14 + phase * 2.0, z);
                        Vector away = p.getLocation().toVector().subtract(feet.toVector()).setY(0);
                        if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
                        p.setVelocity(away.normalize().multiply(1.2).setY(0.8));
                    }
                }
                if (animT >= 46) endAttack();
            }
            case BOLTS -> {
                if (target != null) face(target);
                int shots = phase == 3 ? 5 : 3;
                if (animT <= 18 && animT % 2 == 0)
                    w.spawnParticle(Particle.WITCH, feet.clone().add(0, 4.2 * F, 0), 10, 1.2, 0.6, 1.2, 0.05);
                if (animT == 1) w.playSound(feet, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 3f, 0.6f);
                int k = animT - 20;
                if (k >= 0 && k % 5 == 0 && k / 5 < shots) shoot(k / 5, shots);
                if (animT >= 20 + 5 * shots + 14) endAttack();
            }
            case SUMMON -> {
                if (target != null) face(target);
                if (animT == 1) w.playSound(feet, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 3f, 0.6f);
                if (animT < 20) ring(feet, 4, Color.PURPLE, 30);
                if (animT == 20) {
                    int n = 2 + phase;
                    for (int i = 0; i < n; i++) {
                        double a = 2 * Math.PI * i / n;
                        Location sl = feet.clone().add(Math.cos(a) * 4, 2, Math.sin(a) * 4);
                        w.spawn(sl, Vex.class, v -> {
                            v.customName(MM.deserialize("<dark_purple>Esbirro del Vacío"));
                            v.addScoreboardTag("titan_minion");
                            v.setLimitedLifetime(true);
                            v.setLimitedLifetimeTicks(20 * 30);
                            v.setPersistent(false);
                            if (target != null) v.setTarget(target);
                            minions.add(v);
                        });
                        w.spawnParticle(Particle.WITCH, sl, 20, 0.4, 0.6, 0.4, 0.1);
                    }
                    w.playSound(feet, Sound.ENTITY_EVOKER_CAST_SPELL, 3f, 0.7f);
                }
                if (animT >= 45) endAttack();
            }
            case DASH -> {
                if (animT < 20) {
                    if (target != null) face(target);
                    if (animT == 1) w.playSound(feet, Sound.ENTITY_RAVAGER_ROAR, 3f, 0.9f);
                    w.spawnParticle(Particle.SMOKE, feet, 8, 1.2, 0.1, 1.2, 0.02);
                } else if (animT < 34) {
                    if (animT == 20) {
                        Vector d = target != null ? target.getLocation().toVector().subtract(feet.toVector()) : feet.getDirection();
                        d.setY(0);
                        dashDir = d.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : d.normalize();
                        w.playSound(feet, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.8f);
                    }
                    z.setVelocity(dashDir.clone().multiply(1.3).setY(Math.min(0, z.getVelocity().getY())));
                    w.spawnParticle(Particle.CLOUD, feet, 10, 1.0, 0.2, 1.0, 0.05);
                    for (Player p : nearby(4.2 * S / 2.5)) {
                        if (p.getLocation().getY() - feet.getY() > 4 || !dashHit.add(p.getUniqueId())) continue;
                        p.damage(12 + phase * 2.0, z);
                        p.setVelocity(dashDir.clone().multiply(1.5).setY(0.6));
                    }
                } else if (animT >= 44) {
                    z.setVelocity(new Vector(0, 0, 0));
                    endAttack();
                }
            }
            case METEORS -> {
                if (animT == 1) w.playSound(feet, Sound.ENTITY_ENDER_DRAGON_GROWL, 4f, 0.5f);
                if (animT < 15) w.spawnParticle(Particle.FLAME, feet.clone().add(0, 5 * F, 0), 10, 1.5, 0.5, 1.5, 0.05);
                if (animT == 15) {
                    for (Player p : nearby(45)) {
                        int n = phase == 3 ? 4 : 2;
                        meteor(p.getLocation());
                        for (int i = 1; i < n; i++) {
                            ThreadLocalRandom r = ThreadLocalRandom.current();
                            meteor(p.getLocation().add(r.nextDouble(-5, 5), 0, r.nextDouble(-5, 5)));
                        }
                    }
                }
                if (animT >= 60) endAttack();
            }
        }
    }

    private void shoot(int i, int shots) {
        Location from = z.getLocation().add(0, 4.4 * F, 0);
        Vector dir = target != null
                ? target.getEyeLocation().toVector().subtract(from.toVector()).normalize()
                : z.getLocation().getDirection();
        if (shots > 3) dir.rotateAroundY((i - shots / 2) * 0.14);
        final Vector d = dir.clone();
        w.spawn(from.clone().add(d.clone().multiply(2.5)), SmallFireball.class, f -> {
            f.setShooter(z);
            f.setDirection(d);
            f.setIsIncendiary(false);
            f.setYield(0f);
        });
        w.playSound(from, Sound.ENTITY_BLAZE_SHOOT, 3f, 0.6f);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, from, 15, 0.4, 0.4, 0.4, 0.05);
    }

    private void meteor(Location at) {
        final Location c = at.clone();
        new BukkitRunnable() {
            int k = 0;
            @Override public void run() {
                k++;
                if (k % 2 == 0) ring(c, 3.2, Color.ORANGE, 24);
                w.spawnParticle(Particle.FLAME, c.getX(), c.getY() + 14 - k * (14.0 / 35), c.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
                if (k == 1) w.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.5f);
                if (k >= 35) {
                    w.strikeLightningEffect(c);
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.LAVA, c, 30, 1.5, 0.3, 1.5, 0);
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.8f);
                    for (Player p : w.getPlayers())
                        if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(c) <= 3.5 * 3.5) {
                            p.damage(10 + phase, z);
                            p.setVelocity(new Vector(0, 0.7, 0));
                        }
                    cancel();
                }
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    // ===================================================== ANIMACIÓN 3D

    private static float lerp(float a, float b, float k) { return a + (b - a) * k; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private void animate() {
        float swing = moving ? (float) Math.sin(t * 0.4) * 0.7f : 0f;
        float armPX = swing, armNX = -swing, armZ = 0.12f;
        float legPX = -swing * 0.8f, legNX = swing * 0.8f;
        float bodyX = 0, headX = 0;
        float yOff = (float) Math.sin(t * 0.1) * 0.06f;
        float sm = 1f + hurtT * 0.012f;

        switch (anim) {
            case SPAWN -> {
                float p = clamp01(animT / 100f);
                yOff += -(1 - p) * 3.4f;
                armZ = lerp(0.9f, 0.12f, p);
                armPX = armNX = -0.3f * (1 - p);
                legPX = legNX = 0;
            }
            case SLAM -> {
                if (animT < 22) {
                    float k = clamp01(animT / 22f);
                    armPX = armNX = lerp(armPX, -2.8f, k); armZ = 0.25f;
                    bodyX = -0.25f * k; headX = -0.3f * k; yOff += 0.35f * k;
                } else if (animT < 28) {
                    float k = clamp01((animT - 22) / 6f);
                    armPX = armNX = lerp(-2.8f, -0.3f, k); armZ = 0.25f;
                    bodyX = lerp(-0.25f, 0.4f, k); headX = lerp(-0.3f, 0.3f, k); yOff += lerp(0.35f, -0.35f, k);
                } else {
                    float k = clamp01((animT - 28) / 16f);
                    armPX = armNX = lerp(-0.3f, 0f, k);
                    bodyX = lerp(0.4f, 0f, k); headX = lerp(0.3f, 0f, k); yOff += lerp(-0.35f, 0f, k);
                }
            }
            case CAST -> {
                float k = clamp01(Math.min(animT, 15) / 15f);
                armZ = lerp(0.12f, 1.9f, k);
                armPX = armNX = lerp(armPX, -0.3f, k);
                headX = -0.25f * k;
                yOff += 0.5f * k + (float) Math.sin(t * 1.5) * 0.04f;
                legPX = legNX = 0;
            }
            case DASH -> {
                float k = animT < 20 ? clamp01(animT / 20f) : 1f;
                bodyX = 0.55f * k; headX = 0.3f * k;
                armPX = armNX = lerp(armPX, 1.2f, k);
                yOff -= 0.2f * k;
                if (animT >= 20 && animT < 34) { legPX = (float) Math.sin(t * 0.9) * 1.0f; legNX = -legPX; }
            }
            default -> { }
        }
        set(body, q(bodyX, 0), yOff, sm);
        set(head, q(headX, 0), yOff, sm);
        set(armP, q(armPX, armZ), yOff, sm);
        set(armN, q(armNX, -armZ), yOff, sm);
        set(legP, q(legPX, 0), 0f, sm);
        set(legN, q(legNX, 0), 0f, sm);
    }

    private static Quaternionf q(float x, float zr) { return new Quaternionf().rotateX(x).rotateZ(zr); }

    private void set(Part p, Quaternionf rot, float yOff, float sm) {
        Vector3f tr = new Vector3f(p.pos).mul(F);
        tr.y += yOff * F;
        Vector3f sc = new Vector3f(p.scale).mul(K * sm);
        p.d.setInterpolationDelay(0);
        p.d.setInterpolationDuration(3);
        p.d.setTransformation(new Transformation(tr, rot, sc, new Quaternionf()));
    }

    private void sync() {
        Location l = z.getLocation();
        l.setYaw(l.getYaw() + yawOff);
        l.setPitch(0f);
        for (Part p : parts) p.d.teleport(l);
    }

    // ===================================================== UTILIDADES

    private List<Player> nearby(double r) {
        List<Player> out = new ArrayList<>();
        Location l = z.getLocation();
        for (Player p : w.getPlayers()) {
            if (p.isDead() || (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE)) continue;
            double dx = p.getLocation().getX() - l.getX(), dz = p.getLocation().getZ() - l.getZ();
            if (dx * dx + dz * dz <= r * r && Math.abs(p.getLocation().getY() - l.getY()) < 8) out.add(p);
        }
        return out;
    }

    private void face(Player t) {
        Vector d = t.getLocation().toVector().subtract(z.getLocation().toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        z.setRotation(yaw, 0f);
    }

    private void ring(Location c, double r, Color col, int points) {
        Particle.DustOptions o = new Particle.DustOptions(col, 1.6f);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, c.getY() + 0.15, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, o);
        }
    }

    public void registerDamage(Player p, double amount) { damage.merge(p.getUniqueId(), amount, Double::sum); }

    public void hurt() {
        hurtT = 8;
        w.playSound(z.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.2f, 0.5f);
    }

    // ===================================================== MUERTE Y DROPS

    public void onDeath(EntityDeathEvent e) {
        if (dying) return;
        dying = true;
        if (task != null) task.cancel();
        Location loc = e.getEntity().getLocation();
        e.getDrops().clear();
        e.setDroppedExp(pl.getConfig().getInt("boss.xp", 3000));
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        for (Vex v : minions) if (v.isValid()) v.remove();

        // Drops personalizados
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextInt(100) < pl.getConfig().getInt("drops.propulsor-chance", 100)) dropFancy(loc, pl.items.propulsor(), true);
        int min = pl.getConfig().getInt("drops.coins-min", 40), max = Math.max(min, pl.getConfig().getInt("drops.coins-max", 90));
        int coins = r.nextInt(min, max + 1);
        while (coins > 0) { int n = Math.min(64, coins); dropFancy(loc, pl.items.coin(n), false); coins -= n; }

        // Mensajes
        List<Map.Entry<UUID, Double>> top = new ArrayList<>(damage.entrySet());
        top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Component msg = MM.deserialize("<dark_gray>[<dark_purple>☠<dark_gray>] ").append(MM.deserialize(name))
                .append(MM.deserialize(" <green>ha sido derrotado!"));
        Bukkit.broadcast(msg);
        int pos = 1;
        for (var en : top) {
            if (pos > 3) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(en.getKey());
            Bukkit.broadcast(MM.deserialize("<gray> " + pos + ". <white>" + (op.getName() == null ? "?" : op.getName()) + " <dark_gray>- <red>" + (int) en.getValue().doubleValue() + " daño"));
            pos++;
        }
        Title title = Title.title(MM.deserialize("<green><bold>¡VICTORIA!"), MM.deserialize("<gray>El Titán del Vacío ha caído"),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofSeconds(1)));
        for (Player p : w.getPlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 4f, 0.8f);

        // Animación de muerte: giro y colapso
        new BukkitRunnable() {
            int k = 0;
            @Override public void run() {
                k++;
                ThreadLocalRandom rr = ThreadLocalRandom.current();
                if (k == 1) {
                    for (Part p : parts) {
                        p.d.setInterpolationDelay(0);
                        p.d.setInterpolationDuration(60);
                        p.d.setTransformation(new Transformation(
                                new Vector3f(p.pos).mul(F).mul(0.2f), new Quaternionf().rotateY(8f).rotateX(0.6f),
                                new Vector3f(0.02f), new Quaternionf()));
                    }
                }
                if (k % 4 == 0) {
                    Location at = loc.clone().add(rr.nextDouble(-2, 2), rr.nextDouble(0, 4), rr.nextDouble(-2, 2));
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                    w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f + rr.nextFloat() * 0.5f);
                }
                if (k % 2 == 0) w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 2.5, 0), 20, 1.5, 1.5, 1.5, 0.08);
                if (k >= 62) {
                    w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.9f);
                    w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 80, 2, 2, 2, 0.2);
                    for (Part p : parts) p.d.remove();
                    cancel();
                }
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    private void dropFancy(Location loc, ItemStack stack, boolean special) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Item it = w.dropItem(loc.clone().add(0, 1.5, 0), stack);
        it.setGlowing(true);
        it.setInvulnerable(true);
        if (special) it.setUnlimitedLifetime(true);
        it.setVelocity(new Vector(r.nextDouble(-0.3, 0.3), 0.45, r.nextDouble(-0.3, 0.3)));
    }

    public void removeNow() {
        dying = true;
        if (task != null) task.cancel();
        for (Part p : parts) p.d.remove();
        for (Vex v : minions) v.remove();
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        if (z.isValid()) z.remove();
        mgr.forget(z.getUniqueId());
    }
}
