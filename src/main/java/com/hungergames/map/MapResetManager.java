package com.hungergames.map;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.game.KillTracker;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class MapResetManager {
    private final JavaPlugin plugin;
    private final KillTracker killTracker;
    private final MapManager mapManager;

    private boolean autoApresPartie = Reglages.booleen("reset.auto-after-match");

    private BooleanSupplier lancementOuClotureEnCours = () -> false;

    public void setLancementOuClotureEnCours(BooleanSupplier verification) {
        this.lancementOuClotureEnCours = verification;
    }

    private final Map<GameMap, Boolean> propre = new HashMap<>();

    public void marquerJouee(GameMap map) {
        propre.put(map, Boolean.FALSE);
    }

    public boolean estPropre(GameMap map) {
        return propre.getOrDefault(map, Boolean.TRUE);
    }

    public boolean sauvegardeDisponible(GameMap map) {
        if (map.getDossierSauvegarde() == null) {
            return false;
        }
        return Files.isRegularFile(Bukkit.getWorldContainer().toPath()
                .resolve(map.getDossierSauvegarde()).resolve("level.dat"));
    }

    public MapResetManager(JavaPlugin plugin, KillTracker killTracker, MapManager mapManager) {
        this.plugin = plugin;
        this.killTracker = killTracker;
        this.mapManager = mapManager;
    }

    public boolean isAutoApresPartie() {
        return autoApresPartie;
    }

    public void setAutoApresPartie(boolean actif) {
        this.autoApresPartie = actif;
    }

    public void reinitialiser(GameMap map, CommandSender demandeur) {
        if (map.getDossierSauvegarde() == null) {
            demandeur.sendMessage(Messages.get("mapresetmanager.impossible_aucun_backup_folder_defini_pour"));
            return;
        }

        if ((killTracker.estPartieDemarree() || lancementOuClotureEnCours.getAsBoolean()) && mapManager.getMapActuelle() == map) {
            demandeur.sendMessage(Messages.get("mapresetmanager.impossible_une_partie_est_en_cours"));
            return;
        }

        Path racine = Bukkit.getWorldContainer().toPath();
        Path dossierBackup = racine.resolve(map.getDossierSauvegarde());
        Path dossierMonde = racine.resolve(map.getCle());
        Path dossierStaging = racine.resolve(map.getCle() + "_staging_tmp");

        if (!Files.isRegularFile(dossierBackup.resolve("level.dat"))) {
            demandeur.sendMessage(Messages.get("mapresetmanager.impossible_dossier_de_sauvegarde_introuvable_ou", "dossierbackup", dossierBackup));
            return;
        }

        demandeur.sendMessage(Messages.get("mapresetmanager.reinitialisation_de_en_cours_voir_la", "valeur", map.getAffichage()));
        Bukkit.getLogger().info("[MapReset] Début réinitialisation " + map.getCle() + " depuis " + dossierBackup);

        World mondeActuel = Bukkit.getWorld(map.getCle());
        if (mondeActuel != null) {
            for (Player joueur : mondeActuel.getPlayers()) {
                mapManager.teleporterVersLobby(joueur);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> copierPuisRemplacer(map, demandeur, dossierBackup, dossierMonde, dossierStaging));
    }

    // La copie part d'abord vers un dossier temporaire : si elle échoue, le monde en place n'a pas été touché.
    private void copierPuisRemplacer(GameMap map, CommandSender demandeur, Path dossierBackup, Path dossierMonde, Path dossierStaging) {
        try {
            if (Files.exists(dossierStaging)) {
                supprimerRecursivement(dossierStaging);
            }
            copierRecursivement(dossierBackup, dossierStaging);
        } catch (IOException exception) {
            Bukkit.getLogger().warning("[MapReset] Échec de la copie de sauvegarde : " + exception.getMessage());
            Bukkit.getScheduler().runTask(plugin, () ->
                    demandeur.sendMessage(Messages.get("mapresetmanager.erreur_pendant_la_copie_de_la", "message", exception.getMessage())));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            World mondeADecharger = Bukkit.getWorld(map.getCle());

            if (mondeADecharger != null) {
                if (!Bukkit.unloadWorld(mondeADecharger, false)) {
                    List<String> restants = new ArrayList<>();
                    for (Player joueur : mondeADecharger.getPlayers()) {
                        restants.add(joueur.getName());
                    }

                    Bukkit.getLogger().warning("[MapReset] Déchargement de " + map.getCle()
                            + " refusé — joueurs encore présents : " + restants);
                    demandeur.sendMessage(Messages.get("mapresetmanager.reinitialisation_annulee_impossible_de_decharger_joueurs", "valeur", map.getAffichage(), "restants", (restants.isEmpty() ? Messages.get("mapresetmanager.aucun_monde_occupe_autrement") : String.join(", ", restants))));
                    demandeur.sendMessage(Messages.get("mapresetmanager.le_monde_pas_ete_touche_fais"));

                    nettoyerStaging(dossierStaging);
                    return;
                }
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> remplacerPuisRecharger(map, demandeur, dossierMonde, dossierStaging));
        });
    }

    private void nettoyerStaging(Path dossierStaging) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (Files.exists(dossierStaging)) {
                    supprimerRecursivement(dossierStaging);
                }
            } catch (IOException exception) {
                Bukkit.getLogger().warning("[MapReset] Dossier temporaire non nettoyé (" + dossierStaging
                        + ") : " + exception.getMessage());
            }
        });
    }

    private void remplacerPuisRecharger(GameMap map, CommandSender demandeur, Path dossierMonde, Path dossierStaging) {
        try {
            if (Files.exists(dossierMonde)) {
                supprimerRecursivement(dossierMonde);
            }
            Files.move(dossierStaging, dossierMonde);
        } catch (IOException exception) {
            Bukkit.getLogger().warning("[MapReset] Échec du remplacement : " + exception.getMessage());
            Bukkit.getScheduler().runTask(plugin, () ->
                    demandeur.sendMessage(Messages.get("mapresetmanager.erreur_pendant_le_remplacement_du_monde", "message", exception.getMessage(), "dossierstaging", dossierStaging)));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            World nouveauMonde = new WorldCreator(map.getCle()).createWorld();
            if (nouveauMonde != null) {
                propre.put(map, Boolean.TRUE);
                Bukkit.getLogger().info("[MapReset] " + map.getCle() + " réinitialisée et rechargée avec succès.");
                demandeur.sendMessage(Messages.get("mapresetmanager.reinitialisee_avec_succes_depuis_sa_sauvegarde", "valeur", map.getAffichage()));
                Bukkit.broadcastMessage(Messages.get("mapresetmanager.ete_reinitialisee_par_un_admin", "valeur", map.getAffichage()));
            } else {
                demandeur.sendMessage(Messages.get("mapresetmanager.erreur_le_monde_ete_restaure_sur"));
            }
        });
    }

    private void copierRecursivement(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void supprimerRecursivement(Path dossier) throws IOException {
        try (var flux = Files.walk(dossier)) {
            for (Path chemin : (Iterable<Path>) flux.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(chemin);
            }
        }
    }
}
