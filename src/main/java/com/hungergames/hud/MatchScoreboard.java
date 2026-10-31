package com.hungergames.hud;

import com.hungergames.config.Messages;
import com.hungergames.game.KillTracker;
import com.hungergames.game.MatchScheduler;
import com.hungergames.game.VictoryManager;
import com.hungergames.map.BorderManager;
import com.hungergames.map.GameMap;
import com.hungergames.map.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

public class MatchScoreboard {
    private static final String OBJECTIF = "hg_sidebar";

    private static final String OBJECTIF_VIE = "hg_health";

    private static final String OBJECTIF_VIE_NAMETAG = "hg_health_nom";
    private static final String PREFIXE_TEAM_LIGNE = "hg_sb_l";
    private static final int NB_LIGNES = 5;

    private final JavaPlugin plugin;
    private final MapManager mapManager;
    private final KillTracker killTracker;
    private final VictoryManager victoryManager;
    private final MatchScheduler matchScheduler;
    private final BorderManager borderManager;

    private final List<Team> lignes = new ArrayList<>();
    private BukkitTask tacheMiseAJour;

    public MatchScoreboard(JavaPlugin plugin, MapManager mapManager, KillTracker killTracker,
                            VictoryManager victoryManager, MatchScheduler matchScheduler, BorderManager borderManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.killTracker = killTracker;
        this.victoryManager = victoryManager;
        this.matchScheduler = matchScheduler;
        this.borderManager = borderManager;

        purgerResidus();

        killTracker.ajouterEcouteurDemarragePartie(this::demarrer);
        killTracker.ajouterEcouteurFinPartie(this::arreter);
    }

    private void demarrer() {
        arreter();

        // Scoreboard principal obligatoire : un scoreboard dédié ferait perdre les couleurs des Team.
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objectif = scoreboard.registerNewObjective(OBJECTIF, "dummy", Messages.get("matchscoreboard.hunger_games"));
        objectif.setDisplaySlot(DisplaySlot.SIDEBAR);

        Objective objectifVie = scoreboard.registerNewObjective(OBJECTIF_VIE, "health", Messages.get("matchscoreboard.texte"));
        objectifVie.setRenderType(RenderType.INTEGER);
        objectifVie.setDisplaySlot(DisplaySlot.PLAYER_LIST);

        Objective objectifVieNametag = scoreboard.registerNewObjective(OBJECTIF_VIE_NAMETAG, "health", Messages.get("matchscoreboard.texte"));
        objectifVieNametag.setRenderType(RenderType.INTEGER);
        objectifVieNametag.setDisplaySlot(DisplaySlot.BELOW_NAME);

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            int vie = (int) Math.ceil(joueur.getHealth());
            objectifVie.getScore(joueur.getName()).setScore(vie);
            objectifVieNametag.getScore(joueur.getName()).setScore(vie);
        }

        for (int i = 0; i < NB_LIGNES; i++) {
            String entree = ChatColor.values()[i].toString();
            Team team = scoreboard.registerNewTeam(PREFIXE_TEAM_LIGNE + i);
            team.addEntry(entree);
            objectif.getScore(entree).setScore(NB_LIGNES - i);
            lignes.add(team);
        }

        mettreAJour();
        tacheMiseAJour = Bukkit.getScheduler().runTaskTimer(plugin, this::mettreAJour, 20L, 20L);
    }

    private void arreter() {
        if (tacheMiseAJour != null) {
            tacheMiseAJour.cancel();
            tacheMiseAJour = null;
        }

        lignes.clear();
        purgerResidus();
    }

    private void purgerResidus() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        for (int i = 0; i < NB_LIGNES; i++) {
            Team team = scoreboard.getTeam(PREFIXE_TEAM_LIGNE + i);
            if (team != null) {
                team.unregister();
            }
        }

        Objective objectif = scoreboard.getObjective(OBJECTIF);
        if (objectif != null) {
            objectif.unregister();
        }

        Objective objectifVie = scoreboard.getObjective(OBJECTIF_VIE);
        if (objectifVie != null) {
            objectifVie.unregister();
        }

        Objective objectifVieNametag = scoreboard.getObjective(OBJECTIF_VIE_NAMETAG);
        if (objectifVieNametag != null) {
            objectifVieNametag.unregister();
        }
    }

    private void mettreAJour() {
        if (!killTracker.estPartieDemarree() || lignes.size() < NB_LIGNES) {
            return;
        }

        GameMap map = mapManager.getMapActuelle();

        lignes.get(0).setPrefix(Messages.get("matchscoreboard.map", "valeur", map.getAffichage()));
        lignes.get(1).setPrefix(Messages.get("matchscoreboard.joueurs", "joueursrestants", victoryManager.getJoueursRestants(), "joueursdepart", victoryManager.getJoueursDepart()));
        lignes.get(2).setPrefix(Messages.get("matchscoreboard.equipes", "equipesrestantes", victoryManager.getEquipesRestantes(), "equipesdepart", victoryManager.getEquipesDepart()));
        lignes.get(3).setPrefix(Messages.get("matchscoreboard.largage", "secondesavantprochainlargage", formatMinuteur(matchScheduler.getSecondesAvantProchainLargage(), false)));
        lignes.get(4).setPrefix(Messages.get("matchscoreboard.bordure", "secondesavantretrecissement", formatMinuteur(borderManager.getSecondesAvantRetrecissement(map), true)));
    }

    private String formatMinuteur(long secondes, boolean estBordure) {
        if (secondes < 0) {
            return Messages.get("matchscoreboard.texte_2");
        }
        if (estBordure && secondes == 0) {
            return Messages.get("matchscoreboard.en_cours");
        }
        long m = secondes / 60;
        long s = secondes % 60;
        return Messages.get("matchscoreboard.texte_3", "m", m, "s", (s < 10 ? "0" + s : String.valueOf(s)));
    }
}
