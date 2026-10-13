package fr.onyx.event;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class OnyxEventPlugin extends JavaPlugin implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private NamespacedKey shardKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        shardKey = new NamespacedKey(this, "onyx_shard");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("OnyxEvent activé !");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        World world = Bukkit.getWorld(getConfig().getString("world", "world"));
        if (world == null) {
            sender.sendMessage(Component.text("Monde introuvable, vérifie config.yml.", NamedTextColor.RED));
            return true;
        }

        // Annonce du lancement, visible par tout le monde
        Bukkit.broadcast(MM.deserialize(
                "<dark_purple><bold>🖤 [ONYX EVENT] <reset><white>" + sender.getName()
                        + " <gray>a lancé l'événement <light_purple><bold>ONYX SHARD<reset><gray> !"));

        findAndDrop(world, 1);
        return true;
    }

    /** Cherche un endroit sûr (chunk chargé en async), puis drop le shard. */
    private void findAndDrop(World world, int attempt) {
        int maxAttempts = getConfig().getInt("max-attempts", 40);
        if (attempt > maxAttempts) {
            Bukkit.broadcast(MM.deserialize("<red>Impossible de trouver un endroit sûr pour l'Onyx Shard."));
            return;
        }

        int radius = getConfig().getInt("radius", 5000);
        Location spawn = world.getSpawnLocation();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // Point aléatoire dans un cercle
        double angle = rnd.nextDouble() * Math.PI * 2;
        double dist = radius * Math.sqrt(rnd.nextDouble());
        final int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        final int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = world.getBlockAt(x, y, z);
            Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);

            // On évite l'eau/lave et l'extérieur de la bordure
            if (ground.isLiquid() || !world.getWorldBorder().isInside(loc)) {
                findAndDrop(world, attempt + 1);
                return;
            }
            dropShard(loc);
        });
    }

    private void dropShard(Location loc) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("ONYX SHARD", TextColor.color(0xA855F7), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true); // brille
        meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        Item drop = loc.getWorld().dropItem(loc, item);
        drop.setUnlimitedLifetime(true); // ne despawn jamais
        drop.setInvulnerable(true);      // ni feu, ni lave, ni explosion
        drop.setGlowing(true);           // halo visible

        Bukkit.broadcast(MM.deserialize(
                "<dark_purple><bold>💎 [ONYX EVENT] <reset><light_purple>L'ONYX SHARD est apparu en <white><bold>X: "
                        + loc.getBlockX() + " | Y: " + loc.getBlockY() + " | Z: " + loc.getBlockZ()
                        + " <reset><light_purple>! Récupère-le et fais <white>clic droit<light_purple> pour gagner !"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(shardKey, PersistentDataType.BYTE)) return;

        e.setCancelled(true);
        Player player = e.getPlayer();
        item.setAmount(item.getAmount() - 1); // le shard est consommé

        Bukkit.broadcast(MM.deserialize(
                "<dark_purple><bold>🏆 [ONYX EVENT] <reset><white><bold>" + player.getName()
                        + " <reset><light_purple><bold>A GAGNÉ L'ONYX SHARD !"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }
}
