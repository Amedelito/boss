package dev.titan;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class VoidTitanPlugin extends JavaPlugin {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    public Items items;
    public BossManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);
        manager = new BossManager(this);
        getServer().getPluginManager().registerEvents(new Events(this), this);
        getLogger().info("VoidTitan activado.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.removeAll();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) {
            s.sendMessage("/titan give <egg|propulsor|coin> [jugador] [cantidad] | spawn | kill | reload");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "give" -> {
                if (a.length < 2) { s.sendMessage("Uso: /titan give <egg|propulsor|coin> [jugador] [cantidad]"); return true; }
                Player target = a.length > 2 ? Bukkit.getPlayer(a[2]) : (s instanceof Player p ? p : null);
                if (target == null) { s.sendMessage("Jugador no encontrado."); return true; }
                int amount = 1;
                if (a.length > 3) { try { amount = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException ignored) {} }
                ItemStack it = switch (a[1].toLowerCase()) {
                    case "egg" -> items.bossEgg();
                    case "propulsor" -> items.propulsor();
                    case "coin" -> items.coin(amount);
                    default -> null;
                };
                if (it == null) { s.sendMessage("Item desconocido."); return true; }
                if (!a[1].equalsIgnoreCase("coin")) it.setAmount(Math.min(amount, it.getMaxStackSize()));
                target.getInventory().addItem(it).values().forEach(r -> target.getWorld().dropItem(target.getLocation(), r));
                s.sendMessage("Entregado a " + target.getName());
            }
            case "spawn" -> {
                if (s instanceof Player p) manager.spawn(p.getLocation(), p);
                else s.sendMessage("Solo jugadores.");
            }
            case "kill" -> { manager.removeAll(); s.sendMessage("Jefes eliminados."); }
            case "reload" -> { reloadConfig(); s.sendMessage("Config recargada."); }
            default -> s.sendMessage("Subcomando desconocido.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1) r.addAll(List.of("give", "spawn", "kill", "reload"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) r.addAll(List.of("egg", "propulsor", "coin"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("give")) Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName()));
        return r;
    }
}
