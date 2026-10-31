package com.hungergames.map;

import com.hungergames.config.Messages;
import com.hungergames.lobby.LobbyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.util.logging.Logger;

public class MapManager {
    private final SpawnCercleManager spawnCercleManager;

    private GameMap mapActuelle = GameMap.aucune();

    public MapManager(SpawnCercleManager spawnCercleManager) {
        this.spawnCercleManager = spawnCercleManager;
    }

    public void chargerMondes(Logger logger) {
        for (GameMap map : GameMap.values()) {
            if (!map.doitEtreChargeeAuDemarrage() || Bukkit.getWorld(map.getCle()) != null) {
                continue;
            }
            if (!Files.isRegularFile(Bukkit.getWorldContainer().toPath().resolve(map.getCle()).resolve("level.dat"))) {
                logger.warning("Monde introuvable pour la map " + map.getCle() + " : aucun dossier avec level.dat.");
                continue;
            }
            new WorldCreator(map.getCle()).createWorld();
        }
    }

    public GameMap getMapActuelle() {
        return mapActuelle;
    }

    public void retourLobby() {
        mapActuelle = GameMap.aucune();

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            teleporterVersLobby(joueur);
        }
    }

    public void teleporterVersLobby(Player joueur) {
        Location lobby = LobbyManager.getSpawnPrincipal();

        joueur.teleport(lobby);

        joueur.setFallDistance(0f);
    }

    public void demarrerMap(Player initiateur, GameMap map, Runnable apresPlacement) {
        World monde = Bukkit.getWorld(map.getCle());
        if (monde == null) {
            if (initiateur != null) {
                initiateur.sendMessage(Messages.get("mapmanager.erreur_le_monde_de_est_pas", "valeur", map.getAffichage()));
                initiateur.sendMessage(Messages.get("mapmanager.verifie_le_dossier_du_monde_et", "cle", map.getCle()));
            }
            return;
        }

        mapActuelle = map;

        if (initiateur != null) {
            initiateur.sendMessage(Messages.get("mapmanager.map_active_chaque_equipe_teleportee_directement", "valeur", map.getAffichage()));
        }

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            joueur.setInvulnerable(true);
        }

        spawnCercleManager.placerEquipes(monde, map);

        if (apresPlacement != null) {
            apresPlacement.run();
        } else {
            for (Player joueur : Bukkit.getOnlinePlayers()) {
                joueur.setInvulnerable(false);
            }
        }
    }
}
