package com.mira.gamba.listener;

import com.mira.gamba.MiraGambaPlugin;
import com.mira.gamba.model.SlotMachine;
import com.mira.gamba.service.MachineService;
import com.mira.gamba.service.SpinService;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class MachineListener implements Listener {
    private final MiraGambaPlugin plugin;
    private final MachineService machines;
    private final SpinService spins;

    public MachineListener(MiraGambaPlugin plugin, MachineService machines, SpinService spins) {
        this.plugin = plugin;
        this.machines = machines;
        this.spins = spins;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        SlotMachine machine = machines.byTrigger(event.getClickedBlock());
        if (machine == null) return;
        event.setCancelled(true);
        spins.spin(event.getPlayer(), machine);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;
        if (machines.cell(frame) < 0) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrameBreak(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;
        if (machines.cell(frame) < 0) return;
        event.setCancelled(true);
    }
}
