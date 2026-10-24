package dev.titan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class Items {
    private final VoidTitanPlugin plugin;
    public final NamespacedKey TYPE;

    public Items(VoidTitanPlugin plugin) {
        this.plugin = plugin;
        this.TYPE = new NamespacedKey(plugin, "item_type");
    }

    /** Clave del modelo dentro del pack compartido (namespace configurable). */
    public NamespacedKey model(String id) {
        String ns = plugin.getConfig().getString("pack-namespace", "casino");
        return NamespacedKey.fromString(ns + ":" + id);
    }

    private ItemStack make(Material m, String type, String name, String modelId, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(VoidTitanPlugin.MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = lore.stream()
                .map(x -> (Component) VoidTitanPlugin.MM.deserialize(x).decoration(TextDecoration.ITALIC, false))
                .toList();
        meta.lore(l);
        meta.setItemModel(model(modelId));
        meta.getPersistentDataContainer().set(TYPE, PersistentDataType.STRING, type);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack bossEgg() {
        return make(Material.ZOMBIE_SPAWN_EGG, "boss_egg",
                "<gradient:#7b2ff7:#f107a3><bold>Huevo del Titán del Vacío",
                "titan_egg",
                List.of("<gray>Haz <white>clic derecho</white> sobre un bloque", "<gray>para invocar al jefe.", "", "<dark_purple>¡Prepárate para la batalla!"));
    }

    public ItemStack propulsor() {
        ItemStack it = make(Material.FEATHER, "propulsor",
                "<gradient:#00e5ff:#7b2ff7><bold>Propulsor del Vacío",
                "propulsor",
                List.of("<gray>Clic derecho: <white>impulso hacia delante",
                        "<gray>Agachado + clic derecho: <white>super salto",
                        "<gray>Sin daño de caída mientras lo lleves.",
                        "", "<aqua>Infinito · Irrompible"));
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        meta.setMaxStackSize(1);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack coin(int amount) {
        ItemStack it = make(Material.GOLD_NUGGET, "slot_coin",
                "<gold><bold>Moneda de Tragaperras",
                plugin.getConfig().getString("drops.coin-model", "slot_coin"),
                List.of("<gray>Úsala en las tragaperras."));
        // Compatibilidad opcional con el plugin de tragaperras (tag PDC)
        String key = plugin.getConfig().getString("drops.coin-pdc-key", "");
        String val = plugin.getConfig().getString("drops.coin-pdc-value", "");
        if (!key.isEmpty()) {
            NamespacedKey nk = NamespacedKey.fromString(key);
            if (nk != null) {
                ItemMeta meta = it.getItemMeta();
                meta.getPersistentDataContainer().set(nk, PersistentDataType.STRING, val);
                it.setItemMeta(meta);
            }
        }
        it.setAmount(Math.max(1, Math.min(64, amount)));
        return it;
    }

    public String typeOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(TYPE, PersistentDataType.STRING);
    }
}
