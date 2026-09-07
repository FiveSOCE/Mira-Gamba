package com.mira.gamba.model;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.UUID;

public record CardMachine(UUID id, Location origin, BlockFace facing, long bet) {}
