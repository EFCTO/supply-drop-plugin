package com.example.myproject;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class supplydropplugin extends JavaPlugin implements Listener, TabExecutor {

    private final Map<Location, SupplyInfo> activeSupplies = new HashMap<>();
    private final Random random = new Random();
    private SupplyScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("supply").setExecutor(this);

        // 30분마다 일반 보급품 생성
        new BukkitRunnable() {
            public void run() {
                spawnRandomSupply(false);
            }
        }.runTaskTimer(this, 30L * 60L * 20L, 30L * 60L * 20L);

        scheduleNextNetheriteSupply();

        new BukkitRunnable() {
            public void run() {
                checkSupplyExpiration();
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60);

        new BukkitRunnable() {
            public void run() {
                checkNetheriteApproach();
            }
        }.runTaskTimer(this, 0L, 20L * 5);

        scoreboardManager = new SupplyScoreboardManager(this);
        scoreboardManager.startUpdatingScoreboard();
    }

    @Override
    public void onDisable() {
        scoreboardManager.stopUpdatingScoreboard();
        activeSupplies.clear();
    }

    private void scheduleNextNetheriteSupply() {
        int h = random.nextInt(7 - 3 + 1) + 3;
        long ticks = h * 60L * 60L * 20L;
        new BukkitRunnable() {
            public void run() {
                spawnRandomSupply(true);
                scheduleNextNetheriteSupply();
            }
        }.runTaskLater(this, ticks);
    }


    private void spawnRandomSupply(boolean netherite) {
        int range = 1450;
        int x = random.nextInt(range * 2 + 1) - range;
        int z = random.nextInt(range * 2 + 1) - range;
        World world = Bukkit.getWorld("world");
        int y = world.getHighestBlockYAt(x, z);
        createSupplyChest(new Location(world, (double)x, (double)y, (double)z), netherite);
    }

    private void spawnSupplyAtPlayer(Player p, boolean netherite) {
        createSupplyChest(p.getLocation(), netherite);
    }

    private void createSupplyChest(Location loc, boolean netherite) {
        loc.getBlock().setType(Material.CHEST);
        long now = System.currentTimeMillis();
        long expireMillis = netherite ? (now + 4L * 60L * 60L * 1000L) : (now + 90L * 60L * 1000L);
        SupplyInfo info = new SupplyInfo(expireMillis, netherite);
        if (netherite) {
            info.obsidianBlocks = surroundWithObsidian(loc);
        }
        activeSupplies.put(loc, info);
        Chest chest = (Chest) loc.getBlock().getState();
        fillSupplyInventory(chest.getInventory(), netherite);

        if (netherite) {
            startBlackSmokeTask(loc);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[보급] " + ChatColor.WHITE +
                    "네더라이트 보급품이 " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + " 에 생성되었습니다!!");
        } else {
            startRedSmokeTask(loc);
            int bx = loc.getBlockX() + random.nextInt(101) - 50;
            int bz = loc.getBlockZ() + random.nextInt(101) - 50;
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[보급] " + ChatColor.WHITE +
                    bx + ", " + loc.getBlockY() + ", " + bz + " 근처에 보급품이 투하되었습니다!!");
        }
    }

    private void checkSupplyExpiration() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Location, SupplyInfo>> it = activeSupplies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, SupplyInfo> entry = it.next();
            Location loc = entry.getKey();
            SupplyInfo info = entry.getValue();
            if (now >= info.expireTime) {
                if (loc.getBlock().getType() == Material.CHEST) {
                    if (info.netherite) {
                        Bukkit.broadcastMessage(ChatColor.GRAY + "[알림] 네더라이트 보급품이 사라졌습니다!");
                    } else {
                        Bukkit.broadcastMessage(ChatColor.GRAY + "[알림] 보급품이 사라졌습니다!");
                    }
                    loc.getBlock().setType(Material.AIR);
                }
                if (info.netherite && info.obsidianBlocks != null) {
                    for (Location o : info.obsidianBlocks) {
                        if (o.getBlock().getType() == Material.OBSIDIAN) {
                            o.getBlock().setType(Material.AIR);
                        }
                    }
                }
                it.remove();
            }
        }
    }

    private List<Location> surroundWithObsidian(Location chestLoc) {
        List<Location> obsidianList = new ArrayList<>();
        World w = chestLoc.getWorld();
        int cx = chestLoc.getBlockX();
        int cy = chestLoc.getBlockY();
        int cz = chestLoc.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location floorLoc = new Location(w, (double)(cx + dx), (double)(cy - 1), (double)(cz + dz));
                if (floorLoc.getBlock().getType() != Material.CHEST) {
                    floorLoc.getBlock().setType(Material.OBSIDIAN);
                    obsidianList.add(floorLoc);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Location sideLoc = new Location(w, (double)(cx + dx), (double)cy, (double)(cz + dz));
                if (sideLoc.getBlock().getType() != Material.CHEST) {
                    sideLoc.getBlock().setType(Material.OBSIDIAN);
                    obsidianList.add(sideLoc);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location topLoc = new Location(w, (double)(cx + dx), (double)(cy + 1), (double)(cz + dz));
                if (topLoc.getBlock().getType() != Material.CHEST) {
                    topLoc.getBlock().setType(Material.OBSIDIAN);
                    obsidianList.add(topLoc);
                }
            }
        }
        return obsidianList;
    }

    private void fillSupplyInventory(Inventory inv, boolean netherite) {
        inv.clear();
        int centerSlot = inv.getSize() / 2;
        if (random.nextDouble() < 0.50) {
            inv.setItem(centerSlot, getRandomTrimTemplate());
        }
        if (netherite) {
            inv.setItem(1, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            inv.setItem(2, new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            inv.setItem(3, new ItemStack(Material.DIAMOND_BLOCK, 1));
            inv.setItem(4, new ItemStack(Material.MACE, 1));
            inv.setItem(5, new ItemStack(Material.NETHERITE_INGOT, 1));
            inv.setItem(6, new ItemStack(Material.ELYTRA, 1));
            ItemStack knockFireStick = new ItemStack(Material.STICK, 1);
            ItemMeta stickMeta = knockFireStick.getItemMeta();
            stickMeta.addEnchant(Enchantment.KNOCKBACK, 5, true);
            stickMeta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
            stickMeta.setDisplayName(ChatColor.GOLD + "Knock-Fire Stick");
            knockFireStick.setItemMeta(stickMeta);
            inv.setItem(6, knockFireStick);
            int slot = 7;
            if (random.nextDouble() < 0.10) {
                inv.setItem(slot++, new ItemStack(Material.TRIDENT, 1));
            }
            if (random.nextDouble() < 0.07) {
                inv.setItem(slot++, new ItemStack(Material.ELYTRA, 1));
            }
            if (random.nextDouble() < 0.15) {
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
                ItemMeta bookMeta = book.getItemMeta();
                if (random.nextBoolean()) {
                    bookMeta.addEnchant(Enchantment.PROTECTION, 4, true);
                } else {
                    bookMeta.addEnchant(Enchantment.SHARPNESS, 5, true);
                }
                book.setItemMeta(bookMeta);
                inv.setItem(slot++, book);
            }
            for (; slot < inv.getSize(); slot++) {
                ItemStack r = rollGeneralSupplyItem(true);
                if (r != null) {
                    inv.setItem(slot, r);
                }
            }
        } else {
            for (int slot = 0; slot < inv.getSize(); slot++) {
                if (slot == centerSlot) continue;
                ItemStack r = rollGeneralSupplyItem(false);
                if (r != null) {
                    inv.setItem(slot, r);
                }
            }
        }
    }


    private ItemStack getRandomTrimTemplate() {
        List<Material> trimTemplates = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (mat.name().endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) {
                trimTemplates.add(mat);
            }
        }
        if (!trimTemplates.isEmpty()) {
            Material chosen = trimTemplates.get(random.nextInt(trimTemplates.size()));
            return new ItemStack(chosen, 1);
        }
        return new ItemStack(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 1);
    }

    private ItemStack rollGeneralSupplyItem(boolean netherite) {
        if (random.nextDouble() < 0.10) return null;
        double totalProb = 55.0408;
        double roll = random.nextDouble() * totalProb;
        double cumulative = 0;
        cumulative += 10.0;
        if (roll < cumulative) {
            return getRandomEquipment(!netherite ? EquipmentType.IRON : EquipmentType.DIAMOND);
        }
        cumulative += 5.0;
        if (roll < cumulative) {
            return new ItemStack(Material.BOW, 1);
        }
        cumulative += 8.0;
        if (roll < cumulative) {
            int amount3 = 5 + random.nextInt(3);
            return new ItemStack(Material.IRON_NUGGET, amount3);
        }
        cumulative += 9.0;
        if (roll < cumulative) {
            int amount2 = 7 + random.nextInt(8);
            return new ItemStack(Material.ARROW, amount2);
        }
        cumulative += 10.0;
        if (roll < cumulative) {
            int amount1 = 1 + random.nextInt(3);
            return new ItemStack(Material.COOKED_BEEF, amount1);
        }
        cumulative += 1.0;
        if (roll < cumulative) {
            return getRandomEquipment(EquipmentType.DIAMOND);
        }
        cumulative += 0.2;
        if (roll < cumulative) {
            return new ItemStack(Material.ANCIENT_DEBRIS, 1);
        }
        cumulative += 0.07;
        if (roll < cumulative) {
            return new ItemStack(Material.NETHERITE_INGOT, 1);
        }
        cumulative += 0.02;
        if (roll < cumulative) {
            return getRandomEquipment(EquipmentType.NETHERITE);
        }
        cumulative += 0.8;
        if (roll < cumulative) {
            return new ItemStack(Material.DIAMOND_BLOCK, 1);
        }
        cumulative += 1.0;
        if (roll < cumulative) {
            return createEnchantedBook(Enchantment.MENDING, 1);
        }
        cumulative += 1.0;
        if (roll < cumulative) {
            return createEnchantedBook(Enchantment.FROST_WALKER, 1);
        }
        cumulative += 0.9;
        if (roll < cumulative) {
            return createEnchantedBook(Enchantment.SWEEPING_EDGE, 1);
        }
        cumulative += 1.0;
        if (roll < cumulative) {
            return createRandomEnchantedBook();
        }
        cumulative += 0.5;
        if (roll < cumulative) {
            return new ItemStack(Material.WITHER_SKELETON_SKULL, 1);
        }
        cumulative += 0.25;
        if (roll < cumulative) {
            return new ItemStack(Material.NETHER_STAR, 1);
        }
        cumulative += 2.0;
        if (roll < cumulative) {
            return new ItemStack(Material.EMERALD, 1);
        }
        cumulative += 0.5;
        if (roll < cumulative) {
            return new ItemStack(Material.TOTEM_OF_UNDYING, 1);
        }
        cumulative += 2.0;
        if (roll < cumulative) {
            return new ItemStack(Material.EMERALD, 1);
        }
        cumulative += 0.6;
        if (roll < cumulative) {
            return new ItemStack(Material.EMERALD_BLOCK, 1);
        }
        cumulative += 0.0008;
        if (roll < cumulative) {
            return new ItemStack(Material.NETHERITE_BLOCK, 1);
        }
        cumulative += 1.0;
        if (roll < cumulative) {
            return new ItemStack(Material.GOLDEN_APPLE, 1);
        }
        cumulative += 0.2;
        if (roll < cumulative) {
            return new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1);
        }
        return null;
    }

    public enum EquipmentType {
        IRON, DIAMOND, NETHERITE
    }

    private ItemStack getRandomEquipment(EquipmentType type) {
        Material[] items;
        switch (type) {
            case IRON:
                items = new Material[] {
                        Material.IRON_HELMET,
                        Material.IRON_CHESTPLATE,
                        Material.IRON_LEGGINGS,
                        Material.IRON_BOOTS,
                        Material.IRON_PICKAXE,
                        Material.IRON_SHOVEL,
                        Material.IRON_AXE,
                        Material.IRON_HOE,
                        Material.IRON_INGOT,
                        Material.IRON_HORSE_ARMOR
                };
                break;
            case DIAMOND:
                items = new Material[] {
                        Material.DIAMOND_HELMET,
                        Material.DIAMOND_CHESTPLATE,
                        Material.DIAMOND_LEGGINGS,
                        Material.DIAMOND_BOOTS,
                        Material.DIAMOND_PICKAXE,
                        Material.DIAMOND_SHOVEL,
                        Material.DIAMOND_AXE,
                        Material.DIAMOND_HOE,
                        Material.DIAMOND,
                        Material.DIAMOND_HORSE_ARMOR
                };
                break;
            case NETHERITE:
                items = new Material[] {
                        Material.NETHERITE_HELMET,
                        Material.NETHERITE_CHESTPLATE,
                        Material.NETHERITE_LEGGINGS,
                        Material.NETHERITE_BOOTS,
                        Material.NETHERITE_PICKAXE,
                        Material.NETHERITE_SHOVEL,
                        Material.NETHERITE_AXE,
                        Material.NETHERITE_HOE,
                        Material.NETHERITE_INGOT
                };
                break;
            default:
                items = new Material[] { Material.IRON_HELMET };
        }
        Material chosen = items[random.nextInt(items.length)];
        if (random.nextDouble() < 0.01) {
            chosen = getGoldenVariant(chosen);
        }
        return new ItemStack(chosen, 1);
    }

    private Material getGoldenVariant(Material m) {
        switch (m) {
            case IRON_HELMET: return Material.GOLDEN_HELMET;
            case IRON_CHESTPLATE: return Material.GOLDEN_CHESTPLATE;
            case IRON_LEGGINGS: return Material.GOLDEN_LEGGINGS;
            case IRON_BOOTS: return Material.GOLDEN_BOOTS;
            case IRON_PICKAXE: return Material.GOLDEN_PICKAXE;
            case IRON_SHOVEL: return Material.GOLDEN_SHOVEL;
            case IRON_AXE: return Material.GOLDEN_AXE;
            case IRON_HOE: return Material.GOLDEN_HOE;
            default: return m;
        }
    }

    private ItemStack createEnchantedBook(Enchantment ench, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = book.getItemMeta();
        meta.addEnchant(ench, level, true);
        book.setItemMeta(meta);
        return book;
    }

    private ItemStack createRandomEnchantedBook() {
        Enchantment[] possible = new Enchantment[] {
                Enchantment.PROTECTION,
                Enchantment.SHARPNESS,
                Enchantment.FIRE_ASPECT,
                Enchantment.KNOCKBACK,
                Enchantment.SWEEPING_EDGE,
                Enchantment.MENDING
        };
        Enchantment chosen = possible[random.nextInt(possible.length)];
        int level = chosen.getMaxLevel();
        return createEnchantedBook(chosen, level);
    }

    private void startBlackSmokeTask(Location loc) {
        new BukkitRunnable() {
            public void run() {
                if (loc.getBlock().getType() != Material.CHEST) {
                    cancel();
                    return;
                }
                loc.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE,
                        loc.clone().add(0.5, 1.0, 0.5),
                        10, 0.2, 0.5, 0.2, 0.01);
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startRedSmokeTask(Location loc) {
        new BukkitRunnable() {
            public void run() {
                if (loc.getBlock().getType() != Material.CHEST) {
                    cancel();
                    return;
                }
                loc.getWorld().spawnParticle(Particle.DUST,
                        loc.clone().add(0.5, 1.0, 0.5),
                        20, new Particle.DustOptions(Color.RED, 1));
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void checkNetheriteApproach() {
        for (Map.Entry<Location, SupplyInfo> e : activeSupplies.entrySet()) {
            Location chestLoc = e.getKey();
            SupplyInfo info = e.getValue();
            if (!info.netherite) continue;
            if (chestLoc.getBlock().getType() != Material.CHEST) continue;
            for (Player p : chestLoc.getWorld().getPlayers()) {
                if (p.getLocation().distance(chestLoc) <= 10) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));
                } else {
                    p.removePotionEffect(PotionEffectType.GLOWING);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest)) return;
        Chest c = (Chest) e.getInventory().getHolder();
        Location loc = c.getLocation();
        if (!activeSupplies.containsKey(loc)) return;
        if (isInventoryEmpty(c.getInventory())) {
            loc.getBlock().setType(Material.AIR);
            SupplyInfo info = activeSupplies.get(loc);
            if (info.netherite && info.obsidianBlocks != null) {
                for (Location o : info.obsidianBlocks) {
                    if (o.getBlock().getType() == Material.OBSIDIAN) {
                        o.getBlock().setType(Material.AIR);
                    }
                }
            }
            if (info.netherite) {
                Bukkit.broadcastMessage(ChatColor.GREEN + "플레이어가 네더라이트 보급품을 습득했습니다!");
            }
            activeSupplies.remove(loc);
        }
    }

    private boolean isInventoryEmpty(Inventory inv) {
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.isOp()) {
            s.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (!(s instanceof Player)) {
            s.sendMessage("플레이어만 사용 가능합니다.");
            return true;
        }
        Player p = (Player) s;
        if (args.length < 1) {
            p.sendMessage("/supply [normal|netherite]");
            return true;
        }
        if (args[0].equalsIgnoreCase("normal")) {
            spawnSupplyAtPlayer(p, false);
            p.sendMessage("일반 보급품 소환");
        } else if (args[0].equalsIgnoreCase("netherite")) {
            spawnSupplyAtPlayer(p, true);
            p.sendMessage("네더라이트 보급품 소환");
        } else {
            p.sendMessage("/supply [normal|netherite]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("normal", "netherite");
        }
        return Collections.emptyList();
    }

    public Map<Location, SupplyInfo> getActiveSupplies() {
        return activeSupplies;
    }

    static class SupplyInfo {
        long expireTime;
        boolean netherite;
        List<Location> obsidianBlocks;
        Set<UUID> approachedPlayers = new HashSet<>();

        public SupplyInfo(long expireTime, boolean netherite) {
            this.expireTime = expireTime;
            this.netherite = netherite;
        }
    }

    public class SupplyScoreboardManager {
        private Scoreboard board;
        private Objective obj;
        private BukkitRunnable updateTask;
        private final supplydropplugin plugin;

        public SupplyScoreboardManager(supplydropplugin plugin) {
            this.plugin = plugin;
            setup();
        }

        void setup() {
            ScoreboardManager m = Bukkit.getScoreboardManager();
            if (m == null) return;
            board = m.getNewScoreboard();
            obj = board.registerNewObjective("NetheriteDrops", "dummy", ChatColor.BOLD + "네더라이트 보급품");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
 
        public void startUpdatingScoreboard() {
            if (updateTask != null) {
                updateTask.cancel();
            }
            updateTask = new BukkitRunnable() {
                public void run() {
                    update();
                }
            };
            updateTask.runTaskTimer(plugin, 0L, 20L);
        }

        public void stopUpdatingScoreboard() {
            if (updateTask != null) {
                updateTask.cancel();
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getScoreboard() == board) {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
            }
        }

        void update() {
            if (board == null || obj == null) return;
            board.getEntries().forEach(e -> board.resetScores(e));
            long now = System.currentTimeMillis();
            List<NetheriteEntry> netheriteList = new ArrayList<>();
            plugin.getActiveSupplies().forEach((loc, info) -> {
                if (info.netherite) {
                    netheriteList.add(new NetheriteEntry(loc, info.expireTime));
                }
            });
            netheriteList.sort(Comparator.comparingLong(x -> x.expire));
            if (netheriteList.isEmpty()) {
                Score s = obj.getScore(ChatColor.GRAY + "활성 보급품 없음");
                s.setScore(1);
            } else {
                int max = 15;
                int idx = 0;
                int scoreVal = max;
                for (NetheriteEntry ne : netheriteList) {
                    if (idx >= max) break;
                    long diff = ne.expire - now;
                    long sec = diff / 1000;
                    long min = sec / 60;
                    String line = ChatColor.WHITE + "(" + ne.loc.getBlockX() + " " + ne.loc.getBlockY() + " " + ne.loc.getBlockZ() + ")"
                            + ChatColor.YELLOW + " (" + min + "분 뒤 만료)";
                    Score s = obj.getScore(line);
                    s.setScore(scoreVal);
                    idx++;
                    scoreVal--;
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(board);
            }
        }

        class NetheriteEntry {
            Location loc;
            long expire;

            public NetheriteEntry(Location loc, long expire) {
                this.loc = loc;
                this.expire = expire;
            }
        }
    }
}

class SupplyScoreboardManager {
    private Scoreboard board;
    private Objective obj;
    private BukkitRunnable updateTask;
    private final supplydropplugin plugin;

    public SupplyScoreboardManager(supplydropplugin plugin) {
        this.plugin = plugin;
        setup();
    }

    void setup() {
        ScoreboardManager m = Bukkit.getScoreboardManager();
        if (m == null) return;
        board = m.getNewScoreboard();
        obj = board.registerNewObjective("NetheriteDrops", "dummy", ChatColor.BOLD + "네더라이트 보급품");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void startUpdatingScoreboard() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = new BukkitRunnable() {
            public void run() {
                update();
            }
        };
        updateTask.runTaskTimer(plugin, 0L, 20L);
    }

    public void stopUpdatingScoreboard() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getScoreboard() == board) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }

    void update() {
        if (board == null || obj == null) return;
        board.getEntries().forEach(e -> board.resetScores(e));
        long now = System.currentTimeMillis();
        List<NetheriteEntry> netheriteList = new ArrayList<>();
        plugin.getActiveSupplies().forEach((loc, info) -> {
            if (info.netherite) {
                netheriteList.add(new NetheriteEntry(loc, info.expireTime));
            }
        });
        netheriteList.sort(Comparator.comparingLong(x -> x.expire));
        if (netheriteList.isEmpty()) {
            Score s = obj.getScore(ChatColor.GRAY + "활성 보급품 없음");
            s.setScore(1);
        } else {
            int max = 15;
            int idx = 0;
            int scoreVal = max;
            for (NetheriteEntry ne : netheriteList) {
                if (idx >= max) break;
                long diff = ne.expire - now;
                long sec = diff / 1000;
                long min = sec / 60;
                String line = ChatColor.WHITE + "(" + ne.loc.getBlockX() + " " + ne.loc.getBlockY() + " " + ne.loc.getBlockZ() + ")"
                        + ChatColor.YELLOW + " (" + min + "분 뒤 만료)";
                Score s = obj.getScore(line);
                s.setScore(scoreVal);
                idx++;
                scoreVal--;
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
        }
    }

    static class NetheriteEntry {
        Location loc;
        long expire;

        public NetheriteEntry(Location loc, long expire) {
            this.loc = loc;
            this.expire = expire;
        }
    }
}


