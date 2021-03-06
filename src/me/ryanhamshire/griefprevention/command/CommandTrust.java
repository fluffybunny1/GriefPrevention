package me.ryanhamshire.griefprevention.command;

import me.ryanhamshire.griefprevention.GriefPrevention;
import me.ryanhamshire.griefprevention.claim.ClaimPermission;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;

public class CommandTrust implements CommandExecutor {

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        try {
            CommandHelper.handleTrustCommand(GriefPrevention.checkPlayer(src), ClaimPermission.BUILD, args.<String>getOne("subject").get());
        } catch (CommandException e) {
            src.sendMessage(e.getText());
        }
        return CommandResult.success();
    }
}
