package com.hungergames.game;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTagManager implements Listener {
    private static final long DUREE_TAG_SECONDES = Reglages.entier("combat-tag.tag-seconds");
    private static final long DELAI_TIMEOUT_SECONDES = Reglages.entier("combat-tag.disconnect-timeout-seconds");
    private static final double SEUIL_ELIMINATION_IMMEDIATE = Reglages.decimal("combat-tag.instant-elimination-health");

    private final JavaPlugin plugin;
    private final KillTracker killTracker;
    private final RoleManager roleManager;

    private final Map<UUID, Long> dernierCombatMillis = new HashMap<>();
    private final Map<UUID, UUID> dernierAttaquant = new HashMap<>();
    private final Map<UUID, BukkitTask> tachesTimeout = new HashMap<>();

    public CombatTagManager(JavaPlugin plugin, KillTracker killTracker, RoleManager roleManager) {
        this.plugin = plugin;
        this.killTracker = killTracker;
        this.roleManager = roleManager;
        killTracker.ajouterEcouteurFinPartie(this::reinitialiser);
    }

    private void reinitialiser() {
        for (BukkitTask tache : tachesTimeout.values()) {
            tache.cancel();
        }
        tachesTimeout.clear();
        dernierCombatMillis.clear();
        dernierAttaquant.clear();
    }

    @EventHandler
    public void onDegatsJoueur(EntityDamageByEntityEvent event) {
        if (!killTracker.estPartieDemarree()) {
            return;
        }

        if (!(event.getEntity() instanceof Player victime)) {
            return;
        }

        Player attaquant = EquipeManager.resoudreAttaquant(event.getDamager());
        if (attaquant == null || attaquant.equals(victime)) {
            return;
        }

        if (roleManager.estExclu(victime) || roleManager.estExclu(attaquant)) {
            return;
        }

        long maintenant = System.currentTimeMillis();
        dernierCombatMillis.put(victime.getUniqueId(), maintenant);
        dernierCombatMillis.put(attaquant.getUniqueId(), maintenant);
        dernierAttaquant.put(victime.getUniqueId(), attaquant.getUniqueId());
    }

    private boolean estEnCombat(UUID uuid) {
        Long dernier = dernierCombatMillis.get(uuid);
        return dernier != null && (System.currentTimeMillis() - dernier) <= DUREE_TAG_SECONDES * 1000L;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player joueur = event.getPlayer();
        UUID uuid = joueur.getUniqueId();

        if (!killTracker.estPartieDemarree() || roleManager.estExclu(joueur) || !estEnCombat(uuid)) {
            return;
        }

        Player tueur = resoudreTueurPourAbandon(uuid);
        String nom = joueur.getName();

        if (joueur.getHealth() < SEUIL_ELIMINATION_IMMEDIATE) {
            killTracker.enregistrerEliminationHorsLigne(uuid, nom, tueur);
            nettoyerSuivi(uuid);
            return;
        }

        BukkitTask tache = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tachesTimeout.remove(uuid);
            killTracker.enregistrerEliminationHorsLigne(uuid, nom, resoudreTueurPourAbandon(uuid));
            nettoyerSuivi(uuid);
        }, DELAI_TIMEOUT_SECONDES * 20L);

        tachesTimeout.put(uuid, tache);
    }

    private Player resoudreTueurPourAbandon(UUID victimeUuid) {
        UUID attaquantUuid = dernierAttaquant.get(victimeUuid);
        if (attaquantUuid == null) {
            return null;
        }

        Player tueur = Bukkit.getPlayer(attaquantUuid);
        return tueur != null && tueur.isOnline() ? tueur : null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        BukkitTask tache = tachesTimeout.remove(uuid);
        if (tache != null) {
            tache.cancel();
            event.getPlayer().sendMessage(Messages.get("combattagmanager.tu_es_revenu_temps_pas_elimination"));
        }
    }

    private void nettoyerSuivi(UUID uuid) {
        dernierCombatMillis.remove(uuid);
        dernierAttaquant.remove(uuid);
    }
}
