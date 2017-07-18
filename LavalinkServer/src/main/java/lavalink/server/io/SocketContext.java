package lavalink.server.io;

import lavalink.server.Launcher;
import lavalink.server.player.Player;
import lavalink.server.util.Util;
import net.dv8tion.jda.Core;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SocketContext {

    private static final Logger log = LoggerFactory.getLogger(SocketContext.class);

    private final WebSocket socket;
    private int shardCount;
    private final HashMap<Integer, Core> cores = new HashMap<>();
    private final HashMap<String, Player> players = new HashMap<>();
    private ScheduledExecutorService statsExecutor;

    SocketContext(WebSocket socket, int shardCount) {
        this.socket = socket;
        this.shardCount = shardCount;

        statsExecutor = Executors.newSingleThreadScheduledExecutor();
        statsExecutor.scheduleAtFixedRate(new StatsTask(this), 0, 1, TimeUnit.MINUTES);
    }

    Core getCore(int shardId) {
        return cores.computeIfAbsent(shardId,
                __ -> new Core(Launcher.config.getUserId(), new CoreClientImpl(socket, shardId))
        );
    }

    Player getPlayer(String guildId) {
        return players.computeIfAbsent(guildId,
                __ -> new Player(this, guildId)
        );
    }

    public int getShardCount() {
        return shardCount;
    }

    public WebSocket getSocket() {
        return socket;
    }

    public HashMap<String, Player> getPlayers() {
        return players;
    }

    public List<Player> getPlayingPlayers() {
        List<Player> newList = new LinkedList<>();
        players.values().forEach(player -> {
            if(player.isPlaying()) newList.add(player);
        });
        return newList;
    }

    void shutdown() {
        log.info("Shutting down " + cores.size() + " cores and " + getPlayingPlayers().size() + " playing players.");
        statsExecutor.shutdown();
        players.keySet().forEach(s -> {
            Core core = cores.get(Util.getShardFromSnowflake(s, shardCount));
            core.getAudioManager(s).closeAudioConnection();
        });

        players.values().forEach(Player::stop);
    }

}