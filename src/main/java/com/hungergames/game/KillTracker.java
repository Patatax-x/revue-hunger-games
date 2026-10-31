package com.hungergames.game;

import com.hungergames.classe.ClasseManager;
import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.hud.Titre;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KillTracker implements Listener {
    private static final long DUREE_REAPPARITION_TICKS = Reglages.entier("teams.revive-delay-seconds") * 20L;

    private static final long DUREE_CYCLE_REAPPARITION_TICKS = Reglages.entier("teams.revive-sweep-seconds") * 20L;

    private final JavaPlugin plugin;
    private final EquipeManager equipeManager;
    private final ClasseManager classeManager;
    private final RoleManager roleManager;

    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, String> nomsConnus = new HashMap<>();
    private final Set<UUID> eliminesHorsLigne = new HashSet<>();

    private final Set<UUID> eliminesPartie = new HashSet<>();

    private final Set<UUID> enAttenteReapparition = new HashSet<>();
    private final Map<UUID, BukkitTask> tachesReapparition = new HashMap<>();

    private final Map<UUID, BossBar> bossBarsReapparition = new HashMap<>();

    private BukkitTask tacheBalayageReapparition;

    private boolean partieDemarree = false;

    private final List<Runnable> ecouteursDemarrage = new ArrayList<>();
    private final List<Runnable> ecouteursFin = new ArrayList<>();
    private final List<Runnable> ecouteursElimination = new ArrayList<>();

    public KillTracker(JavaPlugin plugin, EquipeManager equipeManager, ClasseManager classeManager, RoleManager roleManager) {
        this.plugin = plugin;
        this.equipeManager = equipeManager;
        this.classeManager = classeManager;
        this.roleManager = roleManager;
    }

    public void ajouterEcouteurDemarragePartie(Runnable ecouteur) {
        ecouteursDemarrage.add(ecouteur);
    }

    public void ajouterEcouteurFinPartie(Runnable ecouteur) {
        ecouteursFin.add(ecouteur);
    }

    public void ajouterEcouteurElimination(Runnable ecouteur) {
        ecouteursElimination.add(ecouteur);
    }

    public void demarrerPartie() {
        partieDemarree = true;
        kills.clear();
        nomsConnus.clear();
        eliminesHorsLigne.clear();
        eliminesPartie.clear();
        annulerToutesLesReapparitions();

        tacheBalayageReapparition = Bukkit.getScheduler().runTaskTimer(
                plugin, this::balayerReapparitions, DUREE_CYCLE_REAPPARITION_TICKS, DUREE_CYCLE_REAPPARITION_TICKS);

        for (Runnable ecouteur : ecouteursDemarrage) {
            ecouteur.run();
        }
    }

    public void terminerPartie() {
        partieDemarree = false;
        eliminesHorsLigne.clear();

        eliminesPartie.clear();
        annulerToutesLesReapparitions();

        if (tacheBalayageReapparition != null) {
            tacheBalayageReapparition.cancel();
            tacheBalayageReapparition = null;
        }

        for (Runnable ecouteur : ecouteursFin) {
            ecouteur.run();
        }
    }

    private void annulerToutesLesReapparitions() {
        for (BukkitTask tache : tachesReapparition.values()) {
            tache.cancel();
        }
        tachesReapparition.clear();
        enAttenteReapparition.clear();

        for (BossBar barre : bossBarsReapparition.values()) {
            barre.removeAll();
        }
        bossBarsReapparition.clear();
    }

    private void balayerReapparitions() {
        for (UUID uuidVictime : new HashSet<>(eliminesPartie)) {
            Player victime = Bukkit.getPlayer(uuidVictime);
            if (victime == null || !victime.isOnline()) continue;

            UUID uuidPartenaire = equipeManager.getPartenaire(victime);
            if (uuidPartenaire == null) continue;

            if (eliminesPartie.contains(uuidPartenaire) || enAttenteReapparition.contains(uuidPartenaire)) {
                continue;
            }

            Player partenaire = Bukkit.getPlayer(uuidPartenaire);
            if (partenaire == null || !partenaire.isOnline()) continue;

            eliminesPartie.remove(uuidVictime);
            reapparaitrePresDuPartenaire(victime, partenaire);
        }
    }

    public boolean estPartieDemarree() {
        return partieDemarree;
    }

    public boolean estElimine(UUID uuid) {
        return eliminesPartie.contains(uuid);
    }

    public boolean estEnAttenteReapparition(UUID uuid) {
        return enAttenteReapparition.contains(uuid);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!partieDemarree) {
            return;
        }

        Player victime = event.getEntity();
        Player tueur = victime.getKiller();

        if (roleManager.estExclu(victime) || eliminesPartie.contains(victime.getUniqueId())) {
            return;
        }

        event.setDeathMessage(null);

        classeManager.reinitialiserCooldownPourJoueur(victime);

        if (tueur != null) {
            UUID uuidTueur = tueur.getUniqueId();
            kills.merge(uuidTueur, 1, Integer::sum);
            nomsConnus.put(uuidTueur, tueur.getName());
        }

        Player partenaire = trouverPartenaireVivant(victime);

        if (partenaire != null) {
            demarrerReapparition(victime, partenaire, tueur);
            return;
        }

        UUID uuidPartenaireDuo = equipeManager.getPartenaire(victime);
        if (uuidPartenaireDuo != null && enAttenteReapparition.contains(uuidPartenaireDuo)) {
            annulerReapparitionEtEliminer(uuidPartenaireDuo);
        }

        eliminesPartie.add(victime.getUniqueId());

        if (tueur != null) {
            Bukkit.broadcastMessage(Messages.get("killtracker.ete_elimine_par", "victime", victime.getName(), "tueur", tueur.getName()));
        } else {
            Bukkit.broadcastMessage(Messages.get("killtracker.ete_elimine", "victime", victime.getName()));
        }

        for (Runnable ecouteur : ecouteursElimination) {
            ecouteur.run();
        }
    }

    private String formaterDelai(int secondes) {
        if (secondes < 60) {
            return secondes + "s";
        }
        int minutes = secondes / 60;
        int reste = secondes % 60;
        return reste == 0 ? minutes + "min" : minutes + "min" + reste;
    }

    private Player trouverPartenaireVivant(Player victime) {
        UUID uuidPartenaire = equipeManager.getPartenaire(victime);
        if (uuidPartenaire == null) {
            return null;
        }

        Player partenaire = Bukkit.getPlayer(uuidPartenaire);
        if (partenaire == null || !partenaire.isOnline()) {
            return null;
        }

        if (eliminesPartie.contains(uuidPartenaire) || enAttenteReapparition.contains(uuidPartenaire)) {
            return null;
        }

        return partenaire;
    }

    private void demarrerReapparition(Player victime, Player partenaire, Player tueur) {
        UUID uuidVictime = victime.getUniqueId();
        enAttenteReapparition.add(uuidVictime);

        int dureeSecondes = (int) (DUREE_REAPPARITION_TICKS / 20L);
        String delaiAffiche = formaterDelai(dureeSecondes);

        if (tueur != null) {
            Bukkit.broadcastMessage(Messages.get("killtracker.ete_mis_terre_par_reapparait_dans", "victime", victime.getName(), "tueur", tueur.getName(), "delaiaffiche", delaiAffiche, "partenaire", partenaire.getName()));
        } else {
            Bukkit.broadcastMessage(Messages.get("killtracker.ete_mis_terre_reapparait_dans_si", "victime", victime.getName(), "delaiaffiche", delaiAffiche, "partenaire", partenaire.getName()));
        }

        Titre.enFile(victime, Messages.get("killtracker.terre"), Messages.get("killtracker.retour_dans_clique_reapparaitre", "delaiaffiche", delaiAffiche), 5, 60, 10);

        BossBar barre = Bukkit.createBossBar(Messages.get("killtracker.coequipier_revient_dans", "delaiaffiche", delaiAffiche), BarColor.YELLOW, BarStyle.SOLID);
        barre.addPlayer(partenaire);
        barre.setProgress(1.0);
        bossBarsReapparition.put(uuidVictime, barre);

        BukkitTask tache = new BukkitRunnable() {
            private int secondesRestantes = dureeSecondes;

            @Override
            public void run() {
                Player victimeActuelle = Bukkit.getPlayer(uuidVictime);

                if (secondesRestantes <= 0) {
                    terminerReapparition(uuidVictime, partenaire.getUniqueId());
                    cancel();
                    return;
                }

                if (victimeActuelle != null) {
                    victimeActuelle.sendActionBar(Messages.get("killtracker.reapparition_dans", "secondesrestantes", secondesRestantes));
                }

                barre.setTitle(Messages.get("killtracker.coequipier_revient_dans_2", "secondesrestantes", secondesRestantes));
                barre.setProgress(Math.max(0.0, Math.min(1.0, secondesRestantes / (double) dureeSecondes)));

                secondesRestantes--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        tachesReapparition.put(uuidVictime, tache);
    }

    private void terminerReapparition(UUID uuidVictime, UUID uuidPartenaire) {
        enAttenteReapparition.remove(uuidVictime);
        tachesReapparition.remove(uuidVictime);

        BossBar barre = bossBarsReapparition.remove(uuidVictime);
        if (barre != null) {
            barre.removeAll();
        }

        Player victime = Bukkit.getPlayer(uuidVictime);
        Player partenaire = Bukkit.getPlayer(uuidPartenaire);

        boolean partenaireVivant = partenaire != null && partenaire.isOnline()
                && !eliminesPartie.contains(uuidPartenaire)
                && !enAttenteReapparition.contains(uuidPartenaire);

        if (victime == null || !victime.isOnline() || !partenaireVivant) {
            eliminesPartie.add(uuidVictime);
            for (Runnable ecouteur : ecouteursElimination) {
                ecouteur.run();
            }
            return;
        }

        reapparaitrePresDuPartenaire(victime, partenaire);
    }

    private void annulerReapparitionSansEliminer(UUID uuid) {
        enAttenteReapparition.remove(uuid);

        BukkitTask tache = tachesReapparition.remove(uuid);
        if (tache != null) {
            tache.cancel();
        }

        BossBar barre = bossBarsReapparition.remove(uuid);
        if (barre != null) {
            barre.removeAll();
        }
    }

    private void annulerReapparitionEtEliminer(UUID uuid) {
        annulerReapparitionSansEliminer(uuid);

        eliminesPartie.add(uuid);

        Player joueur = Bukkit.getPlayer(uuid);
        if (joueur != null && joueur.isOnline()) {
            Titre.enFile(joueur, Messages.get("killtracker.elimine"), Messages.get("killtracker.ton_coequipier_est_tombe_aussi"), 10, 40, 10);
        }
    }

    private void reapparaitrePresDuPartenaire(Player victime, Player partenaire) {
        forcerRespawnSiMort(victime);

        victime.teleport(partenaire.getLocation().clone().add(1.0, 0, 0));
        victime.setGameMode(GameMode.SURVIVAL);
        victime.setHealth(victime.getAttribute(Attribute.MAX_HEALTH).getValue());
        victime.setFoodLevel(20);
        Titre.enFile(victime, Messages.get("killtracker.de_retour"), Messages.get("killtracker.ton_coequipier_permis_de_reapparaitre"), 10, 40, 10);
        partenaire.sendMessage(Messages.get("killtracker.est_reapparu_tes_cotes", "victime", victime.getName()));
        Bukkit.broadcastMessage(Messages.get("killtracker.est_reapparu", "victime", victime.getName()));
    }

    private void forcerRespawnSiMort(Player joueur) {
        if (joueur.isDead()) {
            joueur.spigot().respawn();
        }
    }

    public void enregistrerEliminationHorsLigne(UUID victimeUuid, String victimeNom, Player tueur) {
        if (!partieDemarree || eliminesHorsLigne.contains(victimeUuid)) {
            return;
        }

        eliminesHorsLigne.add(victimeUuid);
        eliminesPartie.add(victimeUuid);

        annulerReapparitionSansEliminer(victimeUuid);

        UUID uuidPartenaireDuo = equipeManager.getPartenaire(victimeUuid);
        if (uuidPartenaireDuo != null && enAttenteReapparition.contains(uuidPartenaireDuo)) {
            annulerReapparitionEtEliminer(uuidPartenaireDuo);
        }

        if (equipeManager.aUneEquipe(victimeUuid)) {
            equipeManager.quitterEquipeParUuid(victimeUuid, victimeNom);
        }

        if (tueur != null) {
            UUID uuidTueur = tueur.getUniqueId();
            kills.merge(uuidTueur, 1, Integer::sum);
            nomsConnus.put(uuidTueur, tueur.getName());

            Bukkit.broadcastMessage(Messages.get("killtracker.ete_elimine_deconnexion_par", "victimenom", victimeNom, "tueur", tueur.getName()));
        } else {
            Bukkit.broadcastMessage(Messages.get("killtracker.ete_elimine_deconnexion", "victimenom", victimeNom));
        }

        for (Runnable ecouteur : ecouteursElimination) {
            ecouteur.run();
        }
    }

    public void eliminerCarSortiDuMatch(Player joueur) {
        UUID uuid = joueur.getUniqueId();

        if (!partieDemarree || eliminesPartie.contains(uuid) || roleManager.estExclu(joueur)) {
            return;
        }

        annulerReapparitionSansEliminer(uuid);
        eliminesPartie.add(uuid);
        nomsConnus.putIfAbsent(uuid, joueur.getName());

        UUID uuidPartenaire = equipeManager.getPartenaire(uuid);
        if (uuidPartenaire != null && enAttenteReapparition.contains(uuidPartenaire)) {
            annulerReapparitionEtEliminer(uuidPartenaire);
        }

        if (equipeManager.aUneEquipe(uuid)) {
            equipeManager.quitterEquipe(joueur);
        }

        Bukkit.broadcastMessage(Messages.get("killtracker.ete_elimine_revenu_en_cours_de", "joueur", joueur.getName()));

        for (Runnable ecouteur : ecouteursElimination) {
            ecouteur.run();
        }
    }

    public List<Map.Entry<String, Integer>> getTop(int n) {
        List<Map.Entry<UUID, Integer>> tries = new ArrayList<>(kills.entrySet());
        tries.sort((a, b) -> b.getValue() - a.getValue());

        List<Map.Entry<String, Integer>> resultat = new ArrayList<>();
        for (int i = 0; i < Math.min(n, tries.size()); i++) {
            UUID uuid = tries.get(i).getKey();
            String nom = nomsConnus.getOrDefault(uuid, uuid.toString());
            resultat.add(Map.entry(nom, tries.get(i).getValue()));
        }

        return resultat;
    }
}
