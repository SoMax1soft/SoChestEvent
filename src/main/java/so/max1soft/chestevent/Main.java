package so.max1soft.chestevent;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class Main extends JavaPlugin {
    private Location chestLocation;
    private long chestUpdateInterval;
    private String pluginPrefix;
    private String chestUpdateNotification;
    private Hologram hologram;
    private String chestOpenNotification;
    private Map<String, ItemStack> itemsMap = new HashMap<>();
    private Essentials essentials;

    @Override
    public void onEnable() {
        getLogger().info("");
        getLogger().info("§fИвент: §aЗапущен");
        getLogger().info("§fСоздатель: §b@max1soft");
        getLogger().info("§fВерсия: §c1.2");
        getLogger().info("");
        saveDefaultConfig();
        loadConfigValues();
        loadItemsFromConfig();
        startChestEvent();

        Plugin plugin = getServer().getPluginManager().getPlugin("Essentials");
        if (plugin instanceof Essentials) {
            essentials = (Essentials) plugin;
        }

        startFlightAndGodModeDisabler();
    }
    @Override
    public void onDisable() {
        if (hologram != null) {
            hologram.delete();
        }
    }

    private void loadConfigValues() {
        chestLocation = new Location(Bukkit.getWorld(getConfig().getString("chestLocation.world", "world")),
                getConfig().getDouble("chestLocation.x", 0),
                getConfig().getDouble("chestLocation.y", 0),
                getConfig().getDouble("chestLocation.z", 0));

        chestUpdateInterval = getConfig().getLong("chestUpdateInterval", 30) * 60 * 20;
        pluginPrefix = getConfig().getString("pluginPrefix", ChatColor.GOLD + "§7[§6§lСУНДУК-ГРАФА§7] ");
        chestUpdateNotification = getConfig().getString("messages.chestUpdateNotification", ChatColor.YELLOW + "§fЛут §6графа обновится§f через §a5 минут!§7 (/warp pvp)");
        chestOpenNotification = getConfig().getString("messages.chestOpenNotification", ChatColor.GREEN + "§fСундук §6графа §a§lоткрыт! §7(/warp pvp)");
    }

    private void startChestEvent() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateChestContents();
            }
        }.runTaskTimer(this, 0, chestUpdateInterval);
    }

    private void updateChestContents() {
        if (chestLocation.getBlock().getType() == Material.CHEST || chestLocation.getBlock().getType() == Material.ENDER_CHEST) {
            Inventory chestInventory = ((Chest) chestLocation.getBlock().getState()).getInventory();
            chestInventory.clear();

            Random random = new Random();

            itemsMap.forEach((itemId, itemStack) -> {
                int chance = getConfig().getInt("items." + itemId + ".chance", 0);
                int amount = getConfig().getInt("items." + itemId + ".amount", 1);
                for (int i = 0; i < amount; i++) {
                    if (random.nextInt(100) < chance) {
                        chestInventory.addItem(itemStack.clone());
                    }
                }
            });

            updateHologram();
            notifyPlayers();
        }
    }

    private void loadItemsFromConfig() {
        itemsMap.clear();
        if (getConfig().contains("items")) {
            Set<String> itemIds = getConfig().getConfigurationSection("items").getKeys(false);
            for (String itemId : itemIds) {
                String materialName = getConfig().getString("items." + itemId + ".material");
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    ItemStack itemStack = new ItemStack(material);

                    ItemMeta meta = itemStack.getItemMeta();
                    if (meta != null) {
                        if (getConfig().contains("items." + itemId + ".name")) {
                            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString("items." + itemId + ".name")));
                        }
                        if (getConfig().contains("items." + itemId + ".lore")) {
                            List<String> lore = new ArrayList<>();
                            for (String line : getConfig().getStringList("items." + itemId + ".lore")) {
                                lore.add(ChatColor.translateAlternateColorCodes('&', line));
                            }
                            meta.setLore(lore);
                        }
                        if (getConfig().contains("items." + itemId + ".enchants")) {
                            for (String enchantName : getConfig().getConfigurationSection("items." + itemId + ".enchants").getKeys(false)) {
                                Enchantment enchantment = Enchantment.getByName(enchantName);
                                if (enchantment != null) {
                                    int level = getConfig().getInt("items." + itemId + ".enchants." + enchantName);
                                    meta.addEnchant(enchantment, level, true);
                                }
                            }
                        }
                        if (meta instanceof PotionMeta && getConfig().contains("items." + itemId + ".potion_type")) {
                            PotionMeta potionMeta = (PotionMeta) meta;
                            PotionType potionType = PotionType.valueOf(getConfig().getString("items." + itemId + ".potion_type"));
                            int potionLevel = getConfig().getInt("items." + itemId + ".potion_level", 1);
                            PotionData potionData = new PotionData(potionType, false, potionLevel > 1);
                            potionMeta.setBasePotionData(potionData);
                        }

                        itemStack.setItemMeta(meta);
                    }

                    itemsMap.put(itemId, itemStack);
                }
            }
        }
    }

    private void updateHologram() {
        if (hologram != null) {
            hologram.delete();
        }
        Location hologramLocation = chestLocation.clone().add(0.0, 1.5, 0.0);
        hologram = DHAPI.createHologram("chest_hologram", hologramLocation);
        DHAPI.addHologramLine(hologram, ChatColor.GOLD + getConfig().getString("hologramname"));
        DHAPI.addHologramLine(hologram, ChatColor.GREEN + getConfig().getString("hologramsub" + ChatColor.RED + "5"));

        new BukkitRunnable() {
            int countdown = (int) chestUpdateInterval / 20;

            @Override
            public void run() {
                if (countdown > 0) {
                    countdown--;
                    DHAPI.setHologramLine(hologram, 1, ChatColor.GREEN + "§fЛут §6обновиться§а через " + ChatColor.RED + (countdown / 60) + " минут");
                } else {
                    DHAPI.setHologramLine(hologram, 1, ChatColor.GREEN + "Сундук обновлен");
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    private void notifyPlayers() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(pluginPrefix + chestUpdateNotification);
                }
            }
        }.runTaskLater(this, (25 * 60 * 20));

        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(pluginPrefix + chestOpenNotification);
                spawnFireworkExplosion(chestLocation);
            }
        }, chestUpdateInterval);
    }

    private void spawnFireworkExplosion(Location location) {
        location.getWorld().spawn(location, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.RED)
                    .with(FireworkEffect.Type.BURST)
                    .build());
            firework.setFireworkMeta(meta);
        });
    }

    private void startFlightAndGodModeDisabler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (essentials == null) {
                    return;
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().equals(chestLocation.getWorld()) && player.getLocation().distance(chestLocation) <= 10) {
                        User user = essentials.getUser(player);
                        if (user != null) {
                            if (player.hasPermission("sochestevent.bypass")) {
                                return;
                            }
                            if (user.isGodModeEnabled()) {
                                user.setGodModeEnabled(false);
                            }
                            if (player.getAllowFlight()) {
                                player.setAllowFlight(false);
                                player.setFlying(false);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.hasPermission("sochestevent.command")) {

            if (command.getName().equalsIgnoreCase("sochestevent") && args.length > 0 && args[0].equalsIgnoreCase("start")) {
                updateChestContents();
                return true;
            }
        } else {
            sender.sendMessage("§cПрав нет брат.");
            return true;
        }
        return false;
    }
}
