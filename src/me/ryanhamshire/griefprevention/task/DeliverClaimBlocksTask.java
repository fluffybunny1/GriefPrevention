/*
 * This file is part of GriefPrevention, licensed under the MIT License (MIT).
 *
 * Copyright (c) Ryan Hamshire
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.ryanhamshire.griefprevention.task;

import me.ryanhamshire.griefprevention.CustomLogEntryTypes;
import me.ryanhamshire.griefprevention.DataStore;
import me.ryanhamshire.griefprevention.GriefPrevention;
import me.ryanhamshire.griefprevention.PlayerData;
import me.ryanhamshire.griefprevention.configuration.PlayerStorageData;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.manipulator.mutable.entity.VehicleData;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

//FEATURE: give players claim blocks for playing, as long as they're not away from their computer

//runs every 5 minutes in the main thread, grants blocks per hour / 12 to each online player who appears to be actively playing
public class DeliverClaimBlocksTask implements Runnable {

    private Player player;

    public DeliverClaimBlocksTask(Player player) {
        this.player = player;
    }

    @Override
    public void run() {
        if (this.player == null) {
            for (World world : Sponge.getServer().getWorlds()) {
                if (GriefPrevention.getActiveConfig(world.getProperties()).getConfig().claim.claimBlocksEarned  <= 0) {
                    return;
                }

                // if no player specified, this task will create a player-specific task
                // for each player, scheduled one tick apart
                int i = 0;
                for (Entity entity : world.getEntities()) {
                    if (!(entity instanceof Player)) {
                        continue;
                    }

                    Player player = (Player) entity;
                    DeliverClaimBlocksTask newTask = new DeliverClaimBlocksTask(player);
                    Sponge.getGame().getScheduler().createTaskBuilder().delayTicks(i++).execute(newTask)
                            .submit(GriefPrevention.instance);
                }
            }
        }

        // otherwise, deliver claim blocks to the specified player
        else {
            DataStore dataStore = GriefPrevention.instance.dataStore;
            PlayerData playerData = dataStore.getPlayerData(player.getWorld(), player.getUniqueId());

            Location<World> lastLocation = playerData.lastAfkCheckLocation;
            try {
                // if he's not in a vehicle and has moved at least three blocks since the last check and he's not being pushed around by fluids
                if (!player.get(VehicleData.class).isPresent() &&
                        (lastLocation == null || lastLocation.getPosition().distanceSquared(player.getLocation().getPosition()) >= 0) &&
                        !((net.minecraft.block.Block) player.getLocation().getBlockType()).getMaterial().isLiquid()) {
                    // add blocks
                    int accruedBlocks = GriefPrevention.getActiveConfig(player.getWorld().getProperties()).getConfig().claim.claimBlocksEarned / 6;
                    if (accruedBlocks < 0) {
                        accruedBlocks = 1;
                    }

                    GriefPrevention.addLogEntry("Delivering " + accruedBlocks + " blocks to " + player.getName(), CustomLogEntryTypes.Debug, true);
                    PlayerStorageData playerStorage = playerData.getStorageData();
                    if (playerStorage == null) {
                        GriefPrevention.instance.dataStore.createPlayerData(player.getWorld().getProperties(), player.getUniqueId());
                        playerStorage = playerData.getStorageData();
                    }
                    playerStorage.getConfig().accruedClaimBlocks += accruedBlocks;
                } else {
                    GriefPrevention.addLogEntry(player.getName() + " isn't active enough.", CustomLogEntryTypes.Debug, true);
                }
            } catch (IllegalArgumentException e) {
            } catch (Exception e) {
                GriefPrevention.addLogEntry("Problem delivering claim blocks to player " + player.getName() + ":");
                e.printStackTrace();
            }

            // remember current location for next time
            playerData.lastAfkCheckLocation = player.getLocation();
        }
    }
}
