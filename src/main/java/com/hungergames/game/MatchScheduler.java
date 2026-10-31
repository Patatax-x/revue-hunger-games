package com.hungergames.game;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.hud.Titre;
import com.hungergames.loot.ChestManager;
import com.hungergames.loot.ConfigMap;
import com.hungergames.map.GameMap;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MatchScheduler {
    private final JavaPlugin plugin;
    private final ChestManager chestManager;
    private final RoleManager roleManager;
    private final KillTracker killTracker;
    private final Random random = new Random();

    private BukkitTask tacheLargage;
    private BukkitTask tacheFinPartie;

    private long intervalleLargageMillis = -1;
    private long prochainLargageMillis = -1;

    public MatchScheduler(JavaPlugin plugin, ChestManager chestManager, RoleManager roleManager, KillTracker killTracker) {
        this.plugin = plugin;
        this.chestManager = chestManager;
        this.roleManager = roleManager;
        this.killTracker = killTracker;
    }

    public void demarrer(World monde, GameMap map, ConfigMap config, Runnable finAutomatique) {
        arreter();

        intervalleLargageMillis = config.getIntervalleLargageAutoSecondes() * 1000L;
        long delaiPremierMillis = config.getDelaiPremierLargageSecondes() * 1000L;
        prochainLargageMillis = System.currentTimeMillis() + delaiPremierMillis;

        long delaiPremierTicks = config.getDelaiPremierLargageSecondes() * 20L;
        long intervalleTicks = config.getIntervalleLargageAutoSecondes() * 20L;
        tacheLargage = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            declencherLargageAuto(monde, map, config);
            prochainLargageMillis = System.currentTimeMillis() + intervalleLargageMillis;
        }, delaiPremierTicks, intervalleTicks);

        long dureeMaxTicks = config.getDureeMaxPartieSecondes() * 20L;
        tacheFinPartie = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tacheFinPartie = null;
            Bukkit.broadcastMessage(Messages.get("matchscheduler.duree_maximale_de_la_partie_atteinte"));
            finAutomatique.run();
        }, dureeMaxTicks);
    }

    public void arreter() {
        if (tacheLargage != null) {
            tacheLargage.cancel();
            tacheLargage = null;
        }
        if (tacheFinPartie != null) {
            tacheFinPartie.cancel();
            tacheFinPartie = null;
        }
        prochainLargageMillis = -1;
    }

    public long getSecondesAvantProchainLargage() {
        if (prochainLargageMillis < 0) {
            return -1;
        }
        return Math.max(0, (prochainLargageMillis - System.currentTimeMillis()) / 1000L);
    }

    private void declencherLargageAuto(World monde, GameMap map, ConfigMap config) {
        Player cible = joueurAleatoireEligible();
        if (cible == null) {
            return;
        }

        declencherLargagePresDeJoueur(monde, map, config, cible, true);
    }

    public boolean declencherLargageManuel(World monde, GameMap map, ConfigMap config, Player cibleOuNull) {
        Player cible = cibleOuNull != null ? cibleOuNull : joueurAleatoireEligible();
        if (cible == null) {
            return false;
        }

        return declencherLargagePresDeJoueur(monde, map, config, cible, false);
    }

    private static final double RAYON_LARGAGE_MANUEL = Reglages.decimal("airdrops.manual-radius");

    private boolean declencherLargagePresDeJoueur(World monde, GameMap map, ConfigMap config, Player cible, boolean automatique) {
        double rayon = automatique ? config.getRayonLargageAutoAutourJoueur() : RAYON_LARGAGE_MANUEL;
        Location loc = chestManager.declencherLargagePres(monde, map, cible.getLocation(), rayon);
        if (loc == null) {
            return false;
        }

        monde.spawnParticle(Particle.FIREWORK, loc.clone().add(0.5, 1, 0.5), 40, 0.5, 1, 0.5, 0.1);
        monde.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2.0f, 1.0f);

        String prefixe = automatique ? Messages.get("matchscheduler.largage_automatique") : Messages.get("matchscheduler.largage");
        Bukkit.broadcastMessage(Messages.get("matchscheduler.un_coffre_rare_est_apparu_en", "prefixe", prefixe, "loc", loc.getBlockX(), "loc2", loc.getBlockY(), "loc3", loc.getBlockZ()));

        for (Player p : Bukkit.getOnlinePlayers()) {
            Titre.enFile(p, Messages.get("matchscheduler.largage_2"), Messages.get("matchscheduler.fonce_le_recuperer"), 10, 40, 10);
        }

        return true;
    }

    private Player joueurAleatoireEligible() {
        List<Player> eligibles = getJoueursEligibles();
        return eligibles.isEmpty() ? null : eligibles.get(random.nextInt(eligibles.size()));
    }

    public List<Player> getJoueursEligibles() {
        List<Player> eligibles = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(p)) continue;
            if (killTracker.estElimine(p.getUniqueId())) continue;
            if (killTracker.estEnAttenteReapparition(p.getUniqueId())) continue;
            eligibles.add(p);
        }
        return eligibles;
    }
}
