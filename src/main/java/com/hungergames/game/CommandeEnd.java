package com.hungergames.game;

import com.hungergames.admin.MatchAdminKit;
import com.hungergames.classe.ClasseManager;
import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.lobby.LobbyManager;
import com.hungergames.loot.ChestManager;
import com.hungergames.map.BorderManager;
import com.hungergames.map.GameMap;
import com.hungergames.map.MapManager;
import com.hungergames.map.MapResetManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CommandeEnd implements CommandExecutor, TabCompleter {
    private static final long DELAI_RETOUR_LOBBY_SECONDES = Reglages.entier("match.end-delay-seconds");

    private final JavaPlugin plugin;
    private final KillTracker killTracker;
    private final MapManager mapManager;
    private final ChestManager chestManager;
    private final BorderManager borderManager;
    private final MatchScheduler matchScheduler;
    private final ClasseManager classeManager;
    private final EquipeManager equipeManager;
    private final LobbyManager lobbyManager;
    private final MatchAdminKit matchAdminKit;

    private VictoryManager victoryManager;

    private MapResetManager mapResetManager;

    // estPartieDemarree repasse à false dès la victoire, avant le retour au lobby : tester aussi ce drapeau.
    private volatile boolean finDePartieEnCours = false;

    public CommandeEnd(
            JavaPlugin plugin,
            KillTracker killTracker,
            MapManager mapManager,
            ChestManager chestManager,
            BorderManager borderManager,
            MatchScheduler matchScheduler,
            ClasseManager classeManager,
            EquipeManager equipeManager,
            LobbyManager lobbyManager,
            MatchAdminKit matchAdminKit
    ) {
        this.plugin = plugin;
        this.killTracker = killTracker;
        this.mapManager = mapManager;
        this.chestManager = chestManager;
        this.borderManager = borderManager;
        this.matchScheduler = matchScheduler;
        this.classeManager = classeManager;
        this.equipeManager = equipeManager;
        this.lobbyManager = lobbyManager;
        this.matchAdminKit = matchAdminKit;
    }

    public void setVictoryManager(VictoryManager victoryManager) {
        this.victoryManager = victoryManager;
    }

    public void setMapResetManager(MapResetManager mapResetManager) {
        this.mapResetManager = mapResetManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!killTracker.estPartieDemarree()) {
            sender.sendMessage(Messages.get("commandeend.erreur_aucune_partie_est_en_cours"));
            return true;
        }

        String bandeauVictoire = victoryManager != null ? victoryManager.calculerEtAnnoncerBandeauManuel() : null;
        finDePartie(bandeauVictoire);
        return true;
    }

    public boolean isFinDePartieEnCours() {
        return finDePartieEnCours;
    }

    public void finDePartie() {
        finDePartie(null);
    }

    public void finDePartie(String bandeauVictoire) {
        finDePartieEnCours = true;

        Bukkit.broadcastMessage(Messages.get("commandeend.partie_terminee"));

        if (bandeauVictoire != null) {
            Bukkit.broadcastMessage(bandeauVictoire);
        }

        List<Map.Entry<String, Integer>> top = killTracker.getTop(Reglages.entier("match.top-kills"));

        Bukkit.broadcastMessage(Messages.get("commandeend.top_kills"));

        if (top.isEmpty()) {
            Bukkit.broadcastMessage(Messages.get("commandeend.aucun_kill_enregistre_cette_partie"));
        } else {
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<String, Integer> entree = top.get(i);
                Bukkit.broadcastMessage(Messages.get("commandeend.kill", "rang", (i + 1), "entree", entree.getKey(), "entree2", entree.getValue()));
            }
        }

        killTracker.terminerPartie();
        matchScheduler.arreter();
        matchAdminKit.retirerKit();

        GameMap mapActive = mapManager.getMapActuelle();
        World monde = Bukkit.getWorld(mapActive.getCle());

        if (monde != null) {
            monde.setPVP(false);
        }

        borderManager.arreter(monde, mapActive);

        Bukkit.broadcastMessage(Messages.get("commandeend.retour_au_lobby_dans_secondes", "delai_retour_lobby_secondes", DELAI_RETOUR_LOBBY_SECONDES));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (monde != null) {
                chestManager.nettoyerFinDePartie(monde, mapActive);
            }

            for (Player joueur : Bukkit.getOnlinePlayers()) {
                joueur.getInventory().clear();

                if (classeManager.getClasse(joueur).isPresent()) {
                    classeManager.retirerClasse(joueur);
                }
                if (equipeManager.aUneEquipe(joueur)) {
                    equipeManager.retirerEquipe(joueur);
                }
            }

            equipeManager.reinitialiserCompteurDistrict();

            lobbyManager.activerLobby();
            finDePartieEnCours = false;

            if (mapResetManager != null && mapResetManager.isAutoApresPartie()) {
                mapResetManager.reinitialiser(mapActive, Bukkit.getConsoleSender());
            }
        }, DELAI_RETOUR_LOBBY_SECONDES * 20L);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
