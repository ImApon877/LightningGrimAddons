package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.command.PlayerSelector;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

public class GrimTestWebhook implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("testwebhook")
                        .permission("grim.testwebhook")
                        .handler(this::handleTestWebhook)
        );
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("testwebhook")
                        .permission("grim.testwebhook")
                        .required("target", arguments.singlePlayerSelectorParser())
                        .handler(this::handleTargetTestWebhook)
        );
    }

    private void handleTestWebhook(@NotNull CommandContext<Sender> context) {
        if (GrimAPI.INSTANCE.getDiscordManager().isDisabled()) {
            context.sender().sendMessage(MessageUtil.miniMessage(GrimAPI.INSTANCE.getConfigManager().getWebhookNotEnabled()));
            return;
        }

        Sender sender = context.sender();
        GrimPlayer player = sender.isPlayer()
                ? GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(sender.getUniqueId())
                : null;
        if (player == null) {
            sender.sendMessage(MessageUtil.miniMessage("<red>From console use: /grim testwebhook <player>"));
            return;
        }

        sendPreview(sender, player);
    }

    private void handleTargetTestWebhook(@NotNull CommandContext<Sender> context) {
        if (GrimAPI.INSTANCE.getDiscordManager().isDisabled()) {
            context.sender().sendMessage(MessageUtil.miniMessage(GrimAPI.INSTANCE.getConfigManager().getWebhookNotEnabled()));
            return;
        }

        Sender sender = context.sender();
        PlayerSelector target = context.get("target");
        PlatformPlayer platformPlayer = target.getSinglePlayer().getPlatformPlayer();
        GrimPlayer player = platformPlayer == null ? null
                : GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(platformPlayer.getUniqueId());
        if (player == null) {
            sender.sendMessage(MessageUtil.miniMessage("<red>That player is exempt or offline."));
            return;
        }

        sendPreview(sender, player);
    }

    private void sendPreview(Sender sender, GrimPlayer player) {
        GrimAPI.INSTANCE.getDiscordManager().sendAlert(player, "Webhook test preview", "Webhook Test", 1).whenCompleteAsync(((successful, throwable) -> {
            if (successful == true) {
                sender.sendMessage(MessageUtil.miniMessage(GrimAPI.INSTANCE.getConfigManager().getWebhookTestSucceeded()));
                return;
            }

            sender.sendMessage(MessageUtil.miniMessage(GrimAPI.INSTANCE.getConfigManager().getWebhookTestFailed()));

            if (throwable != null) {
                LogUtil.error("Exception caught while sending a Discord webhook test alert", throwable);
            }
        }));
    }
}
