/*
 * This file is part of GriefPrevention, licensed under the MIT License (MIT).
 *
 * Copyright (c) bloodmc
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
package me.ryanhamshire.griefprevention;

import com.google.common.collect.Maps;
import me.ryanhamshire.griefprevention.claim.Claim;
import me.ryanhamshire.griefprevention.configuration.GriefPreventionConfig;
import me.ryanhamshire.griefprevention.configuration.PlayerStorageData;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.storage.WorldProperties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

public class PlayerDataWorldManager {

    public final static Path playerDataPath = Paths.get("GriefPreventionData", "PlayerData");
    private WorldProperties worldProperties;
    private GriefPreventionConfig<?> activeConfig;
    private boolean useGlobalStorage = false;
    
    // World UUID -> player data
    private Map<UUID, PlayerData> playerDataList = Maps.newHashMap();
    // Player UUID -> storage
    private Map<UUID, PlayerStorageData> playerStorageList = Maps.newHashMap();
    // Player UUID -> claims
    private Map<UUID, List<Claim>> playerClaimList = Maps.newHashMap();
    // World claim list
    private List<Claim> worldClaims = new ArrayList<>();
    // Claim UUID -> Claim
    private Map<UUID, Claim> claimUniqueIdMap = Maps.newHashMap();

    public PlayerDataWorldManager() {
        this.worldProperties = null;
        this.activeConfig = GriefPrevention.getGlobalConfig();
        this.useGlobalStorage = true;
    }

    public PlayerDataWorldManager(WorldProperties worldProperties) {
        this.worldProperties = worldProperties;
        this.activeConfig = GriefPrevention.getActiveConfig(worldProperties);
    }

    public PlayerData getPlayerData(UUID playerUniqueId) {
        PlayerData playerData = this.playerDataList.get(playerUniqueId);
        if (playerData == null) {
            return createPlayerData(playerUniqueId);
        } else {
            return playerData;
        }
    }

    public PlayerData createPlayerData(UUID playerUniqueId) {
        PlayerData playerData = this.playerDataList.get(playerUniqueId);
        if (playerData != null) {
            return playerData;
        }

        Path rootPath = Sponge.getGame().getSavesDirectory().resolve(Sponge.getGame().getServer().getDefaultWorld().get().getWorldName());
        Path playerFilePath = null;
        if (this.useGlobalStorage || this.worldProperties.getUniqueId() == Sponge.getGame().getServer().getDefaultWorld().get().getUniqueId()) {
            playerFilePath = rootPath.resolve(playerDataPath).resolve(playerUniqueId.toString());
        } else {
            playerFilePath = rootPath.resolve(this.worldProperties.getWorldName()).resolve(playerDataPath).resolve(playerUniqueId.toString());
        }

        PlayerStorageData playerStorage = new PlayerStorageData(playerFilePath, playerUniqueId, this.activeConfig.getConfig().general.claimInitialBlocks);
        List<Claim> claimList = new ArrayList<>();
        playerData = new PlayerData(this.worldProperties, playerUniqueId, playerStorage, this.activeConfig, claimList);
        this.playerStorageList.put(playerUniqueId, playerStorage);
        this.playerDataList.put(playerUniqueId, playerData);
        this.playerClaimList.put(playerUniqueId, claimList);
        return playerData;
    }

    public void removePlayer(UUID playerUniqueId) {
        this.playerClaimList.remove(playerUniqueId);
        this.playerStorageList.remove(playerUniqueId);
        this.playerDataList.remove(playerUniqueId);
    }

    public void addPlayerClaim(Claim claim) {
        // validate player data
        if (claim.ownerID != null && this.playerDataList.get(claim.ownerID) == null) {
            // create PlayerData for claim
            createPlayerData(claim.ownerID);
        }

        if (claim.parent == null) {
            List<Claim> claims = this.playerClaimList.get(claim.ownerID);
            claims.add(claim);
            this.worldClaims.add(claim);
            this.claimUniqueIdMap.put(claim.id, claim);
            return;
        }

        // subdivisions are added under their parent, not directly to the hash map for direct search
        if (!claim.parent.children.contains(claim)) {
            claim.parent.children.add(claim);
        }
    }

    public void removePlayerClaim(Claim claim) {
        this.playerClaimList.get(claim.ownerID).remove(claim);
        this.worldClaims.remove(claim);
        this.claimUniqueIdMap.remove(claim.id);
    }

    @Nullable
    public Claim getClaimByUUID(UUID claimUniqueId) {
        return this.claimUniqueIdMap.get(claimUniqueId);
    }

    @Nullable
    public List<Claim> getPlayerClaims(UUID playerUniqueId) {
        return this.playerClaimList.get(playerUniqueId);
    }

    public List<Claim> getWorldClaims() {
        return this.worldClaims;
    }

    public void changeClaimOwner(Claim claim, UUID newOwnerID) throws NoTransferException {
        // if it's a subdivision, throw an exception
        if (claim.parent != null) {
            throw new NoTransferException("Subdivisions can't be transferred.  Only top-level claims may change owners.");
        }

        // determine current claim owner
        if (claim.isAdminClaim()) {
            return;
        }

        PlayerData ownerData = this.getPlayerData(claim.ownerID);
        // determine new owner
        PlayerData newOwnerData = this.getPlayerData(newOwnerID);

        if (newOwnerData == null) {
            throw new NoTransferException("Could not locate PlayerData for new owner with UUID " + newOwnerID + ".");
        }
        // transfer
        claim.ownerID = newOwnerID;
        claim.getClaimData().setClaimOwnerUniqueId(newOwnerID);

        // adjust blocks and other records
        if (ownerData != null) {
              ownerData.getClaims().remove(claim);
        }
        newOwnerData.getClaims().add(claim);
        claim.getClaimStorage().save();
    }

    public void save() {
        for (List<Claim> claimList : this.playerClaimList.values()) {
            for (Claim claim : claimList) {
                claim.getClaimStorage().save();
            }
        }

        for (PlayerStorageData storageData : this.playerStorageList.values()) {
            if (storageData != null) {
                storageData.save();
            }
        }
    }

    @SuppressWarnings("serial")
    public class NoTransferException extends Exception {

        NoTransferException(String message) {
            super(message);
        }
    }
}
