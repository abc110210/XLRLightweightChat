package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Shan - spark health RCON 桥接插件
 *
 * spark 的 health 命令在 RCON 下输出聊天组件，RCON 收到空 payload。
 * 本插件临时拦截 System.out，以控制台身份执行 spark health，
 * 等待输出完成后把捕获的纯文本回送给 RCON 调用方。
 *
 * 用法（RCON 或控制台）： /shan [命令]
 *   不带参数 → 默认执行 "spark health"
 *   带参数   → 执行自定义命令
 *
 * 同目录下需配套 plugin.yml（内容见文件末尾注释）。
 */
public class Shan extends JavaPlugin {

    private static final int CAPTURE_DELAY_MS = 800;
    private static final int FUTURE_TIMEOUT_SEC = 5;

    @Override
    public void onEnable() {
        getLogger().info("Shan 已加载，RCON 调用 /shan 触发 spark health");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("shan")) {
            return false;
        }
        String targetCommand = args.length > 0 ? String.join(" ", args) : "spark health";

        Future<String> future = Bukkit.getScheduler().callSyncMethod(
            this,
            () -> captureOutput(targetCommand)
        );

        String output;
        try {
            output = future.get(FUTURE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            output = "Error: " + e.getMessage();
        }
        sender.sendMessage(output);
        return true;
    }

    private String captureOutput(String command) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream capturing = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        synchronized (System.class) {
            System.setOut(capturing);
            System.setErr(capturing);
            try {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                Bukkit.dispatchCommand(console, command);
                Thread.sleep(CAPTURE_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                capturing.println("Error: " + e.getMessage());
            } finally {
                capturing.flush();
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }

        String output = buffer.toString(StandardCharsets.UTF_8);
        return output.isEmpty() ? "(无输出)" : output;
    }
}
