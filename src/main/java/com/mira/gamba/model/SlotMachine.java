package com.mira.gamba.model;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.UUID;

public record SlotMachine(UUID id, Location trigger, BlockFace facing, long bet) {}
