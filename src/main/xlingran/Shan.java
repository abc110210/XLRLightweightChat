package xlingran;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shan extends JavaPlugin implements Listener {

    private FileConfiguration config;

    @Override
    public void onLoad() {
        getLogger().info("该插件已加载");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("聊天监听已注册");
    }

    @Override
    public void onDisable() {
        getLogger().info("该插件已卸载");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("xlrchat")) {
            if (!sender.hasPermission("xlr.admin.reload")) {
                sender.sendMessage("§c你没有权限使用此命令");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage("§c用法: /xlrchat reload");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                config = getConfig();
                sender.sendMessage("§a配置已重载");
                return true;
            }

            sender.sendMessage("§c未知的子命令");
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        ConfigurationSection chatSection = config.getConfigurationSection("Chat");
        if (chatSection == null) return;

        String format = null;
        for (String key : chatSection.getKeys(false)) {
            String permission = "xlr.chat." + key;
            if (player.hasPermission(permission)) {
                format = chatSection.getString(key);
                break;
            }
        }

        if (format == null) {
            format = chatSection.getString("default");
            if (format == null) return;
        }

        String message = event.getMessage();
        String playerName = player.getName();

        ConfigurationSection variableSection = config.getConfigurationSection("Variable");
        Map<String, String> colorMap = new HashMap<>();
        if (variableSection != null) {
            for (String key : variableSection.getKeys(false)) {
                String gradient = variableSection.getString(key);
                String hexColor = applyGradient(message, gradient);
                colorMap.put("%" + key + "%", hexColor);
            }
        }

        String formattedMessage = format
                .replace("%player%", playerName)
                .replace("%chat%", message);

        for (Map.Entry<String, String> entry : colorMap.entrySet()) {
            formattedMessage = formattedMessage.replace(entry.getKey(), entry.getValue());
        }

        formattedMessage = translateHexColorCodes(formattedMessage);

        event.setFormat(formattedMessage);
    }

    private String applyGradient(String text, String gradient) {
        if (text == null || text.isEmpty() || gradient == null || gradient.isEmpty()) {
            return "";
        }

        String[] colors = gradient.split("-");
        if (colors.length != 2) {
            return translateHexColorCodes("#" + colors[0]);
        }

        String startHex = colors[0].replace("#", "");
        String endHex = colors[1].replace("#", "");

        int startR = Integer.parseInt(startHex.substring(0, 2), 16);
        int startG = Integer.parseInt(startHex.substring(2, 4), 16);
        int startB = Integer.parseInt(startHex.substring(4, 6), 16);

        int endR = Integer.parseInt(endHex.substring(0, 2), 16);
        int endG = Integer.parseInt(endHex.substring(2, 4), 16);
        int endB = Integer.parseInt(endHex.substring(4, 6), 16);

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            double ratio = (double) i / (text.length() - 1);

            int r = (int) (startR + (endR - startR) * ratio);
            int g = (int) (startG + (endG - startG) * ratio);
            int b = (int) (startB + (endB - startB) * ratio);

            result.append(String.format("&#%02x%02x%02x", r, g, b));
        }

        return result.toString();
    }

    private String translateHexColorCodes(String message) {
        Pattern pattern = Pattern.compile("&#([0-9a-fA-F]{6})");
        Matcher matcher = pattern.matcher(message);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = net.md_5.bungee.api.ChatColor.of("#" + hex).toString();
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        result = result.replace("&", "§");

        return result;
    }
}
