package me.fisch37.betterresourcepack;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HexFormat;
import java.util.UUID;

import static me.fisch37.betterresourcepack.Utils.sendMessage;

public class ReloadPackTask extends BukkitRunnable {
    private final Plugin plugin;
    private final PackInfo packInfo;
    private final boolean sync;
    private final boolean push;
    private final CommandSender taskAuthor;
    private final String oldHash;

    private FetchTask executingTask;

    public ReloadPackTask(Plugin plugin, CommandSender taskAuthor, PackInfo packInfo, boolean sync, boolean push){
        super();
        this.plugin = plugin;
        this.taskAuthor = taskAuthor;
        this.packInfo = packInfo;
        this.sync = sync;
        this.push = push;
        this.oldHash = this.packInfo.isConfigured() ? HexFormat.of().formatHex(this.packInfo.getSha1()) : null;
    }

    private static class FetchTask extends BukkitRunnable{
        private final PackInfo packInfo;
        // Using Boolean for trinary false-null-true state
        private Boolean isSuccessful;

        public FetchTask(PackInfo packInfo){
            this.packInfo = packInfo;
        }

        @Override
        public void run() {
            try {
                this.packInfo.updateSha1();
                this.isSuccessful = true;
            } catch (IOException e) {
                this.isSuccessful = false;
            }
        }

        public Boolean getSuccessState(){
            return this.isSuccessful;
        }
    }

    public void start(){
        this.runTaskTimer(this.plugin,0L,2L);
    }


    @Override
    public void run() {
        if (this.executingTask == null) startFetchTask();
        // Success state is null when task still running
        if (this.executingTask.getSuccessState() == null) return;

        boolean op_success = this.executingTask.getSuccessState();

        if (!op_success) {
            sendToAuthor(ChatColor.RED + "Could not fetch resource pack!");
            // Logging sync allows me to essentially debug the situation. Intention is that only /reload executes with sync
            Bukkit.getLogger().warning("[BSP] Could not fetch resource pack in reload task! Sync: " + this.sync);
        } else if (saveHash()){
            if (this.push && this.oldHash != null && !this.oldHash.equals(HexFormat.of().formatHex(this.packInfo.getSha1()))) {
                sendToAuthor("Updated pack hash!");
                Bukkit.getLogger().info("[BSP] Updated pack hash!");
                pushPackToPlayers();
            }
        }
        cancel();
    }

    private void startFetchTask(){
        this.executingTask = new FetchTask(this.packInfo);
        if (this.sync) this.executingTask.runTask(this.plugin);
        else this.executingTask.runTaskAsynchronously(this.plugin);
    }

    private boolean saveHash(){
        try {
            this.packInfo.saveHash();
            return true;
        } catch (IOException e) {
            sendToAuthor(ChatColor.RED + "Could not save hash! The hash is still updated, but will reset on the next restart.");
            Bukkit.getLogger().warning("Could not save hash to cache file in reload task. Sync: " + this.sync);
            return false;
        }
    }

    public static final UUID PACK_UUID = UUID.randomUUID();

    public static String hex(byte[] bytes) {
        final StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b)); // lowercase hex
        }
        return hexString.toString();
    }

    private void pushPackToPlayers(){
        sendToAuthor("Pushing update to all players");

        try {
            final ResourcePackInfo pack = ResourcePackInfo.resourcePackInfo()
                .uri(this.packInfo.getUrl().toURI())
                .hash(hex(this.packInfo.getSha1()))
                .id(PACK_UUID)
                .build();

            final ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                    .packs(pack)
                    .required(false)
                    .replace(false)
                    .build();

            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                player.sendResourcePacks(request);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


    private void sendToAuthor(String message){
        if (this.taskAuthor != null) sendMessage(this.taskAuthor,message);
    }
}
