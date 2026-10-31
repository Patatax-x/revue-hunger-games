package com.hungergames.map;

import com.hungergames.equipe.EquipeManager;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SpawnCercleManager {
    private static final double ECART_COEQUIPIERS = 2.0;

    private final EquipeManager equipeManager;
    private final RoleManager roleManager;
    private final Random random = new Random();

    public SpawnCercleManager(EquipeManager equipeManager, RoleManager roleManager) {
        this.equipeManager = equipeManager;
        this.roleManager = roleManager;
    }

    public void placerEquipes(World monde, GameMap map) {
        List<List<Player>> groupes = construireGroupes();
        int total = groupes.size();

        if (total == 0) {
            return;
        }

        Collections.shuffle(groupes, random);
        double angleDepart = random.nextDouble() * 2 * Math.PI;

        for (int i = 0; i < total; i++) {
            double angle = angleDepart + (2 * Math.PI * i / total);
            double centreX = map.getCentreX() + map.getRayon() * Math.cos(angle);
            double centreZ = map.getCentreZ() + map.getRayon() * Math.sin(angle);

            List<Player> groupe = groupes.get(i);
            teleporterGroupe(monde, map, groupe, centreX, centreZ, angle);
        }
    }

    private void teleporterGroupe(World monde, GameMap map, List<Player> groupe, double centreX, double centreZ, double angle) {
        for (int j = 0; j < groupe.size(); j++) {
            double decalage = (j - (groupe.size() - 1) / 2.0) * ECART_COEQUIPIERS;
            double px = centreX + decalage * Math.cos(angle + Math.PI / 2);
            double pz = centreZ + decalage * Math.sin(angle + Math.PI / 2);

            float yaw = calculerYawVersCentre(px, pz, map.getCentreX(), map.getCentreZ());

            Location destination = new Location(monde, px, map.getCentreY(), pz, yaw, 0f);
            Player joueur = groupe.get(j);
            joueur.teleport(destination);

            joueur.setFallDistance(0f);
        }
    }

    private float calculerYawVersCentre(double px, double pz, double centreX, double centreZ) {
        double dx = centreX - px;
        double dz = centreZ - pz;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private List<List<Player>> construireGroupes() {
        List<List<Player>> groupes = new ArrayList<>(equipeManager.getEquipesEnLigne());

        Set<Player> dejaPlaces = new HashSet<>();
        for (List<Player> groupe : groupes) {
            dejaPlaces.addAll(groupe);
        }

        for (Player joueur : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(joueur)) continue;
            if (dejaPlaces.contains(joueur)) continue;
            if (equipeManager.aUneEquipe(joueur)) continue;

            groupes.add(List.of(joueur));
        }

        return groupes;
    }
}
