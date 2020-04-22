package me.libraryaddict.disguise.commands.libsdisguises;

import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.disguisetypes.TargetedDisguise;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.translations.LibsMsg;
import me.libraryaddict.disguise.utilities.translations.TranslateType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

/**
 * Created by libraryaddict on 20/04/2020.
 */
public class LDCount implements LDCommand {
    @Override
    public List<String> getTabComplete() {
        return Collections.singletonList("count");
    }

    @Override
    public String getPermission() {
        return "libsdisguises.count";
    }

    @Override
    public void onCommand(CommandSender sender, String[] args) {
        HashMap<DisguiseType, Integer> counts = new HashMap<>();

        for (Set<TargetedDisguise> disguises : DisguiseUtilities.getDisguises().values()) {
            for (Disguise disguise : disguises) {
                counts.compute(disguise.getType(), (a, b) -> (b != null ? b : 0) + 1);
            }
        }

        if (counts.isEmpty()) {
            sender.sendMessage(LibsMsg.NO_DISGUISES_IN_USE.get());
        } else {
            sender.sendMessage(LibsMsg.ACTIVE_DISGUISES_COUNT.get(counts.values().stream().reduce(Integer::sum).get()));

            ArrayList<DisguiseType> types = new ArrayList<>(counts.keySet());
            types.sort((d1, d2) -> String.CASE_INSENSITIVE_ORDER.compare(TranslateType.DISGUISES.get(d1.toReadable()),
                    TranslateType.DISGUISES.get(d2.toReadable())));

            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < types.size(); i++) {
                builder.append(LibsMsg.ACTIVE_DISGUISES_DISGUISE
                        .get(TranslateType.DISGUISES.get(types.get(i).toReadable()), counts.get(types.get(i))));

                if (i + 1 < types.size()) {
                    builder.append(LibsMsg.ACTIVE_DISGUISES_SEPERATOR.get());
                }
            }

            sender.sendMessage(LibsMsg.ACTIVE_DISGUISES.get(builder.toString()));
        }
    }

    @Override
    public LibsMsg getHelp() {
        return null;
    }
}
