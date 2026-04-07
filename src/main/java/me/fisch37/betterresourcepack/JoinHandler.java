package me.fisch37.betterresourcepack;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.resource.ResourcePackCallback;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static me.fisch37.betterresourcepack.ReloadPackTask.PACK_UUID;
import static me.fisch37.betterresourcepack.ReloadPackTask.hex;

public class JoinHandler implements Listener {
    private final PackInfo packInfo;

    public JoinHandler(PackInfo packInfo){
        super();
        this.packInfo = packInfo;
    }

    private final Map<UUID, CountDownLatch> pendingPlayers = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrePlayerLeave(PlayerConnectionCloseEvent event) {
        final CountDownLatch latch = pendingPlayers.remove(event.getPlayerUniqueId());
        if (latch != null)
            latch.countDown();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrePlayerJoin(AsyncPlayerConnectionConfigureEvent event){
        if (!this.packInfo.isConfigured())
            return;

        final UUID uuid = event.getConnection().getProfile().getId();
        if (uuid == null)
            return;

        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch previous = pendingPlayers.put(uuid, latch);
        if (previous != null)
            previous.countDown();

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
                .callback(
                    ResourcePackCallback.onTerminal(
                        // Applied
                        (packId, audience) -> latch.countDown(),
                        // Declined
                        (packId, audience) -> latch.countDown()
                    )
                )
                .build();

            event.getConnection().getAudience().sendResourcePacks(request);
            latch.await(30L, TimeUnit.SECONDS);

        } catch (URISyntaxException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            pendingPlayers.remove(uuid, latch); // only remove if it's still the same latch, otherwise it means the player has reconnected and we don't want to remove the new latch
        }
    }
}
