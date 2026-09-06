package ac.grim.grimac.command.commands;

import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.predictionengine.MovementCheckRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class GrimPerf {

    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        Command.Builder<Sender> grimCommand = commandManager.commandBuilder("grim", "grimac");

        Command.Builder<Sender> configuredBuilder = grimCommand
                .literal("perf", "performance")
                .permission("grim.performance")
                .handler(this::handlePerformance);

        commandManager.command(configuredBuilder);
        commandManager.command(
                grimCommand.literal("stats")
                        .permission("grim.stats")
                        .handler(this::handleStats)
        );
        commandManager.command(
                commandManager.commandBuilder("grimstats")
                        .permission("grim.stats")
                        .handler(this::handleStats)
        );
    }

    private void handlePerformance(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();

        double millis = MovementCheckRunner.predictionNanos / 1000000;
        double longMillis = MovementCheckRunner.longPredictionNanos / 1000000;

        Component message1 = Component.text()
                .append(Component.text("Milliseconds per prediction (avg. 500): ", NamedTextColor.GRAY))
                .append(Component.text(millis, NamedTextColor.WHITE))
                .build();

        Component message2 = Component.text()
                .append(Component.text("Milliseconds per prediction (avg. 20k): ", NamedTextColor.GRAY))
                .append(Component.text(longMillis, NamedTextColor.WHITE))
                .build();

        sender.sendMessage(message1);
        sender.sendMessage(message2);
    }

    private void handleStats(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        double tps = ac.grim.grimac.GrimAPI.INSTANCE.getPlatformServer().getTPS();
        String formattedTps = Double.isFinite(tps) ? String.format(Locale.ROOT, "%.2f", tps) : "N/A";
        int onlinePlayers = ac.grim.grimac.GrimAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers().size();
        double predictionMillis = MovementCheckRunner.predictionNanos / 1_000_000D;

        sender.sendMessage(Component.text("LightningGrim statistics", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("TPS: " + formattedTps + " | Tracked players: " + onlinePlayers, NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Prediction average (500): " + predictionMillis + " ms", NamedTextColor.GRAY));
    }
}
