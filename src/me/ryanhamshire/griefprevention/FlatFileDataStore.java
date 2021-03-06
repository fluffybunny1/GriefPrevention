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
package me.ryanhamshire.griefprevention;

import com.flowpowered.math.vector.Vector3i;
import me.ryanhamshire.griefprevention.claim.Claim;
import me.ryanhamshire.griefprevention.configuration.ClaimStorageData;
import me.ryanhamshire.griefprevention.configuration.ClaimStorageData.SubDivisionDataNode;
import me.ryanhamshire.griefprevention.configuration.GriefPreventionConfig;
import me.ryanhamshire.griefprevention.configuration.GriefPreventionConfig.DimensionConfig;
import me.ryanhamshire.griefprevention.configuration.GriefPreventionConfig.Type;
import me.ryanhamshire.griefprevention.task.CleanupUnusedClaimsTask;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

//manages data stored in the file system
public class FlatFileDataStore extends DataStore {

    private final static Path schemaVersionFilePath = dataLayerFolderPath.resolve("_schemaVersion");
    private final static Path worldsConfigFolderPath = dataLayerFolderPath.resolve("worlds");
    public final static Path claimDataPath = Paths.get("GriefPreventionData", "ClaimData");
    public final static Path playerDataPath = Paths.get("GriefPreventionData", "PlayerData");
    private final Path rootConfigPath = Sponge.getGame().getSavesDirectory().resolve("config").resolve("GriefPrevention").resolve("worlds");
    private Path rootWorldSavePath;

    public FlatFileDataStore() {
    }

    @Override
    void initialize() throws Exception {
        // ensure data folders exist
        File worldsDataFolder = worldsConfigFolderPath.toFile();

        if (!worldsDataFolder.exists()) {
            worldsDataFolder.mkdirs();
        }

        this.rootWorldSavePath = Sponge.getGame().getSavesDirectory().resolve(Sponge.getServer().getDefaultWorldName());

        super.initialize();
    }

