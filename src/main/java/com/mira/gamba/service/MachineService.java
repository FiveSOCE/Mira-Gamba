package com.mira.gamba.service;

import com.mira.gamba.MiraGambaPlugin;
import com.mira.gamba.model.SlotMachine;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MachineService {
    private static final int FRAME_COUNT = 15;

    private final MiraGambaPlugin plugin;
    private final File file;
    private final NamespacedKey machineKey;
    private final NamespacedKey cellKey;
    private final Map<UUID, SlotMachine> machines = new HashMap<>();

    public MachineService(MiraGambaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "machines.yml");
        this.machineKey = new NamespacedKey(plugin, "machine_id");
        this.cellKey = new NamespacedKey(plugin, "cell_index");
        load();
    }

    public Collection<SlotMachine> all() {
        return Collections.unmodifiableCollection(machines.values());
    }

    public SlotMachine byTrigger(Block block) {
        if (block == null) return null;
        for (SlotMachine machine : machines.values()) {
            if (sameBlock(machine.trigger(), block.getLocation())) return machine;
        }
        return null;
    }

    public SlotMachine nearest(Location location, double radius) {
        SlotMachine best = null;
        double bestSq = radius * radius;
        for (SlotMachine machine : machines.values()) {
            if (machine.trigger().getWorld() != location.getWorld()) continue;
            double distance = machine.trigger().distanceSquared(location);
            if (distance <= bestSq) {
                bestSq = distance;
                best = machine;
            }
        }
        return best;
    }

    public SlotMachine create(Player player, long bet) {
        BlockFace forward = cardinal(player.getFacing());
        BlockFace front = forward.getOppositeFace();
        BlockFace right = rotateRight(forward);

        Location base = player.getLocation().getBlock().getRelative(forward, 4).getLocation();
        Material backing = Material.matchMaterial(
                plugin.getConfig().getString("machine.backing-material", "BLACK_CONCRETE")
        );
        if (backing == null || !backing.isBlock()) backing = Material.BLACK_CONCRETE;

        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                offset(base, right, x, y).getBlock().setType(backing, false);
            }
        }

        Material trigger = Material.matchMaterial(
                plugin.getConfig().getString("machine.trigger-material", "STONE_BUTTON")
        );
        if (trigger == null || !trigger.isBlock()) trigger = Material.STONE_BUTTON;

        Block backingBlock = offset(base, right, 0, 0).getBlock();
        Block triggerBlock = backingBlock.getRelative(front);
        triggerBlock.setType(trigger, false);

        if (triggerBlock.getBlockData() instanceof Switch button) {
            button.setFace(FaceAttachable.AttachedFace.WALL);
            button.setFacing(front);
            triggerBlock.setBlockData(button, false);
        }

        UUID id = UUID.randomUUID();
        SlotMachine machine = new SlotMachine(id, triggerBlock.getLocation(), front, bet);
        machines.put(id, machine);

        spawnFrames(machine, base, right);
        save();
        return machine;
    }

    public boolean remove(SlotMachine machine) {
        if (machine == null) return false;
        machines.remove(machine.id());

        World world = machine.trigger().getWorld();
        if (world != null) {
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                String id = frame.getPersistentDataContainer().get(machineKey, PersistentDataType.STRING);
                if (machine.id().toString().equals(id)) frame.remove();
            }
            machine.trigger().getBlock().setType(Material.AIR, false);
        }

        save();
        return true;
    }

    public List<ItemFrame> frames(SlotMachine machine) {
        List<ItemFrame> frames = new ArrayList<>();
        if (machine == null || machine.trigger().getWorld() == null) return frames;

        for (ItemFrame frame : machine.trigger().getWorld().getEntitiesByClass(ItemFrame.class)) {
            String id = frame.getPersistentDataContainer().get(machineKey, PersistentDataType.STRING);
            if (machine.id().toString().equals(id)) frames.add(frame);
        }

        frames.sort(Comparator.comparingInt(frame ->
                Optional.ofNullable(
                        frame.getPersistentDataContainer().get(cellKey, PersistentDataType.INTEGER)
                ).orElse(99)
        ));
        return frames;
    }

    public int cell(ItemFrame frame) {
        return Optional.ofNullable(
                frame.getPersistentDataContainer().get(cellKey, PersistentDataType.INTEGER)
        ).orElse(-1);
    }

    public void respawnMissingFrames() {
        for (SlotMachine machine : machines.values()) {
            if (frames(machine).size() == FRAME_COUNT) continue;
            World world = machine.trigger().getWorld();
            if (world == null) continue;

            BlockFace front = machine.facing();
            BlockFace forward = front.getOppositeFace();
            BlockFace right = rotateRight(forward);
            Location base = machine.trigger().getBlock().getRelative(forward).getLocation();

            for (ItemFrame frame : frames(machine)) frame.remove();
            spawnFrames(machine, base, right);

            Block triggerBlock = machine.trigger().getBlock();
            if (triggerBlock.getBlockData() instanceof Switch button) {
                button.setFace(FaceAttachable.AttachedFace.WALL);
                button.setFacing(front);
                triggerBlock.setBlockData(button, false);
            }
        }
    }

    private void spawnFrames(SlotMachine machine, Location base, BlockFace right) {
        World world = base.getWorld();
        if (world == null) return;

        int index = 0;
        for (int row = 2; row >= 0; row--) {
            for (int col = -2; col <= 2; col++) {
                Location backing = offset(base, right, col, row + 1);
                Location spawn = backing.getBlock()
                        .getRelative(machine.facing())
                        .getLocation()
                        .add(0.5, 0.5, 0.5);

                ItemFrame frame = world.spawn(spawn, ItemFrame.class, entity -> {
                    entity.setFacingDirection(machine.facing(), true);
                    entity.setFixed(true);
                    entity.setVisible(true);
                    entity.setInvulnerable(true);
                    if (plugin.getConfig().getBoolean("machine.frame-glowing", true)) {
                        entity.setGlowing(true);
                    }
                    entity.getPersistentDataContainer().set(
                            machineKey,
                            PersistentDataType.STRING,
                            machine.id().toString()
                    );
                });

                frame.getPersistentDataContainer().set(
                        cellKey,
                        PersistentDataType.INTEGER,
                        index++
                );
            }
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

                double x = section.getDouble(raw + ".x");
                double y = section.getDouble(raw + ".y");
                double z = section.getDouble(raw + ".z");
                BlockFace facing = BlockFace.valueOf(section.getString(raw + ".facing", "SOUTH"));
                long bet = section.getLong(raw + ".bet");

                machines.put(
                        id,
                        new SlotMachine(id, new Location(world, x, y, z), facing, bet)
                );
            } catch (Exception ex) {
                plugin.getLogger().warning(
                        "Skipped invalid slot machine " + raw + ": " + ex.getMessage()
                );
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (SlotMachine machine : machines.values()) {
            String root = "machines." + machine.id();
            yaml.set(root + ".world", machine.trigger().getWorld().getUID().toString());
            yaml.set(root + ".x", machine.trigger().getBlockX());
            yaml.set(root + ".y", machine.trigger().getBlockY());
            yaml.set(root + ".z", machine.trigger().getBlockZ());
            yaml.set(root + ".facing", machine.facing().name());
            yaml.set(root + ".bet", machine.bet());
        }

        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save machines.yml: " + ex.getMessage());
        }
    }

    private static Location offset(Location base, BlockFace right, int horizontal, int vertical) {
        return base.clone().add(
                right.getModX() * horizontal,
                vertical,
                right.getModZ() * horizontal
        );
    }

    private static BlockFace cardinal(BlockFace face) {
        return switch (face) {
            case NORTH, SOUTH, EAST, WEST -> face;
            default -> BlockFace.NORTH;
        };
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

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
