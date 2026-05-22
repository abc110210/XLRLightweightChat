package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shan extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Map<String, String> colorVariables;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        reloadConfig();
        
        // 加载配置
        config = getConfig();
        loadColorVariables();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("插件已加载");
    }

    @Override
    public void onDisable() {
        getLogger().info("插件已卸载");
    }

    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        super.reloadConfig();
        config = getConfig();
        loadColorVariables();
    }

    /**
     * 加载颜色变量配置
     */
    private void loadColorVariables() {
        colorVariables = new HashMap<>();
        if (config.contains("Variable")) {
            for (String key : config.getConfigurationSection("Variable").getKeys(false)) {
                String value = config.getString("Variable." + key);
                if (value != null) {
                    colorVariables.put("%" + key + "%", value);
                }
            }
        }
    }

    /**
     * 监听聊天事件
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // 取消默认消息
        event.setCancelled(true);
        
        // 查找匹配的聊天格式
        String format = findMatchingFormat(player);
        
        if (format == null) {
            // 如果没有匹配格式，使用默认格式
            format = config.getString("Chat.default");
        }
        
        if (format == null) {
            // 如果连默认格式都没有，直接发送原始消息
            broadcastMessage(player.getName(), message);
            return;
        }
        
        // 处理格式字符串
        String processedFormat = processFormat(format, player, message);
        
        // 广播消息
        broadcastProcessedMessage(processedFormat);
    }

    /**
     * 查找匹配的聊天格式（按优先级从上往下检查）
     */
    private String findMatchingFormat(Player player) {
        if (!config.contains("Chat")) {
            return null;
        }
        
        Set<String> formats = config.getConfigurationSection("Chat").getKeys(false);
        for (String formatName : formats) {
            String permission = "xlr.chat." + formatName;
            if (player.hasPermission(permission)) {
                return config.getString("Chat." + formatName);
            }
        }
        
        return null;
    }

    /**
     * 处理格式字符串，替换占位符并应用颜色
     */
    private String processFormat(String format, Player player, String message) {
        // 替换 %player%
        String result = format.replace("%player%", player.getName());
        
        // 先替换颜色变量 %color1%, %color2% 等（在 %chat% 之前）
        for (Map.Entry<String, String> entry : colorVariables.entrySet()) {
            if (result.contains(entry.getKey())) {
                String gradientConfig = entry.getValue();
                String placeholder = entry.getKey();
                
                // 查找 %colorX%%chat% 并替换为渐变后的消息
                String pattern = placeholder + "%chat%";
                if (result.contains(pattern)) {
                    String gradientResult = applyGradient(gradientConfig, message);
                    result = result.replace(pattern, gradientResult);
                }
            }
        }
        
        // 最后替换 %chat%（处理没有颜色变量的情况）
        result = result.replace("%chat%", message);
        
        // 转换传统颜色代码 & -> §
        result = ChatColor.translateAlternateColorCodes('&', result);
        
        return result;
    }

    /**
     * 应用渐变颜色到文本
     */
    private String applyGradient(String gradientConfig, String text) {
        // 解析渐变配置，格式: "#RRGGBB-#RRGGBB"
        String[] colors = gradientConfig.split("-");
        if (colors.length != 2) {
            return text;
        }
        
        String startColor = colors[0].trim();
        String endColor = colors[1].trim();
        
        // 移除 # 符号
        startColor = startColor.replace("#", "");
        endColor = endColor.replace("#", "");
        
        // 解析 RGB 值
        int startR = Integer.parseInt(startColor.substring(0, 2), 16);
        int startG = Integer.parseInt(startColor.substring(2, 4), 16);
        int startB = Integer.parseInt(startColor.substring(4, 6), 16);
        
        int endR = Integer.parseInt(endColor.substring(0, 2), 16);
        int endG = Integer.parseInt(endColor.substring(2, 4), 16);
        int endB = Integer.parseInt(endColor.substring(4, 6), 16);
        
        // 计算可见字符数量（跳过颜色代码）
        List<Integer> visibleCharIndices = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                // 跳过 § 格式的颜色代码
                i++;
                continue;
            }
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == 'x' || Character.isLetterOrDigit(next)) {
                    // 跳过 & 格式的颜色代码
                    i++;
                    continue;
                }
            }
            visibleCharIndices.add(i);
        }
        
        if (visibleCharIndices.isEmpty()) {
            return text;
        }
        
        // 构建渐变文本
        StringBuilder result = new StringBuilder();
        int visibleCount = visibleCharIndices.size();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // 处理 § 格式颜色代码
            if (c == '§' && i + 1 < text.length()) {
                result.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            
            // 处理 & 格式颜色代码
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == 'x' || Character.isLetterOrDigit(next)) {
                    result.append('§').append(next);
                    i++;
                    continue;
                }
            }
            
            // 计算当前可见字符的索引
            int visibleIndex = -1;
            for (int j = 0; j < visibleCharIndices.size(); j++) {
                if (visibleCharIndices.get(j) == i) {
                    visibleIndex = j;
                    break;
                }
            }
            
            if (visibleIndex >= 0) {
                // 计算渐变颜色
                float ratio = visibleCount > 1 ? (float) visibleIndex / (visibleCount - 1) : 0;
                int r = (int) (startR + (endR - startR) * ratio);
                int g = (int) (startG + (endG - startG) * ratio);
                int b = (int) (startB + (endB - startB) * ratio);
                
                // 生成 16 进制颜色代码 (§x§R§R§G§G§B§B)
                String hexColor = String.format("§x§%c§%c§%c§%c§%c§%c",
                    Character.forDigit((r >> 4) & 0xF, 16),
                    Character.forDigit(r & 0xF, 16),
                    Character.forDigit((g >> 4) & 0xF, 16),
                    Character.forDigit(g & 0xF, 16),
                    Character.forDigit((b >> 4) & 0xF, 16),
                    Character.forDigit(b & 0xF, 16));
                
                result.append(hexColor).append(c);
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }

    /**
     * 广播处理后的消息
     */
    private void broadcastProcessedMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    /**
     * 广播原始消息（备用方法）
     */
    private void broadcastMessage(String playerName, String message) {
        String formatted = "§a" + playerName + ": §f" + message;
        broadcastProcessedMessage(formatted);
    }

    /**
     * 处理命令
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("xlrchat")) {
            if (!sender.hasPermission("xlr.admin.reload")) {
                sender.sendMessage("§c你没有权限执行此命令！");
                return true;
            }
            
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage("§a配置已重新加载！");
                getLogger().info("配置已重新加载 by " + sender.getName());
                return true;
            }
            
            sender.sendMessage("§e用法: /xlrchat reload");
            return true;
        }
        
        return false;
    }
}