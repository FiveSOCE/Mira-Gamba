package com.mira.gamba.service;

import com.mira.gamba.MiraGambaPlugin;
import com.mira.gamba.model.CardMachine;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CardMachineService {
    private final MiraGambaPlugin plugin;
    private final File file;
    private final NamespacedKey machineKey;
    private final NamespacedKey cardIndexKey;
    private final Map<UUID, CardMachine> machines = new HashMap<>();

    public CardMachineService(MiraGambaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "card-machines.yml");
        this.machineKey = new NamespacedKey(plugin, "card_machine_id");
        this.cardIndexKey = new NamespacedKey(plugin, "card_index");
        load();
    }

    public Collection<CardMachine> all() {
        return Collections.unmodifiableCollection(machines.values());
    }

    public CardMachine create(Player player, long bet) {
        BlockFace forward = cardinal(player.getFacing());
        BlockFace front = forward.getOppositeFace();
        BlockFace right = rotateRight(forward);
        Location base = player.getLocation().getBlock().getRelative(forward, 4).getLocation();

        Material backing = Material.matchMaterial(plugin.getConfig().getString("cards.backing-material", "DARK_OAK_PLANKS"));
        if (backing == null || !backing.isBlock()) backing = Material.DARK_OAK_PLANKS;

        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 3; y++) {
                offset(base, right, x, y).getBlock().setType(backing, false);
            }
        }

        UUID id = UUID.randomUUID();
        CardMachine machine = new CardMachine(id, base, front, bet);
        machines.put(id, machine);

        spawnFrames(machine, base, right);
        spawnButtons(machine, base, right);
        save();
        return machine;
    }

    public CardMachine byButton(Block block) {
        if (block == null) return null;
        for (CardMachine machine : machines.values()) {
            if (buttonAction(machine, block) != null) return machine;
        }
        return null;
    }

    public String buttonAction(Block block) {
        if (block == null) return null;
        CardMachine machine = byButton(block);
        return machine == null ? null : buttonAction(machine, block);
    }

    public List<ItemFrame> frames(CardMachine machine) {
        List<ItemFrame> frames = new ArrayList<>();
        if (machine == null || machine.origin().getWorld() == null) return frames;

        for (ItemFrame frame : machine.origin().getWorld().getEntitiesByClass(ItemFrame.class)) {
            String id = frame.getPersistentDataContainer().get(machineKey, PersistentDataType.STRING);
            if (machine.id().toString().equals(id)) frames.add(frame);
        }

        frames.sort(Comparator.comparingInt(frame ->
                Optional.ofNullable(frame.getPersistentDataContainer().get(cardIndexKey, PersistentDataType.INTEGER)).orElse(99)
        ));
        return frames;
    }

    public int frameIndex(ItemFrame frame) {
        return Optional.ofNullable(frame.getPersistentDataContainer().get(cardIndexKey, PersistentDataType.INTEGER)).orElse(-1);
    }

    public boolean removeNearest(Location location, double radius) {
        CardMachine nearest = null;
        double best = radius * radius;
        for (CardMachine machine : machines.values()) {
            if (machine.origin().getWorld() != location.getWorld()) continue;
            double distance = machine.origin().distanceSquared(location);
            if (distance <= best) {
                best = distance;
                nearest = machine;
            }
        }

        if (nearest == null) return false;
        remove(nearest);
        return true;
    }

    public void remove(CardMachine machine) {
        machines.remove(machine.id());
        World world = machine.origin().getWorld();
        if (world != null) {
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                String id = frame.getPersistentDataContainer().get(machineKey, PersistentDataType.STRING);
                if (machine.id().toString().equals(id)) frame.remove();
            }
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                String id = display.getPersistentDataContainer().get(machineKey, PersistentDataType.STRING);
                if (machine.id().toString().equals(id)) display.remove();
            }

            BlockFace right = rotateRight(machine.facing().getOppositeFace());
            for (int x = -3; x <= 3; x++) {
                for (int y = 0; y <= 1; y++) {
                    Block backing = offset(machine.origin(), right, x, y).getBlock();
                    Block front = backing.getRelative(machine.facing());
                    if (buttonAction(machine, front) != null) front.setType(Material.AIR, false);
                }
            }
        }
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (CardMachine machine : machines.values()) {
            String root = "machines." + machine.id();
            yaml.set(root + ".world", machine.origin().getWorld().getUID().toString());
            yaml.set(root + ".x", machine.origin().getBlockX());
            yaml.set(root + ".y", machine.origin().getBlockY());
            yaml.set(root + ".z", machine.origin().getBlockZ());
            yaml.set(root + ".facing", machine.facing().name());
            yaml.set(root + ".bet", machine.bet());
        }

        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save card-machines.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("machines");
        if (section == null) return;

        for (String raw : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(raw);
                World world = Bukkit.getWorld(UUID.fromString(section.getString(raw + ".world")));
                if (world == null) continue;
                Location origin = new Location(
                        world,
                        section.getDouble(raw + ".x"),
                        section.getDouble(raw + ".y"),
                        section.getDouble(raw + ".z")
                );
                BlockFace facing = BlockFace.valueOf(section.getString(raw + ".facing", "SOUTH"));
                long bet = section.getLong(raw + ".bet");
                machines.put(id, new CardMachine(id, origin, facing, bet));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped invalid card machine " + raw + ": " + ex.getMessage());
            }
        }
    }

    private void spawnFrames(CardMachine machine, Location base, BlockFace right) {
        World world = base.getWorld();
        if (world == null) return;

        int[] cols = {-3, -1, 1, 3};
        for (int i = 0; i < cols.length; i++) {
            Location backing = offset(base, right, cols[i], 3);
            Location spawn = backing.getBlock().getRelative(machine.facing()).getLocation().add(0.5, 0.5, 0.5);
            final int index = i;
            ItemFrame frame = world.spawn(spawn, ItemFrame.class, entity -> {
                entity.setFacingDirection(machine.facing(), true);
                entity.setFixed(true);
                entity.setInvulnerable(true);
                entity.getPersistentDataContainer().set(machineKey, PersistentDataType.STRING, machine.id().toString());
                entity.getPersistentDataContainer().set(cardIndexKey, PersistentDataType.INTEGER, index);
            });
        }
    }

    private void spawnButtons(CardMachine machine, Location base, BlockFace right) {
        Map<String, int[]> actions = new LinkedHashMap<>();
        actions.put("RED", new int[]{-3, 1});
        actions.put("BLACK", new int[]{-2, 1});
        actions.put("HIGHER", new int[]{-1, 1});
        actions.put("LOWER", new int[]{0, 1});
        actions.put("INSIDE", new int[]{1, 1});
        actions.put("OUTSIDE", new int[]{2, 1});
        actions.put("SPADES", new int[]{-3, 0});
        actions.put("CLUBS", new int[]{-1, 0});
        actions.put("HEARTS", new int[]{1, 0});
        actions.put("DIAMONDS", new int[]{3, 0});

        Material buttonMaterial = Material.matchMaterial(plugin.getConfig().getString("cards.button-material", "STONE_BUTTON"));
        if (buttonMaterial == null || !buttonMaterial.isBlock()) buttonMaterial = Material.STONE_BUTTON;

        for (Map.Entry<String, int[]> entry : actions.entrySet()) {
            int[] pos = entry.getValue();
            Block backing = offset(base, right, pos[0], pos[1]).getBlock();
            Block button = backing.getRelative(machine.facing());
            button.setType(buttonMaterial, false);

            if (button.getBlockData() instanceof Switch data) {
                data.setAttachedFace(FaceAttachable.AttachedFace.WALL);
                data.setFacing(machine.facing());
                button.setBlockData(data, false);
            }

            Location labelLocation = button.getLocation().add(0.5, 0.85, 0.5);
            TextDisplay label = button.getWorld().spawn(labelLocation, TextDisplay.class, display -> {
                display.text(plugin.component("&f" + entry.getKey()));
                display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.setShadowed(true);
                display.getPersistentDataContainer().set(
                        machineKey,
                        PersistentDataType.STRING,
                        machine.id().toString()
                );
            });
        }
    }

    private String buttonAction(CardMachine machine, Block block) {
        if (machine == null || block == null || machine.origin().getWorld() != block.getWorld()) return null;

        BlockFace right = rotateRight(machine.facing().getOppositeFace());
        Map<String, int[]> actions = new LinkedHashMap<>();
        actions.put("RED", new int[]{-3, 1});
        actions.put("BLACK", new int[]{-2, 1});
        actions.put("HIGHER", new int[]{-1, 1});
        actions.put("LOWER", new int[]{0, 1});
        actions.put("INSIDE", new int[]{1, 1});
        actions.put("OUTSIDE", new int[]{2, 1});
        actions.put("SPADES", new int[]{-3, 0});
        actions.put("CLUBS", new int[]{-1, 0});
        actions.put("HEARTS", new int[]{1, 0});
        actions.put("DIAMONDS", new int[]{3, 0});

        for (Map.Entry<String, int[]> entry : actions.entrySet()) {
            int[] pos = entry.getValue();
            Block expected = offset(machine.origin(), right, pos[0], pos[1])
                    .getBlock()
                    .getRelative(machine.facing());
            if (sameBlock(expected.getLocation(), block.getLocation())) return entry.getKey();
        }
        return null;
    }

    private static Location offset(Location base, BlockFace right, int horizontal, int vertical) {
        return base.clone().add(right.getModX() * horizontal, vertical, right.getModZ() * horizontal);
    }

    private static BlockFace cardinal(BlockFace face) {
        return switch (face) {
            case NORTH, SOUTH, EAST, WEST -> face;
            default -> BlockFace.NORTH;
        };
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static BlockFace rotateRight(BlockFace forward) {
        return switch (forward) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }
}