    @Override
    public void loadWorldData(WorldProperties worldProperties) {
        DimensionType dimType = worldProperties.getDimensionType();
        if (!Files.exists(rootConfigPath.resolve(dimType.getId()).resolve(worldProperties.getWorldName()))) {
            try {
                Files.createDirectories(rootConfigPath.resolve(dimType.getId()).resolve(worldProperties.getWorldName()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // create/load configs
        // create dimension config
        DataStore.dimensionConfigMap.put(worldProperties.getUniqueId(),
                new GriefPreventionConfig<DimensionConfig>(Type.DIMENSION, rootConfigPath.resolve(dimType.getId()).resolve("dimension.conf")));
        // create world config
        DataStore.worldConfigMap.put(worldProperties.getUniqueId(), new GriefPreventionConfig<>(Type.WORLD,
                rootConfigPath.resolve(dimType.getId()).resolve(worldProperties.getWorldName()).resolve("world.conf")));

        // check if claims are supported
        GriefPreventionConfig<GriefPreventionConfig.WorldConfig> worldConfig = DataStore.worldConfigMap.get(worldProperties.getUniqueId());
        if (worldConfig != null && worldConfig.getConfig().configEnabled && worldConfig.getConfig().claim.claimMode == 0) {
            GriefPrevention.addLogEntry("Error - World '" + worldProperties.getWorldName() + "' does not allow claims. Skipping...");
            return;
        }

        if (!GriefPrevention.getGlobalConfig().getConfig().playerdata.useGlobalPlayerDataStorage) {
            this.playerDataManagers.put(worldProperties.getUniqueId(), new PlayerDataWorldManager(worldProperties));
            // run cleanup task
            CleanupUnusedClaimsTask cleanupTask = new CleanupUnusedClaimsTask(worldProperties);
            Sponge.getGame().getScheduler().createTaskBuilder().delay(1, TimeUnit.MINUTES).execute(cleanupTask).submit(GriefPrevention.instance);
        }

        // check if world has existing data
        Path worldClaimDataPath = Paths.get(worldProperties.getWorldName()).resolve(claimDataPath);
        Path worldPlayerDataPath = Paths.get(worldProperties.getWorldName()).resolve(playerDataPath);
        if (worldProperties.getUniqueId() == Sponge.getGame().getServer().getDefaultWorld().get().getUniqueId()) {
            worldClaimDataPath = claimDataPath;
            worldPlayerDataPath = playerDataPath;
        }

        try {
            if (Files.exists(rootWorldSavePath.resolve(worldClaimDataPath))) {
                File[] files = rootWorldSavePath.resolve(worldClaimDataPath).toFile().listFiles();
                this.loadClaimData(files);
                GriefPrevention.addLogEntry("[" + worldProperties.getWorldName() + "]" + files.length + " total claims loaded.");
            } else {
                Files.createDirectories(rootWorldSavePath.resolve(worldClaimDataPath));
            }
    
            if (Files.exists(rootWorldSavePath.resolve(worldPlayerDataPath))) {
                File[] files = rootWorldSavePath.resolve(worldPlayerDataPath).toFile().listFiles();
                this.loadPlayerData(worldProperties, files);
            }
            if (!Files.exists(rootWorldSavePath.resolve(worldPlayerDataPath))) {
                Files.createDirectories(rootWorldSavePath.resolve(worldPlayerDataPath));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void unloadWorldData(WorldProperties worldProperties) {
        if (GriefPrevention.getGlobalConfig().getConfig().playerdata.useGlobalPlayerDataStorage) {
            this.playerDataManagers.remove(worldProperties);
        }
    }

    void loadClaimData(File[] files) throws Exception {
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) // avoids folders
            {
                // the filename is the claim ID. try to parse it
                UUID claimId;

                try {
                    claimId = UUID.fromString(files[i].getName());
                } catch (Exception e) {
                    GriefPrevention.addLogEntry("ERROR!! could not read claim file " + files[i].getAbsolutePath());
                    continue;
                }

                try {
                   this.loadClaim(files[i], claimId);
                }

                // if there's any problem with the file's content, log an error message and skip it
                catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("World not found")) {
                        files[i].delete();
                    } else {
                        StringWriter errors = new StringWriter();
                        e.printStackTrace(new PrintWriter(errors));
                        GriefPrevention.addLogEntry(files[i].getName() + " " + errors.toString(), CustomLogEntryTypes.Exception);
                    }
                }
            }
        }
    }

    void loadPlayerData(WorldProperties worldProperties, File[] files) throws Exception {
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) // avoids folders
            {
                // the filename is the claim ID. try to parse it
                UUID playerUUID;

                try {
                    playerUUID = UUID.fromString(files[i].getName());
                } catch (Exception e) {
                    GriefPrevention.addLogEntry("ERROR!! could not read player file " + files[i].getAbsolutePath());
                    continue;
                }

                if (!Sponge.getServer().getPlayer(playerUUID).isPresent()) {
                    return;
                }

                try {
                    this.createPlayerData(worldProperties, playerUUID);
                }

                // if there's any problem with the file's content, log an error message and skip it
                catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("World not found")) {
                        files[i].delete();
                    } else {
                        StringWriter errors = new StringWriter();
                        e.printStackTrace(new PrintWriter(errors));
                        GriefPrevention.addLogEntry(files[i].getName() + " " + errors.toString(), CustomLogEntryTypes.Exception);
                    }
                }
            }
        }
    }

    Claim loadClaim(File claimFile, UUID claimId)
            throws Exception {
        Claim claim;

        ClaimStorageData claimStorage = new ClaimStorageData(claimFile.toPath());

        // identify world the claim is in
        UUID worldUniqueId = claimStorage.getConfig().worldUniqueId;
        World world = null;
        for (World w : Sponge.getGame().getServer().getWorlds()) {
            if (w.getUniqueId().equals(worldUniqueId)) {
                world = w;
                break;
            }
        }

        if (world == null) {
            throw new Exception("World UUID not found: \"" + worldUniqueId + "\"");
        }

        // boundaries
        Vector3i lesserBoundaryCornerPos = positionFromString(claimStorage.getConfig().lesserBoundaryCornerPos);
        Vector3i greaterBoundaryCornerPos = positionFromString(claimStorage.getConfig().greaterBoundaryCornerPos);
        Location<World> lesserBoundaryCorner = new Location<World>(world, lesserBoundaryCornerPos);
        Location<World> greaterBoundaryCorner = new Location<World>(world, greaterBoundaryCornerPos);

        // owner
        UUID ownerID = claimStorage.getConfig().ownerUniqueId;
        if (ownerID == null) {
            GriefPrevention.addLogEntry("Error - this is not a valid UUID: " + ownerID + ".");
            GriefPrevention.addLogEntry("  Converted land claim to administrative @ " + lesserBoundaryCorner.toString());
        }

        // instantiate
        claim = new Claim(lesserBoundaryCorner, greaterBoundaryCorner, claimId);
        claim.ownerID = ownerID;
        claim.world = lesserBoundaryCorner.getExtent();
        claim.type = claimStorage.getConfig().claimType;
        claim.setClaimStorage(claimStorage);
        claim.setClaimData(claimStorage.getConfig());
        claim.context = new Context("claim", claim.id.toString());

        // add parent claim first
        this.addClaim(claim, false);
        // check for subdivisions
        for(Map.Entry<UUID, SubDivisionDataNode> mapEntry : claimStorage.getConfig().subdivisions.entrySet()) {
            SubDivisionDataNode subDivisionData = mapEntry.getValue();
            Vector3i subLesserBoundaryCornerPos = positionFromString(subDivisionData.lesserBoundaryCornerPos);
            Vector3i subGreaterBoundaryCornerPos = positionFromString(subDivisionData.greaterBoundaryCornerPos);
            Location<World> subLesserBoundaryCorner = new Location<World>(world, subLesserBoundaryCornerPos);
            Location<World> subGreaterBoundaryCorner = new Location<World>(world, subGreaterBoundaryCornerPos);

            Claim subDivision = new Claim(subLesserBoundaryCorner, subGreaterBoundaryCorner, mapEntry.getKey());
            subDivision.id = mapEntry.getKey();
            subDivision.world = subLesserBoundaryCorner.getExtent();
            subDivision.setClaimStorage(claimStorage);
            subDivision.context = new Context("claim", subDivision.id.toString());
            subDivision.parent = claim;
            subDivision.type = Claim.Type.SUBDIVISION;
            subDivision.setClaimData(subDivisionData);
            // add subdivision
            this.addClaim(subDivision, false);
        }
        return claim;
    }

    public void updateClaimData(Claim claim, File claimFile) {

        ClaimStorageData claimStorage = claim.getClaimStorage();
        if (claimStorage == null) {
            claimStorage = new ClaimStorageData(claimFile.toPath());
            claim.setClaimStorage(claimStorage);
            claim.setClaimData(claimStorage.getConfig());
        }

        // owner
        if (!claim.isSubdivision()) {
            claimStorage.getConfig().ownerUniqueId = claim.ownerID;
            claimStorage.getConfig().worldUniqueId = claim.world.getUniqueId();
            claimStorage.getConfig().lesserBoundaryCornerPos = positionToString(claim.lesserBoundaryCorner);
            claimStorage.getConfig().greaterBoundaryCornerPos = positionToString(claim.greaterBoundaryCorner);
            claimStorage.getConfig().claimType = claim.type;
        } else {
            if (claim.getClaimData() == null) {
                claim.setClaimData(new SubDivisionDataNode());
            }

            claim.getClaimData().setLesserBoundaryCorner(positionToString(claim.lesserBoundaryCorner));
            claim.getClaimData().setGreaterBoundaryCorner(positionToString(claim.greaterBoundaryCorner));
            claimStorage.getConfig().subdivisions.put(claim.id, (SubDivisionDataNode) claim.getClaimData());
        }

        claimStorage.save();
    }

    @Override
    synchronized void writeClaimToStorage(Claim claim) {
        Path rootPath = Sponge.getGame().getSavesDirectory().resolve(Sponge.getGame().getServer().getDefaultWorld().get().getWorldName());

        try {
            // open the claim's file
            Path claimDataFolderPath = null;
            // check if main world
            if (claim.world.getUniqueId() == Sponge.getGame().getServer().getDefaultWorld().get().getUniqueId()) {
                claimDataFolderPath = rootPath.resolve(claimDataPath);
            } else {
                claimDataFolderPath = rootPath.resolve(claim.world.getName()).resolve(claimDataPath);
            }

            UUID claimId = claim.parent != null ? claim.parent.id : claim.id;
            File claimFile = new File(claimDataFolderPath + File.separator + claimId);
            if (!claimFile.exists()) {
                claimFile.createNewFile();
            }

            updateClaimData(claim, claimFile);
        }

        // if any problem, log it
        catch (Exception e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            GriefPrevention.addLogEntry(claim.id + " " + errors.toString(), CustomLogEntryTypes.Exception);
        }
    }

    // deletes a claim from the file system
    @Override
    synchronized void deleteClaimFromSecondaryStorage(Claim claim) {
        try {
            Files.delete(claim.getClaimStorage().filePath);
        } catch (IOException e) {
            e.printStackTrace();
            GriefPrevention.addLogEntry("Error: Unable to delete claim file \"" + claim.getClaimStorage().filePath + "\".");
        }
    }

    // grants a group (players with a specific permission) bonus claim blocks as
    // long as they're still members of the group
    // TODO - hook into permissions
    @Override
    synchronized void saveGroupBonusBlocks(String groupName, int currentValue) {
        /*
        // write changes to file to ensure they don't get lost
        BufferedWriter outStream = null;
        try {
            // open the group's file
            File groupDataFile = new File(playerDataFolderPath + File.separator + "$" + groupName);
            groupDataFile.createNewFile();
            outStream = new BufferedWriter(new FileWriter(groupDataFile));

            // first line is number of bonus blocks
            outStream.write(String.valueOf(currentValue));
            outStream.newLine();
        }

        // if any problem, log it
        catch (Exception e) {
            GriefPrevention.AddLogEntry("Unexpected exception saving data for group \"" + groupName + "\": " + e.getMessage());
        }

        try {
            // close the file
            if (outStream != null) {
                outStream.close();
            }
        } catch (IOException exception) {
        }
        */
    }

    @Override
    int getSchemaVersionFromStorage() {
        File schemaVersionFile = schemaVersionFilePath.toFile();
        if (schemaVersionFile.exists()) {
            BufferedReader inStream = null;
            int schemaVersion = 0;
            try {
                inStream = new BufferedReader(new FileReader(schemaVersionFile.getAbsolutePath()));

                // read the version number
                String line = inStream.readLine();

                // try to parse into an int value
                schemaVersion = Integer.parseInt(line);
            } catch (Exception e) {
            }

            try {
                if (inStream != null) {
                    inStream.close();
                }
            } catch (IOException exception) {
            }

            return schemaVersion;
        } else {
            this.updateSchemaVersionInStorage(0);
            return 0;
        }
    }

    @Override
    void updateSchemaVersionInStorage(int versionToSet) {
        BufferedWriter outStream = null;

        try {
            // open the file and write the new value
            File schemaVersionFile = schemaVersionFilePath.toFile();
            schemaVersionFile.createNewFile();
            outStream = new BufferedWriter(new FileWriter(schemaVersionFile));

            outStream.write(String.valueOf(versionToSet));
        }

        // if any problem, log it
        catch (Exception e) {
            GriefPrevention.addLogEntry("Unexpected exception saving schema version: " + e.getMessage());
        }

        // close the file
        try {
            if (outStream != null) {
                outStream.close();
            }
        } catch (IOException exception) {
        }

    }

    @Override
    void incrementNextClaimID() {
        // TODO Auto-generated method stub

    }

    @Override
    PlayerData getPlayerDataFromStorage(UUID playerID) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    void overrideSavePlayerData(UUID playerID, PlayerData playerData) {
        // TODO Auto-generated method stub

    }

}
