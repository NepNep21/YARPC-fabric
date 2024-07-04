/*
Copyright (C) 2022 Nep Nep
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

Additional permission under GNU GPL version 3 section 7

If you modify this Program, or any covered work, by linking or combining it with Minecraft
(or a modified version of that library), containing parts covered by the terms of the Minecraft End User License Agreement,
the licensors of this Program grant you additional permission to convey the resulting work.
*/

package club.bottomservices.discordrpc.fabricmod;

import club.bottomservices.discordrpc.lib.*;
import club.bottomservices.discordrpc.lib.exceptions.NoDiscordException;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;

public class YARPC implements ClientModInitializer {
    private byte tickTimer = 0;
    private volatile YARPCConfig config;
    private Thread watchThread = null;
    private final RichPresence.Builder builder = new RichPresence.Builder();

    @Override
    public void onInitializeClient() {
        ConfigHolder<YARPCConfig> holder = AutoConfig.register(YARPCConfig.class, Toml4jConfigSerializer::new);
        this.config = holder.getConfig();
        
        var logger = LogManager.getLogger();

        var discordClient = new DiscordRPCClient(new EventListener() {
            // Log4j adds a shutdown hook that stops the logging system, since the discord connection is also closed in a shutdown
            // hook and the run order isn't guaranteed, logging the connection closing is pointless
            @Override
            public void onReady(@NotNull DiscordRPCClient client, @NotNull User user) {
                logger.info("DiscordRPC Ready");
                long ts = System.currentTimeMillis() / 1000;
                
                YARPC.this.builder.setTimestamps(ts, null);
                client.sendPresence(
                        new RichPresence.Builder().setTimestamps(ts, null).build()
                );
            }

            @Override
            public void onError(@NotNull DiscordRPCClient client, @Nullable IOException exception, @Nullable ErrorEvent event) {
                if (exception != null) {
                    logger.error("DiscordRPC error with IOException", exception);
                } else if (event != null) {
                    logger.error("DiscordRPC error with ErrorEvent code {} and message {}", event.code, event.message);
                }
            }
        }, config.appId);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (discordClient.isConnected) {
                discordClient.disconnect();
            }
            if (watchThread != null) {
                watchThread.interrupt();
            }
        }, "YARPC Shutdown Hook"));
        
        try {
            var watchService = FileSystems.getDefault().newWatchService();
            Paths.get("config").register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            watchThread = new Thread(() -> {
                WatchKey key;
                try {
                    while ((key = watchService.take()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            watchService.close();
                            break;
                        }
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }
                            if (((Path) event.context()).endsWith("yarpc.toml")) {
                                logger.info("Reloading config");
                                if (holder.load()) {
                                    init(holder, logger, discordClient);
                                }
                            }
                        }
                        key.reset();
                    }
                } catch (InterruptedException ignored) {
                } catch (IOException e) {
                    logger.error("Failed to close filesystem watcher", e);
                }
            }, "YARPC Config Watcher");
            watchThread.start();
        } catch (IOException e) {
            logger.error("Failed to create filesystem watcher for configs", e);
        }

        init(holder, logger, discordClient);

        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            // Only run every 4 seconds
            if (++tickTimer % 80 == 0) {
                tickTimer = 0;
                String largeImage = null;
                String dimensionPath = "Main Menu";
                var world = client.world;
                if (world != null) {
                    String largeImageConfig = config.largeImage;
                    dimensionPath = world.getRegistryKey().getValue().toString();
                    largeImage = largeImageConfig.isEmpty() ? dimensionPath : largeImageConfig;
                }

                String smallImage = config.smallImage;
                smallImage = smallImage.isEmpty() ? null : smallImage;
                String smallText = config.smallText;
                smallText = smallText.isEmpty() ? null : smallText;
                builder.setAssets(largeImage == null ? null : largeImage.replace(':', '_'), config.largeText, smallImage, smallText);

                String text = config.detailsFormat + '\n' + config.stateFormat;
                String placeholder = "%s";

                var player = client.player;
                for (var arg : config.formatArgs) {
                    switch (arg) {
                        case "DIMENSION" -> {
                            // This shouldn't be necessary, but apparently it is
                            assert dimensionPath != null;
                            text = text.replaceFirst(placeholder, dimensionPath);
                        }
                        case "USERNAME" -> text = text.replaceFirst(placeholder, client.getSession().getUsername());
                        case "HEALTH" -> text = text.replaceFirst(placeholder, "Health " + (player != null ? player.getHealth() : "0.0"));
                        case "HUNGER" -> text = text.replaceFirst(placeholder, "Food " + (player != null ? player.getHungerManager().getFoodLevel() : "0"));
                        case "SERVER" -> text = text.replaceFirst(placeholder, getServer(client, true));
                        case "HELD_ITEM" -> {
                            String item = "Air";
                            if (player != null) {
                                item = player.getMainHandStack().toHoverableText().getString();
                                // Remove unnecessary brackets, why is this not necessary in forge?
                                item = item.substring(1, item.length() - 1);
                            }
                            text = text.replaceFirst(placeholder, "Holding " + item);
                        }
                        case "SERVER_NAME" -> text = text.replaceFirst(placeholder, getServer(client, false));
                    }
                }
                String[] split = text.split("\n");
                builder.setText(split[0], split[1]);
            }
        });
    }
    
    private static String getServer(MinecraftClient client, boolean address) {
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null) {
            return address ? server.address : server.name;
        }
        if (client.isIntegratedServerRunning()) {
            return "Singleplayer";
        }
        return "Main Menu";
    }
    
    private void init(ConfigHolder<YARPCConfig> holder, Logger logger, DiscordRPCClient discordClient) {
        this.config = holder.getConfig();
        
        if (this.config.isEnabled) {
            if (!discordClient.isConnected) {
                try {
                    discordClient.connect();

                    new Thread(() -> {
                        while (true) {
                            if (discordClient.isConnected) {
                                discordClient.sendPresence(this.builder.build());
                            } else if (this.config.isEnabled) {
                                try {
                                    init(holder, logger, discordClient);
                                    break;
                                } catch (NoDiscordException e) {
                                    // Don't want to spam logs
                                }
                            }

                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }, "YARPC Update Thread").start();
                } catch (NoDiscordException e) {
                    logger.error("Failed initial discord connection", e);
                }
            }
        } else if (discordClient.isConnected) {
            discordClient.disconnect();
        }
    }
}