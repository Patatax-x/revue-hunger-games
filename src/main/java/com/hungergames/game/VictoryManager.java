package com.hungergames.game;

import com.hungergames.config.Messages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.hud.Titre;
import com.hungergames.lobby.LobbyManager;
import com.hungergames.map.GameMap;
import com.hungergames.map.MapManager;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VictoryManager implements Listener {
    private static final double HAUTEUR_SPECTATE = 10.0;

    private final JavaPlugin plugin;
    private final KillTracker killTracker;
    private final EquipeManager equipeManager;
    private final RoleManager roleManager;
    private final MapManager mapManager;
    private final CommandeEnd commandeEnd;

    private final List<List<UUID>> equipesEnJeu = new ArrayList<>();
    private final Map<UUID, String> nomsJoueurs = new HashMap<>();

    private final Set<Integer> equipesDejaAnnonceesEliminees = new HashSet<>();
    private long debutPartieMillis = -1;

    private boolean glowFinalistesActif = false;

    public VictoryManager(JavaPlugin plugin, KillTracker killTracker, EquipeManager equipeManager,
                           RoleManager roleManager, MapManager mapManager, CommandeEnd commandeEnd) {
        this.plugin = plugin;
        this.killTracker = killTracker;
        this.equipeManager = equipeManager;
        this.roleManager = roleManager;
        this.mapManager = mapManager;
        this.commandeEnd = commandeEnd;

        killTracker.ajouterEcouteurDemarragePartie(this::demarrerSuivi);
        killTracker.ajouterEcouteurFinPartie(this::arreterSuivi);
        killTracker.ajouterEcouteurElimination(this::verifierVictoire);
    }

    private void demarrerSuivi() {
        equipesEnJeu.clear();
        nomsJoueurs.clear();
        equipesDejaAnnonceesEliminees.clear();
        debutPartieMillis = System.currentTimeMillis();

        for (List<Player> equipe : equipeManager.getEquipesEnLigne()) {
            List<UUID> ids = new ArrayList<>();
            for (Player joueur : equipe) {
                if (roleManager.estExclu(joueur)) continue;
                ids.add(joueur.getUniqueId());
                nomsJoueurs.put(joueur.getUniqueId(), joueur.getName());
            }
            if (!ids.isEmpty()) {
                equipesEnJeu.add(ids);
            }
        }
    }

    private void arreterSuivi() {
        if (glowFinalistesActif) {
            for (List<UUID> equipe : equipesEnJeu) {
                for (UUID uuid : equipe) {
                    Player joueur = Bukkit.getPlayer(uuid);

                    if (joueur != null) {
                        joueur.setGlowing(false);
                    }
                }
            }
        }
        glowFinalistesActif = false;

        equipesEnJeu.clear();
        nomsJoueurs.clear();
        equipesDejaAnnonceesEliminees.clear();
        debutPartieMillis = -1;
    }

    private void verifierVictoire() {
        if (!killTracker.estPartieDemarree()) return;
        if (commandeEnd.isFinDePartieEnCours()) return;

        List<List<UUID>> survivantes = new ArrayList<>();
        for (int i = 0; i < equipesEnJeu.size(); i++) {
            List<UUID> equipe = equipesEnJeu.get(i);

            if (aUnSurvivant(equipe)) {
                survivantes.add(equipe);
                continue;
            }

            if (equipesDejaAnnonceesEliminees.add(i)) {
                List<String> noms = new ArrayList<>();
                for (UUID uuid : equipe) {
                    noms.add(nomsJoueurs.getOrDefault(uuid, uuid.toString()));
                }
                Bukkit.broadcastMessage(Messages.get("victorymanager.equipe_ete_eliminee", "noms", String.join(" & ", noms)));

                String sousTitre = equipe.size() > 1
                        ? Messages.get("victorymanager.toute_ton_equipe_est_tombee_plus")
                        : Messages.get("victorymanager.ton_equipe_est_eliminee_plus_de");

                for (UUID uuid : equipe) {
                    Player membre = Bukkit.getPlayer(uuid);
                    if (membre != null && membre.isOnline()) {
                        Titre.enFile(membre, Messages.get("victorymanager.elimine"), sousTitre, 10, 70, 20);
                    }
                }
            }
        }

        CalculVictoire.Issue issue = CalculVictoire.evaluer(equipesEnJeu.size(), survivantes.size());

        if (issue == CalculVictoire.Issue.FINALE && !glowFinalistesActif) {
            activerGlowFinalistes(survivantes);
        }
        if (issue != CalculVictoire.Issue.VICTOIRE && issue != CalculVictoire.Issue.EGALITE) {
            return;
        }

        String bandeau;
        final String titreFin;
        final String sousTitreFin;

        if (issue == CalculVictoire.Issue.EGALITE) {
            bandeau = Messages.get("victorymanager.plus_aucun_survivant_egalite_aucune_equipe");
            titreFin = Messages.get("victorymanager.egalite");
            sousTitreFin = Messages.get("victorymanager.plus_aucun_survivant");
        } else {
            List<String> noms = new ArrayList<>();
            for (UUID uuid : survivantes.get(0)) {
                noms.add(nomsJoueurs.getOrDefault(uuid, uuid.toString()));
            }
            String nomsEquipe = String.join(" & ", noms);
            bandeau = Messages.get("victorymanager.victoire_de", "nomsequipe", nomsEquipe.toUpperCase());
            titreFin = Messages.get("victorymanager.victoire");
            sousTitreFin = Messages.get("victorymanager.nomsequipe", "nomsequipe", nomsEquipe);
        }

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            Titre.enFile(joueur, titreFin, sousTitreFin, 10, 70, 20);
        }

        commandeEnd.finDePartie(bandeau);
    }

    public String calculerEtAnnoncerBandeauManuel() {
        if (equipesEnJeu.isEmpty()) return null;

        List<List<UUID>> survivantes = CalculVictoire.survivantes(equipesEnJeu, killTracker::estElimine);

        if (survivantes.size() != 1) {
            return null;
        }

        List<String> noms = new ArrayList<>();
        for (UUID uuid : survivantes.get(0)) {
            noms.add(nomsJoueurs.getOrDefault(uuid, uuid.toString()));
        }
        String nomsEquipe = String.join(" & ", noms);
        String bandeau = Messages.get("victorymanager.victoire_de", "nomsequipe", nomsEquipe.toUpperCase());

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            Titre.enFile(joueur, Messages.get("victorymanager.victoire"), Messages.get("victorymanager.nomsequipe", "nomsequipe", nomsEquipe), 10, 70, 20);
        }

        return bandeau;
    }

    private void activerGlowFinalistes(List<List<UUID>> deuxEquipes) {
        glowFinalistesActif = true;
        Bukkit.broadcastMessage(Messages.get("victorymanager.plus_que_equipes_elles_brillent_maintenant"));

        for (List<UUID> equipe : deuxEquipes) {
            for (UUID uuid : equipe) {
                Player joueur = Bukkit.getPlayer(uuid);
                if (joueur != null) {
                    joueur.setGlowing(true);
                }
            }
        }
    }

    private boolean aUnSurvivant(List<UUID> equipe) {
        return CalculVictoire.aUnSurvivant(equipe, killTracker::estElimine);
    }

    public int getJoueursDepart() {
        int total = 0;
        for (List<UUID> equipe : equipesEnJeu) {
            total += equipe.size();
        }
        return total;
    }

    public int getJoueursRestants() {
        int total = 0;
        for (List<UUID> equipe : equipesEnJeu) {
            for (UUID uuid : equipe) {
                if (!killTracker.estElimine(uuid)) total++;
            }
        }
        return total;
    }

    public int getEquipesDepart() {
        return equipesEnJeu.size();
    }

    public int getEquipesRestantes() {
        int count = 0;
        for (List<UUID> equipe : equipesEnJeu) {
            if (aUnSurvivant(equipe)) count++;
        }
        return count;
    }

    public long getSecondesEcoulees() {
        if (debutPartieMillis < 0) return -1;
        return Math.max(0, (System.currentTimeMillis() - debutPartieMillis) / 1000L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player joueur = event.getPlayer();

        if (!killTracker.estPartieDemarree()) {
            Location lobby = LobbyManager.getSpawnPrincipal();
            if (lobby.getWorld() != null) {
                event.setRespawnLocation(lobby);
            }

            if (!roleManager.estAdmin(joueur)) {
                Bukkit.getScheduler().runTask(plugin, () -> joueur.setGameMode(GameMode.ADVENTURE));
            }
            return;
        }

        if (!killTracker.estElimine(joueur.getUniqueId()) && !killTracker.estEnAttenteReapparition(joueur.getUniqueId())) return;

        Location destination = null;
        UUID uuidPartenaire = equipeManager.getPartenaire(joueur);
        if (uuidPartenaire != null) {
            Player partenaire = Bukkit.getPlayer(uuidPartenaire);
            if (partenaire != null && partenaire.isOnline()
                    && !killTracker.estElimine(uuidPartenaire)
                    && !killTracker.estEnAttenteReapparition(uuidPartenaire)) {
                destination = partenaire.getLocation();
            }
        }

        if (destination == null) {
            GameMap map = mapManager.getMapActuelle();
            World monde = Bukkit.getWorld(map.getCle());
            if (monde != null) {
                destination = new Location(monde, map.getCentreX() + 0.5, map.getCentreY() + HAUTEUR_SPECTATE, map.getCentreZ() + 0.5);
            }
        }

        if (destination != null) {
            event.setRespawnLocation(destination);
        }

        Bukkit.getScheduler().runTask(plugin, () -> joueur.setGameMode(GameMode.SPECTATOR));
    }
}
