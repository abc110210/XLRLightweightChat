package xlingran;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class Shan extends JavaPlugin implements Listener {

    private final Map<String, String> chatFormats = new HashMap<>();

    @Override
    public void onEnable() {
        // 从配置文件读取所有聊天格式
        loadChatFormats();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("插件已启用!");
    }

    @Override
    public void onDisable() {
        getLogger().info("该插件已卸载");
    }

    private void loadChatFormats() {
        chatFormats.clear();

        ConfigurationSection messageSection = getConfig().getConfigurationSection("Message");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                String format = messageSection.getString(key);
                if (format != null && !format.isEmpty()) {
                    chatFormats.put(key, format);
                    getLogger().info("已加载聊天格式: " + key + " -> " + format);
                }
            }
        }

        getLogger().info("共加载了 " + chatFormats.size() + " 个聊天格式");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // 遍历所有聊天格式，找到玩家有权限的第一个格式
        for (Map.Entry<String, String> entry : chatFormats.entrySet()) {
            String permission = "xlr.message." + entry.getKey();

            if (player.hasPermission(permission)) {
                String format = entry.getValue()
                    .replace("%player%", player.getDisplayName())
                    .replace("%message%", event.getMessage());

                event.setFormat(ChatColor.translateAlternateColorCodes('&', format));
                return; // 找到匹配的格式后直接返回
            }
        }
    }
}
