package com.mira.gamba.listener;

import com.mira.gamba.model.CardMachine;
import com.mira.gamba.service.CardGameService;
import com.mira.gamba.service.CardMachineService;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class CardMachineListener implements Listener {

    private final CardMachineService machines;
    private final CardGameService games;

    public CardMachineListener(CardMachineService machines, CardGameService games) {
        this.machines = machines;
        this.games = games;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onButton(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        CardMachine machine = machines.byButton(event.getClickedBlock());
        if (machine == null) return;

        String action = machines.buttonAction(event.getClickedBlock());
        if (action == null) return;

        event.setCancelled(true);
        games.press(event.getPlayer(), machine, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;
        if (machines.frameIndex(frame) < 0) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrameBreak(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;
        if (machines.frameIndex(frame) < 0) return;
        event.setCancelled(true);
    }
}
