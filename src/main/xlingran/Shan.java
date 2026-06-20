package xlingran;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Shan - spark health RCON 桥接插件
 *
 * spark health 在主线程 dispatchCommand 时会抛 NPE（spark 自身 bug），
 * 但实际上 spark 把报告生成调度到 Craft Scheduler 异步线程，
 * 通过 System.out 输出。本插件捕获 System.out 收集异步输出。
 *
 * 用法（RCON 或控制台）： /shan [命令]
 *   不带参数 → 默认执行 "spark health"
 */
public class Shan extends JavaPlugin {

    private static final int CAPTURE_DELAY_MS = 3000;
    private static final int DISPATCH_TIMEOUT_SEC = 3;

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

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream capturing = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        synchronized (System.class) {
            System.setOut(capturing);
            System.setErr(capturing);
            try {
                // dispatchCommand 调度到主线程；spark 会抛 NPE 但不影响异步输出，吞掉
                Future<?> future = Bukkit.getScheduler().callSyncMethod(this, () -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), targetCommand);
                    } catch (Throwable ignored) {
                    }
                    return null;
                });
                try {
                    future.get(DISPATCH_TIMEOUT_SEC, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }

                // sleep 在 RCON 线程，给 spark 异步输出时间，不阻塞主线程
                Thread.sleep(CAPTURE_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                capturing.flush();
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }

        String output = buffer.toString(StandardCharsets.UTF_8);
        sender.sendMessage(output.isEmpty() ? "(无输出)" : output);
        return true;
    }
}
