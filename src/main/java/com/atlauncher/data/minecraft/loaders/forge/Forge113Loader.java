/*
 * ATLauncher - https://github.com/ATLauncher/ATLauncher
 * Copyright (C) 2013-2019 ATLauncher
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.atlauncher.data.minecraft.loaders.forge;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.atlauncher.App;
import com.atlauncher.Gsons;
import com.atlauncher.LogManager;
import com.atlauncher.data.Downloadable;
import com.atlauncher.data.minecraft.Arguments;
import com.atlauncher.data.minecraft.Download;
import com.atlauncher.data.minecraft.Library;
import com.atlauncher.utils.Hashing;
import com.atlauncher.utils.Utils;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class Forge113Loader extends ForgeLoader {
    @Override
    public ForgeInstallProfile getInstallProfile() {
        ForgeInstallProfile installProfile = super.getInstallProfile();

        installProfile.data.put("SIDE", new Data("client", "server"));
        installProfile.data.put("MINECRAFT_JAR",
                new Data(instanceInstaller.getMinecraftJarLibrary("client").getAbsolutePath(),
                        instanceInstaller.getMinecraftJarLibrary("server").getAbsolutePath()));

        return installProfile;
    }

    public Version getVersion() {
        Version version = null;

        try {
            version = Gsons.DEFAULT.fromJson(new FileReader(new File(this.tempDir, "version.json")), Version.class);
        } catch (JsonSyntaxException | JsonIOException | FileNotFoundException e) {
            LogManager.logStackTrace(e);
        }

        return version;
    }

    @Override
    public List<Downloadable> getDownloadableLibraries() {
        List<Downloadable> librariesToDownload = new ArrayList<>();

        File librariesDirectory = this.instanceInstaller.isServer() ? this.instanceInstaller.getLibrariesDirectory()
                : App.settings.getGameLibrariesDir();

        ForgeInstallProfile installProfile = this.getInstallProfile();

        for (ForgeLibrary library : installProfile.getLibraries()) {
            Download artifact = library.downloads.artifact;
            File downloadTo = new File(App.settings.getGameLibrariesDir(), artifact.path);
            File finalDownloadTo = new File(librariesDirectory, artifact.path);

            if (artifact.url == null) {
                File extractedLibraryFile = new File(this.tempDir, "maven/" + artifact.path);

                if (extractedLibraryFile.exists() && !finalDownloadTo.exists()) {
                    finalDownloadTo.getParentFile().mkdirs();
                    if (!Utils.copyFile(extractedLibraryFile, finalDownloadTo, true)) {
                        LogManager.error("Failed to copy forge library file");
                        instanceInstaller.cancel(true);
                    }
                } else if (finalDownloadTo.exists()
                        && !Hashing.sha1(finalDownloadTo.toPath()).equals(Hashing.HashCode.fromString(artifact.sha1))) {
                    LogManager.error("Failed to find and verify forge library file " + extractedLibraryFile);
                    instanceInstaller.cancel(true);
                }
            } else {
                librariesToDownload.add(new Downloadable(artifact.url, downloadTo, artifact.sha1, artifact.size,
                        instanceInstaller, false, finalDownloadTo, true));
            }
        }

        Version version = this.getVersion();

        for (ForgeLibrary library : version.libraries) {
            Download artifact = library.downloads.artifact;
            File downloadTo = new File(App.settings.getGameLibrariesDir(), artifact.path);
            File finalDownloadTo = new File(librariesDirectory, artifact.path);

            if (artifact.url == null) {
                File extractedLibraryFile = new File(this.tempDir, "maven/" + artifact.path);

                if (extractedLibraryFile.exists() && !finalDownloadTo.exists()) {

                    new File(finalDownloadTo.getAbsolutePath().substring(0,
                            finalDownloadTo.getAbsolutePath().lastIndexOf(File.separatorChar))).mkdirs();
                    Utils.copyFile(extractedLibraryFile, finalDownloadTo, true);
                } else if (finalDownloadTo.exists()
                        && !Hashing.sha1(finalDownloadTo.toPath()).equals(Hashing.HashCode.fromString(artifact.sha1))) {
                    LogManager.warn("Cannot resolve Forge loader version library with name of " + library.name);
                }

                if (this.instanceInstaller.isServer()) {
                    Utils.copyFile(extractedLibraryFile,
                            new File(this.instanceInstaller.getRootDirectory(), downloadTo.getName()), true);
                }
            } else {
                librariesToDownload.add(new Downloadable(artifact.url, downloadTo, artifact.sha1, artifact.size,
                        instanceInstaller, false, finalDownloadTo, true));
            }
        }

        return librariesToDownload;
    }

    public void runProcessors() {
        ForgeInstallProfile installProfile = this.getInstallProfile();

        installProfile.processors.stream().forEach(processor -> {
            if (!instanceInstaller.isCancelled()) {
                try {
                    processor.process(installProfile, this.tempDir, instanceInstaller);
                } catch (IOException e) {
                    LogManager.logStackTrace(e);
                    LogManager.error("Failed to process processor with jar " + processor.getJar());
                    instanceInstaller.cancel(true);
                }
            }
        });
    }

    public List<Library> getLibraries() {
        return this.getVersion().libraries.stream().collect(Collectors.toList());
    }

    public Arguments getArguments() {
        return this.getVersion().arguments;
    }

    public String getMainClass() {
        return this.getVersion().mainClass;
    }

    @Override
    public String getServerJar() {
        Library forgeLibrary = this.getVersion().libraries.stream()
                .filter(library -> library.downloads.artifact.url.isEmpty()).findFirst().orElse(null);

        if (forgeLibrary != null) {
            return forgeLibrary.downloads.artifact.path.substring(
                    forgeLibrary.downloads.artifact.path.lastIndexOf("/") + 1,
                    forgeLibrary.downloads.artifact.path.length());
        }

        return null;
    }

    @Override
    public boolean useMinecraftLibraries() {
        return true;
    }

    @Override
    public boolean useMinecraftArguments() {
        return true;
    }
}