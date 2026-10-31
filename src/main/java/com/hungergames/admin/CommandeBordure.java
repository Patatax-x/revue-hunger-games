package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.game.KillTracker;
import com.hungergames.loot.ChestManager;
import com.hungergames.loot.ConfigMap;
import com.hungergames.map.BorderManager;
import com.hungergames.map.GameMap;
import com.hungergames.map.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public class CommandeBordure implements CommandExecutor, TabCompleter {
    private static final double MULTIPLICATEUR_LENT = Reglages.decimal("border.slow-multiplier");

    private final KillTracker killTracker;
    private final MapManager mapManager;
    private final BorderManager borderManager;
    private final ChestManager chestManager;

    public CommandeBordure(KillTracker killTracker, MapManager mapManager, BorderManager borderManager, ChestManager chestManager) {
        this.killTracker = killTracker;
        this.mapManager = mapManager;
        this.borderManager = borderManager;
        this.chestManager = chestManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!killTracker.estPartieDemarree()) {
            sender.sendMessage(Messages.get("commandebordure.erreur_bordure_est_utilisable_que_pendant"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(Messages.get("commandebordure.usage_bordure_normal_lent_eleve_treseleve"));
            return true;
        }

        GameMap map = mapManager.getMapActuelle();
        World monde = Bukkit.getWorld(map.getCle());
        if (monde == null) {
            sender.sendMessage(Messages.get("commun.erreur_map_introuvable"));
            return true;
        }

        ConfigMap config = chestManager.getConfig(map);
        int dureeNormale = config.getDureeRetrecissementSecondes();

        String arg = args[0].toLowerCase(Locale.ROOT);

        Double vitesseBlocsParSeconde = switch (arg) {
            case "eleve", "élevé", "rapide" -> BorderManager.VITESSE_ELEVE_BLOCS_PAR_SECONDE;
            case "treseleve", "tres_eleve", "trèseleve", "très_élevé" -> BorderManager.VITESSE_TRES_ELEVE_BLOCS_PAR_SECONDE;
            case "megaeleve", "mega_eleve", "mégaeleve", "méga_élevé" -> BorderManager.VITESSE_MEGA_ELEVE_BLOCS_PAR_SECONDE;
            default -> null;
        };

        if (vitesseBlocsParSeconde != null) {
            borderManager.definirVitesseBlocsParSeconde(monde, map, vitesseBlocsParSeconde);
            Bukkit.broadcastMessage(Messages.get("commandebordure.la_vitesse_de_la_zone_de", "vitesseblocsparseconde", vitesseBlocsParSeconde));
            return true;
        }

        int secondes;
        switch (arg) {
            case "normal" -> secondes = dureeNormale;
            case "lent" -> secondes = (int) Math.round(dureeNormale * MULTIPLICATEUR_LENT);
            default -> {
                try {
                    secondes = Integer.parseInt(arg);
                } catch (NumberFormatException exception) {
                    sender.sendMessage(Messages.get("commandebordure.valeur_invalide_attendu_normal_lent_eleve", "args", args[0]));
                    return true;
                }
            }
        }

        if (secondes <= 0) {
            sender.sendMessage(Messages.get("commandebordure.la_duree_doit_etre_superieure_seconde"));
            return true;
        }

        borderManager.definirVitesseRetrecissement(monde, map, secondes);
        Bukkit.broadcastMessage(Messages.get("commandebordure.la_vitesse_de_la_zone_de_2", "secondes", secondes));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("normal", "lent", "eleve", "treseleve", "megaeleve");
        }
        return List.of();
    }
}
